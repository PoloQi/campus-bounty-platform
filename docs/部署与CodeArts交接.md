# 部署与 CodeArts 交接

## 单机启动

使用 Docker 和 Compose v2。在项目根目录把 `.env.example` 复制为 `.env`，并填写以下变量。Compose 会在启动前检查必填值。

| 变量 | 用途 |
|---|---|
| `MYSQL_ROOT_PASSWORD` | MySQL 管理口令，供建库和维护使用 |
| `DB_PASSWORD` | 应用专用 `campus_bounty` 数据库账号口令 |
| `JWT_SECRET` | 至少 32 字节的随机 JWT 签名密钥 |
| `BOUNTY_ADMIN_USERNAME` | 初始化管理员的用户名，最长 20 个字符 |
| `BOUNTY_ADMIN_PASSWORD` | 管理员初始密码，12 至 32 个字符，且不超过 72 个 UTF-8 字节 |
| `FRONTEND_PORT` | 可选，默认 `5173` |

```powershell
Copy-Item .env.example .env
# 填好 .env 后执行
docker compose up -d --build
docker compose ps
```

访问 `http://127.0.0.1:5173/`，用初始化账号登录并打开 `/admin`。MySQL 和 Spring Boot 只加入 Compose 内部网络。Nginx 同源代理 API、图片和私信 WebSocket，MySQL 数据和上传文件分别保存在命名卷中。

空 MySQL 卷首次启动时自动执行 `schema.sql`。复用现有数据库前，按 [数据库升级说明](../frontend/README.md#后端配置与数据库升级)准备备份并执行对应迁移。`docker compose down` 保留命名卷。

应用在启动时按环境变量创建管理员。重复启动时已有同名管理员沿用原密码，已有同名学生会使启动失败。非 Compose 启动也可通过这两个环境变量指定管理员。

## CodeArts 交接

以同一 Git 提交构建后端和前端镜像，并按提交标识打版本标签。将镜像发布到目标项目的镜像仓库，将 `compose.yaml`、数据库初始化脚本和本说明交给部署任务。

在 CodeArts 的目标环境中以受保护变量注入数据库、JWT 和管理员配置，并设置 `BOUNTY_BACKEND_IMAGE` 与 `BOUNTY_FRONTEND_IMAGE` 为对应的镜像标签。目标主机配置镜像仓库访问权限后，执行 `docker compose pull mysql backend frontend` 和 `docker compose up -d --no-build`。对已有环境升级时先完成数据库备份与迁移。需要从主机外访问时，通过主机的 HTTPS 入口转发到 Compose 暴露的本地端口。

验收时记录 CodeArts 构建与部署任务、Git 提交、镜像标签、目标环境和冒烟结果。冒烟覆盖首页、学生登录、管理员登录、图片上传与私信连接。本文件是可执行交接说明，当前仓库尚无实际 CodeArts 运行记录。
