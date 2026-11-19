<script setup lang="ts">
withDefaults(
  defineProps<{
    text?: string
    hint?: string
  }>(),
  { text: '暂无数据', hint: '' },
)
</script>

<template>
  <div class="empty-state">
    <svg class="empty-state__art" viewBox="0 0 96 72" fill="none" aria-hidden="true">
      <rect x="12" y="10" width="72" height="52" rx="8" fill="#F4F7FE" stroke="#DCE4F5" stroke-width="1.5" />
      <path d="M12 26h72" stroke="#DCE4F5" stroke-width="1.5" />
      <circle cx="21" cy="18" r="2" fill="#C7D4EC" />
      <circle cx="28" cy="18" r="2" fill="#C7D4EC" />
      <rect x="24" y="36" width="34" height="4" rx="2" fill="#E3EAF8" />
      <rect x="24" y="46" width="24" height="4" rx="2" fill="#EDF1FA" />
    </svg>
    <p class="empty-state__text">{{ text }}</p>
    <p v-if="hint" class="empty-state__hint">{{ hint }}</p>
    <div class="empty-state__actions"><slot /></div>
  </div>
</template>
