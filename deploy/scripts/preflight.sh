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
  BOOTSTRAP_ADMIN_ACCOUNT BOOTSTRAP_ADMIN_NAME BOOTSTRAP_ADMIN_PASSWORD
)
for key in "${required[@]}"; do
  value="$(grep -E "^$key=" "$ENV_FILE" | tail -n1 | cut -d= -f2- || true)"
  [ -n "$value" ] || fail "$key is empty"
  case "$value" in
    replace-with-*|sk-...|'') fail "$key still contains a placeholder" ;;
  esac
done

s3_endpoint="$(grep -E '^APP_FILE_S3_ENDPOINT=' "$ENV_FILE" | tail -n1 | cut -d= -f2-)"
case "$s3_endpoint" in https://*) ;; *) fail "APP_FILE_S3_ENDPOINT must use HTTPS" ;; esac
grep -q '^DB_USE_SSL=true' "$ENV_FILE" || fail "DB_USE_SSL must be true in production"

echo "production preflight: PASS"
