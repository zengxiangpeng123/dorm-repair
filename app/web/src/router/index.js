import { createRouter, createWebHistory } from 'vue-router'
import { userOf } from '../api/session'

/**
 * 三端一套代码：学生端/师傅端是手机页（430 壳），管理端是桌面页（desk 壳）。
 * 路由前缀决定用哪张皮，也决定请求头里带哪个身份。
 */
const routes = [
  { path: '/', name: 'entry', component: () => import('../views/Entry.vue'), meta: { shell: 'desk' } },
  { path: '/login', name: 'login', component: () => import('../views/Login.vue'), meta: { shell: 'desk', public: true } },

  { path: '/student', name: 's-home', component: () => import('../views/student/Home.vue'), meta: { shell: 'phone' } },
  { path: '/student/new', name: 's-new', component: () => import('../views/student/New.vue'), meta: { shell: 'phone' } },
  { path: '/student/list', name: 's-list', component: () => import('../views/student/List.vue'), meta: { shell: 'phone' } },
  { path: '/student/detail/:no?', name: 's-detail', component: () => import('../views/student/Detail.vue'), meta: { shell: 'phone' } },

  { path: '/worker', name: 'w-home', component: () => import('../views/worker/Home.vue'), meta: { shell: 'phone' } },
  { path: '/worker/building', name: 'w-building', component: () => import('../views/worker/Building.vue'), meta: { shell: 'phone' } },
  { path: '/worker/task/:no?', name: 'w-task', component: () => import('../views/worker/Task.vue'), meta: { shell: 'phone' } },
  { path: '/worker/parts', name: 'w-parts', component: () => import('../views/worker/Parts.vue'), meta: { shell: 'phone' } },

  { path: '/admin', name: 'a-home', component: () => import('../views/admin/Home.vue'), meta: { shell: 'desk' } },
  { path: '/lab', name: 'lab', component: () => import('../views/Lab.vue'), meta: { shell: 'desk' } },

  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  if (to.meta.public || to.path === '/') return true
  const user = userOf()
  if (!user) return { path: '/login', query: { redirect: to.fullPath } }
  const role = String(user.role || '').toUpperCase()
  if (to.path.startsWith('/student') && role !== 'STUDENT') return '/login'
  if (to.path.startsWith('/worker') && role !== 'WORKER' && role !== 'ADMIN') return '/login'
  if (to.path.startsWith('/admin') && role !== 'ADMIN') return '/login'
  return true
})

export default router
