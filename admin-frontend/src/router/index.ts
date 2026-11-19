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
        path: 'appeals',
        name: 'appeals',
        component: () => import('@/views/appeals/AppealListView.vue'),
        meta: { title: '申诉仲裁' },
      },
      {
        path: 'reports',
        name: 'reports',
        component: () => import('@/views/reports/ReportListView.vue'),
        meta: { title: '举报处理' },
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
