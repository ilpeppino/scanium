***REMOVED***!/usr/bin/env bash
***REMOVED*** =============================================================================
***REMOVED*** Scanium Smoke Test
***REMOVED*** =============================================================================
***REMOVED*** Tests backend endpoints for basic reachability and authentication.
***REMOVED*** Returns exit code 0 if all tests pass, 1 otherwise.
***REMOVED***
***REMOVED*** Usage:
***REMOVED***   ./scripts/ops/smoke.sh --help
***REMOVED***   ./scripts/ops/smoke.sh --base-url https://scanium.gtemp1.com
***REMOVED***   SCANIUM_API_KEY=xxx ./scripts/ops/smoke.sh --base-url https://scanium.gtemp1.com
***REMOVED***
***REMOVED*** =============================================================================

***REMOVED*** Source common library
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
***REMOVED*** shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

***REMOVED*** -----------------------------------------------------------------------------
***REMOVED*** Defaults
***REMOVED*** -----------------------------------------------------------------------------
BASE_URL=""
API_KEY="${SCANIUM_API_KEY:-}"
TIMEOUT=10
VERBOSE=false
FAILED=0

***REMOVED*** -----------------------------------------------------------------------------
***REMOVED*** Help
***REMOVED*** -----------------------------------------------------------------------------
show_help() {
  cat <<EOF
smoke.sh - Scanium backend smoke tests

Usage: $(basename "$0") [OPTIONS]

Options:
  --base-url URL    Base URL to test (default: http://localhost:8080)
  --api-key KEY     API key for authenticated tests (or set SCANIUM_API_KEY)
  --timeout SECS    Timeout per request in seconds (default: 10)
  --verbose         Show detailed output including response bodies
  --help            Show this help message

Examples:
  ***REMOVED*** Test public production endpoint
  ./scripts/ops/smoke.sh --base-url https://scanium.gtemp1.com

  ***REMOVED*** Test with authentication
  SCANIUM_API_KEY=xxx ./scripts/ops/smoke.sh --base-url https://scanium.gtemp1.com

  ***REMOVED*** Test local development
  ./scripts/ops/smoke.sh --base-url http://localhost:8080 --verbose

Endpoints tested:
  GET /health           - Health check (expect 200)
  GET /v1/config        - Config endpoint (200 with key, 401 without)
  GET /v1/preflight     - Preflight check (200 with key, 401 without)
  GET /v1/assist/status - Assist status (200/403 with key, 401/403 without)

Exit codes:
  0 - All tests passed
  1 - One or more tests failed

EOF
}

***REMOVED*** -----------------------------------------------------------------------------
***REMOVED*** Parse arguments
***REMOVED*** -----------------------------------------------------------------------------
while [[ $***REMOVED*** -gt 0 ]]; do
  case "$1" in
    --help|-h)
      show_help
      exit 0
      ;;
    --base-url)
      BASE_URL="$2"
      shift 2
      ;;
    --base-url=*)
      BASE_URL="${1***REMOVED****=}"
      shift
      ;;
    --api-key)
      API_KEY="$2"
      shift 2
      ;;
    --api-key=*)
      API_KEY="${1***REMOVED****=}"
      shift
      ;;
    --timeout)
      TIMEOUT="$2"
      shift 2
      ;;
    --timeout=*)
      TIMEOUT="${1***REMOVED****=}"
      shift
      ;;
    --verbose|-v)
      VERBOSE=true
      shift
      ;;
    *)
      ***REMOVED*** Support legacy positional argument for base URL
      if [[ -z "$BASE_URL" && "$1" != -* ]]; then
        BASE_URL="$1"
        shift
      else
        die "Unknown option: $1. Use --help for usage."
      fi
      ;;
  esac
done

***REMOVED*** -----------------------------------------------------------------------------
***REMOVED*** Validate base URL
***REMOVED*** -----------------------------------------------------------------------------
if [[ -z "$BASE_URL" ]]; then
  ***REMOVED*** Try localhost first
  if curl -s --max-time 2 -o /dev/null http://localhost:8080/health 2>/dev/null; then
    BASE_URL="http://localhost:8080"
    log_info "Using detected local backend: $BASE_URL"
  else
    die "No --base-url provided and localhost:8080 not reachable. Use --help for usage."
  fi
fi

***REMOVED*** Strip trailing slash
BASE_URL="${BASE_URL%/}"

***REMOVED*** -----------------------------------------------------------------------------
***REMOVED*** Test functions
***REMOVED*** -----------------------------------------------------------------------------
test_endpoint() {
  local endpoint="$1"
  local expected_codes="$2"  ***REMOVED*** Comma-separated list of acceptable codes
  local auth_required="${3:-false}"
  local url="${BASE_URL}${endpoint}"

  local headers=""
  if [[ "$auth_required" == "true" && -n "$API_KEY" ]]; then
    headers="X-API-Key: $API_KEY"
  fi

  ***REMOVED*** Get status and body
  local response
  local http_code
  local body=""

  if [[ "$VERBOSE" == "true" ]]; then
    local tmp_body
    tmp_body=$(mktemp)
    http_code=$(curl -s -w '%{http_code}' --max-time "$TIMEOUT" -o "$tmp_body" \
      ${headers:+-H "$headers"} "$url" 2>/dev/null || echo "000")
    body=$(cat "$tmp_body" 2>/dev/null || true)
    rm -f "$tmp_body"
  else
    http_code=$(curl -s -o /dev/null -w '%{http_code}' --max-time "$TIMEOUT" \
      ${headers:+-H "$headers"} "$url" 2>/dev/null || echo "000")
  fi

  ***REMOVED*** Check if status is in expected list
  local IFS=','
  local expected
  local passed=false
  for expected in $expected_codes; do
    if [[ "$http_code" == "$expected" ]]; then
      passed=true
      break
    fi
  done

  local auth_suffix=""
  [[ "$auth_required" == "true" && -n "$API_KEY" ]] && auth_suffix=" (auth)"

  if [[ "$passed" == "true" ]]; then
    log_success "$endpoint$auth_suffix -> HTTP $http_code"
    return 0
  else
    log_fail "$endpoint$auth_suffix -> HTTP $http_code (expected: $expected_codes)"

    if [[ "$VERBOSE" == "true" && -n "$body" ]]; then
      local truncated
      truncated=$(truncate_string "$body" 200 | redact_secrets)
      echo "  Response: $truncated"
    fi
    return 1
  fi
}

***REMOVED*** -----------------------------------------------------------------------------
***REMOVED*** Run tests
***REMOVED*** -----------------------------------------------------------------------------
log_info "Starting smoke tests against $BASE_URL"
[[ -n "$API_KEY" ]] && log_info "API key provided - will run authenticated tests"
echo ""

***REMOVED*** Test 1: Health endpoint (always expect 200)
if ! test_endpoint "/health" "200"; then
  FAILED=1
fi

***REMOVED*** Test 2: Config endpoint
***REMOVED*** - With API key: expect 200
***REMOVED*** - Without API key: expect 401 (auth required)
if [[ -n "$API_KEY" ]]; then
  if ! test_endpoint "/v1/config" "200" "true"; then
    FAILED=1
  fi
else
  if ! test_endpoint "/v1/config" "401"; then
    FAILED=1
  fi
fi

***REMOVED*** Test 3: Preflight endpoint
***REMOVED*** - With API key: expect 200
***REMOVED*** - Without API key: expect 401 (auth required)
if [[ -n "$API_KEY" ]]; then
  if ! test_endpoint "/v1/preflight" "200" "true"; then
    FAILED=1
  fi
else
  if ! test_endpoint "/v1/preflight" "401"; then
    FAILED=1
  fi
fi

***REMOVED*** Test 4: Assist status endpoint
***REMOVED*** - With API key: expect 200 or 403 (route protected but reachable)
***REMOVED*** - Without API key: expect 401 or 403
if [[ -n "$API_KEY" ]]; then
  if ! test_endpoint "/v1/assist/status" "200,403" "true"; then
    FAILED=1
  fi
else
  if ! test_endpoint "/v1/assist/status" "401,403"; then
    FAILED=1
  fi
fi

***REMOVED*** -----------------------------------------------------------------------------
***REMOVED*** Summary
***REMOVED*** -----------------------------------------------------------------------------
echo ""
if [[ "$FAILED" -eq 0 ]]; then
  log_success "All smoke tests passed"
  exit 0
else
  log_fail "Some smoke tests failed"
  exit 1
fi
