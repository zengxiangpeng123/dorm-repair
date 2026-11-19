#!/usr/bin/env bash
set -euo pipefail

: "${DB_HOST:?DB_HOST required}"
: "${MIGRATION_DB_USER:=${DB_USER:?DB_USER or MIGRATION_DB_USER required}}"
: "${MIGRATION_DB_PASSWORD:=${DB_PASSWORD:?DB_PASSWORD or MIGRATION_DB_PASSWORD required}}"
: "${DB_NAME:=dorm_repair}"

MYSQL_CNF="$(mktemp)"
trap 'rm -f "$MYSQL_CNF"' EXIT
chmod 600 "$MYSQL_CNF"
printf '[client]\nhost=%s\nuser=%s\npassword=%s\n' "$DB_HOST" "$MIGRATION_DB_USER" "$MIGRATION_DB_PASSWORD" > "$MYSQL_CNF"

MYSQL=(mysql --defaults-extra-file="$MYSQL_CNF" --default-character-set=utf8mb4 "$DB_NAME")
MIGRATIONS_DIR="${MIGRATIONS_DIR:-../app/server/src/main/resources/db/migrations}"

if [ ! -d "$MIGRATIONS_DIR" ]; then
  echo "migration directory not found: $MIGRATIONS_DIR" >&2
  exit 1
fi

echo "waiting for database $DB_HOST/$DB_NAME"
for attempt in $(seq 1 60); do
  if mysqladmin --defaults-extra-file="$MYSQL_CNF" ping --silent >/dev/null 2>&1; then
    break
  fi
  if [ "$attempt" -eq 60 ]; then
    echo "database did not become ready" >&2
    exit 1
  fi
  sleep 2
done

# Legacy upgrades must start from the pre-split t_user schema.
# Refuse to silently skip a migration because the target is a fresh split schema.
legacy_user_count="$("$MYSQL" --batch --skip-column-names -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_user'")"
if [ "$legacy_user_count" = "0" ]; then
  echo "legacy t_user not found; use schema.sql for a fresh installation" >&2
  exit 1
fi

"$MYSQL" <<'SQL'
CREATE TABLE IF NOT EXISTS t_schema_migration (
  version     VARCHAR(100) NOT NULL,
  checksum    CHAR(64)     NOT NULL,
  applied_at  DATETIME     NOT NULL,
  PRIMARY KEY (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='已执行的数据库迁移';
SQL

for migration in "$MIGRATIONS_DIR"/*.sql; do
  [ -f "$migration" ] || continue
  version="$(basename "$migration")"
  checksum="$(sha256sum "$migration" | awk '{print $1}')"
  recorded="$("$MYSQL" --batch --skip-column-names -e     "SELECT checksum FROM t_schema_migration WHERE version='$version'")"
  if [ -n "$recorded" ]; then
    if [ "$recorded" != "$checksum" ]; then
      echo "migration checksum changed after execution: $version" >&2
      exit 1
    fi
    echo "skip $version (already applied)"
    continue
  fi
  echo "applying $version"
  "$MYSQL" < "$migration"
  "$MYSQL" -e     "INSERT INTO t_schema_migration(version,checksum,applied_at) VALUES('$version','$checksum',NOW())"
done

echo "migrations complete"
