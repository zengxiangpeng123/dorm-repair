# 返修单链路验收：驳回生成 -R1 → 返修单验收通过 → 原单级联关闭
# 用法：pwsh -File app/server/tests/m4-rework.ps1
$ErrorActionPreference = 'Stop'
$serverDir = Split-Path -Parent $PSScriptRoot                          # app/server
$root = Split-Path -Parent (Split-Path -Parent $serverDir)            # 项目根
$base = 'http://127.0.0.1:8082/api/v1'
$pass = 0; $fail = 0

function T($name, $cond, $got) {
  if ($cond) { $script:pass++; Write-Host ("  OK   " + $name) }
  else { $script:fail++; Write-Host ("  FAIL " + $name + " -> " + $got) }
}

function Api($method, $path, $body, $actor) {
  $h = @{ 'Content-Type' = 'application/json' }
  if ($actor) { $h['X-Actor-Type'] = $actor[0]; $h['X-Actor-Id'] = $actor[1] }
  $args = @{ Method = $method; Uri = ($base + $path); Headers = $h; TimeoutSec = 20 }
  if ($body -ne $null) { $args['Body'] = ($body | ConvertTo-Json -Depth 6 -Compress) }
  try {
    $r = Invoke-WebRequest @args -SkipHttpErrorCheck
    return @{ status = [int]$r.StatusCode; json = ($r.Content | ConvertFrom-Json) }
  } catch {
    return @{ status = 0; json = $null; err = $_.Exception.Message }
  }
}

Write-Host "== 0. 起服务（APP_STORE=mysql） =="
$env:APP_STORE = 'mysql'
$env:DB_NAME = 'dorm_repair_test'
Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*dorm-repair-server*' } |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force; Write-Host ("  killed pid " + $_.ProcessId) }
$jar = Join-Path $serverDir 'target\dorm-repair-server-1.0.0.jar'
# 鉴权上线后，脚本仍用 X-Actor-* 头冒充身份：这个开关只给测试用，线上默认关
$env:APP_AUTH_ALLOW_HEADER = 'true'
Start-Process java -ArgumentList '-jar', $jar -WorkingDirectory $serverDir -WindowStyle Hidden
$up = $false
for ($i = 0; $i -lt 40; $i++) {
  Start-Sleep -Milliseconds 700
  $h = Api 'GET' '/health' $null $null
  if ($h.status -eq 200) { $up = $true; break }
}
if (-not $up) { Write-Host "服务没起来，退出"; exit 1 }
T '服务起来了' $up $h.status
$reset = Api 'POST' '/demo/reset' @{} @('admin', 'A001')
T '重置样本 11 单' ($reset.json.data.orders -eq 11) $reset.json.data.orders

Write-Host "== 1. 建单 → 派工 → 到门 → 完工（推到待验收） =="
$s = Api 'POST' '/orders' @{ desc = '洗手盆软管一直滴水，水桶快接满了'; window = '全天'; photosBefore = @('demo://before/1.jpg') } @('student', 'S001')
T '建单 201' ($s.status -eq 201) $s.status
$no = $s.json.data.no
$rl = $s.json.data.level
Write-Host ("  单号 " + $no + "  定级 " + $s.json.data.category + "/" + $rl + " 来源 " + $s.json.data.levelSource)
Api 'POST' "/orders/$no/assign" @{} @('system', 'system') | Out-Null
Api 'POST' "/orders/$no/arrive" @{} @('worker', 'W0428') | Out-Null
$f = Api 'POST' "/orders/$no/finish" @{ version = 3; photosAfter = @('demo://after/1.jpg'); noteTags = @('更换软管') } @('worker', 'W0428')
T '完工 → 待验收' ($f.json.data.status -eq 'VERIFYING') $f.json.data.status

Write-Host "== 2. 学生驳回 → 生成返修单 -R1 =="
$rj = Api 'POST' "/orders/$no/reject" @{ version = 4; reason = '水还在滴，没修好' } @('student', 'S001')
$rw = $rj.json.data
T '驳回 200' ($rj.status -eq 200) $rj.status
T '-R1 单号' ($rw.no -eq ($no + '-R1')) $rw.no
T '返修单回待派工' ($rw.status -eq 'PENDING') $rw.status
T 'reworkOf 指回原单' ($rw.reworkOf -eq $no) $rw.reworkOf
T 'reworkIndex=1' ($rw.reworkIndex -eq 1) $rw.reworkIndex
T '类型继承' ($rw.category -eq $s.json.data.category) $rw.category
T '紧急度不降' ($rw.level -eq $rl) ($rl + ' -> ' + $rw.level)
$o1 = Api 'GET' "/orders/$no" $null @('student', 'S001')
T '原单挂返修中' ($o1.json.data.status -eq 'REWORKING') $o1.json.data.status
T '原单 reworkCount=1' ($o1.json.data.reworkCount -eq 1) $o1.json.data.reworkCount

Write-Host "== 3. 返修单走完 → 验收通过 → 原单级联关闭 =="
$n2 = $rw.no
Api 'POST' "/orders/$n2/assign" @{} @('system', 'system') | Out-Null
Api 'POST' "/orders/$n2/arrive" @{} @('worker', 'W0428') | Out-Null
Api 'POST' "/orders/$n2/finish" @{ version = 3; photosAfter = @('demo://after/2.jpg'); noteTags = @('更换软管') } @('worker', 'W0428') | Out-Null
$ac = Api 'POST' "/orders/$n2/accept" @{ version = 4; signerName = '张同学' } @('student', 'S001')
T '返修单已完成' ($ac.json.data.status -eq 'DONE') $ac.json.data.status
$o2 = Api 'GET' "/orders/$no" $null @('student', 'S001')
T '原单已关闭' ($o2.json.data.status -eq 'CLOSED') $o2.json.data.status
T '原单 closedAt 有值' ([bool]$o2.json.data.closedAt) $o2.json.data.closedAt
$ev = Api 'GET' "/orders/$no/events" $null @('student', 'S001')
$last = $ev.json.data.items[-1]
T '原单末事件是 ORIGIN_CLOSED' ($last.event -eq 'ORIGIN_CLOSED') $last.event
T '级联事件 manual=1（算人的动作）' ($last.manual -eq 1) $last.manual

Write-Host "== 4. 新审查链路：每次返修先管理员审查，R2 后关闭整条链 =="
$s2 = Api 'POST' '/orders' @{ building='B7'; floor=4; room='402'; desc='洗漱台下水软管还在滴水，需要继续维修'; window='全天'; photosBefore=@('demo://before/m4-review.jpg') } @('student', 'S001')
$no2 = $s2.json.data.no
Api 'POST' "/orders/$no2/assign" @{} @('system', 'system') | Out-Null
Api 'POST' "/orders/$no2/arrive" @{} @('worker', 'W0428') | Out-Null
$f2 = Api 'POST' "/orders/$no2/finish" @{ version = 3; photosAfter = @('demo://after/m4-review-1.jpg') } @('worker', 'W0428')
$req2 = Api 'POST' "/orders/$no2/rework-request" @{ version = $f2.json.data.version; reason = '水还在滴，维修没有解决问题' } @('student', 'S001')
T '学生申请返修 → 仍待验收' ($req2.status -eq 202 -and $req2.json.data.order.status -eq 'VERIFYING') $req2.status
$q1 = Api 'GET' '/admin/rework-reviews' $null @('admin', 'A001')
T '管理端队列出现申请' ($q1.json.data.total -eq 1) $q1.json.data.total
$ap1 = Api 'POST' "/orders/$no2/rework-review" @{ version = $q1.json.data.items[0].version; decision = 'APPROVE'; reason = '完工照显示接口未紧固，属维修质量问题' } @('admin', 'A001')
$r1 = $ap1.json.data.reworkOrder
T '批准后生成 -R1' ($r1.no -eq ($no2 + '-R1')) $r1.no

Api 'POST' "/orders/$($r1.no)/assign" @{} @('system', 'system') | Out-Null
Api 'POST' "/orders/$($r1.no)/arrive" @{} @('worker', 'W0428') | Out-Null
$f3 = Api 'POST' "/orders/$($r1.no)/finish" @{ version = 3; photosAfter = @('demo://after/m4-review-2.jpg') } @('worker', 'W0428')
$req3 = Api 'POST' "/orders/$($r1.no)/rework-request" @{ version = $f3.json.data.version; reason = '第二次维修后仍然渗水' } @('student', 'S001')
T '第 2 次返修仍进审查' ($req3.status -eq 202 -and $req3.json.data.review.status -eq 'PENDING') $req3.status
$q2 = Api 'GET' '/admin/rework-reviews' $null @('admin', 'A001')
$ap2 = Api 'POST' "/orders/$($r1.no)/rework-review" @{ version = $q2.json.data.items[0].version; decision = 'APPROVE'; reason = '复核仍属维修质量问题，转反复返修' } @('admin', 'A001')
$r2 = $ap2.json.data.reworkOrder
T '第 2 次批准生成 -R2' ($r2.no -eq ($no2 + '-R2') -and $r2.reworkIndex -eq 2) $r2.no
$ex = Api 'GET' '/admin/exceptions' $null @('admin', 'A001')
$repeat = @($ex.json.data.items | Where-Object { $_.kind -eq 'REPEAT_REWORK' -and $_.no -eq $no2 })
T '反复返修进入例外清单' ($repeat.Count -eq 1) $ex.json.data.total

Api 'POST' "/orders/$($r2.no)/assign" @{} @('system', 'system') | Out-Null
Api 'POST' "/orders/$($r2.no)/arrive" @{} @('worker', 'W0428') | Out-Null
$f4 = Api 'POST' "/orders/$($r2.no)/finish" @{ version = 3; photosAfter = @('demo://after/m4-review-3.jpg') } @('worker', 'W0428')
Api 'POST' "/orders/$($r2.no)/accept" @{ version = $f4.json.data.version; signerName = '张同学' } @('student', 'S001') | Out-Null
$origin2 = Api 'GET' "/orders/$no2" $null @('student', 'S001')
$parent2 = Api 'GET' "/orders/$($r1.no)" $null @('student', 'S001')
T 'R2 验收后原单关闭' ($origin2.json.data.status -eq 'CLOSED') $origin2.json.data.status
T 'R2 验收后上一张返修单也关闭' ($parent2.json.data.status -eq 'CLOSED') $parent2.json.data.status

Write-Host ""
Write-Host ("结果：" + $pass + " 通过 / " + $fail + " 失败")
if ($fail -gt 0) { exit 1 }
