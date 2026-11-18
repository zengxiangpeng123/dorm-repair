const SESSION_KEY = 'dorm.admin.session'
const EXPECTED_ROLE = 'ADMIN'

export function saveSession(payload) {
  if (payload?.user?.role !== EXPECTED_ROLE) throw new Error('这个入口只接受 ADMIN 账号')
  localStorage.setItem(SESSION_KEY, JSON.stringify(payload))
  return payload
}
export function sessionOf() {
  try { return JSON.parse(localStorage.getItem(SESSION_KEY) || 'null') } catch { return null }
}
export function tokenOf() { return sessionOf()?.token || '' }
export function userOf() { return sessionOf()?.user || null }
export function clearSession() { localStorage.removeItem(SESSION_KEY) }
