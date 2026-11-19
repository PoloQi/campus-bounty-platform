import { createApp } from 'vue'
import { createPinia } from 'pinia'
// 函数式 API（ElMessage / ElMessageBox）不会被组件解析器自动引入，样式要显式引
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import './styles/index.css'
import App from './App.vue'
import router from './router'
import { setupRouterGuard } from './router/guard'
import { setUnauthorizedHandler } from './api/http'
import { useAuthStore } from './stores/auth'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
setupRouterGuard(router)
app.use(router)

// 401 统一处理：清会话 + 只跳一次登录页（并发请求不重复提示）
setUnauthorizedHandler(() => {
  const auth = useAuthStore(pinia)
  auth.clear()

  const current = router.currentRoute.value
  if (current.name === 'login') return
  const redirect = current.fullPath && current.fullPath !== '/' ? current.fullPath : undefined
  // expired 只在这一处产生：并发 5 个 401 也只会有一次 replace，登录页因此只提示一次
  void router.replace({
    name: 'login',
    query: { ...(redirect ? { redirect } : {}), expired: '1' },
  })
})

app.mount('#app')
