import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { login as loginApi, type LoginPayload } from '@/api/auth'
import type { LoginResult } from '@/types/domain'
import { clearSession, getSessionUser, getToken, saveSession, type SessionUser } from '@/utils/session'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(getToken())
  const user = ref<SessionUser | null>(getSessionUser())

  const isLoggedIn = computed(() => Boolean(token.value) && user.value != null)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const displayName = computed(() => {
    if (!user.value) return '未登录'
    return user.value.nickname || `管理员 #${user.value.userId}`
  })

  async function login(payload: LoginPayload): Promise<LoginResult> {
    const result = await loginApi(payload)
    saveSession(result)
    token.value = result.token
    user.value = {
      userId: result.userId,
      nickname: result.nickname,
      role: result.role,
    }
    return result
  }

  function clear(): void {
    clearSession()
    token.value = ''
    user.value = null
  }

  return { token, user, isLoggedIn, isAdmin, displayName, login, clear }
})
