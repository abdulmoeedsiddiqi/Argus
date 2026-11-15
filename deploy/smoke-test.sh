#!/usr/bin/env bash
# Smoke test of the demo: client -> Nginx -> gateway (authentication, routing) -> backend copy.
# Run scripts/seed.sh first; it issues the demo keys this script uses.
# Usage: deploy/smoke-test.sh [gateway-url]     (default http://localhost:8080, through Nginx)
set -euo pipefail

GATEWAY_URL="${1:-http://localhost:8080}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=admin-lib.sh
source "${HERE}/admin-lib.sh"
load_demo_keys
DEMO_KEY="${SHOP_APP_KEY}"
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

# Prints "<status> <body>" for one request; extra arguments are passed to curl.
call() {
  curl --silent --show-error --max-time 10 --write-out ' %{http_code}' "$@" \
    | awk '{ status = $NF; $NF = ""; print status " " $0 }'
}

# FR-06: API keys (issued through the Admin API, known to the gateways through Kafka).
check "valid X-API-Key returns 200 and order 42" '200 {"id":42' \
  "$(call -H "X-API-Key: ${DEMO_KEY}" "${GATEWAY_URL}/orders/42")"
check "missing key is refused with 401" '401 ' "$(call "${GATEWAY_URL}/orders/42")"
check "wrong key is refused with 401" '401 ' \
  "$(call -H "X-API-Key: ak_live_wrong_key" "${GATEWAY_URL}/orders/42")"
check "revoked key is refused with 401" '401 ' \
  "$(call -H "X-API-Key: ${REVOKED_KEY}" "${GATEWAY_URL}/orders/42")"
check "refusals carry the reason code" 'AUTHENTICATION_FAILED' "$(call "${GATEWAY_URL}/orders/42")"

# FR-05: JWTs.
check "valid JWT returns 200" '200 ' \
  "$(call -H "Authorization: Bearer $("${HERE}/demo-jwt.sh")" "${GATEWAY_URL}/orders/42")"
check "expired JWT is refused with 401" '401 ' \
  "$(call -H "Authorization: Bearer $("${HERE}/demo-jwt.sh" expired)" "${GATEWAY_URL}/orders/42")"

# FR-01: an unknown path is rejected with a reason code (after authentication).
check "unknown path is rejected with ROUTE_NOT_FOUND" 'ROUTE_NOT_FOUND' \
  "$(call -H "X-API-Key: ${DEMO_KEY}" "${GATEWAY_URL}/payments/1")"

# FR-02: over 40 requests both Orders copies answer (weighted 3:1).
copies="$(curl --silent --max-time 60 --output /dev/null -H "X-API-Key: ${DEMO_KEY}" \
  --write-out '%header{x-argus-upstream}\n' "${GATEWAY_URL}/orders/[1-40]" | sort | uniq -c)"
echo "${copies}"
check "orders-a receives requests" 'orders-a' "${copies}"
check "orders-b receives requests" 'orders-b' "${copies}"

if (( failures > 0 )); then
  echo "${failures} check(s) failed" >&2
  exit 1
fi
echo "All checks passed"
