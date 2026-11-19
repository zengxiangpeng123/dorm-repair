#!/usr/bin/env node
import { spawnSync } from 'node:child_process'
const commands = [
  ['node', ['app/tests/acceptance/preflight.mjs']],
  ['node', ['app/tests/acceptance/run.mjs']],
  ['node', ['app/tests/acceptance/security.mjs']],
  ['node', ['app/tests/acceptance/resilience.mjs']],
  ['node', ['app/tests/acceptance/people.mjs']],
  ['node', ['app/tests/acceptance/load.mjs']],
  ['node', ['app/tests/check-three-apps.mjs']],
  ['node', ['app/tests/shoot-three-apps.mjs']]
]
function waitForRateLimitWindow() {
  const seconds = Number(process.env.ACCEPTANCE_RATE_LIMIT_WAIT_SECONDS || 0)
  if (seconds <= 0) return
  console.log('\n> waiting ' + seconds + 's for login rate-limit window')
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, seconds * 1000)
}
let failed = 0
for (const [cmd, args] of commands) {
  if (args.includes('app/tests/acceptance/load.mjs')) waitForRateLimitWindow()
  console.log('\n> ' + cmd + ' ' + args.join(' '))
  const childEnv = { ...process.env }
  if (args.includes('app/tests/shoot-three-apps.mjs')) {
    childEnv.SCREENSHOT_DIR = childEnv.ACCEPTANCE_SCREENSHOT_DIR || 'app/tests/acceptance/screenshots'
  }
  const r = spawnSync(cmd, args, { stdio: 'inherit', env: childEnv })
  if (r.status !== 0) failed++
}
console.log('\nACCEPTANCE SUITE ' + (failed ? 'FAIL' : 'PASS'))
if (failed) process.exitCode = 1
