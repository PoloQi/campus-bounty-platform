<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 0 基页码 */
    page: number
    hasNext: boolean
    /** 只有申诉接口会返回；其余列表为 undefined */
    totalPages?: number
    loading?: boolean
  }>(),
  { totalPages: undefined, loading: false },
)

const emit = defineEmits<{ change: [page: number] }>()

/**
 * 后端列表接口没有 total，只有 hasNext，所以不用 el-pagination：
 * 伪造 total 会让跳页错乱，还会诱导显示「共 N 条」这种后端并不存在的数字。
 *
 * 申诉接口虽然有 totalPages，但有两个坑：
 *   - 空结果时 totalPages = 0，不能显示「第 1 / 0 页」；
 *   - page 越界时后端不夹取（page=99 会原样回 99），所以不能写死「x / y」。
 */
const label = computed(() => {
  const total = props.totalPages
  if (total && total > 0) {
    return props.page + 1 <= total
      ? `第 ${props.page + 1} / ${total} 页`
      : `第 ${props.page + 1} 页 · 共 ${total} 页`
  }
  return `第 ${props.page + 1} 页`
})
</script>

<template>
  <nav class="pager" aria-label="翻页">
    <span class="pager__label">{{ label }}</span>
    <div class="pager__actions">
      <el-button
        v-if="page > 0"
        size="small"
        :disabled="loading"
        @click="emit('change', 0)"
      >
        回到第一页
      </el-button>
      <el-button size="small" :disabled="page === 0 || loading" @click="emit('change', page - 1)">
        上一页
      </el-button>
      <el-button size="small" :disabled="!hasNext || loading" @click="emit('change', page + 1)">
        下一页
      </el-button>
    </div>
  </nav>
</template>
