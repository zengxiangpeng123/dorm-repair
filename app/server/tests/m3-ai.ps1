# M3 验收：AI 定级服务接上了，且 AI 挂了不拦单。
# 跑法：pwsh -NoProfile -File tests/m3-ai.ps1
$ErrorActionPreference = 'Stop'
$serverDir = Split-Path -Parent $PSScriptRoot
$aiDir = Join-Path (Split-Path -Parent $serverDir) 'ai'
$py = 'D:\develop\python-envs\dorm-ai\Scripts\python.exe'
$base = 'http://localhost:8082/api/v1'
$aiBase = 'http://127.0.0.1:8001'
$adminHeaders = @{ 'X-Actor-Type' = 'ADMIN'; 'X-Actor-Id' = 'A001'; 'X-Actor-Name' = 'Admin' }
$pass = 0; $fail = 0

function Check($name, $cond, $detail = '') {
    if ($cond) { $script:pass++; "  [OK]   $name" }
    else { $script:fail++; "  [FAIL] $name  $detail" }
}

function Wait-Up($url, $seconds = 60) {
    for ($i = 0; $i -lt $seconds; $i++) {
        try { Invoke-RestMethod $url -TimeoutSec 2 | Out-Null; return $true } catch { Start-Sleep -Seconds 1 }
    }
    return $false
}

function Kill-Port($port) {
    Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
        ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
}

"== 0 清场"
Kill-Port 8082; Kill-Port 8001
Start-Sleep -Seconds 1

"== 1 起 AI 服务"
$aiLog = Join-Path $env:TEMP 'm3-ai.log'
$ai = Start-Process $py -ArgumentList '-m', 'uvicorn', 'app:app', '--host', '127.0.0.1', '--port', '8001' `
    -WorkingDirectory $aiDir -PassThru -WindowStyle Hidden -RedirectStandardOutput $aiLog
Check 'AI 服务起来' (Wait-Up "$aiBase/health")
$h = Invoke-RestMethod "$aiBase/health"
Check 'AI /health 报 5 类规则' ($h.rules -eq 5) ($h | ConvertTo-Json -Compress)

"== 2 直连 AI 判两段描述"
$r1 = Invoke-RestMethod "$aiBase/classify" -Method Post -ContentType 'application/json' -Body '{"desc":"插座打火，有焦味，不敢用了"}'
Check '打火 → 紧急' ($r1.level -eq '紧急') ($r1 | ConvertTo-Json -Compress)
Check '打火 → 电气' ($r1.category -eq '电气')
$r2 = Invoke-RestMethod "$aiBase/classify" -Method Post -ContentType 'application/json' -Body '{"desc":"洗手盆软管一直滴水，水桶快接满了"}'
Check '快接满了 → 紧急（浸水风险）' ($r2.level -eq '紧急') ($r2 | ConvertTo-Json -Compress)
Check '快接满了 → 给排水' ($r2.category -eq '给排水')
Check '依据里写明浸水风险' ($r2.basis -like '*浸水风险*') $r2.basis

"== 3 起后端（连真库）"
$env:APP_STORE = 'mysql'; $env:DB_NAME = 'dorm_repair_test'
    $env:APP_DEMO = 'true'; $env:DB_USER = 'root'; $env:DB_PASSWORD = '123456'
$jar = Join-Path $serverDir 'target\dorm-repair-server-1.0.0.jar'
if (-not (Test-Path $jar)) { throw "先打包：cd app/server; mvn -q -DskipTests package" }
$srvLog = Join-Path $env:TEMP 'm3-server.log'
$env:APP_AUTH_ALLOW_HEADER = 'true'
$srv = Start-Process java -ArgumentList '-jar', $jar -WorkingDirectory $serverDir -PassThru -WindowStyle Hidden -RedirectStandardOutput $srvLog
Check '后端起来' (Wait-Up "$base/health" 90)

"== 4 后端看得到 AI"
$a = Invoke-RestMethod "$base/ai/health" -Headers $adminHeaders
Check '/ai/health 报 up=true' ($a.data.up -eq $true) ($a | ConvertTo-Json -Compress)
Check '延迟在 200ms 内' ($a.data.latencyMs -lt 200) ("latencyMs=" + $a.data.latencyMs)

"== 5 建单走真 AI"
Invoke-RestMethod "$base/demo/reset" -Method Post -ContentType 'application/json' -Headers $adminHeaders -Body '{}' | Out-Null
$body = '{"building":"B7","floor":4,"room":"402","desc":"插座打火，有焦味，不敢用了","window":"上午 08:00-12:00","photosBefore":["demo://b1.jpg"]}'
$o = (Invoke-RestMethod "$base/orders" -Method Post -ContentType 'application/json; charset=utf-8' -Headers @{ 'X-Actor-Type' = 'student'; 'X-Actor-Id' = 'S001' } -Body ([System.Text.Encoding]::UTF8.GetBytes($body))).data
Check '建单 201 且待派工' ($o.status -eq 'PENDING') $o.status
Check '类型由 AI 定（电气）' ($o.category -eq '电气') $o.category
Check '紧急程度由 AI 定（紧急）' ($o.level -eq '紧急') $o.level
Check 'levelSource=AI' ($o.levelSource -eq 'AI') $o.levelSource
Check '依据是 AI 写的' ($o.aiBasis -like '*安全类*') $o.aiBasis

"== 6 杀掉 AI，再建一单"
Stop-Process -Id $ai.Id -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2
$body2 = '{"building":"B8","floor":2,"room":"205","desc":"床板有点响，翻个身就咯吱","window":"全天（08:00-22:00）","photosBefore":["demo://b2.jpg"]}'
$o2 = (Invoke-RestMethod "$base/orders" -Method Post -ContentType 'application/json; charset=utf-8' -Headers @{ 'X-Actor-Type' = 'student'; 'X-Actor-Id' = 'S003' } -Body ([System.Text.Encoding]::UTF8.GetBytes($body2))).data
Check 'AI 挂了照样建单成功' ($o2.status -eq 'PENDING') ($o2 | ConvertTo-Json -Compress)
Check 'levelSource=FALLBACK' ($o2.levelSource -eq 'FALLBACK') $o2.levelSource
Check '兜底不是普通' ($o2.level -eq '较急') $o2.level
Check '依据写明兜底' ($o2.aiBasis -like '*兜底*') $o2.aiBasis

"== 7 收尾"
$a2 = Invoke-RestMethod "$base/ai/health" -Headers $adminHeaders
Check '/ai/health 报 up=false' ($a2.data.up -eq $false)
Stop-Process -Id $srv.Id -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 1
Kill-Port 8082
""
"结果：$pass 通过 / $fail 失败"
exit ([int]($fail -gt 0))
