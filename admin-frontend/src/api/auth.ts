import { http } from './http'
import type { LoginResult } from '@/types/domain'

export interface LoginPayload {
  username: string
  password: string
}

/**
 * 管理员登录。失败有两种：
 *   密码错误 / 账号被封禁 → HTTP 200 + code=40000（走 biz 分支，就地提示）
 *   参数为空 → 400 + 40000
 */
export function login(payload: LoginPayload): Promise<LoginResult> {
  return http.post<LoginResult>('/api/auth/login', payload)
}
