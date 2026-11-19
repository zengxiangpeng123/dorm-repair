# Migration entry point

Run migrations against an existing pre-split database after a verified backup and before
starting the new application version.

```bash
export DB_HOST=mysql MIGRATION_DB_USER=dorm_migrator MIGRATION_DB_PASSWORD='...' DB_NAME=dorm_repair
./migrate.sh
```

The runner records each migration filename and SHA-256 checksum in
`t_schema_migration`. Already-applied files are skipped; a changed file is rejected.
Migration files must never hard-code a database name. Fresh installations use
`schema.sql` only and must not run the legacy migration set.

Production migration and backup credentials are separate from the runtime application
account. Configure `MIGRATION_DB_USER` / `MIGRATION_DB_PASSWORD` for DDL changes and
`BACKUP_DB_USER` / `BACKUP_DB_PASSWORD` for dumps; `preflight` rejects reusing the runtime
`DB_USER` for either privileged operation.

## AI gateway probe

`preflight.sh` and `preflight.ps1` call the matching `check-ai-gateway` script before
database checks. The probe requires the fixed model
`raccoon/sn-deepseek-v4-1-flash-1017`, sends one minimal `/chat/completions` request,
confirms the gateway echoes that model, and confirms an invalid key receives HTTP 401/403.
Credentials are read from the environment or production env file and are never printed.
