#!/usr/bin/env bash
set -euo pipefail

##############################################################################
# deploy-monitoring-nas.sh
#
# Deploys the Scanium monitoring stack on NAS with verification gates.
# Includes preflight checks to prevent orphan container DNS issues.
#
# Usage:
#   bash scripts/monitoring/deploy-monitoring-nas.sh
#
# Requirements:
#   - Must run on NAS
#   - Docker and docker-compose in PATH
#   - Repo at /volume1/docker/scanium/repo
##############################################################################

REPO_ROOT="/volume1/docker/scanium/repo"
MONITORING_DIR="$REPO_ROOT/monitoring"
DOCKER="/usr/local/bin/docker"
DOCKER_COMPOSE="/usr/local/bin/docker-compose"

# Ensure docker is in PATH
export PATH="/usr/local/bin:$PATH"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "════════════════════════════════════════════════════════════"
echo "Scanium Monitoring Stack Deployment"
echo "════════════════════════════════════════════════════════════"
echo ""

##############################################################################
# PREFLIGHT: Detect orphan/duplicate containers
##############################################################################
echo "[PREFLIGHT] Checking for orphan containers..."

# Find all scanium monitoring containers (running or exited)
ALL_MONITORING=$($DOCKER ps -a --filter 'name=scanium-grafana' --filter 'name=scanium-mimir' --filter 'name=scanium-loki' --filter 'name=scanium-tempo' --filter 'name=scanium-alloy' --format '{{.Names}}\t{{.Status}}' || true)

if [ -n "$ALL_MONITORING" ]; then
  echo "Found monitoring containers:"
  echo "$ALL_MONITORING"
  echo ""

  # Count duplicates (multiple containers with same base name)
  DUPLICATES=$($DOCKER ps -a --filter 'name=scanium-grafana' --filter 'name=scanium-mimir' --filter 'name=scanium-loki' --filter 'name=scanium-tempo' --filter 'name=scanium-alloy' --format '{{.Names}}' | sort | uniq -d || true)

  if [ -n "$DUPLICATES" ]; then
    echo -e "${RED}✗ ERROR: Duplicate containers detected${NC}"
    echo "$DUPLICATES"
    echo ""
    echo "This can cause DNS resolution issues. Run:"
    echo "  cd $MONITORING_DIR && docker-compose down --remove-orphans"
    echo "  docker ps -a --filter 'name=scanium-' --format '{{.Names}}' | xargs docker rm -f"
    exit 1
  fi

  # Count exited containers
  EXITED=$($DOCKER ps -a --filter 'name=scanium-' --filter 'status=exited' --format '{{.Names}}' || true)
  if [ -n "$EXITED" ]; then
    echo -e "${YELLOW}⚠ WARNING: Found exited containers${NC}"
    echo "$EXITED"
    echo "These will be cleaned up during deployment."
    echo ""
  fi
else
  echo "✓ No existing monitoring containers found (clean slate)"
  echo ""
fi

##############################################################################
# Step 1: Update repository
##############################################################################
echo "[1/5] Updating repository from origin/main..."
cd "$REPO_ROOT"
git checkout main
git pull --rebase origin main
echo "✓ Repository updated"
echo ""

# Capture current commit
CURRENT_SHA=$(git rev-parse --short HEAD)
DEPLOY_TAG="monitoring-$(date -u +%Y.%m.%d)-$CURRENT_SHA"
echo "Deploy tag: $DEPLOY_TAG"
echo ""

##############################################################################
# Step 2: Stop and remove old stack (with orphan cleanup)
##############################################################################
echo "[2/5] Stopping monitoring stack..."
cd "$MONITORING_DIR"
$DOCKER_COMPOSE down --remove-orphans
echo "✓ Stack stopped and orphans removed"
echo ""

##############################################################################
# Step 3: Start monitoring stack
##############################################################################
echo "[3/5] Starting monitoring stack..."
$DOCKER_COMPOSE up -d
echo "✓ Stack started"
echo ""

##############################################################################
# Step 4: Wait for health checks
##############################################################################
echo "[4/5] Waiting for health checks (max 90s)..."
MAX_WAIT=90
WAIT_COUNT=0
ALL_HEALTHY=false

while [ $WAIT_COUNT -lt $MAX_WAIT ]; do
  # Check health of all monitoring containers
  HEALTH_STATUS=$($DOCKER_COMPOSE ps --format json 2>/dev/null | grep -o '"Health":"[^"]*"' | cut -d'"' -f4 || echo "")

  # Count total containers and healthy containers
  TOTAL=$($DOCKER_COMPOSE ps --format '{{.Name}}' 2>/dev/null | wc -l | tr -d ' ')
  HEALTHY=$(echo "$HEALTH_STATUS" | grep -c "healthy" || echo "0")

  if [ "$HEALTHY" -eq "$TOTAL" ] && [ "$TOTAL" -gt 0 ]; then
    ALL_HEALTHY=true
    break
  fi

  echo "   Health: $HEALTHY/$TOTAL containers healthy (waiting...)"
  sleep 3
  WAIT_COUNT=$((WAIT_COUNT + 3))
done

if [ "$ALL_HEALTHY" = true ]; then
  echo "✓ All containers healthy"
else
  echo -e "${YELLOW}⚠ WARNING: Not all containers became healthy within ${MAX_WAIT}s${NC}"
  echo "Continuing with verification..."
fi
echo ""

##############################################################################
# Step 5: Run verification
##############################################################################
echo "[5/5] Running monitoring verification..."
echo ""

if bash "$REPO_ROOT/scripts/monitoring/verify-monitoring.sh"; then
  echo ""
  echo "════════════════════════════════════════════════════════════"
  echo "DEPLOYMENT COMPLETE"
  echo "════════════════════════════════════════════════════════════"
  echo "Deployed at: $(date -u +%Y-%m-%d\ %H:%M:%S\ UTC)"
  echo "Git commit: $CURRENT_SHA"
  echo "Suggested tag: $DEPLOY_TAG"
  echo ""
  echo "To tag this release:"
  echo "  git tag -a $DEPLOY_TAG -m 'Monitoring release'"
  echo "  git push origin $DEPLOY_TAG"
  echo ""
  exit 0
else
  echo ""
  echo -e "${RED}════════════════════════════════════════════════════════════${NC}"
  echo -e "${RED}DEPLOYMENT FAILED VERIFICATION${NC}"
  echo -e "${RED}════════════════════════════════════════════════════════════${NC}"
  echo "Check logs:"
  echo "  docker logs scanium-grafana --tail 50"
  echo "  docker logs scanium-mimir --tail 50"
  echo "  docker logs scanium-loki --tail 50"
  echo "  docker logs scanium-tempo --tail 50"
  echo "  docker logs scanium-alloy --tail 50"
  echo ""
  exit 1
fi
