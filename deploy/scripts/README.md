# Migration entry point

Run migrations against an existing database after a verified backup and before starting
the new application version.

```bash
export DB_HOST=mysql DB_USER=dorm_app DB_PASSWORD='...' DB_NAME=dorm_repair
./migrate.sh
```

All files in `app/server/src/main/resources/db/migrations` are applied in filename order
and must be safe to rerun. A fresh Compose database gets the bootstrap schema from
`deploy/mysql-init`; existing databases use this migration entry point.
