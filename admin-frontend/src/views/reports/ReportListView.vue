<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import FilterBar from '@/components/FilterBar.vue'
import QueryTable from '@/components/QueryTable.vue'
import Pager from '@/components/Pager.vue'
import StatusTag from '@/components/StatusTag.vue'
import TimeText from '@/components/TimeText.vue'
import { banUser, closeTradePost, deletePost, fetchModerationTarget, fetchReports } from '@/api/admin'
import { usePagedQuery } from '@/composables/usePagedQuery'
import { useRouteFilters } from '@/composables/useRouteFilters'
import { useConfirm } from '@/composables/useConfirm'
import { dictLabel, reportStatusSelect, reportTargetTypeSelect } from '@/constants/options'
import type { ModerationTarget, ReportRow } from '@/types/domain'
import type { ReportFilters } from '@/types/query'
import type { ReportTargetType } from '@/types/enums'
import { toAppError } from '@/utils/errors'

const router = useRouter()
const { confirm } = useConfirm()

/** 举报页默认只看待处理：这一页是工作台，不是档案室 */
const DEFAULT_FILTERS: ReportFilters = {
  status: 'PENDING',
  targetType: '',
}

const { filters, page, hydrate, apply, goPage, reset } = useRouteFilters<ReportFilters>(DEFAULT_FILTERS)

const { items, loading, error, hasNext, canNext, totalPages, load } = usePagedQuery<ReportRow, ReportFilters>(
  (params, signal) => fetchReports(params, signal),
  () => ({ ...filters }),
)

const isFiltered = computed(() => filters.targetType !== '' || filters.status !== 'PENDING')

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
  void router.push({ name: 'report-detail', params: { id: String(id) } })
}

/* ───────────────── 按编号治理（不经举报，直接处置对象）───────────────── */

const mdOpen = ref(false)
const md = reactive({ type: 'POST' as ReportTargetType, id: '' })
const mdTarget = ref<ModerationTarget | null>(null)
const mdError = ref('')
const mdLoading = ref(false)
const mdSubmitting = ref(false)

/** 类型 → 唯一可执行的治理动作。后端没有「任选动作」的口子，动作由类型决定 */
const MODERATION_ACTIONS: Record<ReportTargetType, { label: string; impact: string }> = {
  POST: {
    label: '删除帖子',
    impact: '帖子将置为「已删除」，学生端不再展示。平台没有恢复接口，请先预览确认帖子内容。',
  },
  TRADE_POST: {
    label: '下架交易帖',
    impact: '交易帖将置为「已关闭」，不能再被购买或接单；已产生的订单不受影响。',
  },
  USER: {
    label: '封禁用户',
    impact: '仅可封禁学生账号；封禁后该学生无法登录、发布与交易，进行中的订单与资金不受影响。',
  },
}

const mdAction = computed(() => MODERATION_ACTIONS[md.type])

/** 换类型或改编号都要清掉旧预览，否则会拿旧对象的摘要去确认新对象 */
watch(
  () => [md.type, md.id],
  () => {
    mdTarget.value = null
    mdError.value = ''
  },
)

function resetMd(): void {
  md.type = 'POST'
  md.id = ''
  mdTarget.value = null
  mdError.value = ''
  mdLoading.value = false
  mdSubmitting.value = false
}

async function previewTarget(): Promise<void> {
  mdError.value = ''
  mdTarget.value = null
  const id = Number(md.id.trim())
  if (!Number.isInteger(id) || id <= 0) {
    mdError.value = '请输入数字编号（对象 ID）'
    return
  }
  mdLoading.value = true
  try {
    mdTarget.value = await fetchModerationTarget(md.type, id)
  } catch (cause) {
    mdError.value = toAppError(cause).message
  } finally {
    mdLoading.value = false
  }
}

async function executeModeration(): Promise<void> {
  const target = mdTarget.value
  if (!target) return
  const ok = await confirm({
    title: `确认${mdAction.value.label}？`,
    rows: [
      { label: '对象', value: `${dictLabel('reportTargetType', md.type)} #${target.id}` },
      { label: '当前状态', value: target.status },
      { label: '内容摘要', value: target.summary || '（空）' },
    ],
    impact: mdAction.value.impact,
    confirmText: mdAction.value.label,
  })
  if (!ok) return

  mdSubmitting.value = true
  try {
    if (md.type === 'POST') await deletePost(target.id)
    else if (md.type === 'TRADE_POST') await closeTradePost(target.id)
    else await banUser(target.id)
    ElMessage.success(`已${mdAction.value.label}：${dictLabel('reportTargetType', md.type)} #${target.id}`)
    mdOpen.value = false
    resetMd()
    await load()
  } catch (cause) {
    mdError.value = toAppError(cause).message
  } finally {
    mdSubmitting.value = false
  }
}

onMounted(async () => {
  hydrate()
  await load(page.value)
})
</script>

<template>
  <div>
    <PageHeader
      title="举报处理"
      description="学生提交的举报统一在这里裁决。默认只看待处理；裁决时可一并执行治理动作，但驳回举报不允许联动治理。"
    >
      <template #actions>
        <el-button :icon="Plus" @click="mdOpen = true">按编号治理</el-button>
        <el-button :loading="loading" @click="load()">刷新</el-button>
      </template>
    </PageHeader>

    <FilterBar :loading="loading" @submit="submit" @reset="clearAll">
      <div class="filter-field">
        <label for="r-status">处理状态</label>
        <el-select id="r-status" v-model="filters.status" placeholder="全部状态">
          <el-option label="全部状态" value="" />
          <el-option v-for="option in reportStatusSelect" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
      </div>

      <div class="filter-field">
        <label for="r-type">举报对象</label>
        <el-select id="r-type" v-model="filters.targetType" placeholder="全部对象">
          <el-option label="全部对象" value="" />
          <el-option v-for="option in reportTargetTypeSelect" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
      </div>
    </FilterBar>

    <QueryTable
      :loading="loading"
      :error="error"
      :empty="items.length === 0"
      :empty-text="
        page > 0
          ? '这一页没有举报了'
          : filters.status === 'PENDING' && !filters.targetType
            ? '没有待处理的举报'
            : '没有符合条件的举报'
      "
      :empty-hint="
        page > 0
          ? '筛选条件变化后页码可能已经翻过头，回到第一页看看。'
          : filters.status === 'PENDING' && !filters.targetType
            ? '所有举报都已裁决。把「处理状态」改成「全部状态」可以查看历史处理记录。'
            : '换个条件再试，或点「重置」回到默认视图。'
      "
      @retry="load()"
    >
      <el-table :data="items" row-key="id" class="data-table report-table" @row-click="openDetail">
        <el-table-column label="编号" width="84">
          <template #default="{ row }">
            <RouterLink class="order-table__id" :to="{ name: 'report-detail', params: { id: row.id } }" @click.stop>
              #{{ row.id }}
            </RouterLink>
          </template>
        </el-table-column>

        <el-table-column label="举报对象" width="164">
          <template #default="{ row }">
            <div class="report-target">
              <StatusTag kind="reportTargetType" :value="row.targetType" />
              <span class="mono">#{{ row.targetId }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="举报原因" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reason }}</template>
        </el-table-column>

        <el-table-column label="举报人" width="86">
          <template #default="{ row }"><span class="mono">#{{ row.reporterId }}</span></template>
        </el-table-column>

        <el-table-column label="举报时间" width="146">
          <template #default="{ row }"><TimeText :value="row.createdAt" /></template>
        </el-table-column>

        <el-table-column label="状态" width="96">
          <template #default="{ row }"><StatusTag kind="reportStatus" :value="row.status" /></template>
        </el-table-column>

        <el-table-column label="处置结论" width="150">
          <template #default="{ row }">
            <div v-if="row.status === 'HANDLED'" class="report-verdict">
              <StatusTag kind="reportResult" :value="row.result" />
              <StatusTag v-if="row.moderationAction" kind="moderationAction" :value="row.moderationAction" />
            </div>
            <span v-else class="text-muted">待裁决</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="82" align="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" link type="primary" @click.stop="openDetail(row)">处理</el-button>
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

    <!-- 按编号治理：没有学生举报、但管理员主动发现违规内容时使用 -->
    <el-dialog v-model="mdOpen" title="按编号治理" width="520px" :close-on-click-modal="false" @closed="resetMd">
      <p class="md-intro">
        直接对社区帖子、交易帖或用户执行治理动作，不依赖举报记录。动作会写入审计日志，可在「审计日志」追溯。
      </p>

      <div class="md-form">
        <div class="filter-field">
          <label for="md-type">对象类型</label>
          <el-select id="md-type" v-model="md.type">
            <el-option
              v-for="option in reportTargetTypeSelect"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </div>
        <div class="filter-field">
          <label for="md-id">对象编号</label>
          <el-input id="md-id" v-model="md.id" placeholder="数字 ID" clearable @keyup.enter="previewTarget" />
        </div>
      </div>

      <div v-if="mdTarget" class="md-preview">
        <div class="md-preview__head">
          <StatusTag kind="reportTargetType" :value="md.type" />
          <span class="mono">#{{ mdTarget.id }}</span>
          <span class="md-preview__status">当前状态 {{ mdTarget.status }}</span>
        </div>
        <p class="md-preview__summary">{{ mdTarget.summary || '（内容为空）' }}</p>
      </div>

      <p v-if="mdError" class="form-error">{{ mdError }}</p>

      <template #footer>
        <el-button @click="mdOpen = false">取消</el-button>
        <el-button :loading="mdLoading" @click="previewTarget">预览对象</el-button>
        <el-button type="danger" :disabled="!mdTarget" :loading="mdSubmitting" @click="executeModeration">
          {{ mdAction.label }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
