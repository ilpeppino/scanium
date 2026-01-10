# Redeploying Scanium Backend on NAS

This document provides step-by-step instructions for redeploying the Scanium backend on the Synology NAS.

## Quick Redeploy (Most Common)

When code changes have been pushed to the repository, follow these steps:

### 1. SSH into the NAS

```bash
ssh admin@NAS_IP_ADDRESS
# Or if you have an alias:
ssh nas
```

### 2. Navigate to the Backend Directory

```bash
cd /volume1/docker/scanium/backend
```

### 3. Pull Latest Code

```bash
git pull origin main
```

### 4. Rebuild and Restart

```bash
# Stop the API container
docker compose down api

# Rebuild with no cache (ensures fresh build)
docker compose build --no-cache api

# Start the API container
docker compose up -d api

# Verify it's running
docker ps | grep scanium-api
```

### 5. Verify the Deployment

```bash
# Check container health
docker inspect --format='{{.State.Health.Status}}' scanium-api

# Check logs
docker logs scanium-api --tail 50

# Test the warmup endpoint
curl -X POST http://localhost:8080/v1/assist/warmup \
  -H "X-API-Key: YOUR_API_KEY"
```

Expected output:
```json
{"status":"ok","provider":"claude","model":"...","ts":"...","correlationId":"..."}
```

### 6. Test via Cloudflare Tunnel

```bash
curl -X POST https://scanium.gtemp1.com/v1/assist/warmup \
  -H "X-API-Key: YOUR_API_KEY"
```

## Full Stack Redeploy

When you need to redeploy all services:

```bash
cd /volume1/docker/scanium/backend

# Stop all services
docker compose down

# Pull latest code
git pull origin main

# Rebuild all services
docker compose build --no-cache

# Start all services
docker compose up -d

# Verify all containers are running
docker compose ps
```

## Troubleshooting

### Container Won't Start

Check logs:
```bash
docker logs scanium-api --tail 100
```

Common issues:
- Missing environment variables in `.env`
- Database not reachable
- Port conflicts

### Health Check Failing

```bash
# Check health status
docker inspect --format='{{json .State.Health}}' scanium-api | jq .

# Test health endpoint directly
curl http://localhost:8080/health
```

### Old Image Still Running

Force rebuild:
```bash
docker compose down api
docker rmi scanium-backend_api:latest 2>/dev/null || true
docker compose build --no-cache api
docker compose up -d api
```

### Database Connection Issues

```bash
# Check PostgreSQL container
docker logs scanium-postgres --tail 50

# Test database connectivity
docker exec scanium-postgres pg_isready -U scanium
```

### Cloudflare Tunnel Issues

```bash
# Check tunnel container
docker logs scanium-cloudflared --tail 50

# Verify tunnel is running
docker ps | grep cloudflared
```

## Rollback

If deployment fails and you need to rollback:

```bash
cd /volume1/docker/scanium/backend

# Find previous commit
git log --oneline -10

# Checkout previous version
git checkout PREVIOUS_COMMIT_HASH

# Rebuild and restart
docker compose down api
docker compose build --no-cache api
docker compose up -d api
```

## Monitoring Commands

```bash
# Real-time logs
docker logs -f scanium-api

# Container stats
docker stats scanium-api

# Inspect container
docker inspect scanium-api

# Check all running Scanium containers
docker ps --filter "name=scanium"
```

## Pre-Deployment Checklist

Before deploying:

1. [ ] Tests pass locally: `npm test`
2. [ ] TypeScript compiles: `npm run typecheck`
3. [ ] Build succeeds: `npm run build`
4. [ ] Docker builds locally: `docker build -t scanium-backend:test .`
5. [ ] Changes committed and pushed to main

## Post-Deployment Verification

After deploying:

1. [ ] Container is running: `docker ps | grep scanium-api`
2. [ ] Health check passes: `curl http://localhost:8080/health`
3. [ ] Warmup returns 200: `curl -X POST http://localhost:8080/v1/assist/warmup -H "X-API-Key: KEY"`
4. [ ] External access works: `curl -X POST https://scanium.gtemp1.com/v1/assist/warmup -H "X-API-Key: KEY"`
5. [ ] Logs show no errors: `docker logs scanium-api --tail 50`
