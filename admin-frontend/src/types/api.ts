/**
 * 后端统一响应包装：{ code, message, data }
 * 成功 code=0；业务规则失败是 HTTP 200 + code=40000（最高频的「正常失败」）。
 */
export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
}

/** 列表分页：订单 / 用户 / 举报查询 / 流水 / 审计 —— 没有 total */
export interface BackendPage<T> {
  items: T[]
  page: number
  hasNext: boolean
}

/** 申诉分页：结构不同，没有 hasNext，空结果时 totalPages=0 */
export interface AppealPageRaw<T> {
  items: T[]
  page: number
  totalPages: number
}

/** 前端统一分页模型：api 层把两套结构归一到这里，页面不感知差异 */
export interface Paged<T> {
  items: T[]
  page: number
  hasNext: boolean
  totalPages?: number
}

/** 分页参数：page 是 0 基 */
export interface PageQuery {
  page?: number
}

/** 日期区间筛选：yyyy-MM-dd */
export interface DateRangeQuery {
  from?: string
  through?: string
}
