<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import FilterBar from '@/components/FilterBar.vue'
import QueryTable from '@/components/QueryTable.vue'
import Pager from '@/components/Pager.vue'
import StatusTag from '@/components/StatusTag.vue'
import TimeText from '@/components/TimeText.vue'
import { fetchAppeals } from '@/api/admin'
import { usePagedQuery } from '@/composables/usePagedQuery'
import { useRouteFilters } from '@/composables/useRouteFilters'
import { appealStatusSelect } from '@/constants/options'
import type { Appeal } from '@/types/domain'
import type { AppealFilters } from '@/types/query'

const router = useRouter()

/** 和举报页同样的工作台语义：这一页默认只看待裁决，不是档案室 */
const DEFAULT_FILTERS: AppealFilters = {
  status: 'OPEN',
}

const { filters, page, hydrate, apply, goPage, reset } = useRouteFilters<AppealFilters>(DEFAULT_FILTERS)

/**
 * 申诉接口是唯一返回 totalPages 的列表。api 层已把 totalPages 反推成 hasNext，
 * 所以这里仍然只讨论 hasNext —— 页面不感知两套分页结构的差异。
 */
const { items, loading, error, hasNext, canNext, totalPages, load } = usePagedQuery<Appeal, AppealFilters>(
  (params, signal) => fetchAppeals(params, signal),
  () => ({ ...filters }),
)

const isFiltered = computed(() => filters.status !== 'OPEN')

async function submit(): Promise<void> {
  // 先写 URL 再查：顺序反了会出现「表格是旧数据、地址栏是新条件」
  await apply()
  await load(0)
}

async function changePage(target: number): Promise<void> {
  await goPage(target)
  await load(target)
}

async function clearAll(): Promise<void> {
  await reset()
  await load(0)
}

function openDetail(row: unknown): void {
  const id = Number((row as { id?: unknown } | null)?.id)
  if (!Number.isInteger(id) || id <= 0) return
  void router.push({ name: 'appeal-detail', params: { id: String(id) } })
}

function openOrder(row: unknown): void {
  const id = Number((row as { orderId?: unknown } | null)?.orderId)
  if (!Number.isInteger(id) || id <= 0) return
  void router.push({ name: 'order-detail', params: { id: String(id) } })
}

onMounted(async () => {
  hydrate()
  await load(page.value)
})
</script>

<template>
  <div>
    <PageHeader
      title="申诉仲裁"
      description="学生在订单纠纷中发起的申诉。默认只看待裁决；裁决会立即移动托管款，且不可撤销。"
    >
      <template #actions>
        <el-button :loading="loading" @click="load()">刷新</el-button>
      </template>
    </PageHeader>

    <FilterBar :loading="loading" @submit="submit" @reset="clearAll">
      <div class="filter-field">
        <label for="a-status">申诉状态</label>
        <el-select id="a-status" v-model="filters.status" placeholder="全部状态">
          <el-option label="全部状态" value="" />
          <el-option v-for="option in appealStatusSelect" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
      </div>
    </FilterBar>

    <QueryTable
      :loading="loading"
      :error="error"
      :empty="items.length === 0"
      :empty-text="
        page > 0
          ? '这一页没有申诉了'
          : filters.status === 'OPEN'
            ? '没有待裁决的申诉'
            : '没有符合条件的申诉'
      "
      :empty-hint="
        page > 0
          ? '筛选条件变化后页码可能已经翻过头，回到第一页看看。'
          : filters.status === 'OPEN'
            ? '所有申诉都已结束。把「申诉状态」改成「全部状态」可以查看历史记录。'
            : '换个状态再试，或点「重置」回到待裁决视图。'
      "
      @retry="load()"
    >
      <el-table :data="items" row-key="id" class="data-table" @row-click="openDetail">
        <el-table-column label="编号" width="84">
          <template #default="{ row }">
            <RouterLink class="order-table__id" :to="{ name: 'appeal-detail', params: { id: row.id } }" @click.stop>
              #{{ row.id }}
            </RouterLink>
          </template>
        </el-table-column>

        <el-table-column label="关联订单" width="100">
          <template #default="{ row }">
            <button type="button" class="link-button" @click.stop="openOrder(row)">#{{ row.orderId }}</button>
          </template>
        </el-table-column>

        <el-table-column label="申诉人" width="86">
          <template #default="{ row }"><span class="mono">#{{ row.appellantId }}</span></template>
        </el-table-column>

        <el-table-column label="发起前状态" width="104">
          <template #default="{ row }"><StatusTag kind="orderStatus" :value="row.fromStatus" /></template>
        </el-table-column>

        <el-table-column label="申诉理由" min-width="250" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reason }}</template>
        </el-table-column>

        <el-table-column label="发起时间" width="146">
          <template #default="{ row }"><TimeText :value="row.createdAt" /></template>
        </el-table-column>

        <el-table-column label="状态" width="96">
          <template #default="{ row }"><StatusTag kind="appealStatus" :value="row.status" /></template>
        </el-table-column>

        <el-table-column label="裁决结果" width="128">
          <template #default="{ row }">
            <StatusTag v-if="row.outcome" kind="appealOutcome" :value="row.outcome" />
            <span v-else-if="row.status === 'OPEN'" class="text-muted">待裁决</span>
            <span v-else class="text-muted">未裁决</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="88" align="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'OPEN'" link type="primary" @click.stop="openDetail(row)">去裁决</el-button>
            <el-button v-else link @click.stop="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <template #empty-action>
        <el-button v-if="page > 0" @click="changePage(0)">回到第一页</el-button>
        <el-button v-else-if="isFiltered" @click="clearAll">重置筛选</el-button>
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
