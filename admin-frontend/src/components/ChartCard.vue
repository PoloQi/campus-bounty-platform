<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string
    /** 图表容器必须有显式高度，否则 init 后尺寸为 0，表现为「空白但不报错」 */
    height?: number
    empty?: boolean
    emptyText?: string
  }>(),
  { height: 260, empty: false, emptyText: '暂无数据' },
)
</script>

<template>
  <section class="panel chart-card">
    <header class="chart-card__head">
      <h3 class="chart-card__title">{{ title }}</h3>
      <div class="chart-card__extra"><slot name="extra" /></div>
    </header>
    <div class="chart-card__body" :style="{ height: `${height}px` }">
      <div v-if="empty" class="chart-card__empty">{{ emptyText }}</div>
      <slot v-else />
    </div>
  </section>
</template>
