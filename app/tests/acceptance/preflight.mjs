#!/usr/bin/env node
import fs from 'node:fs'
import path from 'node:path'
import { createRequire } from 'node:module'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const require = createRequire(import.meta.url)
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..')
function resetTestRateLimit() {
  if (process.env.ACCEPTANCE_RESET_RATE_LIMIT !== '1') return
  const mysql = process.env.MYSQL_BIN || 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe'
  const db = process.env.DB_NAME || 'dorm_repair_test'
  execFileSync(mysql, ['-uroot', '-p123456', '--default-character-set=utf8mb4', db, '-e', 'DELETE FROM t_login_attempt'], { stdio: 'pipe' })
  console.log('PASS 测试库登录限流已重置 - ' + db)
}
const checks = []
resetTestRateLimit()
function check(name, ok, detail = '') { checks.push({ name, ok, detail }); console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${detail ? ' - ' + detail : ''}`) }
async function http(name, url) {
  try { const r = await fetch(url, { signal: AbortSignal.timeout(4000) }); check(name, r.ok, String(r.status)) }
  catch (e) { check(name, false, e.message) }
}
try {
  require.resolve(process.env.PLAYWRIGHT_MODULE || 'playwright')
  check('Playwright 模块可用', true, process.env.PLAYWRIGHT_MODULE || 'playwright')
} catch { check('Playwright 模块可用', false, '设置 PLAYWRIGHT_MODULE 或安装 playwright') }
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
const browser = resolveBrowser()
check('Chromium 可执行文件存在', fs.existsSync(browser), browser)
check('截图源 PNG 存在', fs.existsSync(path.join(root, 'app/screens/vue/student-home.png')), 'app/screens/vue/student-home.png')
await http('后端健康', 'http://127.0.0.1:8082/api/v1/health')
await http('学生端在线', 'http://127.0.0.1:5173/student/')
await http('师傅端在线', 'http://127.0.0.1:5174/worker/')
await http('管理端在线', 'http://127.0.0.1:5175/admin/')
const failed = checks.filter((c) => !c.ok)
console.log(`PREFLIGHT ${failed.length ? 'FAIL' : 'PASS'} ${checks.length - failed.length}/${checks.length}`)
if (failed.length) process.exitCode = 1
