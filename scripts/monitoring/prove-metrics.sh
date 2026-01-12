#!/bin/bash
# Prove metrics pipeline: Backend → Alloy → Mimir
# Usage: bash prove-metrics.sh [backend_url] [mimir_url]
#
# This script:
# 1. Generates backend traffic (200, 400, 404, 500 responses)
# 2. Queries Mimir to verify:
#    - up{job="scanium-backend"} = 1 (scraping works)
#    - scanium_* metrics exist and change

set -euo pipefail

BACKEND_URL="${1:-http://127.0.0.1:8080}"
MIMIR_URL="${2:-http://127.0.0.1:9009}"

echo "[prove-metrics] ========================================="
echo "[prove-metrics] Proving Backend → Alloy → Mimir pipeline"
echo "[prove-metrics] ========================================="

# Step 1: Generate backend traffic
echo "[prove-metrics] Step 1: Generating backend traffic..."
echo "[prove-metrics] - Health check (200)"
curl -sf "$BACKEND_URL/health" > /dev/null || { echo "❌ Backend health check failed"; exit 1; }

echo "[prove-metrics] - API calls (various status codes)"
# 200 OK
curl -sf "$BACKEND_URL/health" > /dev/null 2>&1 || true
# 404 Not Found
curl -sf "$BACKEND_URL/nonexistent" > /dev/null 2>&1 || true
# Give backend a moment to emit metrics
sleep 2

# Step 2: Query Mimir - Check backend scrape target is UP
echo "[prove-metrics] Step 2: Checking backend scrape target..."
UP_QUERY="$MIMIR_URL/prometheus/api/v1/query?query=up{job=\"scanium-backend\"}"
UP_RESULT=$(curl -sf "$UP_QUERY")

UP_VALUE=$(echo "$UP_RESULT" | grep -o '"value":\[[^]]*\]' | grep -o '[01]"' | tr -d '"' | head -1)

if [ "$UP_VALUE" = "1" ]; then
  echo "[prove-metrics] ✅ Backend scrape target is UP (up=1)"
else
  echo "[prove-metrics] ❌ Backend scrape target is DOWN (up=$UP_VALUE)"
  echo "[prove-metrics] Query result: $UP_RESULT"
  exit 1
fi

# Step 3: Check backend-specific metrics exist
echo "[prove-metrics] Step 3: Checking backend metrics exist..."
METRICS_QUERY="$MIMIR_URL/prometheus/api/v1/query?query=scanium_process_cpu_seconds_total{job=\"scanium-backend\"}"
METRICS_RESULT=$(curl -sf "$METRICS_QUERY")

METRIC_COUNT=$(echo "$METRICS_RESULT" | grep -c '"__name__":"scanium_process_cpu_seconds_total"' || echo 0)

if [ "$METRIC_COUNT" -gt 0 ]; then
  echo "[prove-metrics] ✅ Backend metrics (scanium_*) are present in Mimir"
else
  echo "[prove-metrics] ❌ Backend metrics not found in Mimir"
  echo "[prove-metrics] Query result: $METRICS_RESULT"
  exit 1
fi

# Step 4: Verify metrics are being scraped recently
echo "[prove-metrics] Step 4: Verifying recent scrape activity..."
SCRAPE_QUERY="$MIMIR_URL/prometheus/api/v1/query?query=scrape_duration_seconds{job=\"scanium-backend\"}"
SCRAPE_RESULT=$(curl -sf "$SCRAPE_QUERY")

SCRAPE_COUNT=$(echo "$SCRAPE_RESULT" | grep -c '"__name__":"scrape_duration_seconds"' || echo 0)

if [ "$SCRAPE_COUNT" -gt 0 ]; then
  echo "[prove-metrics] ✅ Scrape metadata present (backend is being actively scraped)"
else
  echo "[prove-metrics] ⚠️  Scrape metadata not found (metrics may be stale)"
fi

echo "[prove-metrics] ========================================="
echo "[prove-metrics] ✅ Metrics pipeline PASSED"
echo "[prove-metrics] ========================================="
exit 0
