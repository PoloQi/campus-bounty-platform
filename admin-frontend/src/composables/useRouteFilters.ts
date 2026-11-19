import { computed, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { parsePage, toQueryRecord } from '@/utils/query'

/**
 * 筛选条件 ↔ URL 双向同步。
 *
 * 空值不写入 URL（地址栏不会出现 ?status=），刷新后仍能还原筛选，
 * 从详情页返回列表也保留原条件。
 */
export function useRouteFilters<T extends Record<string, string>>(defaults: T) {
  const route = useRoute()
  const router = useRouter()
  const filters = reactive({ ...defaults }) as T

  const page = computed(() => parsePage(route.query.page))

  function hydrate(): void {
    const target = filters as Record<string, string>
    for (const key of Object.keys(defaults)) {
      const raw = route.query[key]
      const value = Array.isArray(raw) ? raw[0] : raw
      target[key] = typeof value === 'string' ? value : defaults[key]
    }
  }

  function buildQuery(extra?: Record<string, string>): Record<string, string> {
    const query = toQueryRecord(filters as Record<string, unknown>)
    if (extra) Object.assign(query, extra)
    return query
  }

  /** 查询：回到第 0 页 */
  async function apply(): Promise<void> {
    await router.replace({ query: buildQuery() })
  }

  async function goPage(target: number): Promise<void> {
    await router.replace({
      query: buildQuery(target > 0 ? { page: String(target) } : undefined),
    })
  }

  async function reset(): Promise<void> {
    Object.assign(filters, defaults)
    await router.replace({ query: {} })
  }

  return { filters, page, hydrate, apply, goPage, reset }
}
