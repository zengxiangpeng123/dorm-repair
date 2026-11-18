# ============================================================
# M2 验收脚本：MySQL 仓储
#   1) 用 APP_STORE=mysql 起服务（真库 dorm_repair）
#   2) 建单 → 查询 → 故意推非法事件拿 409
#   3) 杀掉进程、重启，再查同一张单 —— 数据还在（这就是"重启数据还在"）
#   4) 用 mysql 客户端数一遍表里的行数
# 跑法： pwsh -File app/server/tests/m2-mysql.ps1
# ============================================================
$ErrorActionPreference = 'Stop'
$serverDir = Split-Path -Parent $PSScriptRoot          # app\server
$jar  = Join-Path $serverDir 'target\dorm-repair-server-1.0.0.jar'
$base = 'http://localhost:8082/api/v1'
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'

if (-not (Test-Path $jar)) { throw "找不到 jar：$jar（先跑 mvn -DskipTests package）" }

$pass = 0; $fail = 0
function Check($name, $cond, $detail) {
    if ($cond) { $script:pass++; "[PASS] $name" }
    else { $script:fail++; "[FAIL] $name  ->  $detail" }
}

function Start-App {
    $env:APP_STORE = 'mysql'
    $env:DB_NAME = 'dorm_repair_test'
    $env:APP_DEMO = 'true'
    $env:DB_USER = 'root'
    $env:DB_PASSWORD = '123456'
    # 鉴权上线后，脚本仍用 X-Actor-* 头冒充身份：这个开关只给测试用，线上默认关
$env:APP_AUTH_ALLOW_HEADER = 'true'
$p = Start-Process java -ArgumentList '-jar', $jar -WorkingDirectory $serverDir -PassThru -WindowStyle Hidden
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Milliseconds 500
        try {
            $r = Invoke-RestMethod "$base/health" -TimeoutSec 2
            if ($r.ok) { return $p }
        } catch { }
    }
    throw '服务 60 秒没起来'
}

function Stop-App($p) {
    if ($p -and -not $p.HasExited) { Stop-Process -Id $p.Id -Force; Start-Sleep -Milliseconds 1200 }
}

function Post($path, $body) {
    Invoke-RestMethod "$base$path" -Method Post -ContentType 'application/json; charset=utf-8' `
        -Headers @{ 'X-Actor-Type' = 'STUDENT'; 'X-Actor-Id' = 'S001'; 'X-Actor-Name' = 'Zhang' } `
        -Body ([System.Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 6)))
}

$studentHeaders = @{ 'X-Actor-Type' = 'STUDENT'; 'X-Actor-Id' = 'S001'; 'X-Actor-Name' = 'Zhang' }

Write-Host '=== 第一次启动（APP_STORE=mysql）===' -ForegroundColor Cyan
$app = Start-App
$health = Invoke-RestMethod "$base/health"
Check 'health 起来' ($health.ok -eq $true) ($health | ConvertTo-Json -Compress)

# 建单：带 autoAssign 之外先不派，验证落库
$submit = @{
    desc = 'M2 验收：卫生间地漏返味，进来看看'
    window = '下午 14:00-18:00'
    photosBefore = @('demo://before/m2-1.jpg')
    category = '给排水'
    level = '普通'
}
$created = Post '/orders' $submit
$no = $created.data.no
Check '建单 201 且拿到单号' ($no -match '^BX\d{11}$') $no
Check '状态是待派工' ($created.data.status -eq 'PENDING') $created.data.status
Check '版本从 1 开始' ($created.data.version -eq 1) $created.data.version

# 查询：落库读得回来
$one = Invoke-RestMethod "$base/orders/$no" -Headers $studentHeaders
Check '回头查得到' ($one.data.no -eq $no) $one.data.no
Check '照片也落库了' ($one.data.photosBefore.Count -ge 1) ($one.data.photosBefore -join ',')
Check '事件有一条 SUBMIT' (($one.data.events | Where-Object { $_.event -eq 'SUBMIT' }).Count -eq 1) 'SUB:missing'

# 非法转移：已派工/待派工推 FINISH 必须 409，而且库里一个字没动
$status = 0; $err = $null
try {
    Invoke-RestMethod "$base/orders/$no/finish" -Method Post -ContentType 'application/json' `
        -Headers @{ 'X-Actor-Type' = 'WORKER'; 'X-Actor-Id' = 'W0428'; 'X-Actor-Name' = 'Li' } `
        -Body ([System.Text.Encoding]::UTF8.GetBytes('{"photoUrls":["demo://after/x.jpg"]}'))
} catch {
    $status = [int]$_.Exception.Response.StatusCode
    $err = $_.ErrorDetails.Message
}
Check '待派工推完工 → 409' ($status -eq 409) "status=$status $err"
$after = Invoke-RestMethod "$base/orders/$no" -Headers $studentHeaders
Check '被拒之后版本没变' ($after.data.version -eq 1) $after.data.version
Check '被拒之后状态没变' ($after.data.status -eq 'PENDING') $after.data.status

Stop-App $app

Write-Host '=== 重启（模拟第二天开机）===' -ForegroundColor Cyan
$app2 = Start-App
$again = Invoke-RestMethod "$base/orders/$no" -Headers $studentHeaders
Check '重启后同一张单还在' ($again.data.no -eq $no) $again.data.no
Check '重启后状态还是待派工' ($again.data.status -eq 'PENDING') $again.data.status

# 走完一条链，证明改状态也真的落库
$h = @{ 'X-Actor-Type' = 'SYSTEM'; 'X-Actor-Id' = 'system'; 'X-Actor-Name' = 'System' }
Invoke-RestMethod "$base/orders/$no/assign" -Method Post -ContentType 'application/json' -Headers $h -Body '{}' | Out-Null
$w = @{ 'X-Actor-Type' = 'WORKER'; 'X-Actor-Id' = 'W0428'; 'X-Actor-Name' = 'Li' }
Invoke-RestMethod "$base/orders/$no/arrive" -Method Post -ContentType 'application/json' -Headers $w -Body '{}' | Out-Null
Invoke-RestMethod "$base/orders/$no/finish" -Method Post -ContentType 'application/json' -Headers $w `
    -Body ([System.Text.Encoding]::UTF8.GetBytes('{"photosAfter":["demo://after/m2.jpg"],"note":"M2 验收完工：疏通地漏，换密封圈"}')) | Out-Null
$done = Invoke-RestMethod "$base/orders/$no" -Headers $studentHeaders
Check '走到待验收' ($done.data.status -eq 'VERIFYING') $done.data.status
Check '版本累加到 4' ($done.data.version -eq 4) $done.data.version
Check '事件 4 条（提交/派工/到门/完工）' ($done.data.events.Count -eq 4) $done.data.events.Count
Check '完工照落库' ($done.data.photosAfter.Count -ge 1) ($done.data.photosAfter -join ',')

Stop-App $app2

Write-Host '=== 直接数一遍库 ===' -ForegroundColor Cyan
$sql = @"
USE dorm_repair_test;
SELECT 'orders' t, COUNT(*) c FROM t_work_order
UNION ALL SELECT 'events', COUNT(*) FROM t_work_order_event
UNION ALL SELECT 'photos', COUNT(*) FROM t_work_order_photo;
SELECT no, status, version FROM t_work_order WHERE no = '$no';
"@
$tmp = Join-Path $env:TEMP 'm2check.sql'
[System.IO.File]::WriteAllText($tmp, $sql, (New-Object System.Text.UTF8Encoding($false)))
& $mysql -uroot -p123456 --default-character-set=utf8mb4 -e "source $($tmp.Replace('\','/'))" 2>&1 |
    Where-Object { $_ -notmatch 'insecure' }

Write-Host ''
Write-Host ("结果：{0} 通过 / {1} 失败" -f $pass, $fail) -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
