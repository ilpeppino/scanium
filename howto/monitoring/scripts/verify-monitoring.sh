#!/usr/bin/env bash
set -euo pipefail

##############################################################################
# verify-monitoring.sh
#
# Verifies the Scanium monitoring stack is operational.
# Checks: Grafana, Mimir (metrics), Tempo (traces), Loki (logs).
#
# Usage:
#   bash scripts/monitoring/verify-monitoring.sh
#
# Exit codes:
#   0 - All critical checks passed (warnings OK)
#   1 - One or more critical checks failed
##############################################################################

DOCKER="/usr/local/bin/docker"
FAIL_COUNT=0
WARN_COUNT=0

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

print_status() {
  local status=$1
  local message=$2

  case "$status" in
    OK)
      echo -e "${GREEN}✓${NC} $message"
      ;;
    FAIL)
      echo -e "${RED}✗${NC} $message"
      FAIL_COUNT=$((FAIL_COUNT + 1))
      ;;
    WARN)
      echo -e "${YELLOW}⚠${NC} $message"
      WARN_COUNT=$((WARN_COUNT + 1))
      ;;
  esac
}

echo "════════════════════════════════════════════════════════════"
echo "Scanium Monitoring Stack Verification"
echo "════════════════════════════════════════════════════════════"
echo ""

##############################################################################
# A) GRAFANA CHECKS
##############################################################################
echo "[1/4] Grafana Health..."

# Check if container is running
if ! $DOCKER ps --format '{{.Names}}' | grep -q '^scanium-grafana$'; then
  print_status FAIL "Grafana: Container not running"
else
  # Check HTTP endpoint
  if $DOCKER exec scanium-grafana wget -q -O- --timeout=5 http://localhost:3000/api/health 2>/dev/null | grep -q '"database"'; then
    print_status OK "Grafana: API healthy"

    # Check datasources (optional - requires API access)
    DATASOURCES=$($DOCKER exec scanium-grafana wget -q -O- --timeout=5 http://localhost:3000/api/datasources 2>/dev/null || echo "[]")

    MIMIR_DS=$(echo "$DATASOURCES" | grep -c '"name":"Mimir"' || echo "0")
    LOKI_DS=$(echo "$DATASOURCES" | grep -c '"name":"Loki"' || echo "0")
    TEMPO_DS=$(echo "$DATASOURCES" | grep -c '"name":"Tempo"' || echo "0")

    if [ "$MIMIR_DS" -gt 0 ] && [ "$LOKI_DS" -gt 0 ] && [ "$TEMPO_DS" -gt 0 ]; then
      print_status OK "Grafana: All datasources configured (Mimir, Loki, Tempo)"
    else
      print_status WARN "Grafana: Some datasources may be missing (found: Mimir=$MIMIR_DS, Loki=$LOKI_DS, Tempo=$TEMPO_DS)"
    fi
  else
    print_status FAIL "Grafana: API not responding"
  fi
fi
echo ""

##############################################################################
# B) MIMIR CHECKS (METRICS)
##############################################################################
echo "[2/4] Mimir Metrics..."

if ! $DOCKER ps --format '{{.Names}}' | grep -q '^scanium-mimir$'; then
  print_status FAIL "Mimir: Container not running"
else
  # Check ready endpoint
  if $DOCKER exec scanium-mimir wget -q -O- --timeout=5 http://localhost:9009/ready 2>/dev/null | grep -q 'ready'; then
    print_status OK "Mimir: Ready endpoint OK"

    # Query pipeline metrics (up{source="pipeline"})
    PIPELINE_QUERY='up{source="pipeline"}'
    PIPELINE_RESULT=$($DOCKER exec scanium-mimir wget -q -O- --timeout=10 "http://localhost:9009/prometheus/api/v1/query?query=${PIPELINE_QUERY}" 2>/dev/null || echo "{}")

    if echo "$PIPELINE_RESULT" | grep -q '"status":"success"'; then
      PIPELINE_COUNT=$(echo "$PIPELINE_RESULT" | grep -o '"result":\[[^]]*\]' | grep -o '\[' | wc -l | tr -d ' ')
      if [ "$PIPELINE_COUNT" -gt 0 ]; then
        print_status OK "Mimir: Pipeline metrics present (up{source=\"pipeline\"})"
      else
        print_status WARN "Mimir: No pipeline metrics found (up{source=\"pipeline\"})"
      fi
    else
      print_status WARN "Mimir: Query failed for pipeline metrics"
    fi

    # Query backend metrics (up{job="scanium-backend"})
    BACKEND_QUERY='up{job="scanium-backend"}'
    BACKEND_RESULT=$($DOCKER exec scanium-mimir wget -q -O- --timeout=10 "http://localhost:9009/prometheus/api/v1/query?query=${BACKEND_QUERY}" 2>/dev/null || echo "{}")

    if echo "$BACKEND_RESULT" | grep -q '"status":"success"'; then
      BACKEND_COUNT=$(echo "$BACKEND_RESULT" | grep -o '"__name__":"up"' | wc -l | tr -d ' ')
      if [ "$BACKEND_COUNT" -gt 0 ]; then
        print_status OK "Mimir: Backend metrics present (up{job=\"scanium-backend\"})"
      else
        print_status WARN "Mimir: No backend metrics found (up{job=\"scanium-backend\"})"
      fi
    else
      print_status WARN "Mimir: Query failed for backend metrics"
    fi
  else
    print_status FAIL "Mimir: Ready endpoint not responding"
  fi
fi
echo ""

##############################################################################
# C) TEMPO CHECKS (TRACES)
##############################################################################
echo "[3/4] Tempo Traces..."

if ! $DOCKER ps --format '{{.Names}}' | grep -q '^scanium-tempo$'; then
  print_status FAIL "Tempo: Container not running"
else
  # Check ready endpoint
  if $DOCKER exec scanium-tempo wget -q -O- --timeout=5 http://localhost:3200/ready 2>/dev/null | grep -q 'ready'; then
    print_status OK "Tempo: Ready endpoint OK"

    # Query services
    SERVICES=$($DOCKER exec scanium-tempo wget -q -O- --timeout=10 http://localhost:3200/api/search/tags 2>/dev/null || echo "{}")

    if echo "$SERVICES" | grep -q '"tagNames"'; then
      print_status OK "Tempo: API responding"
      # Note: Empty services is expected if no traces have been sent
      print_status WARN "Tempo: No traffic validation (empty trace store is normal initially)"
    else
      print_status WARN "Tempo: API may not be fully ready"
    fi
  else
    print_status FAIL "Tempo: Ready endpoint not responding"
  fi
fi
echo ""

##############################################################################
# D) LOKI CHECKS (LOGS)
##############################################################################
echo "[4/4] Loki Logs..."

if ! $DOCKER ps --format '{{.Names}}' | grep -q '^scanium-loki$'; then
  print_status FAIL "Loki: Container not running"
else
  # Check ready endpoint
  if $DOCKER exec scanium-loki wget -q -O- --timeout=5 http://localhost:3100/ready 2>/dev/null | grep -q 'ready'; then
    print_status OK "Loki: Ready endpoint OK"

    # Query labels
    LABELS=$($DOCKER exec scanium-loki wget -q -O- --timeout=10 http://localhost:3100/loki/api/v1/labels 2>/dev/null || echo "{}")

    if echo "$LABELS" | grep -q '"status":"success"'; then
      LABEL_COUNT=$(echo "$LABELS" | grep -o '"data":\[[^]]*\]' | tr ',' '\n' | grep -c '"' || echo "0")
      if [ "$LABEL_COUNT" -gt 0 ]; then
        print_status OK "Loki: Labels present ($LABEL_COUNT labels found)"
      else
        print_status WARN "Loki: No labels found (logs ingestion may not be working)"
      fi
    else
      print_status WARN "Loki: Labels query failed"
    fi
  else
    print_status FAIL "Loki: Ready endpoint not responding"
  fi
fi
echo ""

##############################################################################
# SUMMARY
##############################################################################
echo "════════════════════════════════════════════════════════════"
echo "Verification Summary"
echo "════════════════════════════════════════════════════════════"

if [ $FAIL_COUNT -eq 0 ]; then
  if [ $WARN_COUNT -eq 0 ]; then
    echo -e "${GREEN}✓ ALL CHECKS PASSED${NC}"
    echo "  - Grafana: OK"
    echo "  - Mimir (Metrics): OK"
    echo "  - Tempo (Traces): OK"
    echo "  - Loki (Logs): OK"
    exit 0
  else
    echo -e "${YELLOW}⚠ PASSED WITH WARNINGS${NC}"
    echo "  - $WARN_COUNT warning(s) detected"
    echo "  - All critical systems operational"
    echo "  - Review warnings above for non-critical issues"
    exit 0
  fi
else
  echo -e "${RED}✗ VERIFICATION FAILED${NC}"
  echo "  - $FAIL_COUNT critical failure(s)"
  echo "  - $WARN_COUNT warning(s)"
  echo "  - Review failures above and check container logs"
  exit 1
fi
