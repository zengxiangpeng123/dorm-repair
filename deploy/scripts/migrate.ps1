param(
    [string]$DbHost = $env:DB_HOST,
    [string]$DbUser = $env:DB_USER,
    [string]$DbPassword = $env:DB_PASSWORD,
    [string]$DbName = $(if ($env:DB_NAME) { $env:DB_NAME } else { 'dorm_repair' }),
    [string]$MigrationsDir = '../app/server/src/main/resources/db/migrations'
)
$ErrorActionPreference = 'Stop'
if (-not $DbHost -or -not $DbUser -or -not $DbPassword) {
    throw 'DB_HOST, DB_USER and DB_PASSWORD are required'
}
if (-not (Test-Path $MigrationsDir)) {
    throw "migration directory not found: $MigrationsDir"
}
Get-ChildItem $MigrationsDir -Filter '*.sql' | Sort-Object Name | ForEach-Object {
    Write-Host "applying $($_.Name)"
    $env:MYSQL_PWD = $DbPassword
    & mysql --host=$DbHost --user=$DbUser --default-character-set=utf8mb4 $DbName --execute="source $($_.FullName)"
    if ($LASTEXITCODE -ne 0) { throw "migration failed: $($_.Name)" }
}
Write-Host 'migrations complete'
