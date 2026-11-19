/**
 * 后端 DTO 的镜像类型。以 campus-bounty-user-backend 源码为准，不要照旧文档改。
 *
 * ── 字段名陷阱对照（写错会静默变 undefined，不报错）────────────────────
 *   详情里的流水数组   → logs          （不是 walletLogs）
 *   时间线/审计的详情  → details       （不是 detail / content）
 *   冻结核对三项       → frozen / expected / difference
 *                                       （不是 frozenTotal / inFlightAmount / diff）
 *   看板趋势的日期键   → orderDay      （不是 day / date）
 *   流水的变动后余额   → balanceAfter  （不是 balance）
 *   举报的联动动作     → moderationAction
 *   治理对象摘要       → summary
 * ────────────────────────────────────────────────────────────────────
 *
 * 序列化约定：BigDecimal → JSON number（700.00 会变 700）；LocalDateTime → 无时区字符串
 * "2026-11-18T09:12:33"。所以金额写 number、时间写 string，不要写 Date。
 */
import type {
  AppealOutcome,
  AppealStatus,
  AuditAction,
  AuditTargetType,
  ModerationAction,
  OrderStatus,
  PostType,
  ReportResult,
  ReportStatus,
  ReportTargetType,
  TimelineType,
  UserRole,
  UserStatus,
  WalletLogType,
} from './enums'

/** 登录响应 */
export interface LoginResult {
  token: string
  userId: number
  nickname: string | null
  role: UserRole
}

/** 看板 */
export interface DashboardTotals {
  students: number
  orders: number
  gmv: number
  frozen: number
  openAppeals: number
  pendingReports: number
}

export interface StatusCount {
  status: OrderStatus
  total: number
}

export interface DayCount {
  /** 字段名就是 orderDay，格式 yyyy-MM-dd */
  orderDay: string
  total: number
}

export interface Dashboard {
  totals: DashboardTotals
  /** 固定 6 项，按 OrderStatus 枚举顺序，0 值也在 */
  distribution: StatusCount[]
  /** 固定 7 项，连续升序，末项为今天 */
  trend: DayCount[]
}

/** 订单列表行 */
export interface OrderRow {
  id: number
  type: PostType
  postId: number
  title: string
  buyerId: number
  /** 悬赏未接单时为 null */
  sellerId: number | null
  amount: number
  status: OrderStatus
  createdAt: string
}

/** 用户摘要（订单详情里的 buyer / seller） */
export interface UserRow {
  id: number
  username: string
  nickname: string | null
  role: UserRole
  status: UserStatus
  createdAt: string
}

export interface WalletLog {
  id: number
  userId: number
  type: WalletLogType
  /** 带符号：FREEZE 为负，UNFREEZE/RECHARGE 为正，PAY 为 0 */
  amount: number
  balanceAfter: number
  orderId: number | null
  remark: string | null
  createdAt: string
}

export interface TradeOrder {
  id: number
  postId: number
  buyerId: number
  sellerId: number | null
  amount: number
  status: OrderStatus
  createdAt: string
  updatedAt: string
}

export interface TradePost {
  id: number
  publisherId: number
  type: PostType
  title: string
  description: string
  /** 逗号拼接的图片路径 */
  images: string | null
  price: number
  category: string | null
  deadline: string | null
  status: string
  createdAt: string
}

export interface Appeal {
  id: number
  orderId: number
  appellantId: number
  reason: string
  fromStatus: OrderStatus
  status: AppealStatus
  outcome: AppealOutcome | null
  adminId: number | null
  adminComment: string | null
  createdAt: string
  handledAt: string | null
}

export interface TimelineEvent {
  type: TimelineType
  createdAt: string
  actorId: number | null
  details: string | null
}

/** 订单详情聚合：七块 */
export interface OrderDetail {
  order: TradeOrder
  post: TradePost
  buyer: UserRow | null
  seller: UserRow | null
  /** 注意字段名是 logs */
  logs: WalletLog[]
  appeals: Appeal[]
  timeline: TimelineEvent[]
}

/** 举报列表行 / 详情 */
export interface ReportRow {
  id: number
  targetType: ReportTargetType
  targetId: number
  reporterId: number
  reason: string
  status: ReportStatus
  createdAt: string
  result: ReportResult | null
  comment: string | null
  adminId: number | null
  handledAt: string | null
  moderationAction: ModerationAction | null
}

/** 治理对象预览 */
export interface ModerationTarget {
  id: number
  summary: string
  status: string
}

/** 举报处置请求体 */
export interface HandleReportBody {
  result: ReportResult
  /** 后端限制 1–160 字 */
  comment: string
  moderate?: boolean
}

/** 冻结核对 */
export interface FundTotals {
  frozen: number
  expected: number
  /** frozen - expected，正常应为 0 */
  difference: number
}

export interface Wallet {
  id: number
  userId: number
  balance: number
  frozen: number
}

/** 审计日志行 */
export interface AuditRow {
  id: number
  adminId: number
  action: AuditAction
  targetType: AuditTargetType
  targetId: number
  /** 注意字段名是 details */
  details: string
  createdAt: string
}

/** 仲裁请求体 */
export interface ArbitrationBody {
  outcome: AppealOutcome
  /** 后端限制 1–500 字 */
  comment: string
}
