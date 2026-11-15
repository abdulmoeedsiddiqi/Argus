#!/usr/bin/env bash
# Sets up the Argus demo with one command, entirely through the Admin API:
#   1. signs in as the demo administrator
#   2. sends deploy/seed/demo-config.yaml to POST /admin/import
#      (plans, backend copies, routes, consumers; keys are issued by the server)
#   3. revokes the key of the "test-script" consumer, so the demo has a revoked key
#   4. saves the issued keys in deploy/seed/demo-keys.txt for the other demo scripts
#
# The database is never touched directly. Running the script again is safe: existing entries
# are updated and consumers that already have a working key get no second one.
#
# Usage: scripts/seed.sh            (Admin API at http://localhost:9000, or set ADMIN_URL)
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../deploy/admin-lib.sh
source "${HERE}/../deploy/admin-lib.sh"
SEED_FILE="${ARGUS_ROOT}/deploy/seed/demo-config.yaml"

echo "Waiting for the Admin API at ${ADMIN_URL} ..."
wait_for_admin
admin_login
echo "Signed in as ${ADMIN_USERNAME}."

result="$(admin_call POST /admin/import -H 'Content-Type: application/yaml' --data-binary "@${SEED_FILE}")"
status="${result%% *}"
body="${result#* }"
if [[ "${status}" != "200" ]]; then
  echo "Import failed (${status}): ${body}" >&2
  exit 1
fi
created="$(printf '%s' "${body}" | sed -n 's/.*"created":\([0-9]*\).*/\1/p')"
updated="$(printf '%s' "${body}" | sed -n 's/.*"updated":\([0-9]*\).*/\1/p')"
echo "Imported ${SEED_FILE##*/}: ${created} created, ${updated} updated."

# Issued keys, one per line as "<key id> <key> <consumer id>". The full key is in this answer
# only; the server keeps just its hash.
issued="$(printf '%s' "${body}" \
  | grep -o '"id":"[^"]*","key":"[^"]*","consumerId":"[^"]*"' \
  | sed 's/"id":"\([^"]*\)","key":"\([^"]*\)","consumerId":"\([^"]*\)"/\1 \2 \3/' || true)"

# Keep the keys of an earlier run: on a repeated run only new keys are returned.
SHOP_APP_KEY=""
PARTNER_API_KEY=""
REVOKED_KEY=""
if [[ -f "${DEMO_KEYS_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${DEMO_KEYS_FILE}"
fi

while read -r key_id key consumer; do
  [[ -z "${key_id}" ]] && continue
  case "${consumer}" in
    shop-app)    SHOP_APP_KEY="${key}" ;;
    partner-api) PARTNER_API_KEY="${key}" ;;
    test-script)
      revoke="$(admin_call POST "/admin/keys/${key_id}/revoke")"
      if [[ "${revoke%% *}" != "200" ]]; then
        echo "Could not revoke the test-script key: ${revoke}" >&2
        exit 1
      fi
      REVOKED_KEY="${key}"
      ;;
  esac
  # Only the display prefix is printed; the full keys go to the file below.
  echo "  key issued for ${consumer}: ${key:0:12}... (id ${key_id})"
done <<< "${issued}"

if [[ -z "${SHOP_APP_KEY}" || -z "${REVOKED_KEY}" ]]; then
  echo "The demo keys are not known: the configuration was seeded before and" >&2
  echo "${DEMO_KEYS_FILE} is missing. Start from an empty stack" >&2
  echo "(docker compose -f deploy/docker-compose.yml down, then up) and seed again." >&2
  exit 1
fi

{
  echo "# Demo API keys issued by scripts/seed.sh. Local demo values; this file is not committed."
  echo "SHOP_APP_KEY=${SHOP_APP_KEY}"
  echo "PARTNER_API_KEY=${PARTNER_API_KEY}"
  echo "REVOKED_KEY=${REVOKED_KEY}"
} > "${DEMO_KEYS_FILE}"

echo "Demo keys saved in deploy/seed/demo-keys.txt (shop-app, partner-api, one revoked key)."
echo "Try it:   deploy/smoke-test.sh"
echo "Admin UI: ${ADMIN_URL}/swagger-ui.html"
