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
$required = @('DB_ROOT_PASSWORD','DB_USER','DB_PASSWORD','DB_NAME','APP_FILE_S3_ENDPOINT','APP_FILE_S3_BUCKET','AI_BASE_URL','AI_MODEL','AI_API_KEY','BOOTSTRAP_ADMIN_ACCOUNT','BOOTSTRAP_ADMIN_NAME','BOOTSTRAP_ADMIN_PASSWORD')
foreach ($key in $required) {
    $value = $values[$key]
    if ([string]::IsNullOrWhiteSpace($value)) { Fail "$key is empty" }
    if ($value -match '^(replace-with-|sk-\.\.\.)') { Fail "$key still contains a placeholder" }
}
if ($values['APP_FILE_S3_ENDPOINT'] -notmatch '^https://') { Fail 'APP_FILE_S3_ENDPOINT must use HTTPS' }
if ($values['DB_USE_SSL'] -ne 'true') { Fail 'DB_USE_SSL must be true in production' }
$mysql = Get-Command mysql -ErrorAction SilentlyContinue
if (-not $mysql) { Fail 'mysql client is required to verify the production schema' }
$dbHost = if ($values['DB_HOST']) { $values['DB_HOST'] } else { '127.0.0.1' }
$schemaSql = @"
SELECT CONCAT('MISSING_TABLE:', TABLE_NAME) FROM (
  SELECT 't_student' TABLE_NAME UNION ALL SELECT 't_worker' UNION ALL SELECT 't_admin'
  UNION ALL SELECT 't_worker_building' UNION ALL SELECT 't_auth_session'
  UNION ALL SELECT 't_login_attempt' UNION ALL SELECT 't_building'
  UNION ALL SELECT 't_building_floor' UNION ALL SELECT 't_work_order'
  UNION ALL SELECT 't_work_order_event' UNION ALL SELECT 't_work_order_photo'
  UNION ALL SELECT 't_file' UNION ALL SELECT 't_ai_review_task'
  UNION ALL SELECT 't_part' UNION ALL SELECT 't_part_request'
  UNION ALL SELECT 't_rework_review' UNION ALL SELECT 't_exception_record'
  UNION ALL SELECT 't_config' UNION ALL SELECT 't_idempotency_record'
  UNION ALL SELECT 't_seq'
) required
WHERE NOT EXISTS (
  SELECT 1 FROM information_schema.TABLES actual
  WHERE actual.TABLE_SCHEMA=DATABASE() AND actual.TABLE_NAME=required.TABLE_NAME
    AND actual.TABLE_TYPE='BASE TABLE'
);
SELECT 'MISSING_VIEW:v_user_account'
WHERE NOT EXISTS (
  SELECT 1 FROM information_schema.VIEWS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='v_user_account'
);
SELECT CONCAT('MISSING_COLUMN:', c.TABLE_NAME, '.', c.COLUMN_NAME) FROM (
  SELECT 't_work_order' TABLE_NAME, 'verify_due_at' COLUMN_NAME UNION ALL
  SELECT 't_work_order', 'archive_due_at' UNION ALL
  SELECT 't_file', 'cleanup_claimed_at' UNION ALL
  SELECT 't_ai_review_task', 'latency_ms'
) c
WHERE NOT EXISTS (
  SELECT 1 FROM information_schema.COLUMNS actual
  WHERE actual.TABLE_SCHEMA=DATABASE() AND actual.TABLE_NAME=c.TABLE_NAME
    AND actual.COLUMN_NAME=c.COLUMN_NAME
);
"@
$env:MYSQL_PWD = $values['DB_PASSWORD']
try {
    $schemaErrors = & $mysql.Source --host=$dbHost --user=$values['DB_USER'] --batch --skip-column-names $values['DB_NAME'] --execute=$schemaSql 2>$null
    if ($LASTEXITCODE -ne 0) { Fail 'could not inspect the production database schema' }
    if ($schemaErrors) { Fail ('database schema incomplete: ' + ($schemaErrors -join ', ') + '; run deploy/scripts/migrate.ps1') }
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}
Write-Host 'production preflight: PASS'
