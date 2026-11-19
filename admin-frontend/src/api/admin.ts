/**
 * 管理端接口。这里唯一允许出现接口路径字符串。
 * 契约以 campus-bounty-user-backend 源码为准（AdminController 17 个 + AdminAppealController 3 个）。
 *
 * M0 只接 dashboard 一个端点验证链路，其余在对应模块补齐。
 */
import { http } from './http'
import type { Dashboard } from '@/types/domain'

export function fetchDashboard(): Promise<Dashboard> {
  return http.get<Dashboard>('/api/admin/dashboard')
}
