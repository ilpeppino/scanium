***REMOVED*** Redeploying Scanium Backend on NAS

This document provides step-by-step instructions for redeploying the Scanium backend on the Synology NAS.

***REMOVED******REMOVED*** Quick Redeploy (Most Common)

When code changes have been pushed to the repository, follow these steps:

***REMOVED******REMOVED******REMOVED*** 1. SSH into the NAS

```bash
ssh admin@NAS_IP_ADDRESS
***REMOVED*** Or if you have an alias:
ssh nas
```

***REMOVED******REMOVED******REMOVED*** 2. Navigate to the Backend Directory

```bash
cd /volume1/docker/scanium/backend
```

***REMOVED******REMOVED******REMOVED*** 3. Pull Latest Code

```bash
git pull origin main
```

***REMOVED******REMOVED******REMOVED*** 4. Rebuild and Restart

```bash
***REMOVED*** Stop the API container
docker compose down api

***REMOVED*** Rebuild with no cache (ensures fresh build)
docker compose build --no-cache api

***REMOVED*** Start the API container
docker compose up -d api

***REMOVED*** Verify it's running
docker ps | grep scanium-api
```

***REMOVED******REMOVED******REMOVED*** 5. Verify the Deployment

```bash
***REMOVED*** Check container health
docker inspect --format='{{.State.Health.Status}}' scanium-api

***REMOVED*** Check logs
docker logs scanium-api --tail 50

***REMOVED*** Test the warmup endpoint
curl -X POST http://localhost:8080/v1/assist/warmup \
  -H "X-API-Key: YOUR_API_KEY"
```

Expected output:
```json
{"status":"ok","provider":"claude","model":"...","ts":"...","correlationId":"..."}
```

***REMOVED******REMOVED******REMOVED*** 6. Test via Cloudflare Tunnel

```bash
curl -X POST https://scanium.gtemp1.com/v1/assist/warmup \
  -H "X-API-Key: YOUR_API_KEY"
```

***REMOVED******REMOVED*** Full Stack Redeploy

When you need to redeploy all services:

```bash
cd /volume1/docker/scanium/backend

***REMOVED*** Stop all services
docker compose down

***REMOVED*** Pull latest code
git pull origin main

***REMOVED*** Rebuild all services
docker compose build --no-cache

***REMOVED*** Start all services
docker compose up -d

***REMOVED*** Verify all containers are running
docker compose ps
```

***REMOVED******REMOVED*** Troubleshooting

***REMOVED******REMOVED******REMOVED*** Container Won't Start

Check logs:
```bash
docker logs scanium-api --tail 100
```

Common issues:
- Missing environment variables in `.env`
- Database not reachable
- Port conflicts

***REMOVED******REMOVED******REMOVED*** Health Check Failing

```bash
***REMOVED*** Check health status
docker inspect --format='{{json .State.Health}}' scanium-api | jq .

***REMOVED*** Test health endpoint directly
curl http://localhost:8080/health
```

***REMOVED******REMOVED******REMOVED*** Old Image Still Running

Force rebuild:
```bash
docker compose down api
docker rmi scanium-backend_api:latest 2>/dev/null || true
docker compose build --no-cache api
docker compose up -d api
```

***REMOVED******REMOVED******REMOVED*** Database Connection Issues

```bash
***REMOVED*** Check PostgreSQL container
docker logs scanium-postgres --tail 50

***REMOVED*** Test database connectivity
docker exec scanium-postgres pg_isready -U scanium
```

***REMOVED******REMOVED******REMOVED*** Cloudflare Tunnel Issues

```bash
***REMOVED*** Check tunnel container
docker logs scanium-cloudflared --tail 50

***REMOVED*** Verify tunnel is running
docker ps | grep cloudflared
```

***REMOVED******REMOVED*** Rollback

If deployment fails and you need to rollback:

```bash
cd /volume1/docker/scanium/backend

***REMOVED*** Find previous commit
git log --oneline -10

***REMOVED*** Checkout previous version
git checkout PREVIOUS_COMMIT_HASH

***REMOVED*** Rebuild and restart
docker compose down api
docker compose build --no-cache api
docker compose up -d api
```

***REMOVED******REMOVED*** Monitoring Commands

```bash
***REMOVED*** Real-time logs
docker logs -f scanium-api

***REMOVED*** Container stats
docker stats scanium-api

***REMOVED*** Inspect container
docker inspect scanium-api

***REMOVED*** Check all running Scanium containers
docker ps --filter "name=scanium"
```

***REMOVED******REMOVED*** Pre-Deployment Checklist

Before deploying:

1. [ ] Tests pass locally: `npm test`
2. [ ] TypeScript compiles: `npm run typecheck`
3. [ ] Build succeeds: `npm run build`
4. [ ] Docker builds locally: `docker build -t scanium-backend:test .`
5. [ ] Changes committed and pushed to main

***REMOVED******REMOVED*** Post-Deployment Verification

After deploying:

1. [ ] Container is running: `docker ps | grep scanium-api`
2. [ ] Health check passes: `curl http://localhost:8080/health`
3. [ ] Warmup returns 200: `curl -X POST http://localhost:8080/v1/assist/warmup -H "X-API-Key: KEY"`
4. [ ] External access works: `curl -X POST https://scanium.gtemp1.com/v1/assist/warmup -H "X-API-Key: KEY"`
5. [ ] Logs show no errors: `docker logs scanium-api --tail 50`
