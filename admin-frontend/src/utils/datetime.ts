/**
 * 时间格式化。
 *
 * 后端 LocalDateTime 序列化成无时区字符串 "2026-11-18T09:12:33"。
 * 一律做纯字符串处理，不构造 Date —— new Date(无时区串) 在不同环境下会被按
 * 本地/UTC 解析，再经 toISOString() 或指定 timeZone 就会偏移 8 小时。
 */

const EMPTY = '—'

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return EMPTY
  const normalized = value.replace('T', ' ')
  return normalized.slice(0, 16)
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return EMPTY
  return value.slice(0, 10)
}

/** 看板折线图 x 轴：2026-11-18 → 11/18 */
export function formatMonthDay(value: string | null | undefined): string {
  if (!value) return ''
  const part = value.slice(5, 10)
  return part.replace('-', '/')
}

/** 相对日期区间（用于日期选择器的快捷项，M1 起使用） */
export function toDateString(date: Date): string {
  const y = date.getFullYear()
  const m = `${date.getMonth() + 1}`.padStart(2, '0')
  const d = `${date.getDate()}`.padStart(2, '0')
  return `${y}-${m}-${d}`
}

export function today(): string {
  return toDateString(new Date())
}

export function daysAgo(days: number): string {
  const date = new Date()
  date.setDate(date.getDate() - days)
  return toDateString(date)
}
