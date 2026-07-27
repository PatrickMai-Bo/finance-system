import { createRouter, createWebHistory } from 'vue-router'
import { authApi } from '../api'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/Login.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('../layout/MobileLayout.vue'),
    children: [
      { path: '', redirect: '/home' },
      { path: 'home', name: 'home', component: () => import('../views/Home.vue') },
      { path: 'finance', name: 'finance', component: () => import('../views/Finance.vue') },
      { path: 'stock', name: 'stock', component: () => import('../views/Stock.vue') },
      { path: 'fund', name: 'fund', component: () => import('../views/Fund.vue') },
      { path: 'decision', name: 'decision', component: () => import('../views/Decision.vue') },
      { path: 'ai', name: 'ai', component: () => import('../views/Ai.vue') },
      { path: 'profile', name: 'profile', component: () => import('../views/Profile.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory('/m/'),
  routes
})

router.beforeEach(async (to) => {
  const token = localStorage.getItem('token')
  if (to.meta.public) return true
  if (!token) return '/login'
  // 验证 token 有效性
  try {
    await authApi.me()
    return true
  } catch (e) {
    localStorage.removeItem('token')
    return '/login'
  }
})

export default router
