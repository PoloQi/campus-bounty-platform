/**
 * 管理端接口。这里唯一允许出现接口路径字符串。
 * 契约以 campus-bounty-user-backend 源码为准（AdminController 17 个 + AdminAppealController 3 个）。
 */
import { http } from './http'
import type { BackendPage, Paged } from '@/types/api'
import type { Dashboard, OrderDetail, OrderRow } from '@/types/domain'
import type { OrderQuery } from '@/types/query'

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
