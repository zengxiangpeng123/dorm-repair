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
mysqldump --host="$DB_HOST" --user="$DB_USER" --password="$DB_PASSWORD"   --single-transaction --routines --triggers --set-gtid-purged=OFF "$DB_NAME"   | gzip > "$BACKUP_DIR/${DB_NAME}-$STAMP.sql.gz"
if [ -n "${S3_BUCKET:-}" ]; then
  aws s3 cp "$BACKUP_DIR/${DB_NAME}-$STAMP.sql.gz" "s3://$S3_BUCKET/db/${DB_NAME}-$STAMP.sql.gz"
fi
find "$BACKUP_DIR" -type f -name '*.sql.gz' -mtime +"${BACKUP_RETENTION_DAYS:-14}" -delete
echo "backup complete: $BACKUP_DIR/${DB_NAME}-$STAMP.sql.gz"
