***REMOVED***!/bin/bash
***REMOVED*** Verify Mobile Telemetry End-to-End
***REMOVED*** Usage: ./verify-mobile-telemetry.sh

echo "🚀 Sending test telemetry event..."

TEST_ID="test-$(date +%s)"
PAYLOAD="{\"event_name\":\"test.verification\",\"platform\":\"android\",\"app_version\":\"0.0.0-test\",\"env\":\"dev\",\"properties\":{\"test_id\":\"$TEST_ID\"}}"

***REMOVED*** Send to backend (assuming running on localhost:8080 on NAS or accessible via port forwarding)
***REMOVED*** We try localhost:8080 first
TARGET_URL="http://localhost:8080/v1/telemetry/mobile"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$TARGET_URL" -H "Content-Type: application/json" -d "$PAYLOAD")

if [ "$HTTP_CODE" != "202" ]; then
  echo "❌ Failed to send event. HTTP Code: $HTTP_CODE"
  echo "Ensure Backend is running on port 8080."
  exit 1
fi

echo "✅ Event sent. Waiting 5s for ingestion..."
sleep 5

***REMOVED*** Query Loki
***REMOVED*** Assuming Loki is on localhost:3100
LOKI_URL="http://localhost:3100/loki/api/v1/query_range"
QUERY="{source=\"scanium-mobile\"} |~ \"$TEST_ID\""

echo "🔎 Querying Loki: $QUERY"

RESPONSE=$(curl -s -G --data-urlencode "query=$QUERY" --data-urlencode "limit=1" "$LOKI_URL")

***REMOVED*** Check if result contains the test ID
if echo "$RESPONSE" | grep -q "$TEST_ID"; then
  echo "✅ Found test event in Loki!"
  exit 0
else
  echo "❌ Test event not found in Loki."
  echo "Response: $RESPONSE"
  exit 1
fi
