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

export interface ReportFilters extends Record<string, string> {
  /** ReportStatus PENDING/HANDLED，空串表示全部 */
  status: string
  /** ReportTargetType POST/TRADE_POST/USER，空串表示全部 */
  targetType: string
}

export type ReportQuery = ReportFilters & PageQuery

export interface AppealFilters extends Record<string, string> {
  /** AppealStatus OPEN/RESOLVED/WITHDRAWN，空串表示全部 */
  status: string
}

export type AppealQuery = AppealFilters & PageQuery

export interface UserFilters extends Record<string, string> {
  /**
   * 一个输入框兼两种匹配方式（后端 SQL 就是这么写的）：
   * 用户名 / 昵称走 like 模糊，用户编号走精确相等。
   * 所以输 "12" 只命中 #12，不会把用户名里带 12 的账号一并捞出来。
   */
  keyword: string
  /** UserStatus ACTIVE/BANNED，空串表示全部 */
  status: string
}

export type UserQuery = UserFilters & PageQuery

export interface LedgerFilters extends Record<string, string> {
  /** 用户 ID，精确匹配；空串表示全部用户 */
  userId: string
  /** WalletLogType，空串表示全部类型 */
  type: string
  /** 关联订单 ID，精确匹配；不传则不限订单 */
  orderId: string
  /** yyyy-MM-dd */
  from: string
  /** yyyy-MM-dd */
  through: string
}

export type LedgerQuery = LedgerFilters & PageQuery
