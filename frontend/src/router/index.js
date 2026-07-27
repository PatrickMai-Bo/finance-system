import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    children: [
      { path: '', redirect: '/home' },
      { path: 'home', name: '首页', component: () => import('../views/Home.vue') },
      { path: 'finance', name: '个人财务', component: () => import('../views/Finance.vue') },
      { path: 'fund', name: '基金筛选', component: () => import('../views/FundScreen.vue') },
      { path: 'stock', name: '股票筛选', component: () => import('../views/StockScreen.vue') },
      { path: 'decision', name: '决策思维', component: () => import('../views/Decision.vue') },
      { path: 'ai', name: 'AI设置', component: () => import('../views/AiPanel.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isLogin) {
    return '/login'
  }
})

export default router
