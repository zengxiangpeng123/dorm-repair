# Migration entry point

Run migrations against an existing pre-split database after a verified backup and before
starting the new application version.

```bash
export DB_HOST=mysql DB_USER=dorm_app DB_PASSWORD='...' DB_NAME=dorm_repair
./migrate.sh
```

The runner records each migration filename and SHA-256 checksum in
`t_schema_migration`. Already-applied files are skipped; a changed file is rejected.
Migration files must never hard-code a database name. Fresh installations use
`schema.sql` only and must not run the legacy migration set.

Production backup credentials are separate from the runtime application account. Configure
`BACKUP_DB_USER` / `BACKUP_DB_PASSWORD` with only the privileges needed by `mysqldump` and
restore verification; `preflight` rejects using the runtime `DB_USER` as the backup account.
