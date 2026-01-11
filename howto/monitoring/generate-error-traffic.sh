#!/usr/bin/env bash
# Generate minimal error traffic for monitoring validation
# Runs for 90 seconds at ~2 rps total
# Generates 200s, 400s, 401s, 404s for metrics verification

set -euo pipefail

BACKEND_URL="${1:-http://172.23.0.5:8080}"
DURATION_SECONDS=90
INTERVAL=0.5  # 2 requests per second total

echo "=== Scanium Error Traffic Generator ==="
echo "Backend: $BACKEND_URL"
echo "Duration: ${DURATION_SECONDS}s"
echo "Rate: ~2 rps total"
echo "Starting: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo

START_TIME=$(date +%s)
END_TIME=$((START_TIME + DURATION_SECONDS))
REQUEST_COUNT=0

# Traffic generation loop
while [ "$(date +%s)" -lt "$END_TIME" ]; do
  CYCLE=$((REQUEST_COUNT % 8))

  case $CYCLE in
    0)
      # 200 OK - root endpoint
      curl -s -o /dev/null -w "200 OK: %{http_code}\n" "$BACKEND_URL/" &
      ;;
    1)
      # 404 Not Found - nonexistent route
      curl -s -o /dev/null -w "404 Not Found: %{http_code}\n" "$BACKEND_URL/this-does-not-exist" &
      ;;
    2)
      # 401 Unauthorized - protected endpoint without auth
      curl -s -o /dev/null -w "401 Unauthorized: %{http_code}\n" "$BACKEND_URL/v1/assist/chat" &
      ;;
    3)
      # 404 Not Found - another nonexistent route
      curl -s -o /dev/null -w "404 Not Found: %{http_code}\n" "$BACKEND_URL/v1/nonexistent-module" &
      ;;
    4)
      # 200 OK - health endpoint
      curl -s -o /dev/null -w "200 OK (health): %{http_code}\n" "$BACKEND_URL/health" &
      ;;
    5)
      # 404 Not Found - deep nonexistent path
      curl -s -o /dev/null -w "404 Not Found: %{http_code}\n" "$BACKEND_URL/v1/items/missing/route" &
      ;;
    6)
      # 401 Unauthorized - protected classifier endpoint
      curl -s -o /dev/null -w "401 Unauthorized: %{http_code}\n" "$BACKEND_URL/v1/classify" &
      ;;
    7)
      # 200 OK - config endpoint
      curl -s -o /dev/null -w "200 OK (config): %{http_code}\n" "$BACKEND_URL/v1/config" &
      ;;
  esac

  REQUEST_COUNT=$((REQUEST_COUNT + 1))
  sleep "$INTERVAL"
done

# Wait for background jobs
wait

END_TIMESTAMP=$(date -u +%Y-%m-%dT%H:%M:%SZ)

echo
echo "=== Traffic Generation Complete ==="
echo "End time: $END_TIMESTAMP"
echo "Total requests: $REQUEST_COUNT"
echo "Expected distribution:"
echo "  - 200 OK: ~37.5% (~$((REQUEST_COUNT * 3 / 8)) requests)"
echo "  - 401 Unauthorized: ~25% (~$((REQUEST_COUNT / 4)) requests)"
echo "  - 404 Not Found: ~37.5% (~$((REQUEST_COUNT * 3 / 8)) requests)"
echo
echo "Wait 90 seconds for metrics scrape, then query:"
echo "  sum(rate(scanium_http_requests_total{status_code=~\"4..\"}[5m]))"
echo "  sum(rate(scanium_http_requests_total{status_code=~\"2..\"}[5m]))"
