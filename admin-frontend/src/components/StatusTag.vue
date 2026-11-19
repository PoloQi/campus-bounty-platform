<script setup lang="ts">
import { computed } from 'vue'
import { dictLabel, dictTone, type DictKind } from '@/constants/options'

const props = defineProps<{
  kind: DictKind
  value?: string | null
}>()

const text = computed(() => dictLabel(props.kind, props.value))
const tone = computed(() => dictTone(props.kind, props.value))
</script>

<template>
  <span class="status-tag" :class="`status-tag--${tone}`">{{ text }}</span>
</template>
