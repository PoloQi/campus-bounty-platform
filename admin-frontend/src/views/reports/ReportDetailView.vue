<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, WarningFilled } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import DetailSection from '@/components/DetailSection.vue'
import StatusTag from '@/components/StatusTag.vue'
import TimeText from '@/components/TimeText.vue'
import ErrorState from '@/components/ErrorState.vue'
import { fetchModerationTarget, fetchReport, handleReport } from '@/api/admin'
import { useDetail } from '@/composables/useDetail'
import { useConfirm } from '@/composables/useConfirm'
import { dictLabel } from '@/constants/options'
import type { ModerationTarget, ReportRow } from '@/types/domain'
import type { ReportResult, ReportTargetType } from '@/types/enums'
import { AppError, toAppError } from '@/utils/errors'

const route = useRoute()
const router = useRouter()
const { confirm } = useConfirm()

/** 路由参数是 string | string[]，且可能是 'abc'。非法值不发请求，直接给失败态 */
const reportId = computed(() => {
  const raw = route.params.id
  const text = Array.isArray(raw) ? raw[0] : raw
  const value = Number(text)
  return Number.isInteger(value) && value > 0 ? value : null
})

const invalidError = new AppError('notfound', '举报编号无效，请从举报列表进入')

const { data: report, loading, error, load } = useDetail<ReportRow>((signal) => {
  const id = reportId.value
  if (id === null) return Promise.reject(invalidError)
  return fetchReport(id, signal)
})

/* ───────────────── 治理对象预览 ───────────────── */

const target = ref<ModerationTarget | null>(null)
const targetError = ref<AppError | null>(null)
const targetLoading = ref(false)
let targetController: AbortController | null = null

async function loadTarget(source: ReportRow): Promise<void> {
  targetController?.abort()
  const current = new AbortController()
  targetController = current
  targetLoading.value = true
  targetError.value = null
  target.value = null
  try {
    target.value = await fetchModerationTarget(source.targetType, source.targetId, current.signal)
  } catch (cause) {
    if (!current.signal.aborted) targetError.value = toAppError(cause)
  } finally {
    if (!current.signal.aborted) targetLoading.value = false
  }
}

// 只跟 id 走：裁决后重新拉详情时 id 不变，不必再打一次治理预览
watch(
  () => report.value?.id,
  () => {
    const source = report.value
    if (source) void loadTarget(source)
  },
)

onBeforeUnmount(() => targetController?.abort())

/* ───────────────── 处置表单 ───────────────── */

/** 类型决定唯一的治理动作，后端没有「任选动作」的口子 */
const MODERATION_TEXT: Record<ReportTargetType, { code: string; label: string }> = {
  POST: { code: 'DELETE_POST', label: '删除社区帖子' },
  TRADE_POST: { code: 'CLOSE_TRADE_POST', label: '下架交易帖' },
  USER: { code: 'BAN_USER', label: '封禁该用户' },
}

const VERDICTS = [
  { value: 'VALID', label: '举报成立', desc: '对象确属违规，可同时执行治理动作' },
  { value: 'REJECTED', label: '举报驳回', desc: '未发现违规，只记录结论，不做治理' },
] as const

const form = reactive<{ result: ReportResult | ''; comment: string; moderate: boolean }>({
  result: '',
  comment: '',
  moderate: false,
})

const formError = ref('')
const submitting = ref(false)

const isPending = computed(() => report.value?.status === 'PENDING')
const actionText = computed(() => (report.value ? MODERATION_TEXT[report.value.targetType] : null))

function chooseResult(value: ReportResult): void {
  form.result = value
  // 驳回则强制取消治理勾选：后端对「驳回 + 治理」是直接报错的
  if (value !== 'VALID') form.moderate = false
  formError.value = ''
}

/** 治理开关为什么不可点 —— 每种原因都要说清楚，否则用户只会看到灰掉的复选框 */
const moderateBlockedReason = computed(() => {
  if (form.result !== 'VALID') return '选择「举报成立」后才能联动治理；驳回举报时后端会直接拒绝该组合。'
  if (targetLoading.value) return '正在确认治理对象是否仍然存在…'
  if (targetError.value) return '治理对象已不存在或不可访问，无法联动治理，但仍可单独裁决这条举报。'
  return ''
})

const canModerate = computed(() => form.result === 'VALID' && target.value !== null)

const commentLength = computed(() => form.comment.length)

const statusHint = computed(() => {
  const value = report.value
  if (!value) return ''
  if (value.status === 'PENDING') return '待裁决'
  const parts = [dictLabel('reportResult', value.result)]
  if (value.moderationAction) parts.push(dictLabel('moderationAction', value.moderationAction))
  return parts.join(' · ')
})

async function submit(): Promise<void> {
  const value = report.value
  if (!value) return
  formError.value = ''

  const result = form.result
  if (!result) {
    formError.value = '请先选择处理结论'
    return
  }
  const comment = form.comment.trim()
  if (!comment) {
    formError.value = '处理意见必填，会原样展示给举报人'
    return
  }
  if (commentLength.value > 160) {
    formError.value = `处理意见最多 160 字，当前 ${commentLength.value} 字`
    return
  }

  const moderate = form.moderate && result === 'VALID'
  const targetText = `${dictLabel('reportTargetType', value.targetType)} #${value.targetId}`

  const ok = await confirm({
    title: moderate ? '确认裁决并执行治理？' : '确认提交裁决？',
    rows: [
      { label: '举报编号', value: `#${value.id}` },
      { label: '治理对象', value: targetText },
      { label: '处理结论', value: dictLabel('reportResult', result) },
      { label: '联动治理', value: moderate && actionText.value ? actionText.value.label : '不执行' },
    ],
    impact: moderate
      ? `提交后会立即${actionText.value?.label ?? '执行治理动作'}，并一并写入两条审计（处理举报 + 治理动作）。治理动作没有撤销接口，请确认对象内容无误。`
      : '提交后该举报置为「已处理」，并向举报人推送处理结果通知。',
    confirmText: moderate ? '确认并治理' : '确认提交',
  })
  if (!ok) return

  submitting.value = true
  try {
    await handleReport(value.id, { result, comment, ...(moderate ? { moderate: true } : {}) })
    ElMessage.success(moderate ? '已裁决并执行治理' : '已裁决')
    await load()
  } catch (cause) {
    // 40000 的高频场景：别的管理员抢先处理了同一条举报
    formError.value = toAppError(cause).message
  } finally {
    submitting.value = false
  }
}

function backToList(): void {
  const state = window.history.state as { back?: string | null } | null
  if (state?.back) router.back()
  else void router.push({ name: 'reports' })
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div>
    <PageHeader
      :title="reportId ? `举报 #${reportId}` : '举报详情'"
      description="先看治理对象的原文摘要，再决定成立或驳回；成立时可一并执行治理动作。"
    >
      <template #actions>
        <el-button :icon="ArrowLeft" @click="backToList">返回列表</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <ErrorState v-if="error" class="panel" :error="error" @retry="load" />

    <div v-else-if="loading && !report" class="panel detail-skeleton">
      <el-skeleton :rows="8" animated />
    </div>

    <template v-else-if="report">
      <section class="panel detail-hero">
        <div class="detail-hero__main">
          <div class="detail-hero__line">
            <span class="detail-hero__id mono">#{{ report.id }}</span>
            <StatusTag kind="reportStatus" :value="report.status" />
            <StatusTag kind="reportTargetType" :value="report.targetType" />
          </div>
          <h2 class="detail-hero__title">{{ report.reason }}</h2>
          <p class="detail-hero__meta">
            举报人 <span class="mono">#{{ report.reporterId }}</span>
            <span class="detail-hero__dot">·</span>对象
            <span class="mono">#{{ report.targetId }}</span>
            <span class="detail-hero__dot">·</span>提交于 <TimeText :value="report.createdAt" />
          </p>
        </div>
        <div class="report-hero__verdict" :class="{ 'is-pending': isPending }">
          <span class="report-hero__verdict-label">{{ isPending ? '当前状态' : '裁决结果' }}</span>
          <span class="report-hero__verdict-value">{{ statusHint }}</span>
          <span v-if="!isPending && report.handledAt" class="report-hero__verdict-time">
            <TimeText :value="report.handledAt" />
          </span>
        </div>
      </section>

      <div class="detail-grid">
        <div class="detail-grid__main">
          <DetailSection title="举报信息" hint="来自 t_report">
            <div class="kv-grid">
              <div class="kv">
                <span class="kv__label">举报编号</span>
                <span class="kv__value mono">#{{ report.id }}</span>
              </div>
              <div class="kv">
                <span class="kv__label">当前状态</span>
                <span class="kv__value"><StatusTag kind="reportStatus" :value="report.status" /></span>
              </div>
              <div class="kv">
                <span class="kv__label">对象类型</span>
                <span class="kv__value"><StatusTag kind="reportTargetType" :value="report.targetType" /></span>
              </div>
              <div class="kv">
                <span class="kv__label">对象编号</span>
                <span class="kv__value mono">#{{ report.targetId }}</span>
              </div>
              <div class="kv">
                <span class="kv__label">举报人</span>
                <span class="kv__value mono">#{{ report.reporterId }}</span>
              </div>
              <div class="kv">
                <span class="kv__label">提交时间</span>
                <span class="kv__value"><TimeText :value="report.createdAt" /></span>
              </div>
            </div>
            <p class="report-reason">{{ report.reason }}</p>
          </DetailSection>

          <!-- 已处理：结论优先展示 -->
          <DetailSection v-if="!isPending" title="处理结果" hint="裁决后不可修改">
            <div class="kv-grid">
              <div class="kv">
                <span class="kv__label">处理结论</span>
                <span class="kv__value"><StatusTag kind="reportResult" :value="report.result" /></span>
              </div>
              <div class="kv">
                <span class="kv__label">联动治理</span>
                <span class="kv__value">
                  <StatusTag v-if="report.moderationAction" kind="moderationAction" :value="report.moderationAction" />
                  <span v-else class="text-muted">未执行治理</span>
                </span>
              </div>
              <div class="kv">
                <span class="kv__label">处理人</span>
                <span class="kv__value mono">{{ report.adminId ? `#${report.adminId}` : '—' }}</span>
              </div>
              <div class="kv">
                <span class="kv__label">处理时间</span>
                <span class="kv__value"><TimeText :value="report.handledAt" /></span>
              </div>
            </div>
            <p class="report-comment">{{ report.comment || '（未填写处理意见）' }}</p>
          </DetailSection>

          <DetailSection title="治理对象" hint="处理前请核对原文，避免误删">
            <div v-if="targetLoading" class="detail-empty">正在读取对象摘要…</div>

            <div v-else-if="targetError" class="report-target-missing">
              <el-icon class="report-target-missing__icon"><WarningFilled /></el-icon>
              <div>
                <p class="report-target-missing__title">对象当前不可访问</p>
                <p class="report-target-missing__text">
                  {{ targetError.message }}。可能已被删除、下架或封禁。此状态下无法联动治理，但举报本身仍可裁决。
                </p>
              </div>
            </div>

            <template v-else-if="target">
              <div class="report-target-card">
                <div class="report-target-card__head">
                  <StatusTag kind="reportTargetType" :value="report.targetType" />
                  <span class="mono">#{{ target.id }}</span>
                  <span class="report-target-card__status">状态 {{ target.status }}</span>
                </div>
                <p class="report-target-card__summary">{{ target.summary || '（内容为空）' }}</p>
              </div>
            </template>
          </DetailSection>

          <!-- 待处理：处置表单 -->
          <DetailSection v-if="isPending" title="裁决与处置" hint="提交后不可修改，请核对对象与结论">
            <div class="verdict" role="radiogroup" aria-label="处理结论">
              <button
                v-for="option in VERDICTS"
                :key="option.value"
                type="button"
                class="verdict__card"
                :class="[`verdict__card--${option.value.toLowerCase()}`, { 'is-active': form.result === option.value }]"
                role="radio"
                :aria-checked="form.result === option.value"
                @click="chooseResult(option.value)"
              >
                <span class="verdict__label">{{ option.label }}</span>
                <span class="verdict__desc">{{ option.desc }}</span>
              </button>
            </div>

            <div class="form-field">
              <label class="form-field__label" for="handle-comment">
                处理意见
                <span class="form-field__hint">会原样展示给举报人·1–160 字</span>
              </label>
              <el-input
                id="handle-comment"
                v-model="form.comment"
                type="textarea"
                :rows="3"
                maxlength="160"
                show-word-limit
                placeholder="例如：经核对，内容确属违规，已按平台规则处置。"
              />
            </div>

            <div class="moderate-toggle" :class="{ 'is-disabled': !canModerate }">
              <el-checkbox v-model="form.moderate" :disabled="!canModerate">
                <span class="moderate-toggle__title">
                  同时执行治理：{{ actionText?.label ?? '—' }}
                  <code v-if="actionText" class="moderate-toggle__code">{{ actionText.code }}</code>
                </span>
              </el-checkbox>
              <p class="moderate-toggle__reason">
                {{ moderateBlockedReason || '将一并写入两条审计（处理举报 + 治理动作），举报人会收到站内通知。' }}
              </p>
            </div>

            <p v-if="formError" class="form-error">{{ formError }}</p>

            <div class="form-actions">
              <el-button type="primary" :loading="submitting" @click="submit">提交裁决</el-button>
              <span class="form-actions__note">
                处理意见 {{ commentLength }} / 160
              </span>
            </div>
          </DetailSection>

          <DetailSection v-else title="处置说明" hint="举报已结束，无需再次操作">
            <p class="detail-desc">
              这条举报已于 <TimeText :value="report.handledAt" /> 由管理员
              <span class="mono">#{{ report.adminId }}</span> 裁决为「{{ dictLabel('reportResult', report.result) }}」。
              平台不支持二次裁决；如需处理同一对象，请从列表进入新的举报，或使用「按编号治理」。
            </p>
          </DetailSection>
        </div>

        <aside class="detail-grid__side">
          <DetailSection title="对象定位">
            <div class="kv-list">
              <div class="kv">
                <span class="kv__label">对象类型</span>
                <span class="kv__value"><StatusTag kind="reportTargetType" :value="report.targetType" /></span>
              </div>
              <div class="kv">
                <span class="kv__label">对象编号</span>
                <span class="kv__value mono">#{{ report.targetId }}</span>
              </div>
              <div class="kv">
                <span class="kv__label">对象状态</span>
                <span class="kv__value">{{ target?.status ?? '不可访问' }}</span>
              </div>
            </div>
            <p class="side-note">
              <template v-if="report.targetType === 'USER'">
                可在「用户管理」用关键词 <span class="mono">#{{ report.targetId }}</span> 搜到该账号，核对其历史行为后再封禁。
              </template>
              <template v-else-if="report.targetType === 'TRADE_POST'">
                交易帖下架后不能再被购买或接单，已产生的订单不受影响。
              </template>
              <template v-else>社区帖子删除后学生端不再展示，平台没有恢复接口。</template>
            </p>
          </DetailSection>

          <DetailSection title="处置须知" hint="照抄后端规则，不是建议">
            <ul class="rule-list">
              <li>驳回举报时勾选治理会被后端直接拒绝，所以驳回后治理开关不可用。</li>
              <li>勾选治理只写一次操作，但审计里会出现两条：处理举报 + 治理动作。</li>
              <li>对象已被处理过时，治理动作仍然可执行（幂等），但对象不存在会整体失败。</li>
              <li>处理意见必填，会原样进入举报人的站内通知。</li>
            </ul>
          </DetailSection>
        </aside>
      </div>
    </template>
  </div>
</template>
