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

$mysql = 'mysql'
$env:MYSQL_PWD = $DbPassword
try {
    $legacyUserCount = & $mysql --host=$DbHost --user=$DbUser --batch --skip-column-names $DbName --execute="SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_user'"
    if ($LASTEXITCODE -ne 0) { throw 'failed to inspect legacy schema' }
    if ($legacyUserCount.Trim() -eq '0') { throw 'legacy t_user not found; use schema.sql for a fresh installation' }

    $migrationTableSql = @"
CREATE TABLE IF NOT EXISTS t_schema_migration (
  version VARCHAR(100) NOT NULL,
  checksum CHAR(64) NOT NULL,
  applied_at DATETIME NOT NULL,
  PRIMARY KEY (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='applied schema migrations';
"@
    & $mysql --host=$DbHost --user=$DbUser --default-character-set=utf8mb4 $DbName --execute=$migrationTableSql
    if ($LASTEXITCODE -ne 0) { throw 'failed to create migration table' }

    $migrations = Get-ChildItem $MigrationsDir -Filter '*.sql' | Sort-Object Name
    foreach ($migration in $migrations) {
        $version = $migration.Name
        $checksum = (Get-FileHash $migration.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        $recorded = & $mysql --host=$DbHost --user=$DbUser --batch --skip-column-names $DbName --execute="SELECT checksum FROM t_schema_migration WHERE version='$version'"
        if ($LASTEXITCODE -ne 0) { throw "failed to read migration state: $version" }
        if ($recorded) {
            if ($recorded.Trim() -ne $checksum) { throw "migration checksum changed after execution: $version" }
            Write-Host "skip $version (already applied)"
            continue
        }
        Write-Host "applying $version"
        & $mysql --host=$DbHost --user=$DbUser --default-character-set=utf8mb4 $DbName --execute="source $($migration.FullName.Replace([char]92,[char]47))"
        if ($LASTEXITCODE -ne 0) { throw "migration failed: $version" }
        & $mysql --host=$DbHost --user=$DbUser $DbName --execute="INSERT INTO t_schema_migration(version,checksum,applied_at) VALUES('$version','$checksum',NOW())"
        if ($LASTEXITCODE -ne 0) { throw "failed to record migration: $version" }
    }
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}
Write-Host 'migrations complete'
