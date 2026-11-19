# Backup and restore

Backups are gzip-compressed MySQL logical dumps. Object storage must use the provider's
versioned bucket and lifecycle policy; this directory does not pretend that a database
dump also backs up S3 objects.

```bash
export DB_HOST=mysql DB_USER=dorm_app DB_PASSWORD='...' DB_NAME=dorm_repair
./backup.sh
./verify.sh
RESTORE_CONFIRM=dorm_repair ./restore.sh /backup/dorm_repair-YYYYMMDD-HHMMSS.sql.gz
```

Run a restore drill against a separate database before production. Keep at least 14 daily
backups, encrypt the backup bucket, and record the restore time and row counts. The restore
command re-runs `verify.sh`; verification checks core table counts plus orphan event/photo
rows, not just the total number of tables.

When `S3_BUCKET` is set, `S3_BACKUP_RETENTION_DAYS` controls server-side retention of
database dumps under `db/`. Keep bucket versioning and lifecycle rules enabled as the
provider-side safety net; local retention is controlled by `BACKUP_RETENTION_DAYS`.
