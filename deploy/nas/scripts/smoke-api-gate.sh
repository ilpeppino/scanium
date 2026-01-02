***REMOVED***!/usr/bin/env sh
set -eu

BASE="${1:-https://scanium.gtemp1.com}"
KEY="${2:-}"

echo "Base: $BASE"

echo "1) No key (should be 403 at Cloudflare OR 401 at backend):"
curl -sS -o /dev/null -w "HTTP=%{http_code}\n" \
  "$BASE/v1/assist/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"ping","items":[],"history":[]}'

if [ -n "$KEY" ]; then
  echo "2) With key (should be 200):"
  curl -sS -o /dev/null -w "HTTP=%{http_code}\n" \
    "$BASE/v1/assist/chat" \
    -H "X-API-Key: $KEY" \
    -H "Content-Type: application/json" \
    -d '{"message":"ping","items":[],"history":[]}'
else
  echo "2) Skipped (no key provided)"
fi
