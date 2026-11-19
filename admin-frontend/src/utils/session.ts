/**
 * 会话存储。独立 key，避免与旧 frontend/ 的学生端会话混用
 * （旧版 key 是 campus-bounty-session）。
 */
import type { LoginResult } from '@/types/domain'

const TOKEN_KEY = 'campus-bounty-admin-token'
const USER_KEY = 'campus-bounty-admin-user'

export type SessionUser = Omit<LoginResult, 'token'>

export function getToken(): string {
  return sessionStorage.getItem(TOKEN_KEY) ?? ''
}

export function getSessionUser(): SessionUser | null {
  const raw = sessionStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as SessionUser
  } catch {
    return null
  }
}

export function saveSession(result: LoginResult): void {
  const { token, ...user } = result
  sessionStorage.setItem(TOKEN_KEY, token)
  sessionStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearSession(): void {
  sessionStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(USER_KEY)
}
