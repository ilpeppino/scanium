#!/bin/bash
set -e

##############################################################################
# ULTIMATE CLOUDFLARED DEPLOYMENT
#
# This script ensures cloudflared ALWAYS has the correct networks by:
# 1. Deploying from SINGLE SOURCE OF TRUTH (git repo)
# 2. Using absolute paths to .env and config.yml
# 3. Completely removing and rebuilding container
# 4. Disabling the old /volume1/docker/cloudflared/docker-compose.yml
#
# This guarantees networks are baked into the container configuration.
##############################################################################

REPO_DIR="/volume1/docker/scanium/repo/deploy/nas/cloudflared"
OLD_DIR="/volume1/docker/cloudflared"
CONTAINER_NAME="scanium-cloudflared"

echo "🚀 ULTIMATE Cloudflared Deployment"
echo "   Single source of truth: $REPO_DIR"
echo ""

# Ensure we're on NAS
if [ ! -d "$REPO_DIR" ]; then
    echo "❌ Error: Must run on NAS"
    echo "   $REPO_DIR not found"
    exit 1
fi

# Change to repo directory (single source of truth)
cd "$REPO_DIR"
echo "✓ Working directory: $PWD"

# Verify required files exist
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

# Check Docker networks
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

# Disable old docker-compose.yml to prevent confusion
if [ -f "$OLD_DIR/docker-compose.yml" ]; then
    echo "Disabling old docker-compose.yml..."
    TIMESTAMP=$(date +%Y%m%d-%H%M%S)
    mv "$OLD_DIR/docker-compose.yml" "$OLD_DIR/docker-compose.yml.DISABLED.$TIMESTAMP"
    echo "  ✓ Renamed to docker-compose.yml.DISABLED.$TIMESTAMP"
    echo "  ℹ️  Always deploy from: $REPO_DIR"
    echo ""
fi

# Complete removal of existing container
echo "Removing existing container (if any)..."
/usr/local/bin/docker stop "$CONTAINER_NAME" 2>/dev/null || true
/usr/local/bin/docker rm -f "$CONTAINER_NAME" 2>/dev/null || true
echo "  ✓ Container removed"
echo ""

# Pull latest image (no cache)
echo "Pulling latest cloudflared image..."
/usr/local/bin/docker pull cloudflare/cloudflared:latest
echo ""

# Deploy from repo (single source of truth)
echo "Deploying from git repo..."
echo "  Source: $REPO_DIR/docker-compose.yml"
echo "  Networks: backend_scanium-network, compose_scanium_net, scanium_net, scanium-observability"
echo ""
/usr/local/bin/docker-compose up -d --force-recreate --no-build

# Wait for startup
echo "Waiting for container to start..."
sleep 5

# Verify deployment
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

# Check networks
echo "Connected networks:"
CONNECTED_NETWORKS=$(/usr/local/bin/docker inspect "$CONTAINER_ID" --format '{{range $net, $conf := .NetworkSettings.Networks}}{{$net}} {{end}}')
echo "  $CONNECTED_NETWORKS"

# Verify critical network
if echo "$CONNECTED_NETWORKS" | grep -q "scanium_net"; then
    echo "  ✅ scanium_net CONNECTED"
else
    echo "  ❌ ERROR: scanium_net NOT connected!"
    exit 1
fi

# Test DNS resolution
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
