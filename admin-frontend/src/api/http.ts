import axios, { type AxiosError, type AxiosRequestConfig } from 'axios'
import type { ApiEnvelope } from '@/types/api'
import { AppError } from '@/utils/errors'
import { cleanParams } from '@/utils/query'
import { clearSession, getToken } from '@/utils/session'

/**
 * 请求层。三件事只在这里做一次：
 *   1. 注入 Bearer
 *   2. 清洗空参数（否则 buyerId='' / from='' 会变成 40000）
 *   3. 把六类错误归一成 AppError
 *
 * 注意：后端大批「正常失败」是 HTTP 200 + code=40000，
 * 所以成功分支也必须检查 code。
 */

let unauthorizedHandler: (() => void) | null = null
let redirecting = false

/** 由 main.ts 注入，避免 http → router → guard → store → http 的循环依赖 */
export function setUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler
}

const client = axios.create({
  baseURL: '/',
  timeout: 15000,
})

client.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  if (config.params) {
    config.params = cleanParams(config.params as Record<string, unknown>)
  }
  return config
})

client.interceptors.response.use(
  (response) => {
    const body = response.data as ApiEnvelope<unknown> | undefined
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) return body.data
      // 业务失败：交给页面就地提示，绝不在这里弹全局窗或跳转
      throw new AppError('biz', body.message || '操作失败', { code: body.code, status: response.status })
    }
    return response.data
  },
  (error: AxiosError<ApiEnvelope<unknown>>) => {
    throw normalizeAxiosError(error)
  },
)

function normalizeAxiosError(error: AxiosError<ApiEnvelope<unknown>>): AppError {
  if (!error.response) {
    if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
      return new AppError('network', '请求超时，请稍后重试')
    }
    return new AppError('network', '暂时无法连接服务，请稍后重试')
  }

  const { status, data } = error.response
  const code = data?.code
  const message = data?.message || error.message

  if (status === 401 || code === 40100) {
    handleUnauthorized()
    return new AppError('auth', message || '登录状态已失效', { status, code })
  }
  if (status === 403 || code === 40300) {
    return new AppError('forbidden', message || '需要管理员权限', { status, code })
  }
  if (status === 404) {
    return new AppError('notfound', message || '请求的资源不存在', { status, code })
  }
  if (status >= 500) {
    return new AppError('server', message || '服务端异常，请稍后重试', { status, code })
  }
  // 400 / 405 等：参数不合法、方法不支持，按业务失败处理
  return new AppError('biz', message || '请求无法完成', { status, code })
}

function handleUnauthorized(): void {
  clearSession()
  if (redirecting) return
  redirecting = true
  unauthorizedHandler?.()
  // 留出跳转时间后复位，避免并发 401 触发多次跳转
  window.setTimeout(() => {
    redirecting = false
  }, 800)
}

/** 统一出口：成功时直接拿到 data */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  try {
    return (await client.request(config)) as unknown as T
  } catch (error) {
    throw error instanceof AppError ? error : new AppError('network', '网络请求失败')
  }
}

export const http = {
  get: <T>(url: string, params?: Record<string, unknown>, config?: AxiosRequestConfig) =>
    request<T>({ ...config, method: 'GET', url, params }),
  post: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
    request<T>({ ...config, method: 'POST', url, data }),
}
