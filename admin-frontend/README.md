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
7. **三态固定**：加载走骨架、空态走 `EmptyState`（翻过头 / 筛不到 / 平台无数据三分支）、失败走 `ErrorState`（可重试）。**后端不可用时绝不渲染成 0 或空表**。
8. 样式按页拆文件（`funds.css` / `audit.css` …），公共块只放 `layout.css` 与 `data.css`；新增页面样式记得在 `styles/index.css` 里 `@import`。

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
└─ styles/       tokens（令牌）、element（EP 覆盖）、layout / data（公共）、按页样式（dashboard / reports / appeals / users / funds / audit）
```

## 交付进度

- [x] **M0** 工程基座（骨架 / 主题 / 布局 / 登录 / 请求层 / 公共组件）
- [x] **M1** 订单（列表 + 详情）
- [x] **M2** 仪表盘（KPI + 图表）
- [x] **M3** 举报
- [x] **M4** 申诉
- [x] **M5** 用户
- [x] **M6** 资金
- [x] **M7** 审计
- [x] **M8** 收口（三态巡检 / 并发 401 / 404 入布局 / 体积基线 / 覆盖对照表）

## 构建基线

`npm run build` 于 2026-11-19 实测，产物 `dist/` 共 46 个文件（35 JS + 11 CSS），未压缩合计 1.41 MB。

| 产物 | 原始 | gzip | 说明 |
|---|---|---|---|
| `index-*.js`（入口 + 公共依赖） | 228 KB | 85 KB | 所有页面共用，含 Vue / Router / Pinia / axios |
| `index-*.css` | 91 KB | 14 KB | 设计令牌 + 布局 + 各页样式 |
| `vendor-echarts-*.js` | 501 KB | 169 KB | **仅仪表盘按需加载**，其他页不下载 |
| `el-table-column-*.js` | 133 KB | 46 KB | Element Plus 表格，列表页共用 |
| `el-date-picker-*.js` | 105 KB | 29 KB | 有日期筛选的页面才加载 |
| 各页面 chunk | 9–23 KB | 4–9 KB | 按路由懒加载，单页约 5 KB gzip |
| **JS + CSS 合计** | — | **465 KB** | gzip 后总量 |

体积守卫：ECharts 通过 `manualChunks` 单独成 chunk，仪表盘自身 chunk 只有 8.8 KB；不引入全量 Element Plus，组件与样式按需。

## 相关文档

- 旧 `/admin` 功能点覆盖对照表：[`../docs/管理端前端-旧版功能覆盖对照表.md`](../docs/管理端前端-旧版功能覆盖对照表.md)
- 接口契约以 `../campus-bounty-user-backend` 源码为准（`AdminController` 17 个 + `AdminAppealController` 3 个）
