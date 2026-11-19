import { ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { toAppError, type AppError } from '@/utils/errors'

export interface AsyncActionOptions {
  /** 成功提示文案，不传则不提示 */
  success?: string
  /** 自定义失败处理（表单内联红字），不传则 toast 后端 message */
  onError?: (error: AppError) => void
}

/**
 * 写操作统一包装：防重复点击 + 成功提示 + 失败提示。
 * 失败时保留调用方的表单状态，由调用方决定是就地显示还是 toast。
 */
export function useAsyncAction() {
  const loading = ref(false)

  async function run<T>(task: () => Promise<T>, options: AsyncActionOptions = {}): Promise<T | undefined> {
    if (loading.value) return undefined
    loading.value = true
    try {
      const result = await task()
      if (options.success) ElMessage.success(options.success)
      return result
    } catch (cause) {
      const error = toAppError(cause)
      if (options.onError) options.onError(error)
      else ElMessage.error(error.message)
      return undefined
    } finally {
      loading.value = false
    }
  }

  return { loading: loading as Ref<boolean>, run }
}
