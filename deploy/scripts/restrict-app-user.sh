#!/usr/bin/env bash
set -euo pipefail

: "${DB_ROOT_PASSWORD:?DB_ROOT_PASSWORD required}"
: "${DB_USER:?DB_USER required}"
: "${DB_NAME:?DB_NAME required}"

MYSQL_CNF="$(mktemp)"
trap 'rm -f "$MYSQL_CNF"' EXIT
chmod 600 "$MYSQL_CNF"
printf '[client]\nhost=%s\nuser=root\npassword=%s\n' "${DB_HOST:-127.0.0.1}" "$DB_ROOT_PASSWORD" > "$MYSQL_CNF"

mysql --defaults-extra-file="$MYSQL_CNF" --protocol=TCP <<SQL
REVOKE ALL PRIVILEGES, GRANT OPTION FROM '${DB_USER}'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, EXECUTE ON \`${DB_NAME}\`.* TO '${DB_USER}'@'%';
FLUSH PRIVILEGES;
SQL

echo "runtime database privileges restricted for ${DB_USER} on ${DB_NAME}"
