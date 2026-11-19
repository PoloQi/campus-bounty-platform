<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string
    hint?: string
  }>(),
  { hint: '' },
)
</script>

<template>
  <section class="panel detail-section">
    <header class="detail-section__head">
      <h3 class="detail-section__title">{{ title }}</h3>
      <p v-if="hint" class="detail-section__hint">{{ hint }}</p>
      <div class="detail-section__extra"><slot name="extra" /></div>
    </header>
    <div class="detail-section__body">
      <slot />
    </div>
  </section>
</template>
