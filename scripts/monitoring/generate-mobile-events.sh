***REMOVED***!/usr/bin/env bash
set -euo pipefail

***REMOVED*** Generate synthetic mobile telemetry events for testing
***REMOVED*** Usage: ./generate-mobile-events.sh [backend_url]
***REMOVED***
***REMOVED*** This script sends a batch of synthetic mobile events to the backend
***REMOVED*** for testing the mobile telemetry pipeline (Backend → Loki → Grafana)

BACKEND_URL="${1:-http://localhost:8080}"
ENDPOINT="$BACKEND_URL/v1/telemetry/mobile"
TIMESTAMP=$(date +%s)000  ***REMOVED*** Current timestamp in milliseconds

echo "═══════════════════════════════════════"
echo "Generating Synthetic Mobile Events"
echo "═══════════════════════════════════════"
echo "Backend: $BACKEND_URL"
echo "Endpoint: $ENDPOINT"
echo "Timestamp: $(date -Iseconds)"
echo "═══════════════════════════════════════"
echo

***REMOVED*** Generate batch payload with diverse events
PAYLOAD=$(cat <<EOF
{
  "events": [
    {
      "event_name": "app_launch",
      "platform": "android",
      "app_version": "1.0.0",
      "build_type": "dev",
      "timestamp_ms": $TIMESTAMP,
      "session_id": "test-session-001",
      "result": "ok",
      "attributes": {
        "launch_type": "cold_start"
      }
    },
    {
      "event_name": "scan_started",
      "platform": "android",
      "app_version": "1.0.0",
      "build_type": "dev",
      "timestamp_ms": $((TIMESTAMP + 1000)),
      "session_id": "test-session-001",
      "result": "ok",
      "attributes": {
        "scan_source": "camera"
      }
    },
    {
      "event_name": "scan_completed",
      "platform": "android",
      "app_version": "1.0.0",
      "build_type": "dev",
      "timestamp_ms": $((TIMESTAMP + 3000)),
      "session_id": "test-session-001",
      "result": "ok",
      "latency_ms": 2000,
      "attributes": {
        "item_count": 3
      }
    },
    {
      "event_name": "assist_clicked",
      "platform": "android",
      "app_version": "1.0.0",
      "build_type": "dev",
      "timestamp_ms": $((TIMESTAMP + 4000)),
      "session_id": "test-session-001",
      "result": "ok",
      "attributes": {
        "context": "scan_result"
      }
    },
    {
      "event_name": "share_started",
      "platform": "android",
      "app_version": "1.0.0",
      "build_type": "dev",
      "timestamp_ms": $((TIMESTAMP + 5000)),
      "session_id": "test-session-001",
      "result": "ok",
      "attributes": {
        "share_type": "text"
      }
    },
    {
      "event_name": "error_shown",
      "platform": "android",
      "app_version": "1.0.0",
      "build_type": "dev",
      "timestamp_ms": $((TIMESTAMP + 6000)),
      "session_id": "test-session-002",
      "result": "fail",
      "error_code": "NETWORK_ERROR",
      "attributes": {
        "error_category": "network",
        "is_recoverable": true
      }
    },
    {
      "event_name": "crash_marker",
      "platform": "android",
      "app_version": "1.0.0",
      "build_type": "dev",
      "timestamp_ms": $((TIMESTAMP + 7000)),
      "session_id": "test-session-002",
      "result": "fail",
      "error_code": "UNCAUGHT_EXCEPTION",
      "attributes": {
        "crash_type": "uncaught_exception"
      }
    },
    {
      "event_name": "app_launch",
      "platform": "android",
      "app_version": "1.1.0",
      "build_type": "beta",
      "timestamp_ms": $((TIMESTAMP + 8000)),
      "session_id": "test-session-003",
      "result": "ok",
      "attributes": {
        "launch_type": "warm_start"
      }
    },
    {
      "event_name": "scan_started",
      "platform": "android",
      "app_version": "1.1.0",
      "build_type": "beta",
      "timestamp_ms": $((TIMESTAMP + 9000)),
      "session_id": "test-session-003",
      "result": "ok",
      "attributes": {
        "scan_source": "gallery"
      }
    },
    {
      "event_name": "scan_completed",
      "platform": "android",
      "app_version": "1.1.0",
      "build_type": "beta",
      "timestamp_ms": $((TIMESTAMP + 10000)),
      "session_id": "test-session-003",
      "result": "ok",
      "latency_ms": 1000,
      "attributes": {
        "item_count": 1
      }
    }
  ]
}
EOF
)

echo "📤 Sending batch of 10 events..."
echo

***REMOVED*** Submit events to backend
RESPONSE=$(curl -sf -X POST "$ENDPOINT" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD" 2>&1) || {
  echo "❌ Failed to submit events"
  echo "Response: $RESPONSE"
  exit 1
}

echo "✅ Events submitted successfully"
echo "Response: $RESPONSE"
echo
echo "═══════════════════════════════════════"
echo "Summary:"
echo "  Total events: 10"
echo "  Event types: app_launch (2), scan_started (2), scan_completed (2),"
echo "               assist_clicked (1), share_started (1), error_shown (1), crash_marker (1)"
echo "  Platforms: android"
echo "  Versions: 1.0.0 (dev), 1.1.0 (beta)"
echo "  Sessions: 3 unique"
echo "  Timestamp: $(date -Iseconds)"
echo "═══════════════════════════════════════"
echo
echo "💡 Next steps:"
echo "  1. Wait 30-60 seconds for logs to flow through Alloy to Loki"
echo "  2. Query Loki: curl 'http://localhost:3100/loki/api/v1/query' --data-urlencode 'query={source=\"scanium-mobile\"}'"
echo "  3. Check Grafana dashboard: Scanium - Mobile App Health"
