<script setup lang="ts">
import { useRoute } from 'vue-router'
import { ADMIN_MENU, findMenuByPath } from '@/constants/menu'
import { useUiStore } from '@/stores/ui'

const route = useRoute()
const ui = useUiStore()

function isActive(path: string): boolean {
  return findMenuByPath(route.path)?.path === path
}
</script>

<template>
  <aside class="sidebar" :class="{ 'sidebar--collapsed': ui.collapsed }">
    <div class="sidebar__brand">
      <span class="sidebar__mark">悬</span>
      <span v-if="!ui.collapsed" class="sidebar__brand-text">
        <strong>校园悬赏</strong>
        <small>管理台</small>
      </span>
    </div>

    <nav class="sidebar__nav" aria-label="主导航">
      <RouterLink
        v-for="item in ADMIN_MENU"
        :key="item.path"
        :to="item.path"
        class="sidebar__link"
        :class="{ 'is-active': isActive(item.path) }"
        :title="ui.collapsed ? item.title : undefined"
        :aria-current="isActive(item.path) ? 'page' : undefined"
      >
        <el-icon class="sidebar__icon"><component :is="item.icon" /></el-icon>
        <span v-if="!ui.collapsed">{{ item.short }}</span>
      </RouterLink>
    </nav>

    <div v-if="!ui.collapsed" class="sidebar__footer">
      管理端 v0.1<br />
      交易保障 · 内容治理 · 资金监管
    </div>
  </aside>
</template>
