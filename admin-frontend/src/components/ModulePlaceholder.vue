<script setup lang="ts">
withDefaults(
  defineProps<{
    /** 计划书里的模块编号 */
    module: string
    title: string
    summary?: string
    features?: string[]
  }>(),
  { summary: '', features: () => [] },
)
</script>

<template>
  <section class="panel placeholder">
    <span class="placeholder__badge">待交付 · {{ module }}</span>
    <h2 class="placeholder__title">{{ title }}</h2>
    <p v-if="summary" class="placeholder__text">{{ summary }}</p>
    <p v-if="features.length" class="placeholder__text">
      本模块将交付：{{ features.join(' · ') }}
    </p>
  </section>
</template>
