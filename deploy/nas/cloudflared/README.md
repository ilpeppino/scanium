# Cloudflared Deployment

## The Network Problem

Cloudflared needs to be on the **same Docker network** as the backend to resolve the `scanium-backend` hostname.

### Why This Keeps Breaking

There are two docker-compose.yml files:
1. `/volume1/docker/cloudflared/docker-compose.yml` - Active deployment location
2. `/volume1/docker/scanium/repo/deploy/nas/cloudflared/docker-compose.yml` - Source of truth (in git)

When cloudflared is redeployed (manually or via restart), it may use an outdated compose file that's missing networks.

### Required Networks

Cloudflared MUST be connected to these networks:
- `scanium_net` - **CRITICAL** - Where scanium-backend lives
- `backend_scanium-network` - Legacy network
- `compose_scanium_net` - Legacy network
- `scanium-observability` - For monitoring (optional but recommended)

## The Fix

### Option 1: Use the Deployment Script (Recommended)

From your Mac:
```bash
# From repo root
./deploy-cloudflared.sh
```

Or directly on NAS:
```bash
cd /volume1/docker/cloudflared
docker-compose down
docker-compose up -d
# The updated compose file will ensure all networks are connected
```

### Option 2: Manual Network Connection

If cloudflared is running but not working:
```bash
docker network connect scanium_net scanium-cloudflared
```

## Deployment Script Features

The `redeploy.sh` script:
- ✅ Verifies all required networks exist
- ✅ Syncs docker-compose.yml from repo
- ✅ Backs up current configuration
- ✅ Restarts container with correct networks
- ✅ Validates network connectivity
- ✅ Tests DNS resolution
- ✅ Can be run from Mac or NAS

## Verification

After deployment, verify:

```bash
# Check connected networks
docker inspect scanium-cloudflared --format '{{json .NetworkSettings.Networks}}' | jq 'keys'

# Should include: scanium_net

# Test DNS resolution
docker exec scanium-cloudflared nslookup scanium-backend

# Test tunnel
curl https://scanium.gtemp1.com/health
```

## Troubleshooting

### Tunnel returns 502 errors
```bash
# Check cloudflared logs for DNS errors
docker logs scanium-cloudflared --tail 50 | grep -E 'ERR|no such host'

# If you see "no such host", run:
docker network connect scanium_net scanium-cloudflared
```

### Container keeps losing network connection
This means the container is being recreated from an old docker-compose.yml. Run the deployment script to ensure the correct configuration is used.

## Preventing Future Issues

1. **Always use the deployment script** instead of manual docker commands
2. **Keep both compose files in sync** - The script handles this automatically
3. **Never manually edit** `/volume1/docker/cloudflared/docker-compose.yml` directly
4. **Make changes in git** at `deploy/nas/cloudflared/docker-compose.yml` and deploy

## Quick Commands

```bash
# Deploy from Mac
./deploy-cloudflared.sh

# Check status
ssh nas "docker ps | grep cloudflared"

# View logs
ssh nas "docker logs -f scanium-cloudflared"

# Test tunnel
curl https://scanium.gtemp1.com/health
```
