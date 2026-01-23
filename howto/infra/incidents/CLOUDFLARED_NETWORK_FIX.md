# Cloudflared Network Fix - Structural Solution

## The Problem

Cloudflared tunnel was experiencing two critical issues:

### Issue 1: Network Connectivity (502 Errors)

The tunnel was repeatedly losing connection to the `scanium_net` Docker network, causing 502 errors.
This happened because:

1. **Two compose file locations** that could get out of sync:
    - `/volume1/docker/cloudflared/docker-compose.yml` (active deployment)
    - `/volume1/docker/scanium/repo/deploy/nas/cloudflared/docker-compose.yml` (in git)

2. **Manual container restarts** didn't preserve network connections

3. **No automated verification** that networks were properly connected

### Issue 2: Token Substitution (Error 1033)

After implementing the single source of truth deployment, the tunnel failed with error 1033. The
root cause was:

- Docker-compose's `${CLOUDFLARED_TOKEN}` variable substitution requires a `.env` file in the
  working directory
- The `env_file:` directive only affects the container's environment, NOT docker-compose's variable
  substitution
- Deploying from `/volume1/docker/scanium/repo/deploy/nas/cloudflared/` without a local `.env` file
  meant the token was empty in the command

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

### 3. .env Symlink for Variable Substitution

Fixed the token substitution issue by creating a symlink:

```bash
ln -s /volume1/docker/cloudflared/.env /volume1/docker/scanium/repo/deploy/nas/cloudflared/.env
```

This allows docker-compose to substitute `${CLOUDFLARED_TOKEN}` during deployment while keeping
secrets in a single location.

### 4. Deployment Script Features

The `deploy-cloudflared.sh` script:

- ✅ Verifies all required networks exist
- ✅ Creates .env symlink for docker-compose variable substitution
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
- ✅ .env symlink created automatically
- ✅ Token properly substituted in command
- ✅ All networks connected automatically
- ✅ Tunnel responding: https://scanium.gtemp1.com/health
- ✅ Backend connectivity working
- ✅ No DNS resolution errors
- ✅ No error 1033 or other tunnel errors

## Summary

**Before:**

- Manual network connection required after every restart (502 errors)
- Token substitution failures (error 1033)

**After:**

- Automated deployment with guaranteed network connectivity
- Proper token substitution via .env symlink
- Single source of truth deployment from git repo

This structural fix ensures cloudflared always has the correct network configuration and proper
authentication, eliminating 502 and 1033 errors permanently.
