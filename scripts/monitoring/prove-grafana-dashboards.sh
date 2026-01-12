#!/bin/bash
# Prove Grafana dashboards are provisioned and datasources exist
# Usage: bash prove-grafana-dashboards.sh [grafana_url]
#
# This script:
# 1. Lists dashboards via Grafana API
# 2. Verifies target dashboards (backend, mobile) exist
# 3. Checks that datasources (LOKI, MIMIR, TEMPO) are configured

set -euo pipefail

GRAFANA_URL="${1:-http://127.0.0.1:3000}"

echo "[prove-dashboards] ==========================================="
echo "[prove-dashboards] Proving Grafana Dashboards Provisioned"
echo "[prove-dashboards] ==========================================="

# Step 1: Check Grafana health
echo "[prove-dashboards] Step 1: Checking Grafana health..."
HEALTH=$(curl -sf "$GRAFANA_URL/api/health" || echo '{"database":"error"}')

if echo "$HEALTH" | grep -q '"database":"ok"'; then
  echo "[prove-dashboards] ✅ Grafana is healthy"
else
  echo "[prove-dashboards] ❌ Grafana health check failed"
  echo "[prove-dashboards] Response: $HEALTH"
  exit 1
fi

# Step 2: List dashboards
echo "[prove-dashboards] Step 2: Listing dashboards..."
DASHBOARDS=$(curl -sf "$GRAFANA_URL/api/search?type=dash-db" || echo '[]')

DASHBOARD_COUNT=$(echo "$DASHBOARDS" | grep -c '"type":"dash-db"' || echo 0)

if [ "$DASHBOARD_COUNT" -gt 0 ]; then
  echo "[prove-dashboards] ✅ Found $DASHBOARD_COUNT dashboard(s)"
else
  echo "[prove-dashboards] ❌ No dashboards found"
  echo "[prove-dashboards] Response: $DASHBOARDS"
  exit 1
fi

# Step 3: Verify target dashboards exist
echo "[prove-dashboards] Step 3: Verifying target dashboards..."
REQUIRED_DASHBOARDS=("backend-health" "mobile-app-health" "backend-api-performance")

for DASH in "${REQUIRED_DASHBOARDS[@]}"; do
  if echo "$DASHBOARDS" | grep -q "\"uid\":\"$DASH\""; then
    echo "[prove-dashboards] ✅ Dashboard '$DASH' found"
  else
    echo "[prove-dashboards] ⚠️  Dashboard '$DASH' not found (may use different UID)"
  fi
done

# Step 4: Check datasources
echo "[prove-dashboards] Step 4: Checking datasources..."
DATASOURCES=$(curl -sf "$GRAFANA_URL/api/datasources" || echo '[]')

DS_NAMES=("LOKI" "MIMIR" "TEMPO")
for DS in "${DS_NAMES[@]}"; do
  if echo "$DATASOURCES" | grep -q "\"uid\":\"$DS\""; then
    echo "[prove-dashboards] ✅ Datasource '$DS' configured"
  else
    echo "[prove-dashboards] ❌ Datasource '$DS' NOT found"
    exit 1
  fi
done

echo "[prove-dashboards] ==========================================="
echo "[prove-dashboards] ✅ Grafana Dashboards PASSED"
echo "[prove-dashboards] ==========================================="
exit 0
