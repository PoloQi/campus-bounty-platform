<script setup lang="ts">
import { computed } from 'vue'
import { formatMoney, formatSignedMoney, isNegative } from '@/utils/money'

const props = withDefaults(
  defineProps<{
    value?: number | null
    /** 是否显示正负号（资金流水用） */
    signed?: boolean
    /** 强调样式（KPI、合计） */
    strong?: boolean
    /** 无数据时的占位 */
    placeholder?: string
  }>(),
  { value: null, signed: false, strong: false, placeholder: '—' },
)

const negative = computed(() => isNegative(props.value))
const text = computed(() => {
  if (props.value == null) return props.placeholder
  return props.signed ? formatSignedMoney(props.value) : formatMoney(props.value)
})
</script>

<template>
  <span class="money" :class="{ 'money--negative': negative, 'money--strong': strong }">{{ text }}</span>
</template>
