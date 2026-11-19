/**
 * 错误归一化。
 *
 * 后端错误有六类，处理方式完全不同，混在一起就会出现
 * 「40000 弹全局窗打断填表」或「401 每个请求都跳一次登录」。
 */
export type ErrorKind =
  | 'network'
  | 'auth'
  | 'forbidden'
  | 'notfound'
  | 'server'
  | 'biz'

export interface AppErrorOptions {
  status?: number
  code?: number
}

export class AppError extends Error {
  readonly kind: ErrorKind
  readonly status?: number
  readonly code?: number

  constructor(kind: ErrorKind, message: string, options: AppErrorOptions = {}) {
    super(message)
    this.name = 'AppError'
    this.kind = kind
    this.status = options.status
    this.code = options.code
  }
}

export function isAppError(value: unknown): value is AppError {
  return value instanceof AppError
}

/** 给页面用的兜底文案 */
export function errorMessage(error: unknown): string {
  if (isAppError(error)) return error.message
  if (error instanceof Error && error.message) return error.message
  return '操作失败，请稍后重试'
}

/** 页面级失败态（ErrorState）用的标题与说明 */
export function errorHint(error: AppError): { title: string; detail: string } {
  switch (error.kind) {
    case 'network':
      return { title: '暂时无法连接服务', detail: '请确认后端已启动，或稍后重试' }
    case 'auth':
      return { title: '登录状态已失效', detail: '请重新登录后继续' }
    case 'forbidden':
      return { title: '没有管理员权限', detail: '当前账号无法访问管理端' }
    case 'notfound':
      return { title: '内容不存在', detail: error.message || '资源可能已被删除' }
    case 'server':
      return { title: '服务出现异常', detail: error.message || '请稍后重试' }
    default:
      return { title: '加载失败', detail: error.message }
  }
}

/** axios 错误 → AppError（业务 code 的归一在 http.ts 里做） */
export function toAppError(value: unknown): AppError {
  if (isAppError(value)) return value
  if (value instanceof Error) {
    return new AppError('network', value.message || '网络请求失败')
  }
  return new AppError('network', '网络请求失败')
}
