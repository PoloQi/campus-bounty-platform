<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useAsyncAction } from '@/composables/useAsyncAction'
import { safeRedirect } from '@/utils/query'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { loading, run } = useAsyncAction()

const form = reactive({ username: '', password: '' })
const errorText = ref('')
/** 模板里不能直接写 import.meta */
const isDev = import.meta.env.DEV

async function submit(): Promise<void> {
  errorText.value = ''
  const username = form.username.trim()

  if (!username || !form.password) {
    errorText.value = '请输入用户名和密码'
    return
  }

  const result = await run(() => auth.login({ username, password: form.password }), {
    onError: (error) => {
      // 密码错误 / 账号封禁都是 HTTP 200 + 40000，就地提示，不弹全局窗
      errorText.value = error.message
    },
  })
  if (!result) return

  if (result.role !== 'ADMIN') {
    auth.clear()
    errorText.value = '该账号不是管理员，无法进入管理台'
    return
  }

  await router.replace(safeRedirect(route.query.redirect))
}
</script>

<template>
  <div class="login">
    <section class="login__brand">
      <div class="login__brand-top">
        <span class="login__brand-mark">悬</span>
        <span>校园悬赏平台 · 管理台</span>
      </div>

      <div class="login__brand-main">
        <h1 class="login__headline">把每一笔校园交易，<br />都看得清清楚楚</h1>
        <p class="login__subline">
          交易保障、内容治理、资金监管与操作审计，<br />四件事在一个后台里闭环。
        </p>
        <dl class="login__points">
          <div class="login__point">
            <dt>托管资金</dt>
            <dd>冻结与在途实时核对</dd>
          </div>
          <div class="login__point">
            <dt>纠纷裁决</dt>
            <dd>先锁订单，再动钱</dd>
          </div>
          <div class="login__point">
            <dt>操作留痕</dt>
            <dd>每一步都可追溯</dd>
          </div>
        </dl>
      </div>

      <div class="login__brand-foot">仅限平台管理员使用 · 所有操作纳入审计</div>
    </section>

    <section class="login__panel">
      <form class="login__form" @submit.prevent="submit">
        <h2 class="login__title">管理员登录</h2>
        <p class="login__desc">使用平台管理员账号进入管理台</p>

        <div class="login__fields">
          <div class="login__field">
            <label for="login-username">用户名</label>
            <el-input
              id="login-username"
              v-model="form.username"
              size="large"
              autocomplete="username"
              placeholder="请输入管理员用户名"
              :disabled="loading"
            />
          </div>

          <div class="login__field">
            <label for="login-password">密码</label>
            <el-input
              id="login-password"
              v-model="form.password"
              type="password"
              size="large"
              show-password
              autocomplete="current-password"
              placeholder="请输入密码"
              :disabled="loading"
            />
          </div>

          <el-button
            class="login__submit"
            type="primary"
            size="large"
            native-type="submit"
            :loading="loading"
          >
            登录
          </el-button>

          <p class="login__error" role="alert">{{ errorText }}</p>
        </div>

        <p v-if="isDev" class="login__hint">
          本地开发账号：<code>admin</code> / <code>admin123</code>（由后端 local、test profile 初始化）
        </p>
      </form>
    </section>
  </div>
</template>
