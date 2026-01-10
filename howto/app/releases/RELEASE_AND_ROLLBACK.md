# Scanium Release and Rollback Guide

This guide covers deploying and rolling back the Scanium backend and monitoring stack on NAS.

## Table of Contents

- [Backend Releases](#backend-releases)
  - [Overview](#backend-overview)
  - [Why dist/ Changes Don't Deploy](#why-dist-changes-dont-deploy)
  - [Image Tagging Strategy](#backend-tagging-strategy)
  - [Deploying a New Version](#deploying-backend)
  - [Rolling Back](#rolling-back-backend)
  - [Managing Image Storage](#managing-image-storage)
  - [Troubleshooting](#backend-troubleshooting)
- [Monitoring Releases](#monitoring-releases)
  - [Overview](#monitoring-overview)
  - [Git Tag Strategy](#monitoring-tagging-strategy)
  - [Deploying Monitoring Changes](#deploying-monitoring)
  - [Rolling Back Monitoring](#rolling-back-monitoring)
  - [Verification Gates](#monitoring-verification)
  - [Troubleshooting](#monitoring-troubleshooting)

---

# Backend Releases

## Backend Overview

The Scanium backend deployment system uses **NAS-local Docker image tagging** for version control and rollback capability. This approach:

- Builds uniquely tagged images on each deploy
- Keeps images locally on NAS (no external registry)
- Allows instant rollback to any previous image
- Enables safe testing and quick recovery

## Why dist/ Changes Don't Deploy

**Important:** Changes to `backend/dist/` do NOT automatically deploy to production.

The backend Docker image is built **from source code** inside the Docker build process. The `dist/` directory is generated during the Docker build, not used as input.

**Deployment workflow:**
1. Code changes are committed to `main` branch
2. Deploy script pulls latest `main`
3. Docker builds the backend from source (runs `npm run build` inside container)
4. Tagged image is created and deployed

**TL;DR:** Commit source changes to `main`, then run the deploy script. The `dist/` directory is an artifact, not a deployment input.

## Backend Tagging Strategy

Images are tagged with: `YYYY.MM.DD-<shortSHA>`

**Example:** `2026.01.10-abc123f`

- `YYYY.MM.DD` - UTC date of deployment
- `shortSHA` - 7-character git commit hash

This provides:
- Human-readable chronological ordering
- Traceability back to exact git commit
- Uniqueness (no tag collisions)

## Deploying Backend

### Prerequisites

- Changes merged to `main` branch
- SSH access to NAS
- Docker permissions on NAS

### Deploy Command

From your local machine:

```bash
ssh nas "cd /volume1/docker/scanium/repo && bash scripts/app/deploy-backend-nas.sh"
```

Or directly on NAS:

```bash
cd /volume1/docker/scanium/repo
bash scripts/app/deploy-backend-nas.sh
```

### What the Deploy Script Does

1. Updates repo from `origin/main`
2. Computes image tag from current git commit
3. Builds Docker image: `scanium-backend:<TAG>`
4. Recreates backend container with new image
5. Waits for health check to pass
6. Verifies deployment
7. Displays deployed tag and recent tags

### Expected Output

```
════════════════════════════════════════════════════════════
Scanium Backend Deployment - NAS Local Tagged Build
════════════════════════════════════════════════════════════

[1/8] Updating repository from origin/main...
✓ Repository updated

[2/8] Computing image tag...
✓ Image tag: 2026.01.10-abc123f

[3/8] Setting BACKEND_TAG environment variable...
✓ BACKEND_TAG=2026.01.10-abc123f

[4/8] Building backend image (scanium-backend:2026.01.10-abc123f)...
✓ Image built

[5/8] Verifying image tag...
✓ Image scanium-backend:2026.01.10-abc123f exists

[6/8] Deploying backend container...
✓ Container recreated

[7/8] Waiting for backend to be healthy...
✓ Backend is healthy

[8/8] Verifying deployment...
✓ Container is running with image: scanium-backend:2026.01.10-abc123f
✓ Health endpoint responding

════════════════════════════════════════════════════════════
DEPLOYMENT COMPLETE
════════════════════════════════════════════════════════════
DEPLOYED_BACKEND_TAG=2026.01.10-abc123f
```

## Rolling Back Backend

### When to Roll Back

- New deployment causes errors
- Performance degradation detected
- Need to revert to known-good state quickly

### List Available Tags

```bash
ssh nas "cd /volume1/docker/scanium/repo && bash scripts/app/deploy-backend-nas.sh --list-tags"
```

Or:

```bash
ssh nas "docker images scanium-backend"
```

### Rollback Command

Replace `<TAG>` with the target version tag:

```bash
ssh nas "cd /volume1/docker/scanium/repo && bash scripts/app/rollback-backend-nas.sh <TAG>"
```

**Example:**

```bash
ssh nas "cd /volume1/docker/scanium/repo && bash scripts/app/rollback-backend-nas.sh 2026.01.09-def4567"
```

### What the Rollback Script Does

1. Verifies target image exists locally
2. Shows current vs target image
3. Prompts for confirmation if already running target
4. Recreates container with target image
5. Waits for health check
6. Verifies rollback success

### Expected Output

```
════════════════════════════════════════════════════════════
Scanium Backend Rollback - NAS Local
════════════════════════════════════════════════════════════

Target tag: 2026.01.09-def4567

[1/5] Verifying image scanium-backend:2026.01.09-def4567 exists...
✓ Image exists

[2/5] Checking current deployment...
   Current image: scanium-backend:2026.01.10-abc123f
   Target image:  scanium-backend:2026.01.09-def4567

[3/5] Setting BACKEND_TAG and redeploying...
✓ Container recreated

[4/5] Waiting for backend to be healthy...
✓ Backend is healthy

[5/5] Verifying rollback...
✓ Container is running with image: scanium-backend:2026.01.09-def4567
✓ Health endpoint responding

════════════════════════════════════════════════════════════
ROLLBACK COMPLETE
════════════════════════════════════════════════════════════
Previous image: scanium-backend:2026.01.10-abc123f
Current image:  scanium-backend:2026.01.09-def4567

✓ Successfully rolled back to: 2026.01.09-def4567
```

## Managing Image Storage

### Disk Usage

Docker images consume disk space. Each backend image is approximately 570MB.

### List Images with Sizes

```bash
ssh nas "docker images scanium-backend --format 'table {{.Tag}}\t{{.CreatedAt}}\t{{.Size}}'"
```

### Cleanup Strategy

**Keep last N deployments** (e.g., keep last 10):

```bash
ssh nas "docker images scanium-backend --format '{{.Tag}}' | grep -v 'latest' | tail -n +11 | xargs -r -I {} docker rmi scanium-backend:{}"
```

This keeps:
- `latest` tag (always)
- Most recent 10 dated tags
- Removes older tags

**Manual cleanup:**

```bash
# Remove a specific tag
ssh nas "docker rmi scanium-backend:2026.01.05-xyz9876"

# Remove dangling images (untagged)
ssh nas "docker image prune -f"
```

### Recommended Cleanup Schedule

- **Weekly:** Remove images older than 2 weeks
- **Monthly:** Keep only last 10 deployments
- **Before major releases:** Clean up to free space

**Automated cleanup script** (example for keeping last 15 images):

```bash
#!/usr/bin/env bash
# cleanup-old-backend-images.sh
docker images scanium-backend --format '{{.Tag}}' | \
  grep -E '^[0-9]{4}\.[0-9]{2}\.[0-9]{2}' | \
  sort -r | \
  tail -n +16 | \
  xargs -r -I {} docker rmi scanium-backend:{} || true
```

## Backend Troubleshooting

### Health Check Fails After Deploy

**Check logs:**

```bash
ssh nas "docker logs scanium-backend --tail 100"
```

**Common issues:**
- Database migration pending
- Environment variable missing
- Port conflict

**Quick fix:** Roll back to previous working version while investigating.

### Image Tag Not Created

**Check if build succeeded:**

```bash
ssh nas "docker images | grep scanium-backend"
```

**Manual tag if needed:**

```bash
ssh nas "docker tag backend_api:latest scanium-backend:2026.01.10-abc123f"
```

### Container Won't Start

**Check container status:**

```bash
ssh nas "docker ps -a | grep scanium-backend"
ssh nas "docker inspect scanium-backend"
```

**Force remove and recreate:**

```bash
ssh nas "cd /volume1/docker/scanium/repo/backend && docker-compose down api && docker-compose up -d api"
```

### Out of Disk Space

**Check Docker disk usage:**

```bash
ssh nas "docker system df"
```

**Clean up:**

```bash
# Remove unused images
ssh nas "docker image prune -a -f"

# Remove unused volumes (CAREFUL - check what will be removed first)
ssh nas "docker volume ls"
ssh nas "docker volume prune -f"
```

### Git Pull Fails (Dirty Repo)

**Check git status:**

```bash
ssh nas "cd /volume1/docker/scanium/repo && git status"
```

**Stash or discard local changes:**

```bash
# Stash changes
ssh nas "cd /volume1/docker/scanium/repo && git stash"

# Or discard (DESTRUCTIVE)
ssh nas "cd /volume1/docker/scanium/repo && git reset --hard origin/main"
```

### Wrong Image Running

**Check what image is actually running:**

```bash
ssh nas "docker inspect scanium-backend --format='{{.Config.Image}}'"
```

**Force recreate with specific tag:**

```bash
ssh nas "cd /volume1/docker/scanium/repo && bash scripts/app/rollback-backend-nas.sh <TAG>"
```

---

# Monitoring Releases

## Monitoring Overview

The Scanium monitoring stack (Grafana, Mimir, Loki, Tempo, Alloy) uses **git tags** for version control instead of Docker image tags. This is because:

- Monitoring services use upstream images (Grafana Labs official images)
- Configuration changes (compose, Alloy config, dashboards) are tracked in git
- Rollback means checking out a previous git tag, not swapping Docker images

**Key differences from backend:**
- Backend: Docker image tags (e.g., `scanium-backend:2026.01.10-abc123`)
- Monitoring: Git tags (e.g., `monitoring-2026.01.10-abc123`)

## Monitoring Tagging Strategy

Tags follow the format: `monitoring-YYYY.MM.DD-<shortSHA>`

**Example:** `monitoring-2026.01.10-abc123f`

- `monitoring-` prefix distinguishes from backend tags
- `YYYY.MM.DD` - UTC date of deployment
- `shortSHA` - 7-character git commit hash

**When to create a tag:**
- After successful monitoring stack deployment
- After configuration changes (Alloy, Grafana datasources, dashboards)
- Before risky changes (for easy rollback)

## Deploying Monitoring

### Prerequisites

- Changes merged to `main` branch
- SSH access to NAS
- Docker permissions on NAS

### Deploy Command

From your local machine:

```bash
ssh nas "cd /volume1/docker/scanium/repo && bash scripts/monitoring/deploy-monitoring-nas.sh"
```

Or directly on NAS:

```bash
cd /volume1/docker/scanium/repo
bash scripts/monitoring/deploy-monitoring-nas.sh
```

### What the Deploy Script Does

1. **Preflight checks:**
   - Detects orphan/duplicate containers (prevents DNS poisoning)
   - Warns about exited containers

2. **Repository update:**
   - Pulls latest `main` branch
   - Computes deployment tag

3. **Stack deployment:**
   - Runs `docker-compose down --remove-orphans`
   - Runs `docker-compose up -d`
   - Waits for all containers to become healthy

4. **Verification:**
   - Runs `verify-monitoring.sh` to check:
     - Grafana API and datasources
     - Mimir metrics queries
     - Tempo traces API
     - Loki logs API

5. **Tag suggestion:**
   - Prints suggested tag for this deployment
   - Instructions for creating the tag

### Expected Output

```
════════════════════════════════════════════════════════════
Scanium Monitoring Stack Deployment
════════════════════════════════════════════════════════════

[PREFLIGHT] Checking for orphan containers...
✓ No existing monitoring containers found (clean slate)

[1/5] Updating repository from origin/main...
✓ Repository updated

Deploy tag: monitoring-2026.01.10-abc123f

[2/5] Stopping monitoring stack...
✓ Stack stopped and orphans removed

[3/5] Starting monitoring stack...
✓ Stack started

[4/5] Waiting for health checks (max 90s)...
   Health: 5/5 containers healthy (waiting...)
✓ All containers healthy

[5/5] Running monitoring verification...
════════════════════════════════════════════════════════════
Scanium Monitoring Stack Verification
════════════════════════════════════════════════════════════

[1/4] Grafana Health...
✓ Grafana: API healthy
✓ Grafana: All datasources configured (Mimir, Loki, Tempo)

[2/4] Mimir Metrics...
✓ Mimir: Ready endpoint OK
✓ Mimir: Pipeline metrics present (up{source="pipeline"})
✓ Mimir: Backend metrics present (up{job="scanium-backend"})

[3/4] Tempo Traces...
✓ Tempo: Ready endpoint OK
✓ Tempo: API responding
⚠ Tempo: No traffic validation (empty trace store is normal initially)

[4/4] Loki Logs...
✓ Loki: Ready endpoint OK
⚠ Loki: No labels found (logs ingestion may not be working)

════════════════════════════════════════════════════════════
Verification Summary
════════════════════════════════════════════════════════════
⚠ PASSED WITH WARNINGS
  - 2 warning(s) detected
  - All critical systems operational
  - Review warnings above for non-critical issues

════════════════════════════════════════════════════════════
DEPLOYMENT COMPLETE
════════════════════════════════════════════════════════════
Deployed at: 2026-01-10 18:00:00 UTC
Git commit: abc123f
Suggested tag: monitoring-2026.01.10-abc123f

To tag this release:
  git tag -a monitoring-2026.01.10-abc123f -m 'Monitoring release'
  git push origin monitoring-2026.01.10-abc123f
```

### Creating a Monitoring Tag

After successful deployment, create a git tag on NAS:

```bash
ssh nas "cd /volume1/docker/scanium/repo && \
  git tag -a monitoring-2026.01.10-abc123f -m 'Monitoring release' && \
  git push origin monitoring-2026.01.10-abc123f"
```

## Rolling Back Monitoring

### When to Roll Back

- New monitoring configuration causes issues
- Dashboard changes break visualizations
- Alloy configuration errors
- Need to revert to known-good state quickly

### List Available Tags

```bash
ssh nas "cd /volume1/docker/scanium/repo && git tag -l 'monitoring-*'"
```

### Rollback Command

Replace `<TAG>` with the target monitoring tag:

```bash
ssh nas "cd /volume1/docker/scanium/repo && bash scripts/monitoring/rollback-monitoring-nas.sh <TAG>"
```

**Example:**

```bash
ssh nas "cd /volume1/docker/scanium/repo && bash scripts/monitoring/rollback-monitoring-nas.sh monitoring-2026.01.09-def4567"
```

### What the Rollback Script Does

1. Verifies target git tag exists
2. Shows current vs target commit
3. Checks out target tag (detached HEAD state)
4. Runs `docker-compose down --remove-orphans`
5. Runs `docker-compose up -d`
6. Waits for health checks
7. Runs verification script

### Expected Output

```
════════════════════════════════════════════════════════════
Scanium Monitoring Stack Rollback
════════════════════════════════════════════════════════════

Target tag: monitoring-2026.01.09-def4567

[1/5] Verifying git tag exists...
✓ Tag exists

[2/5] Checking current state...
   Current: main @ abc123f
   Target:  monitoring-2026.01.09-def4567 @ def4567

[3/5] Checking out tag monitoring-2026.01.09-def4567...
✓ Checked out monitoring-2026.01.09-def4567

[4/5] Redeploying monitoring stack...
✓ Stack redeployed

Waiting for health checks (max 90s)...
✓ All containers healthy

[5/5] Running monitoring verification...
[... verification output ...]

════════════════════════════════════════════════════════════
ROLLBACK COMPLETE
════════════════════════════════════════════════════════════
Rolled back to: monitoring-2026.01.09-def4567
Commit: def4567

⚠ NOTE: You are now in detached HEAD state
To return to main branch:
  git checkout main
```

**Important:** After rollback, you're in detached HEAD state. Return to main when ready:

```bash
ssh nas "cd /volume1/docker/scanium/repo && git checkout main"
```

## Monitoring Verification

The `verify-monitoring.sh` script checks critical monitoring components:

### What It Checks

**1. Grafana:**
- API health endpoint
- Datasources configured (Mimir, Loki, Tempo)

**2. Mimir (Metrics):**
- Ready endpoint
- Pipeline metrics: `up{source="pipeline"}`
- Backend metrics: `up{job="scanium-backend"}`

**3. Tempo (Traces):**
- Ready endpoint
- API responding
- (Note: Empty trace store is normal if no traffic)

**4. Loki (Logs):**
- Ready endpoint
- Labels present
- (Warning: Logs ingestion may be optional/not yet working)

### Exit Codes

- `0` - All checks passed (warnings OK)
- `1` - Critical failure (deployment should be considered failed)

### Manual Verification

Run verification independently:

```bash
ssh nas "cd /volume1/docker/scanium/repo && bash scripts/monitoring/verify-monitoring.sh"
```

## Monitoring Troubleshooting

### Orphan Container DNS Issues

**Symptom:** Queries fail even though services are running. Alloy can't reach Mimir/Loki/Tempo.

**Cause:** Duplicate containers cause Docker DNS to resolve to the wrong (stopped) container.

**Fix:**

```bash
ssh nas "cd /volume1/docker/scanium/repo/monitoring && \
  docker-compose down --remove-orphans && \
  docker ps -a --filter 'name=scanium-' | grep -E 'mimir|loki|tempo|grafana|alloy' | awk '{print \$1}' | xargs docker rm -f && \
  docker-compose up -d"
```

**Prevention:** Always use `--remove-orphans` flag (deploy script does this automatically).

**Verify DNS resolution from Alloy:**

```bash
ssh nas "docker exec scanium-alloy getent hosts mimir"
ssh nas "docker exec scanium-alloy getent hosts loki"
ssh nas "docker exec scanium-alloy getent hosts tempo"
```

### Mimir Not Showing Recent Metrics

**Symptom:** Metrics from last few hours missing in Grafana.

**Cause:** Mimir querier not configured to query ingesters for recent data.

**Fix:** Ensure these flags are in `monitoring/docker-compose.yml` (already included):

```yaml
command:
  - -querier.query-ingesters-within=12h
  - -querier.query-store-after=0s
```

**Verify:**

```bash
ssh nas "docker exec scanium-mimir wget -q -O- 'http://localhost:9009/prometheus/api/v1/query?query=up{source=\"pipeline\"}' | jq"
```

### Loki Logs Not Appearing

**Status:** Loki logs ingestion may be optional or not yet fully configured.

**Expected:** Verification warns about missing labels but doesn't fail deployment.

**Debug:**

1. Check Loki is ready:
   ```bash
   ssh nas "docker exec scanium-loki wget -q -O- http://localhost:3100/ready"
   ```

2. Check Alloy logs for Loki write errors:
   ```bash
   ssh nas "docker logs scanium-alloy --tail 100 | grep -i loki"
   ```

3. Check docker.sock permissions (if using docker log scraping):
   ```bash
   ssh nas "docker exec scanium-alloy ls -la /var/run/docker.sock"
   ```

### Grafana Datasources Missing

**Symptom:** Dashboards show "Data source not found".

**Fix:**

1. Check datasource provisioning:
   ```bash
   ssh nas "docker exec scanium-grafana ls -la /etc/grafana/provisioning/datasources/"
   ```

2. Check datasource UIDs match dashboard JSON:
   - Mimir UID: `MIMIR`
   - Loki UID: `LOKI`
   - Tempo UID: `TEMPO`

3. Restart Grafana:
   ```bash
   ssh nas "cd /volume1/docker/scanium/repo/monitoring && docker-compose restart grafana"
   ```

### All Containers Unhealthy After Deploy

**Check logs:**

```bash
ssh nas "docker logs scanium-grafana --tail 50"
ssh nas "docker logs scanium-mimir --tail 50"
ssh nas "docker logs scanium-loki --tail 50"
ssh nas "docker logs scanium-tempo --tail 50"
ssh nas "docker logs scanium-alloy --tail 50"
```

**Common causes:**
- Config file syntax errors
- Port conflicts
- Permission issues on data volumes
- Network issues

**Quick restart:**

```bash
ssh nas "cd /volume1/docker/scanium/repo/monitoring && docker-compose restart"
```

---

## Best Practices

1. **Always test in dev first** - Don't deploy untested code to NAS
2. **Deploy during low-traffic windows** - Minimize user impact
3. **Keep last 10-15 images** - Balance between rollback capability and disk usage
4. **Document major changes** - Note what changed in git commit messages
5. **Monitor after deploy** - Watch logs and metrics for 10-15 minutes post-deploy
6. **Have rollback plan ready** - Know the last known-good tag before deploying

## Related Documentation

- [Monitoring Guide](./MONITORING.md) - Observability and metrics
- [Backend README](../backend/README.md) - Backend architecture and development

---

**Last updated:** 2026-01-10
**Maintained by:** Release Engineering
