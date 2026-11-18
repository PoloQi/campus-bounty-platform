#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
管理端接口一键验证脚本（看板 / 订单 / 用户 / 举报 / 资金 / 审计 / 申诉仲裁）

两种模式，用途不同，不要混用：

  readonly  只读探针。登录并读取接口，使用已有学生账号时也检查学生权限。

  full      完整业务链路。会注册用户、充值、发单、接单、举报、封禁、申诉，
            请使用 H2 内存库（test profile）或独立的测试数据库。

用法：

  # 先设置 BOUNTY_ADMIN_USERNAME 和 BOUNTY_ADMIN_PASSWORD
  python scripts/admin-smoke/smoke.py --mode readonly
  python scripts/admin-smoke/smoke.py --base http://localhost:8080 \
      --admin-user admin --admin-password admin123 --mode full

退出码：0 全部通过；1 有失败项。
"""
import argparse
import datetime
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

PASSED = []
FAILED = []


class Api:
    def __init__(self, base):
        self.base = base.rstrip("/")

    def call(self, method, path, body=None, token=None, headers=None):
        data = json.dumps(body).encode("utf-8") if body is not None else None
        request = urllib.request.Request(self.base + path, data=data, method=method, headers=headers or {})
        request.add_header("Content-Type", "application/json")
        if token:
            request.add_header("Authorization", "Bearer " + token)
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                raw = response.read().decode("utf-8")
                return response.status, (json.loads(raw) if raw else None), raw
        except urllib.error.HTTPError as error:
            raw = error.read().decode("utf-8")
            try:
                return error.code, (json.loads(raw) if raw else None), raw
            except json.JSONDecodeError:
                return error.code, None, raw
        except urllib.error.URLError as error:
            raise SystemExit("无法连接 %s：%s\n请先启动后端服务，见 docs/管理端接口验证手册.md"
                             % (self.base, error.reason))


def check(name, condition, detail=""):
    if condition:
        PASSED.append(name)
        print("  [PASS] %-36s %s" % (name, detail))
    else:
        FAILED.append(name)
        print("  [FAIL] %-36s %s" % (name, detail))


def data_of(body):
    return (body or {}).get("data")


def code_of(body):
    return (body or {}).get("code")


def num_of(mapping, key, default=-1.0):
    """安全取数值。不要写 `float(x.get(k) or -1)`：0.0 是 falsy，会被误判成取不到值。"""
    value = (mapping or {}).get(key)
    return default if value is None else float(value)


def amount_eq(actual, expected):
    return abs(actual - expected) < 0.001


def funds_difference(api, token):
    status, body, _ = api.call("GET", "/api/admin/funds", token=token)
    return num_of(data_of(body) or {}, "difference", 0.0)


def register(api, username, nickname):
    api.call("POST", "/api/auth/register", {
        "username": username, "password": "123456",
        "confirmPassword": "123456", "nickname": nickname})


def login(api, username, password):
    status, body, _ = api.call("POST", "/api/auth/login",
                               {"username": username, "password": password})
    return (data_of(body) or {}).get("token"), (data_of(body) or {})


def find_order_id(api, token, buyer_id):
    """用管理端订单接口反查最新一单，顺便验证这个接口真能用。"""
    status, body, _ = api.call("GET", "/api/admin/orders?buyerId=%s" % buyer_id, token=token)
    items = (data_of(body) or {}).get("items") or []
    return items[0].get("id") if items else None


def preflight(api, args):
    print("\n[1] 鉴权：无 token 必须被挡住")
    status, body, _ = api.call("GET", "/api/admin/dashboard")
    check("无 token 返回 401", status == 401, "status=%s" % status)

    print("\n[2] admin 登录")
    token, profile = login(api, args.admin_user, args.admin_password)
    check("admin 登录成功且角色为 ADMIN",
          token is not None and profile.get("role") == "ADMIN",
          "role=%s" % profile.get("role"))
    return token if profile.get("role") == "ADMIN" else None


def readonly_checks(api, token, admin_user):
    print("\n[3] 数据看板")
    status, body, _ = api.call("GET", "/api/admin/dashboard", token=token)
    dash = data_of(body) or {}
    totals = dash.get("totals") or {}
    check("看板 code=0 且返回 totals", code_of(body) == 0 and bool(totals))
    check("六个总量字段齐全",
          all(k in totals for k in ("students", "orders", "gmv", "frozen",
                                    "openAppeals", "pendingReports")),
          "keys=%s" % ",".join(sorted(totals.keys())))
    dist = dash.get("distribution") or []
    check("状态分布覆盖全部状态", len(dist) == 6, "n=%s" % len(dist))
    dist_sum = sum(int(row.get("total") or 0) for row in dist)
    check("分布求和等于订单总数", dist_sum == int(totals.get("orders") or 0),
          "%s == %s" % (dist_sum, totals.get("orders")))
    trend = dash.get("trend") or []
    check("趋势固定 7 项", len(trend) == 7, "n=%s" % len(trend))
    days = [row.get("orderDay") for row in trend]
    check("趋势日期严格升序", days == sorted(days) and len(set(days)) == len(days),
          "first=%s" % (days[0] if days else None))
    check("趋势最后一天是今天",
          bool(days) and days[-1] == datetime.date.today().isoformat(),
          "last=%s" % (days[-1] if days else None))

    print("\n[4] 订单查询")
    status, body, _ = api.call("GET", "/api/admin/orders", token=token)
    page = data_of(body) or {}
    check("列表返回分页结构 items/page/hasNext",
          all(k in page for k in ("items", "page", "hasNext")),
          "keys=%s" % ",".join(sorted(page.keys())))
    status, body, _ = api.call("GET", "/api/admin/orders?status=COMPLETED", token=token)
    rows = (data_of(body) or {}).get("items") or []
    check("status 过滤只返回 COMPLETED",
          all(r.get("status") == "COMPLETED" for r in rows), "n=%s" % len(rows))

    print("\n[5] 用户查询")
    query = urllib.parse.urlencode({"keyword": admin_user})
    status, body, _ = api.call("GET", "/api/admin/users?" + query, token=token)
    page = data_of(body) or {}
    rows = page.get("items") or []
    check("关键字命中当前管理员账号",
          code_of(body) == 0 and any(r.get("username", "").casefold() == admin_user.casefold()
                                    and r.get("role") == "ADMIN" for r in rows),
          "n=%s" % len(rows))
    check("用户列表不含密码字段",
          "passwordHash" not in json.dumps(rows), "leak=%s" % ("passwordHash" in json.dumps(rows)))

    print("\n[6] 举报列表")
    status, body, _ = api.call("GET", "/api/admin/reports", token=token)
    pending = data_of(body) or []
    check("PENDING 举报列表 code=0", code_of(body) == 0, "n=%s" % len(pending))
    check("列表内全部为 PENDING",
          all(r.get("status") == "PENDING" for r in pending), "n=%s" % len(pending))
    status, body, _ = api.call("GET", "/api/admin/reports/query", token=token)
    page = data_of(body) or {}
    check("举报查询返回分页结构",
          all(k in page for k in ("items", "page", "hasNext")),
          "n=%s" % len(page.get("items") or []))

    print("\n[7] 资金对账")
    status, body, _ = api.call("GET", "/api/admin/funds", token=token)
    funds = data_of(body) or {}
    frozen = num_of(funds, "frozen")
    expected = num_of(funds, "expected")
    difference = num_of(funds, "difference")
    check("资金三元组齐全", "frozen" in funds and "expected" in funds and "difference" in funds,
          "frozen=%.2f expected=%.2f" % (frozen, expected))
    check("difference 等于 frozen - expected", amount_eq(frozen - expected, difference),
          "%.2f vs %.2f" % (frozen - expected, difference))

    print("\n[8] 账本与审计")
    status, body, _ = api.call("GET", "/api/admin/funds/ledger", token=token)
    page = data_of(body) or {}
    check("资金账本返回分页结构",
          all(k in page for k in ("items", "page", "hasNext")),
          "n=%s" % len(page.get("items") or []))
    status, body, _ = api.call("GET", "/api/admin/audit", token=token)
    page = data_of(body) or {}
    check("审计日志返回分页结构",
          all(k in page for k in ("items", "page", "hasNext")),
          "n=%s" % len(page.get("items") or []))
    status, body, _ = api.call("GET", "/api/admin/audit?action=BAN_USER", token=token)
    rows = (data_of(body) or {}).get("items") or []
    check("action 过滤只返回该动作",
          all(r.get("action") == "BAN_USER" for r in rows), "n=%s" % len(rows))

    print("\n[9] 申诉列表")
    status, body, _ = api.call("GET", "/api/admin/appeals?status=OPEN", token=token)
    page = data_of(body) or {}
    check("申诉列表返回 items/page/totalPages",
          all(k in page for k in ("items", "page", "totalPages")),
          "keys=%s" % ",".join(sorted(page.keys())))
    rows = page.get("items") or []
    check("status=OPEN 只返回待裁决",
          all(r.get("status") == "OPEN" for r in rows), "n=%s" % len(rows))

def student_access_check(api, username, password):
    print("\n[10] 学生 token 不得进入管理端")
    student_token, profile = login(api, username, password)
    if not student_token or profile.get("role") != "STUDENT":
        check("七个只读端点均拒绝学生", False, "学生登录失败或账号不是学生")
        return
    blocked = True
    path = ""
    for path in ("/api/admin/dashboard", "/api/admin/orders", "/api/admin/users",
                 "/api/admin/reports", "/api/admin/funds", "/api/admin/audit",
                 "/api/admin/appeals"):
        status, _, _ = api.call("GET", path, token=student_token)
        if status != 403:
            blocked = False
            break
    check("七个只读端点均拒绝学生", blocked,
          "首个异常=%s" % (path if not blocked else "无"))


def full_checks(api, token):
    suffix = uuid.uuid4().hex[:8]

    print("\n[11] 造数：买家充值 + 发悬赏 + 卖家接单")
    buyer_name = "smb" + suffix
    seller_name = "smk" + suffix
    register(api, buyer_name, "冒烟买家")
    register(api, seller_name, "冒烟卖家")
    buyer_token, buyer = login(api, buyer_name, "123456")
    seller_token, seller = login(api, seller_name, "123456")
    buyer_id = buyer.get("userId")
    seller_id = seller.get("userId")
    check("买卖双方注册并登录成功", bool(buyer_token) and bool(seller_token),
          "buyerId=%s sellerId=%s" % (buyer_id, seller_id))

    api.call("POST", "/api/wallet/recharge", {"amount": 100}, buyer_token)
    status, body, _ = api.call("POST", "/api/trade/posts", {
        "title": "冒烟悬赏：仲裁链路 %s" % suffix,
        "description": "管理端冒烟脚本生成",
        "price": 20,
        "category": "学习",
        "deadline": (datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8)))
                     + datetime.timedelta(days=1)).replace(tzinfo=None).isoformat(timespec="seconds")},
        buyer_token, headers={"Idempotency-Key": str(uuid.uuid4())})
    post_id = data_of(body)
    check("发布悬赏成功", post_id is not None, "postId=%s" % post_id)
    if post_id is None:
        print("  发帖失败，后续链路无法继续。")
        return

    wallet = data_of(api.call("GET", "/api/wallet", token=buyer_token)[1]) or {}
    check("发帖后买家冻结 20", amount_eq(num_of(wallet, "frozen"), 20.0),
          "frozen=%s" % wallet.get("frozen"))

    order_id = find_order_id(api, token, buyer_id)
    check("管理端能反查到该订单", order_id is not None, "orderId=%s" % order_id)
    if order_id is None:
        return
    status, body, _ = api.call("POST", "/api/trade/orders/%s/accept" % order_id, None, seller_token)
    check("卖家接单成功", code_of(body) == 0, "code=%s" % code_of(body))

    print("\n[12] 订单详情取证")
    status, body, _ = api.call("GET", "/api/admin/orders/%s" % order_id, token=token)
    detail = data_of(body) or {}
    check("详情聚合订单/帖子/双方/流水",
          all(k in detail for k in ("order", "post", "buyer", "seller", "logs",
                                    "appeals", "timeline")),
          "keys=%s" % ",".join(sorted(detail.keys())))
    check("详情不含 passwordHash",
          "passwordHash" not in json.dumps(detail), "")
    order = detail.get("order") or {}
    check("订单状态为 IN_PROGRESS", order.get("status") == "IN_PROGRESS",
          "status=%s" % order.get("status"))

    print("\n[13] 举报 → 处置 → 通知")
    reporter_name = "smr" + suffix
    register(api, reporter_name, "冒烟举报人")
    reporter_token, reporter = login(api, reporter_name, "123456")
    status, body, _ = api.call("POST", "/api/reports", {
        "targetType": "TRADE_POST", "targetId": post_id,
        "reason": "冒烟脚本提交的举报"}, reporter_token)
    check("学生提交举报成功", code_of(body) == 0, "code=%s" % code_of(body))

    status, body, _ = api.call("GET", "/api/admin/reports/query?status=PENDING", token=token)
    items = (data_of(body) or {}).get("items") or []
    mine = [r for r in items if int(r.get("targetId") or 0) == int(post_id)]
    check("待处理列表命中该举报", len(mine) == 1, "n=%s" % len(mine))
    if not mine:
        return
    report_id = mine[0].get("id")

    status, body, _ = api.call("POST", "/api/admin/reports/%s/handle" % report_id, {
        "result": "VALID", "moderate": True, "comment": "冒烟核实成立，下架处理"}, token)
    check("处置提交成功", code_of(body) == 0, "code=%s" % code_of(body))

    status, body, _ = api.call("GET", "/api/admin/reports/%s" % report_id, token=token)
    row = data_of(body) or {}
    check("举报状态回写 HANDLED/VALID",
          row.get("status") == "HANDLED" and row.get("result") == "VALID",
          "status=%s result=%s" % (row.get("status"), row.get("result")))
    check("治理动作落库 CLOSE_TRADE_POST",
          row.get("moderationAction") == "CLOSE_TRADE_POST",
          "action=%s" % row.get("moderationAction"))

    status, body, _ = api.call("GET", "/api/admin/moderation/TRADE_POST/%s" % post_id, token=token)
    moderation = data_of(body) or {}
    check("被举报交易帖已下架",
          str(moderation.get("status", "")).upper() == "CLOSED",
          "status=%s" % moderation.get("status"))

    status, body, _ = api.call("GET", "/api/admin/audit?action=HANDLE_REPORT", token=token)
    rows = (data_of(body) or {}).get("items") or []
    hits = [r for r in rows if int(r.get("targetId") or 0) == int(report_id)]
    check("审计留有 HANDLE_REPORT 记录", len(hits) == 1, "rows=%s" % len(hits))

    status, body, _ = api.call("POST", "/api/admin/reports/%s/handle" % report_id, {
        "result": "REJECTED", "comment": "重复处置"}, token)
    check("重复处置被拒", code_of(body) == 40000, "code=%s" % code_of(body))

    print("\n[14] 封禁 → token 失效 → 解封 → 恢复")
    victim_name = "smv" + suffix
    register(api, victim_name, "冒烟被禁用户")
    victim_token, victim = login(api, victim_name, "123456")
    victim_id = victim.get("id") or victim.get("userId")

    status, _, _ = api.call("GET", "/api/trade/posts", token=victim_token)
    check("封禁前 token 可用", status == 200, "status=%s" % status)

    status, body, _ = api.call("POST", "/api/admin/users/%s/ban" % victim_id, None, token)
    check("封禁成功", code_of(body) == 0, "code=%s" % code_of(body))
    status, _, _ = api.call("GET", "/api/trade/posts", token=victim_token)
    check("封禁后旧 token 立即失效", status == 401, "status=%s" % status)
    status, body, _ = api.call("POST", "/api/auth/login",
                               {"username": victim_name, "password": "123456"})
    check("封禁期间登录被拒", code_of(body) == 40000, "code=%s" % code_of(body))

    status, body, _ = api.call("POST", "/api/admin/users/%s/unban" % victim_id, None, token)
    check("解封成功", code_of(body) == 0, "code=%s" % code_of(body))
    status, _, _ = api.call("GET", "/api/trade/posts", token=victim_token)
    check("解封后旧 token 恢复可用（未做吊销）", status == 200, "status=%s" % status)

    print("\n[15] 申诉 → 仲裁判付 → 资金到位")
    status, body, _ = api.call("POST", "/api/trade/orders/%s/appeal" % order_id,
                               {"reason": "冒烟：已完成跑腿但对方拒付"}, seller_token)
    check("学生发起申诉成功", code_of(body) == 0, "code=%s" % code_of(body))

    status, body, _ = api.call("GET", "/api/admin/appeals?status=OPEN", token=token)
    items = (data_of(body) or {}).get("items") or []
    mine = [a for a in items if int(a.get("orderId") or 0) == int(order_id)]
    check("待裁决列表命中该申诉", len(mine) == 1, "n=%s" % len(mine))
    if not mine:
        return
    appeal_id = mine[0].get("id")

    diff_before = funds_difference(api, token)
    status, body, _ = api.call("POST", "/api/admin/appeals/%s/arbitrate" % appeal_id,
                               {"outcome": "RELEASE_TO_SELLER",
                                "comment": "冒烟核实已交付，判付"}, token)
    check("仲裁判付成功", code_of(body) == 0, "code=%s" % code_of(body))

    seller_wallet = data_of(api.call("GET", "/api/wallet", token=seller_token)[1]) or {}
    check("卖家收到全额赏金 20", amount_eq(num_of(seller_wallet, "balance"), 20.0),
          "balance=%s" % seller_wallet.get("balance"))
    buyer_wallet = data_of(api.call("GET", "/api/wallet", token=buyer_token)[1]) or {}
    check("买家冻结清零、余额保持 80",
          amount_eq(num_of(buyer_wallet, "frozen"), 0.0)
          and amount_eq(num_of(buyer_wallet, "balance"), 80.0),
          "balance=%s frozen=%s" % (buyer_wallet.get("balance"), buyer_wallet.get("frozen")))

    status, body, _ = api.call("GET", "/api/admin/orders/%s" % order_id, token=token)
    order = (data_of(body) or {}).get("order") or {}
    check("订单终态为 COMPLETED", order.get("status") == "COMPLETED",
          "status=%s" % order.get("status"))

    diff_after = funds_difference(api, token)
    check("对账差额增量守恒（裁决只挪冻结）",
          amount_eq(diff_after - diff_before, 0.0),
          "%.2f -> %.2f" % (diff_before, diff_after))

    status, body, _ = api.call("GET", "/api/admin/audit?action=ARBITRATE_APPEAL", token=token)
    rows = (data_of(body) or {}).get("items") or []
    hits = [r for r in rows if int(r.get("targetId") or 0) == int(appeal_id)]
    check("审计留有 ARBITRATE_APPEAL 记录", len(hits) == 1, "rows=%s" % len(hits))

    status, body, _ = api.call("POST", "/api/admin/appeals/%s/arbitrate" % appeal_id,
                               {"outcome": "REFUND_TO_BUYER", "comment": "重复裁决"}, token)
    check("重复裁决被拒（钱只动一次）", code_of(body) == 40000, "code=%s" % code_of(body))
    seller_wallet = data_of(api.call("GET", "/api/wallet", token=seller_token)[1]) or {}
    check("重复裁决后卖家余额仍为 20", amount_eq(num_of(seller_wallet, "balance"), 20.0),
          "balance=%s" % seller_wallet.get("balance"))

    print("\n[16] 看板随链路更新")
    status, body, _ = api.call("GET", "/api/admin/dashboard", token=token)
    dash = data_of(body) or {}
    totals = dash.get("totals") or {}
    dist = dash.get("distribution") or []
    check("分布求和仍等于订单总数",
          sum(int(r.get("total") or 0) for r in dist) == int(totals.get("orders") or 0),
          "orders=%s" % totals.get("orders"))
    check("GMV 已计入本单 20", num_of(totals, "gmv") >= 20.0,
          "gmv=%s" % totals.get("gmv"))


def summarize():
    print("\n结果：%s 通过，%s 失败" % (len(PASSED), len(FAILED)))
    if FAILED:
        print("\n未通过项：")
        for name in FAILED:
            print("  - %s" % name)
        return 1
    return 0


def main():
    parser = argparse.ArgumentParser(description="管理端接口一键验证")
    parser.add_argument("--base", default="http://localhost:8080")
    parser.add_argument("--admin-user", default=os.environ.get("BOUNTY_ADMIN_USERNAME"))
    parser.add_argument("--admin-password", default=os.environ.get("BOUNTY_ADMIN_PASSWORD"))
    parser.add_argument("--student-user", default=os.environ.get("SMOKE_STUDENT_USERNAME"))
    parser.add_argument("--student-password", default=os.environ.get("SMOKE_STUDENT_PASSWORD"))
    parser.add_argument("--mode", choices=("readonly", "full"), default="readonly")
    args = parser.parse_args()
    if not args.admin_user or not args.admin_password:
        parser.error("请设置 BOUNTY_ADMIN_USERNAME 和 BOUNTY_ADMIN_PASSWORD，或同时提供 --admin-user 和 --admin-password")
    if bool(args.student_user) != bool(args.student_password):
        parser.error("--student-user 和 --student-password 必须同时提供")

    api = Api(args.base)
    print("目标：%s" % args.base)
    print("模式：%s（%s）" % (args.mode,
                             "只读，不写数据" if args.mode == "readonly"
                             else "完整链路，使用独立测试数据库"))

    token = preflight(api, args)
    if not token:
        print("  admin 登录失败，后续断言无法进行。请检查账号密码。")
        return summarize()

    readonly_checks(api, token, args.admin_user)
    if args.student_user:
        student_access_check(api, args.student_user, args.student_password)
    elif args.mode == "full":
        probe_name = "smp" + uuid.uuid4().hex[:8]
        register(api, probe_name, "冒烟探针")
        student_access_check(api, probe_name, "123456")
    else:
        print("\n[10] 未提供已有学生账号，跳过学生权限探针")
    if args.mode == "full":
        full_checks(api, token)
    return summarize()


if __name__ == "__main__":
    sys.exit(main())
