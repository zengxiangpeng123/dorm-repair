#!/usr/bin/env bash
set -euo pipefail

fail() { echo "ai-preflight: $*" >&2; exit 1; }

[ -n "${AI_BASE_URL:-}" ] || fail "AI_BASE_URL is empty"
[ -n "${AI_MODEL:-}" ] || fail "AI_MODEL is empty"
[ -n "${AI_API_KEY:-}" ] || fail "AI_API_KEY is empty"
case "$AI_BASE_URL" in
  http://*|https://*) ;;
  *) fail "AI_BASE_URL must start with http:// or https://" ;;
esac
[[ "$AI_MODEL" =~ ^[A-Za-z0-9._/-]+$ ]] || fail "AI_MODEL contains unsupported characters"
command -v curl >/dev/null 2>&1 || fail "curl is required for the AI gateway probe"

BASE_URL="${AI_BASE_URL%/}"
URL="$BASE_URL/chat/completions"
PAYLOAD="$(printf '{"model":"%s","messages":[{"role":"user","content":"Reply with OK."}],"temperature":0,"max_tokens":64}' "$AI_MODEL")"
HEADER_FILE="$(mktemp)"
BODY_FILE="$(mktemp)"
trap 'rm -f "$HEADER_FILE" "$BODY_FILE"' EXIT
chmod 600 "$HEADER_FILE" "$BODY_FILE"

printf 'Authorization: Bearer %s\n' "$AI_API_KEY" > "$HEADER_FILE"
if ! STATUS="$(printf '%s' "$PAYLOAD" | curl --silent --show-error --max-time 60 \
  --header @"$HEADER_FILE" --header 'Content-Type: application/json' \
  --data-binary @- --output "$BODY_FILE" --write-out '%{http_code}' "$URL")"; then
  fail "live request failed; check gateway reachability and TLS"
fi
case "$STATUS" in
  2??) ;;
  *) fail "configured key/model was rejected (HTTP $STATUS)" ;;
esac
grep -Eq '"choices"[[:space:]]*:' "$BODY_FILE" || fail "response has no choices"
grep -Fq "\"model\":\"$AI_MODEL\"" "$BODY_FILE" || grep -Fq "\"model\": \"$AI_MODEL\"" "$BODY_FILE" \
  || fail "gateway returned a different model"
grep -Eq '"(content|reasoning_content)"[[:space:]]*:[[:space:]]*"[^"]+"' "$BODY_FILE" \
  || fail "model returned no content"

printf 'Authorization: Bearer invalid-preflight-key\n' > "$HEADER_FILE"
if ! INVALID_STATUS="$(printf '%s' "$PAYLOAD" | curl --silent --show-error --max-time 60 \
  --header @"$HEADER_FILE" --header 'Content-Type: application/json' \
  --data-binary @- --output /dev/null --write-out '%{http_code}' "$URL")"; then
  fail "invalid-key probe failed; check gateway reachability and TLS"
fi
case "$INVALID_STATUS" in
  401|403) ;;
  *) fail "invalid key was not rejected with HTTP 401/403 (got $INVALID_STATUS)" ;;
esac

echo "AI gateway live probe: PASS (model=$AI_MODEL)"
