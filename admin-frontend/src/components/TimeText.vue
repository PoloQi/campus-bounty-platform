<script setup lang="ts">
import { computed } from 'vue'
import { formatDateTime } from '@/utils/datetime'

const props = withDefaults(
  defineProps<{
    value?: string | null
    /** 只显示日期 */
    dateOnly?: boolean
  }>(),
  { value: null, dateOnly: false },
)

const text = computed(() => {
  if (!props.value) return '—'
  return props.dateOnly ? props.value.slice(0, 10) : formatDateTime(props.value)
})
</script>

<template>
  <time class="time-text" :datetime="value ?? undefined">{{ text }}</time>
</template>
