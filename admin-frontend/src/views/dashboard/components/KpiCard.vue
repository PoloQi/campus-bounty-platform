<script setup lang="ts">
import type { Component } from 'vue'

withDefaults(
  defineProps<{
    label: string
    /** 已格式化好的展示值（金额用 MoneyText 那种格式传进来也一样） */
    value: string
    /** 说明文字：口径、单位或补充信息 */
    hint?: string
    icon: Component
    tone?: 'primary' | 'success' | 'warning' | 'danger' | 'info'
    /** 可跳转的模块路径；不传则是纯展示卡 */
    to?: string
  }>(),
  { hint: '', tone: 'primary', to: '' },
)
</script>

<template>
  <component :is="to ? 'router-link' : 'div'" :to="to || undefined" class="kpi" :class="{ 'kpi--link': Boolean(to) }">
    <div class="kpi__head">
      <span class="kpi__label">{{ label }}</span>
      <span class="kpi__icon" :class="`kpi__icon--${tone}`">
        <el-icon :size="15"><component :is="icon" /></el-icon>
      </span>
    </div>
    <p class="kpi__value">{{ value }}</p>
    <p v-if="hint" class="kpi__foot">{{ hint }}</p>
  </component>
</template>
