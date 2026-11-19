<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import DetailSection from '@/components/DetailSection.vue'
import StatusTag from '@/components/StatusTag.vue'
import MoneyText from '@/components/MoneyText.vue'
import TimeText from '@/components/TimeText.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'
import { fetchOrderDetail } from '@/api/admin'
import { useDetail } from '@/composables/useDetail'
import { dictTone } from '@/constants/options'
import type { OrderDetail } from '@/types/domain'
import { AppError } from '@/utils/errors'
import { formatNumber, formatSignedMoney } from '@/utils/money'

const route = useRoute()
const router = useRouter()

/** 路由参数是 string | string[]，且可能是 'abc'。非法值不发请求，直接给失败态 */
const orderId = computed(() => {
  const raw = route.params.id
  const text = Array.isArray(raw) ? raw[0] : raw
  const value = Number(text)
  return Number.isInteger(value) && value > 0 ? value : null
})

const invalidError = new AppError('notfound', '订单编号无效，请从订单列表进入')

const { data, loading, error, load } = useDetail<OrderDetail>((signal) => {
  const id = orderId.value
  if (id === null) return Promise.reject(invalidError)
  return fetchOrderDetail(id, signal)
})

const post = computed(() => data.value?.post ?? null)
const buyer = computed(() => data.value?.buyer ?? null)
const seller = computed(() => data.value?.seller ?? null)
const logs = computed(() => data.value?.logs ?? [])
const appeals = computed(() => data.value?.appeals ?? [])
const timeline = computed(() => data.value?.timeline ?? [])

const images = computed(() => (post.value?.images ?? '').split(',').map((item) => item.trim()).filter(Boolean))

/** 流水金额带符号，求和就是本单对相关钱包的净变动 */
const logTotal = computed(() => logs.value.reduce((sum, log) => sum + log.amount, 0))
const logHint = computed(() => `共 ${logs.value.length} 条，本单净变动 ${formatSignedMoney(logTotal.value)}`)

const parties = computed(() => [
  { role: '出资方（买家）', user: buyer.value },
  { role: '接单方（卖家）', user: seller.value },
])

function backToList(): void {
  // 筛选条件在 URL 上，走 history 返回能原样带回；直接打开详情时没有上一条，退回列表默认条件
  const state = window.history.state as { back?: string | null } | null
  if (state?.back) router.back()
  else void router.push({ name: 'orders' })
}

/** 纠纷单在列表里能看到 openAppealId，这里顺手打通到裁决页，省得再回申诉列表找 */
function goAppeal(id: number): void {
  void router.push({ name: 'appeal-detail', params: { id: String(id) } })
}

onMounted(() => {
  // 非法 id 也走 load：fetcher 直接 reject 成失败态，比留空白页好排查
  void load()
})
</script>

<template>
  <div>
    <PageHeader :title="orderId ? `订单 #${orderId}` : '订单详情'" description="订单摘要、交易帖、交易双方、资金流水与申诉记录的聚合视图。">
      <template #actions>
        <el-button :icon="ArrowLeft" @click="backToList">返回列表</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <ErrorState v-if="error" class="panel" :error="error" @retry="load" />

    <div v-else-if="loading && !data" class="panel detail-skeleton">
      <el-skeleton :rows="10" animated />
    </div>

    <template v-else-if="data">
      <!-- 摘要条 -->
      <section class="panel detail-hero">
        <div class="detail-hero__main">
          <div class="detail-hero__line">
            <span class="detail-hero__id mono">#{{ data.order.id }}</span>
            <StatusTag kind="orderStatus" :value="data.order.status" />
            <StatusTag kind="postType" :value="data.post.type" />
          </div>
          <h2 class="detail-hero__title">{{ data.post.title }}</h2>
          <p class="detail-hero__meta">
            出资方 <span class="mono">#{{ data.order.buyerId }}</span>
            <template v-if="data.order.sellerId">
              <span class="detail-hero__dot">·</span>接单方 <span class="mono">#{{ data.order.sellerId }}</span>
            </template>
            <span class="detail-hero__dot">·</span>下单于 <TimeText :value="data.order.createdAt" />
          </p>
        </div>
        <div class="detail-hero__amount">
          <span class="detail-hero__amount-label">托管金额</span>
          <MoneyText :value="data.order.amount" strong />
        </div>
      </section>

      <div class="detail-grid">
        <div class="detail-grid__main">
          <DetailSection title="订单信息" hint="来自 trade_order">
            <div class="kv-grid">
              <div class="kv">
                <span class="kv__label">订单号</span>
                <span class="kv__value mono">#{{ data.order.id }}</span>
              </div>
              <div class="kv">
                <span class="kv__label">当前状态</span>
                <span class="kv__value"><StatusTag kind="orderStatus" :value="data.order.status" /></span>
              </div>
              <div class="kv">
                <span class="kv__label">交易类型</span>
                <span class="kv__value"><StatusTag kind="postType" :value="data.post.type" /></span>
              </div>
              <div class="kv">
                <span class="kv__label">关联交易帖</span>
                <span class="kv__value mono">#{{ data.order.postId }}</span>
              </div>
              <div class="kv">
                <span class="kv__label">下单时间</span>
                <span class="kv__value"><TimeText :value="data.order.createdAt" /></span>
              </div>
              <div class="kv">
                <span class="kv__label">最后更新</span>
                <span class="kv__value"><TimeText :value="data.order.updatedAt" /></span>
              </div>
            </div>
          </DetailSection>

          <DetailSection title="交易帖内容" hint="发布时的原始信息，与订单状态相互独立">
            <div class="kv-grid">
              <div class="kv">
                <span class="kv__label">标题</span>
                <span class="kv__value">{{ data.post.title }}</span>
              </div>
              <div class="kv">
                <span class="kv__label">分类</span>
                <span class="kv__value">{{ data.post.category || '未分类' }}</span>
              </div>
              <div class="kv">
                <span class="kv__label">挂牌价格</span>
                <span class="kv__value"><MoneyText :value="data.post.price" /></span>
              </div>
              <div class="kv">
                <span class="kv__label">帖子状态</span>
                <span class="kv__value mono">{{ data.post.status }}</span>
              </div>
              <div class="kv">
                <span class="kv__label">接单截止</span>
                <span class="kv__value"><TimeText :value="data.post.deadline" /></span>
              </div>
              <div class="kv">
                <span class="kv__label">发布时间</span>
                <span class="kv__value"><TimeText :value="data.post.createdAt" /></span>
              </div>
            </div>

            <p class="detail-desc">{{ data.post.description }}</p>

            <div v-if="images.length" class="thumb-strip">
              <el-image
                v-for="(src, index) in images"
                :key="src"
                class="thumb-strip__item"
                :src="src"
                :preview-src-list="images"
                :initial-index="index"
                fit="cover"
                preview-teleported
                hide-on-click-modal
              />
            </div>
          </DetailSection>

          <DetailSection title="资金流水" :hint="logHint">
            <el-table v-if="logs.length" :data="logs" row-key="id" class="data-table" size="small">
              <el-table-column label="时间" width="150">
                <template #default="{ row }"><TimeText :value="row.createdAt" /></template>
              </el-table-column>
              <el-table-column label="类型" width="106">
                <template #default="{ row }"><StatusTag kind="walletLogType" :value="row.type" /></template>
              </el-table-column>
              <el-table-column label="变动金额" width="118" align="right">
                <template #default="{ row }"><MoneyText :value="row.amount" signed /></template>
              </el-table-column>
              <el-table-column label="变动后余额" width="126" align="right">
                <template #default="{ row }"><MoneyText :value="row.balanceAfter" /></template>
              </el-table-column>
              <el-table-column label="用户" width="86">
                <template #default="{ row }"><span class="mono">#{{ row.userId }}</span></template>
              </el-table-column>
              <el-table-column label="备注" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">{{ row.remark || '—' }}</template>
              </el-table-column>
            </el-table>
            <p v-else class="detail-empty">这笔订单还没有产生资金流水。</p>
          </DetailSection>

          <DetailSection title="申诉记录" :hint="`共 ${appeals.length} 条`">
            <div v-if="appeals.length" class="stack">
              <article v-for="appeal in appeals" :key="appeal.id" class="appeal-card">
                <header class="appeal-card__head">
                  <span class="mono">申诉 #{{ appeal.id }}</span>
                  <StatusTag kind="appealStatus" :value="appeal.status" />
                  <StatusTag v-if="appeal.outcome" kind="appealOutcome" :value="appeal.outcome" />
                  <TimeText class="appeal-card__time" :value="appeal.createdAt" />
                  <el-button
                    v-if="appeal.status === 'OPEN'"
                    link
                    type="primary"
                    size="small"
                    class="appeal-card__action"
                    @click="goAppeal(appeal.id)"
                  >
                    去裁决
                  </el-button>
                </header>
                <p class="appeal-card__reason">{{ appeal.reason }}</p>
                <dl class="appeal-card__meta">
                  <div>
                    <dt>发起人</dt>
                    <dd class="mono">#{{ appeal.appellantId }}</dd>
                  </div>
                  <div>
                    <dt>发起前状态</dt>
                    <dd><StatusTag kind="orderStatus" :value="appeal.fromStatus" /></dd>
                  </div>
                  <div>
                    <dt>处理人</dt>
                    <dd class="mono">{{ appeal.adminId ? `#${appeal.adminId}` : '未处理' }}</dd>
                  </div>
                  <div>
                    <dt>处理时间</dt>
                    <dd><TimeText :value="appeal.handledAt" /></dd>
                  </div>
                </dl>
                <p v-if="appeal.adminComment" class="appeal-card__comment">
                  <span class="appeal-card__comment-label">裁决意见</span>{{ appeal.adminComment }}
                </p>
              </article>
            </div>
            <p v-else class="detail-empty">这笔订单没有申诉记录。</p>
          </DetailSection>
        </div>

        <aside class="detail-grid__side">
          <DetailSection title="交易双方">
            <div class="party-list">
              <div v-for="party in parties" :key="party.role" class="party">
                <div v-if="party.user" class="party__body">
                  <div class="party__avatar">{{ (party.user.nickname || party.user.username).slice(0, 1) }}</div>
                  <div class="party__info">
                    <p class="party__name">{{ party.user.nickname || party.user.username }}</p>
                    <p class="party__account mono">@{{ party.user.username }} · #{{ party.user.id }}</p>
                  </div>
                </div>
                <div v-else class="party__body party__body--empty">尚未接单</div>
                <div class="party__foot">
                  <span class="party__role">{{ party.role }}</span>
                  <template v-if="party.user">
                    <StatusTag kind="userRole" :value="party.user.role" />
                    <StatusTag kind="userStatus" :value="party.user.status" />
                  </template>
                  <TimeText v-if="party.user" class="party__time" :value="party.user.createdAt" date-only />
                </div>
              </div>
            </div>
          </DetailSection>

          <DetailSection title="交易时间线" :hint="`${formatNumber(timeline.length)} 个节点，按时间升序`">
            <ol v-if="timeline.length" class="timeline">
              <li
                v-for="(event, index) in timeline"
                :key="`${event.type}-${event.createdAt}-${index}`"
                class="timeline__item"
                :class="{ 'is-last': index === timeline.length - 1 }"
              >
                <span class="timeline__dot" :class="`timeline__dot--${dictTone('timelineType', event.type)}`" />
                <div class="timeline__body">
                  <div class="timeline__head">
                    <StatusTag kind="timelineType" :value="event.type" />
                    <TimeText class="timeline__time" :value="event.createdAt" />
                  </div>
                  <p v-if="event.details" class="timeline__detail">{{ event.details }}</p>
                  <p class="timeline__actor">
                    操作人 <span class="mono">{{ event.actorId ? `#${event.actorId}` : '系统' }}</span>
                  </p>
                </div>
              </li>
            </ol>
            <EmptyState v-else text="没有可展示的时间线" hint="订单尚未产生任何状态变更。" />
          </DetailSection>
        </aside>
      </div>
    </template>
  </div>
</template>
