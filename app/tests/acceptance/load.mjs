#!/usr/bin/env node
import fs from 'node:fs'
import path from 'node:path'
const base = (process.env.ACCEPTANCE_BASE_URL || 'http://127.0.0.1:8082/api/v1').replace(/\/$/, '')
const total = Number(process.env.LOAD_REQUESTS || 200)
const concurrency = Math.max(1, Number(process.env.LOAD_CONCURRENCY || 20))
const account = process.env.ACCEPTANCE_STUDENT || 'S001'
const password = process.env.ACCEPTANCE_STUDENT_PASSWORD || 'dorm@2026'

async function login() {
  const r = await fetch(base + '/auth/login', {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ account, password })
  })
  if (!r.ok) throw new Error('login failed: ' + r.status)
  return (await r.json()).data.token
}

async function one(token, i) {
  const start = performance.now()
  const r = await fetch(base + '/my/orders?status=ALL', { headers: { Authorization: 'Bearer ' + token } })
  await r.arrayBuffer()
  return { ms: performance.now() - start, status: r.status, i }
}

const token = await login()
const durations = []
let errors = 0
let next = 0
const started = performance.now()
async function worker() {
  while (true) {
    const i = next++
    if (i >= total) return
    try {
      const r = await one(token, i)
      durations.push(r.ms)
      if (r.status >= 400) errors++
    } catch { errors++ }
  }
}
await Promise.all(Array.from({ length: concurrency }, worker))
const elapsed = (performance.now() - started) / 1000
durations.sort((a, b) => a - b)
const pct = p => durations.length ? durations[Math.min(durations.length - 1, Math.floor(durations.length * p))] : 0
const report = {
  startedAt: new Date().toISOString(), base, total, concurrency, elapsedSeconds: Number(elapsed.toFixed(3)),
  throughputPerSecond: Number((durations.length / elapsed).toFixed(2)), errors,
  errorRate: Number((errors / total).toFixed(4)),
  p50Ms: Number(pct(0.50).toFixed(2)), p95Ms: Number(pct(0.95).toFixed(2)),
  p99Ms: Number(pct(0.99).toFixed(2)), maxMs: Number((durations.at(-1) || 0).toFixed(2))
}
const out = path.resolve(process.env.LOAD_REPORT || 'app/tests/acceptance/load-report.json')
fs.mkdirSync(path.dirname(out), { recursive: true })
fs.writeFileSync(out, JSON.stringify(report, null, 2))
console.log(JSON.stringify(report, null, 2))
if (errors / total > 0.01 || report.p95Ms > 2000) process.exitCode = 1
