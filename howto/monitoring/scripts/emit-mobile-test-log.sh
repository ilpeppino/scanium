***REMOVED***!/bin/bash
***REMOVED*** Emit synthetic mobile telemetry log to Loki for dashboard wiring validation
***REMOVED*** Usage: bash emit-mobile-test-log.sh [loki_url]
***REMOVED***
***REMOVED*** This script pushes a single test log entry to Loki with mobile-app labels.
***REMOVED*** It's used to validate that mobile dashboards can query and display data correctly,
***REMOVED*** without requiring a live mobile app session.

set -euo pipefail

LOKI_URL="${1:-http://127.0.0.1:3100}"
TIMESTAMP_NS=$(date +%s%N)

***REMOVED*** Log entry with mobile telemetry structure
LOG_ENTRY='{
  "source": "scanium-mobile",
  "event_name": "monitor_smoke",
  "platform": "android",
  "app_version": "test",
  "build_type": "debug",
  "session_id": "test-session-synthetic",
  "timestamp_ms": '$(date +%s000)',
  "message": "Synthetic test log for mobile dashboard validation"
}'

***REMOVED*** Loki push API payload
***REMOVED*** Labels must match what mobile dashboards query:
***REMOVED*** source="scanium-mobile", platform, app_version, build_type, event_name
PAYLOAD='{
  "streams": [
    {
      "stream": {
        "source": "scanium-mobile",
        "env": "dev",
        "platform": "android",
        "app_version": "test",
        "build_type": "debug",
        "event_name": "monitor_smoke"
      },
      "values": [
        ["'$TIMESTAMP_NS'", "'"$(echo "$LOG_ENTRY" | tr -d '\n' | sed 's/"/\\"/g')"'"]
      ]
    }
  ]
}'

echo "[emit-mobile-test-log] Pushing synthetic mobile log to Loki..."
echo "[emit-mobile-test-log] Target: $LOKI_URL/loki/api/v1/push"

HTTP_CODE=$(curl -s -w "%{http_code}" -o /dev/null -X POST "$LOKI_URL/loki/api/v1/push" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD")

if [ "$HTTP_CODE" = "204" ] || [ "$HTTP_CODE" = "200" ]; then
  echo "[emit-mobile-test-log] ✅ Synthetic mobile log pushed successfully (HTTP $HTTP_CODE)"
else
  echo "[emit-mobile-test-log] ❌ Failed to push log (HTTP $HTTP_CODE)"
  exit 1
fi

***REMOVED*** Verify the log is queryable
echo "[emit-mobile-test-log] Verifying log is queryable..."
sleep 2  ***REMOVED*** Give Loki a moment to ingest

QUERY_RESULT=$(curl -sf -G "$LOKI_URL/loki/api/v1/query" \
  --data-urlencode 'query={source="scanium-mobile",event_name="monitor_smoke"}' \
  --data-urlencode 'limit=1')

COUNT=$(echo "$QUERY_RESULT" | grep -o '"result":\[[^]]*\]' | grep -c '"stream"' || echo 0)

if [ "$COUNT" -gt 0 ]; then
  echo "[emit-mobile-test-log] ✅ Test log is queryable in Loki"
  exit 0
else
  echo "[emit-mobile-test-log] ❌ Test log not found in Loki query results"
  echo "[emit-mobile-test-log] Query result: $QUERY_RESULT"
  exit 1
fi
