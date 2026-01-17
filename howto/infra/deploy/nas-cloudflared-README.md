***REMOVED*** Cloudflared Deployment

***REMOVED******REMOVED*** The Network Problem

Cloudflared needs to be on the **same Docker network** as the backend to resolve the `scanium-backend` hostname.

***REMOVED******REMOVED******REMOVED*** Why This Keeps Breaking

There are two docker-compose.yml files:
1. `/volume1/docker/cloudflared/docker-compose.yml` - Active deployment location
2. `/volume1/docker/scanium/repo/deploy/nas/cloudflared/docker-compose.yml` - Source of truth (in git)

When cloudflared is redeployed (manually or via restart), it may use an outdated compose file that's missing networks.

***REMOVED******REMOVED******REMOVED*** Required Networks

Cloudflared MUST be connected to these networks:
- `scanium_net` - **CRITICAL** - Where scanium-backend lives
- `backend_scanium-network` - Legacy network
- `compose_scanium_net` - Legacy network
- `scanium-observability` - For monitoring (optional but recommended)

***REMOVED******REMOVED*** The Fix

***REMOVED******REMOVED******REMOVED*** Option 1: Use the Deployment Script (Recommended)

From your Mac:
```bash
***REMOVED*** From repo root
./deploy-cloudflared.sh
```

Or directly on NAS:
```bash
cd /volume1/docker/cloudflared
docker-compose down
docker-compose up -d
***REMOVED*** The updated compose file will ensure all networks are connected
```

***REMOVED******REMOVED******REMOVED*** Option 2: Manual Network Connection

If cloudflared is running but not working:
```bash
docker network connect scanium_net scanium-cloudflared
```

***REMOVED******REMOVED*** Deployment Script Features

The `redeploy.sh` script:
- ✅ Verifies all required networks exist
- ✅ Syncs docker-compose.yml from repo
- ✅ Backs up current configuration
- ✅ Restarts container with correct networks
- ✅ Validates network connectivity
- ✅ Tests DNS resolution
- ✅ Can be run from Mac or NAS

***REMOVED******REMOVED*** Verification

After deployment, verify:

```bash
***REMOVED*** Check connected networks
docker inspect scanium-cloudflared --format '{{json .NetworkSettings.Networks}}' | jq 'keys'

***REMOVED*** Should include: scanium_net

***REMOVED*** Test DNS resolution
docker exec scanium-cloudflared nslookup scanium-backend

***REMOVED*** Test tunnel
curl https://scanium.gtemp1.com/health
```

***REMOVED******REMOVED*** Troubleshooting

***REMOVED******REMOVED******REMOVED*** Tunnel returns 502 errors
```bash
***REMOVED*** Check cloudflared logs for DNS errors
docker logs scanium-cloudflared --tail 50 | grep -E 'ERR|no such host'

***REMOVED*** If you see "no such host", run:
docker network connect scanium_net scanium-cloudflared
```

***REMOVED******REMOVED******REMOVED*** Container keeps losing network connection
This means the container is being recreated from an old docker-compose.yml. Run the deployment script to ensure the correct configuration is used.

***REMOVED******REMOVED*** Preventing Future Issues

1. **Always use the deployment script** instead of manual docker commands
2. **Keep both compose files in sync** - The script handles this automatically
3. **Never manually edit** `/volume1/docker/cloudflared/docker-compose.yml` directly
4. **Make changes in git** at `deploy/nas/cloudflared/docker-compose.yml` and deploy

***REMOVED******REMOVED*** Quick Commands

```bash
***REMOVED*** Deploy from Mac
./deploy-cloudflared.sh

***REMOVED*** Check status
ssh nas "docker ps | grep cloudflared"

***REMOVED*** View logs
ssh nas "docker logs -f scanium-cloudflared"

***REMOVED*** Test tunnel
curl https://scanium.gtemp1.com/health
```
