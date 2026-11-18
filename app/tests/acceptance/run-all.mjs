#!/usr/bin/env node
import { spawnSync } from 'node:child_process'
const commands = [
  ['node', ['app/tests/acceptance/run.mjs']],
  ['node', ['app/tests/acceptance/security.mjs']],
  ['node', ['app/tests/acceptance/resilience.mjs']],
  ['node', ['app/tests/acceptance/people.mjs']],
  ['node', ['app/tests/acceptance/load.mjs']],
  ['node', ['app/tests/check-three-apps.mjs']]
]
let failed = 0
for (const [cmd, args] of commands) {
  console.log('\n> ' + cmd + ' ' + args.join(' '))
  const r = spawnSync(cmd, args, { stdio: 'inherit', env: process.env })
  if (r.status !== 0) failed++
}
console.log('\nACCEPTANCE SUITE ' + (failed ? 'FAIL' : 'PASS'))
if (failed) process.exitCode = 1
