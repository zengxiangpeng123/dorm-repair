$ErrorActionPreference = 'Stop'
# 重建独立测试库 dorm_repair_test。会清空该测试库，不会碰 dorm_repair 演示库。
$serverDir = Split-Path -Parent $PSScriptRoot
$dbDir = Join-Path $serverDir 'src\main\resources\db'
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$temp = Join-Path $env:TEMP ('dorm-test-db-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temp | Out-Null
$utf8 = New-Object System.Text.UTF8Encoding($false)

$schema = [System.IO.File]::ReadAllText((Join-Path $dbDir 'schema.sql')).Replace('dorm_repair', 'dorm_repair_test')
$data = [System.IO.File]::ReadAllText((Join-Path $dbDir 'data.sql')).Replace('dorm_repair', 'dorm_repair_test')
$schemaPath = Join-Path $temp 'schema.sql'
$dataPath = Join-Path $temp 'data.sql'
[System.IO.File]::WriteAllText($schemaPath, $schema, $utf8)
[System.IO.File]::WriteAllText($dataPath, $data, $utf8)

& $mysql -uroot -p123456 --default-character-set=utf8mb4 -e "DROP DATABASE IF EXISTS dorm_repair_test;"
& $mysql -uroot -p123456 --default-character-set=utf8mb4 -e "source $($schemaPath.Replace('\','/'))"
& $mysql -uroot -p123456 --default-character-set=utf8mb4 -e "source $($dataPath.Replace('\','/'))"

$tables = (& $mysql -uroot -p123456 --default-character-set=utf8mb4 -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='dorm_repair_test' AND table_type='BASE TABLE';")
if ([int]$tables -ne 15) { throw "测试库表数应为 15，实际 $tables" }
Write-Host "测试库已重建：dorm_repair_test，15 张表" -ForegroundColor Green