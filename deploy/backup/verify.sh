#!/usr/bin/env bash
set -euo pipefail
: "${DB_HOST:?DB_HOST required}"
: "${DB_USER:?DB_USER required}"
: "${DB_PASSWORD:?DB_PASSWORD required}"
: "${DB_NAME:=dorm_repair}"
mysql --host="$DB_HOST" --user="$DB_USER" --password="$DB_PASSWORD" -N -e   "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME'" "$DB_NAME"
