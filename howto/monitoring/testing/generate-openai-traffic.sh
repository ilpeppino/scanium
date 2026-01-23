#!/bin/bash
#
# Minimal OpenAI traffic generator for dashboard testing
# Generates controlled traffic to /assist/chat endpoint
#
# Usage:
#   ./scripts/monitoring/generate-openai-traffic.sh [BASE_URL]
#
# Environment:
#   SCANIUM_API_KEY - API key for backend (required)
#   BASE_URL - Backend URL (default: http://127.0.0.1:8080)

set -euo pipefail

BASE_URL="${1:-http://127.0.0.1:8080}"
API_KEY="${SCANIUM_API_KEY:-}"

if [ -z "$API_KEY" ]; then
  echo "ERROR: SCANIUM_API_KEY environment variable required"
  echo "Usage: SCANIUM_API_KEY=xxx ./scripts/monitoring/generate-openai-traffic.sh [BASE_URL]"
  exit 1
fi

echo "=== OpenAI Traffic Generator ==="
echo "Target: $BASE_URL"
echo "Start time: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo ""

# Warmup check
echo "[1/4] Checking backend availability..."
HEALTH_RESPONSE=$(curl -s "$BASE_URL/" | jq -r '.name // "unknown"')

if [ "$HEALTH_RESPONSE" == "unknown" ]; then
  echo "ERROR: Backend not available"
  exit 1
fi

echo "✓ Backend: $HEALTH_RESPONSE"
echo ""

# Generate valid traffic
echo "[2/4] Generating 3 valid requests (20s apart)..."
SUCCESS_COUNT=0

for i in {1..3}; do
  PAYLOAD=$(cat <<EOF
{
  "items": [{
    "itemId": "test-item-$i",
    "title": "Vintage Camera",
    "category": "Electronics",
    "attributes": [
      {"key": "brand", "value": "Canon", "source": "USER"}
    ]
  }],
  "message": "Help me write a marketplace listing for this vintage camera"
}
EOF
)

  echo -n "  Request $i... "
  RESPONSE=$(curl -s -X POST "$BASE_URL/v1/assist/chat" \
    -H "x-api-key: $API_KEY" \
    -H "Content-Type: application/json" \
    -d "$PAYLOAD")

  if echo "$RESPONSE" | jq -e '.reply' >/dev/null 2>&1; then
    echo "✓ success"
    ((SUCCESS_COUNT++))
  else
    echo "✗ failed"
    echo "$RESPONSE" | jq . 2>/dev/null || echo "$RESPONSE"
  fi

  [ $i -lt 3 ] && sleep 20
done

echo ""

# Generate one error
echo "[3/4] Generating 1 error request (invalid payload)..."
ERROR_COUNT=0

ERROR_PAYLOAD='{"items":[],"message":"x"}'
RESPONSE=$(curl -s -X POST "$BASE_URL/v1/assist/chat" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d "$ERROR_PAYLOAD")

if echo "$RESPONSE" | jq -e '.error.code' >/dev/null 2>&1; then
  echo "✓ Error triggered (expected)"
  ((ERROR_COUNT++))
else
  echo "✗ Unexpected response"
  echo "$RESPONSE" | jq . 2>/dev/null || echo "$RESPONSE"
fi

echo ""

# Summary
echo "[4/4] Summary"
echo "─────────────────────────────────"
echo "Success requests: $SUCCESS_COUNT"
echo "Error requests: $ERROR_COUNT"
echo "Total requests: $((SUCCESS_COUNT + ERROR_COUNT))"
echo "End time: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo ""
echo "✓ Traffic generation complete"
echo ""
echo "Wait 90 seconds for metrics to propagate to Mimir, then check dashboard."
