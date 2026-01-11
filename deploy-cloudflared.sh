#!/bin/bash
##############################################################################
# CLOUDFLARED ULTIMATE DEPLOYMENT (from Mac)
#
# Deploys cloudflared with guaranteed network configuration.
# Uses single source of truth: git repo on NAS
##############################################################################

set -e

echo "🚀 ULTIMATE Cloudflared Deployment"
echo "   Deploying from git repo (single source of truth)"
echo ""

# Deploy directly from repo on NAS (no file copying needed!)
ssh nas "bash /volume1/docker/scanium/repo/deploy/nas/cloudflared/deploy-ultimate.sh"

echo ""
echo "═══════════════════════════════════════════════════════"
echo "  ✅ DEPLOYMENT COMPLETE FROM MAC"
echo "═══════════════════════════════════════════════════════"
echo ""
echo "Test tunnel:"
echo "  curl https://scanium.gtemp1.com/health"
echo ""
echo "View logs:"
echo "  ssh nas 'docker logs -f scanium-cloudflared'"
echo ""
