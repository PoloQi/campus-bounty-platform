/**
 * 枚举的唯一事实来源。这些字面量与后端 Java 枚举/字符串常量一一对应，
 * 改动必须同步后端，否则筛选参数会被后端拒绝（HTTP 400 + 40000）。
 */

export type OrderStatus =
  | 'PENDING'
  | 'IN_PROGRESS'
  | 'CONFIRMING'
  | 'DISPUTED'
  | 'COMPLETED'
  | 'CANCELLED'

export type PostType = 'BOUNTY' | 'SERVICE'

export type UserRole = 'STUDENT' | 'ADMIN'
export type UserStatus = 'ACTIVE' | 'BANNED'

export type ReportStatus = 'PENDING' | 'HANDLED'
export type ReportResult = 'VALID' | 'REJECTED'
export type ReportTargetType = 'POST' | 'TRADE_POST' | 'USER'
export type ModerationAction = 'DELETE_POST' | 'CLOSE_TRADE_POST' | 'BAN_USER'

export type AppealStatus = 'OPEN' | 'RESOLVED' | 'WITHDRAWN'
export type AppealOutcome = 'RELEASE_TO_SELLER' | 'REFUND_TO_BUYER'

export type AuditAction =
  | 'HANDLE_REPORT'
  | 'DELETE_POST'
  | 'CLOSE_TRADE_POST'
  | 'BAN_USER'
  | 'UNBAN_USER'
  | 'ARBITRATE_APPEAL'

export type AuditTargetType = 'POST' | 'TRADE_POST' | 'USER' | 'REPORT' | 'APPEAL'

export type WalletLogType = 'RECHARGE' | 'FREEZE' | 'UNFREEZE' | 'PAY' | 'INCOME'

/** 订单详情时间线的事件类型：流水 5 个 + 交易/申诉事件 */
export type TimelineType =
  | WalletLogType
  | 'BOUNTY_PUBLISHED'
  | 'ORDER_CREATED'
  | 'ORDER_ACCEPTED'
  | 'ORDER_DELIVERED'
  | 'BOUNTY_EXPIRED'
  | 'ORDER_COMPLETED'
  | 'ORDER_CANCELLED'
  | 'APPEAL_CREATED'
  | 'APPEAL_WITHDRAWN'
  | 'APPEAL_RESOLVED'

/** 交易帖状态（详情页 post.status） */
export type TradePostStatus = 'OPEN' | 'LOCKED' | 'CLOSED'
/** 社区帖状态（治理预览 moderation 返回） */
export type CommunityPostStatus = 'VISIBLE' | 'DELETED'
