import type { Router } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const APP_TITLE = '校园悬赏平台 · 管理台'

/**
 * 守卫只解决「能不能进后台」，不做按钮级权限：
 * 后端只有 STUDENT / ADMIN 两种角色，/api/admin/** 统一 hasRole("ADMIN")，
 * 前端再细分也是假安全。
 */
export function setupRouterGuard(router: Router): void {
  router.beforeEach((to) => {
    const auth = useAuthStore()

    if (to.meta.public) {
      // 已登录还去登录页 → 回工作台
      if (to.name === 'login' && auth.isLoggedIn && auth.isAdmin) {
        return { path: '/dashboard' }
      }
      return true
    }

    if (!auth.isLoggedIn) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }

    if (!auth.isAdmin) {
      auth.clear()
      return { name: 'forbidden' }
    }

    return true
  })

  router.afterEach((to) => {
    const title = typeof to.meta.title === 'string' ? to.meta.title : ''
    document.title = title ? `${title} · ${APP_TITLE}` : APP_TITLE
  })
}
