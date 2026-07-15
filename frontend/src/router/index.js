import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../views/AppLayout.vue'
import AuthView from '../views/AuthView.vue'
import DashboardView from '../views/DashboardView.vue'
import HistoryView from '../views/HistoryView.vue'
import QueryView from '../views/QueryView.vue'
import { isAuthenticated } from '../utils/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: AuthView,
      props: { initialMode: 'login' },
      meta: { guestOnly: true }
    },
    {
      path: '/register',
      name: 'register',
      component: AuthView,
      props: { initialMode: 'register' },
      meta: { guestOnly: true }
    },
    {
      path: '/',
      component: AppLayout,
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', name: 'dashboard', component: DashboardView, meta: { title: '企业数据分析工作台' } },
        {
          path: 'query',
          name: 'query',
          component: QueryView,
          meta: { title: '智能查询中心' }
        },
        {
          path: 'history',
          name: 'history',
          component: HistoryView,
          meta: { title: '查询历史' }
        }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})

router.beforeEach((to) => {
  // 路由守卫只优化前端体验；任何真实权限边界仍由后端 JWT 校验保证。
  if (to.meta.requiresAuth && !isAuthenticated()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guestOnly && isAuthenticated()) {
    return { name: 'dashboard' }
  }
  return true
})

export default router
