import { createRouter, createWebHistory } from 'vue-router'
import { saveSession, tokenOf, userOf } from './api/session'
import { authApi } from './api/auth'
import Login from './views/Login.vue'

const routes = [
  { path: '/', redirect: '/worker' },
  { path: '/login', component: Login, meta: { public: true } },
  { path: '/worker', component: () => import('./views/worker/Home.vue') },
  { path: '/worker/building', component: () => import('./views/worker/Building.vue') },
  { path: '/worker/task/:no?', component: () => import('./views/worker/Task.vue') },
  { path: '/worker/parts', component: () => import('./views/worker/Parts.vue') },
  { path: '/:pathMatch(.*)*', redirect: '/worker' }
]
const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach(async (to) => {
  if (to.meta.public) return true
  const user = userOf()
  if (!user || !tokenOf()) return { path: '/login', query: { redirect: to.fullPath } }
  if (user.role !== 'WORKER') return '/login'
  try {
    // 每次进入受保护路由都以服务端身份为准，清掉过期或被改写的本地会话。
    const me = await authApi.me()
    saveSession({ token: tokenOf(), user: me.user, expiresAt: me.expiresAt })
    return true
  } catch {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
})
export default router
