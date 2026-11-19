<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Expand, Fold, SwitchButton } from '@element-plus/icons-vue'
import { findMenuByPath } from '@/constants/menu'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()

const menu = computed(() => findMenuByPath(route.path))
const pageTitle = computed(() => {
  const title = route.meta.title
  return typeof title === 'string' ? title : ''
})
const showDetailCrumb = computed(() => Boolean(menu.value) && pageTitle.value !== menu.value?.title)

function logout(): void {
  auth.clear()
  void router.replace({ name: 'login' })
}
</script>

<template>
  <header class="app-header">
    <div class="app-header__left">
      <button
        class="app-header__toggle"
        type="button"
        :aria-label="ui.collapsed ? '展开侧栏' : '收起侧栏'"
        @click="ui.toggleSidebar()"
      >
        <el-icon :size="18"><component :is="ui.collapsed ? Expand : Fold" /></el-icon>
      </button>

      <nav class="app-header__crumb" aria-label="面包屑">
        <span>管理台</span>
        <template v-if="menu">
          <span class="app-header__crumb-sep">/</span>
          <span :class="{ 'app-header__crumb-current': !showDetailCrumb }">{{ menu.title }}</span>
        </template>
        <template v-if="showDetailCrumb">
          <span class="app-header__crumb-sep">/</span>
          <span class="app-header__crumb-current">{{ pageTitle }}</span>
        </template>
      </nav>
    </div>

    <div class="app-header__right">
      <div class="app-user">
        <span class="app-user__avatar">{{ auth.displayName.slice(0, 1) }}</span>
        <span>{{ auth.displayName }}</span>
      </div>
      <button class="app-header__logout" type="button" @click="logout">
        <el-icon :size="14"><SwitchButton /></el-icon>
        退出
      </button>
    </div>
  </header>
</template>
