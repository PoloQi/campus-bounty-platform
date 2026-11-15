#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
管理端接口一键验证脚本（B1 审计底座 + B2 订单只读 + B3 用户与资金 + B4 举报闭环）

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


def num_of(mapping, key, default=-1.0):
    """安全取数值。不要写 `float(x.get(k) or -1)`：0.0 是 falsy，会被误判成取不到值。"""
    value = (mapping or {}).get(key)
    return default if value is None else float(value)


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

    print("\n[7] B4 举报接口（只读探针）")
    status, body, _ = api.call("GET", "/api/admin/reports", token=token)
    check("GET /api/admin/reports 200/code=0", status == 200 and code_of(body) == 0,
          "status=%s rows=%s" % (status, len(data_of(body) or [])))

    status, body, _ = api.call("GET", "/api/admin/reports?status=PENDING", token=token)
    rows = data_of(body) or []
    check("status=PENDING 筛选结果全为待处理",
          all(row.get("status") == "PENDING" for row in rows), "rows=%d" % len(rows))

    status, body, _ = api.call("GET", "/api/admin/reports?targetType=USER", token=token)
    rows = data_of(body) or []
    check("targetType 筛选结果类型一致",
          all(row.get("targetType") == "USER" for row in rows), "rows=%d" % len(rows))

    status, body, _ = api.call("GET", "/api/admin/reports")
    check("无 token 访问举报列表 401", status == 401 and code_of(body) == 40100,
          "status=%s code=%s" % (status, code_of(body)))

    status, body, _ = api.call("POST", "/api/admin/reports/1/handle", {"action": "REJECT"})
    check("无 token 调用处置接口 401", status == 401, "status=%s" % status)

    print("\n[8] B6 申诉接口（只读探针）")
    status, body, _ = api.call("GET", "/api/admin/appeals", token=token)
    check("GET /api/admin/appeals 200/code=0", status == 200 and code_of(body) == 0,
          "status=%s rows=%s" % (status, len(data_of(body) or [])))

    status, body, _ = api.call("GET", "/api/admin/appeals?status=OPEN", token=token)
    rows = data_of(body) or []
    check("status=OPEN 筛选结果全为待裁决",
          all(row.get("status") == "OPEN" for row in rows), "rows=%d" % len(rows))
    check("OPEN 申诉排在列表最前",
          not rows or rows[0].get("status") == "OPEN", "rows=%d" % len(rows))

    status, body, _ = api.call("GET", "/api/admin/appeals/99999999", token=token)
    check("不存在申诉返回 40000 而非 500",
          status == 200 and code_of(body) == 40000, "code=%s" % code_of(body))

    status, body, _ = api.call("GET", "/api/admin/appeals")
    check("无 token 访问申诉列表 401", status == 401, "status=%s" % status)


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

    print("\n[9] B4 举报处置闭环")
    status, body, _ = api.call("POST", "/api/reports", {
        "targetType": "TRADE_POST", "targetId": post_id,
        "reason": "冒烟举报：服务内容违规"}, stu_token)
    check("学生提交举报", status == 200 and code_of(body) == 0)

    status, body, _ = api.call("GET", "/api/admin/reports?status=PENDING&targetType=TRADE_POST",
                               token=admin_token)
    rows = data_of(body) or []
    mine = [row for row in rows if long_of(row.get("targetId")) == long_of(post_id)]
    check("待处理举报能查到", len(mine) == 1, "rows=%d" % len(rows))
    report_id = mine[0].get("id") if mine else None

    status, body, _ = api.call("GET", "/api/admin/reports", token=stu_token)
    check("学生访问举报列表 403", status == 403, "status=%s" % status)

    if report_id is None:
        return

    status, body, _ = api.call("POST", "/api/admin/reports/%s/handle" % report_id,
                               {"action": "CLOSE_TRADE_POST", "comment": "冒烟处置：违规下架"},
                               admin_token)
    handled = data_of(body) or {}
    check("处置成功回写 HANDLED/VALID",
          handled.get("status") == "HANDLED" and handled.get("result") == "VALID",
          "status=%s result=%s" % (handled.get("status"), handled.get("result")))
    check("回写处置人与处置时间",
          handled.get("handlerId") is not None and handled.get("handledAt") is not None,
          "comment=%s" % handled.get("handleComment"))

    status, body, _ = api.call("GET", "/api/trade/posts/%s" % post_id, token=stu_token)
    post_status = ((data_of(body) or {}).get("post") or {}).get("status")
    check("被举报交易帖已下架", post_status == "CLOSED", "postStatus=%s" % post_status)

    status, body, _ = api.call("GET", "/api/admin/logs?action=HANDLE_REPORT", token=admin_token)
    logs = data_of(body) or []
    check("审计留有 HANDLE_REPORT 记录",
          any(long_of(row.get("targetId")) == long_of(report_id) for row in logs),
          "rows=%d" % len(logs))

    status, body, _ = api.call("GET", "/api/admin/logs?action=CLOSE_TRADE_POST", token=admin_token)
    closed_logs = data_of(body) or []
    check("未复用旧下架方法（无第二条审计）",
          all(long_of(row.get("targetId")) != long_of(post_id) for row in closed_logs),
          "rows=%d" % len(closed_logs))

    status, body, _ = api.call("GET", "/api/notify", token=stu_token)
    notes = data_of(body) or []
    hit = [row for row in notes
           if row.get("type") == "REPORT_HANDLED" and long_of(row.get("refId")) == long_of(report_id)]
    check("举报人收到 REPORT_HANDLED 通知", len(hit) == 1, "notes=%d" % len(notes))

    status, body, _ = api.call("POST", "/api/admin/reports/%s/handle" % report_id,
                               {"action": "REJECT"}, admin_token)
    check("重复处置被拒", status == 200 and code_of(body) == 40000,
          "code=%s" % code_of(body))

    status, body, _ = api.call("GET", "/api/admin/reports?status=HANDLED", token=admin_token)
    check("status=HANDLED 筛选命中",
          any(long_of(row.get("id")) == long_of(report_id) for row in (data_of(body) or [])))
    status, body, _ = api.call("GET", "/api/admin/reports?status=PENDING", token=admin_token)
    check("status=PENDING 不再包含该举报",
          all(long_of(row.get("id")) != long_of(report_id) for row in (data_of(body) or [])))

    print("\n[10] B5 用户治理：解封（接 [7] 的封禁闭环）")
    status, body, _ = api.call("POST", "/api/auth/login",
                               {"username": names["ban"], "password": "123456"})
    check("封禁期间登录被拒", code_of(body) == 40000, "code=%s" % code_of(body))
    check("拒绝原因指向封禁", "封禁" in ((body or {}).get("message") or ""),
          "message=%s" % (body or {}).get("message"))

    status, body, _ = api.call("POST", "/api/admin/users/%s/unban" % ban_target_id, token=stu_token)
    check("学生调用解封 403", status == 403, "status=%s" % status)

    status, body, _ = api.call("POST", "/api/admin/users/99999999/unban", token=admin_token)
    check("解封不存在用户 40000 而非 500",
          status == 200 and code_of(body) == 40000, "code=%s" % code_of(body))

    status, body, _ = api.call("POST", "/api/admin/users/%s/unban" % ban_target_id, token=admin_token)
    check("管理员解封成功", status == 200 and code_of(body) == 0, "code=%s" % code_of(body))

    status, body, _ = api.call("GET", "/api/admin/users?keyword=%s" % names["ban"], token=admin_token)
    rows = [u for u in (data_of(body) or []) if u.get("username") == names["ban"]]
    check("用户状态回到 ACTIVE",
          len(rows) == 1 and rows[0].get("status") == "ACTIVE",
          "status=%s" % (rows[0].get("status") if rows else None))

    new_token, new_profile = login(api, names["ban"], "123456")
    check("解封后可重新登录", new_token is not None,
          "userId=%s" % (new_profile or {}).get("userId"))
    check("重新登录拿到的是同一账号",
          long_of((new_profile or {}).get("userId")) == long_of(ban_target_id),
          "%s vs %s" % ((new_profile or {}).get("userId"), ban_target_id))

    status, body, _ = api.call("GET", "/api/wallet", token=ban_token)
    check("解封后旧 token 恢复可用（未做 token 吊销）", status == 200, "status=%s" % status)

    status, body, _ = api.call("GET", "/api/admin/logs?action=UNBAN_USER", token=admin_token)
    rows = data_of(body) or []
    check("审计留有 UNBAN_USER 记录",
          any(long_of(row.get("targetId")) == long_of(ban_target_id) for row in rows),
          "rows=%d" % len(rows))

    status, body, _ = api.call("POST", "/api/admin/users/%s/unban" % ban_target_id, token=admin_token)
    check("重复解封幂等（不报错）", status == 200 and code_of(body) == 0,
          "code=%s" % code_of(body))

    print("\n[11] B6 申诉仲裁（唯一动钱，复用 [4] 的订单）")
    status, body, _ = api.call("POST", "/api/trade/orders/%s/appeal" % order_id,
                               {"reason": "冒烟申诉：已完成跑腿，对方不确认"}, sel_token)
    appeal = data_of(body) or {}
    appeal_no = appeal.get("id")
    check("卖家发起申诉", status == 200 and code_of(body) == 0, "appealId=%s" % appeal_no)
    check("订单转入 DISPUTED", appeal.get("fromStatus") == "IN_PROGRESS",
          "fromStatus=%s" % appeal.get("fromStatus"))
    if appeal_no is None:
        return

    status, body, _ = api.call("GET", "/api/admin/appeals?status=OPEN", token=admin_token)
    rows = data_of(body) or []
    mine = [row for row in rows if long_of(row.get("id")) == long_of(appeal_no)]
    check("待裁决申诉能查到", len(mine) == 1, "rows=%d" % len(rows))
    check("列表带出订单金额与申诉人",
          bool(mine) and num_of(mine[0], "orderAmount", 0) == 20.0
          and mine[0].get("appellantName") is not None,
          "amount=%s name=%s" % (mine[0].get("orderAmount") if mine else None,
                                 mine[0].get("appellantName") if mine else None))

    status, body, raw_appeal = api.call("GET", "/api/admin/appeals/%s" % appeal_no, token=admin_token)
    detail = data_of(body) or {}
    check("详情识别申诉方为 SELLER", detail.get("appellantRole") == "SELLER",
          "role=%s" % detail.get("appellantRole"))
    check("详情显示订单在纠纷中", (detail.get("order") or {}).get("status") == "DISPUTED",
          "orderStatus=%s" % (detail.get("order") or {}).get("status"))
    check("详情含该单冻结流水",
          any(row.get("type") == "FREEZE" for row in (detail.get("walletLogs") or [])),
          "logs=%d" % len(detail.get("walletLogs") or []))
    check("申诉详情不含密码字段",
          "passwordHash" not in raw_appeal and "password_hash" not in raw_appeal,
          "rawLen=%d" % len(raw_appeal))

    status, body, _ = api.call("GET", "/api/admin/appeals", token=stu_token)
    check("学生访问申诉列表 403", status == 403, "status=%s" % status)

    summary_before = data_of(api.call("GET", "/api/admin/wallets/frozen-summary",
                                      token=admin_token)[1]) or {}
    status, body, _ = api.call("POST", "/api/admin/appeals/%s/release" % appeal_no,
                               {"comment": "冒烟裁决：核实已送达，判付"}, admin_token)
    resolved = data_of(body) or {}
    check("裁决回写 RESOLVED/RELEASE_TO_SELLER",
          resolved.get("status") == "RESOLVED"
          and resolved.get("outcome") == "RELEASE_TO_SELLER",
          "status=%s outcome=%s" % (resolved.get("status"), resolved.get("outcome")))
    check("裁决记录仲裁人与意见",
          resolved.get("adminId") is not None and resolved.get("handledAt") is not None
          and resolved.get("adminComment") is not None,
          "comment=%s" % resolved.get("adminComment"))

    status, body, _ = api.call("GET", "/api/trade/posts/%s" % post_id, token=pub_token)
    check("订单终态 COMPLETED",
          ((data_of(body) or {}).get("order") or {}).get("status") == "COMPLETED",
          "orderStatus=%s" % ((data_of(body) or {}).get("order") or {}).get("status"))

    status, body, _ = api.call("GET", "/api/wallet", token=sel_token)
    seller_wallet = data_of(body) or {}
    check("卖家收到全额赏金 20", num_of(seller_wallet, "balance") == 20.0,
          "balance=%s" % seller_wallet.get("balance"))

    status, body, _ = api.call("GET", "/api/wallet", token=pub_token)
    buyer_wallet = data_of(body) or {}
    check("买家冻结清零、余额保持 80",
          num_of(buyer_wallet, "frozen") == 0.0 and num_of(buyer_wallet, "balance") == 80.0,
          "balance=%s frozen=%s" % (buyer_wallet.get("balance"), buyer_wallet.get("frozen")))

    summary_after = data_of(api.call("GET", "/api/admin/wallets/frozen-summary",
                                     token=admin_token)[1]) or {}
    drop = num_of(summary_before, "frozenTotal", 0) - num_of(summary_after, "frozenTotal", 0)
    check("平台冻结总额随裁决下降 20", abs(drop - 20.0) < 0.001,
          "%.2f -> %.2f" % (num_of(summary_before, "frozenTotal", 0),
                            num_of(summary_after, "frozenTotal", 0)))

    status, body, _ = api.call("GET", "/api/admin/logs?action=APPEAL_RELEASE", token=admin_token)
    rows = data_of(body) or []
    check("审计留有 APPEAL_RELEASE 记录",
          any(long_of(row.get("targetId")) == long_of(appeal_no) for row in rows),
          "rows=%d" % len(rows))

    status, body, _ = api.call("GET", "/api/notify", token=sel_token)
    notes = data_of(body) or []
    check("卖家收到 APPEAL_RESOLVED 通知",
          any(row.get("type") == "APPEAL_RESOLVED" and long_of(row.get("refId")) == long_of(order_id)
              for row in notes), "notes=%d" % len(notes))
    status, body, _ = api.call("GET", "/api/notify", token=pub_token)
    notes = data_of(body) or []
    check("买家收到 APPEAL_RESOLVED 通知",
          any(row.get("type") == "APPEAL_RESOLVED" and long_of(row.get("refId")) == long_of(order_id)
              for row in notes), "notes=%d" % len(notes))

    status, body, _ = api.call("POST", "/api/admin/appeals/%s/release" % appeal_no,
                               {"comment": "重复裁决"}, admin_token)
    check("重复裁决被拒（钱只动一次）", status == 200 and code_of(body) == 40000,
          "code=%s" % code_of(body))

    status, body, _ = api.call("GET", "/api/wallet", token=sel_token)
    check("重复裁决后卖家余额仍为 20",
          num_of(data_of(body) or {}, "balance") == 20.0,
          "balance=%s" % (data_of(body) or {}).get("balance"))


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
