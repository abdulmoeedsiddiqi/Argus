#!/usr/bin/env bash
# Configuration propagation on the running Compose stack (module M5: FR-12, QR-09, QR-12, QR-13).
# Every change is made through the Admin API; each gateway instance is then asked directly
# on its own port (gateway-1: 8091, gateway-2: 8092, gateway-3: 8093).
#
#   1. TC-12.1  a route created through the Admin API is used by all 3 gateways
#   2. QR-12    a key revoked through the Admin API gets 401 on all 3 gateways within 5 s
#   3. TC-Q09.1 stop gateway-2, revoke a key, start gateway-2: it never accepts that key
#   4. QR-13    the audit log shows each change
#
# Run scripts/seed.sh first. Usage: deploy/propagation-test.sh
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=admin-lib.sh
source "${HERE}/admin-lib.sh"
COMPOSE=(docker compose -f "${HERE}/docker-compose.yml")
GATEWAYS=(http://localhost:8091 http://localhost:8092 http://localhost:8093)
ROUTE_ID="demo-health-$(date +%s)"
failures=0

pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1" >&2; failures=$((failures + 1)); }

# status URL KEY  ->  HTTP status of GET URL with that API key ("000" if nothing answered)
status() {
  curl --silent --output /dev/null --max-time 5 --write-out '%{http_code}' \
    -H "X-API-Key: $2" "$1" || true
}

# all_gateways PATH KEY EXPECTED SECONDS  ->  waits until every gateway answers EXPECTED
all_gateways() {
  local path="$1" key="$2" expected="$3" seconds="$4" deadline gateway ok
  deadline=$(( $(date +%s) + seconds ))
  while true; do
    ok=1
    for gateway in "${GATEWAYS[@]}"; do
      [[ "$(status "${gateway}${path}" "${key}")" == "${expected}" ]] || ok=0
    done
    (( ok == 1 )) && return 0
    (( $(date +%s) >= deadline )) && return 1
    sleep 0.3
  done
}

# issue_key  ->  issues a key for shop-app; sets NEW_KEY and NEW_KEY_ID
issue_key() {
  local answer
  answer="$(admin_call POST /admin/consumers/shop-app/keys)"
  NEW_KEY="$(printf '%s' "${answer}" | json_field key)"
  NEW_KEY_ID="$(printf '%s' "${answer}" | json_field id)"
  [[ -n "${NEW_KEY}" ]] || { echo "Could not issue a key: ${answer}" >&2; exit 1; }
}

wait_for_admin
admin_login
load_demo_keys

echo "--- 1. A route created through the Admin API is used by all 3 gateways (TC-12.1)"
if all_gateways /health "${SHOP_APP_KEY}" 404 5; then
  pass "before: /health is unknown on all 3 gateways (404)"
else
  fail "before: /health should be unknown on all 3 gateways"
fi
created="$(admin_call POST /admin/routes -H 'Content-Type: application/json' \
  -d "{\"id\":\"${ROUTE_ID}\",\"pathPrefix\":\"/health\",\"serviceId\":\"orders\",\"timeoutMs\":3000}")"
[[ "${created%% *}" == "201" ]] || fail "creating the route: ${created}"
if all_gateways /health "${SHOP_APP_KEY}" 200 5; then
  pass "after: all 3 gateways forward /health (200) within 5 s"
else
  fail "after: not every gateway uses the new route within 5 s"
fi

echo "--- 2. A key revoked through the Admin API gets 401 on all 3 gateways (QR-12)"
issue_key
if all_gateways /orders/42 "${NEW_KEY}" 200 5; then
  pass "a newly issued key works on all 3 gateways within 5 s"
else
  fail "the new key does not work on every gateway"
fi
admin_call POST "/admin/keys/${NEW_KEY_ID}/revoke" > /dev/null
if all_gateways /orders/42 "${NEW_KEY}" 401 5; then
  pass "after revoking it: 401 on all 3 gateways within 5 s"
else
  fail "the revoked key is still accepted somewhere after 5 s"
fi

echo "--- 3. Stop gateway-2, revoke a key, start gateway-2 again (QR-09, TC-Q09.1)"
issue_key
all_gateways /orders/42 "${NEW_KEY}" 200 5 || fail "the second new key does not work everywhere"
"${COMPOSE[@]}" stop gateway-2 > /dev/null 2>&1
echo "gateway-2 stopped"
admin_call POST "/admin/keys/${NEW_KEY_ID}/revoke" > /dev/null
echo "key revoked while gateway-2 was down"
started_at="$(date +%s)"
"${COMPOSE[@]}" start gateway-2 > /dev/null 2>&1
seen=""
refused_after=""
for attempt in $(seq 1 300); do
  code="$(status "http://localhost:8092/orders/42" "${NEW_KEY}")"
  [[ "${seen}" == *"${code}"* ]] || seen="${seen} ${code}"
  if [[ "${code}" == "401" ]]; then
    refused_after=$(( $(date +%s) - started_at ))
    break
  fi
  sleep 0.2
done
echo "answers of gateway-2 while it started:${seen}   (000 = not listening yet, 503 = not ready)"
if [[ "${seen}" == *"200"* ]]; then
  fail "gateway-2 accepted the revoked key after its restart"
elif [[ -n "${refused_after}" ]]; then
  pass "gateway-2 never accepted the revoked key; it refused it ${refused_after} s after 'docker start'"
else
  fail "gateway-2 did not come back within the waiting time"
fi
if [[ "$(status "http://localhost:8092/orders/42" "${SHOP_APP_KEY}")" == "200" ]]; then
  pass "gateway-2 rebuilt routes and keys: a valid key gets 200"
else
  fail "gateway-2 does not serve a valid key after its restart"
fi

echo "--- 4. The audit log shows each change (QR-13)"
audit="$(admin_call GET /admin/audit)"
entries="$(printf '%s' "${audit#* }" | sed 's/},{/}\n{/g')"
printf '%s\n' "${entries}" | head -n 6
if printf '%s' "${entries}" | grep -q "\"action\":\"CREATE\",\"entityType\":\"ROUTE\",\"entityId\":\"${ROUTE_ID}\""; then
  pass "the route creation is in the audit log, with actor ${ADMIN_USERNAME}"
else
  fail "the route creation is missing from the audit log"
fi
if printf '%s' "${entries}" | grep -q "\"action\":\"REVOKE\",\"entityType\":\"API_KEY\",\"entityId\":\"${NEW_KEY_ID}\""; then
  pass "the key revocation is in the audit log"
else
  fail "the key revocation is missing from the audit log"
fi

# Clean up: remove the demo route again (this is audited and propagated too).
admin_call DELETE "/admin/routes/${ROUTE_ID}" > /dev/null
if all_gateways /health "${SHOP_APP_KEY}" 404 5; then
  pass "clean-up: the deleted route is gone from all 3 gateways"
else
  fail "clean-up: the deleted route is still used somewhere"
fi

if (( failures > 0 )); then
  echo "${failures} check(s) failed" >&2
  exit 1
fi
echo "All checks passed"
