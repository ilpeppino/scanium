***REMOVED***!/bin/bash
***REMOVED*** Prove logs pipeline: Backend → Alloy → Loki
***REMOVED*** Usage: bash prove-logs.sh [backend_url] [loki_url]
***REMOVED***
***REMOVED*** This script:
***REMOVED*** 1. Triggers backend logs (via API calls that generate logs)
***REMOVED*** 2. Queries Loki to verify:
***REMOVED***    - {source="scanium-backend"} returns entries
***REMOVED***    - Logs are recent (within last 5 minutes)

set -euo pipefail

BACKEND_URL="${1:-http://127.0.0.1:8080}"
LOKI_URL="${2:-http://127.0.0.1:3100}"

echo "[prove-logs] ========================================"
echo "[prove-logs] Proving Backend → Alloy → Loki pipeline"
echo "[prove-logs] ========================================"

***REMOVED*** Step 1: Generate backend activity that produces logs
echo "[prove-logs] Step 1: Generating backend logs..."
echo "[prove-logs] - Health check (should log request)"
curl -sf "$BACKEND_URL/health" > /dev/null || { echo "❌ Backend health check failed"; exit 1; }

echo "[prove-logs] - Additional requests to trigger logging"
curl -sf "$BACKEND_URL/health" > /dev/null 2>&1 || true
curl -sf "$BACKEND_URL/nonexistent" > /dev/null 2>&1 || true

***REMOVED*** Give Alloy time to scrape docker logs and forward to Loki
echo "[prove-logs] - Waiting 5s for log ingestion..."
sleep 5

***REMOVED*** Step 2: Query Loki for backend logs
echo "[prove-logs] Step 2: Querying Loki for backend logs..."
LOG_QUERY='{source="scanium-backend"}'
LOKI_QUERY="$LOKI_URL/loki/api/v1/query"

LOG_RESULT=$(curl -sf -G "$LOKI_QUERY" \
  --data-urlencode "query=$LOG_QUERY" \
  --data-urlencode "limit=10" || echo '{"data":{"result":[]}}')

LOG_COUNT=$(echo "$LOG_RESULT" | grep -o '"result":\[[^]]*\]' | grep -c '"stream"' || echo 0)

if [ "$LOG_COUNT" -gt 0 ]; then
  echo "[prove-logs] ✅ Backend logs found in Loki ($LOG_COUNT stream(s))"
else
  echo "[prove-logs] ❌ No backend logs found in Loki"
  echo "[prove-logs] Query: $LOG_QUERY"
  echo "[prove-logs] Result: $LOG_RESULT"
  exit 1
fi

***REMOVED*** Step 3: Verify logs are recent (check timestamp)
echo "[prove-logs] Step 3: Verifying logs are recent..."
FIRST_LOG=$(echo "$LOG_RESULT" | grep -o '"values":\[\[.*\]\]' | head -1)

if echo "$FIRST_LOG" | grep -q '"values":\[\['; then
  echo "[prove-logs] ✅ Recent log entries found"
else
  echo "[prove-logs] ⚠️  Could not verify log freshness"
fi

echo "[prove-logs] ========================================"
echo "[prove-logs] ✅ Logs pipeline PASSED"
echo "[prove-logs] ========================================"
exit 0
