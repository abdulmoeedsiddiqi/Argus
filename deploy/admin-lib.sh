#!/usr/bin/env bash
# Shared helpers for the demo scripts that talk to the Admin API. Source it, do not run it.
# Needs only bash, curl, sed and grep (no jq).

ADMIN_URL="${ADMIN_URL:-http://localhost:9000}"
# Demo administrator from deploy/docker-compose.yml (not real credentials).
ADMIN_USERNAME="${ARGUS_ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ARGUS_ADMIN_PASSWORD:-demo-admin-password-not-for-real-use}"

# File in which scripts/seed.sh saves the demo keys it was issued (ignored by Git).
ARGUS_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEMO_KEYS_FILE="${ARGUS_ROOT}/deploy/seed/demo-keys.txt"

# Waits until the Admin API answers its health check (up to about 3 minutes).
wait_for_admin() {
  local attempt
  for attempt in $(seq 1 90); do
    if curl --silent --fail --max-time 3 "${ADMIN_URL}/actuator/health" > /dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "The Admin API at ${ADMIN_URL} did not become healthy." >&2
  return 1
}

# Signs in and stores the admin token in ADMIN_TOKEN.
admin_login() {
  local response
  response="$(curl --silent --show-error --max-time 10 -X POST "${ADMIN_URL}/admin/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${ADMIN_USERNAME}\",\"password\":\"${ADMIN_PASSWORD}\"}")"
  ADMIN_TOKEN="$(printf '%s' "${response}" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
  if [[ -z "${ADMIN_TOKEN}" ]]; then
    echo "Admin sign-in failed: ${response}" >&2
    return 1
  fi
}

# admin_call METHOD PATH [curl arguments...]  ->  prints "<status> <body>"
admin_call() {
  local method="$1" path="$2"
  shift 2
  curl --silent --show-error --max-time 20 -X "${method}" "${ADMIN_URL}${path}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" --write-out '\n%{http_code}' "$@" \
    | awk '{ lines[NR] = $0 } END { body = ""; for (i = 1; i < NR; i++) body = body lines[i]; print lines[NR] " " body }'
}

# json_field NAME  ->  reads JSON on stdin, prints the first string value of that field
json_field() {
  sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p" | head -n 1
}

# Loads SHOP_APP_KEY, PARTNER_API_KEY and REVOKED_KEY saved by scripts/seed.sh.
load_demo_keys() {
  if [[ ! -f "${DEMO_KEYS_FILE}" ]]; then
    echo "No demo keys found. Run scripts/seed.sh first." >&2
    return 1
  fi
  # shellcheck disable=SC1090
  source "${DEMO_KEYS_FILE}"
}
