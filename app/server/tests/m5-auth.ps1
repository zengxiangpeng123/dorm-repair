# m5-auth.ps1 —— 真账号登录的端到端验收（不需要 APP_AUTH_ALLOW_HEADER）
#
# 前提：服务已起，且**没有**开 APP_AUTH_ALLOW_HEADER（默认就是关的）。
#   1) 先停掉旧服务：占 8082 的 java 进程
#   2) $env:APP_STORE='mysql'; $env:DB_NAME='dorm_repair_test'; java -jar target\dorm-repair-server-1.0.0.jar
#   3) pwsh -File app\server\tests\m5-auth.ps1
#
# 验的是"不登录进不来"这件事本身：没有任何 X-Actor-* 头，全靠 Authorization: Bearer

$ErrorActionPreference = 'Stop'
$env:APP_DEMO = 'true'
$base = 'http://127.0.0.1:8082/api/v1'
$pass = 0; $fail = 0

function Check($name, $cond, $detail) {
    if ($cond) { $script:pass++; Write-Host ("  ok   " + $name) -ForegroundColor Green }
    else { $script:fail++; Write-Host ("  FAIL " + $name + "  " + $detail) -ForegroundColor Red }
}

function Call($method, $path, $body, $token, $extraHeaders) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    if ($extraHeaders) { foreach ($k in $extraHeaders.Keys) { $headers[$k] = $extraHeaders[$k] } }
    $params = @{ Uri = "$base$path"; Method = $method; Headers = $headers; SkipHttpErrorCheck = $true }
    if ($body -ne $null) {
        $params['Body'] = $body
        $params['ContentType'] = 'application/json; charset=utf-8'
    }
    $r = Invoke-WebRequest @params
    $json = $null
    try { $json = $r.Content | ConvertFrom-Json } catch { }
    return @{ code = $r.StatusCode; raw = $r.Content; json = $json }
}

function Login($account, $password) {
    $r = Call 'POST' '/auth/login' ("{""account"":""" + $account + """,""password"":""" + $password + """}") $null $null
    return $r
}

Write-Host "== 0. 服务在不在 ==" -ForegroundColor Cyan
$h = Call 'GET' '/health' $null $null $null
Check 'health 200' ($h.code -eq 200) $h.raw
Write-Host ("  当前工单 " + $h.json.data.orders + " 张")

Write-Host "== 1. 不登录，进不来 ==" -ForegroundColor Cyan
$r = Call 'GET' '/my/orders' $null $null $null
Check '无令牌 → 401' ($r.code -eq 401) $r.raw
Check '返回码是 UNAUTHORIZED' ($r.json.error.code -eq 'UNAUTHORIZED') $r.raw

$r = Call 'GET' '/my/orders' $null 'this-is-not-a-token' $null
Check '瞎编令牌 → 401' ($r.code -eq 401) $r.raw

$r = Call 'GET' '/admin/dashboard' $null $null @{'X-Actor-Type' = 'admin'; 'X-Actor-Id' = 'A001' }
Check '冒名请求头也进不来 → 401' ($r.code -eq 401) $r.raw

Write-Host "== 2. 口令换令牌 ==" -ForegroundColor Cyan
$r = Login 'S001' 'dorm@2026'
Check '正确口令 → 200' ($r.code -eq 200) $r.raw
Check '拿到 token' ($r.json.data.token.Length -ge 40) $r.raw
Check '身份是学生' ($r.json.data.user.role -eq 'STUDENT') $r.raw
Check '响应里没有口令字段' ($null -eq $r.json.data.user.password) $r.raw
$stu = $r.json.data.token

$r1 = Login 'S001' 'wrong-password'
$r2 = Login 'S999' 'dorm@2026'
Check '口令错 → 401' ($r1.code -eq 401) $r1.raw
Check '账号不存在 → 401' ($r2.code -eq 401) $r2.raw
Check '两种失败的措辞一样（不泄露哪个对）' ($r1.json.error.message -eq $r2.json.error.message) ($r1.raw + ' / ' + $r2.raw)

Write-Host "== 3. 令牌换身份：我是谁 ==" -ForegroundColor Cyan
$r = Call 'GET' '/auth/me' $null $stu $null
Check '/auth/me 200' ($r.code -eq 200) $r.raw
Check '账号对得上' ($r.json.data.user.id -eq 'S001') $r.raw

$r = Call 'GET' '/my/orders?status=ALL' $null $stu $null
Check '带令牌查我的单 → 200' ($r.code -eq 200) $r.raw
Check '拿到 items' ($r.json.data.items.Count -ge 1) $r.raw

Write-Host "== 4. 认得你，但你没这个权限：403 不是 401 ==" -ForegroundColor Cyan
$r = Call 'GET' '/admin/dashboard' $null $stu $null
Check '学生看管理端看板 → 403' ($r.code -eq 403) $r.raw
Check '错误码是 FORBIDDEN' ($r.json.error.code -eq 'FORBIDDEN') $r.raw

$adm = (Login 'A001' 'dorm@2026').json.data.token
$r = Call 'GET' '/admin/dashboard' $null $adm $null
Check '管理端看板 → 200' ($r.code -eq 200) $r.raw
Check '看板有零人工完成率' ($null -ne $r.json.data.zeroManual) $r.raw

$wk = (Login 'W0428' 'dorm@2026').json.data.token
$r = Call 'GET' '/worker/todo' $null $wk $null
Check '师傅看工作台 → 200' ($r.code -eq 200) $r.raw
$r = Call 'GET' '/worker/todo' $null $stu $null
Check '学生看师傅工作台 → 403' ($r.code -eq 403) $r.raw

Write-Host "== 5. 动数据也必须登录 ==" -ForegroundColor Cyan
$r = Call 'POST' '/orders' '{"photosBefore":["demo://m5.jpg"],"desc":"m5 脚本验真登录，卫生间地漏反水"}' $stu $null
Check '学生带令牌建单 → 201' ($r.code -eq 201) $r.raw
$no = $r.json.data.no
Check '拿到单号' ($no -like 'BX*') $r.raw

$r = Call 'POST' "/orders/$no/assign" '{"version":1,"workerId":"W0428"}' $stu $null
Check '学生自己派工 → 403' ($r.code -eq 403) $r.raw

$r = Call 'POST' "/orders/$no/assign" '{"version":1,"workerId":"W0428"}' $adm $null
Check '管理端派工 → 200 且已派工' ($r.code -eq 200 -and $r.json.data.status -eq 'ASSIGNED') $r.raw

Write-Host "== 6. system 令牌：能推，但没到点照样 422 ==" -ForegroundColor Cyan
$r = Call 'POST' '/auth/system-token' '{}' $stu $null
Check '学生不能申请 system 令牌 → 403' ($r.code -eq 403) $r.raw
$r = Call 'POST' '/auth/system-token' '{}' $adm $null
Check '管理端能领 system 令牌（演示接口）' ($r.code -eq 200) $r.raw
$sys = $r.json.data.token
Check 'system 令牌身份是 SYSTEM' ($r.json.data.actor.type -eq 'SYSTEM') $r.raw

$r = Call 'GET' '/orders' $null $adm $null
$verify = $r.json.data.items | Where-Object { $_.status -eq 'VERIFYING' } | Select-Object -First 1
if ($verify) {
    $r = Call 'POST' ("/orders/" + $verify.no + '/auto-accept') '{}' $sys $null
    Check 'system 推 AUTO_ACCEPT（T1 未到）→ 422' ($r.code -eq 422) $r.raw
    Check '原因是 T1_NOT_DUE' ($r.json.error.reason -eq 'T1_NOT_DUE') $r.raw
    $r = Call 'POST' ("/orders/" + $verify.no + '/auto-accept') '{}' $adm $null
    Check '管理端推 AUTO_ACCEPT → 403（这条边只给 system）' ($r.code -eq 403) $r.raw
}
else {
    Write-Host '  跳过：库里没有待验收的单' -ForegroundColor Yellow
}

Write-Host "== 7. 登出立刻失效 ==" -ForegroundColor Cyan
$r = Call 'POST' '/auth/logout' '{}' $stu $null
Check '登出 → 200' ($r.code -eq 200) $r.raw
$r = Call 'GET' '/my/orders' $null $stu $null
Check '登出后同一个令牌 → 401' ($r.code -eq 401) $r.raw
Check '说的是"登录已失效"' ($r.json.error.message -like '*失效*') $r.raw

Write-Host "== 8. 收尾：复位演示数据 ==" -ForegroundColor Cyan
$r = Call 'POST' '/demo/reset' '{}' $adm $null
Check '管理端 reset → 200' ($r.code -eq 200) $r.raw
Write-Host ("  复位后 " + $r.json.data.orders + " 张单")

Write-Host ''
Write-Host ("结果：" + $pass + " 通过 / " + $fail + " 失败") -ForegroundColor Cyan
if ($fail -gt 0) { exit 1 }
