#!/bin/bash
# Prove mobile dashboard wiring: Synthetic test log → Loki → Dashboard queries work
# Usage: bash prove-mobile-dashboard-wiring.sh [loki_url]
#
# This script:
# 1. Emits a synthetic mobile test log to Loki
# 2. Verifies the log is queryable with mobile dashboard label selectors
# 3. Proves mobile dashboard panels would display data (if present)

set -euo pipefail

LOKI_URL="${1:-http://127.0.0.1:3100}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "[prove-mobile-wiring] ================================================"
echo "[prove-mobile-wiring] Proving Mobile Dashboard Wiring"
echo "[prove-mobile-wiring] ================================================"

# Step 1: Emit synthetic mobile log
echo "[prove-mobile-wiring] Step 1: Emitting synthetic mobile test log..."
bash "$SCRIPT_DIR/emit-mobile-test-log.sh" "$LOKI_URL" || {
  echo "[prove-mobile-wiring] ❌ Failed to emit test log"
  exit 1
}

# Step 2: Query with dashboard label selectors
echo "[prove-mobile-wiring] Step 2: Querying with mobile dashboard selectors..."

# This matches the query pattern from mobile-app-health.json:
# {source="scanium-mobile", platform=~"$platform", build_type=~"$build_type", app_version=~"$app_version"}
MOBILE_QUERY='{source="scanium-mobile",platform="android",build_type="debug",app_version="test"}'

QUERY_RESULT=$(curl -sf -G "$LOKI_URL/loki/api/v1/query" \
  --data-urlencode "query=$MOBILE_QUERY" \
  --data-urlencode "limit=5" || echo '{"data":{"result":[]}}')

LOG_COUNT=$(echo "$QUERY_RESULT" | grep -o '"result":\[[^]]*\]' | grep -c '"stream"' || echo 0)

if [ "$LOG_COUNT" -gt 0 ]; then
  echo "[prove-mobile-wiring] ✅ Mobile logs queryable with dashboard selectors"
else
  echo "[prove-mobile-wiring] ❌ Mobile logs not found with dashboard selectors"
  echo "[prove-mobile-wiring] Query: $MOBILE_QUERY"
  echo "[prove-mobile-wiring] Result: $QUERY_RESULT"
  exit 1
fi

# Step 3: Verify event_name label (used in dashboard aggregations)
echo "[prove-mobile-wiring] Step 3: Verifying event_name label..."
EVENT_QUERY='{source="scanium-mobile",event_name="monitor_smoke"}'

EVENT_RESULT=$(curl -sf -G "$LOKI_URL/loki/api/v1/query" \
  --data-urlencode "query=$EVENT_QUERY" \
  --data-urlencode "limit=1" || echo '{"data":{"result":[]}}')

EVENT_COUNT=$(echo "$EVENT_RESULT" | grep -c '"event_name":"monitor_smoke"' || echo 0)

if [ "$EVENT_COUNT" -gt 0 ]; then
  echo "[prove-mobile-wiring] ✅ event_name label present and queryable"
else
  echo "[prove-mobile-wiring] ❌ event_name label not found"
  exit 1
fi

echo "[prove-mobile-wiring] ================================================"
echo "[prove-mobile-wiring] ✅ Mobile Dashboard Wiring PASSED"
echo "[prove-mobile-wiring] ================================================"
echo "[prove-mobile-wiring] Note: This proves the dashboard can query mobile"
echo "[prove-mobile-wiring] telemetry. For live app data, mobile OTLP ingestion"
echo "[prove-mobile-wiring] must be configured in the app."
exit 0
