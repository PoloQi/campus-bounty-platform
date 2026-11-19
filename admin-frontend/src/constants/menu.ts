import type { Component } from 'vue'
import {
  Bell,
  ChatLineSquare,
  List,
  Odometer,
  Tickets,
  User,
  Wallet,
} from '@element-plus/icons-vue'

export interface MenuItem {
  /** 侧栏跳转路径 */
  path: string
  /** 完整标题（顶栏面包屑用） */
  title: string
  /** 侧栏短标题 */
  short: string
  icon: Component
}

/** 顺序与旧后台保持一致，减少交接时的认知成本 */
export const ADMIN_MENU: MenuItem[] = [
  { path: '/dashboard', title: '仪表盘', short: '仪表盘', icon: Odometer },
  { path: '/orders', title: '订单管理', short: '订单', icon: Tickets },
  { path: '/appeals', title: '申诉仲裁', short: '申诉', icon: ChatLineSquare },
  { path: '/reports', title: '举报处理', short: '举报', icon: Bell },
  { path: '/users', title: '用户管理', short: '用户', icon: User },
  { path: '/funds', title: '资金监管', short: '资金', icon: Wallet },
  { path: '/audit', title: '审计日志', short: '审计', icon: List },
]

/** 按路径前缀找当前模块（详情页 /orders/12 也能命中订单管理） */
export function findMenuByPath(path: string): MenuItem | undefined {
  return ADMIN_MENU.find((item) => path === item.path || path.startsWith(`${item.path}/`))
}
