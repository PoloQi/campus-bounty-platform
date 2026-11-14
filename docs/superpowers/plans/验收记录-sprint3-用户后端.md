# Sprint 3 用户后端（学生侧）验收记录

> 分支：`feature/user-backend-sprint3`（基线：`origin/main` = `11cca95`，含 Sprint 1/2 全部代码）
> 日期：2026-11-14
> 范围：PB-32 学生侧申诉、PB-35 学生侧举报、PB-38 权限边界；不含管理端仲裁/看板/审计

## 一、交付物

| 模块 | 内容 | 提交 |
|---|---|---|
| 权限收口 | `/api/admin/**` 统一 `hasRole("ADMIN")`；学生访问 403；`accessDeniedHandler` 区分 401/403 | 327040f |
| 封禁拦截 | `JwtAuthFilter` 对 BANNED 用户不写 SecurityContext，旧 token 立即 401 | 327040f |
| 举报迁移 | `POST /api/admin/reports` → `POST /api/reports`；同人同对象 PENDING 去重 | 327040f |
| 申诉后端 | `t_appeal` 实体 + `AppealService.open/withdraw/viewOpen` + 3 个学生侧接口 | 176ff71 |
| DISPUTED 挂起 | 不改 `TradeOrderService`，靠既有状态白名单拒绝 accept/deliver/confirm/cancel | 176ff71 |
| 通知 | `APPEAL_CREATED` / `APPEAL_WITHDRAWN` 通知对方 | 176ff71 |
| 双端文档 | `用户端后端成果与双端对接说明.md` 追加 §9.3 Sprint 3 学生侧 | 3c62add |
| 计划文档 | `docs/superpowers/plans/2026-11-14-sprint3-用户后端.md` | 327040f |

## 二、自动化验证

```bash
cd campus-bounty-user-backend && ./mvnw.cmd clean verify
```

结果：**114/114 通过，Failures 0，Errors 0，Skipped 0，BUILD SUCCESS**，打包产出 `target/campus-bounty-backend-0.0.1-SNAPSHOT.jar`。

新增测试：

| 测试类 | 用例数 | 覆盖点 |
|---|---:|---|
| `AdminAccessSecurityTest` | 4 | 学生访问管理接口 403；admin 可访问；举报新路径 + 去重；BANNED token 401 |
| `AppealFlowTest` | 5 | 申诉置 DISPUTED + 通知；空理由/非参与方/重复/PENDING 拒绝；DISPUTED 挂起四操作；撤回恢复 + 仅申诉人 + 不可二次撤回；HTTP 冒烟 + 越权查询 |

修改既有测试：`AdminServiceTest` 中学生调用管理接口的断言由 body `code=40300` 改为 HTTP 403（安全链在控制器前拦截），举报路径改为 `/api/reports`。

## 三、关键场景验证（测试驱动）

| PRD 场景 | 验证方式 | 结果 |
|---|---|---|
| UC-A01 挂起 | `disputed_blocks_normal_flows`：DISPUTED 下 deliver/cancel 抛异常；撤回→交付→再申诉后 confirm/accept 抛异常 | ✅ |
| UC-A02 撤回 | `withdraw_restores_status_only_appellant_once`：非申诉人撤回失败；撤回后恢复 `from_status`，二次撤回失败 | ✅ |
| UC-A03 重复申诉 | `open_rejects_empty_outsider_duplicate_and_pending`：已有 OPEN 时再次申诉抛异常 | ✅ |
| 封禁旧 token 失效 | `banned_token_is_rejected`：封禁后旧 token 请求受保护接口 401 | ✅ |
| 举报去重 | `student_report_path_and_dedupe`：同 target 二次提交 `code=40000` | ✅ |
| 学生越权管理接口 | `student_forbidden_admin_api` + `AdminServiceTest`：HTTP 403 | ✅ |

## 四、与总计划的偏差（已按真实代码修正）

| 总计划假设 | 实际处理 |
|---|---|
| 后端目录 `backend/` | 实际 `campus-bounty-user-backend/` |
| Spring Boot 3.2.5 + Docker MySQL 测试 | 实际 Spring Boot 4.1.2 + H2（`@ActiveProfiles("test")`） |
| 需要给 `LoginResp` 加 role | S2 已完成，本分支未改动 |
| 封禁 token 断言 403 | 改为 401（未认证口径），并为「已认证但非 ADMIN」显式配置 `accessDeniedHandler` 返回 403 |
| `findByOrderIdAndStatus` | 改用 `findFirstByOrderIdAndStatusOrderByIdDesc`，避免多条记录抛异常 |
| `Appeal` 仲裁字段后续再加 | 一次性建全 `outcome/admin_id/admin_comment/handled_at`，管理端只加方法不改表 |

## 五、遗留与边界

1. 本分支**不提供**仲裁 `release/refund`、举报处置 `handle`、审计日志、订单/资金/看板查询——由管理端在 `AppealService` / `AdminService` 上加法实现。
2. `t_appeal` 仲裁字段已预留，管理端无需改表。
3. `OrderDetailView` 结构未变；学生前端需单独调用 `GET /api/trade/orders/{id}/appeal` 渲染纠纷横幅与撤回按钮。
4. 自动化测试基于 H2；真实 MySQL 联调前，数据库负责人需先执行 `schema.sql` 中新增的 `t_appeal` DDL（生产 `ddl-auto=validate`）。
5. 申诉/举报并发去重依赖订单行悲观锁（申诉）与服务层校验（举报）；课程规模不加数据库部分索引。
