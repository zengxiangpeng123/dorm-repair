#!/usr/bin/env bash
set -euo pipefail
: "${DB_HOST:?DB_HOST required}"
: "${BACKUP_DB_USER:=${DB_USER:?DB_USER or BACKUP_DB_USER required}}"
: "${BACKUP_DB_PASSWORD:=${DB_PASSWORD:?DB_PASSWORD or BACKUP_DB_PASSWORD required}}"
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
user=$BACKUP_DB_USER
password=$BACKUP_DB_PASSWORD
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
  if [ "${S3_BACKUP_RETENTION_DAYS:-0}" -gt 0 ]; then
    cutoff="$(date -u -d "-${S3_BACKUP_RETENTION_DAYS} days" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null       || date -u -v-"${S3_BACKUP_RETENTION_DAYS}"d +%Y-%m-%dT%H:%M:%SZ)"
    aws s3api list-objects-v2 --bucket "$S3_BUCKET" --prefix "db/${DB_NAME}-"       --query "Contents[?LastModified<='${cutoff}'].Key" --output text       | tr '\t' '\n'       | while read -r key; do
          [ -z "$key" ] || [ "$key" = "None" ] || aws s3 rm "s3://$S3_BUCKET/$key"
        done
  fi
fi
find "$BACKUP_DIR" -type f -name '*.sql.gz' -mtime +"${BACKUP_RETENTION_DAYS:-14}" -delete
echo "backup complete: $DUMP_FILE"
