# 校园悬赏平台前端

## 本地运行

在 `frontend` 目录运行 `node server.mjs`，然后访问 http://127.0.0.1:5173/。需要 Node.js 18 或更新版本。前端无需安装 npm 依赖。

Node 服务将 `/api/*`、`/uploads/*` 和 `/ws/chat` 代理到 Spring Boot。默认后端地址是 `http://127.0.0.1:8080`，可以通过 `BOUNTY_API_ORIGIN` 修改。使用 `FRONTEND_PORT` 调整前端端口。启动后端时按项目配置提供数据库连接和 JWT 配置。

PowerShell：

```powershell
$env:BOUNTY_API_ORIGIN = 'http://127.0.0.1:8080'
node server.mjs
```

## 页面与接口

- 首页基于选中的 `01.png` 设计。游客首页展示平台功能和数据库中可公开的待接单悬赏标题。登录后，首页从悬赏、技能服务和社区接口加载实际记录。
- 学生端包含悬赏大厅、技能服务、社区、全站搜索、私信、通知、账户资料、个人中心、钱包、交易订单、申诉、评价与举报。受保护页面在登录后返回原入口。
- 管理员登录后访问 `/admin`，可使用仪表盘、订单管理、申诉仲裁、举报处理、用户管理、资金监管和审计日志。
- 后端当前未提供历史会话列表。私信历史由对应用户的聊天记录接口加载，近期联系人保存在当前浏览器并按账号区分。

接口字段、权限和状态以 [用户端后端接口文档](../docs/用户端后端接口文档.md) 和实际后端实现为准。

## 后端配置与数据库升级

启动 Spring Boot 前，提供 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 和 `JWT_SECRET`，或使用后端提供的 `application-local.yml.example` 创建本地配置并启用 `local` profile。

- 新数据库先创建 `campus_bounty`，再执行 [schema.sql](../campus-bounty-user-backend/src/main/resources/db/schema.sql)。
- 现有数据库升级时，在启动新版后端前对 `campus_bounty` 执行同一份建表脚本以补齐管理表，再执行 [历史流水迁移](../campus-bounty-user-backend/src/main/resources/db/migrations/2026-11-17-wallet-available-delta.sql)。迁移可重复执行，会把旧 `PAY` 流水的可用余额变动置零，并在备注保留结算金额。
