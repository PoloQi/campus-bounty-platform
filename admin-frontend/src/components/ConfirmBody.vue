<script setup lang="ts">
import type { ConfirmRow } from '@/composables/useConfirm'

withDefaults(
  defineProps<{
    rows?: ConfirmRow[]
    impact?: string
  }>(),
  { rows: () => [], impact: '' },
)
</script>

<template>
  <div class="confirm-body">
    <dl v-if="rows.length" class="confirm-body__rows">
      <div v-for="row in rows" :key="row.label" class="confirm-body__row">
        <dt>{{ row.label }}</dt>
        <dd>{{ row.value }}</dd>
      </div>
    </dl>
    <p v-if="impact" class="confirm-body__impact">{{ impact }}</p>
  </div>
</template>
