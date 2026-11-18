$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/api/v1'

function Call($method, $path, $body, $actorType, $actorId) {
    $headers = @{}
    if ($actorType) { $headers['X-Actor-Type'] = $actorType }
    if ($actorId)   { $headers['X-Actor-Id']   = $actorId }
    $args = @{
        Uri                = "$base$path"
        Method             = $method
        Headers            = $headers
        SkipHttpErrorCheck = $true
        TimeoutSec         = 20
    }
    if ($null -ne $body) {
        $json = $body | ConvertTo-Json -Depth 8 -Compress
        $args['Body'] = [System.Text.Encoding]::UTF8.GetBytes($json)
        $args['ContentType'] = 'application/json; charset=utf-8'
    }
    $r = Invoke-WebRequest @args
    $obj = $null
    try { $obj = $r.Content | ConvertFrom-Json } catch {}
    [pscustomobject]@{ Status = [int]$r.StatusCode; Body = $obj; Raw = $r.Content }
}

$script:results = @()
function Show($label, $r, $expectStatus, $expectState) {
    $st = $r.Body.data.status
    $code = $r.Body.error.code
    $reason = $r.Body.error.reason
    $ver = $r.Body.data.version
    $line = "{0,-44} HTTP {1,-3} " -f $label, $r.Status
    if ($st)  { $line += "status=$st v=$ver " }
    if ($code) { $line += "code=$code " }
    if ($reason) { $line += "reason=$reason " }
    $pass = ($r.Status -eq $expectStatus) -and ($null -eq $expectState -or $st -eq $expectState)
    if ($pass) { Write-Host ("PASS  " + $line) -ForegroundColor Green }
    else { Write-Host ("FAIL  " + $line) -ForegroundColor Red; Write-Host ("      " + $r.Raw) -ForegroundColor DarkGray }
    $script:results += $pass
}

$r = Call POST '/demo/reset' @{} 'system' 'system'
Show 'demo/reset' $r 200 $null

$r = Call POST '/orders' @{
    building = 'B7'; floor = 4; room = '402'
    desc = '洗手盆软管一直滴水，水桶快接满了'
    window = '上午 08:00-12:00'
    photosBefore = @('demo://before-1.jpg')
} 'student' 'S001'
Show '1 建单 SUBMIT' $r 201 'PENDING'
$no = $r.Body.data.no
$v1 = $r.Body.data.version
Write-Host "      单号 $no  version=$v1  报修人=$($r.Body.data.studentName)" -ForegroundColor DarkGray

$r = Call POST "/orders/$no/assign" @{ version = $v1; workerId = 'W0428'; workerName = '李建国' } 'system' 'system'
Show '2 派工 PENDING->ASSIGNED' $r 200 'ASSIGNED'
$v2 = $r.Body.data.version

$r = Call POST "/orders/$no/arrive" @{ version = $v2 } 'worker' 'W0428'
Show '3 到门 ASSIGNED->REPAIRING' $r 200 'REPAIRING'
$v3 = $r.Body.data.version

$r = Call POST "/orders/$no/finish" @{ version = $v3 } 'worker' 'W0428'
Show '4 完工缺照片 (应 422 PHOTO_AFTER_REQUIRED)' $r 422 $null

$r = Call POST "/orders/$no/finish" @{ version = $v3; photosAfter = @('demo://after-1.jpg'); note = '更换软管' } 'worker' 'W0428'
Show '5 完工 REPAIRING->VERIFYING' $r 200 'VERIFYING'
$v4 = $r.Body.data.version

$r = Call POST "/orders/$no/finish" @{ version = $v4 } 'worker' 'W0428'
Show '6 非法转移 (应 409 STATE_CONFLICT)' $r 409 $null
Write-Host ("      from=" + $r.Body.error.from + " event=" + $r.Body.error.event + " allowed=" + ($r.Body.error.allowed -join '/')) -ForegroundColor DarkGray

$r = Call POST "/orders/$no/accept" @{ version = 99; signerName = '张同学' } 'student' 'S001'
Show '7 旧版本号 (应 409 VERSION_CONFLICT)' $r 409 $null

$r = Call POST "/orders/$no/accept" @{ version = $v4; signerName = '张同学' } 'student' 'S001'
Show '8 验收 VERIFYING->DONE' $r 200 'DONE'

$r = Call POST '/orders' @{ building='B7'; floor=5; room='508'; desc='插座打火，有焦味'; window='全天（08:00-22:00）'; photosBefore=@('demo://before-2.jpg') } 'student' 'S001'
$no2 = $r.Body.data.no; $w1 = $r.Body.data.version
$r = Call POST "/orders/$no2/assign" @{ version = $w1; workerId='W0428'; workerName='李建国' } 'system' 'system'
$w2 = $r.Body.data.version
$r = Call POST "/orders/$no2/arrive" @{ version = $w2 } 'worker' 'W0428'
$w3 = $r.Body.data.version
$r = Call POST "/orders/$no2/cancel" @{ version = $w3; reason='不需要了' } 'student' 'S001'
Show '9 进楼后撤销 (应 409)' $r 409 $null
Write-Host ("      from=" + $r.Body.error.from + " reason=" + $r.Body.error.reason) -ForegroundColor DarkGray

$r = Call GET '/state/matrix' $null $null $null
$m = $r.Body.data
Show ("10 矩阵 rows=$($m.rowCount) events=$($m.eventCount) total=$($m.total) legal=$($m.legal)") $r 200 $null
if ($m.legal -ne 13) { Write-Host '      legal 不等于 13' -ForegroundColor Red; $script:results += $false }

$r = Call POST "/orders/$no2/assign" @{ version = $w3 } 'student' 'S001'
Show '11 学生派工 (应 403)' $r 403 $null

$r = Call GET '/orders/BX99999999999' $null $null $null
Show '12 不存在的单 (应 404)' $r 404 $null

Write-Host ''
$pass = ($script:results | Where-Object { $_ }).Count
$fail = $script:results.Count - $pass
Write-Host ("结果：" + $pass + " 通过 / " + $fail + " 失败") -ForegroundColor Cyan
