<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import DetailSection from '@/components/DetailSection.vue'
import FilterBar from '@/components/FilterBar.vue'
import QueryTable from '@/components/QueryTable.vue'
import Pager from '@/components/Pager.vue'
import StatusTag from '@/components/StatusTag.vue'
import MoneyText from '@/components/MoneyText.vue'
import TimeText from '@/components/TimeText.vue'
import ErrorState from '@/components/ErrorState.vue'
import { fetchFunds, fetchLedger, fetchWallet } from '@/api/admin'
import { useDetail } from '@/composables/useDetail'
import { usePagedQuery } from '@/composables/usePagedQuery'
import { useRouteFilters } from '@/composables/useRouteFilters'
import { walletLogTypeSelect } from '@/constants/options'
import type { FundTotals, Wallet, WalletLog } from '@/types/domain'
import type { LedgerFilters } from '@/types/query'
import { formatMoney, formatSignedMoney, isZeroAmount } from '@/utils/money'
import { toAppError } from '@/utils/errors'

/* ───────────────────────────── 托管资金核对 ───────────────────────────── */

const { data: funds, loading: fundsLoading, error: fundsError, load: loadFunds } = useDetail<FundTotals>((signal) =>
  fetchFunds(signal),
)

/** 差额用「吸收残差」的比较，不写死 === 0（BigDecimal 减完可能留 0.000001） */
const balanced = computed(() => isZeroAmount(funds.value?.difference))

const verdictText = computed(() => {
  const value = funds.value
  if (!value) return ''
  const diff = value.difference
  if (isZeroAmount(diff)) {
    return '钱包冻结总额与在途订单应收一致，托管资金没有错账。'
  }
  if (diff > 0) {
    return `冻结比应收多 ${formatMoney(diff)}：可能有订单已结算但冻结未释放，或存在多余的冻结记录，需按流水逐笔核对。`
  }
  return `冻结比应收少 ${formatMoney(Math.abs(diff))}：存在订单已进入托管流程却没有对应冻结资金的情况，属于资金缺口，应优先排查。`
})

/* ───────────────────────────── 钱包速查 ───────────────────────────── */

const walletId = ref('')
const wallet = ref<Wallet | null>(null)
const walletLoading = ref(false)
const walletError = ref('')
const walletSearched = ref(false)

/** 两个金额相加要按分收敛，别让 0.1 + 0.2 冒出 0.30000000000000004 */
function sumMoney(a: number, b: number): number {
  return Math.round((a + b) * 100) / 100
}

const walletTotal = computed(() => (wallet.value ? sumMoney(wallet.value.balance, wallet.value.frozen) : 0))

async function lookupWallet(): Promise<void> {
  walletError.value = ''
  const id = Number(walletId.value.trim())
  if (!Number.isInteger(id) || id <= 0) {
    wallet.value = null
    walletSearched.value = false
    walletError.value = '请输入数字用户编号（可先在「用户管理」里查到）'
    return
  }

  walletLoading.value = true
  try {
    wallet.value = await fetchWallet(id)
    walletSearched.value = true
  } catch (cause) {
    wallet.value = null
    walletSearched.value = false
    walletError.value = toAppError(cause).message
  } finally {
    walletLoading.value = false
  }
}

/* ───────────────────────────── 资金流水 ───────────────────────────── */

const DEFAULT_FILTERS: LedgerFilters = {
  userId: '',
  type: '',
  orderId: '',
  from: '',
  through: '',
}

const { filters, page, hydrate, apply, goPage, reset } = useRouteFilters<LedgerFilters>(DEFAULT_FILTERS)

const { items, loading, error, hasNext, canNext, totalPages, load } = usePagedQuery<WalletLog, LedgerFilters>(
  (params, signal) => fetchLedger(params, signal),
  () => ({ ...filters }),
)

/** 日期区间单独存：el-date-picker 的 daterange 只认数组，和 from/through 两个字段对不上 */
const dateRange = ref<[string, string] | null>(null)
const formError = ref('')

const isFiltered = computed(() =>
  Object.entries(filters).some(([, value]) => typeof value === 'string' && value.trim() !== ''),
)

function rangeFromFilters(): void {
  dateRange.value = filters.from && filters.through ? [filters.from, filters.through] : null
}

/** 数字 ID 校验放在请求前：'abc' 传给 Long 参数会变成 HTTP 400 + 40000，用户看不懂 */
function validate(): boolean {
  formError.value = ''
  for (const [label, value] of [
    ['用户编号', filters.userId],
    ['订单编号', filters.orderId],
  ] as const) {
    if (value.trim() && !/^\d+$/.test(value.trim())) {
      formError.value = `${label}必须是正整数`
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

function scrollToLedger(): void {
  document.getElementById('ledger')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

/** 点表格里的用户编号 → 把它变成筛选条件，比让管理员手抄 ID 靠谱 */
async function filterByUser(id: unknown): Promise<void> {
  const value = Number(id)
  if (!Number.isInteger(value) || value <= 0) return
  filters.userId = String(value)
  filters.orderId = ''
  await apply()
  await load(0)
  scrollToLedger()
}

/** 钱包卡里的「只看该用户流水」 */
async function filterByWalletUser(): Promise<void> {
  if (!wallet.value) return
  await filterByUser(wallet.value.userId)
}

const updatedAt = ref('')

async function refresh(): Promise<void> {
  await Promise.all([loadFunds(), load()])
  if (!fundsError.value && !error.value) {
    const now = new Date()
    const pad = (n: number): string => `${n}`.padStart(2, '0')
    updatedAt.value = `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
  }
}

onMounted(async () => {
  hydrate()
  rangeFromFilters()
  await Promise.all([loadFunds(), load(page.value)])
})
</script>

<template>
  <div>
    <PageHeader
      title="资金监管"
      description="核对平台冻结总额与在途订单应收是否一致，按编号查单个钱包，并检索全平台资金流水。管理端只读，不提供调账、加钱或扣钱入口。"
    >
      <template #actions>
        <span v-if="updatedAt" class="funds-updated">更新于 {{ updatedAt }}</span>
        <el-button :icon="Refresh" :loading="fundsLoading || loading" @click="refresh">刷新</el-button>
      </template>
    </PageHeader>

    <div class="funds-top">
      <DetailSection title="托管资金核对" hint="钱包冻结总额 − 在途订单应收 = 差额">
        <el-skeleton v-if="fundsLoading && !funds" :rows="3" animated />
        <ErrorState v-else-if="fundsError" :error="fundsError" @retry="loadFunds" />

        <template v-else-if="funds">
          <div class="check-board">
            <div class="check-cell">
              <span class="check-cell__label">钱包冻结总额</span>
              <span class="check-cell__value">{{ formatMoney(funds.frozen) }}</span>
              <span class="check-cell__hint">所有账号 frozen 之和</span>
            </div>
            <span class="check-op" aria-hidden="true">−</span>
            <div class="check-cell">
              <span class="check-cell__label">在途订单应收</span>
              <span class="check-cell__value">{{ formatMoney(funds.expected) }}</span>
              <span class="check-cell__hint">待接单 / 进行中 / 待确认 / 纠纷中</span>
            </div>
            <span class="check-op" aria-hidden="true">=</span>
            <div class="check-cell" :class="balanced ? 'is-balanced' : 'is-off'">
              <span class="check-cell__label">差额</span>
              <span class="check-cell__value">{{ formatSignedMoney(funds.difference) }}</span>
              <span class="check-cell__hint">{{ balanced ? '账目平衡' : '需要排查' }}</span>
            </div>
          </div>
          <p class="check-verdict" :class="balanced ? 'is-balanced' : 'is-off'">{{ verdictText }}</p>
        </template>
      </DetailSection>

      <DetailSection title="钱包速查" hint="按用户编号查余额">
        <div class="wallet-form">
          <el-input
            id="w-id"
            v-model="walletId"
            placeholder="用户编号，如 18"
            clearable
            @keyup.enter="lookupWallet"
          />
          <el-button type="primary" :loading="walletLoading" @click="lookupWallet">查询</el-button>
        </div>

        <p v-if="walletError" class="form-error">{{ walletError }}</p>

        <template v-else-if="wallet">
          <dl class="wallet-figures">
            <div>
              <dt>可用余额</dt>
              <dd><MoneyText :value="wallet.balance" strong /></dd>
            </div>
            <div>
              <dt>冻结中</dt>
              <dd><MoneyText :value="wallet.frozen" /></dd>
            </div>
            <div>
              <dt>账号合计</dt>
              <dd><MoneyText :value="walletTotal" strong /></dd>
            </div>
          </dl>
          <div class="wallet-actions">
            <RouterLink class="wallet-link" :to="{ name: 'users', query: { keyword: String(wallet.userId) } }">
              用户管理里查看 #{{ wallet.userId }}
            </RouterLink>
            <el-button link type="primary" @click="filterByWalletUser">只看该用户流水</el-button>
          </div>
        </template>

        <p v-else-if="walletSearched" class="wallet-empty">
          没有查到钱包记录。该用户可能尚未注册，或还没有产生过任何资金往来（新注册账号不会自动建钱包）。
        </p>
        <p v-else class="wallet-empty">输入编号后回车，可查看该账号的可用余额、冻结金额与合计。</p>
      </DetailSection>
    </div>

    <div id="ledger">
      <FilterBar :loading="loading" @submit="submit" @reset="clearAll">
        <div class="filter-field">
          <label for="l-user">用户编号</label>
          <el-input id="l-user" v-model="filters.userId" placeholder="精确 ID" clearable />
        </div>

        <div class="filter-field">
          <label for="l-type">流水类型</label>
          <el-select id="l-type" v-model="filters.type" placeholder="全部类型">
            <el-option label="全部类型" value="" />
            <el-option v-for="option in walletLogTypeSelect" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </div>

        <div class="filter-field">
          <label for="l-order">关联订单</label>
          <el-input id="l-order" v-model="filters.orderId" placeholder="精确 ID" clearable />
        </div>

        <div class="filter-field filter-field--wide">
          <label for="l-range">发生日期</label>
          <el-date-picker
            id="l-range"
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

      <p class="ledger-note">
        金额带符号：冻结为负、解冻 / 充值 / 收入为正；托管结算（PAY）记 0，因为钱在冻结那一步就已经从可用余额扣走了。
        「变动后余额」指可用余额，不含冻结部分。点用户编号可直接筛出该账号的全部流水。
      </p>

      <QueryTable
        :loading="loading"
        :error="error"
        :empty="items.length === 0"
        :empty-text="page > 0 ? '这一页没有流水了' : isFiltered ? '没有符合条件的流水' : '平台还没有产生任何资金流水'"
        :empty-hint="
          page > 0
            ? '筛选条件变化后页码可能已经翻过头，回到第一页看看。'
            : isFiltered
              ? '用户编号与订单编号都是精确匹配，写错一位就查不到。'
              : '学生充值或产生第一笔订单后，这里才会有记录。'
        "
        @retry="load()"
      >
        <el-table :data="items" row-key="id" class="data-table ledger-table">
          <el-table-column label="流水" width="82">
            <template #default="{ row }"><span class="mono">#{{ row.id }}</span></template>
          </el-table-column>

          <el-table-column label="用户" width="88">
            <template #default="{ row }">
              <button
                type="button"
                class="ledger-user"
                title="只看该用户的流水"
                @click="filterByUser(row.userId)"
              >
                #{{ row.userId }}
              </button>
            </template>
          </el-table-column>

          <el-table-column label="类型" width="106">
            <template #default="{ row }"><StatusTag kind="walletLogType" :value="row.type" /></template>
          </el-table-column>

          <el-table-column label="金额" width="120" align="right">
            <template #default="{ row }"><MoneyText :value="row.amount" signed /></template>
          </el-table-column>

          <el-table-column label="变动后余额" width="124" align="right">
            <template #default="{ row }"><MoneyText :value="row.balanceAfter" /></template>
          </el-table-column>

          <el-table-column label="关联订单" width="96">
            <template #default="{ row }">
              <RouterLink
                v-if="row.orderId"
                class="ledger-order"
                :to="{ name: 'order-detail', params: { id: String(row.orderId) } }"
              >
                #{{ row.orderId }}
              </RouterLink>
              <span v-else class="text-muted">—</span>
            </template>
          </el-table-column>

          <el-table-column label="备注" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.remark || '—' }}</template>
          </el-table-column>

          <el-table-column label="时间" width="142">
            <template #default="{ row }"><TimeText :value="row.createdAt" /></template>
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
  </div>
</template>
