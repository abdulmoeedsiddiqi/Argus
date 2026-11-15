#!/usr/bin/env bash
# Mints a demo JWT for the Argus gateway, the way a trusted issuer would (HS256).
# For the demo only: it signs with the demo secret from docker-compose.yml.
#
# Usage:
#   deploy/demo-jwt.sh            # a token that is valid for 5 minutes
#   deploy/demo-jwt.sh expired    # a token that expired 1 minute ago
#
#   curl -H "Authorization: Bearer $(deploy/demo-jwt.sh)" http://localhost:8080/orders/42
set -euo pipefail

SECRET="${ARGUS_JWT_SECRET:-demo-only-jwt-secret-not-for-real-use-0123456789}"
ISSUER="argus-demo"
SUBJECT="partner-api"
PLAN="pro"

now="$(date +%s)"
if [[ "${1:-valid}" == "expired" ]]; then
  expires_at=$((now - 60))
else
  expires_at=$((now + 300))
fi

# Base64url without padding, as the JWT format requires.
base64url() {
  openssl base64 -A | tr '+/' '-_' | tr -d '='
}

header="$(printf '%s' '{"alg":"HS256","typ":"JWT"}' | base64url)"
payload="$(printf '{"iss":"%s","sub":"%s","plan":"%s","exp":%s}' \
  "${ISSUER}" "${SUBJECT}" "${PLAN}" "${expires_at}" | base64url)"
signature="$(printf '%s' "${header}.${payload}" \
  | openssl dgst -binary -sha256 -hmac "${SECRET}" | base64url)"

printf '%s.%s.%s\n' "${header}" "${payload}" "${signature}"
