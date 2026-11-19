#!/usr/bin/env node
import fs from 'node:fs'
import path from 'node:path'

const base = (process.env.ACCEPTANCE_BASE_URL || 'http://127.0.0.1:8082/api/v1').replace(/\/$/, '')
const report = { startedAt: new Date().toISOString(), checks: [], passed: 0, failed: 0 }
function check(name, ok, detail = '') {
  report.checks.push({ name, ok, detail })
  if (ok) report.passed++; else report.failed++
  console.log((ok ? 'PASS ' : 'FAIL ') + name + (detail ? ' - ' + detail : ''))
}
async function req(method, url, { token, body, expected = [200] } = {}) {
  const headers = {}
  if (token) headers.Authorization = 'Bearer ' + token
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const response = await fetch(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  const text = await response.text()
  let json
  try { json = text ? JSON.parse(text) : null } catch { json = null }
  if (!expected.includes(response.status)) throw new Error(method + ' ' + url + ' expected ' + expected.join('/') + ' got ' + response.status + ': ' + text.slice(0, 300))
  return { response, json, text }
}
async function login(account, password, expected = [200]) {
  return req('POST', base + '/auth/login', { body: { account, password }, expected })
}

const suffix = String(Date.now()).slice(-8)
const id = 'W9' + suffix
const password = 'Strong-Worker-2026'
const admin = (await login('A001', 'dorm@2026')).json.data.token
const temporaryWorkerIds = []
try {
  const create = await req('POST', base + '/admin/users', {
    token: admin,
    body: { id, role: 'WORKER', name: '生命周期验收师傅', team: '综合班', buildings: ['B7'], password }
  })
  temporaryWorkerIds.push(id)
  check('管理员创建临时师傅', create.json?.data?.id === id, id)

  const workerToken = (await login(id, password)).json.data.token
  check('临时师傅可登录', Boolean(workerToken))

  const disabled = await req('POST', base + '/admin/users/' + id + '/active', {
    token: admin, body: { active: false }
  })
  check('停用账号并撤销会话', disabled.json?.data?.active === false && disabled.json.data.revokedSessions >= 1, JSON.stringify(disabled.json?.data))

  const stale = await req('GET', base + '/my/orders', { token: workerToken, expected: [401] })
  check('停用后旧令牌失效', stale.response.status === 401)
  const disabledLogin = await login(id, password, [401])
  check('停用后禁止登录且不泄露账号状态', disabledLogin.json?.error?.code === 'UNAUTHORIZED')

  const enabled = await req('POST', base + '/admin/users/' + id + '/active', {
    token: admin, body: { active: true }
  })
  check('恢复账号', enabled.json?.data?.active === true, JSON.stringify(enabled.json?.data))
  await login(id, password)
  check('恢复后可重新登录', true)

  const student = (await login('S001', 'dorm@2026')).json.data.token
  const denied = await req('POST', base + '/admin/users/' + id + '/active', {
    token: student, body: { active: false }, expected: [403]
  })
  check('非管理员不能停用账号', denied.response.status === 403)

  const self = await req('POST', base + '/admin/users/A001/active', {
    token: admin, body: { active: false }, expected: [409]
  })
  check('管理员不能停用自己', self.json?.error?.code === 'CANNOT_DISABLE_SELF')
} finally {
  for (const workerId of temporaryWorkerIds) {
    const disabled = await req('POST', base + '/admin/users/' + workerId + '/active', {
      token: admin, body: { active: false }
    }).catch(() => null)
    if (!disabled) console.warn('WARN 临时账号停用失败：' + workerId)
  }
  const users = await req('GET', base + '/admin/users', { token: admin }).catch(() => null)
  for (const worker of users?.json?.data?.items || []) {
    if (worker.id?.startsWith('W9')) {
      await req('POST', base + '/admin/users/' + worker.id + '/active', {
        token: admin, body: { active: false }
      }).catch(() => null)
    }
  }
}
report.finishedAt = new Date().toISOString()
report.ok = report.failed === 0
const out = path.resolve(process.env.PEOPLE_REPORT || 'app/tests/acceptance/people-report.json')
fs.mkdirSync(path.dirname(out), { recursive: true })
fs.writeFileSync(out, JSON.stringify(report, null, 2))
console.log('PEOPLE ' + (report.ok ? 'PASS' : 'FAIL') + ' ' + report.passed + '/' + report.checks.length)
if (!report.ok) process.exitCode = 1
