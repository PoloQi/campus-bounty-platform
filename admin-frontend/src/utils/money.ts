/** 金额格式化：统一两位小数 + 千分位，绝不使用 toFixed（丢千分位且有舍入坑） */
const formatter = new Intl.NumberFormat('zh-CN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

export function formatMoney(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return '—'
  // 后端 BigDecimal 减法可能留下 -0.00
  const normalized = Object.is(value, -0) ? 0 : value
  return `¥${formatter.format(normalized)}`
}

/** 带符号金额：负数输出 -¥30.00，正数输出 +¥30.00 */
export function formatSignedMoney(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return '—'
  const normalized = Object.is(value, -0) ? 0 : value
  if (normalized === 0) return formatMoney(0)
  return `${normalized > 0 ? '+' : '-'}${formatMoney(Math.abs(normalized))}`
}

export function isNegative(value: number | null | undefined): boolean {
  return typeof value === 'number' && value < 0
}

/** 统计类数值：千分位整数 */
export function formatNumber(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return '—'
  return new Intl.NumberFormat('zh-CN').format(value)
}

/** 差值为 0 的判定：吸收浮点/BigDecimal 残差 */
export function isZeroAmount(value: number | null | undefined): boolean {
  return typeof value === 'number' && Math.abs(value) < 0.005
}
