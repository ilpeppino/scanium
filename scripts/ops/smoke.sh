***REMOVED***!/bin/bash
***REMOVED*** =============================================================================
***REMOVED*** Scanium Public Endpoint Smoke Test
***REMOVED*** =============================================================================
***REMOVED*** Tests that the backend is reachable through Cloudflare tunnel.
***REMOVED*** Returns exit code 0 if all tests pass, 1 otherwise.
***REMOVED***
***REMOVED*** Usage:
***REMOVED***   ./scripts/ops/smoke.sh                     ***REMOVED*** Test production
***REMOVED***   ./scripts/ops/smoke.sh https://other.url  ***REMOVED*** Test custom URL
***REMOVED***
***REMOVED*** Add to cron for monitoring (e.g., every 5 minutes):
***REMOVED***   */5 * * * * /path/to/smoke.sh >> /var/log/scanium-smoke.log 2>&1
***REMOVED*** =============================================================================

set -euo pipefail

BASE_URL="${1:-https://scanium.gtemp1.com}"
TIMEOUT=10
FAILED=0

log() {
  echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] $*"
}

test_endpoint() {
  local endpoint="$1"
  local expected_code="$2"
  local url="${BASE_URL}${endpoint}"

  local http_code
  http_code=$(curl -s -o /dev/null -w '%{http_code}' --max-time "$TIMEOUT" "$url" 2>/dev/null || echo "000")

  if [[ "$http_code" == "$expected_code" ]]; then
    log "PASS: $endpoint -> HTTP $http_code"
    return 0
  else
    log "FAIL: $endpoint -> HTTP $http_code (expected $expected_code)"
    return 1
  fi
}

log "Starting smoke tests against $BASE_URL"

***REMOVED*** Test 1: Health endpoint (no auth required, should return 200)
if ! test_endpoint "/health" "200"; then
  FAILED=1
fi

***REMOVED*** Test 2: Config endpoint (requires auth, should return 401 without API key)
if ! test_endpoint "/v1/config" "401"; then
  FAILED=1
fi

***REMOVED*** Test 3: Assist status (requires auth, should return 401 without API key)
***REMOVED*** Note: May return 403 if Cloudflare WAF blocks the request
http_code=$(curl -s -o /dev/null -w '%{http_code}' --max-time "$TIMEOUT" "${BASE_URL}/v1/assist/status" 2>/dev/null || echo "000")
if [[ "$http_code" == "401" || "$http_code" == "403" ]]; then
  log "PASS: /v1/assist/status -> HTTP $http_code (401 or 403 expected)"
else
  log "FAIL: /v1/assist/status -> HTTP $http_code (expected 401 or 403)"
  FAILED=1
fi

***REMOVED*** Summary
if [[ "$FAILED" -eq 0 ]]; then
  log "All smoke tests passed"
  exit 0
else
  log "Some smoke tests failed"
  exit 1
fi
