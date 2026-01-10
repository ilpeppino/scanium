***REMOVED***!/usr/bin/env bash
set -euo pipefail

***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***
***REMOVED*** deploy-backend-nas.sh
***REMOVED***
***REMOVED*** Builds a uniquely tagged scanium-backend image and deploys it on NAS.
***REMOVED*** Tags are based on date + git short SHA: YYYY.MM.DD-<shortSHA>
***REMOVED***
***REMOVED*** Usage:
***REMOVED***   bash scripts/app/deploy-backend-nas.sh
***REMOVED***
***REMOVED*** Requirements:
***REMOVED***   - Must run on NAS (uses sudo docker)
***REMOVED***   - Repo at /volume1/docker/scanium/repo
***REMOVED***   - Main branch must be up to date
***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***

REPO_ROOT="/volume1/docker/scanium/repo"
BACKEND_DIR="$REPO_ROOT/backend"
DOCKER="sudo /usr/local/bin/docker"
DOCKER_COMPOSE="sudo -E PATH=/usr/local/bin:\$PATH /usr/local/bin/docker-compose"

***REMOVED*** Ensure docker is in PATH for docker-compose
export PATH="/usr/local/bin:$PATH"

***REMOVED*** Helper function to list recent image tags
list_tags() {
  echo "════════════════════════════════════════════════════════════"
  echo "Recent scanium-backend image tags:"
  echo "════════════════════════════════════════════════════════════"
  $DOCKER images scanium-backend --format "table {{.Tag}}\t{{.CreatedAt}}\t{{.Size}}" | head -n 21
  echo ""
}

***REMOVED*** If --list-tags flag is provided, show tags and exit
if [[ "${1:-}" == "--list-tags" ]]; then
  list_tags
  exit 0
fi

echo "════════════════════════════════════════════════════════════"
echo "Scanium Backend Deployment - NAS Local Tagged Build"
echo "════════════════════════════════════════════════════════════"
echo ""

***REMOVED*** Step 1: Navigate to repo and update from origin
echo "[1/8] Updating repository from origin/main..."
cd "$REPO_ROOT"
git checkout main
git pull --rebase origin main
echo "✓ Repository updated"
echo ""

***REMOVED*** Step 2: Compute tag from current commit
echo "[2/8] Computing image tag..."
SHORT_SHA=$(git rev-parse --short HEAD)
TAG=$(date -u +%Y.%m.%d)-${SHORT_SHA}
echo "✓ Image tag: $TAG"
echo ""

***REMOVED*** Step 3: Export BACKEND_TAG for docker-compose
echo "[3/8] Setting BACKEND_TAG environment variable..."
export BACKEND_TAG="$TAG"
echo "✓ BACKEND_TAG=$BACKEND_TAG"
echo ""

***REMOVED*** Step 4: Build the image with the tag
echo "[4/8] Building backend image (scanium-backend:$TAG)..."
cd "$BACKEND_DIR"
$DOCKER_COMPOSE build api
echo "✓ Image built"
echo ""

***REMOVED*** Step 5: Verify the image was tagged correctly
echo "[5/8] Verifying image tag..."
if ! $DOCKER image inspect "scanium-backend:$TAG" > /dev/null 2>&1; then
  echo "✗ ERROR: Image scanium-backend:$TAG was not created"
  echo "Attempting to tag manually..."
  $DOCKER tag backend_api:latest "scanium-backend:$TAG" || {
    echo "✗ FATAL: Failed to tag image"
    exit 1
  }
fi
echo "✓ Image scanium-backend:$TAG exists"
echo ""

***REMOVED*** Step 6: Deploy (recreate container with new image)
echo "[6/8] Deploying backend container..."
$DOCKER_COMPOSE up -d --force-recreate api
echo "✓ Container recreated"
echo ""

***REMOVED*** Step 7: Wait for container to be healthy
echo "[7/8] Waiting for backend to be healthy..."
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
  echo "✗ WARNING: Backend did not become healthy within ${MAX_WAIT}s"
  echo "   Current status: $HEALTH"
  echo "   Check logs: sudo docker logs scanium-backend"
else
  ***REMOVED*** Step 8: Verify deployed image tag
  echo ""
  echo "[8/8] Verifying deployment..."
  DEPLOYED_IMAGE=$($DOCKER inspect --format='{{.Config.Image}}' scanium-backend)
  echo "✓ Container is running with image: $DEPLOYED_IMAGE"

  ***REMOVED*** Test health endpoint
  echo "   Testing health endpoint..."
  if $DOCKER exec scanium-backend node -e "require('http').get('http://localhost:8080/health', (r) => {process.exit(r.statusCode === 200 ? 0 : 1)}).on('error', () => process.exit(1))"; then
    echo "✓ Health endpoint responding"
  else
    echo "✗ WARNING: Health endpoint check failed"
  fi
fi

echo ""
echo "════════════════════════════════════════════════════════════"
echo "DEPLOYMENT COMPLETE"
echo "════════════════════════════════════════════════════════════"
echo "DEPLOYED_BACKEND_TAG=$TAG"
echo "Image: scanium-backend:$TAG"
echo "Container: scanium-backend"
echo ""
list_tags

echo ""
echo "To rollback to a previous tag:"
echo "  bash scripts/app/rollback-backend-nas.sh <TAG>"
echo ""
