***REMOVED***!/bin/bash
set -e

***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***
***REMOVED*** ULTIMATE CLOUDFLARED DEPLOYMENT
***REMOVED***
***REMOVED*** This script ensures cloudflared ALWAYS has the correct networks by:
***REMOVED*** 1. Deploying from SINGLE SOURCE OF TRUTH (git repo)
***REMOVED*** 2. Using absolute paths to .env and config.yml
***REMOVED*** 3. Completely removing and rebuilding container
***REMOVED*** 4. Disabling the old /volume1/docker/cloudflared/docker-compose.yml
***REMOVED***
***REMOVED*** This guarantees networks are baked into the container configuration.
***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***

REPO_DIR="/volume1/docker/scanium/repo/deploy/nas/cloudflared"
OLD_DIR="/volume1/docker/cloudflared"
CONTAINER_NAME="scanium-cloudflared"

echo "🚀 ULTIMATE Cloudflared Deployment"
echo "   Single source of truth: $REPO_DIR"
echo ""

***REMOVED*** Ensure we're on NAS
if [ ! -d "$REPO_DIR" ]; then
    echo "❌ Error: Must run on NAS"
    echo "   $REPO_DIR not found"
    exit 1
fi

***REMOVED*** Change to repo directory (single source of truth)
cd "$REPO_DIR"
echo "✓ Working directory: $PWD"

***REMOVED*** Verify required files exist
if [ ! -f "$OLD_DIR/.env" ]; then
    echo "❌ Error: $OLD_DIR/.env not found"
    echo "   This file contains CLOUDFLARED_TOKEN"
    exit 1
fi

if [ ! -f "$OLD_DIR/config.yml" ]; then
    echo "❌ Error: $OLD_DIR/config.yml not found"
    exit 1
fi

echo "✓ Required files exist (.env, config.yml)"
echo ""

***REMOVED*** Create symlink to .env in repo directory for docker-compose variable substitution
***REMOVED*** This allows docker-compose to substitute ${CLOUDFLARED_TOKEN} in the command
echo "Setting up .env symlink for docker-compose..."
if [ -L "$REPO_DIR/.env" ] || [ -f "$REPO_DIR/.env" ]; then
    rm -f "$REPO_DIR/.env"
fi
ln -s "$OLD_DIR/.env" "$REPO_DIR/.env"
echo "  ✓ Symlinked $OLD_DIR/.env → $REPO_DIR/.env"
echo ""

***REMOVED*** Check Docker networks
echo "Verifying Docker networks..."
REQUIRED_NETWORKS=(
    "backend_scanium-network"
    "compose_scanium_net"
    "scanium_net"
    "scanium-observability"
)

for network in "${REQUIRED_NETWORKS[@]}"; do
    if /usr/local/bin/docker network inspect "$network" &>/dev/null; then
        echo "  ✓ $network"
    else
        echo "  ⚠️  $network (creating...)"
        /usr/local/bin/docker network create "$network" || true
    fi
done
echo ""

***REMOVED*** Disable old docker-compose.yml to prevent confusion
if [ -f "$OLD_DIR/docker-compose.yml" ]; then
    echo "Disabling old docker-compose.yml..."
    TIMESTAMP=$(date +%Y%m%d-%H%M%S)
    mv "$OLD_DIR/docker-compose.yml" "$OLD_DIR/docker-compose.yml.DISABLED.$TIMESTAMP"
    echo "  ✓ Renamed to docker-compose.yml.DISABLED.$TIMESTAMP"
    echo "  ℹ️  Always deploy from: $REPO_DIR"
    echo ""
fi

***REMOVED*** Complete removal of existing container
echo "Removing existing container (if any)..."
/usr/local/bin/docker stop "$CONTAINER_NAME" 2>/dev/null || true
/usr/local/bin/docker rm -f "$CONTAINER_NAME" 2>/dev/null || true
echo "  ✓ Container removed"
echo ""

***REMOVED*** Pull latest image (no cache)
echo "Pulling latest cloudflared image..."
/usr/local/bin/docker pull cloudflare/cloudflared:latest
echo ""

***REMOVED*** Deploy from repo (single source of truth)
echo "Deploying from git repo..."
echo "  Source: $REPO_DIR/docker-compose.yml"
echo "  Networks: backend_scanium-network, compose_scanium_net, scanium_net, scanium-observability"
echo ""
/usr/local/bin/docker-compose up -d --force-recreate --no-build

***REMOVED*** Wait for startup
echo "Waiting for container to start..."
sleep 5

***REMOVED*** Verify deployment
echo ""
echo "═══════════════════════════════════════════════════════"
echo "  VERIFICATION"
echo "═══════════════════════════════════════════════════════"

CONTAINER_ID=$(/usr/local/bin/docker ps -qf "name=$CONTAINER_NAME")
if [ -z "$CONTAINER_ID" ]; then
    echo "❌ ERROR: Container not running!"
    echo ""
    echo "Check logs:"
    echo "  docker logs $CONTAINER_NAME"
    exit 1
fi

echo "✓ Container running: $CONTAINER_ID"
echo ""

***REMOVED*** Check networks
echo "Connected networks:"
CONNECTED_NETWORKS=$(/usr/local/bin/docker inspect "$CONTAINER_ID" --format '{{range $net, $conf := .NetworkSettings.Networks}}{{$net}} {{end}}')
echo "  $CONNECTED_NETWORKS"

***REMOVED*** Verify critical network
if echo "$CONNECTED_NETWORKS" | grep -q "scanium_net"; then
    echo "  ✅ scanium_net CONNECTED"
else
    echo "  ❌ ERROR: scanium_net NOT connected!"
    exit 1
fi

***REMOVED*** Test DNS resolution
echo ""
echo "Testing DNS resolution..."
if /usr/local/bin/docker exec "$CONTAINER_NAME" nslookup scanium-backend &>/dev/null; then
    echo "  ✅ Can resolve scanium-backend hostname"
else
    echo "  ⚠️  Cannot resolve scanium-backend (backend might not be running)"
fi

echo ""
echo "═══════════════════════════════════════════════════════"
echo "  ✅ DEPLOYMENT COMPLETE"
echo "═══════════════════════════════════════════════════════"
echo ""
echo "Container deployed from: $REPO_DIR"
echo "Networks are now permanently configured"
echo ""
echo "Test tunnel:"
echo "  curl https://scanium.gtemp1.com/health"
echo ""
echo "View logs:"
echo "  docker logs -f $CONTAINER_NAME"
echo ""
echo "IMPORTANT:"
echo "  Always deploy from: $REPO_DIR"
echo "  Do NOT use: $OLD_DIR/docker-compose.yml (disabled)"
echo ""
