import { createRouter, createWebHistory } from 'vue-router'
import { saveSession, tokenOf, userOf } from './api/session'
import { authApi } from './api/auth'
import Login from './views/Login.vue'

const routes = [
  { path: '/', redirect: '/admin' },
  { path: '/login', component: Login, meta: { public: true } },
  { path: '/admin', component: () => import('./views/admin/Home.vue') },
  { path: '/admin/people', component: () => import('./views/admin/People.vue') },
  { path: '/:pathMatch(.*)*', redirect: '/admin' }
]
const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach(async (to) => {
  if (to.meta.public) return true
  const user = userOf()
  if (!user || !tokenOf()) return { path: '/login', query: { redirect: to.fullPath } }
  if (user.role !== 'ADMIN') return '/login'
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
