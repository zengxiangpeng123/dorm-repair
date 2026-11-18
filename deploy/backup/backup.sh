#!/usr/bin/env bash
set -euo pipefail
: "${DB_HOST:?DB_HOST required}"
: "${DB_USER:?DB_USER required}"
: "${DB_PASSWORD:?DB_PASSWORD required}"
: "${DB_NAME:=dorm_repair}"
BACKUP_DIR="${BACKUP_DIR:-/backup}"
STAMP="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP_DIR"
umask 077
CNF="$(mktemp)"
cleanup() { rm -f "$CNF"; }
trap cleanup EXIT INT TERM
chmod 600 "$CNF"
cat > "$CNF" <<EOF
[client]
host=$DB_HOST
user=$DB_USER
password=$DB_PASSWORD
EOF
DUMP_FILE="$BACKUP_DIR/${DB_NAME}-$STAMP.sql.gz"
if ! mysqldump --defaults-extra-file="$CNF" --single-transaction --routines --triggers --set-gtid-purged=OFF "$DB_NAME" | gzip > "$DUMP_FILE"; then
  rm -f "$DUMP_FILE"
  echo "backup failed: mysqldump returned an error" >&2
  exit 1
fi
if [ ! -s "$DUMP_FILE" ] || [ "$(stat -c %s "$DUMP_FILE")" -lt 100 ]; then
  rm -f "$DUMP_FILE"
  echo "backup failed: dump is empty or truncated" >&2
  exit 1
fi
if [ -n "${S3_BUCKET:-}" ]; then
  aws s3 cp "$DUMP_FILE" "s3://$S3_BUCKET/db/${DB_NAME}-$STAMP.sql.gz"
fi
find "$BACKUP_DIR" -type f -name '*.sql.gz' -mtime +"${BACKUP_RETENTION_DAYS:-14}" -delete
echo "backup complete: $DUMP_FILE"
