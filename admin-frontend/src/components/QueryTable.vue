<script setup lang="ts">
import ErrorState from './ErrorState.vue'
import EmptyState from './EmptyState.vue'
import type { AppError } from '@/utils/errors'

withDefaults(
  defineProps<{
    loading: boolean
    error: AppError | null
    /** 当前是否没有数据（由页面传 items.length === 0） */
    empty: boolean
    emptyText?: string
    emptyHint?: string
  }>(),
  { emptyText: '暂无数据', emptyHint: '' },
)

defineEmits<{ retry: [] }>()
</script>

<template>
  <div class="query-table panel">
    <ErrorState v-if="error" :error="error" @retry="$emit('retry')" />

    <div v-else-if="loading && empty" class="query-table__skeleton">
      <el-skeleton :rows="6" animated />
    </div>

    <template v-else>
      <div class="query-table__body" :class="{ 'is-loading': loading }">
        <slot />
      </div>

      <EmptyState v-if="empty" :text="emptyText" :hint="emptyHint">
        <slot name="empty-action" />
      </EmptyState>

      <footer v-if="!empty" class="query-table__footer">
        <slot name="pager" />
      </footer>
    </template>
  </div>
</template>
