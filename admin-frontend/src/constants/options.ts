/**
 * 枚举 → 中文文案 / 视觉基调 的唯一映射。
 *
 * 所有字典都用 `satisfies Record<枚举, OptionEntry>` 声明，
 * 后端新增枚举值时这里会直接编译报错，不会静默漏文案。
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
} from '@/types/enums'

export type Tone = 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'muted'

export interface OptionEntry {
  label: string
  tone: Tone
}

export interface SelectOption {
  label: string
  value: string
}

const orderStatusOptions = {
  PENDING: { label: '待接单', tone: 'info' },
  IN_PROGRESS: { label: '进行中', tone: 'primary' },
  CONFIRMING: { label: '待确认', tone: 'primary' },
  DISPUTED: { label: '纠纷中', tone: 'danger' },
  COMPLETED: { label: '已完成', tone: 'success' },
  CANCELLED: { label: '已取消', tone: 'muted' },
} satisfies Record<OrderStatus, OptionEntry>

const postTypeOptions = {
  BOUNTY: { label: '需求悬赏', tone: 'info' },
  SERVICE: { label: '技能服务', tone: 'primary' },
} satisfies Record<PostType, OptionEntry>

const userRoleOptions = {
  STUDENT: { label: '学生', tone: 'info' },
  ADMIN: { label: '管理员', tone: 'warning' },
} satisfies Record<UserRole, OptionEntry>

const userStatusOptions = {
  ACTIVE: { label: '正常', tone: 'success' },
  BANNED: { label: '已封禁', tone: 'danger' },
} satisfies Record<UserStatus, OptionEntry>

const reportStatusOptions = {
  PENDING: { label: '待处理', tone: 'warning' },
  HANDLED: { label: '已处理', tone: 'success' },
} satisfies Record<ReportStatus, OptionEntry>

const reportResultOptions = {
  VALID: { label: '成立', tone: 'danger' },
  REJECTED: { label: '驳回', tone: 'muted' },
} satisfies Record<ReportResult, OptionEntry>

const reportTargetTypeOptions = {
  POST: { label: '社区帖子', tone: 'info' },
  TRADE_POST: { label: '交易帖', tone: 'info' },
  USER: { label: '用户', tone: 'info' },
} satisfies Record<ReportTargetType, OptionEntry>

const moderationActionOptions = {
  DELETE_POST: { label: '删除帖子', tone: 'danger' },
  CLOSE_TRADE_POST: { label: '下架交易帖', tone: 'danger' },
  BAN_USER: { label: '封禁用户', tone: 'danger' },
} satisfies Record<ModerationAction, OptionEntry>

const appealStatusOptions = {
  OPEN: { label: '待裁决', tone: 'warning' },
  RESOLVED: { label: '已裁决', tone: 'success' },
  WITHDRAWN: { label: '已撤回', tone: 'muted' },
} satisfies Record<AppealStatus, OptionEntry>

const appealOutcomeOptions = {
  RELEASE_TO_SELLER: { label: '打款给卖家', tone: 'primary' },
  REFUND_TO_BUYER: { label: '退款给买家', tone: 'warning' },
} satisfies Record<AppealOutcome, OptionEntry>

const auditActionOptions = {
  HANDLE_REPORT: { label: '处理举报', tone: 'warning' },
  DELETE_POST: { label: '删除帖子', tone: 'danger' },
  CLOSE_TRADE_POST: { label: '下架交易帖', tone: 'danger' },
  BAN_USER: { label: '封禁用户', tone: 'danger' },
  UNBAN_USER: { label: '解封用户', tone: 'success' },
  ARBITRATE_APPEAL: { label: '申诉仲裁', tone: 'primary' },
} satisfies Record<AuditAction, OptionEntry>

const auditTargetTypeOptions = {
  POST: { label: '社区帖子', tone: 'info' },
  TRADE_POST: { label: '交易帖', tone: 'info' },
  USER: { label: '用户', tone: 'info' },
  REPORT: { label: '举报', tone: 'info' },
  APPEAL: { label: '申诉', tone: 'info' },
} satisfies Record<AuditTargetType, OptionEntry>

const walletLogTypeOptions = {
  RECHARGE: { label: '模拟充值', tone: 'success' },
  FREEZE: { label: '冻结', tone: 'warning' },
  UNFREEZE: { label: '解冻退款', tone: 'info' },
  PAY: { label: '托管结算', tone: 'primary' },
  INCOME: { label: '订单收入', tone: 'success' },
} satisfies Record<WalletLogType, OptionEntry>

const timelineTypeOptions = {
  RECHARGE: { label: '模拟充值', tone: 'success' },
  FREEZE: { label: '冻结', tone: 'warning' },
  UNFREEZE: { label: '解冻退款', tone: 'info' },
  PAY: { label: '托管结算', tone: 'primary' },
  INCOME: { label: '订单收入', tone: 'success' },
  BOUNTY_PUBLISHED: { label: '发布悬赏', tone: 'primary' },
  ORDER_CREATED: { label: '购买服务', tone: 'primary' },
  ORDER_ACCEPTED: { label: '接单', tone: 'primary' },
  ORDER_DELIVERED: { label: '标记交付', tone: 'primary' },
  BOUNTY_EXPIRED: { label: '接单截止', tone: 'muted' },
  ORDER_COMPLETED: { label: '确认完成', tone: 'success' },
  ORDER_CANCELLED: { label: '取消订单', tone: 'muted' },
  APPEAL_CREATED: { label: '发起申诉', tone: 'danger' },
  APPEAL_WITHDRAWN: { label: '撤回申诉', tone: 'muted' },
  APPEAL_RESOLVED: { label: '平台裁决', tone: 'success' },
} satisfies Record<TimelineType, OptionEntry>

/** StatusTag 可用的字典名 */
export type DictKind =
  | 'orderStatus'
  | 'postType'
  | 'userRole'
  | 'userStatus'
  | 'reportStatus'
  | 'reportResult'
  | 'reportTargetType'
  | 'moderationAction'
  | 'appealStatus'
  | 'appealOutcome'
  | 'auditAction'
  | 'auditTargetType'
  | 'walletLogType'
  | 'timelineType'

export const DICTS: Record<DictKind, Record<string, OptionEntry>> = {
  orderStatus: orderStatusOptions,
  postType: postTypeOptions,
  userRole: userRoleOptions,
  userStatus: userStatusOptions,
  reportStatus: reportStatusOptions,
  reportResult: reportResultOptions,
  reportTargetType: reportTargetTypeOptions,
  moderationAction: moderationActionOptions,
  appealStatus: appealStatusOptions,
  appealOutcome: appealOutcomeOptions,
  auditAction: auditActionOptions,
  auditTargetType: auditTargetTypeOptions,
  walletLogType: walletLogTypeOptions,
  timelineType: timelineTypeOptions,
}

/** 取文案，未命中回退原值（后端新增枚举时不至于整列空白） */
export function dictLabel(kind: DictKind, value?: string | null): string {
  if (value == null || value === '') return '—'
  return DICTS[kind][value]?.label ?? value
}

export function dictTone(kind: DictKind, value?: string | null): Tone {
  if (value == null || value === '') return 'muted'
  return DICTS[kind][value]?.tone ?? 'info'
}

/** 下拉选项（保持声明顺序） */
function toSelectOptions(dict: Record<string, OptionEntry>): SelectOption[] {
  return Object.entries(dict).map(([value, entry]) => ({ value, label: entry.label }))
}

export const orderStatusSelect = toSelectOptions(orderStatusOptions)
export const postTypeSelect = toSelectOptions(postTypeOptions)
export const userStatusSelect = toSelectOptions(userStatusOptions)
export const reportStatusSelect = toSelectOptions(reportStatusOptions)
export const reportTargetTypeSelect = toSelectOptions(reportTargetTypeOptions)
export const appealStatusSelect = toSelectOptions(appealStatusOptions)
export const auditActionSelect = toSelectOptions(auditActionOptions)
export const walletLogTypeSelect = toSelectOptions(walletLogTypeOptions)
export const reportResultSelect = toSelectOptions(reportResultOptions)
export const appealOutcomeSelect = toSelectOptions(appealOutcomeOptions)
