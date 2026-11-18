#!/usr/bin/env node
import fs from 'node:fs'
import path from 'node:path'

const base = (process.env.ACCEPTANCE_BASE_URL || 'http://127.0.0.1:8082/api/v1').replace(/\/$/, '')
const root = (process.env.ACCEPTANCE_ROOT_URL || base.replace(/\/api\/v1$/, '')).replace(/\/$/, '')
const accounts = {
  student: { account: process.env.ACCEPTANCE_STUDENT || 'S001', password: process.env.ACCEPTANCE_STUDENT_PASSWORD || 'dorm@2026' },
  worker: { account: process.env.ACCEPTANCE_WORKER || 'W0428', password: process.env.ACCEPTANCE_WORKER_PASSWORD || 'dorm@2026' },
  admin: { account: process.env.ACCEPTANCE_ADMIN || 'A001', password: process.env.ACCEPTANCE_ADMIN_PASSWORD || 'dorm@2026' }
}
const report = { startedAt: new Date().toISOString(), base, checks: [], passed: 0, failed: 0 }

function record(name, ok, detail = '') {
  report.checks.push({ name, ok, detail })
  if (ok) report.passed++; else report.failed++
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${detail ? ' - ' + detail : ''}`)
}

async function request(method, url, { token, body, headers = {}, expected = [200] } = {}) {
  const h = { ...headers }
  if (token) h.Authorization = 'Bearer ' + token
  if (body !== undefined) h['Content-Type'] = h['Content-Type'] || 'application/json'
  const response = await fetch(url, {
    method,
    headers: h,
    body: body === undefined ? undefined : (Buffer.isBuffer(body) ? body : JSON.stringify(body))
  })
  const text = await response.text()
  let json
  try { json = text ? JSON.parse(text) : null } catch { json = null }
  if (!expected.includes(response.status)) {
    throw new Error(`${method} ${url} expected ${expected.join('/')} got ${response.status}: ${text.slice(0, 300)}`)
  }
  return { response, json, text }
}

async function login(role) {
  const r = await request('POST', base + '/auth/login', { body: accounts[role] })
  return r.json.data.token
}

async function upload(token, purpose = 'BEFORE') {
  const bytes = fs.readFileSync(path.resolve('app/screens/vue/student-home.png'))
  const boundary = '----acceptance' + Date.now()
  const head = Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="acceptance.png"\r\nContent-Type: image/png\r\n\r\n`)
  const tail = Buffer.from(`\r\n--${boundary}--\r\n`)
  const body = Buffer.concat([head, bytes, tail])
  const r = await request('POST', base + '/files?purpose=' + purpose, {
    token,
    body,
    headers: { 'Content-Type': 'multipart/form-data; boundary=' + boundary }
  })
  return r.json.data.id
}

async function main() {
  const student = await login('student')
  const worker = await login('worker')
  const admin = await login('admin')
  record('三端账号登录', true)

  const health = await request('GET', root + '/actuator/health')
  record('后端健康检查', health.json?.status === 'UP', JSON.stringify(health.json))
  const readiness = await request('GET', root + '/actuator/health/readiness')
  record('后端就绪探针', readiness.json?.status === 'UP', JSON.stringify(readiness.json))
  const metrics = await request('GET', root + '/actuator/prometheus')
  record('Prometheus 指标', metrics.text.includes('http_server_requests'), 'http metrics present')

  const studentDenied = await request('GET', base + '/admin/ai-reviews', { token: student, expected: [403] })
  record('学生不能读取管理端审计', studentDenied.response.status === 403)

  const fid = await upload(student)
  const description = `验收工单 ${Date.now()}：宿舍门锁松动，需要维修`
  const submitted = await request('POST', base + '/orders', {
    token: student,
    body: { building: 'B7', floor: 4, room: '402', desc: description, window: '全天（08:00-22:00）', photoIdsBefore: [fid] },
    expected: [201]
  })
  const order = submitted.json.data
  record('真实工单提交与快速定级', Boolean(order.no && order.version), `${order.no} ${order.status}`)

  let current = (await request('GET', base + `/orders/${order.no}`, { token: student })).json.data
  if (current.status === 'PENDING') {
    current = (await request('POST', base + `/orders/${order.no}/assign`, { token: admin, body: { version: current.version, workerId: 'W0428' } })).json.data
  }
  record('派工', ['ASSIGNED', 'REPAIRING'].includes(current.status), current.status)

  if (current.status === 'ASSIGNED') {
    current = (await request('POST', base + `/orders/${order.no}/arrive`, { token: worker, body: { version: current.version } })).json.data
  }
  record('师傅到门', current.status === 'REPAIRING', current.status)

  const afterId = await upload(worker, 'AFTER')
  current = (await request('POST', base + `/orders/${order.no}/finish`, {
    token: worker, body: { version: current.version, photoIdsAfter: [afterId], note: '验收链路完成' }
  })).json.data
  record('完工提交', current.status === 'VERIFYING', current.status)

  current = (await request('POST', base + `/orders/${order.no}/rework-request`, {
    token: student, body: { version: current.version, reason: '验收时仍发现门锁松动，申请返修' }, expected: [202]
  })).json.data.order
  record('返修申请先审查', current.status === 'VERIFYING', current.status)

  const rejected = await request('POST', base + `/orders/${order.no}/rework-review`, {
    token: admin, body: { version: current.version, decision: 'REJECT', reason: '验收证据不足，先完成复核' }
  })
  current = rejected.json.data.original
  record('管理员驳回误报', current.status === 'VERIFYING' && (rejected.json.data.reworkOrder ?? null) === null, current.status)

  current = (await request('POST', base + `/orders/${order.no}/accept`, {
    token: student, body: { version: current.version, signerName: '验收学生' }
  })).json.data
  record('验收通过', current.status === 'DONE', current.status)

  const idemFid = await upload(student)
  const idemBody = {
    building: 'B7', floor: 4, room: '402', desc: `幂等验收 ${Date.now()}：插座松动`,
    window: '全天（08:00-22:00）', photoIdsBefore: [idemFid]
  }
  const key = 'acceptance-' + Date.now()
  const [a, b] = await Promise.all([
    request('POST', base + '/orders', { token: student, body: idemBody, headers: { 'Idempotency-Key': key }, expected: [201, 409] }),
    request('POST', base + '/orders', { token: student, body: idemBody, headers: { 'Idempotency-Key': key }, expected: [201, 409] })
  ])
  const oneCreated = (a.response.status === 201) !== (b.response.status === 201)
  const replayed = a.response.status === 201 && b.response.status === 201 && a.json?.data?.no === b.json?.data?.no
  record('并发幂等只执行一次', oneCreated || replayed, `${a.response.status}/${b.response.status}`)

  report.finishedAt = new Date().toISOString()
  report.ok = report.failed === 0
  const out = path.resolve(process.env.ACCEPTANCE_REPORT || 'app/tests/acceptance/report.json')
  fs.mkdirSync(path.dirname(out), { recursive: true })
  fs.writeFileSync(out, JSON.stringify(report, null, 2))
  console.log(`ACCEPTANCE ${report.ok ? 'PASS' : 'FAIL'} ${report.passed}/${report.checks.length}`)
  if (!report.ok) process.exitCode = 1
}

main().catch(error => {
  record('验收执行异常', false, error.stack || String(error))
  report.finishedAt = new Date().toISOString()
  report.ok = false
  const out = path.resolve(process.env.ACCEPTANCE_REPORT || 'app/tests/acceptance/report.json')
  fs.mkdirSync(path.dirname(out), { recursive: true })
  fs.writeFileSync(out, JSON.stringify(report, null, 2))
  process.exitCode = 1
})
