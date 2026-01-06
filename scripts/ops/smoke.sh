#!/bin/bash
# =============================================================================
# Scanium Public Endpoint Smoke Test
# =============================================================================
# Tests that the backend is reachable through Cloudflare tunnel.
# Returns exit code 0 if all tests pass, 1 otherwise.
#
# Usage:
#   ./scripts/ops/smoke.sh                              # Test public endpoints only
#   ./scripts/ops/smoke.sh https://scanium.gtemp1.com   # Test custom URL
#   SCANIUM_API_KEY=xxx ./scripts/ops/smoke.sh          # Include authenticated tests
#
# Environment variables:
#   SCANIUM_API_KEY - If set, also tests authenticated endpoints (preflight/chat)
#
# Add to cron for monitoring (e.g., every 5 minutes):
#   */5 * * * * /path/to/smoke.sh >> /var/log/scanium-smoke.log 2>&1
# =============================================================================

set -euo pipefail

BASE_URL="${1:-https://scanium.gtemp1.com}"
API_KEY="${SCANIUM_API_KEY:-}"
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

test_authenticated_endpoint() {
  local endpoint="$1"
  local expected_code="$2"
  local method="${3:-GET}"
  local body="${4:-}"
  local url="${BASE_URL}${endpoint}"

  local http_code
  if [[ "$method" == "POST" ]]; then
    http_code=$(curl -s -o /dev/null -w '%{http_code}' --max-time "$TIMEOUT" \
      -X POST \
      -H "Content-Type: application/json" \
      -H "X-API-Key: $API_KEY" \
      -d "$body" \
      "$url" 2>/dev/null || echo "000")
  else
    http_code=$(curl -s -o /dev/null -w '%{http_code}' --max-time "$TIMEOUT" \
      -H "X-API-Key: $API_KEY" \
      "$url" 2>/dev/null || echo "000")
  fi

  if [[ "$http_code" == "$expected_code" ]]; then
    log "PASS: $endpoint (auth) -> HTTP $http_code"
    return 0
  else
    log "FAIL: $endpoint (auth) -> HTTP $http_code (expected $expected_code)"
    return 1
  fi
}

log "Starting smoke tests against $BASE_URL"

# =============================================================================
# PUBLIC ENDPOINT TESTS (no auth required)
# =============================================================================

# Test 1: Health endpoint (no auth required, should return 200)
if ! test_endpoint "/health" "200"; then
  FAILED=1
fi

# Test 2: Config endpoint (requires auth, should return 401 without API key)
if ! test_endpoint "/v1/config" "401"; then
  FAILED=1
fi

# Test 3: Assist status (requires auth, should return 401 without API key)
# Note: May return 403 if Cloudflare WAF blocks the request
http_code=$(curl -s -o /dev/null -w '%{http_code}' --max-time "$TIMEOUT" "${BASE_URL}/v1/assist/status" 2>/dev/null || echo "000")
if [[ "$http_code" == "401" || "$http_code" == "403" ]]; then
  log "PASS: /v1/assist/status -> HTTP $http_code (401 or 403 expected)"
else
  log "FAIL: /v1/assist/status -> HTTP $http_code (expected 401 or 403)"
  FAILED=1
fi

# =============================================================================
# AUTHENTICATED ENDPOINT TESTS (only if API key provided)
# =============================================================================

if [[ -n "$API_KEY" ]]; then
  log "Running authenticated endpoint tests..."

  # Test 4: Config endpoint with valid API key (should return 200)
  if ! test_authenticated_endpoint "/v1/config" "200"; then
    FAILED=1
  fi

  # Test 5: Preflight/chat endpoint with valid API key (should return 200)
  # Uses minimal chat payload that Android preflight sends
  PREFLIGHT_BODY='{"message":"ping","items":[],"history":[]}'
  if ! test_authenticated_endpoint "/v1/assist/chat" "200" "POST" "$PREFLIGHT_BODY"; then
    FAILED=1
  fi
else
  log "Skipping authenticated tests (SCANIUM_API_KEY not set)"
fi

# =============================================================================
# SUMMARY
# =============================================================================

if [[ "$FAILED" -eq 0 ]]; then
  log "All smoke tests passed"
  exit 0
else
  log "Some smoke tests failed"
  exit 1
fi
