<script setup lang="ts">
import PageHeader from '@/components/PageHeader.vue'
import ModulePlaceholder from '@/components/ModulePlaceholder.vue'
</script>

<template>
  <div>
    <PageHeader
      title="订单管理"
      description="按状态、交易类型、交易双方与下单日期检索订单；进入详情可查看订单摘要、交易时间线与关联资金流水。"
    />
    <ModulePlaceholder
      module="M1"
      title="订单列表与详情将在 M1 交付"
      summary="M1 同时会把「筛选 + 分页 + 详情」这套模板打磨成型，后续六个页面直接复用。"
      :features="['六条件筛选', '上一页 / 下一页分页', '筛选条件写入 URL', '订单详情独立页', '交易时间线', '关联资金流水']"
    />
  </div>
</template>
