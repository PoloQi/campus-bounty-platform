<script setup lang="ts">
import PageHeader from '@/components/PageHeader.vue'
import ModulePlaceholder from '@/components/ModulePlaceholder.vue'
</script>

<template>
  <div>
    <PageHeader
      title="申诉仲裁"
      description="处理交易纠纷申诉：核验订单证据后，在「打款给卖家」与「退款给买家」之间做出裁决。"
    />
    <ModulePlaceholder
      module="M4"
      title="申诉列表与仲裁将在 M4 交付"
      summary="裁决是唯一会直接动钱的入口，页面会强制校验「申诉 OPEN 且订单 DISPUTED」才允许提交。"
      :features="['三态状态筛选', '详情联动订单证据', '裁决二选一', '意见 1–500 字', '二次确认含金额摘要']"
    />
  </div>
</template>
