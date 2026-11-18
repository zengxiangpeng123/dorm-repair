#!/usr/bin/env bash
set -euo pipefail

: "${DB_HOST:?DB_HOST required}"
: "${DB_USER:?DB_USER required}"
: "${DB_PASSWORD:?DB_PASSWORD required}"
: "${DB_NAME:=dorm_repair}"

MYSQL=(mysql --host="$DB_HOST" --user="$DB_USER" --password="$DB_PASSWORD" --default-character-set=utf8mb4 "$DB_NAME")
MIGRATIONS_DIR="${MIGRATIONS_DIR:-../app/server/src/main/resources/db/migrations}"

if [ ! -d "$MIGRATIONS_DIR" ]; then
  echo "migration directory not found: $MIGRATIONS_DIR" >&2
  exit 1
fi

echo "waiting for database $DB_HOST/$DB_NAME"
for attempt in $(seq 1 60); do
  if mysqladmin --host="$DB_HOST" --user="$DB_USER" --password="$DB_PASSWORD" ping --silent >/dev/null 2>&1; then
    break
  fi
  if [ "$attempt" -eq 60 ]; then
    echo "database did not become ready" >&2
    exit 1
  fi
  sleep 2
done

# 迁移脚本必须可重复执行；按文件名顺序执行。
for migration in "$MIGRATIONS_DIR"/*.sql; do
  [ -f "$migration" ] || continue
  echo "applying $(basename "$migration")"
  "$MYSQL" < "$migration"
done

echo "migrations complete"
