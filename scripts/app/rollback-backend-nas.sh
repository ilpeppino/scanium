#!/usr/bin/env bash
set -euo pipefail

##############################################################################
# rollback-backend-nas.sh
#
# Rolls back the scanium-backend to a previously built Docker image tag.
#
# Usage:
#   bash scripts/app/rollback-backend-nas.sh <TAG>
#
# Example:
#   bash scripts/app/rollback-backend-nas.sh 2026.01.10-abc123
#   bash scripts/app/rollback-backend-nas.sh latest
#
# Requirements:
#   - Must run on NAS
#   - Docker and docker-compose in PATH
#   - Target image tag must exist locally
#   - Repo at /volume1/docker/scanium/repo
##############################################################################

REPO_ROOT="/volume1/docker/scanium/repo"
BACKEND_DIR="$REPO_ROOT/backend"
DOCKER="/usr/local/bin/docker"
DOCKER_COMPOSE="/usr/local/bin/docker-compose"

# Ensure docker is in PATH
export PATH="/usr/local/bin:$PATH"

# Check arguments
if [ $# -ne 1 ]; then
  echo "Usage: $0 <TAG>"
  echo ""
  echo "Available tags:"
  $DOCKER images scanium-backend --format "  {{.Tag}}" | grep -v '<none>'
  exit 1
fi

TARGET_TAG="$1"

echo "════════════════════════════════════════════════════════════"
echo "Scanium Backend Rollback - NAS Local"
echo "════════════════════════════════════════════════════════════"
echo ""
echo "Target tag: $TARGET_TAG"
echo ""

# Step 1: Verify tag exists
echo "[1/5] Verifying image scanium-backend:$TARGET_TAG exists..."
if ! $DOCKER image inspect "scanium-backend:$TARGET_TAG" > /dev/null 2>&1; then
  echo "✗ ERROR: Image scanium-backend:$TARGET_TAG does not exist"
  echo ""
  echo "Available tags:"
  $DOCKER images scanium-backend --format "  {{.Tag}}\t{{.CreatedAt}}" | head -n 20
  exit 1
fi
echo "✓ Image exists"
echo ""

# Step 2: Get current running image for reference
echo "[2/5] Checking current deployment..."
CURRENT_IMAGE=$($DOCKER inspect --format='{{.Config.Image}}' scanium-backend 2>/dev/null || echo "unknown")
echo "   Current image: $CURRENT_IMAGE"
echo "   Target image:  scanium-backend:$TARGET_TAG"
echo ""

if [ "$CURRENT_IMAGE" == "scanium-backend:$TARGET_TAG" ]; then
  echo "⚠ Container is already running the target image"
  read -p "Continue anyway? (y/N) " -n 1 -r
  echo ""
  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Rollback cancelled"
    exit 0
  fi
fi

# Step 3: Set BACKEND_TAG and deploy
echo "[3/5] Setting BACKEND_TAG and redeploying..."
export BACKEND_TAG="$TARGET_TAG"
cd "$BACKEND_DIR"
$DOCKER_COMPOSE up -d --force-recreate --no-deps api
echo "✓ Container recreated"
echo ""

# Step 4: Wait for container to be healthy
echo "[4/5] Waiting for backend to be healthy..."
MAX_WAIT=60
WAIT_COUNT=0
while [ $WAIT_COUNT -lt $MAX_WAIT ]; do
  HEALTH=$($DOCKER inspect --format='{{.State.Health.Status}}' scanium-backend 2>/dev/null || echo "unknown")
  if [ "$HEALTH" == "healthy" ]; then
    echo "✓ Backend is healthy"
    break
  fi
  echo "   Health status: $HEALTH (waiting...)"
  sleep 2
  WAIT_COUNT=$((WAIT_COUNT + 2))
done

if [ "$HEALTH" != "healthy" ]; then
  echo "✗ ERROR: Backend did not become healthy within ${MAX_WAIT}s"
  echo "   Current status: $HEALTH"
  echo "   Check logs: sudo docker logs scanium-backend"
  echo ""
  echo "⚠ ROLLBACK MAY HAVE FAILED - Container is not healthy"
  echo "   Consider rolling back to a different tag or investigating logs"
  exit 1
fi

# Step 5: Verify deployed image
echo ""
echo "[5/5] Verifying rollback..."
DEPLOYED_IMAGE=$($DOCKER inspect --format='{{.Config.Image}}' scanium-backend)
echo "✓ Container is running with image: $DEPLOYED_IMAGE"

# Test health endpoint
echo "   Testing health endpoint..."
if $DOCKER exec scanium-backend node -e "require('http').get('http://localhost:8080/health', (r) => {process.exit(r.statusCode === 200 ? 0 : 1)}).on('error', () => process.exit(1))"; then
  echo "✓ Health endpoint responding"
else
  echo "✗ WARNING: Health endpoint check failed"
fi

echo ""
echo "════════════════════════════════════════════════════════════"
echo "ROLLBACK COMPLETE"
echo "════════════════════════════════════════════════════════════"
echo "Previous image: $CURRENT_IMAGE"
echo "Current image:  $DEPLOYED_IMAGE"
echo ""

if [ "$DEPLOYED_IMAGE" != "scanium-backend:$TARGET_TAG" ]; then
  echo "⚠ WARNING: Deployed image does not match target tag"
  echo "   Expected: scanium-backend:$TARGET_TAG"
  echo "   Got:      $DEPLOYED_IMAGE"
  exit 1
fi

echo "✓ Successfully rolled back to: $TARGET_TAG"
echo ""
