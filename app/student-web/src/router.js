import { createRouter, createWebHistory } from 'vue-router'
import { saveSession, tokenOf, userOf } from './api/session'
import { authApi } from './api/auth'
import Login from './views/Login.vue'

const routes = [
  { path: '/', redirect: '/student' },
  { path: '/login', component: Login, meta: { public: true } },
  { path: '/student', component: () => import('./views/student/Home.vue') },
  { path: '/student/new', component: () => import('./views/student/New.vue') },
  { path: '/student/list', component: () => import('./views/student/List.vue') },
  { path: '/student/detail/:no?', component: () => import('./views/student/Detail.vue') },
  { path: '/:pathMatch(.*)*', redirect: '/student' }
]
const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach(async (to) => {
  if (to.meta.public) return true
  const user = userOf()
  if (!user || !tokenOf()) return { path: '/login', query: { redirect: to.fullPath } }
  if (user.role !== 'STUDENT') return '/login'
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
