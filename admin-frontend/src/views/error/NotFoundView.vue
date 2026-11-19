<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goDashboard(): void {
  void router.replace({ name: 'dashboard' })
}
</script>

<template>
  <div class="error-page">
    <div class="error-page__card panel">
      <span class="error-page__code">404</span>
      <h1 class="error-page__title">页面不存在</h1>
      <p class="error-page__desc">
        地址可能输入有误，或者该记录已被删除。可以回到仪表盘重新进入对应模块。
      </p>
      <div class="error-page__actions">
        <el-button @click="goDashboard">返回仪表盘</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 这个页面渲染在管理布局的内容区里，不能再撑满 100vh，
   否则外层滚一遍、侧栏底下还留一大片空白 */
.error-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 62vh;
  padding: 24px;
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
