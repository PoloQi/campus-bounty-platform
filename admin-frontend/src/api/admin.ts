/**
 * 管理端接口。这里唯一允许出现接口路径字符串。
 * 契约以 campus-bounty-user-backend 源码为准（AdminController 17 个 + AdminAppealController 3 个）。
 */
import { http } from './http'
import type { AppealPageRaw, BackendPage, Paged } from '@/types/api'
import type {
  Appeal,
  ArbitrationBody,
  AuditRow,
  Dashboard,
  FundTotals,
  HandleReportBody,
  ModerationTarget,
  OrderDetail,
  OrderRow,
  ReportRow,
  UserRow,
  Wallet,
  WalletLog,
} from '@/types/domain'
import type {
  AppealQuery,
  AuditQuery,
  LedgerQuery,
  OrderQuery,
  ReportQuery,
  UserQuery,
} from '@/types/query'
import type { ReportTargetType } from '@/types/enums'

/**
 * 后端分页统一是 {items,page,hasNext}，没有 total。
 * 归一放在 api 层，页面只认 Paged<T>，不必各自记得字段名。
 */
function toPaged<T>(raw: BackendPage<T> | null | undefined): Paged<T> {
  return {
    items: raw?.items ?? [],
    page: raw?.page ?? 0,
    hasNext: Boolean(raw?.hasNext),
  }
}

export async function fetchDashboard(): Promise<Dashboard> {
  return http.get<Dashboard>('/api/admin/dashboard')
}

/** GET /api/admin/orders —— 空参数由 http 拦截器统一剔除 */
export async function fetchOrders(query: OrderQuery, signal?: AbortSignal): Promise<Paged<OrderRow>> {
  const raw = await http.get<BackendPage<OrderRow>>('/api/admin/orders', query, { signal })
  return toPaged(raw)
}

/** GET /api/admin/orders/{id} —— 订单不存在时后端返回 40000（不是 404） */
export async function fetchOrderDetail(id: number, signal?: AbortSignal): Promise<OrderDetail> {
  return http.get<OrderDetail>(`/api/admin/orders/${id}`, undefined, { signal })
}

/* ───────────────────────── 举报处理 ───────────────────────── */

/**
 * GET /api/admin/reports/query
 * 排序由后端决定：待处理优先，其次编号倒序。
 */
export async function fetchReports(query: ReportQuery, signal?: AbortSignal): Promise<Paged<ReportRow>> {
  const raw = await http.get<BackendPage<ReportRow>>('/api/admin/reports/query', query, { signal })
  return toPaged(raw)
}

/** GET /api/admin/reports/{id} —— 不存在时是 HTTP 200 + 40000「举报不存在」 */
export async function fetchReport(id: number, signal?: AbortSignal): Promise<ReportRow> {
  return http.get<ReportRow>(`/api/admin/reports/${id}`, undefined, { signal })
}

/**
 * POST /api/admin/reports/{id}/handle
 *
 * 后端有两条硬规则，前端必须提前拦住，否则用户填完表单才吃一个失败：
 *   1. result=REJECTED 时不允许 moderate=true（后端报「驳回举报时不能执行治理操作」）
 *   2. moderate=true 会连带执行治理动作，并写入两条审计（处理举报 + 治理动作）
 */
export async function handleReport(id: number, body: HandleReportBody): Promise<void> {
  await http.post<void>(`/api/admin/reports/${id}/handle`, body)
}

/** GET /api/admin/moderation/{type}/{id} —— 对象不存在时 40000「治理对象不存在」 */
export async function fetchModerationTarget(
  type: ReportTargetType,
  id: number,
  signal?: AbortSignal,
): Promise<ModerationTarget> {
  return http.get<ModerationTarget>(`/api/admin/moderation/${type}/${id}`, undefined, { signal })
}

/* ────────────── 直接治理（不经举报，按编号执行）────────────── */

/** POST /api/admin/posts/{id}/delete —— 社区帖子置为 DELETED，无撤销接口 */
export async function deletePost(id: number): Promise<void> {
  await http.post<void>(`/api/admin/posts/${id}/delete`)
}

/** POST /api/admin/services/{id}/close —— 交易帖置为 CLOSED */
export async function closeTradePost(id: number): Promise<void> {
  await http.post<void>(`/api/admin/services/${id}/close`)
}

/** POST /api/admin/users/{id}/ban —— 仅可治理 STUDENT，管理员账号会被后端拒绝 */
export async function banUser(id: number): Promise<void> {
  await http.post<void>(`/api/admin/users/${id}/ban`)
}

/* ───────────────────────── 用户管理 ───────────────────────── */

/**
 * GET /api/admin/users
 *
 * 三个条件的匹配方式不一样，猜错就会「筛不出来还以为是没数据」：
 *   - keyword：username / nickname 走 like 模糊（后端已 lower()，大小写不敏感），
 *     同一个值还兼作编号精确匹配（cast(id as char(20)) = keyword）。
 *     两者是 **OR** 关系 —— 输 "17" 会同时带出 #17 和用户名里含 "17" 的账号
 *     （实测：user_c_1794898853131 也会被命中）。页面对纯数字关键词做了置顶补偿。
 *   - status：精确相等。传枚举外的值不报错，只会安静地返回空列表。
 *   - 排序固定 id desc，最新注册的在最前。
 *
 * 越界页与订单列表表现一致：items 为空、hasNext 为 false，page 原样回显。
 */
export async function fetchUsers(query: UserQuery, signal?: AbortSignal): Promise<Paged<UserRow>> {
  const raw = await http.get<BackendPage<UserRow>>('/api/admin/users', query, { signal })
  return toPaged(raw)
}

/**
 * POST /api/admin/users/{id}/unban
 *
 * 与封禁对称，后端同样只放行 STUDENT（对管理员调用会返回 40000「只可治理学生账号」）。
 * 解封只是把 status 置回 ACTIVE，不会恢复此前被删除的帖子或关闭的交易帖。
 */
export async function unbanUser(id: number): Promise<void> {
  await http.post<void>(`/api/admin/users/${id}/unban`)
}

/* ───────────────────────── 资金监管 ───────────────────────── */

/**
 * GET /api/admin/funds —— 托管资金核对
 *
 * frozen     = 所有钱包 frozen 之和
 * expected   = 状态在「待接单 / 进行中 / 待确认 / 纠纷中」的订单金额之和
 * difference = frozen - expected，正常应为 0
 *
 * 后端不做任何修正，只是把两个数摆出来；差额的解读放在页面上。
 */
export async function fetchFunds(signal?: AbortSignal): Promise<FundTotals> {
  return http.get<FundTotals>('/api/admin/funds', undefined, { signal })
}

/**
 * GET /api/admin/funds/wallets/{userId}
 *
 * 后端是 findByUserId(...).orElse(null) —— 用户不存在与用户没钱包都返回 data:null，
 * 且都是 code=0。所以调用方拿到 null 时不能断言「用户不存在」。
 */
export async function fetchWallet(userId: number, signal?: AbortSignal): Promise<Wallet | null> {
  return http.get<Wallet | null>(`/api/admin/funds/wallets/${userId}`, undefined, { signal })
}

/** GET /api/admin/funds/ledger —— 流水按 id desc，白名单内的类型才可筛 */
export async function fetchLedger(query: LedgerQuery, signal?: AbortSignal): Promise<Paged<WalletLog>> {
  const raw = await http.get<BackendPage<WalletLog>>('/api/admin/funds/ledger', query, { signal })
  return toPaged(raw)
}

/* ───────────────────────── 申诉仲裁 ───────────────────────── */

/**
 * GET /api/admin/appeals
 *
 * 这是全站唯一一套不同的分页结构：{items, page, totalPages}，没有 hasNext。
 * 所以在这里把 totalPages 反推成 hasNext，页面继续只认 Paged<T>。
 *
 * 两个反直觉点（已实测）：
 *   - 空结果时 totalPages = 0（不是 1），分页器不能显示「第 1 / 0 页」；
 *   - page 越界（如 page=99）后端不夹取，会返回 {items:[], page:99, totalPages:1}。
 */
export async function fetchAppeals(query: AppealQuery, signal?: AbortSignal): Promise<Paged<Appeal>> {
  const raw = await http.get<AppealPageRaw<Appeal>>('/api/admin/appeals', query, { signal })
  const page = raw?.page ?? 0
  const totalPages = raw?.totalPages ?? 0
  return {
    items: raw?.items ?? [],
    page,
    totalPages,
    hasNext: totalPages > 0 && page + 1 < totalPages,
  }
}

/** GET /api/admin/appeals/{id} —— 不存在时 HTTP 200 + 40000「申诉不存在」 */
export async function fetchAppeal(id: number, signal?: AbortSignal): Promise<Appeal> {
  return http.get<Appeal>(`/api/admin/appeals/${id}`, undefined, { signal })
}

/**
 * POST /api/admin/appeals/{id}/arbitrate
 *
 * 后端的硬规则（都是 HTTP 200 + 40000，不会给 4xx）：
 *   - 申诉已不是 OPEN → 「申诉已结束」（别的管理员刚裁决，或学生已撤回）
 *   - 关联订单已不在 DISPUTED → 「订单当前不在纠纷中」
 *   - comment 为空或超过 500 字 → 直接拒绝
 *
 * 裁决不可撤销，且会真实移动托管款：
 *   RELEASE_TO_SELLER → 订单 COMPLETED，托管款结算给卖家
 *   REFUND_TO_BUYER   → 订单 CANCELLED，托管款退回买家
 */
export async function arbitrateAppeal(id: number, body: ArbitrationBody): Promise<Appeal> {
  return http.post<Appeal>(`/api/admin/appeals/${id}/arbitrate`, body)
}

/* ───────────────────────── 审计日志 ───────────────────────── */

/**
 * GET /api/admin/audit
 *
 * 只有四个筛选条件，且都是精确匹配：
 *   - adminId：审计表只落 admin_id，没有冗余操作人名字，所以前端也只能按编号查；
 *   - action：审计动作，传枚举外的值不报错，安静返回空列表；
 *   - from / through：按 created_at 卡日期（含首尾两天），through 早于 from 时后端报 40000。
 *
 * 排序固定 id desc（最新在前）。分页结构与订单/举报/用户一致：{items, page, hasNext}。
 *
 * 两条容易误读的语义，页面必须说明，否则会被当成「日志丢数据」：
 *   1. 只有写操作才留痕（删帖、下架交易帖、封禁、解封、处置举报、申诉裁决），
 *      登录与查看不留痕；
 *   2. 审计写入与业务在同一个事务里（propagation = MANDATORY），操作失败会一起回滚，
 *      所以「有记录」即代表「当时执行成功」，不存在失败记录。
 */
export async function fetchAudit(query: AuditQuery, signal?: AbortSignal): Promise<Paged<AuditRow>> {
  const raw = await http.get<BackendPage<AuditRow>>('/api/admin/audit', query, { signal })
  return toPaged(raw)
}
