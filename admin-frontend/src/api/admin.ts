/**
 * 管理端接口。这里唯一允许出现接口路径字符串。
 * 契约以 campus-bounty-user-backend 源码为准（AdminController 17 个 + AdminAppealController 3 个）。
 */
import { http } from './http'
import type { BackendPage, Paged } from '@/types/api'
import type { Dashboard, HandleReportBody, ModerationTarget, OrderDetail, OrderRow, ReportRow } from '@/types/domain'
import type { OrderQuery, ReportQuery } from '@/types/query'
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
