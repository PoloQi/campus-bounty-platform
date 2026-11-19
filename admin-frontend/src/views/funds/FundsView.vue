<script setup lang="ts">
import PageHeader from '@/components/PageHeader.vue'
import ModulePlaceholder from '@/components/ModulePlaceholder.vue'
</script>

<template>
  <div>
    <PageHeader
      title="资金监管"
      description="核对平台冻结总额与在途应冻金额是否一致，按用户查询钱包余额，并检索全平台资金流水。"
    />
    <ModulePlaceholder
      module="M6"
      title="资金核对与流水查询将在 M6 交付"
      summary="差额不为 0 时会红色告警；管理端只读，不提供任何调账、加钱或扣钱入口。"
      :features="['冻结 / 在途 / 差额三项核对', '按用户查钱包抽屉', '五类流水筛选', '订单跳转']"
    />
  </div>
</template>
