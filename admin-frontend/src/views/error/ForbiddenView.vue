<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goBack(): void {
  if (window.history.length > 1) router.back()
  else void router.replace({ name: 'login' })
}

function goLogin(): void {
  void router.replace({ name: 'login' })
}
</script>

<template>
  <div class="error-page">
    <div class="error-page__card panel">
      <span class="error-page__code">403</span>
      <h1 class="error-page__title">当前账号没有管理员权限</h1>
      <p class="error-page__desc">
        管理台只对平台管理员开放。如需处理治理与仲裁事务，请使用管理员账号登录。
      </p>
      <div class="error-page__actions">
        <el-button @click="goBack">返回上一页</el-button>
        <el-button type="primary" @click="goLogin">重新登录</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.error-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px;
  background: var(--page-bg);
}

.error-page__card {
  width: 100%;
  max-width: 480px;
  padding: 40px 36px 36px;
  text-align: center;
}

.error-page__code {
  display: inline-block;
  font-size: 48px;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: var(--brand-500);
  line-height: 1;
  margin-bottom: 16px;
}

.error-page__title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-strong);
}

.error-page__desc {
  margin-top: 10px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--text-muted);
}

.error-page__actions {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-top: 26px;
}
</style>
