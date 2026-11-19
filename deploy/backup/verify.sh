#!/usr/bin/env bash
set -euo pipefail
: "${DB_HOST:?DB_HOST required}"
: "${BACKUP_DB_USER:=${DB_USER:?DB_USER or BACKUP_DB_USER required}}"
: "${BACKUP_DB_PASSWORD:=${DB_PASSWORD:?DB_PASSWORD or BACKUP_DB_PASSWORD required}}"
: "${DB_NAME:=dorm_repair}"
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
tables="$(mysql --defaults-extra-file="$CNF" -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME' AND table_type='BASE TABLE'" "$DB_NAME")"
orders="$(mysql --defaults-extra-file="$CNF" -N "$DB_NAME" -e "SELECT COUNT(*) FROM t_work_order")"
events="$(mysql --defaults-extra-file="$CNF" -N "$DB_NAME" -e "SELECT COUNT(*) FROM t_work_order_event")"
photos="$(mysql --defaults-extra-file="$CNF" -N "$DB_NAME" -e "SELECT COUNT(*) FROM t_work_order_photo")"
files="$(mysql --defaults-extra-file="$CNF" -N "$DB_NAME" -e "SELECT COUNT(*) FROM t_file")"
orphan_events="$(mysql --defaults-extra-file="$CNF" -N "$DB_NAME" -e "SELECT COUNT(*) FROM t_work_order_event e LEFT JOIN t_work_order o ON o.no=e.order_no WHERE o.no IS NULL")"
orphan_photos="$(mysql --defaults-extra-file="$CNF" -N "$DB_NAME" -e "SELECT COUNT(*) FROM t_work_order_photo p LEFT JOIN t_work_order o ON o.no=p.order_no WHERE o.no IS NULL")"
if [ "${tables:-0}" -lt 20 ]; then
  echo "verify failed: expected at least 20 base tables, got ${tables:-0}" >&2
  exit 1
fi
if [ "${orphan_events:-0}" -ne 0 ] || [ "${orphan_photos:-0}" -ne 0 ]; then
  echo "verify failed: orphan event/photo rows found (events=${orphan_events:-0}, photos=${orphan_photos:-0})" >&2
  exit 1
fi
printf 'database=%s tables=%s orders=%s events=%s photos=%s files=%s orphanEvents=%s orphanPhotos=%s\n' \
  "$DB_NAME" "$tables" "$orders" "$events" "$photos" "$files" "$orphan_events" "$orphan_photos"
