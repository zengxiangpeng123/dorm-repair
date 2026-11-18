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
gunzip -c "$1" | mysql --defaults-extra-file="$CNF" "$DB_NAME"
echo "restore complete: $1"
