<script setup lang="ts">
import { computed } from 'vue'
import type { AppError } from '@/utils/errors'
import { errorHint } from '@/utils/errors'

const props = withDefaults(
  defineProps<{
    /** 传 null 时组件不渲染，方便调用方省掉 v-if 的类型收窄 */
    error: AppError | null
    retryText?: string
  }>(),
  { retryText: '重新加载' },
)

defineEmits<{ retry: [] }>()

const hint = computed(() => (props.error ? errorHint(props.error) : null))
</script>

<template>
  <div v-if="hint" class="error-state">
    <div class="error-state__icon" aria-hidden="true">
      <svg viewBox="0 0 24 24" fill="none">
        <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.6" />
        <path d="M12 7.5v5.2" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
        <circle cx="12" cy="16.4" r="1" fill="currentColor" />
      </svg>
    </div>
    <p class="error-state__title">{{ hint.title }}</p>
    <p class="error-state__detail">{{ hint.detail }}</p>
    <el-button class="error-state__retry" @click="$emit('retry')">{{ retryText }}</el-button>
  </div>
</template>
