***REMOVED***!/bin/bash
***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***
***REMOVED*** CLOUDFLARED ULTIMATE DEPLOYMENT (from Mac)
***REMOVED***
***REMOVED*** Deploys cloudflared with guaranteed network configuration.
***REMOVED*** Uses single source of truth: git repo on NAS
***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***

set -e

echo "🚀 ULTIMATE Cloudflared Deployment"
echo "   Deploying from git repo (single source of truth)"
echo ""

***REMOVED*** Deploy directly from repo on NAS (no file copying needed!)
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
