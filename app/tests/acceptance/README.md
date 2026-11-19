# Production acceptance tools

These scripts exercise a running environment. They are not a substitute for a real pilot
with real users and signed business acceptance.

## Required environment

- API base: `ACCEPTANCE_BASE_URL` (default `http://127.0.0.1:8082/api/v1`)
- Root health/metrics base: `ACCEPTANCE_ROOT_URL` (default derived from API base)
- Student/worker/admin test accounts and passwords
- A valid PNG at `app/screens/vue/student-home.png` for upload checks

## Commands

```powershell
node app/tests/acceptance/run.mjs
node app/tests/acceptance/security.mjs
node app/tests/acceptance/resilience.mjs
node app/tests/acceptance/people.mjs
$env:LOAD_REQUESTS='100'; $env:LOAD_CONCURRENCY='10'; node app/tests/acceptance/load.mjs
node app/tests/acceptance/preflight.mjs
node app/tests/shoot-three-apps.mjs
node app/tests/acceptance/run-all.mjs
```

截图验收需要三端开发服务器（5173/5174/5175）和 Playwright。通过 `PLAYWRIGHT_MODULE` 指向已安装的 Playwright，或在本机安装后直接运行。默认输出到被忽略的 `app/tests/acceptance/screenshots/`；只有显式设置 `SCREENSHOT_DIR=app/screens/three-apps` 才更新正式归档。隔离测试库可用 `ACCEPTANCE_RESET_RATE_LIMIT=1` 在预检阶段清理登录失败计数。

The scripts write `report.json`, `security-report.json`, `resilience-report.json`,
`people-report.json`, and `load-report.json`. Load checks fail above 1% errors or P95 2000 ms. The AI outage drill
must be performed separately: stop the AI process, submit a real order, verify
`levelSource=FALLBACK`, then restart AI and verify `/health`.
