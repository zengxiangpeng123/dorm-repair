import axios from 'axios'
import { tokenOf, clearSession } from './session'

const http = axios.create({ baseURL: '/api/v1', timeout: 30000 })

const pendingIdempotency = new Map()

function idempotencyKey (url, body) {
  const fingerprint = url + '\n' + JSON.stringify(body ?? {})
  if (!pendingIdempotency.has(fingerprint)) {
    pendingIdempotency.set(fingerprint,
      globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`)
  }
  return pendingIdempotency.get(fingerprint)
}

function clearKey (url, body) {
  pendingIdempotency.delete(url + '\n' + JSON.stringify(body ?? {}))
}
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
  post: (url, body) => {
    const request = http.post(url, body ?? {}, { headers: { 'Idempotency-Key': idempotencyKey(url, body) } })
    return request.then(
      (value) => { clearKey(url, body); return value },
      (error) => { if (error.status && !['IDEMPOTENCY_IN_PROGRESS', 'IDEMPOTENCY_KEY_REUSED'].includes(error.code)) clearKey(url, body); return Promise.reject(error) }
    )
  },
  blob: (url) => http.get(url, { responseType: 'blob' }),
  put: (url, body) => {
    const request = http.put(url, body ?? {}, { headers: { 'Idempotency-Key': idempotencyKey(url, body) } })
    return request.then(
      (value) => { clearKey(url, body); return value },
      (error) => { if (error.status) clearKey(url, body); return Promise.reject(error) }
    )
  }
}
export { describeError } from '@dorm/contract'
