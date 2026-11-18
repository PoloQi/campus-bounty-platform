# 校园悬赏平台前端

## 本地运行

前端使用 Vue 3、Vite、Vue Router、Pinia、Element Plus 和 Axios，管理端仪表盘使用 ECharts。需要 Node.js 18 或 20 及以上版本。

在 `frontend` 目录执行以下命令，然后访问 http://127.0.0.1:5173/。

PowerShell：

```powershell
npm ci
npm run dev
```

Vite 将 `/api/*`、`/uploads/*` 和 `/ws/chat` 代理到 Spring Boot。默认后端地址是 `http://127.0.0.1:8080`，可使用 `BOUNTY_API_ORIGIN` 修改。开发端口可通过 `FRONTEND_PORT` 修改。

`npm run build` 生成 `dist/`。`npm run preview` 在本地预览构建产物，并沿用后端代理配置。

使用前后端镜像一键启动，见 [Docker Compose 部署与 CodeArts 交接](../docs/部署与CodeArts交接.md)。

## 页面与接口

- 首页基于选中的 `01.png` 设计。游客首页展示平台功能和数据库中可公开的待接单悬赏标题。登录后，首页从悬赏、技能服务和社区接口加载实际记录。
- 学生端包含悬赏大厅、技能服务、社区、全站搜索、私信、通知、账户资料、个人中心、钱包、交易订单、申诉、评价与举报。受保护页面在登录后返回原入口。
- 管理员登录后访问 `/admin`，可使用仪表盘、订单管理、申诉仲裁、举报处理、用户管理、资金监管和审计日志。
- 私信从 `GET /api/chat/conversations` 加载已保存的会话，换浏览器后也能找到离线期间收到的消息。选中联系人后，通过 `GET /api/chat/history?withUserId={id}` 加载该会话的记录。

接口字段、权限和状态以 [用户端后端接口文档](../docs/用户端后端接口文档.md) 和实际后端实现为准。

## 后端配置与数据库升级

启动 Spring Boot 前，提供 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 和 `JWT_SECRET`，或使用后端提供的 `application-local.yml.example` 创建本地配置并启用 `local` profile。

需要初始化管理员时，同时提供 `BOUNTY_ADMIN_USERNAME` 和 `BOUNTY_ADMIN_PASSWORD`。演示账号只在 `local` 或 `test` profile 且未指定初始化账号时创建。

新数据库先创建 `campus_bounty`，再执行 [schema.sql](../campus-bounty-user-backend/src/main/resources/db/schema.sql)。Compose 在空 MySQL 卷首次启动时自动完成这两步。

升级已有项目数据库前，停止写入并备份。确认当前连接指向待升级的 `campus_bounty` 实例。已有 Compose 卷使用同样的顺序。

1. 执行 [schema.sql](../campus-bounty-user-backend/src/main/resources/db/schema.sql) 补齐基线表。该脚本固定选择 `campus_bounty`，仅用于此项目数据库。
2. 如仍有旧版负额 `PAY` 流水，执行 [历史流水迁移](../campus-bounty-user-backend/src/main/resources/db/migrations/2026-11-17-wallet-available-delta.sql)，将其可用余额变动置零，并在备注保留结算金额。
3. 执行 [管理表结构迁移](../campus-bounty-user-backend/src/main/resources/db/migrations/2026-11-18-shared-admin-schema.sql)，补齐 `t_report` 处置字段，从旧版独立表回填处置数据并复制审计记录。复制的记录使用新 ID，超过 500 字符的详情可在原表查阅。
4. 执行 [举报通知迁移](../campus-bounty-user-backend/src/main/resources/db/migrations/2026-11-18-report-notification-content.sql)，扩展通知内容列以容纳处理人和完整意见。
5. 执行 [交易与钱包迁移](../campus-bounty-user-backend/src/main/resources/db/migrations/2026-11-18-trade-wallet-safeguards.sql)，增加下单请求标识及其唯一约束，并扩大钱包金额相关列的精度。
6. 执行 [悬赏发布幂等迁移](../campus-bounty-user-backend/src/main/resources/db/migrations/2026-11-18-bounty-publish-idempotency.sql)，为悬赏发布增加请求标识及其唯一约束。
