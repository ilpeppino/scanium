***REMOVED***!/usr/bin/env bash
***REMOVED***
***REMOVED*** Generate minimal OpenAI assistant traffic for dashboard testing
***REMOVED*** Usage: ./generate-openai-traffic.sh [BASE_URL] [API_KEY]
***REMOVED***
***REMOVED*** Requirements:
***REMOVED*** - Backend must be running with SCANIUM_ASSISTANT_PROVIDER=openai
***REMOVED*** - Valid OPENAI_API_KEY configured in backend
***REMOVED*** - Generates success + controlled error scenarios
***REMOVED*** - Minimal token usage to reduce API costs
***REMOVED***
***REMOVED*** Output: Timestamps and request counts for validation

set -euo pipefail

***REMOVED*** Configuration
BASE_URL="${1:-http://localhost:8080}"
API_KEY="${2:-dev-key}"
DURATION_SECONDS=90
REQUEST_DELAY=5  ***REMOVED*** Seconds between requests (reduces rate limiting)

***REMOVED*** Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' ***REMOVED*** No Color

echo "============================================================"
echo "OpenAI Assistant Traffic Generator"
echo "============================================================"
echo "Target: $BASE_URL"
echo "Duration: ${DURATION_SECONDS}s"
echo "Delay between requests: ${REQUEST_DELAY}s"
echo "Start time: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo ""

***REMOVED*** Track metrics
SUCCESS_COUNT=0
ERROR_COUNT=0
START_TIME=$(date +%s)
END_TIME=$((START_TIME + DURATION_SECONDS))

***REMOVED*** Helper function to make assistant request
make_request() {
  local payload="$1"
  local description="$2"
  local expect_error="${3:-false}"

  echo -e "${YELLOW}→ ${description}${NC}"

  response=$(curl -s -w "\n%{http_code}" -X POST \
    "${BASE_URL}/v1/assist/chat" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: ${API_KEY}" \
    -d "$payload")

  http_code=$(echo "$response" | tail -n1)
  body=$(echo "$response" | sed '$d')

  if [[ "$expect_error" == "true" ]]; then
    if [[ "$http_code" -ge 400 ]]; then
      echo -e "${GREEN}✓ Expected error received (HTTP $http_code)${NC}"
      ((ERROR_COUNT++)) || true
      return 0
    else
      echo -e "${RED}✗ Expected error but got success (HTTP $http_code)${NC}"
      return 1
    fi
  else
    if [[ "$http_code" -ge 200 && "$http_code" -lt 300 ]]; then
      echo -e "${GREEN}✓ Success (HTTP $http_code)${NC}"
      ((SUCCESS_COUNT++)) || true
      return 0
    else
      echo -e "${RED}✗ Failed (HTTP $http_code)${NC}"
      echo "Response: $body" | head -c 200
      ((ERROR_COUNT++)) || true
      return 1
    fi
  fi
}

***REMOVED*** Scenario 1: Successful request with minimal prompt
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Scenario 1: Successful Assistant Request"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

payload_success=$(cat <<'EOF'
{
  "items": [{
    "itemId": "test-001",
    "title": "Vintage Camera",
    "description": "35mm film camera",
    "category": "Cameras & Photo"
  }],
  "message": "Suggest a title",
  "assistantPrefs": {
    "language": "EN",
    "tone": "PROFESSIONAL"
  }
}
EOF
)

make_request "$payload_success" "Request: Suggest title for vintage camera"
sleep "$REQUEST_DELAY"

***REMOVED*** Scenario 2: Another successful request with different item
if [[ $(date +%s) -lt $END_TIME ]]; then
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "Scenario 2: Different Item Type"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

  payload_success2=$(cat <<'EOF'
{
  "items": [{
    "itemId": "test-002",
    "title": "Leather Jacket",
    "description": "Black leather, size M",
    "category": "Clothing"
  }],
  "message": "Improve the description",
  "assistantPrefs": {
    "language": "EN",
    "tone": "MARKETPLACE"
  }
}
EOF
)

  make_request "$payload_success2" "Request: Improve description for leather jacket"
  sleep "$REQUEST_DELAY"
fi

***REMOVED*** Scenario 3: Invalid input (empty message) - expect 400
if [[ $(date +%s) -lt $END_TIME ]]; then
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "Scenario 3: Invalid Request (Empty Message)"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

  payload_error=$(cat <<'EOF'
{
  "items": [{
    "itemId": "test-003",
    "title": "Test Item"
  }],
  "message": "",
  "assistantPrefs": {}
}
EOF
)

  make_request "$payload_error" "Request: Empty message (should fail validation)" "true"
  sleep "$REQUEST_DELAY"
fi

***REMOVED*** Scenario 4: Another successful request to get more metrics
if [[ $(date +%s) -lt $END_TIME ]]; then
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "Scenario 4: Multiple Items"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

  payload_multi=$(cat <<'EOF'
{
  "items": [{
    "itemId": "test-004",
    "title": "Wooden Chair",
    "description": "Oak dining chair"
  }],
  "message": "Generate full listing",
  "assistantPrefs": {
    "language": "EN",
    "verbosity": "DETAILED"
  }
}
EOF
)

  make_request "$payload_multi" "Request: Generate full listing for chair"
  sleep "$REQUEST_DELAY"
fi

***REMOVED*** Continue generating traffic until duration expires
REQUEST_NUM=5
while [[ $(date +%s) -lt $END_TIME ]]; do
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "Additional Request ***REMOVED***${REQUEST_NUM}"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

  payload_extra=$(cat <<EOF
{
  "items": [{
    "itemId": "test-$(printf "%03d" $REQUEST_NUM)",
    "title": "Test Item ${REQUEST_NUM}",
    "description": "Minimal test description"
  }],
  "message": "Brief title suggestion",
  "assistantPrefs": {
    "language": "EN",
    "tone": "NEUTRAL"
  }
}
EOF
)

  make_request "$payload_extra" "Request ***REMOVED***${REQUEST_NUM}: Brief title" || true
  ((REQUEST_NUM++)) || true

  ***REMOVED*** Check if we have time for another request
  if [[ $(($(date +%s) + REQUEST_DELAY)) -lt $END_TIME ]]; then
    sleep "$REQUEST_DELAY"
  else
    break
  fi
done

***REMOVED*** Summary
CURRENT_TIME=$(date +%s)
ACTUAL_DURATION=$((CURRENT_TIME - START_TIME))

echo ""
echo "============================================================"
echo "Traffic Generation Complete"
echo "============================================================"
echo "End time: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo "Actual duration: ${ACTUAL_DURATION}s"
echo ""
echo -e "${GREEN}Successful requests: ${SUCCESS_COUNT}${NC}"
echo -e "${YELLOW}Error scenarios: ${ERROR_COUNT}${NC}"
echo "Total requests: $((SUCCESS_COUNT + ERROR_COUNT))"
echo ""
echo "Next steps:"
echo "1. Wait 60-90s for metrics to be scraped by Alloy"
echo "2. Query Mimir: curl 'http://localhost:9009/prometheus/api/v1/query?query=scanium_assistant_requests_total'"
echo "3. Open Grafana dashboard: 'Scanium - OpenAI Runtime'"
echo "============================================================"
