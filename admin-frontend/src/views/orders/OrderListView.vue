<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import FilterBar from '@/components/FilterBar.vue'
import QueryTable from '@/components/QueryTable.vue'
import Pager from '@/components/Pager.vue'
import StatusTag from '@/components/StatusTag.vue'
import MoneyText from '@/components/MoneyText.vue'
import TimeText from '@/components/TimeText.vue'
import { fetchOrders } from '@/api/admin'
import { usePagedQuery } from '@/composables/usePagedQuery'
import { useRouteFilters } from '@/composables/useRouteFilters'
import { orderStatusSelect, postTypeSelect } from '@/constants/options'
import type { OrderRow } from '@/types/domain'
import type { OrderFilters } from '@/types/query'

const router = useRouter()

const DEFAULT_FILTERS: OrderFilters = {
  status: '',
  type: '',
  buyerId: '',
  sellerId: '',
  from: '',
  through: '',
}

const { filters, page, hydrate, apply, goPage, reset } = useRouteFilters<OrderFilters>(DEFAULT_FILTERS)

const { items, loading, error, hasNext, canNext, totalPages, load } = usePagedQuery<OrderRow, OrderFilters>(
  (params, signal) => fetchOrders(params, signal),
  () => ({ ...filters }),
)

/** 日期区间单独存：el-date-picker 的 daterange 只认数组，和 from/through 两个字段对不上 */
const dateRange = ref<[string, string] | null>(null)
const formError = ref('')

const hasFilters = computed(() =>
  Object.entries(filters).some(([, value]) => typeof value === 'string' && value.trim() !== ''),
)

function rangeFromFilters(): void {
  dateRange.value = filters.from && filters.through ? [filters.from, filters.through] : null
}

/** 数字 ID 校验放在请求之前：'abc' 传给后端会变成 HTTP 400 + 40000，错误信息对用户没意义 */
function validate(): boolean {
  formError.value = ''
  for (const [label, value] of [
    ['出资方 ID', filters.buyerId],
    ['接单方 ID', filters.sellerId],
  ] as const) {
    if (value.trim() && !/^\d+$/.test(value.trim())) {
      formError.value = `${label} 必须是数字（用户 ID），可先去「用户管理」查到 ID`
      return false
    }
  }
  if (filters.from && filters.through && filters.through < filters.from) {
    formError.value = '结束日期不能早于开始日期'
    return false
  }
  return true
}

async function submit(): Promise<void> {
  if (!validate()) return
  const [from, through] = dateRange.value ?? ['', '']
  filters.from = from
  filters.through = through
  // 先写 URL 再查：顺序反了会出现「表格是旧数据、地址栏是新条件」
  await apply()
  await load(0)
}

async function changePage(target: number): Promise<void> {
  await goPage(target)
  await load(target)
}

async function clearAll(): Promise<void> {
  formError.value = ''
  dateRange.value = null
  await reset()
  await load(0)
}

/** el-table 的行插槽给的是 DefaultRow（宽松记录类型），这里只关心 id，顺手挡掉异常值 */
function openDetail(row: unknown): void {
  const id = Number((row as { id?: unknown } | null)?.id)
  if (!Number.isInteger(id) || id <= 0) return
  void router.push({ name: 'order-detail', params: { id: String(id) } })
}

onMounted(async () => {
  hydrate()
  rangeFromFilters()
  await load(page.value)
})
</script>

<template>
  <div>
    <PageHeader
      title="订单管理"
      description="按状态、交易类型、交易双方与下单日期检索订单。点击任意一行查看订单摘要、交易时间线与资金流水。"
    />

    <FilterBar :loading="loading" @submit="submit" @reset="clearAll">
      <div class="filter-field">
        <label for="f-status">订单状态</label>
        <el-select id="f-status" v-model="filters.status" placeholder="全部状态">
          <el-option label="全部状态" value="" />
          <el-option v-for="option in orderStatusSelect" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
      </div>

      <div class="filter-field">
        <label for="f-type">交易类型</label>
        <el-select id="f-type" v-model="filters.type" placeholder="全部类型">
          <el-option label="全部类型" value="" />
          <el-option v-for="option in postTypeSelect" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
      </div>

      <div class="filter-field">
        <label for="f-buyer">出资方 ID</label>
        <el-input id="f-buyer" v-model="filters.buyerId" placeholder="精确用户 ID" clearable />
      </div>

      <div class="filter-field">
        <label for="f-seller">接单方 ID</label>
        <el-input id="f-seller" v-model="filters.sellerId" placeholder="精确用户 ID" clearable />
      </div>

      <div class="filter-field filter-field--wide">
        <label for="f-range">下单日期</label>
        <el-date-picker
          id="f-range"
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          unlink-panels
          style="width: 100%"
        />
      </div>
    </FilterBar>

    <p v-if="formError" class="form-error">{{ formError }}</p>

    <QueryTable
      :loading="loading"
      :error="error"
      :empty="items.length === 0"
      :empty-text="page > 0 ? '这一页没有订单了' : hasFilters ? '没有符合条件的订单' : '平台还没有产生订单'"
      :empty-hint="
        page > 0
          ? '筛选条件变化后页码可能已经翻过头，回到第一页看看。'
          : hasFilters
            ? '换个条件再试，或点「重置」查看全部订单。'
            : '学生发起悬赏或购买服务后会在这里出现。'
      "
      @retry="load()"
    >
      <el-table :data="items" row-key="id" class="data-table order-table" @row-click="openDetail">
        <el-table-column label="订单号" width="104">
          <template #default="{ row }">
            <RouterLink class="order-table__id" :to="{ name: 'order-detail', params: { id: row.id } }" @click.stop>
              #{{ row.id }}
            </RouterLink>
          </template>
        </el-table-column>

        <el-table-column label="类型" width="106">
          <template #default="{ row }"><StatusTag kind="postType" :value="row.type" /></template>
        </el-table-column>

        <el-table-column label="交易标题" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.title }}</template>
        </el-table-column>

        <el-table-column label="出资方" width="92">
          <template #default="{ row }"><span class="mono">#{{ row.buyerId }}</span></template>
        </el-table-column>

        <el-table-column label="接单方" width="92">
          <template #default="{ row }">
            <span v-if="row.sellerId" class="mono">#{{ row.sellerId }}</span>
            <span v-else class="text-muted">未接单</span>
          </template>
        </el-table-column>

        <el-table-column label="金额" width="122" align="right">
          <template #default="{ row }"><MoneyText :value="row.amount" /></template>
        </el-table-column>

        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag kind="orderStatus" :value="row.status" /></template>
        </el-table-column>

        <el-table-column label="下单时间" width="150">
          <template #default="{ row }"><TimeText :value="row.createdAt" /></template>
        </el-table-column>

        <el-table-column label="操作" width="76" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <template #empty-action>
        <el-button v-if="page > 0" @click="changePage(0)">回到第一页</el-button>
        <el-button v-else-if="hasFilters" @click="clearAll">清除筛选</el-button>
      </template>

      <template #pager>
        <Pager
          :page="page"
          :has-next="hasNext && canNext"
          :total-pages="totalPages"
          :loading="loading"
          @change="changePage"
        />
      </template>
    </QueryTable>
  </div>
</template>
