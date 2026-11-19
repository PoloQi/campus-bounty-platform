<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import FilterBar from '@/components/FilterBar.vue'
import QueryTable from '@/components/QueryTable.vue'
import Pager from '@/components/Pager.vue'
import StatusTag from '@/components/StatusTag.vue'
import TimeText from '@/components/TimeText.vue'
import { banUser, fetchUsers, unbanUser } from '@/api/admin'
import { usePagedQuery } from '@/composables/usePagedQuery'
import { useRouteFilters } from '@/composables/useRouteFilters'
import { useConfirm } from '@/composables/useConfirm'
import { userStatusSelect } from '@/constants/options'
import type { UserRow } from '@/types/domain'
import type { UserFilters } from '@/types/query'
import { formatDateTime } from '@/utils/datetime'
import { toAppError } from '@/utils/errors'

const { confirm } = useConfirm()

/** 默认看全部：这一页的日常是「找人」，不是「看封禁名单」 */
const DEFAULT_FILTERS: UserFilters = {
  keyword: '',
  status: '',
}

const { filters, page, hydrate, apply, goPage, reset } = useRouteFilters<UserFilters>(DEFAULT_FILTERS)

const { items, loading, error, hasNext, canNext, totalPages, load } = usePagedQuery<UserRow, UserFilters>(
  (params, signal) => fetchUsers(params, signal),
  () => ({ ...filters }),
)

/**
 * 后端列表不带 total，所以这里只统计「本页」——
 * 写成「共 N 位用户」就是编数字了。
 */
const bannedCount = computed(() => items.value.filter((item) => item.status === 'BANNED').length)
const adminCount = computed(() => items.value.filter((item) => item.role === 'ADMIN').length)

const isFiltered = computed(() => filters.keyword !== '' || filters.status !== '')

/**
 * 关键词与编号是「或」关系：输 17 会同时命中 #17 和用户名里含 17 的账号，
 * 而后端固定 id desc 排序，#17 只能排在一堆新账号后面。
 * 纯数字关键词时把编号完全相等的那一行提到最前 —— 这一页里管理员大概率就是找它。
 * 只在本页内生效，不改变分页语义。
 */
const rows = computed<UserRow[]>(() => {
  const keyword = filters.keyword.trim()
  if (!/^\d+$/.test(keyword)) return items.value
  const id = Number(keyword)
  if (!items.value.some((item) => item.id === id)) return items.value
  const hit = items.value.filter((item) => item.id === id)
  const rest = items.value.filter((item) => item.id !== id)
  return [...hit, ...rest]
})

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

/* ───────────────────────────── 行展示 ───────────────────────────── */

/*
 * el-table 的插槽 row 类型是 DefaultRow（宽泛索引签名），直接传给 (user: UserRow)
 * 会在编译期报错。所以这几个函数收 unknown、在函数里收窄 —— 类型断言只出现在这一处，
 * 上层模板不必到处 as。
 */
function displayName(value: unknown): string {
  const row = value as Partial<UserRow> | null
  return row?.nickname?.trim() || row?.username || '—'
}

/** 头像位没有真实图片可用，用昵称首字（中文正好一个字，比拼音缩写更好认） */
function initial(value: unknown): string {
  const row = value as Partial<UserRow> | null
  const source = row?.nickname?.trim() || row?.username || ''
  const first = Array.from(source)[0]
  return first ? first.toUpperCase() : '#'
}

/** 管理员行整行降调，避免误当成可封禁的学生 */
function rowClass(payload: { row?: unknown }): string {
  const row = payload?.row as UserRow | undefined
  return row?.role === 'ADMIN' ? 'is-admin-row' : ''
}

/* ───────────────────────────── 封禁 / 解封 ───────────────────────────── */

const acting = ref<number | null>(null)

const BAN_IMPACT =
  '封禁立即生效：该账号之后每次请求都会重新校验状态，受保护接口统一返回 401，学生端页面随即掉线，也无法再次登录、发布或接单。已发布的内容、进行中的订单与托管中的资金不受影响，随时可以解封。'

const UNBAN_IMPACT =
  '解封立即生效：该账号可以重新登录、发布与交易。此前被删除的帖子与被关闭的交易帖不会随之恢复；进行中的订单与托管资金按原状态继续。'

async function toggleBan(value: unknown): Promise<void> {
  const user = value as UserRow
  const banning = user.status !== 'BANNED'

  const ok = await confirm({
    title: banning ? '确认封禁该学生账号？' : '确认解封该学生账号？',
    rows: [
      { label: '账号', value: `${displayName(user)}（@${user.username}）` },
      { label: '编号', value: `#${user.id}` },
      { label: '当前状态', value: banning ? '正常' : '已封禁' },
      { label: '注册时间', value: formatDateTime(user.createdAt) },
    ],
    impact: banning ? BAN_IMPACT : UNBAN_IMPACT,
    confirmText: banning ? '封禁' : '解封',
  })
  if (!ok) return

  acting.value = user.id
  try {
    if (banning) await banUser(user.id)
    else await unbanUser(user.id)

    // 筛选了状态时，被改状态的账号会当场从结果里消失 —— 说一句，免得管理员以为漏了
    const dropped = (banning && filters.status === 'ACTIVE') || (!banning && filters.status === 'BANNED')
    ElMessage.success(
      dropped
        ? `${banning ? '已封禁' : '已解封'} #${user.id}，该账号已移出当前筛选结果`
        : `${banning ? '已封禁' : '已解封'} #${user.id}`,
    )
    await load()
  } catch (cause) {
    ElMessage.error(toAppError(cause).message)
  } finally {
    acting.value = null
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
      title="用户管理"
      description="按用户名、昵称或编号检索账号。封禁与解封仅对学生生效，管理员账号不可治理；每次操作都会写入审计日志。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load()">刷新</el-button>
      </template>
    </PageHeader>

    <FilterBar :loading="loading" @submit="submit" @reset="clearAll">
      <div class="filter-field">
        <label for="u-keyword">关键词</label>
        <el-input
          id="u-keyword"
          v-model="filters.keyword"
          placeholder="用户名 / 昵称 / 账号编号"
          :prefix-icon="Search"
          clearable
        />
      </div>

      <div class="filter-field">
        <label for="u-status">账号状态</label>
        <el-select id="u-status" v-model="filters.status" placeholder="全部状态">
          <el-option label="全部状态" value="" />
          <el-option v-for="option in userStatusSelect" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
      </div>
    </FilterBar>

    <div v-if="items.length" class="user-stats">
      <div class="user-stat">
        <span class="user-stat__label">本页账号</span>
        <span class="user-stat__value">{{ items.length }}</span>
      </div>
      <div class="user-stat">
        <span class="user-stat__label">其中已封禁</span>
        <span class="user-stat__value" :class="{ 'is-danger': bannedCount > 0 }">{{ bannedCount }}</span>
      </div>
      <div class="user-stat">
        <span class="user-stat__label">其中管理员</span>
        <span class="user-stat__value">{{ adminCount }}</span>
      </div>
      <p class="user-stats__note">
        编号精确匹配、用户名与昵称模糊匹配，两者是「或」关系；输纯编号时该账号置顶。只统计本页。
      </p>
    </div>

    <QueryTable
      :loading="loading"
      :error="error"
      :empty="items.length === 0"
      :empty-text="page > 0 ? '这一页没有账号了' : isFiltered ? '没有符合条件的账号' : '还没有学生注册'"
      :empty-hint="
        page > 0
          ? '筛选条件变化后页码可能已经翻过头，回到第一页看看。'
          : isFiltered
            ? '关键词只匹配用户名、昵称与编号。编号要写完整数字，不支持前缀。'
            : '学生端注册后会自动出现在这里。'
      "
      @retry="load()"
    >
      <el-table :data="rows" row-key="id" class="data-table user-table" :row-class-name="rowClass">
        <el-table-column label="编号" width="84">
          <template #default="{ row }"><span class="mono">#{{ row.id }}</span></template>
        </el-table-column>

        <el-table-column label="账号" min-width="240">
          <template #default="{ row }">
            <div class="user-cell">
              <span class="user-cell__avatar" :class="{ 'is-admin': row.role === 'ADMIN' }">{{ initial(row) }}</span>
              <div class="user-cell__info">
                <span class="user-cell__name">{{ displayName(row) }}</span>
                <span class="user-cell__account">@{{ row.username }}</span>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="角色" width="100">
          <template #default="{ row }"><StatusTag kind="userRole" :value="row.role" /></template>
        </el-table-column>

        <el-table-column label="账号状态" width="112">
          <template #default="{ row }"><StatusTag kind="userStatus" :value="row.status" /></template>
        </el-table-column>

        <el-table-column label="注册时间" width="150">
          <template #default="{ row }"><TimeText :value="row.createdAt" /></template>
        </el-table-column>

        <el-table-column label="操作" width="112" align="right">
          <template #default="{ row }">
            <el-tooltip v-if="row.role === 'ADMIN'" content="封禁只对学生账号生效" placement="top">
              <span class="user-locked">不可治理</span>
            </el-tooltip>
            <el-button
              v-else-if="row.status === 'BANNED'"
              link
              type="primary"
              :loading="acting === row.id"
              @click.stop="toggleBan(row)"
            >
              解封
            </el-button>
            <el-button v-else link type="danger" :loading="acting === row.id" @click.stop="toggleBan(row)">
              封禁
            </el-button>
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
