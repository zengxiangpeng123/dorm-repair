import axios from 'axios'
import { actorOf, roleOfPath, tokenOf } from './session'

/**
 * 唯一的接口出口。页面里不许自己写 fetch / axios。
 *
 * 后端统一返回 {ok:true,data} 或 {ok:false,error:{code,message,...}}。
 * 这里把 data 剥出来往上抛，出错就抛一个带 code / status / detail 的 Error，
 * 页面只负责把它显示成人话（见 describeError）。
 */
const http = axios.create({ baseURL: '/api/v1', timeout: 12000 })

http.interceptors.request.use((cfg) => {
  const token = tokenOf()
  if (token) {
    cfg.headers.Authorization = `Bearer ${token}`
  } else {
    const role = cfg.role || roleOfPath(window.location.pathname)
    const actor = actorOf(role)
    cfg.headers['X-Actor-Type'] = actor.type
    cfg.headers['X-Actor-Id'] = actor.id
  }
  return cfg
})

http.interceptors.response.use(
  (res) => res.data?.data ?? res.data,
  (err) => {
    const body = err.response?.data
    const e = new Error(body?.error?.message || err.message || '请求失败')
    e.status = err.response?.status
    e.code = body?.error?.code
    e.detail = body?.error || {}
    return Promise.reject(e)
  }
)

export const api = {
  get: (url, params, opts = {}) => http.get(url, { params, ...opts }),
  post: (url, body, opts = {}) => http.post(url, body ?? {}, opts),
  put: (url, body, opts = {}) => http.put(url, body ?? {}, opts)
}

/** 把接口错误翻成能给人看的一句话。409 要把"现在只能做什么"讲出来。 */
export function describeError (e) {
  const d = e.detail || {}
  if (e.status === 409) {
    if (d.reason === 'VERSION_CONFLICT') return '这条单刚被别人动过，刷新一下再试'
    const allowed = (d.allowed || []).join(' / ')
    const now = d.from ? `这单现在是「${labelOf(d.from)}」` : ''
    return [now, allowed ? `只能做：${allowed}` : '', d.message || ''].filter(Boolean).join('，')
  }
  if (e.status === 422) return `${e.message}${d.reason ? `（${d.reason}）` : ''}`
  if (e.status === 403) return '这个身份不能做这件事'
  if (e.status === 404) return '找不到这张单'
  if (e.status === 501) return '这条边还没批（变更单 CR-001），接口返回 501'
  return e.message || '出错了'
}

function labelOf (code) {
  const map = {
    PENDING: '待派工', ASSIGNED: '已派工', REPAIRING: '维修中', PARTS: '待配件',
    VERIFYING: '待验收', REWORKING: '返修中', DONE: '已完成', CLOSED: '已关闭', CANCELLED: '已取消'
  }
  return map[code] || code
}
