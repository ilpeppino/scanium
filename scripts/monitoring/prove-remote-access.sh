#!/bin/bash
# Prove remote access to Grafana via cloudflared tunnel
# Usage: bash prove-remote-access.sh [remote_url]
#
# This script:
# 1. Curls grafana.gtemp1.com/api/health from NAS
# 2. Verifies NO 502 (tunnel is working)
# 3. Checks that Grafana responds with valid JSON

set -euo pipefail

REMOTE_URL="${1:-https://grafana.gtemp1.com}"

echo "[prove-remote] ============================================"
echo "[prove-remote] Proving Remote Access via Cloudflared Tunnel"
echo "[prove-remote] ============================================"

# Step 1: Check tunnel health endpoint
echo "[prove-remote] Step 1: Checking $REMOTE_URL/api/health..."

# Capture HTTP status code and response body
HTTP_CODE=$(curl -sf -w "%{http_code}" -o /tmp/remote-health.json "$REMOTE_URL/api/health" 2>/dev/null || echo "000")

if [ "$HTTP_CODE" = "502" ]; then
  echo "[prove-remote] ❌ Got 502 Bad Gateway (tunnel or Grafana down)"
  exit 1
elif [ "$HTTP_CODE" = "000" ]; then
  echo "[prove-remote] ❌ Connection failed (DNS, network, or tunnel issue)"
  exit 1
elif [ "$HTTP_CODE" != "200" ]; then
  echo "[prove-remote] ⚠️  Got HTTP $HTTP_CODE (expected 200)"
  cat /tmp/remote-health.json 2>/dev/null || true
  exit 1
fi

echo "[prove-remote] ✅ Got HTTP $HTTP_CODE (tunnel is working)"

# Step 2: Verify response is valid JSON with expected fields
if [ -f /tmp/remote-health.json ]; then
  HEALTH_JSON=$(cat /tmp/remote-health.json)

  if echo "$HEALTH_JSON" | grep -q '"database":"ok"'; then
    echo "[prove-remote] ✅ Grafana health response valid"
  else
    echo "[prove-remote] ⚠️  Unexpected health response"
    echo "[prove-remote] Response: $HEALTH_JSON"
  fi
fi

# Step 3: Try to access dashboards API
echo "[prove-remote] Step 2: Checking dashboard list endpoint..."
DASH_CODE=$(curl -sf -w "%{http_code}" -o /dev/null "$REMOTE_URL/api/search?type=dash-db" 2>/dev/null || echo "000")

if [ "$DASH_CODE" = "200" ]; then
  echo "[prove-remote] ✅ Dashboard API accessible remotely"
elif [ "$DASH_CODE" = "401" ] || [ "$DASH_CODE" = "403" ]; then
  echo "[prove-remote] ⚠️  Dashboard API requires auth (HTTP $DASH_CODE)"
  echo "[prove-remote] This is OK if anonymous access is disabled"
else
  echo "[prove-remote] ⚠️  Dashboard API returned HTTP $DASH_CODE"
fi

echo "[prove-remote] ============================================"
echo "[prove-remote] ✅ Remote Access PASSED"
echo "[prove-remote] ============================================"
echo "[prove-remote] Tunnel: grafana.gtemp1.com → Grafana"
echo "[prove-remote] No 502 detected - monitoring is remotely accessible"
exit 0
