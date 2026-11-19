#!/usr/bin/env bash
set -euo pipefail
: "${DB_HOST:?DB_HOST required}"
: "${DB_USER:?DB_USER required}"
: "${DB_PASSWORD:?DB_PASSWORD required}"
: "${DB_NAME:=dorm_repair}"
: "${1:?usage: restore.sh <backup.sql.gz>}"
test -f "$1"
if [ "${RESTORE_CONFIRM:-}" != "$DB_NAME" ]; then
  echo "refusing restore: set RESTORE_CONFIRM=$DB_NAME to confirm target database" >&2
  exit 1
fi
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
gzip -t "$1"
TMP_SQL="$(mktemp)"
trap 'rm -f "$CNF" "$TMP_SQL"' EXIT INT TERM
gunzip -c "$1" > "$TMP_SQL"
if [ ! -s "$TMP_SQL" ]; then
  echo "refusing restore: backup expands to an empty file" >&2
  exit 1
fi
mysql --defaults-extra-file="$CNF" "$DB_NAME" < "$TMP_SQL"
table_count="$(mysql --defaults-extra-file="$CNF" --batch --skip-column-names -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_TYPE='BASE TABLE'")"
if [ "${table_count:-0}" -lt 20 ]; then
  echo "restore failed: expected at least 20 base tables after import, got ${table_count:-0}" >&2
  exit 1
fi
script_dir="$(cd "$(dirname "$0")" && pwd)"
"$script_dir/verify.sh"
echo "restore complete: $1 (base tables=$table_count)"
