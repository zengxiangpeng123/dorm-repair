#!/usr/bin/env node
import fs from 'node:fs'
import path from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const API = (process.env.ACCEPTANCE_BASE_URL || 'http://127.0.0.1:8082/api/v1').replace(/\/$/, '')
const OUT = path.resolve(process.env.SCREENSHOT_DIR || 'app/tests/acceptance/screenshots')
function resolveBrowser() {
  if (process.env.BROWSER_PATH) return process.env.BROWSER_PATH
  const candidates = process.platform === 'win32'
    ? [
        path.join(process.env.LOCALAPPDATA || '', 'ms-playwright/chromium-1091/chrome-win/chrome.exe'),
        'C:/Program Files/Google/Chrome/Application/chrome.exe',
        'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
      ]
    : ['/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser']
  return candidates.find((candidate) => candidate && fs.existsSync(candidate)) || candidates[0]
}
const BROWSER = resolveBrowser()
const PASSWORD = process.env.ACCEPTANCE_PASSWORD || 'dorm@2026'
const report = { startedAt: new Date().toISOString(), screenshots: [], passed: 0, failed: 0 }

async function api(method, route, { token, body } = {}) {
  const headers = {}
  if (token) headers.Authorization = 'Bearer ' + token
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const response = await fetch(API + route, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  const text = await response.text()
  let json; try { json = text ? JSON.parse(text) : null } catch { json = null }
  if (!response.ok) throw new Error(`${method} ${route} ${response.status}: ${text.slice(0, 240)}`)
  return json?.data
}

async function login(account) {
  const data = await api('POST', '/auth/login', { body: { account, password: PASSWORD } })
  return { token: data.token, user: data.user, expiresAt: data.expiresAt }
}

async function findOrderForWorker(token, workerId) {
  const data = await api('GET', '/my/orders?status=ACTIVE&pageSize=50', { token })
  return (data.items || []).find((o) => o.workerId === workerId) || null
}

async function findPendingReview(adminToken) {
  const data = await api('GET', '/admin/rework-reviews', { token: adminToken })
  return (data.items || [])[0] || null
}

async function snap(page, app, name, { selector, mustContain } = {}) {
  if (selector) await page.locator(selector).first().waitFor({ state: 'visible', timeout: 15000 })
  await page.waitForLoadState('networkidle')
  await page.waitForTimeout(350)
  const text = await page.locator('body').innerText()
  if (mustContain && !text.includes(mustContain)) throw new Error(`${name} 缺少文本：${mustContain}`)
  const file = path.join(OUT, app, name + '.png')
  fs.mkdirSync(path.dirname(file), { recursive: true })
  await page.screenshot({ path: file, fullPage: true, animations: 'disabled' })
  report.screenshots.push({ app, name, file: path.relative(ROOT, file) })
  report.passed++
  console.log('PASS', app + '/' + name)
}

async function context(browser, app, viewport, session) {
  const c = await browser.newContext({ viewport, deviceScaleFactor: 1 })
  if (session) {
    const key = { student: 'dorm.student.session', worker: 'dorm.worker.session', admin: 'dorm.admin.session' }[app]
    await c.addInitScript(({ key, session }) => localStorage.setItem(key, JSON.stringify(session)), { key, session })
  }
  return c
}

async function ensureReviewState(student, worker, admin, workerAccount) {
  const existingReview = await findPendingReview(admin.token)
  if (existingReview) {
    const order = await api('GET', '/orders/' + existingReview.no, { token: student.token })
    return { order, review: existingReview }
  }
  let order = await findOrderForWorker(worker.token, workerAccount)
  if (order?.status === 'VERIFYING') {
    await api('POST', '/orders/' + order.no + '/rework-request', { token: student.token, body: { version: order.version, reason: '截图验收：维修后仍发现门锁松动，请管理员核对' } })
    return { order, review: await findPendingReview(admin.token) }
  }
  const png = fs.readFileSync(path.resolve('app/screens/vue/student-home.png'))
  const boundary = '----shot' + Date.now()
  const form = Buffer.concat([
    Buffer.from('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="shot.png"\r\nContent-Type: image/png\r\n\r\n'),
    png,
    Buffer.from('\r\n--' + boundary + '--\r\n')
  ])
  const upload = await fetch(API + '/files?purpose=BEFORE', { method: 'POST', headers: { Authorization: 'Bearer ' + student.token, 'Content-Type': 'multipart/form-data; boundary=' + boundary }, body: form })
  if (!upload.ok) throw new Error('截图准备上传失败 ' + upload.status + ': ' + (await upload.text()).slice(0, 200))
  const fid = (await upload.json()).data.id
  const created = await api('POST', '/orders', { token: student.token, body: { building: 'B7', floor: 4, room: '402', desc: '截图验收：宿舍门锁松动，维修后仍需复核', window: '全天（08:00-22:00）', photoIdsBefore: [fid] } })
  let current = created
  if (current.status === 'ASSIGNED' && current.workerId !== workerAccount) {
    current = await api('POST', '/orders/' + current.no + '/assign', { token: admin.token, body: { version: current.version, workerId: workerAccount } })
  } else if (current.status === 'PENDING') {
    current = await api('POST', '/orders/' + current.no + '/assign', { token: admin.token, body: { version: current.version, workerId: workerAccount } })
  }
  if (current.status === 'ASSIGNED') current = await api('POST', '/orders/' + current.no + '/arrive', { token: worker.token, body: { version: current.version } })
  const afterBoundary = '----shot-after' + Date.now()
  const afterForm = Buffer.concat([
    Buffer.from('--' + afterBoundary + '\r\nContent-Disposition: form-data; name="file"; filename="after.png"\r\nContent-Type: image/png\r\n\r\n'),
    png,
    Buffer.from('\r\n--' + afterBoundary + '--\r\n')
  ])
  const afterUpload = await fetch(API + '/files?purpose=AFTER', { method: 'POST', headers: { Authorization: 'Bearer ' + worker.token, 'Content-Type': 'multipart/form-data; boundary=' + afterBoundary }, body: afterForm })
  if (!afterUpload.ok) throw new Error('截图准备完工照失败 ' + afterUpload.status + ': ' + (await afterUpload.text()).slice(0, 200))
  const afterId = (await afterUpload.json()).data.id
  current = await api('POST', '/orders/' + current.no + '/finish', { token: worker.token, body: { version: current.version, photoIdsAfter: [afterId], note: '截图验收完工' } })
  await api('POST', '/orders/' + current.no + '/rework-request', { token: student.token, body: { version: current.version, reason: '截图验收：维修后仍发现门锁松动，请管理员核对' } })
  order = await api('GET', '/orders/' + current.no, { token: student.token })
  return { order, review: await findPendingReview(admin.token) }
}

async function main() {
  fs.mkdirSync(OUT, { recursive: true })
  const student = await login(process.env.ACCEPTANCE_STUDENT || 'S001')
  const workerAccount = process.env.ACCEPTANCE_WORKER || 'W0428'
  const worker = await login(workerAccount)
  const admin = await login(process.env.ACCEPTANCE_ADMIN || 'A001')
  const prepared = await ensureReviewState(student, worker, admin, workerAccount)
  const workerOrder = prepared.order
  const review = prepared.review
  if (!review) throw new Error('无法准备待审返修申请')
  const consoleErrors = []
  const browser = await chromium.launch({ executablePath: BROWSER, headless: true, args: ['--disable-gpu', '--no-sandbox'] })
  try {
    let c = await context(browser, 'student', { width: 430, height: 1100 }, student)
    let page = await c.newPage()
    page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push('student: ' + m.text()) })
    await page.goto('http://127.0.0.1:5173/student/?shot=1', { waitUntil: 'networkidle' })
    await snap(page, 'student', 'home', { selector: '.body', mustContain: '我要报修' })
    await page.goto('http://127.0.0.1:5173/student/new?shot=1', { waitUntil: 'networkidle' })
    await snap(page, 'student', 'new', { selector: '.body', mustContain: '提交报修' })
    await page.goto('http://127.0.0.1:5173/student/list?shot=1', { waitUntil: 'networkidle' })
    await snap(page, 'student', 'list', { selector: '.body' })
    if (workerOrder) {
      await page.goto('http://127.0.0.1:5173/student/detail/' + workerOrder.no + '?shot=1', { waitUntil: 'networkidle' })
      await snap(page, 'student', 'detail', { selector: '.body', mustContain: workerOrder.no })
    }
    await c.close()

    c = await context(browser, 'worker', { width: 430, height: 1100 }, worker)
    page = await c.newPage()
    page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push('worker: ' + m.text()) })
    await page.goto('http://127.0.0.1:5174/worker/?shot=1', { waitUntil: 'networkidle' })
    await snap(page, 'worker', 'home', { selector: '.worktop', mustContain: worker.user.name })
    await page.goto('http://127.0.0.1:5174/worker/building?shot=1', { waitUntil: 'networkidle' })
    await snap(page, 'worker', 'building', { selector: '.body' })
    await page.goto('http://127.0.0.1:5174/worker/parts?shot=1', { waitUntil: 'networkidle' })
    await snap(page, 'worker', 'parts', { selector: '.body' })
    if (workerOrder) {
      await page.goto('http://127.0.0.1:5174/worker/task/' + workerOrder.no + '?shot=1', { waitUntil: 'networkidle' })
      await snap(page, 'worker', 'task', { selector: '.body', mustContain: workerOrder.no })
    }
    await c.close()

    c = await context(browser, 'admin', { width: 1440, height: 1100 }, admin)
    page = await c.newPage()
    page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push('admin: ' + m.text()) })
    await page.goto('http://127.0.0.1:5175/admin/?shot=1', { waitUntil: 'networkidle' })
    await snap(page, 'admin', 'dashboard', { selector: '.desk-main', mustContain: '返修审查' })
    await page.locator('.review-row').first().waitFor({ state: 'visible', timeout: 15000 })
    await snap(page, 'admin', 'review-queue', { selector: '.review-panel', mustContain: review.no })
    await page.getByRole('button', { name: '确认返修' }).first().click()
    await snap(page, 'admin', 'review-modal', { selector: '.rv-sheet', mustContain: '提交审查' })
    await page.goto('http://127.0.0.1:5175/admin/people?shot=1', { waitUntil: 'networkidle' })
    await snap(page, 'admin', 'people', { selector: '.people-page', mustContain: '人员管理' })
    await c.close()
  } finally { await browser.close() }
  report.consoleErrors = consoleErrors
  if (consoleErrors.length) throw new Error('浏览器控制台错误：' + consoleErrors.join(' | '))
  report.ok = report.failed === 0
  report.finishedAt = new Date().toISOString()
  const out = path.resolve(process.env.SCREENSHOT_REPORT || 'app/tests/acceptance/screenshot-report.json')
  fs.mkdirSync(path.dirname(out), { recursive: true })
  fs.writeFileSync(out, JSON.stringify(report, null, 2))
  console.log(`SCREENSHOTS PASS ${report.passed}/${report.passed + report.failed}`)
}

main().catch((error) => {
  report.failed++
  report.ok = false
  report.error = error.stack || String(error)
  report.finishedAt = new Date().toISOString()
  const out = path.resolve(process.env.SCREENSHOT_REPORT || 'app/tests/acceptance/screenshot-report.json')
  fs.mkdirSync(path.dirname(out), { recursive: true })
  fs.writeFileSync(out, JSON.stringify(report, null, 2))
  console.error('SCREENSHOTS FAIL', error.stack || error)
  process.exitCode = 1
})
