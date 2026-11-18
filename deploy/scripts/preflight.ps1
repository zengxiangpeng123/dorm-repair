param(
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\.env.production')
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envPath = (Resolve-Path $EnvFile -ErrorAction Stop).Path
$certDir = Join-Path $root 'deploy\certs'
$alertFile = Join-Path $root 'deploy\prometheus\alertmanager.yml'
function Fail([string]$message) { throw "preflight: $message" }
if (!(Test-Path (Join-Path $certDir 'tls.crt'))) { Fail 'missing deploy/certs/tls.crt' }
if (!(Test-Path (Join-Path $certDir 'tls.key'))) { Fail 'missing deploy/certs/tls.key' }
if (!(Test-Path $alertFile)) { Fail 'missing alertmanager.yml; copy the example and configure a real receiver' }
if ((Get-Content $alertFile -Raw) -match 'alert\.example\.com') { Fail 'replace the example Alertmanager receiver' }
$values = @{}
Get-Content $envPath | ForEach-Object {
    if ($_ -match '^([A-Z0-9_]+)=(.*)$') { $values[$matches[1]] = $matches[2] }
}
$required = @('DB_ROOT_PASSWORD','DB_USER','DB_PASSWORD','DB_NAME','APP_FILE_S3_ENDPOINT','APP_FILE_S3_BUCKET','AI_BASE_URL','AI_MODEL','AI_API_KEY')
foreach ($key in $required) {
    $value = $values[$key]
    if ([string]::IsNullOrWhiteSpace($value)) { Fail "$key is empty" }
    if ($value -match '^(replace-with-|sk-\.\.\.)') { Fail "$key still contains a placeholder" }
}
if ($values['APP_FILE_S3_ENDPOINT'] -notmatch '^https://') { Fail 'APP_FILE_S3_ENDPOINT must use HTTPS' }
if ($values['DB_USE_SSL'] -ne 'true') { Fail 'DB_USE_SSL must be true in production' }
Write-Host 'production preflight: PASS'
