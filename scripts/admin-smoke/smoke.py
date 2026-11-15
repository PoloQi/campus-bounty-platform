#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
管理端接口一键验证脚本（B1 审计底座 + B2 订单只读）

两种模式，用途不同，不要混用：

  readonly  只读探针。不写任何数据，可以对着真库（local profile）跑，
            用来确认「服务起来了、鉴权生效、接口返回结构正确」。

  full      完整业务链路。会注册用户、充值、发单、接单、封禁，
            只能对着 H2 内存库（test profile）跑，用完即弃，不污染共享库。

用法：

  # 只读验证真库
  python scripts/admin-smoke/smoke.py --mode readonly

  # 完整链路验证 H2
  python scripts/admin-smoke/smoke.py --mode full

  # 换地址/账号
  python scripts/admin-smoke/smoke.py --base http://localhost:8080 \
      --admin-user admin --admin-password admin123 --mode readonly

退出码：0 全部通过；1 有失败项。
"""
import argparse
import json
import sys
import time
import urllib.error
import urllib.request

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

PASSED = []
FAILED = []


class Api:
    def __init__(self, base):
        self.base = base.rstrip("/")

    def call(self, method, path, body=None, token=None):
        data = json.dumps(body).encode("utf-8") if body is not None else None
        request = urllib.request.Request(self.base + path, data=data, method=method)
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
        print("  [PASS] %-38s %s" % (name, detail))
    else:
        FAILED.append(name)
        print("  [FAIL] %-38s %s" % (name, detail))


def data_of(body):
    return (body or {}).get("data")


def code_of(body):
    return (body or {}).get("code")


def long_of(value):
    return int(value) if isinstance(value, (int, float)) else value


def frozen_diff(api, token):
    status, body, _ = api.call("GET", "/api/admin/wallets/frozen-summary", token=token)
    return float((data_of(body) or {}).get("diff") or 0)


def register(api, username, nickname):
    api.call("POST", "/api/auth/register", {
        "username": username, "password": "123456",
        "confirmPassword": "123456", "nickname": nickname})


def login(api, username, password):
    status, body, _ = api.call("POST", "/api/auth/login",
                               {"username": username, "password": password})
    return (data_of(body) or {}).get("token"), (data_of(body) or {})


def run_readonly(api, args):
    """只读探针：不产生任何写入。"""
    print("\n[1] 鉴权：无 token 必须被挡住")
    status, body, _ = api.call("GET", "/api/admin/logs")
    check("无 token 返回 401/40100", status == 401 and code_of(body) == 40100,
          "status=%s code=%s" % (status, code_of(body)))

    print("\n[2] admin 登录")
    token, profile = login(api, args.admin_user, args.admin_password)
    check("admin 登录成功", token is not None and profile.get("role") == "ADMIN",
          "role=%s" % profile.get("role"))
    if not token:
        print("  admin 登录失败，后续断言无法进行。检查账号密码是否正确。")
        return

    print("\n[3] B1 审计日志接口")
    status, body, _ = api.call("GET", "/api/admin/logs", token=token)
    check("GET /api/admin/logs 200/code=0", status == 200 and code_of(body) == 0,
          "status=%s rows=%s" % (status, len(data_of(body) or [])))

    status, body, _ = api.call("GET", "/api/admin/logs?action=BAN_USER", token=token)
    rows = data_of(body) or []
    check("按 action 筛选不报错", status == 200 and code_of(body) == 0,
          "rows=%d" % len(rows))
    check("筛选结果 action 全为 BAN_USER",
          all(row.get("action") == "BAN_USER" for row in rows), "rows=%d" % len(rows))

    print("\n[4] B2 订单接口")
    status, body, _ = api.call("GET", "/api/admin/orders", token=token)
    check("GET /api/admin/orders 200/code=0", status == 200 and code_of(body) == 0,
          "status=%s rows=%s" % (status, len(data_of(body) or [])))

    status, body, _ = api.call("GET", "/api/admin/orders?postType=BOUNTY&status=IN_PROGRESS",
                               token=token)
    check("组合条件筛选正常", status == 200 and code_of(body) == 0,
          "rows=%s" % len(data_of(body) or []))

    status, body, _ = api.call("GET", "/api/admin/orders?to=2020-01-01T00:00:00", token=token)
    check("时间上界在过去时结果为空", (data_of(body) or []) == [])

    status, body, _ = api.call("GET", "/api/admin/orders/99999999", token=token)
    check("不存在订单返回 40000 而非 500",
          status == 200 and code_of(body) == 40000, "status=%s code=%s" % (status, code_of(body)))

    print("\n[5] 脱敏：任意订单详情不得出现密码字段")
    status, body, _ = api.call("GET", "/api/admin/orders", token=token)
    rows = data_of(body) or []
    if not rows:
        print("  [SKIP] 库里没有订单，无法校验详情脱敏（full 模式可覆盖）")
        return
    order_id = rows[0].get("id")
    status, body, raw = api.call("GET", "/api/admin/orders/%s" % order_id, token=token)
    check("订单 #%s 详情 200/code=0" % order_id, status == 200 and code_of(body) == 0)
    check("详情原文不含 passwordHash",
          "passwordHash" not in raw and "password_hash" not in raw, "rawLen=%d" % len(raw))

    print("\n[6] B3 用户与资金只读")
    status, body, raw_users = api.call("GET", "/api/admin/users", token=token)
    check("GET /api/admin/users 200/code=0", status == 200 and code_of(body) == 0,
          "status=%s rows=%s" % (status, len(data_of(body) or [])))
    check("用户列表不含密码字段",
          "passwordHash" not in raw_users and "password_hash" not in raw_users,
          "rawLen=%d" % len(raw_users))

    status, body, _ = api.call("GET", "/api/admin/users?role=ADMIN", token=token)
    admins = data_of(body) or []
    check("role=ADMIN 筛选结果全为管理员",
          all(row.get("role") == "ADMIN" for row in admins), "rows=%d" % len(admins))

    status, body, _ = api.call("GET", "/api/admin/wallet-logs?type=RECHARGE", token=token)
    recharges = data_of(body) or []
    check("全平台流水按 type 筛选",
          all(row.get("type") == "RECHARGE" for row in recharges), "rows=%d" % len(recharges))

    status, body, _ = api.call("GET", "/api/admin/wallets/frozen-summary", token=token)
    summary = data_of(body) or {}
    check("冻结核对字段齐全",
          all(key in summary for key in
              ("frozenTotal", "inFlightAmount", "diff", "balanced", "inFlightOrderCount")),
          "frozen=%s inFlight=%s diff=%s" % (
              summary.get("frozenTotal"), summary.get("inFlightAmount"), summary.get("diff")))

    status, body, _ = api.call("GET", "/api/admin/users/99999999/wallet", token=token)
    check("不存在用户返回 40000", status == 200 and code_of(body) == 40000,
          "status=%s code=%s" % (status, code_of(body)))


def run_full(api, args):
    """完整链路：会写入数据，仅对 H2 内存库使用。"""
    print("\n" + "!" * 64)
    print("! full 模式会注册用户、充值、发单、接单、封禁 —— 只对 H2 内存库使用！")
    print("!" * 64)

    stamp = int(time.time()) % 1000000
    names = {"pub": "vpub%d" % stamp, "sel": "vsel%d" % stamp,
             "stu": "vstu%d" % stamp, "ban": "vban%d" % stamp}

    print("\n[1] admin 登录")
    admin_token, profile = login(api, args.admin_user, args.admin_password)
    check("admin 登录成功", admin_token is not None and profile.get("role") == "ADMIN",
          "role=%s" % profile.get("role"))
    if not admin_token:
        return

    print("\n[2] 造四名用户（三名普通用户 + 一名待封禁用户）")
    for key, nickname in (("pub", "验证发布者"), ("sel", "验证接单者"),
                          ("stu", "验证学生"), ("ban", "验证被禁者")):
        register(api, names[key], nickname)
    pub_token, pub_profile = login(api, names["pub"], "123456")
    sel_token, _ = login(api, names["sel"], "123456")
    stu_token, _ = login(api, names["stu"], "123456")
    ban_token, ban_profile = login(api, names["ban"], "123456")
    pub_id = (pub_profile or {}).get("userId")
    check("四名用户均可登录",
          all([pub_token, sel_token, stu_token, ban_token]), "pub/sel/stu/ban")

    print("\n[3] 学生被挡在管理端之外（B1/B2 鉴权）")
    status, body, _ = api.call("GET", "/api/admin/logs", token=stu_token)
    check("学生访问日志 403/40300", status == 403 and code_of(body) == 40300,
          "status=%s code=%s" % (status, code_of(body)))
    status, body, _ = api.call("GET", "/api/admin/orders", token=stu_token)
    check("学生访问订单列表 403", status == 403, "status=%s" % status)
    status, body, _ = api.call("GET", "/api/admin/orders/1", token=stu_token)
    check("学生访问订单详情 403", status == 403, "status=%s" % status)

    print("\n[4] 真实业务链路：充值 -> 发悬赏 -> 接单")
    diff_before = frozen_diff(api, admin_token)
    status, body, _ = api.call("POST", "/api/wallet/recharge", {"amount": 100}, pub_token)
    check("发布者充值 100", status == 200 and code_of(body) == 0)
    status, body, _ = api.call("GET", "/api/wallet", token=pub_token)
    check("余额 100、冻结 0",
          float((data_of(body) or {}).get("balance", -1)) == 100.0
          and float((data_of(body) or {}).get("frozen", -1)) == 0.0,
          "balance=%s frozen=%s" % ((data_of(body) or {}).get("balance"),
                                    (data_of(body) or {}).get("frozen")))

    status, body, _ = api.call("POST", "/api/trade/posts", {
        "title": "验证悬赏：管理端订单视图",
        "description": "手工验证用订单",
        "price": 20,
        "category": "学习",
        "deadline": "2026-12-31T23:59:59"}, pub_token)
    post_id = data_of(body)
    check("发布 20 元悬赏", status == 200 and code_of(body) == 0, "postId=%s" % post_id)

    status, body, _ = api.call("GET", "/api/wallet", token=pub_token)
    check("发布后余额 80、冻结 20（钱确实被扣住）",
          float((data_of(body) or {}).get("balance", -1)) == 80.0
          and float((data_of(body) or {}).get("frozen", -1)) == 20.0,
          "balance=%s frozen=%s" % ((data_of(body) or {}).get("balance"),
                                    (data_of(body) or {}).get("frozen")))

    status, body, _ = api.call("GET", "/api/trade/posts/%s" % post_id, token=pub_token)
    order_id = ((data_of(body) or {}).get("order") or {}).get("id")
    check("悬赏自带 PENDING 订单", order_id is not None, "orderId=%s" % order_id)

    status, body, _ = api.call("POST", "/api/trade/orders/%s/accept" % order_id, token=sel_token)
    check("他人接单转 IN_PROGRESS",
          status == 200 and (data_of(body) or {}).get("status") == "IN_PROGRESS",
          "status=%s" % (data_of(body) or {}).get("status"))

    diff_after = frozen_diff(api, admin_token)
    check("发单到接单全程冻结差额守恒", abs(diff_after - diff_before) < 0.001,
          "diff %.2f -> %.2f" % (diff_before, diff_after))

    print("\n[5] B2 列表筛选")
    status, body, _ = api.call("GET",
                               "/api/admin/orders?postType=BOUNTY&status=IN_PROGRESS", token=admin_token)
    rows = data_of(body) or []
    hit = [row for row in rows if long_of(row.get("id")) == order_id]
    check("组合筛选命中刚建的订单", len(hit) == 1, "rows=%d hit=%d" % (len(rows), len(hit)))

    status, body, _ = api.call("GET", "/api/admin/orders?postType=SERVICE", token=admin_token)
    service_rows = data_of(body) or []
    check("postType=SERVICE 不含该悬赏单",
          all(long_of(row.get("id")) != order_id for row in service_rows),
          "rows=%d" % len(service_rows))

    status, body, _ = api.call("GET", "/api/admin/orders?to=2020-01-01T00:00:00", token=admin_token)
    check("时间上界在过去时结果为空", (data_of(body) or []) == [])

    print("\n[6] B2 详情时间线聚合")
    status, body, raw = api.call("GET", "/api/admin/orders/%s" % order_id, token=admin_token)
    detail = data_of(body) or {}
    order_node = detail.get("order") or {}
    post_node = detail.get("post") or {}
    buyer_node = detail.get("buyer") or {}
    seller_node = detail.get("seller") or {}
    logs = detail.get("walletLogs") or []
    check("订单本体正确", long_of(order_node.get("id")) == order_id
          and order_node.get("status") == "IN_PROGRESS",
          "amount=%s" % order_node.get("amount"))
    check("关联帖子正确", long_of(post_node.get("id")) == post_id,
          "title=%s" % post_node.get("title"))
    check("买卖双方摘要正确",
          buyer_node.get("username") == names["pub"]
          and seller_node.get("username") == names["sel"],
          "%s -> %s" % (buyer_node.get("username"), seller_node.get("username")))
    check("资金流水首笔为 FREEZE", len(logs) >= 1 and logs[0].get("type") == "FREEZE",
          "logs=%d" % len(logs))
    check("申诉列表为空", detail.get("appeals") == [])
    check("详情形文不含密码字段",
          "passwordHash" not in raw and "password_hash" not in raw, "rawLen=%d" % len(raw))

    print("\n[7] B1 审计埋点：封禁用户后必须留痕")
    ban_target_id = (ban_profile or {}).get("userId")
    status, body, _ = api.call("POST", "/api/admin/users/%s/ban" % ban_target_id, token=admin_token)
    check("封禁用户成功", status == 200 and code_of(body) == 0, "userId=%s" % ban_target_id)

    status, body, _ = api.call("GET", "/api/admin/logs?action=BAN_USER", token=admin_token)
    rows = data_of(body) or []
    check("审计里能查到 BAN_USER 记录",
          any(long_of(row.get("targetId")) == ban_target_id for row in rows),
          "rows=%d" % len(rows))

    status, body, _ = api.call("GET", "/api/admin/logs?adminId=%s" % profile.get("userId"),
                               token=admin_token)
    check("按 adminId 筛选不报错", status == 200 and code_of(body) == 0,
          "adminId=%s rows=%s" % (profile.get("userId"), len(data_of(body) or [])))

    status, body, _ = api.call("GET", "/api/wallet", token=ban_token)
    check("封禁后旧 token 立即 401", status == 401, "status=%s" % status)

    print("\n[8] B3 用户与资金监管")
    status, body, raw_users = api.call("GET", "/api/admin/users?keyword=%s" % names["pub"],
                                       token=admin_token)
    users = data_of(body) or []
    check("用户列表 keyword 命中", any(u.get("username") == names["pub"] for u in users),
          "rows=%d" % len(users))
    check("用户列表不含密码字段",
          "passwordHash" not in raw_users and "password_hash" not in raw_users,
          "rawLen=%d" % len(raw_users))

    status, body, _ = api.call("GET", "/api/admin/users/%s/wallet" % pub_id, token=admin_token)
    wallet = data_of(body) or {}
    check("单用户钱包余额 80 冻结 20",
          float(wallet.get("balance", -1)) == 80.0 and float(wallet.get("frozen", -1)) == 20.0,
          "balance=%s frozen=%s" % (wallet.get("balance"), wallet.get("frozen")))

    status, body, _ = api.call("GET", "/api/admin/users/%s/wallet-logs" % pub_id, token=admin_token)
    own_logs = data_of(body) or []
    check("单用户流水首条为 FREEZE",
          len(own_logs) >= 1 and own_logs[0].get("type") == "FREEZE",
          "logs=%d" % len(own_logs))
    check("单用户流水不混入他人记录",
          all(long_of(row.get("userId")) == pub_id for row in own_logs), "logs=%d" % len(own_logs))

    status, body, _ = api.call("GET", "/api/admin/wallet-logs?type=FREEZE", token=admin_token)
    freezes = data_of(body) or []
    check("全平台流水按 type 筛选",
          len(freezes) >= 1 and all(row.get("type") == "FREEZE" for row in freezes),
          "rows=%d" % len(freezes))

    status, body, _ = api.call("GET", "/api/admin/wallet-logs?userId=%s" % pub_id, token=admin_token)
    pub_logs = data_of(body) or []
    check("全平台流水按 userId 筛选",
          len(pub_logs) >= 2 and all(long_of(row.get("userId")) == pub_id for row in pub_logs),
          "rows=%d" % len(pub_logs))

    summary = data_of(api.call("GET", "/api/admin/wallets/frozen-summary", token=admin_token)[1]) or {}
    check("冻结核对字段齐全",
          all(key in summary for key in
              ("frozenTotal", "inFlightAmount", "diff", "balanced", "inFlightOrderCount")),
          "frozen=%s inFlight=%s count=%s" % (
              summary.get("frozenTotal"), summary.get("inFlightAmount"),
              summary.get("inFlightOrderCount")))
    check("balanced 与 diff 一致",
          bool(summary.get("balanced")) == (float(summary.get("diff", 1)) == 0.0),
          "diff=%s balanced=%s" % (summary.get("diff"), summary.get("balanced")))


def main():
    parser = argparse.ArgumentParser(description="管理端接口一键验证")
    parser.add_argument("--base", default="http://localhost:8080", help="后端地址")
    parser.add_argument("--admin-user", default="admin", help="管理员用户名")
    parser.add_argument("--admin-password", default="admin123", help="管理员密码")
    parser.add_argument("--mode", choices=("readonly", "full"), default="readonly",
                        help="readonly=只读探针；full=完整链路（会写数据，仅 H2）")
    args = parser.parse_args()

    api = Api(args.base)
    print("=" * 64)
    print("管理端接口验证 | %s | mode=%s" % (api.base, args.mode))
    print("=" * 64)

    if args.mode == "readonly":
        run_readonly(api, args)
    else:
        run_full(api, args)

    print("\n" + "=" * 64)
    print("结果：%d 通过，%d 失败" % (len(PASSED), len(FAILED)))
    if FAILED:
        print("失败项：")
        for name in FAILED:
            print("  - " + name)
        return 1
    print("全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
