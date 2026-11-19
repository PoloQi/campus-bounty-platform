<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Bell, ChatLineSquare, Coin, Tickets, User, Wallet } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import DetailSection from '@/components/DetailSection.vue'
import ChartCard from '@/components/ChartCard.vue'
import ErrorState from '@/components/ErrorState.vue'
import KpiCard from './components/KpiCard.vue'
import TrendChart from './components/TrendChart.vue'
import StatusPieChart from './components/StatusPieChart.vue'
import { fetchDashboard } from '@/api/admin'
import { useDetail } from '@/composables/useDetail'
import type { Dashboard } from '@/types/domain'
import { formatMoney, formatNumber } from '@/utils/money'

const { data, loading, error, load } = useDetail<Dashboard>(() => fetchDashboard())

/** 每次 load 结束时记一下时间，放在页面上，方便判断是不是旧数据 */
const updatedAt = ref('')

async function refresh(): Promise<void> {
  await load()
  if (!error.value) {
    const now = new Date()
    const pad = (n: number): string => `${n}`.padStart(2, '0')
    updatedAt.value = `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
  }
}

const totals = computed(() => data.value?.totals ?? null)

const kpis = computed(() => {
  const value = totals.value
  if (!value) return []
  return [
    {
      label: '学生用户',
      value: formatNumber(value.students),
      hint: '已注册的学生账号',
      icon: User,
      tone: 'primary' as const,
    },
    {
      label: '订单总量',
      value: formatNumber(value.orders),
      hint: '平台累计产生的订单',
      icon: Tickets,
      tone: 'info' as const,
    },
    {
      label: '已完成交易额',
      value: formatMoney(value.gmv),
      hint: '仅统计已完成的订单',
      icon: Coin,
      tone: 'success' as const,
    },
    {
      label: '平台冻结总额',
      value: formatMoney(value.frozen),
      hint: '托管中、尚未结算的资金',
      icon: Wallet,
      tone: 'primary' as const,
    },
    {
      label: '待裁决申诉',
      value: formatNumber(value.openAppeals),
      hint: value.openAppeals > 0 ? '需要尽快裁决' : '暂无待办',
      icon: ChatLineSquare,
      tone: value.openAppeals > 0 ? ('warning' as const) : ('info' as const),
      to: '/appeals',
    },
    {
      label: '待处理举报',
      value: formatNumber(value.pendingReports),
      hint: value.pendingReports > 0 ? '存在内容待治理' : '暂无待办',
      icon: Bell,
      tone: value.pendingReports > 0 ? ('danger' as const) : ('info' as const),
      to: '/reports',
    },
  ]
})

/** 0 值的状态不进饼图（扇区画不出来，图例空占一行），但总量仍按全部状态算 */
const pieSlices = computed(() => (data.value?.distribution ?? []).filter((item) => item.total > 0))

const trendTotal = computed(() =>
  (data.value?.trend ?? []).reduce((sum, item) => sum + item.total, 0),
)

const todos = computed(() => {
  const value = totals.value
  if (!value) return []
  return [
    {
      key: 'appeals',
      title: '待裁决申诉',
      count: value.openAppeals,
      desc: '买家或卖家发起、等待平台给出资金归属结论的争议单',
      action: '去仲裁',
      to: '/appeals',
    },
    {
      key: 'reports',
      title: '待处理举报',
      count: value.pendingReports,
      desc: '学生提交的内容或行为举报，需要判定是否成立并决定联动处置',
      action: '去处理',
      to: '/reports',
    },
  ]
})

/* 指标口径：写清楚每个数字怎么算的，答辩和交接时不用再翻后端代码 */
const metrics = [
  { label: '已完成交易额', value: '状态为「已完成」的订单金额之和' },
  { label: '平台冻结总额', value: '所有钱包 frozen 字段之和，即托管中未结算的钱' },
  { label: '订单状态分布', value: '按六种状态分组统计，0 单的状态同样计入' },
  { label: '近七日成交', value: '按下单日期分组，含今天在内连续 7 天，无单的日子补 0' },
  { label: '待裁决 / 待处理', value: '状态为 OPEN 的申诉数与状态为 PENDING 的举报数' },
]

onMounted(refresh)
</script>

<template>
  <div>
    <PageHeader title="仪表盘" description="平台运营总览：用户与订单规模、托管资金、待办治理事项与近七日成交趋势。">
      <template #actions>
        <span v-if="updatedAt" class="dash-updated">更新于 {{ updatedAt }}</span>
        <el-button :loading="loading" @click="refresh">刷新</el-button>
      </template>
    </PageHeader>

    <ErrorState v-if="error" class="panel" :error="error" @retry="refresh" />

    <template v-else>
      <div v-if="totals" class="kpi-grid">
        <KpiCard v-for="item in kpis" :key="item.label" v-bind="item" />
      </div>
      <div v-else class="kpi-grid">
        <div v-for="n in 6" :key="n" class="panel kpi">
          <el-skeleton :rows="2" animated />
        </div>
      </div>

      <div v-if="data" class="chart-grid">
        <ChartCard title="近七日成交" :height="300">
          <template #extra>
            <span class="chart-card__hint">7 天共 {{ formatNumber(trendTotal) }} 单</span>
          </template>
          <TrendChart :points="data.trend" />
        </ChartCard>

        <ChartCard
          title="订单状态分布"
          :height="300"
          :empty="pieSlices.length === 0"
          empty-text="平台还没有产生订单"
        >
          <StatusPieChart :items="pieSlices" />
        </ChartCard>
      </div>

      <div v-if="data" class="grid-2 dash-bottom">
        <DetailSection title="待办治理" hint="按积压量排序，点进去直接处理">
          <ul class="todo-list">
            <li v-for="todo in todos" :key="todo.key" class="todo-item">
              <div class="todo-item__main">
                <div class="todo-item__head">
                  <span class="todo-item__title">{{ todo.title }}</span>
                  <span class="todo-item__count" :class="{ 'is-zero': todo.count === 0 }">
                    {{ formatNumber(todo.count) }}
                  </span>
                </div>
                <p class="todo-item__desc">{{ todo.desc }}</p>
              </div>
              <RouterLink :to="todo.to" class="todo-item__action">
                {{ todo.count > 0 ? todo.action : '查看' }}
              </RouterLink>
            </li>
          </ul>
        </DetailSection>

        <DetailSection title="指标口径" hint="每个数字的算法，避免口径不一致">
          <dl class="metric-list">
            <div v-for="metric in metrics" :key="metric.label" class="metric-list__row">
              <dt>{{ metric.label }}</dt>
              <dd>{{ metric.value }}</dd>
            </div>
          </dl>
        </DetailSection>
      </div>
    </template>
  </div>
</template>
