# Cloudflared Network Fix - Structural Solution

## The Problem

Cloudflared tunnel was repeatedly losing connection to the `scanium_net` Docker network, causing 502 errors. This happened because:

1. **Two compose file locations** that could get out of sync:
   - `/volume1/docker/cloudflared/docker-compose.yml` (active deployment)
   - `/volume1/docker/scanium/repo/deploy/nas/cloudflared/docker-compose.yml` (in git)

2. **Manual container restarts** didn't preserve network connections

3. **No automated verification** that networks were properly connected

## The Structural Fix

### 1. Updated Active docker-compose.yml
Updated `/volume1/docker/cloudflared/docker-compose.yml` to include all required networks:
```yaml
networks:
  - backend_scanium-network
  - compose_scanium_net
  - scanium_net              # ← CRITICAL for backend connectivity
  - scanium-observability    # ← For monitoring
```

### 2. Created Automated Deployment Script
**From Mac:**
```bash
cd /Users/family/dev/scanium
./deploy-cloudflared.sh
```

**Directly on NAS:**
```bash
cd /volume1/docker/cloudflared
docker-compose down
docker-compose up -d
# The compose file will ensure all networks are connected
```

### 3. Deployment Script Features
The `deploy-cloudflared.sh` script:
- ✅ Verifies all required networks exist
- ✅ Syncs docker-compose.yml from git to active location
- ✅ Backs up current configuration
- ✅ Force removes existing container
- ✅ Restarts with all networks connected
- ✅ Validates network connectivity
- ✅ Tests DNS resolution
- ✅ Works from Mac or NAS

## Usage

### Deploy from Mac (Recommended)
```bash
cd /Users/family/dev/scanium
./deploy-cloudflared.sh
```

### Directly on NAS
```bash
cd /volume1/docker/scanium/repo/deploy/nas/cloudflared
bash redeploy.sh
```

### Quick Manual Fix (If Needed)
If tunnel returns 502 and you need immediate fix:
```bash
ssh nas "docker network connect scanium_net scanium-cloudflared"
```

## Verification

```bash
# Check connected networks
ssh nas "docker inspect scanium-cloudflared --format '{{json .NetworkSettings.Networks}}' | jq 'keys'"
# Should include: scanium_net

# Test tunnel
curl https://scanium.gtemp1.com/health

# Check for errors
ssh nas "docker logs scanium-cloudflared --tail 20 | grep -E 'ERR|no such host'"
# Should see no "no such host" errors
```

## Prevention Strategy

### DO:
✅ Use `./deploy-cloudflared.sh` when redeploying
✅ Keep both docker-compose.yml files in sync (script handles this)
✅ Make changes in git first, then deploy

### DON'T:
❌ Manually edit `/volume1/docker/cloudflared/docker-compose.yml`
❌ Use `docker stop/start` directly (use docker-compose)
❌ Remove networks manually

## Files Created

1. `/deploy-cloudflared.sh` - Mac wrapper script
2. `/deploy/nas/cloudflared/redeploy.sh` - Main deployment script
3. `/deploy/nas/cloudflared/README.md` - Detailed documentation
4. `/docs/cloudflared-tunnel-troubleshooting.md` - Troubleshooting guide

## Testing

Deployment was tested and verified:
- ✅ Script runs successfully from Mac
- ✅ All networks connected automatically
- ✅ Tunnel responding: https://scanium.gtemp1.com/health
- ✅ Backend connectivity working
- ✅ No DNS resolution errors

## Summary

**Before:** Manual network connection required after every restart
**After:** Automated deployment with guaranteed network connectivity

This structural fix ensures cloudflared always has the correct network configuration, eliminating 502 errors permanently.
