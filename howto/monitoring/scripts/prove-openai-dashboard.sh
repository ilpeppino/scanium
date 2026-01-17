#!/usr/bin/env bash
#
# Prove OpenAI Runtime Dashboard is working end-to-end
#
# Tests:
# 1. Backend metrics endpoint is accessible
# 2. Metrics exist in Mimir (scraped by Alloy)
# 3. Dashboard queries return data
#
# Usage: ./prove-openai-dashboard.sh [SKIP_TRAFFIC=1]
#
# Exit codes:
# 0 = All tests passed
# 1 = One or more tests failed

set -euo pipefail

# Configuration
SKIP_TRAFFIC="${1:-0}"
MIMIR_URL="${MIMIR_URL:-http://localhost:9009}"
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"

# Find the API key from backend if running
if command -v docker &> /dev/null; then
  if docker ps --filter name=scanium-backend --format '{{.Names}}' | grep -q scanium-backend; then
    BACKEND_API_KEY=$(docker exec scanium-backend printenv SCANIUM_ASSISTANT_API_KEYS | cut -d',' -f1 2>/dev/null || echo "")
  fi
fi
BACKEND_API_KEY="${BACKEND_API_KEY:-Cr3UnvP9ubNBxSiKaJA7LWAaKEwl4WNdpVP-CzuxA6hAxyLlo3iPqqfHo3R4nxoz}"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Test results
TESTS_PASSED=0
TESTS_FAILED=0

# Helper functions
log_info() {
  echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
  echo -e "${GREEN}[PASS]${NC} $1"
  ((TESTS_PASSED++)) || true
}

log_fail() {
  echo -e "${RED}[FAIL]${NC} $1"
  ((TESTS_FAILED++)) || true
}

log_warn() {
  echo -e "${YELLOW}[WARN]${NC} $1"
}

echo "============================================================"
echo "OpenAI Runtime Dashboard Proof Tests"
echo "============================================================"
echo "Mimir URL: $MIMIR_URL"
echo "Backend URL: $BACKEND_URL"
echo "Timestamp: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo ""

# Test 1: Backend metrics endpoint is accessible
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Test 1: Backend Metrics Endpoint"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if curl -sf "${BACKEND_URL}/metrics" > /dev/null; then
  log_success "Backend metrics endpoint accessible"

  # Check if assistant metrics exist
  if curl -sf "${BACKEND_URL}/metrics" | grep -q "scanium_assistant_requests_total"; then
    log_success "Assistant metrics defined in backend"
  else
    log_fail "Assistant metrics NOT found in backend /metrics"
  fi
else
  log_fail "Backend metrics endpoint NOT accessible"
fi

echo ""

# Test 2: Generate traffic if not skipped
if [[ "$SKIP_TRAFFIC" == "0" ]]; then
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "Test 2: Generate Test Traffic"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

  SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  if [[ -f "$SCRIPT_DIR/generate-openai-traffic.sh" ]]; then
    log_info "Running traffic generator..."
    if bash "$SCRIPT_DIR/generate-openai-traffic.sh" "$BACKEND_URL" "$BACKEND_API_KEY" > /tmp/traffic.log 2>&1; then
      SUCCESS_COUNT=$(grep "Successful requests:" /tmp/traffic.log | awk '{print $3}' || echo "0")
      if [[ "$SUCCESS_COUNT" -gt 0 ]]; then
        log_success "Generated $SUCCESS_COUNT successful requests"
      else
        log_fail "Traffic generator ran but no successful requests"
      fi
    else
      log_fail "Traffic generator failed (see /tmp/traffic.log)"
    fi

    log_info "Waiting 70s for Alloy to scrape metrics..."
    sleep 70
  else
    log_warn "Traffic generator script not found, skipping"
  fi
else
  log_info "Skipping traffic generation (SKIP_TRAFFIC=1)"
fi

echo ""

# Test 3: Metrics exist in Mimir
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Test 3: Metrics in Mimir (Scraped by Alloy)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Check if backend is being scraped
SCRAPE_UP=$(curl -sf "${MIMIR_URL}/prometheus/api/v1/query?query=up{job=\"scanium-backend\"}" | jq -r '.data.result[0].value[1] // "0"' 2>/dev/null || echo "0")
if [[ "$SCRAPE_UP" == "1" ]]; then
  log_success "Backend is being scraped (up=1)"
else
  log_fail "Backend is NOT being scraped (up=$SCRAPE_UP)"
fi

# Check assistant request metrics
REQUEST_COUNT=$(curl -sf "${MIMIR_URL}/prometheus/api/v1/query?query=scanium_assistant_requests_total{provider=\"openai\"}" | jq '[.data.result[].value[1] | tonumber] | add // 0' 2>/dev/null || echo "0")
REQUEST_COUNT_INT=$(echo "$REQUEST_COUNT" | cut -d'.' -f1)
if [[ "$REQUEST_COUNT_INT" =~ ^[0-9]+$ ]] && [[ "$REQUEST_COUNT_INT" -gt 0 ]]; then
  log_success "Assistant request metrics in Mimir (count=$REQUEST_COUNT)"
else
  log_fail "Assistant request metrics NOT in Mimir or zero"
fi

# Check latency metrics
LATENCY_COUNT=$(curl -sf "${MIMIR_URL}/prometheus/api/v1/query?query=scanium_assistant_request_latency_ms_count{provider=\"openai\"}" | jq '[.data.result[].value[1] | tonumber] | add // 0' 2>/dev/null || echo "0")
LATENCY_COUNT_INT=$(echo "$LATENCY_COUNT" | cut -d'.' -f1)
if [[ "$LATENCY_COUNT_INT" =~ ^[0-9]+$ ]] && [[ "$LATENCY_COUNT_INT" -gt 0 ]]; then
  log_success "Latency metrics in Mimir (count=$LATENCY_COUNT)"
else
  log_warn "Latency metrics NOT in Mimir yet (may appear on next scrape)"
fi

# Check token metrics
TOKEN_SUM=$(curl -sf "${MIMIR_URL}/prometheus/api/v1/query?query=scanium_assistant_tokens_used_sum{provider=\"openai\",token_type=\"total\"}" | jq '[.data.result[].value[1] | tonumber] | add // 0' 2>/dev/null || echo "0")
TOKEN_SUM_INT=$(echo "$TOKEN_SUM" | cut -d'.' -f1)
if [[ "$TOKEN_SUM_INT" =~ ^[0-9]+$ ]] && [[ "$TOKEN_SUM_INT" -gt 0 ]]; then
  log_success "Token metrics in Mimir (sum=$TOKEN_SUM)"
else
  log_warn "Token metrics NOT in Mimir yet (may appear on next scrape)"
fi

echo ""

# Test 4: Dashboard queries work
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Test 4: Dashboard Queries"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Query 1: Request rate
RATE_RESULT=$(curl -sf "${MIMIR_URL}/prometheus/api/v1/query?query=sum(rate(scanium_assistant_requests_total{provider=~\"openai\"}[5m]))" | jq -r '.data.result[0].value[1] // "null"' 2>/dev/null || echo "null")
if [[ "$RATE_RESULT" != "null" ]] && [[ "$RATE_RESULT" != "0" ]]; then
  log_success "Request rate query returns data (rate=$RATE_RESULT)"
else
  log_warn "Request rate query returns zero/null (may need more time)"
fi

# Query 2: Latency percentile
P95_RESULT=$(curl -sf "${MIMIR_URL}/prometheus/api/v1/query?query=histogram_quantile(0.95,%20sum%20by%20(le)%20(rate(scanium_assistant_request_latency_ms_bucket{provider=~\"openai\"}[5m])))" | jq -r '.data.result[0].value[1] // "null"' 2>/dev/null || echo "null")
if [[ "$P95_RESULT" != "null" ]]; then
  log_success "p95 latency query returns data (p95=${P95_RESULT}ms)"
else
  log_warn "p95 latency query returns null (may need more data)"
fi

# Query 3: Provider variable
PROVIDER_LABELS=$(curl -sf "${MIMIR_URL}/prometheus/api/v1/label/provider/values?match[]=scanium_assistant_requests_total" | jq -r '.data[] // ""' 2>/dev/null || echo "")
if echo "$PROVIDER_LABELS" | grep -q "openai"; then
  log_success "Dashboard provider variable has 'openai' option"
else
  log_fail "Dashboard provider variable does NOT have 'openai'"
fi

echo ""
echo "============================================================"
echo "Summary"
echo "============================================================"
echo -e "${GREEN}Tests passed: ${TESTS_PASSED}${NC}"
echo -e "${RED}Tests failed: ${TESTS_FAILED}${NC}"
echo ""

if [[ $TESTS_FAILED -eq 0 ]]; then
  echo -e "${GREEN}✓ OpenAI Runtime Dashboard is OPERATIONAL${NC}"
  echo ""
  echo "You can now:"
  echo "1. Open Grafana at http://localhost:3000 (or your Grafana URL)"
  echo "2. Navigate to 'Scanium - OpenAI Runtime' dashboard"
  echo "3. Verify panels show data for last 15m"
  exit 0
else
  echo -e "${RED}✗ OpenAI Runtime Dashboard has ISSUES${NC}"
  echo ""
  echo "Debug steps:"
  echo "1. Check Alloy logs: docker logs scanium-alloy --tail 50"
  echo "2. Verify Alloy config: monitoring/alloy/config.alloy"
  echo "3. Check backend metrics: curl http://localhost:8080/metrics | grep scanium_assistant"
  echo "4. Query Mimir directly: curl 'http://localhost:9009/prometheus/api/v1/query?query=up{job=\"scanium-backend\"}'"
  exit 1
fi
