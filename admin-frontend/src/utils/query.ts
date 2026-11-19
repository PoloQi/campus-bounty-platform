/**
 * 查询参数清洗。
 *
 * 空字符串参数是线上最容易被漏掉的一类故障：
 *   buyerId='' → Long 转换失败 → HTTP 400 + 40000
 *   from=''    → LocalDate 解析失败 → HTTP 400 + 40000
 * 所以清洗只在一个地方做（http.ts 的请求拦截器），页面可以放心地留空。
 */
export function cleanParams<T extends Record<string, unknown>>(params: T | undefined): Record<string, unknown> | undefined {
  if (!params) return params
  const result: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(params)) {
    if (value === null || value === undefined) continue
    if (typeof value === 'string') {
      const trimmed = value.trim()
      if (trimmed === '') continue
      result[key] = trimmed
      continue
    }
    result[key] = value
  }
  return result
}

/** 把筛选对象转成 URL query（只保留非空项） */
export function toQueryRecord(filters: Record<string, unknown>): Record<string, string> {
  const query: Record<string, string> = {}
  for (const [key, value] of Object.entries(filters)) {
    if (value === null || value === undefined) continue
    const text = typeof value === 'string' ? value.trim() : String(value)
    if (text === '') continue
    query[key] = text
  }
  return query
}

/** URL query 里的 page 是 0 基，非法值一律回落到 0 */
export function parsePage(raw: unknown): number {
  const text = Array.isArray(raw) ? raw[0] : raw
  const value = Number(text)
  if (!Number.isFinite(value) || value <= 0) return 0
  return Math.floor(value)
}

/** 只接受站内路径，防开放重定向 */
export function safeRedirect(raw: unknown, fallback = '/dashboard'): string {
  const text = Array.isArray(raw) ? raw[0] : raw
  if (typeof text !== 'string') return fallback
  if (!text.startsWith('/') || text.startsWith('//')) return fallback
  if (text.includes('://') || text.toLowerCase().includes('javascript:')) return fallback
  if (text === '/login') return fallback
  return text
}
