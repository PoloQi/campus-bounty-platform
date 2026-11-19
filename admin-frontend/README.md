# 管理台前端（admin-frontend）

校园悬赏平台管理后台，Vue 3 + Vite + TypeScript + Element Plus + ECharts。
蓝白配色、全浅色侧栏，与 `../frontend/`（学生端 + 旧管理端，原生 JS）**完全独立**。

## 本地启动

```bash
# 1. 先起后端（8080）
cd ../campus-bounty-user-backend
./mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local

# 2. 再起前端（5174）
cd ../admin-frontend
npm install
npm run dev
```

打开 http://localhost:5174 ，用 `admin` / `admin123` 登录（该账号由后端 local、test profile 初始化）。

## 端口与代理

| 项 | 值 |
|---|---|
| 前端 dev 端口 | `5174`（`strictPort`，与旧前端 5173 并存） |
| 代理目标 | `http://localhost:8080`，可用 `ADMIN_API_ORIGIN` 覆盖 |
| 代理前缀 | `/api`、`/uploads`、`/ws`（`ws: true`） |

后端**没有配置 CORS**，浏览器直连 8080 会被拦，所有请求必须走代理。

## 脚本

| 命令 | 说明 |
|---|---|
| `npm run dev` | 开发服务器 |
| `npm run build` | 生产构建（产物 `dist/`） |
| `npm run preview` | 预览构建产物 |
| `npm run type-check` | `vue-tsc` 类型检查 |

## 约定（改代码前先看）

1. **所有接口路径只写在 `src/api/admin.ts`**，页面不出现裸 URL。
2. **后端业务失败是 HTTP 200 + `code=40000`**，请求层已归一成 `AppError('biz')`；页面就地提示，不要弹全局窗。
3. **列表分页没有 total**（`{items,page,hasNext}`），申诉是另一套（`{items,page,totalPages}`）。两套已在 `api` 层归一为 `Paged<T>`，因此**不使用 `el-pagination`**。
4. **金额是 JSON number，时间是无时区字符串**。格式化一律走 `utils/money.ts` 与 `utils/datetime.ts`，不要 `new Date()` 或 `toFixed()`。
5. **一级对象（订单 / 举报 / 申诉）用独立路由页**，附属对象（钱包、治理摘要、审计长文本）用抽屉。
6. 字典与文案集中在 `src/constants/options.ts`，用 `satisfies Record<Enum, ...>` 保证枚举漏项时编译失败。

## 目录

```
src/
├─ api/          http.ts（拦截器与错误归一）、admin.ts（20 个端点）、auth.ts
├─ types/        api.ts（分页包装）、domain.ts（DTO）、enums.ts（枚举字面量）
├─ constants/    options.ts（字典）、menu.ts（侧栏）
├─ router/       路由表与守卫
├─ stores/       auth（会话）、ui（侧栏折叠）
├─ layouts/      AdminLayout / AppSidebar / AppHeader
├─ components/   通用组件（表格壳、分页、状态标签、金额、时间、空/失败态…）
├─ composables/  usePagedQuery / useRouteFilters / useConfirm / useAsyncAction / useEChart
├─ views/        登录、仪表盘、订单、申诉、举报、用户、资金、审计、错误页
├─ utils/        money / datetime / errors / query / session / echarts
└─ styles/       tokens（设计令牌）、element（EP 主题覆盖）、layout（布局与组件样式）
```

## 交付进度

- [x] **M0** 工程基座（骨架 / 主题 / 布局 / 登录 / 请求层 / 公共组件）
- [ ] M1 订单（列表 + 详情）
- [ ] M2 仪表盘（KPI + 图表）
- [ ] M3 举报
- [ ] M4 申诉
- [ ] M5 用户
- [ ] M6 资金
- [ ] M7 审计
- [ ] M8 收口

详细验收清单见 `../../管理端前端开发计划书.md`。
