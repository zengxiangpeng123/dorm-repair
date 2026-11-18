#!/bin/sh
set -eu
response="$(curl --fail --silent --show-error --max-time 4 http://127.0.0.1:8082/actuator/health/readiness)"
case "$response" in
  *'"status":"UP"'*) echo "UP" ;;
  *) echo "readiness not UP: $response" >&2; exit 1 ;;
esac
