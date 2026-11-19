<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter, type RouteLocationRaw } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import FilterBar from '@/components/FilterBar.vue'
import QueryTable from '@/components/QueryTable.vue'
import Pager from '@/components/Pager.vue'
import StatusTag from '@/components/StatusTag.vue'
import TimeText from '@/components/TimeText.vue'
import { fetchAudit } from '@/api/admin'
import { usePagedQuery } from '@/composables/usePagedQuery'
import { useRouteFilters } from '@/composables/useRouteFilters'
import { useAuthStore } from '@/stores/auth'
import { auditActionSelect, dictLabel } from '@/constants/options'
import type { AuditRow } from '@/types/domain'
import type { AuditFilters } from '@/types/query'

const auth = useAuthStore()
const router = useRouter()
const myId = computed(() => auth.user?.userId ?? null)

const DEFAULT_FILTERS: AuditFilters = {
  adminId: '',
  action: '',
  from: '',
  through: '',
}

const { filters, page, hydrate, apply, goPage, reset } = useRouteFilters<AuditFilters>(DEFAULT_FILTERS)

const { items, loading, error, hasNext, canNext, totalPages, load } = usePagedQuery<AuditRow, AuditFilters>(
  (params, signal) => fetchAudit(params, signal),
  () => ({ ...filters }),
)

/** 日期区间单独存：daterange 只认数组，和 from/through 两个字段对不上 */
const dateRange = ref<[string, string] | null>(null)
const formError = ref('')
const updatedAt = ref('')

const isFiltered = computed(() =>
  Object.entries(filters).some(([, value]) => typeof value === 'string' && value.trim() !== ''),
)

/** 只统计本页，不冒充全站口径 —— 后端不给总数，也没有聚合接口 */
const summary = computed(() => {
  const list = items.value
  return {
    count: list.length,
    admins: new Set(list.map((row) => row.adminId)).size,
    actions: new Set(list.map((row) => row.action)).size,
  }
})

function validate(): boolean {
  formError.value = ''
  if (filters.adminId.trim() && !/^\d+$/.test(filters.adminId.trim())) {
    formError.value = '管理员编号必须是正整数'
    return false
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

async function onlyMine(): Promise<void> {
  if (myId.value == null) return
  filters.adminId = String(myId.value)
  await submit()
}

async function refresh(): Promise<void> {
  await load()
  if (!error.value) {
    const now = new Date()
    const pad = (n: number): string => `${n}`.padStart(2, '0')
    updatedAt.value = `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
  }
}

/* ───────────────────────────── 目标对象 ───────────────────────────── */

interface TargetRef {
  label: string
  to?: RouteLocationRaw
  /** 没有可用于跳转的页面时，说明为什么 */
  untitled?: string
}

/**
 * 审计只存 targetType + targetId，能不能点进去取决于该类型有没有详情页：
 *   REPORT → 举报详情、APPEAL → 申诉裁决页、USER → 用户管理按编号筛
 *   POST / TRADE_POST → 管理端没有帖子详情页，只能显示编号
 *
 * 参数收 unknown：el-table 插槽给的是 DefaultRow，收窄成 AuditRow 会整列类型报错。
 */
function targetOf(row: unknown): TargetRef {
  const value = row as Partial<AuditRow> | null
  const id = Number(value?.targetId)
  const type = value?.targetType
  if (!Number.isFinite(id)) return { label: '—' }
  switch (type) {
    case 'REPORT':
      return { label: `举报 #${id}`, to: { name: 'report-detail', params: { id: String(id) } } }
    case 'APPEAL':
      return { label: `申诉 #${id}`, to: { name: 'appeal-detail', params: { id: String(id) } } }
    case 'USER':
      return { label: `用户 #${id}`, to: { name: 'users', query: { keyword: String(id) } } }
    case 'POST':
      return { label: `社区帖子 #${id}`, untitled: '社区帖子在管理端没有详情页，删除后也无法再查看内容' }
    case 'TRADE_POST':
      return { label: `交易帖 #${id}`, untitled: '交易帖在管理端没有详情页，可到订单管理按关联订单追溯' }
    default:
      return { label: `${dictLabel('auditTargetType', type)} #${id}` }
  }
}

/* ───────────────────────────── 详情抽屉 ───────────────────────────── */

const drawerOpen = ref(false)
const current = ref<AuditRow | null>(null)

function openRow(row: unknown): void {
  const value = row as AuditRow | null
  if (!value || typeof value.id !== 'number') return
  current.value = value
  drawerOpen.value = true
}

async function filterByAdmin(id: number): Promise<void> {
  drawerOpen.value = false
  filters.adminId = String(id)
  await submit()
}

async function filterByAction(action: string): Promise<void> {
  drawerOpen.value = false
  filters.action = action
  await submit()
}

/** 抽屉里的跳转要先关抽屉，否则返回时抽屉还盖在列表上 */
function goTarget(to: RouteLocationRaw): void {
  drawerOpen.value = false
  void router.push(to)
}

onMounted(async () => {
  hydrate()
  // 日期从 URL 还原：rangeFromFilters 必须在首屏查询前跑，否则刷新后日期框是空的
  dateRange.value = filters.from && filters.through ? [filters.from, filters.through] : null
  await load(page.value)
})
</script>

<template>
  <div>
    <PageHeader
      title="审计日志"
      description="管理员对内容、账号、举报与纠纷的每一次处置都在这里留痕。记录只读，管理端不提供编辑与删除入口。"
    >
      <template #actions>
        <span v-if="updatedAt" class="audit-updated">更新于 {{ updatedAt }}</span>
        <el-button :icon="Refresh" :loading="loading" @click="refresh">刷新</el-button>
      </template>
    </PageHeader>

    <FilterBar :loading="loading" @submit="submit" @reset="clearAll">
      <div class="filter-field">
        <label for="a-admin">管理员编号</label>
        <el-input id="a-admin" v-model="filters.adminId" placeholder="精确 ID" clearable />
      </div>

      <div class="filter-field">
        <label for="a-action">动作</label>
        <el-select id="a-action" v-model="filters.action" placeholder="全部动作">
          <el-option label="全部动作" value="" />
          <el-option
            v-for="option in auditActionSelect"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </div>

      <div class="filter-field filter-field--wide">
        <label for="a-range">操作日期</label>
        <el-date-picker
          id="a-range"
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

    <p v-if="formError" class="form-error form-error--bar">{{ formError }}</p>

    <p class="audit-note">
      只有写操作才留痕：删帖、下架交易帖、封禁解封、处置举报、申诉裁决；登录、查看与筛选都不记录。
      审计与业务写在同一个事务里，操作失败会一起回滚，所以「有记录」即代表当时执行成功，不存在失败记录。
      删帖只记帖子编号、不记标题——后端写入的是固定文案「删除社区帖子」。
    </p>

    <p class="audit-summary">
      <span class="audit-summary__item">本页 <strong>{{ summary.count }}</strong> 条</span>
      <span class="audit-summary__sep" aria-hidden="true">·</span>
      <span class="audit-summary__item">涉及 <strong>{{ summary.admins }}</strong> 位管理员</span>
      <span class="audit-summary__sep" aria-hidden="true">·</span>
      <span class="audit-summary__item"><strong>{{ summary.actions }}</strong> 种动作</span>
      <span class="audit-summary__hint">只统计当前这一页。点任意一行查看完整详情。</span>
      <button v-if="myId != null" type="button" class="audit-inline-link" @click="onlyMine">
        只看我的操作（#{{ myId }}）
      </button>
    </p>

    <QueryTable
      :loading="loading"
      :error="error"
      :empty="items.length === 0"
      :empty-text="
        page > 0
          ? '这一页没有更早的记录了'
          : isFiltered
            ? '没有符合条件的审计记录'
            : '还没有产生任何审计记录'
      "
      :empty-hint="
        page > 0
          ? '筛选条件变化后页码可能已经翻过头，回到第一页看看。'
          : isFiltered
            ? '管理员编号是精确匹配；动作与日期都选对了吗？'
            : '管理员执行第一次处置（删帖、封禁、裁决等）后，这里才会有记录。'
      "
      @retry="load()"
    >
      <el-table :data="items" row-key="id" class="data-table audit-table" @row-click="openRow">
        <el-table-column label="时间" width="148">
          <template #default="{ row }"><TimeText :value="row.createdAt" /></template>
        </el-table-column>

        <el-table-column label="动作" width="118">
          <template #default="{ row }"><StatusTag kind="auditAction" :value="row.action" /></template>
        </el-table-column>

        <el-table-column label="目标对象" width="152">
          <template #default="{ row }">
            <RouterLink v-if="targetOf(row).to" class="audit-target" :to="targetOf(row).to!" @click.stop>
              {{ targetOf(row).label }}
            </RouterLink>
            <span v-else class="audit-target is-plain" :title="targetOf(row).untitled">
              {{ targetOf(row).label }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="详情" min-width="240" show-overflow-tooltip>
          <template #default="{ row }"><span class="audit-details">{{ row.details || '—' }}</span></template>
        </el-table-column>

        <el-table-column label="操作人" width="104">
          <template #default="{ row }">
            <RouterLink
              class="audit-admin"
              :to="{ name: 'users', query: { keyword: String(row.adminId) } }"
              @click.stop
            >
              #{{ row.adminId }}
            </RouterLink>
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

    <el-drawer v-model="drawerOpen" title="审计记录" size="460px" class="audit-drawer">
      <template v-if="current">
        <div class="audit-drawer__head">
          <StatusTag kind="auditAction" :value="current.action" />
          <span class="audit-drawer__id mono">#{{ current.id }}</span>
        </div>

        <dl class="audit-drawer__meta">
          <div>
            <dt>操作时间</dt>
            <dd><TimeText :value="current.createdAt" /></dd>
          </div>
          <div>
            <dt>操作人</dt>
            <dd>管理员 #{{ current.adminId }}</dd>
          </div>
          <div>
            <dt>目标对象</dt>
            <dd>{{ targetOf(current).label }}</dd>
          </div>
          <div>
            <dt>目标类型</dt>
            <dd>{{ dictLabel('auditTargetType', current.targetType) }}</dd>
          </div>
        </dl>

        <h4 class="audit-drawer__label">详情原文</h4>
        <p class="audit-text">{{ current.details || '—' }}</p>

        <p class="audit-drawer__note">
          这条记录由后端在处置事务中写入，原文不可编辑或删除。若显示内容与处置结果不符，该改的是写入文案，不是这条日志。
        </p>
      </template>

      <template #footer>
        <div v-if="current" class="audit-drawer__actions">
          <el-button
            v-if="targetOf(current).to"
            type="primary"
            class="audit-drawer__primary"
            @click="goTarget(targetOf(current).to!)"
          >
            打开{{ targetOf(current).label }}
          </el-button>
          <el-button @click="filterByAdmin(current.adminId)">该管理员的操作</el-button>
          <el-button @click="filterByAction(current.action)">
            全部{{ dictLabel('auditAction', current.action) }}
          </el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>
