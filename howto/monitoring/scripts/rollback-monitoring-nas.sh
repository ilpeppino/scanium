#!/usr/bin/env bash
set -euo pipefail

##############################################################################
# rollback-monitoring-nas.sh
#
# Rolls back the monitoring stack to a previous git tag.
#
# Usage:
#   bash scripts/monitoring/rollback-monitoring-nas.sh <TAG>
#
# Example:
#   bash scripts/monitoring/rollback-monitoring-nas.sh monitoring-2026.01.10-abc123
#
# Requirements:
#   - Must run on NAS
#   - Docker and docker-compose in PATH
#   - Target git tag must exist
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

# Check arguments
if [ $# -ne 1 ]; then
  echo "Usage: $0 <TAG>"
  echo ""
  echo "Available monitoring tags:"
  cd "$REPO_ROOT"
  git tag -l "monitoring-*" | tail -n 20
  exit 1
fi

TARGET_TAG="$1"

echo "════════════════════════════════════════════════════════════"
echo "Scanium Monitoring Stack Rollback"
echo "════════════════════════════════════════════════════════════"
echo ""
echo "Target tag: $TARGET_TAG"
echo ""

##############################################################################
# Step 1: Verify tag exists
##############################################################################
echo "[1/5] Verifying git tag exists..."
cd "$REPO_ROOT"
git fetch --tags origin

if ! git tag -l | grep -q "^${TARGET_TAG}$"; then
  echo -e "${RED}✗ ERROR: Tag $TARGET_TAG does not exist${NC}"
  echo ""
  echo "Available monitoring tags:"
  git tag -l "monitoring-*" | tail -n 20
  exit 1
fi
echo "✓ Tag exists"
echo ""

##############################################################################
# Step 2: Show current vs target
##############################################################################
echo "[2/5] Checking current state..."
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
CURRENT_SHA=$(git rev-parse --short HEAD)
TARGET_SHA=$(git rev-parse --short "$TARGET_TAG")

echo "   Current: $CURRENT_BRANCH @ $CURRENT_SHA"
echo "   Target:  $TARGET_TAG @ $TARGET_SHA"
echo ""

if [ "$CURRENT_SHA" = "$TARGET_SHA" ]; then
  echo -e "${YELLOW}⚠ Already at target tag${NC}"
  read -p "Continue anyway? (y/N) " -n 1 -r
  echo ""
  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Rollback cancelled"
    exit 0
  fi
fi

##############################################################################
# Step 3: Checkout target tag
##############################################################################
echo "[3/5] Checking out tag $TARGET_TAG..."
git checkout "$TARGET_TAG"
echo "✓ Checked out $TARGET_TAG"
echo ""

##############################################################################
# Step 4: Redeploy stack
##############################################################################
echo "[4/5] Redeploying monitoring stack..."
cd "$MONITORING_DIR"
$DOCKER_COMPOSE down --remove-orphans
$DOCKER_COMPOSE up -d
echo "✓ Stack redeployed"
echo ""

# Wait for health
echo "Waiting for health checks (max 90s)..."
MAX_WAIT=90
WAIT_COUNT=0
ALL_HEALTHY=false

while [ $WAIT_COUNT -lt $MAX_WAIT ]; do
  HEALTH_STATUS=$($DOCKER_COMPOSE ps --format json 2>/dev/null | grep -o '"Health":"[^"]*"' | cut -d'"' -f4 || echo "")
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
  echo "ROLLBACK COMPLETE"
  echo "════════════════════════════════════════════════════════════"
  echo "Rolled back to: $TARGET_TAG"
  echo "Commit: $TARGET_SHA"
  echo ""
  echo -e "${YELLOW}⚠ NOTE: You are now in detached HEAD state${NC}"
  echo "To return to main branch:"
  echo "  git checkout main"
  echo ""
  exit 0
else
  echo ""
  echo -e "${RED}════════════════════════════════════════════════════════════${NC}"
  echo -e "${RED}ROLLBACK FAILED VERIFICATION${NC}"
  echo -e "${RED}════════════════════════════════════════════════════════════${NC}"
  echo "Check logs:"
  echo "  docker logs scanium-grafana --tail 50"
  echo "  docker logs scanium-mimir --tail 50"
  echo "  docker logs scanium-loki --tail 50"
  echo "  docker logs scanium-tempo --tail 50"
  echo "  docker logs scanium-alloy --tail 50"
  echo ""
  echo "To return to main:"
  echo "  git checkout main"
  echo ""
  exit 1
fi
