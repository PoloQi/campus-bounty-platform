<script setup lang="ts">
import PageHeader from '@/components/PageHeader.vue'
import ModulePlaceholder from '@/components/ModulePlaceholder.vue'
</script>

<template>
  <div>
    <PageHeader
      title="举报处理"
      description="审阅用户提交的内容举报，判定成立或驳回；成立时可联动删除帖子、下架交易帖或封禁作者。"
    />
    <ModulePlaceholder
      module="M3"
      title="举报列表与处置将在 M3 交付"
      summary="处置表单会随「成立 / 驳回」联动治理开关，并在提交前展示对象摘要与影响说明。"
      :features="['状态与对象类型筛选', '对象摘要预览', '处置表单联动', '按编号直接治理', '并发处理失败兜底']"
    />
  </div>
</template>
