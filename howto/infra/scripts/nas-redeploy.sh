***REMOVED***!/bin/bash
set -e

***REMOVED*** Cloudflared Deployment Script
***REMOVED*** Ensures all networks are properly configured
***REMOVED*** Can be run from Mac or directly on NAS

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="/volume1/docker/cloudflared"

echo "🌐 Deploying Cloudflare Tunnel with all networks..."

***REMOVED*** Check if running on NAS or via SSH
if [ -d "$DEPLOY_DIR" ]; then
    ***REMOVED*** Running directly on NAS
    cd "$DEPLOY_DIR"
else
    ***REMOVED*** Running from Mac - use SSH
    echo "Running via SSH to NAS..."
    ssh nas "cd $DEPLOY_DIR && bash -s" < "$0"
    exit $?
fi

***REMOVED*** Verify required networks exist
echo "Checking Docker networks..."
REQUIRED_NETWORKS=(
    "backend_scanium-network"
    "compose_scanium_net"
    "scanium_net"
    "scanium-observability"
)

for network in "${REQUIRED_NETWORKS[@]}"; do
    if ! /usr/local/bin/docker network inspect "$network" &>/dev/null; then
        echo "⚠️  Warning: Network '$network' does not exist"
        echo "   Creating network..."
        /usr/local/bin/docker network create "$network" || true
    else
        echo "✓ Network '$network' exists"
    fi
done

***REMOVED*** Backup current docker-compose.yml if it exists
if [ -f "docker-compose.yml" ]; then
    backup_file="docker-compose.yml.bak.$(date +%Y%m%d-%H%M%S)"
    echo "Backing up current config to $backup_file"
    cp docker-compose.yml "$backup_file"
fi

***REMOVED*** Copy docker-compose.yml from repo if it's newer
REPO_COMPOSE="/volume1/docker/scanium/repo/deploy/nas/cloudflared/docker-compose.yml"
if [ -f "$REPO_COMPOSE" ]; then
    echo "Syncing docker-compose.yml from repo..."
    cp "$REPO_COMPOSE" docker-compose.yml
else
    echo "⚠️  Warning: Repo compose file not found, using existing"
fi

***REMOVED*** Stop and remove existing container
echo "Stopping existing container..."
/usr/local/bin/docker-compose down || true
***REMOVED*** Force remove if still exists
/usr/local/bin/docker rm -f scanium-cloudflared 2>/dev/null || true

***REMOVED*** Start with updated configuration
echo "Starting Cloudflared with all networks..."
/usr/local/bin/docker-compose up -d

***REMOVED*** Wait for container to be ready
echo "Waiting for container to start..."
sleep 5

***REMOVED*** Verify networks
echo ""
echo "Verifying network connections..."
CONTAINER_ID=$(/usr/local/bin/docker ps -qf "name=scanium-cloudflared")
if [ -n "$CONTAINER_ID" ]; then
    CONNECTED_NETWORKS=$(/usr/local/bin/docker inspect "$CONTAINER_ID" --format '{{range $net, $conf := .NetworkSettings.Networks}}{{$net}} {{end}}')
    echo "Connected networks: $CONNECTED_NETWORKS"

    ***REMOVED*** Check if scanium_net is connected
    if echo "$CONNECTED_NETWORKS" | grep -q "scanium_net"; then
        echo "✅ scanium_net is connected - tunnel should work!"
    else
        echo "❌ ERROR: scanium_net is NOT connected!"
        echo "   Manually connecting..."
        /usr/local/bin/docker network connect scanium_net scanium-cloudflared
    fi
else
    echo "❌ Container not running!"
    exit 1
fi

***REMOVED*** Test connectivity
echo ""
echo "Testing tunnel connectivity..."
if /usr/local/bin/docker exec scanium-cloudflared nslookup scanium-backend &>/dev/null; then
    echo "✅ DNS resolution works - tunnel is healthy!"
else
    echo "⚠️  Warning: Cannot resolve scanium-backend hostname"
fi

echo ""
echo "🎉 Cloudflared deployment complete!"
echo "   View logs: docker logs -f scanium-cloudflared"
echo "   Test tunnel: curl https://scanium.gtemp1.com/health"
