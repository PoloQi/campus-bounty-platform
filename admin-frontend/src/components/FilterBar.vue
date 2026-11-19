<script setup lang="ts">
defineProps<{
  /** 提交中禁用按钮，防重复查询 */
  loading?: boolean
}>()

defineEmits<{ submit: []; reset: [] }>()
</script>

<template>
  <form class="filter-bar panel" @submit.prevent="$emit('submit')">
    <div class="filter-bar__fields">
      <slot />
    </div>
    <div class="filter-bar__actions">
      <el-button :disabled="loading" @click.prevent="$emit('reset')">重置</el-button>
      <el-button type="primary" native-type="submit" :loading="loading">查询</el-button>
    </div>
  </form>
</template>
