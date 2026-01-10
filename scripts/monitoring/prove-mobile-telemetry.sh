#!/bin/bash
#
# prove-mobile-telemetry.sh
# Verifies that mobile telemetry (OTLP logs) is reaching Loki
#
# Usage: ./scripts/monitoring/prove-mobile-telemetry.sh

set -euo pipefail

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "========================================="
echo "Mobile Telemetry Verification (OTLP)"
echo "========================================="
echo ""

# Configuration
LOKI_URL="http://127.0.0.1:3100"
QUERY='{source="scanium-mobile"}'
LOOKBACK_MINUTES=15

# Calculate time range (last 15 minutes)
END_TIME=$(date +%s)
START_TIME=$((END_TIME - LOOKBACK_MINUTES * 60))

echo "Querying Loki for mobile logs..."
echo "  Query: $QUERY"
echo "  Lookback: Last $LOOKBACK_MINUTES minutes"
echo ""

# Query Loki via SSH to NAS
RESULT=$(ssh nas "curl -s -G 'http://127.0.0.1:3100/loki/api/v1/query_range' \
  --data-urlencode 'query=$QUERY' \
  --data-urlencode 'start=${START_TIME}000000000' \
  --data-urlencode 'end=${END_TIME}000000000' \
  --data-urlencode 'limit=100'")

# Check if query succeeded
if echo "$RESULT" | jq -e '.status == "success"' > /dev/null 2>&1; then
  echo -e "${GREEN}✓ Loki query succeeded${NC}"
else
  echo -e "${RED}✗ Loki query failed${NC}"
  echo "$RESULT" | jq '.error' || echo "$RESULT"
  exit 1
fi

# Count total log entries
TOTAL_LOGS=$(echo "$RESULT" | jq '[.data.result[].values | length] | add // 0')
echo -e "${GREEN}✓ Found $TOTAL_LOGS mobile log entries${NC}"

if [ "$TOTAL_LOGS" -eq 0 ]; then
  echo ""
  echo -e "${RED}✗ No mobile telemetry logs found in last $LOOKBACK_MINUTES minutes${NC}"
  echo ""
  echo "Possible causes:"
  echo "  1. Mobile app hasn't been launched recently"
  echo "  2. OTLP endpoint not reachable from device (check network)"
  echo "  3. Alloy not running or not forwarding logs to Loki"
  echo ""
  echo "Debugging steps:"
  echo "  - Check Alloy logs: ssh nas 'docker logs --tail 50 scanium-alloy'"
  echo "  - Verify OTLP endpoint: curl http://192.168.178.45:4318/health"
  echo "  - Check mobile app logs for OTLP export errors"
  echo ""
  exit 1
fi

echo ""
echo "Event Summary (top 20 by frequency):"
echo "------------------------------------"

# Extract event_name values and count occurrences
echo "$RESULT" | jq -r '
  [.data.result[] | .stream.event_name // "unknown"]
  | group_by(.)
  | map({event: .[0], count: length})
  | sort_by(.count)
  | reverse
  | .[:20]
  | .[]
  | "\(.event): \(.count)"
'

echo ""
echo "Platform Distribution:"
echo "------------------------------------"

# Extract platform values and count occurrences
echo "$RESULT" | jq -r '
  [.data.result[] | .stream.platform // "unknown"]
  | group_by(.)
  | map({platform: .[0], count: length})
  | sort_by(.count)
  | reverse
  | .[]
  | "\(.platform): \(.count)"
'

echo ""
echo "App Versions:"
echo "------------------------------------"

# Extract app_version values (unique)
echo "$RESULT" | jq -r '
  [.data.result[] | .stream.app_version // "unknown"]
  | unique
  | .[]
'

echo ""
echo "Build Types:"
echo "------------------------------------"

# Extract build_type values and count occurrences
echo "$RESULT" | jq -r '
  [.data.result[] | .stream.build_type // "unknown"]
  | group_by(.)
  | map({build_type: .[0], count: length})
  | sort_by(.count)
  | reverse
  | .[]
  | "\(.build_type): \(.count)"
'

echo ""
echo "========================================="
echo -e "${GREEN}✓ Mobile telemetry verification PASSED${NC}"
echo "========================================="
echo ""
echo "Next steps:"
echo "  - Open Grafana: http://192.168.178.45:3000"
echo "  - Navigate to 'Scanium - Mobile App Health' dashboard"
echo "  - Verify panels populate with data"
echo ""

exit 0
