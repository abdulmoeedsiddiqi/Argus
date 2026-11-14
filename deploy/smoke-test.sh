#!/usr/bin/env bash
# Smoke test of the routing demo: client -> gateway -> backend copy -> client.
# Usage: deploy/smoke-test.sh [gateway-url]     (default http://localhost:8080)
set -euo pipefail

GATEWAY_URL="${1:-http://localhost:8080}"
failures=0

check() {
  local description="$1" expected="$2" actual="$3"
  if [[ "${actual}" == *"${expected}"* ]]; then
    echo "PASS: ${description}"
  else
    echo "FAIL: ${description} (expected to find: ${expected}; got: ${actual})" >&2
    failures=$((failures + 1))
  fi
}

# FR-01: a configured route is forwarded to a backend.
body="$(curl --silent --show-error --max-time 10 "${GATEWAY_URL}/orders/42")"
check "GET /orders/42 returns order 42" '"id":42' "${body}"

# FR-01: an unknown path is rejected with a reason code.
body="$(curl --silent --show-error --max-time 10 "${GATEWAY_URL}/payments/1")"
check "GET /payments/1 is rejected with ROUTE_NOT_FOUND" 'ROUTE_NOT_FOUND' "${body}"

# FR-02: over 40 requests both Orders copies answer (weighted 3:1).
copies="$(for _ in $(seq 1 40); do
  curl --silent --max-time 10 --output /dev/null --dump-header - "${GATEWAY_URL}/orders/1" \
    | tr -d '\r' | grep -i '^x-argus-upstream:' || true
done | sort | uniq -c)"
echo "${copies}"
check "orders-a receives requests" 'orders-a' "${copies}"
check "orders-b receives requests" 'orders-b' "${copies}"

if (( failures > 0 )); then
  echo "${failures} check(s) failed" >&2
  exit 1
fi
echo "All checks passed"
