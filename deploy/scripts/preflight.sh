#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="${1:-$ROOT/deploy/.env.production}"
CERT_DIR="$ROOT/deploy/certs"
ALERT_FILE="$ROOT/deploy/prometheus/alertmanager.yml"

fail() { echo "preflight: $*" >&2; exit 1; }

[ -f "$ENV_FILE" ] || fail "missing $ENV_FILE"
[ -f "$CERT_DIR/tls.crt" ] || fail "missing $CERT_DIR/tls.crt"
[ -f "$CERT_DIR/tls.key" ] || fail "missing $CERT_DIR/tls.key"
[ -f "$ALERT_FILE" ] || fail "missing $ALERT_FILE; copy alertmanager.example.yml and configure a real receiver"
grep -q 'alert.example.com' "$ALERT_FILE" && fail "replace the example Alertmanager receiver"

required=(
  DB_ROOT_PASSWORD DB_USER DB_PASSWORD DB_NAME
  APP_FILE_S3_ENDPOINT APP_FILE_S3_BUCKET
  AI_BASE_URL AI_MODEL AI_API_KEY
)
for key in "${required[@]}"; do
  value="$(grep -E "^$key=" "$ENV_FILE" | tail -n1 | cut -d= -f2- || true)"
  [ -n "$value" ] || fail "$key is empty"
  case "$value" in
    replace-with-*|sk-...|'') fail "$key still contains a placeholder" ;;
  esac
done

bootstrap_values=()
for key in BOOTSTRAP_ADMIN_ACCOUNT BOOTSTRAP_ADMIN_NAME BOOTSTRAP_ADMIN_PASSWORD; do
  bootstrap_values+=("$(grep -E "^$key=" "$ENV_FILE" | tail -n1 | cut -d= -f2- || true)")
done
bootstrap_set=0
for value in "${bootstrap_values[@]}"; do [ -z "$value" ] || bootstrap_set=$((bootstrap_set + 1)); done
[ "$bootstrap_set" -eq 0 ] || [ "$bootstrap_set" -eq 3 ] || fail "BOOTSTRAP_ADMIN_* must be all set for first start or all empty after initialization"
if [ -n "${bootstrap_values[2]}" ] && [ "${#bootstrap_values[2]}" -lt 12 ]; then
  fail "BOOTSTRAP_ADMIN_PASSWORD must be at least 12 characters"
fi

s3_endpoint="$(grep -E '^APP_FILE_S3_ENDPOINT=' "$ENV_FILE" | tail -n1 | cut -d= -f2-)"
case "$s3_endpoint" in https://*) ;; *) fail "APP_FILE_S3_ENDPOINT must use HTTPS" ;; esac
grep -q '^DB_USE_SSL=true' "$ENV_FILE" || fail "DB_USE_SSL must be true in production"

echo "checking database schema (read-only)"
command -v mysql >/dev/null 2>&1 || fail "mysql client is required to verify the production schema"
schema_sql="
SELECT CONCAT('MISSING_TABLE:', TABLE_NAME)
FROM (
  SELECT 't_student' TABLE_NAME UNION ALL SELECT 't_worker' UNION ALL SELECT 't_admin'
  UNION ALL SELECT 't_worker_building' UNION ALL SELECT 't_auth_session'
  UNION ALL SELECT 't_login_attempt' UNION ALL SELECT 't_building'
  UNION ALL SELECT 't_building_floor' UNION ALL SELECT 't_work_order'
  UNION ALL SELECT 't_work_order_event' UNION ALL SELECT 't_work_order_photo'
  UNION ALL SELECT 't_file' UNION ALL SELECT 't_ai_review_task'
  UNION ALL SELECT 't_part' UNION ALL SELECT 't_part_request'
  UNION ALL SELECT 't_rework_review' UNION ALL SELECT 't_exception_record'
  UNION ALL SELECT 't_config' UNION ALL SELECT 't_idempotency_record'
  UNION ALL SELECT 't_seq'
) required
WHERE NOT EXISTS (
  SELECT 1 FROM information_schema.TABLES actual
  WHERE actual.TABLE_SCHEMA=DATABASE() AND actual.TABLE_NAME=required.TABLE_NAME
    AND actual.TABLE_TYPE='BASE TABLE'
);
SELECT 'MISSING_VIEW:v_user_account'
WHERE NOT EXISTS (
  SELECT 1 FROM information_schema.VIEWS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='v_user_account'
);
SELECT CONCAT('MISSING_COLUMN:', c.TABLE_NAME, '.', c.COLUMN_NAME)
FROM (
  SELECT 't_work_order' TABLE_NAME, 'verify_due_at' COLUMN_NAME UNION ALL
  SELECT 't_work_order', 'archive_due_at' UNION ALL
  SELECT 't_file', 'cleanup_claimed_at' UNION ALL
  SELECT 't_ai_review_task', 'latency_ms'
) c
WHERE NOT EXISTS (
  SELECT 1 FROM information_schema.COLUMNS actual
  WHERE actual.TABLE_SCHEMA=DATABASE() AND actual.TABLE_NAME=c.TABLE_NAME
    AND actual.COLUMN_NAME=c.COLUMN_NAME
);
"
MYSQL_CNF="$(mktemp)"
trap 'rm -f "$MYSQL_CNF"' EXIT
chmod 600 "$MYSQL_CNF"
printf '[client]\nhost=%s\nuser=%s\npassword=%s\n' "${DB_HOST:-127.0.0.1}" "$DB_USER" "$DB_PASSWORD" > "$MYSQL_CNF"
ssl_cipher="$(mysql --defaults-extra-file="$MYSQL_CNF" --ssl-mode=REQUIRED --batch --skip-column-names -e "SHOW STATUS LIKE 'Ssl_cipher'" 2>/dev/null || true)"
[ -n "$ssl_cipher" ] || fail "database TLS connection failed"
schema_errors="$(mysql --defaults-extra-file="$MYSQL_CNF" --ssl-mode=REQUIRED --batch --skip-column-names "$DB_NAME" -e "$schema_sql" 2>/dev/null || true)"
[ -z "$schema_errors" ] || fail "database schema incomplete: ${schema_errors//$'\n'/, }; run deploy/scripts/migrate.sh"

echo "production preflight: PASS"
