#!/usr/bin/env bash
set -euo pipefail

# Prove mobile telemetry pipeline is working end-to-end
# Usage: ./prove-mobile-telemetry.sh [backend_url] [loki_url] [mimir_url]
#
# This script:
# 1. Generates synthetic mobile events (unless SKIP_GENERATION=1)
# 2. Waits for logs to propagate through Alloy
# 3. Asserts Loki has mobile telemetry logs
# 4. Asserts Mimir has mobile telemetry metrics
# 5. Outputs PASS/FAIL with diagnostics

BACKEND_URL="${1:-http://localhost:8080}"
LOKI_URL="${2:-http://localhost:3100}"
MIMIR_URL="${3:-http://localhost:9009}"
SKIP_GENERATION="${SKIP_GENERATION:-0}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PASS=0
FAIL=0

echo "═══════════════════════════════════════════════════════════"
echo "Mobile Telemetry End-to-End Proof"
echo "═══════════════════════════════════════════════════════════"
echo "Backend:  $BACKEND_URL"
echo "Loki:     $LOKI_URL"
echo "Mimir:    $MIMIR_URL"
echo "═══════════════════════════════════════════════════════════"
echo

# Step 1: Generate events (unless skipped)
if [ "$SKIP_GENERATION" = "1" ]; then
  echo "⏭️  Skipping event generation (SKIP_GENERATION=1)"
  echo
else
  echo "📤 Step 1/4: Generating synthetic mobile events..."
  if bash "$SCRIPT_DIR/generate-mobile-events.sh" "$BACKEND_URL" > /dev/null 2>&1; then
    echo "✅ Events generated successfully"
    PASS=$((PASS + 1))
  else
    echo "❌ Failed to generate events"
    FAIL=$((FAIL + 1))
  fi
  echo

  echo "⏳ Waiting 45 seconds for logs to propagate (Backend → Docker logs → Alloy → Loki)..."
  sleep 45
  echo
fi

# Step 2: Assert Loki has mobile telemetry logs
echo "🔍 Step 2/4: Checking Loki for mobile telemetry logs..."
LOKI_QUERY='{source="scanium-mobile"}'
LOKI_START=$(date -u -d '10 minutes ago' +%s)000000000 2>/dev/null || LOKI_START=$(date -u -v-10M +%s)000000000
LOKI_END=$(date -u +%s)000000000

LOKI_RESPONSE=$(curl -sf -G "$LOKI_URL/loki/api/v1/query" \
  --data-urlencode "query=$LOKI_QUERY" \
  --data-urlencode "start=$LOKI_START" \
  --data-urlencode "end=$LOKI_END" \
  --data-urlencode "limit=100" 2>&1) || {
  echo "❌ Failed to query Loki"
  echo "Response: $LOKI_RESPONSE"
  FAIL=$((FAIL + 1))
  LOKI_COUNT=0
}

if [ -n "${LOKI_RESPONSE:-}" ]; then
  LOKI_COUNT=$(echo "$LOKI_RESPONSE" | grep -o '"result":\[' | wc -l || echo 0)
  LOKI_LINES=$(echo "$LOKI_RESPONSE" | grep -o '"totalLinesProcessed":[0-9]*' | sed 's/.*://g' || echo 0)

  if [ "$LOKI_LINES" -gt 0 ]; then
    echo "✅ Loki has mobile telemetry logs ($LOKI_LINES lines processed)"
    PASS=$((PASS + 1))
  else
    echo "❌ Loki has NO mobile telemetry logs in last 10 minutes"
    echo "Query: $LOKI_QUERY"
    echo "Response preview: $(echo "$LOKI_RESPONSE" | head -c 500)"
    FAIL=$((FAIL + 1))
  fi
fi
echo

# Step 3: Assert Mimir has mobile telemetry metrics
echo "📊 Step 3/4: Checking Mimir for mobile telemetry metrics..."
MIMIR_QUERY="scanium_mobile_events_total"

MIMIR_RESPONSE=$(curl -sf "$MIMIR_URL/prometheus/api/v1/query?query=$MIMIR_QUERY" 2>&1) || {
  echo "❌ Failed to query Mimir"
  echo "Response: $MIMIR_RESPONSE"
  FAIL=$((FAIL + 1))
  MIMIR_RESPONSE=""
}

if [ -n "${MIMIR_RESPONSE:-}" ]; then
  MIMIR_SERIES=$(echo "$MIMIR_RESPONSE" | grep -o '"result":\[[^]]*\]' | grep -o '"metric"' | wc -l || echo 0)

  if [ "$MIMIR_SERIES" -gt 0 ]; then
    echo "✅ Mimir has mobile telemetry metrics ($MIMIR_SERIES series)"
    PASS=$((PASS + 1))
  else
    echo "❌ Mimir has NO mobile telemetry metrics"
    echo "Query: $MIMIR_QUERY"
    echo "Response preview: $(echo "$MIMIR_RESPONSE" | head -c 500)"
    FAIL=$((FAIL + 1))
  fi
fi
echo

# Step 4: Optional - Check Grafana dashboard provisioning
echo "📈 Step 4/4: Checking Grafana dashboard (optional)..."
GRAFANA_URL="${GRAFANA_URL:-http://localhost:3000}"
GRAFANA_DASHBOARD_UID="scanium-mobile-app-health"

GRAFANA_RESPONSE=$(curl -sf "$GRAFANA_URL/api/dashboards/uid/$GRAFANA_DASHBOARD_UID" 2>&1) || {
  echo "⚠️  Could not verify Grafana dashboard (dashboard may not be accessible or provisioned yet)"
  echo "   This is OK if you haven't deployed to Grafana yet"
  GRAFANA_RESPONSE=""
}

if [ -n "${GRAFANA_RESPONSE:-}" ]; then
  if echo "$GRAFANA_RESPONSE" | grep -q "\"title\":\"Scanium - Mobile App Health\""; then
    echo "✅ Grafana dashboard 'Scanium - Mobile App Health' is provisioned"
    PASS=$((PASS + 1))
  else
    echo "⚠️  Grafana dashboard response unexpected"
  fi
else
  echo "⏭️  Skipping Grafana dashboard check"
fi
echo

# Summary
echo "═══════════════════════════════════════════════════════════"
echo "Results:"
echo "  ✅ Passed: $PASS"
echo "  ❌ Failed: $FAIL"
echo "═══════════════════════════════════════════════════════════"
echo

if [ "$FAIL" -eq 0 ]; then
  echo "🎉 PASS: Mobile telemetry pipeline is working"
  exit 0
else
  echo "💥 FAIL: Mobile telemetry pipeline has issues"
  echo
  echo "Troubleshooting:"
  echo "  1. Check backend logs: docker logs scanium-backend"
  echo "  2. Check Alloy logs: docker logs scanium-alloy"
  echo "  3. Check Loki ingestion: curl '$LOKI_URL/loki/api/v1/labels'"
  echo "  4. Check Alloy config: monitoring/alloy/alloy.hcl"
  echo "  5. Verify Alloy can reach backend: docker exec scanium-alloy ping scanium-backend"
  exit 1
fi
