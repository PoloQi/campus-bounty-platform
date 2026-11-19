<script setup lang="ts">
import PageHeader from '@/components/PageHeader.vue'
import ModulePlaceholder from '@/components/ModulePlaceholder.vue'
</script>

<template>
  <div>
    <PageHeader
      title="审计日志"
      description="记录管理员对内容、账号与纠纷的每一次处置，可按管理员、动作类型与时间区间追溯。"
    />
    <ModulePlaceholder
      module="M7"
      title="审计日志检索将在 M7 交付"
      summary="每条日志可跳转到对应的举报或申诉详情；审计记录只读，不提供编辑与删除。"
      :features="['按管理员 / 动作 / 日期筛选', '详情抽屉看全文', '跳转举报与申诉详情']"
    />
  </div>
</template>
