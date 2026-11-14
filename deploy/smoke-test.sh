#!/usr/bin/env bash
# Smoke test of the walking skeleton: client -> gateway -> mock-service -> client.
# Usage: deploy/smoke-test.sh [gateway-url]     (default http://localhost:8080)
set -euo pipefail

GATEWAY_URL="${1:-http://localhost:8080}"

echo "GET ${GATEWAY_URL}/orders/42"
body="$(curl --fail --silent --show-error --max-time 10 "${GATEWAY_URL}/orders/42")"
echo "${body}"

if [[ "${body}" == *'"id":42'* ]]; then
  echo "PASS: the gateway returned order 42 from the backend"
else
  echo "FAIL: the response does not contain order 42" >&2
  exit 1
fi
