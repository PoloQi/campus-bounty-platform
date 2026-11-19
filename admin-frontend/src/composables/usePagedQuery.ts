import { computed, ref, shallowRef, type ComputedRef, type Ref, type ShallowRef } from 'vue'
import type { Paged, PageQuery } from '@/types/api'
import type { AppError } from '@/utils/errors'
import { toAppError } from '@/utils/errors'

export interface PagedQueryResult<T> {
  items: ShallowRef<T[]>
  page: Ref<number>
  loading: Ref<boolean>
  error: Ref<AppError | null>
  totalPages: Ref<number | undefined>
  hasNext: Ref<boolean>
  canPrev: ComputedRef<boolean>
  canNext: ComputedRef<boolean>
  load: (target?: number) => Promise<void>
  go: (target: number) => Promise<void>
  prev: () => Promise<void>
  next: () => Promise<void>
  reload: () => Promise<void>
}

/**
 * 分页内核。两套后端分页结构在 api 层已归一成 Paged<T>，这里只认一套。
 *
 * 内置 AbortController：快速改筛选条件时，旧响应不会覆盖新结果
 * （旧的原生版没有处理这个竞态）。
 */
export function usePagedQuery<T, Q extends Record<string, unknown>>(
  fetcher: (params: Q & PageQuery, signal: AbortSignal) => Promise<Paged<T>>,
  getFilters: () => Q,
  initialPage = 0,
): PagedQueryResult<T> {
  const items = shallowRef<T[]>([]) as ShallowRef<T[]>
  const page = ref(initialPage)
  const loading = ref(false)
  const error = ref<AppError | null>(null)
  const totalPages = ref<number | undefined>(undefined)
  const hasNext = ref(false)

  let controller: AbortController | null = null

  const canPrev = computed(() => page.value > 0)
  const canNext = computed(() => hasNext.value)

  async function load(target = page.value): Promise<void> {
    controller?.abort()
    const current = new AbortController()
    controller = current
    const { signal } = current

    loading.value = true
    error.value = null

    try {
      const result = await fetcher({ ...getFilters(), page: target }, signal)
      if (signal.aborted) return
      items.value = result.items
      page.value = result.page
      hasNext.value = result.hasNext
      totalPages.value = result.totalPages
    } catch (cause) {
      if (signal.aborted) return
      error.value = toAppError(cause)
      items.value = []
      hasNext.value = false
      totalPages.value = undefined
    } finally {
      if (!signal.aborted) loading.value = false
    }
  }

  async function go(target: number): Promise<void> {
    if (target < 0 || target === page.value) return
    await load(target)
  }

  return {
    items,
    page,
    loading,
    error,
    totalPages,
    hasNext,
    canPrev,
    canNext,
    load,
    go,
    prev: () => go(page.value - 1),
    next: () => go(page.value + 1),
    reload: () => load(),
  }
}
