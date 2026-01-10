# Scanium Backend Release and Rollback Guide

This guide covers deploying and rolling back the Scanium backend on NAS using locally tagged Docker images.

## Table of Contents

- [Overview](#overview)
- [Why dist/ Changes Don't Deploy](#why-dist-changes-dont-deploy)
- [Image Tagging Strategy](#image-tagging-strategy)
- [Deploying a New Version](#deploying-a-new-version)
- [Rolling Back](#rolling-back)
- [Managing Image Storage](#managing-image-storage)
- [Troubleshooting](#troubleshooting)

## Overview

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

## Image Tagging Strategy

Images are tagged with: `YYYY.MM.DD-<shortSHA>`

**Example:** `2026.01.10-abc123f`

- `YYYY.MM.DD` - UTC date of deployment
- `shortSHA` - 7-character git commit hash

This provides:
- Human-readable chronological ordering
- Traceability back to exact git commit
- Uniqueness (no tag collisions)

## Deploying a New Version

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

## Rolling Back

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

## Troubleshooting

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
