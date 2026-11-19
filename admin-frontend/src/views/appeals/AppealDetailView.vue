<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, WarningFilled } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import DetailSection from '@/components/DetailSection.vue'
import StatusTag from '@/components/StatusTag.vue'
import MoneyText from '@/components/MoneyText.vue'
import TimeText from '@/components/TimeText.vue'
import ErrorState from '@/components/ErrorState.vue'
import { arbitrateAppeal, fetchAppeal, fetchOrderDetail } from '@/api/admin'
import { useDetail } from '@/composables/useDetail'
import { useConfirm } from '@/composables/useConfirm'
import { dictLabel } from '@/constants/options'
import type { Appeal, OrderDetail } from '@/types/domain'
import type { AppealOutcome } from '@/types/enums'
import { AppError, toAppError } from '@/utils/errors'

const route = useRoute()
const router = useRouter()
const { confirm } = useConfirm()

/** 路由参数是 string | string[]，且可能是 'abc'。非法值不发请求，直接给失败态 */
const appealId = computed(() => {
  const raw = route.params.id
  const text = Array.isArray(raw) ? raw[0] : raw
  const value = Number(text)
  return Number.isInteger(value) && value > 0 ? value : null
})

const invalidError = new AppError('notfound', '申诉编号无效，请从申诉列表进入')

const { data: appeal, loading, error, load } = useDetail<Appeal>((signal) => {
  const id = appealId.value
  if (id === null) return Promise.reject(invalidError)
  return fetchAppeal(id, signal)
})

/* ───────────────── 关联订单证据 ───────────────── */

const order = ref<OrderDetail | null>(null)
const orderError = ref<AppError | null>(null)
const orderLoading = ref(false)
let orderController: AbortController | null = null

async function loadOrder(orderId: number): Promise<void> {
  orderController?.abort()
  const current = new AbortController()
  orderController = current
  orderLoading.value = true
  orderError.value = null
  try {
    order.value = await fetchOrderDetail(orderId, current.signal)
  } catch (cause) {
    if (!current.signal.aborted) {
      orderError.value = toAppError(cause)
      order.value = null
    }
  } finally {
    if (!current.signal.aborted) orderLoading.value = false
  }
}

/**
 * 依赖里必须带上 status：裁决成功后申诉变 RESOLVED，订单状态与流水
 * 同时被后端改掉，只按 orderId 监听会一直显示「纠纷中」的旧证据。
 */
watch(
  () => [appeal.value?.orderId, appeal.value?.status] as const,
  ([orderId]) => {
    if (typeof orderId === 'number' && orderId > 0) void loadOrder(orderId)
  },
)

onBeforeUnmount(() => orderController?.abort())

/* ───────────────── 裁决表单 ───────────────── */

const OUTCOMES = [
  {
    value: 'RELEASE_TO_SELLER',
    label: '打款给卖家',
    desc: '认定卖家已履约。托管款结算给卖家，订单置为「已完成」。',
    className: 'release',
  },
  {
    value: 'REFUND_TO_BUYER',
    label: '退款给买家',
    desc: '认定订单未履约。托管款退回买家，订单置为「已取消」。',
    className: 'refund',
  },
] as const

const form = reactive<{ outcome: AppealOutcome | ''; comment: string }>({
  outcome: '',
  comment: '',
})

const formError = ref('')
const submitting = ref(false)

const isOpen = computed(() => appeal.value?.status === 'OPEN')
const commentLength = computed(() => form.comment.length)
const amount = computed(() => order.value?.order.amount ?? null)

/** 申诉人是买家还是卖家 —— 决定这条理由该按哪一方的立场去读 */
const appellantRole = computed(() => {
  const value = appeal.value
  const data = order.value
  if (!value || !data) return ''
  if (value.appellantId === data.order.buyerId) return '买家'
  if (value.appellantId === data.order.sellerId) return '卖家'
  return '非交易方'
})

const statusHint = computed(() => {
  const value = appeal.value
  if (!value) return ''
  if (value.status === 'OPEN') return '待裁决'
  if (value.status === 'WITHDRAWN') return '学生已撤回'
  return dictLabel('appealOutcome', value.outcome)
})

function chooseOutcome(value: AppealOutcome): void {
  form.outcome = value
  formError.value = ''
}

async function submit(): Promise<void> {
  const value = appeal.value
  if (!value) return
  formError.value = ''

  const outcome = form.outcome
  if (!outcome) {
    formError.value = '请先选择裁决结果'
    return
  }
  const comment = form.comment.trim()
  if (!comment) {
    formError.value = '处理意见必填，会写入审计日志并推送给交易双方'
    return
  }
  if (comment.length > 500) {
    formError.value = `处理意见最多 500 字，当前 ${comment.length} 字`
    return
  }

  const release = outcome === 'RELEASE_TO_SELLER'
  const amountText = amount.value == null ? '托管款' : `托管款 ¥${amount.value.toFixed(2)}`
  const payee = order.value
    ? release
      ? `卖家 #${order.value.order.sellerId ?? '—'}`
      : `买家 #${order.value.order.buyerId}`
    : release
      ? '卖家'
      : '买家'

  const ok = await confirm({
    title: release ? '确认打款给卖家？' : '确认退款给买家？',
    rows: [
      { label: '申诉编号', value: `#${value.id}` },
      { label: '关联订单', value: `#${value.orderId}` },
      { label: '托管金额', value: amount.value == null ? '未知' : `¥${amount.value.toFixed(2)}` },
      { label: '裁决结果', value: dictLabel('appealOutcome', outcome) },
    ],
    impact: `${amountText} 将立即结算给${payee}，订单置为「${release ? '已完成' : '已取消'}」。裁决会写入审计日志并向交易双方推送通知，且没有撤销接口。`,
    confirmText: release ? '确认打款' : '确认退款',
  })
  if (!ok) return

  submitting.value = true
  try {
    await arbitrateAppeal(value.id, { outcome, comment })
    ElMessage.success(release ? '已裁决：托管款结算给卖家' : '已裁决：托管款退回买家')
    form.outcome = ''
    form.comment = ''
    // 重新拉申诉；watch 会连带把订单证据（状态 + 流水）刷新
    await load()
  } catch (cause) {
    // 40000 的高频场景：别的管理员抢先裁决，或学生刚撤回了申诉
    formError.value = toAppError(cause).message
  } finally {
    submitting.value = false
  }
}

function goOrder(): void {
  const id = appeal.value?.orderId
  if (id) void router.push({ name: 'order-detail', params: { id: String(id) } })
}

function backToList(): void {
  const state = window.history.state as { back?: string | null } | null
  if (state?.back) router.back()
  else void router.push({ name: 'appeals' })
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div>
    <PageHeader
      :title="appealId ? `申诉 #${appealId}` : '申诉详情'"
      description="先核对关联订单与托管流水，再在「打款给卖家」与「退款给买家」之间做出裁决。"
    >
      <template #actions>
        <el-button :icon="ArrowLeft" @click="backToList">返回列表</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <ErrorState v-if="error" class="panel" :error="error" @retry="load" />

    <div v-else-if="loading && !appeal" class="panel detail-skeleton">
      <el-skeleton :rows="8" animated />
    </div>

    <template v-else-if="appeal">
      <section class="panel detail-hero">
        <div class="detail-hero__main">
          <div class="detail-hero__line">
            <span class="detail-hero__id mono">#{{ appeal.id }}</span>
            <StatusTag kind="appealStatus" :value="appeal.status" />
            <StatusTag kind="orderStatus" :value="appeal.fromStatus" />
          </div>
          <h2 class="detail-hero__title">订单 #{{ appeal.orderId }} 的交易纠纷</h2>
          <p class="detail-hero__meta">
            申诉人 <span class="mono">#{{ appeal.appellantId }}</span>
            <span v-if="appellantRole" class="detail-hero__dot">·</span>{{ appellantRole }}
            <span class="detail-hero__dot">·</span>发起于 <TimeText :value="appeal.createdAt" />
          </p>
        </div>
        <div class="appeal-hero__verdict" :class="{ 'is-pending': isOpen }">
          <span class="appeal-hero__verdict-label">{{ isOpen ? '当前状态' : '裁决结果' }}</span>
          <span class="appeal-hero__verdict-value">{{ statusHint }}</span>
          <span v-if="!isOpen && appeal.handledAt" class="appeal-hero__verdict-time">
            <TimeText :value="appeal.handledAt" />
          </span>
        </div>
      </section>

      <div class="detail-grid">
        <div class="detail-grid__main">
          <DetailSection title="申诉信息" hint="来自 t_appeal">
            <div class="kv-grid">
              <div class="kv">
                <span class="kv__label">申诉编号</span>
                <span class="kv__value mono">#{{ appeal.id }}</span>
              </div>
              <div class="kv">
                <span class="kv__label">关联订单</span>
                <span class="kv__value">
                  <button type="button" class="link-button" @click="goOrder">#{{ appeal.orderId }}</button>
                </span>
              </div>
              <div class="kv">
                <span class="kv__label">申诉人</span>
                <span class="kv__value">
                  <span class="mono">#{{ appeal.appellantId }}</span>
                  <span v-if="appellantRole" class="text-muted">（{{ appellantRole }}）</span>
                </span>
              </div>
              <div class="kv">
                <span class="kv__label">发起前状态</span>
                <span class="kv__value"><StatusTag kind="orderStatus" :value="appeal.fromStatus" /></span>
              </div>
              <div class="kv">
                <span class="kv__label">当前状态</span>
                <span class="kv__value"><StatusTag kind="appealStatus" :value="appeal.status" /></span>
              </div>
              <div class="kv">
                <span class="kv__label">发起时间</span>
                <span class="kv__value"><TimeText :value="appeal.createdAt" /></span>
              </div>
            </div>
            <p class="appeal-reason">{{ appeal.reason }}</p>
          </DetailSection>

          <!-- 裁决前必须看到钱和流水，否则等于闭眼判案 -->
          <DetailSection title="订单与资金证据" hint="裁决前核对托管金额与流水">
            <div v-if="orderLoading && !order" class="detail-empty">正在读取关联订单…</div>

            <div v-else-if="orderError" class="appeal-order-missing">
              <el-icon class="appeal-order-missing__icon"><WarningFilled /></el-icon>
              <div>
                <p class="appeal-order-missing__title">关联订单不可读</p>
                <p class="appeal-order-missing__text">
                  {{ orderError.message }}。缺少订单证据时无法核对托管金额与流水，建议先查明原因再裁决。
                </p>
              </div>
            </div>

            <template v-else-if="order">
              <div class="appeal-order">
                <div class="appeal-order__amount">
                  <span class="appeal-order__amount-label">托管金额</span>
                  <MoneyText :value="order.order.amount" strong />
                </div>
                <div class="appeal-order__info">
                  <p class="appeal-order__title">{{ order.post.title }}</p>
                  <div class="appeal-order__meta">
                    <StatusTag kind="orderStatus" :value="order.order.status" />
                    <span>买家 <span class="mono">#{{ order.order.buyerId }}</span></span>
                    <span>卖家 <span class="mono">{{ order.order.sellerId ? `#${order.order.sellerId}` : '未接单' }}</span></span>
                  </div>
                </div>
                <el-button link type="primary" @click="goOrder">查看完整订单</el-button>
              </div>

              <el-table v-if="order.logs.length" :data="order.logs" row-key="id" class="data-table" size="small">
                <el-table-column label="类型" width="108">
                  <template #default="{ row }"><StatusTag kind="walletLogType" :value="row.type" /></template>
                </el-table-column>
                <el-table-column label="金额" width="120" align="right">
                  <template #default="{ row }"><MoneyText :value="row.amount" signed /></template>
                </el-table-column>
                <el-table-column label="变动后余额" width="124" align="right">
                  <template #default="{ row }"><MoneyText :value="row.balanceAfter" /></template>
                </el-table-column>
                <el-table-column label="时间" width="150">
                  <template #default="{ row }"><TimeText :value="row.createdAt" /></template>
                </el-table-column>
                <el-table-column label="备注" min-width="140" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.remark || '—' }}</template>
                </el-table-column>
              </el-table>
              <p v-else class="detail-empty">这笔订单还没有产生资金流水。</p>
            </template>
          </DetailSection>

          <!-- 待裁决：处置表单 -->
          <DetailSection v-if="isOpen" title="裁决与处置" hint="提交后立即移动托管款，不可撤销">
            <div class="verdict" role="radiogroup" aria-label="裁决结果">
              <button
                v-for="option in OUTCOMES"
                :key="option.value"
                type="button"
                class="verdict__card"
                :class="[`verdict__card--${option.className}`, { 'is-active': form.outcome === option.value }]"
                role="radio"
                :aria-checked="form.outcome === option.value"
                @click="chooseOutcome(option.value)"
              >
                <span class="verdict__label">{{ option.label }}</span>
                <span class="verdict__desc">{{ option.desc }}</span>
              </button>
            </div>

            <div class="form-field">
              <label class="form-field__label" for="arbitrate-comment">
                处理意见
                <span class="form-field__hint">写入审计日志并推送给交易双方·1–500 字</span>
              </label>
              <el-input
                id="arbitrate-comment"
                v-model="form.comment"
                type="textarea"
                :rows="4"
                maxlength="500"
                show-word-limit
                placeholder="例如：经核验交付记录，卖家已按约完成，托管款结算给卖家。"
              />
            </div>

            <p v-if="formError" class="form-error">{{ formError }}</p>

            <div class="form-actions">
              <el-button type="primary" :loading="submitting" @click="submit">提交裁决</el-button>
              <span class="form-actions__note">
                处理意见 {{ commentLength }} / 500
                <template v-if="amount != null">· 本次将移动 ¥{{ amount.toFixed(2) }}</template>
              </span>
            </div>
          </DetailSection>

          <!-- 已裁决 -->
          <DetailSection v-else-if="appeal.status === 'RESOLVED'" title="裁决结果" hint="已结算，不可修改">
            <div class="kv-grid">
              <div class="kv">
                <span class="kv__label">裁决结果</span>
                <span class="kv__value"><StatusTag kind="appealOutcome" :value="appeal.outcome" /></span>
              </div>
              <div class="kv">
                <span class="kv__label">裁决人</span>
                <span class="kv__value mono">{{ appeal.adminId ? `#${appeal.adminId}` : '—' }}</span>
              </div>
              <div class="kv">
                <span class="kv__label">裁决时间</span>
                <span class="kv__value"><TimeText :value="appeal.handledAt" /></span>
              </div>
              <div class="kv">
                <span class="kv__label">订单终态</span>
                <span class="kv__value">
                  <StatusTag v-if="order" kind="orderStatus" :value="order.order.status" />
                  <span v-else class="text-muted">—</span>
                </span>
              </div>
            </div>
            <p class="appeal-comment">{{ appeal.adminComment || '（未填写处理意见）' }}</p>
          </DetailSection>

          <!-- 已撤回 -->
          <DetailSection v-else title="已撤回" hint="学生自行撤回，无需裁决">
            <p class="detail-desc">
              申诉人已于 <TimeText :value="appeal.handledAt" /> 撤回申诉，订单已恢复为「{{
                dictLabel('orderStatus', appeal.fromStatus)
              }}」。平台未对该笔托管款做任何处置。
            </p>
          </DetailSection>
        </div>

        <aside class="detail-grid__side">
          <DetailSection title="关联订单">
            <div class="kv-list">
              <div class="kv">
                <span class="kv__label">订单编号</span>
                <span class="kv__value">
                  <button type="button" class="link-button" @click="goOrder">#{{ appeal.orderId }}</button>
                </span>
              </div>
              <div class="kv">
                <span class="kv__label">托管金额</span>
                <span class="kv__value">
                  <MoneyText :value="amount" />
                </span>
              </div>
              <div class="kv">
                <span class="kv__label">订单状态</span>
                <span class="kv__value">
                  <StatusTag v-if="order" kind="orderStatus" :value="order.order.status" />
                  <span v-else class="text-muted">{{ orderLoading ? '读取中…' : '—' }}</span>
                </span>
              </div>
              <div class="kv">
                <span class="kv__label">买家 / 卖家</span>
                <span class="kv__value mono">
                  {{ order ? `#${order.order.buyerId}` : '—' }} /
                  {{ order?.order.sellerId ? `#${order.order.sellerId}` : '—' }}
                </span>
              </div>
            </div>
            <p class="side-note">裁决会连带改变订单状态：打款 → 已完成，退款 → 已取消。</p>
          </DetailSection>

          <DetailSection title="裁决须知" hint="照抄后端规则，不是建议">
            <ul class="rule-list">
              <li>只有「待裁决」的申诉能提交，已结束的申诉后端会报「申诉已结束」。</li>
              <li>关联订单必须仍在「纠纷中」，否则后端报「订单当前不在纠纷中」。</li>
              <li>打款：托管款结算给卖家，订单置为已完成；退款：托管款退回买家，订单置为已取消。</li>
              <li>处理意见 1–500 字必填，会写入审计日志，并推送给交易双方。</li>
              <li>裁决没有撤销接口，判错只能靠资金流水人工核对与线下处理。</li>
            </ul>
          </DetailSection>
        </aside>
      </div>
    </template>
  </div>
</template>
