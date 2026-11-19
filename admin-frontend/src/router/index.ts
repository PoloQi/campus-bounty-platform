import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import AdminLayout from '@/layouts/AdminLayout.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/',
    component: AdminLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: '仪表盘' },
      },
      {
        path: 'orders',
        name: 'orders',
        component: () => import('@/views/orders/OrderListView.vue'),
        meta: { title: '订单管理' },
      },
      {
        // 详情独立成页而不是抽屉：订单详情有 7 块内容、还要能横向对比两张表，
        // 塞进抽屉会变成一个内部滚动条套外部滚动条的烂摊子
        path: 'orders/:id',
        name: 'order-detail',
        component: () => import('@/views/orders/OrderDetailView.vue'),
        meta: { title: '订单详情' },
      },
      {
        path: 'appeals',
        name: 'appeals',
        component: () => import('@/views/appeals/AppealListView.vue'),
        meta: { title: '申诉仲裁' },
      },
      {
        // 裁决要同时看申诉理由、托管金额与资金流水，塞进弹窗看不全
        path: 'appeals/:id',
        name: 'appeal-detail',
        component: () => import('@/views/appeals/AppealDetailView.vue'),
        meta: { title: '申诉详情' },
      },
      {
        path: 'reports',
        name: 'reports',
        component: () => import('@/views/reports/ReportListView.vue'),
        meta: { title: '举报处理' },
      },
      {
        // 处置表单要和治理对象摘要并排看，独立成页
        path: 'reports/:id',
        name: 'report-detail',
        component: () => import('@/views/reports/ReportDetailView.vue'),
        meta: { title: '举报详情' },
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('@/views/users/UserListView.vue'),
        meta: { title: '用户管理' },
      },
      {
        path: 'funds',
        name: 'funds',
        component: () => import('@/views/funds/FundsView.vue'),
        meta: { title: '资金监管' },
      },
      {
        path: 'audit',
        name: 'audit',
        component: () => import('@/views/audit/AuditLogView.vue'),
        meta: { title: '审计日志' },
      },
    ],
  },
  {
    path: '/403',
    name: 'forbidden',
    component: () => import('@/views/error/ForbiddenView.vue'),
    meta: { public: true, title: '无权限' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/error/NotFoundView.vue'),
    meta: { public: true, title: '页面不存在' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

export default router
