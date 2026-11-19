/**
 * 列表筛选条件类型。
 *
 * 一律用字符串：筛选条件要同时活在三处 —— 表单控件（v-model 只认字符串）、
 * URL query、axios params。用 number 会在「空值」上立刻出问题：
 * buyerId 清空后是 null 还是 ''，直接决定后端是「不筛选」还是「400 + 40000」。
 * 需要数字的地方在提交前显式校验一次（见 OrderListView 的 validate）。
 */
import type { PageQuery } from './api'

export interface OrderFilters extends Record<string, string> {
  /** OrderStatus，空串表示全部 */
  status: string
  /** PostType，空串表示全部 */
  type: string
  /** 出资方用户 ID（模糊查询不支持，必须是精确 ID） */
  buyerId: string
  /** 接单方用户 ID */
  sellerId: string
  /** yyyy-MM-dd */
  from: string
  /** yyyy-MM-dd */
  through: string
}

export type OrderQuery = OrderFilters & PageQuery
