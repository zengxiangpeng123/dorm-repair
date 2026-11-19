#!/usr/bin/env node
import fs from 'node:fs'
import path from 'node:path'
const base = (process.env.ACCEPTANCE_BASE_URL || 'http://127.0.0.1:8082/api/v1').replace(/\/$/, '')
const root = (process.env.ACCEPTANCE_ROOT_URL || base.replace(/\/api\/v1$/, '')).replace(/\/$/, '')
const report = { startedAt: new Date().toISOString(), checks: [], passed: 0, failed: 0 }
function check(name, ok, detail = '') {
  report.checks.push({ name, ok, detail }); if (ok) report.passed++; else report.failed++
  console.log((ok ? 'PASS ' : 'FAIL ') + name + (detail ? ' - ' + detail : ''))
}
async function login(account, password) {
  const r = await fetch(base + '/auth/login', { method: 'POST', headers: {'content-type':'application/json'}, body: JSON.stringify({account, password}) })
  if (!r.ok) throw new Error('login ' + r.status)
  return (await r.json()).data.token
}
const token = await login(process.env.ACCEPTANCE_STUDENT || 'S001', process.env.ACCEPTANCE_STUDENT_PASSWORD || 'dorm@2026')
const admin = await login(process.env.ACCEPTANCE_ADMIN || 'A001', process.env.ACCEPTANCE_ADMIN_PASSWORD || 'dorm@2026')
const aiHealth = await fetch(base + '/ai/health', { headers: { Authorization: 'Bearer ' + token } })
const aiBody = await aiHealth.json()
const ai = aiBody.data || {}
const expectedModel = process.env.ACCEPTANCE_AI_MODEL || 'raccoon/sn-deepseek-v4-1-flash-1017'
check('AI 健康端点可观测', aiHealth.ok && aiBody.data && 'up' in ai, JSON.stringify(ai))
check('AI 使用正式模型且已启用 LLM', ai.up === true && ai.llmEnabled === true && ai.llmReady === true
  && ai.mode === 'llm' && ai.remoteModel === expectedModel && ai.modelMatched === true,
  `up=${ai.up} mode=${ai.mode} llmEnabled=${ai.llmEnabled} remoteModel=${ai.remoteModel} warning=${ai.warning || ''}`)
const dbOrders = await fetch(base + '/orders?status=ALL', { headers: { Authorization: 'Bearer ' + admin } })
check('数据库读取可用', dbOrders.ok, 'orders status=' + dbOrders.status)
const readiness = await fetch(root + '/actuator/health/readiness')
const readinessBody = await readiness.json()
check('回滚后就绪探针', readiness.ok && readinessBody.status === 'UP', JSON.stringify(readinessBody))
report.finishedAt = new Date().toISOString(); report.ok = report.failed === 0
const out = path.resolve(process.env.RESILIENCE_REPORT || 'app/tests/acceptance/resilience-report.json')
fs.mkdirSync(path.dirname(out), { recursive: true }); fs.writeFileSync(out, JSON.stringify(report, null, 2))
console.log('RESILIENCE ' + (report.ok ? 'PASS' : 'FAIL') + ' ' + report.passed + '/' + report.checks.length)
if (!report.ok) process.exitCode = 1
