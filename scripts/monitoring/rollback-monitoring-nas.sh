***REMOVED***!/bin/bash
***REMOVED*** Rollback monitoring stack to a specific git tag
***REMOVED*** Usage: bash rollback-monitoring-nas.sh <tag>
***REMOVED***
***REMOVED*** This script:
***REMOVED*** 1. Checks out the specified git tag
***REMOVED*** 2. Rebuilds and restarts monitoring containers
***REMOVED*** 3. Runs E2E tests to verify rollback succeeded
***REMOVED***
***REMOVED*** IMPORTANT: Run this ON THE NAS via SSH

set -euo pipefail

TAG="${1:-}"

if [ -z "$TAG" ]; then
  echo "Usage: bash rollback-monitoring-nas.sh <tag>"
  echo ""
  echo "Example: bash rollback-monitoring-nas.sh monitoring-2026.01.12-d5bf933"
  exit 1
fi

echo "========================================================================="
echo " Monitoring Stack Rollback"
echo "========================================================================="
echo "Target tag: $TAG"
echo ""

***REMOVED*** Step 1: Verify we're in the correct directory
if [ ! -d ".git" ]; then
  echo "❌ Not in a git repository. Please run from /volume1/docker/scanium/repo"
  exit 1
fi

***REMOVED*** Step 2: Fetch latest tags
echo "[rollback] Step 1: Fetching latest tags..."
git fetch --tags

***REMOVED*** Step 3: Verify tag exists
if ! git rev-parse "$TAG" >/dev/null 2>&1; then
  echo "❌ Tag '$TAG' does not exist"
  echo ""
  echo "Available monitoring tags:"
  git tag | grep "^monitoring-" || echo "  (none found)"
  exit 1
fi

***REMOVED*** Step 4: Check out the tag
echo "[rollback] Step 2: Checking out tag '$TAG'..."
git checkout "$TAG"

***REMOVED*** Step 5: Rebuild and restart monitoring stack
echo "[rollback] Step 3: Rebuilding and restarting monitoring containers..."
cd monitoring
/usr/local/bin/docker-compose down
/usr/local/bin/docker-compose up -d --build

echo "[rollback] Waiting 15s for services to stabilize..."
sleep 15

***REMOVED*** Step 6: Run E2E tests to verify rollback
echo "[rollback] Step 4: Running E2E tests to verify rollback..."
cd ..
bash scripts/monitoring/e2e-monitoring.sh

***REMOVED*** Step 7: Success
echo ""
echo "========================================================================="
echo " ✅ Rollback to $TAG SUCCESSFUL"
echo "========================================================================="
echo "All E2E tests passed. Monitoring stack is healthy."
exit 0
