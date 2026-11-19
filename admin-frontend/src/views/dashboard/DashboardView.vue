<script setup lang="ts">
import { onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import DetailSection from '@/components/DetailSection.vue'
import ErrorState from '@/components/ErrorState.vue'
import ModulePlaceholder from '@/components/ModulePlaceholder.vue'
import MoneyText from '@/components/MoneyText.vue'
import { fetchDashboard } from '@/api/admin'
import type { Dashboard } from '@/types/domain'
import type { AppError } from '@/utils/errors'
import { toAppError } from '@/utils/errors'
import { formatNumber } from '@/utils/money'

const loading = ref(false)
const error = ref<AppError | null>(null)
const data = ref<Dashboard | null>(null)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    data.value = await fetchDashboard()
  } catch (cause) {
    error.value = toAppError(cause)
    data.value = null
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      title="仪表盘"
      description="平台运营总览：用户与订单规模、托管资金、待办治理事项与近七日成交趋势。"
    />

    <DetailSection
      title="接口连通性自检"
      hint="M0 用于验证「登录 → Vite 代理 → /api/admin/dashboard」链路，M2 会升级为正式仪表盘"
      :style="{ marginBottom: '16px' }"
    >
      <template #extra>
        <el-button size="small" :loading="loading" @click="load">重新拉取</el-button>
      </template>

      <ErrorState v-if="error" :error="error" @retry="load" />

      <el-skeleton v-else-if="!data" :rows="2" animated />

      <div v-else class="kv-grid">
        <div class="kv">
          <span class="kv__label">学生用户</span>
          <span class="kv__value">{{ formatNumber(data.totals.students) }}</span>
        </div>
        <div class="kv">
          <span class="kv__label">订单总量</span>
          <span class="kv__value">{{ formatNumber(data.totals.orders) }}</span>
        </div>
        <div class="kv">
          <span class="kv__label">已完成交易额</span>
          <span class="kv__value"><MoneyText :value="data.totals.gmv" /></span>
        </div>
        <div class="kv">
          <span class="kv__label">平台冻结总额</span>
          <span class="kv__value"><MoneyText :value="data.totals.frozen" /></span>
        </div>
        <div class="kv">
          <span class="kv__label">待裁决申诉</span>
          <span class="kv__value">{{ formatNumber(data.totals.openAppeals) }}</span>
        </div>
        <div class="kv">
          <span class="kv__label">待处理举报</span>
          <span class="kv__value">{{ formatNumber(data.totals.pendingReports) }}</span>
        </div>
      </div>
    </DetailSection>

    <ModulePlaceholder
      module="M2"
      title="仪表盘图表与指标卡将在 M2 交付"
      summary="届时提供六项 KPI 卡、六态订单分布饼图与近七日成交折线，并与后端看板口径逐项对齐。"
    />
  </div>
</template>
