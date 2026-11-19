import { onBeforeUnmount, ref, shallowRef, type Ref, type ShallowRef } from 'vue'
import type { AppError } from '@/utils/errors'
import { toAppError } from '@/utils/errors'

export interface DetailResult<T> {
  data: ShallowRef<T | null>
  loading: Ref<boolean>
  error: Ref<AppError | null>
  load: () => Promise<void>
  reload: () => Promise<void>
}

/**
 * 详情页加载内核。与 usePagedQuery 同构，但只有单个对象。
 *
 * 关键点：组件卸载时 abort。详情页里点返回列表很快，旧请求回来晚了
 * 会往已卸载的组件里写数据（Vue 不报错，但会污染刚打开的新页面状态）。
 */
export function useDetail<T>(fetcher: (signal: AbortSignal) => Promise<T>): DetailResult<T> {
  const data = shallowRef<T | null>(null) as ShallowRef<T | null>
  const loading = ref(false)
  const error = ref<AppError | null>(null)

  let controller: AbortController | null = null

  async function load(): Promise<void> {
    controller?.abort()
    const current = new AbortController()
    controller = current
    const { signal } = current

    loading.value = true
    error.value = null

    try {
      const result = await fetcher(signal)
      if (signal.aborted) return
      data.value = result
    } catch (cause) {
      if (signal.aborted) return
      error.value = toAppError(cause)
      data.value = null
    } finally {
      if (!signal.aborted) loading.value = false
    }
  }

  onBeforeUnmount(() => controller?.abort())

  return { data, loading, error, load, reload: load }
}
