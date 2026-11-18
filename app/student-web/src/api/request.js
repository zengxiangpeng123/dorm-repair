import axios from 'axios'
import { tokenOf, clearSession } from './session'

const http = axios.create({ baseURL: '/api/v1', timeout: 30000 })
http.interceptors.request.use((cfg) => {
  const token = tokenOf()
  if (token) cfg.headers.Authorization = `Bearer ${token}`
  return cfg
})
http.interceptors.response.use(
  (res) => res.data?.data ?? res.data,
  (err) => {
    if (err.response?.status === 401) clearSession()
    const body = err.response?.data
    const e = new Error(body?.error?.message || err.message || '请求失败')
    e.status = err.response?.status
    e.code = body?.error?.code
    e.detail = body?.error || {}
    return Promise.reject(e)
  }
)
export const api = {
  get: (url, params) => http.get(url, { params }),
  post: (url, body) => http.post(url, body ?? {}),
  put: (url, body) => http.put(url, body ?? {})
}
export { describeError } from '@dorm/contract'
