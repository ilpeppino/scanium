#!/bin/bash
# Wrapper script to deploy cloudflared from Mac to NAS
# Usage: ./deploy-cloudflared.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_SCRIPT="$SCRIPT_DIR/deploy/nas/cloudflared/redeploy.sh"

if [ ! -f "$DEPLOY_SCRIPT" ]; then
    echo "❌ Error: Deploy script not found at $DEPLOY_SCRIPT"
    exit 1
fi

echo "🚀 Deploying Cloudflared to NAS..."
echo ""

# Copy deploy script to NAS using ssh+cat (more reliable than scp)
ssh nas "mkdir -p /tmp/scanium-deploy"
cat "$DEPLOY_SCRIPT" | ssh nas "cat > /tmp/scanium-deploy/redeploy.sh"
ssh nas "chmod +x /tmp/scanium-deploy/redeploy.sh"
ssh nas "bash /tmp/scanium-deploy/redeploy.sh"

echo ""
echo "✅ Deployment complete!"
echo "   Test: curl https://scanium.gtemp1.com/health"
