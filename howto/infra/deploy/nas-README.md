# Scanium NAS Deployment Guide

Deploy Scanium backend and monitoring stack on a Synology NAS (DS418play or similar x86_64 NAS)
using Docker Compose via Synology Container Manager.

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Directory Structure](#directory-structure)
4. [Step 1: Prepare the NAS](#step-1-prepare-the-nas)
5. [Step 2: Build the Backend Image](#step-2-build-the-backend-image)
6. [Step 3: Deploy Monitoring Stack](#step-3-deploy-monitoring-stack)
7. [Step 4: Deploy Backend Stack](#step-4-deploy-backend-stack)
8. [Step 5: Database Migrations](#step-5-database-migrations)
9. [Verification Checklist](#verification-checklist)
10. [Exposing Services Securely](#exposing-services-securely)
11. [Maintenance](#maintenance)
12. [Common Failures & Troubleshooting](#common-failures--troubleshooting)

---

## Overview

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Synology NAS                              │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                    Docker Network                           │ │
│  │                                                             │ │
│  │  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐  │ │
│  │  │   Backend    │    │  PostgreSQL  │    │   Grafana    │  │ │
│  │  │   :8080      │───▶│   :5432      │    │   :3000      │  │ │
│  │  └──────────────┘    └──────────────┘    └──────────────┘  │ │
│  │         │                                       ▲          │ │
│  │         │ OTLP                                  │          │ │
│  │         ▼                                       │          │ │
│  │  ┌──────────────┐                               │          │ │
│  │  │    Alloy     │───────────────────────────────┤          │ │
│  │  │ :4317/:4318  │                               │          │ │
│  │  └──────────────┘                               │          │ │
│  │         │                                       │          │ │
│  │    ┌────┴────┬──────────┐                       │          │ │
│  │    ▼         ▼          ▼                       │          │ │
│  │  ┌────┐   ┌─────┐   ┌─────┐                     │          │ │
│  │  │Loki│   │Tempo│   │Mimir│─────────────────────┘          │ │
│  │  │:3100   │:3200│   │:9009│                                │ │
│  │  └────┘   └─────┘   └─────┘                                │ │
│  └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### Resource Requirements

Target: Synology DS418play (Intel Celeron J3455, 2 cores, 6GB RAM)

| Service     | Memory Limit | CPU Limit | Notes                    |
|-------------|--------------|-----------|--------------------------|
| PostgreSQL  | 512 MB       | -         | Database                 |
| Backend API | 512 MB       | 1 core    | Node.js application      |
| Grafana     | 256 MB       | 0.5 core  | Dashboard UI             |
| Alloy       | 128 MB       | 0.25 core | Telemetry collector      |
| Loki        | 256 MB       | 0.5 core  | Log storage              |
| Tempo       | 256 MB       | 0.5 core  | Trace storage            |
| Mimir       | 256 MB       | 0.5 core  | Metrics storage          |
| **Total**   | **~2.2 GB**  | -         | Leaves ~3.8GB for system |

---

## Prerequisites

1. **Synology NAS** with Container Manager installed
    - DSM 7.0 or later
    - x86_64 architecture (DS418play, DS920+, etc.)
    - At least 4GB RAM (6GB+ recommended)

2. **SSH access** to NAS (for initial setup)
    - Enable in Control Panel → Terminal & SNMP

3. **Development machine** (Mac/Linux) with:
    - Docker Desktop
    - Git

4. **Network access** to NAS from your mobile device

---

## Directory Structure

On your NAS, create this structure:

```
/volume1/docker/scanium/
├── backend.env              # Backend environment variables (from env/backend.env.example)
├── monitoring.env           # Monitoring environment variables (from env/monitoring.env.example)
├── postgres/                # PostgreSQL data (auto-created)
├── secrets/                 # Optional: Google Vision credentials
│   └── vision-sa.json
└── monitoring/
    ├── alloy/
    │   └── alloy.hcl        # Copy from repo: monitoring/alloy/alloy.hcl
    ├── loki/
    │   └── loki.yaml        # Copy from repo: monitoring/loki/loki.yaml
    ├── tempo/
    │   └── tempo.yaml       # Copy from repo: monitoring/tempo/tempo.yaml
    ├── mimir/
    │   └── mimir.yaml       # Copy from repo: monitoring/mimir/mimir.yaml
    └── grafana/
        ├── provisioning/    # Copy from repo: monitoring/grafana/provisioning/
        └── dashboards/      # Copy from repo: monitoring/grafana/dashboards/
```

---

## Step 1: Prepare the NAS

### 1.1 Create Directory Structure

SSH into your NAS:

```bash
ssh admin@YOUR_NAS_IP
```

Create directories:

```bash
# Create base directory
sudo mkdir -p /volume1/docker/scanium

# Create subdirectories
sudo mkdir -p /volume1/docker/scanium/{postgres,secrets}
sudo mkdir -p /volume1/docker/scanium/monitoring/{alloy,loki,tempo,mimir,grafana}
sudo mkdir -p /volume1/docker/scanium/monitoring/grafana/{provisioning,dashboards}

# Set permissions (allow Docker to write)
sudo chmod -R 777 /volume1/docker/scanium
```

### 1.2 Copy Configuration Files

From your development machine, copy the monitoring configs:

```bash
# From the repo root directory
NAS_IP="YOUR_NAS_IP"
NAS_PATH="/volume1/docker/scanium"

# Copy monitoring configs
scp monitoring/alloy/alloy.hcl admin@$NAS_IP:$NAS_PATH/monitoring/alloy/
scp monitoring/loki/loki.yaml admin@$NAS_IP:$NAS_PATH/monitoring/loki/
scp monitoring/tempo/tempo.yaml admin@$NAS_IP:$NAS_PATH/monitoring/tempo/
scp monitoring/mimir/mimir.yaml admin@$NAS_IP:$NAS_PATH/monitoring/mimir/

# Copy Grafana provisioning
scp -r monitoring/grafana/provisioning/* admin@$NAS_IP:$NAS_PATH/monitoring/grafana/provisioning/
scp -r monitoring/grafana/dashboards/* admin@$NAS_IP:$NAS_PATH/monitoring/grafana/dashboards/
```

### 1.3 Create Environment Files

Copy example files and edit them:

```bash
# Copy env examples to NAS
scp deploy/nas/env/backend.env.example admin@$NAS_IP:$NAS_PATH/backend.env
scp deploy/nas/env/monitoring.env.example admin@$NAS_IP:$NAS_PATH/monitoring.env
```

SSH into NAS and edit the env files:

```bash
ssh admin@YOUR_NAS_IP
cd /volume1/docker/scanium

# Edit backend.env - fill in all CHANGE_ME values
nano backend.env

# Edit monitoring.env - set Grafana password
nano monitoring.env
```

**Required changes in `backend.env`:**

- `PUBLIC_BASE_URL` → `http://YOUR_NAS_IP:8080`
- `DATABASE_URL` → Update password to match `POSTGRES_PASSWORD`
- `POSTGRES_PASSWORD` → Generate with `openssl rand -base64 24`
- `SCANIUM_API_KEYS` → Generate with `openssl rand -hex 32`
- `SESSION_SIGNING_SECRET` → Generate with `openssl rand -base64 64`

**Required changes in `monitoring.env`:**

- `GF_SECURITY_ADMIN_PASSWORD` → Generate with `openssl rand -base64 24`

### 1.4 Create Docker Network

```bash
# SSH into NAS
ssh admin@YOUR_NAS_IP

# Create shared network for backend and monitoring
sudo docker network create scanium-observability
```

---

## Step 2: Build the Backend Image

The backend needs to be built as a Docker image. You have two options:

### Option A: Build on Development Machine (Recommended)

Build on a faster machine and transfer to NAS:

```bash
# From the repo root, on your Mac/Linux machine
cd backend

# Build the image
docker build -t scanium-api:latest .

# Save image to file
docker save scanium-api:latest | gzip > scanium-api.tar.gz

# Copy to NAS
scp scanium-api.tar.gz admin@YOUR_NAS_IP:/volume1/docker/scanium/

# SSH to NAS and load image
ssh admin@YOUR_NAS_IP
sudo docker load < /volume1/docker/scanium/scanium-api.tar.gz

# Verify
sudo docker images | grep scanium-api
```

### Option B: Build Directly on NAS (Slower)

Copy the backend source and build on NAS:

```bash
# From repo root on your machine
scp -r backend admin@YOUR_NAS_IP:/volume1/docker/scanium/backend-src

# SSH to NAS
ssh admin@YOUR_NAS_IP
cd /volume1/docker/scanium/backend-src
sudo docker build -t scanium-api:latest .

# Clean up source after build
rm -rf /volume1/docker/scanium/backend-src
```

---

## Step 3: Deploy Monitoring Stack

### 3.1 Via Synology Container Manager UI

1. Open **Container Manager** in DSM
2. Go to **Project** → **Create**
3. Project name: `scanium-monitoring`
4. Path: `/volume1/docker/scanium`
5. Source: **Upload docker-compose.yml**
    - Upload `deploy/nas/compose/docker-compose.nas.monitoring.yml`
6. Click **Next**
7. Environment file: Select `/volume1/docker/scanium/monitoring.env`
8. Review and click **Done**

<!-- Screenshot placeholder: Container Manager Create Project dialog -->

### 3.2 Via SSH (Alternative)

```bash
ssh admin@YOUR_NAS_IP
cd /volume1/docker/scanium

# Copy compose file
# (You should have already copied this, or copy now from your machine)

# Set environment variable for data path
export NAS_DATA_PATH=/volume1/docker/scanium
export NAS_CONFIG_PATH=/volume1/docker/scanium

# Start monitoring stack
sudo docker compose -f docker-compose.nas.monitoring.yml \
  --env-file monitoring.env up -d

# Check status
sudo docker compose -f docker-compose.nas.monitoring.yml ps
```

### 3.3 Verify Monitoring Stack

Wait 1-2 minutes for services to start, then:

```bash
# Check all containers are healthy
sudo docker ps --format "table {{.Names}}\t{{.Status}}"

# Expected output:
# NAMES              STATUS
# scanium-grafana    Up X minutes (healthy)
# scanium-alloy      Up X minutes (healthy)
# scanium-loki       Up X minutes (healthy)
# scanium-tempo      Up X minutes (healthy)
# scanium-mimir      Up X minutes (healthy)
```

Access Grafana at `http://YOUR_NAS_IP:3000`

- Login with credentials from `monitoring.env`

> **Important: Grafana Credential Persistence**
> Grafana stores admin credentials in its SQLite database (`/var/lib/grafana/grafana.db`) on first
> startup. After initialization:
> - Changing `GF_SECURITY_ADMIN_PASSWORD` in `monitoring.env` will NOT update the password
> - To reset password: delete `${NAS_DATA_PATH}/monitoring/grafana/grafana.db` and restart Grafana
> - Deleting the database also removes any manually created dashboards or users

---

## Step 4: Deploy Backend Stack

### 4.1 Via Synology Container Manager UI

1. Open **Container Manager** in DSM
2. Go to **Project** → **Create**
3. Project name: `scanium-backend`
4. Path: `/volume1/docker/scanium`
5. Source: **Upload docker-compose.yml**
    - Upload `deploy/nas/compose/docker-compose.nas.backend.yml`
6. Click **Next**
7. Environment file: Select `/volume1/docker/scanium/backend.env`
8. Review and click **Done**

### 4.2 Via SSH (Alternative)

```bash
ssh admin@YOUR_NAS_IP
cd /volume1/docker/scanium

# Set environment variables
export NAS_DATA_PATH=/volume1/docker/scanium

# Start backend stack
sudo docker compose -f docker-compose.nas.backend.yml \
  --env-file backend.env up -d

# Check status
sudo docker compose -f docker-compose.nas.backend.yml ps
```

---

## Step 5: Database Migrations

After the backend container starts for the first time, you need to run Prisma migrations:

### 5.1 Check Migration Status

```bash
ssh admin@YOUR_NAS_IP

# Check current migration status
sudo docker exec scanium-api npx prisma migrate status
```

### 5.2 Run Migrations

```bash
# Deploy pending migrations
sudo docker exec scanium-api npx prisma migrate deploy

# Verify
sudo docker exec scanium-api npx prisma migrate status
```

**Expected output:**

```
Database schema is up to date!
```

### 5.3 (If needed) Generate Prisma Client

The Dockerfile already generates the Prisma client during build. If you need to regenerate:

```bash
sudo docker exec scanium-api npx prisma generate
```

---

## Verification Checklist

### Backend Verification

```bash
# 1. Check container health
sudo docker ps --filter name=scanium-api --format "{{.Status}}"
# Expected: Up X minutes (healthy)

# 2. Check health endpoint
curl -s http://YOUR_NAS_IP:8080/health
# Expected: {"status":"ok","timestamp":"..."}

# 3. Check API is responding (should return 401 without API key)
curl -s -o /dev/null -w "%{http_code}" http://YOUR_NAS_IP:8080/api/v1/classifier
# Expected: 401

# 4. Check database connection
sudo docker exec scanium-api npx prisma migrate status
# Expected: "Database schema is up to date!"

# 5. View logs
sudo docker logs scanium-api --tail 50
```

### Monitoring Verification

```bash
# 1. Check all monitoring containers
sudo docker ps --filter "name=scanium-" --format "table {{.Names}}\t{{.Status}}"

# 2. Check Grafana health
curl -s http://YOUR_NAS_IP:3000/api/health
# Expected: {"commit":"...","database":"ok","version":"..."}

# 3. Check Alloy OTLP endpoint is listening
curl -s -o /dev/null -w "%{http_code}" http://YOUR_NAS_IP:4318/v1/traces
# Expected: 405 (Method Not Allowed - but shows it's listening)

# 4. Check Loki is ready
curl -s http://localhost:3100/ready
# Expected: ready (from NAS itself)

# 5. Access Grafana UI
# Open http://YOUR_NAS_IP:3000 in browser
# Login with admin credentials from monitoring.env
```

### Local Mac Dry-Run

Before deploying to NAS, test locally:

```bash
cd deploy/nas/compose

# Create test env files
cp ../env/backend.env.example ../env/backend.env
cp ../env/monitoring.env.example ../env/monitoring.env
# Edit files with test values

# Create local data directory
mkdir -p /tmp/scanium-test

# Override NAS paths for local test
export NAS_DATA_PATH=/tmp/scanium-test
export NAS_CONFIG_PATH=/tmp/scanium-test

# Copy configs locally (from repo root)
mkdir -p /tmp/scanium-test/monitoring/{alloy,loki,tempo,mimir,grafana}
cp ../../../monitoring/alloy/alloy.hcl /tmp/scanium-test/monitoring/alloy/
cp ../../../monitoring/loki/loki.yaml /tmp/scanium-test/monitoring/loki/
cp ../../../monitoring/tempo/tempo.yaml /tmp/scanium-test/monitoring/tempo/
cp ../../../monitoring/mimir/mimir.yaml /tmp/scanium-test/monitoring/mimir/
cp -r ../../../monitoring/grafana/provisioning /tmp/scanium-test/monitoring/grafana/
cp -r ../../../monitoring/grafana/dashboards /tmp/scanium-test/monitoring/grafana/

# Create network
docker network create scanium-observability || true

# Start monitoring (in a separate terminal)
docker compose -f docker-compose.nas.monitoring.yml --env-file ../env/monitoring.env up

# Build and start backend (in another terminal)
cd ../../../backend && docker build -t scanium-api:latest .
cd ../deploy/nas/compose
docker compose -f docker-compose.nas.backend.yml --env-file ../env/backend.env up

# Test health endpoint
curl http://localhost:8080/health

# Test Grafana
open http://localhost:3000

# Cleanup
docker compose -f docker-compose.nas.backend.yml --env-file ../env/backend.env down
docker compose -f docker-compose.nas.monitoring.yml --env-file ../env/monitoring.env down
```

---

## Exposing Services Securely

### LAN-Only Access (Default)

By default, services are only accessible on your local network:

- Backend API: `http://YOUR_NAS_IP:8080`
- Grafana: `http://YOUR_NAS_IP:3000`
- OTLP endpoint: `http://YOUR_NAS_IP:4318`

This is safe for home use where your network is trusted.

### External Access Options

#### Option 1: Cloudflare Tunnel (Recommended)

Provides secure HTTPS access without port forwarding:

1. Create a Cloudflare Tunnel at [dash.cloudflare.com](https://dash.cloudflare.com) → Zero Trust →
   Networks → Tunnels
2. Get the tunnel token
3. Edit `backend.env`:
   ```
   CLOUDFLARED_TOKEN=your_tunnel_token_here
   ```
4. Uncomment the `cloudflared` service in `docker-compose.nas.backend.yml`
5. Restart the backend stack

#### Option 2: Reverse Proxy with SSL

Use Synology's built-in reverse proxy:

1. Control Panel → Login Portal → Advanced → Reverse Proxy
2. Create rules for each service with SSL certificate
3. Use Synology's DDNS or your own domain

#### Option 3: VPN (Most Secure)

Use Synology VPN Server or WireGuard:

1. Install VPN Server package
2. Configure OpenVPN or L2TP
3. Access NAS services through VPN tunnel

**Do NOT** expose ports directly to the internet without authentication/encryption.

---

## NAS Performance Tuning

### Monitoring Stack Configuration

The monitoring stack configurations in `monitoring/` have been optimized for the DS418play's
resource constraints (2 cores, 4 threads, 6GB RAM). These tuned values differ from development
workstation configurations.

#### Key Configuration Differences

| Setting                      | Dev Value | NAS Value | File                              | Rationale                          |
|------------------------------|-----------|-----------|-----------------------------------|------------------------------------|
| **Tempo: Storage Workers**   | 100       | 4         | `monitoring/tempo/tempo.yaml:40`  | Match NAS thread count (4 threads) |
| **Tempo: Queue Depth**       | 10000     | 1000      | `monitoring/tempo/tempo.yaml:41`  | Reduce memory pressure             |
| **Mimir: Query Parallelism** | 32        | 4         | `monitoring/mimir/mimir.yaml:102` | Prevent CPU contention             |
| **Mimir: Index Cache**       | 512MB     | 128MB     | `monitoring/mimir/mimir.yaml:37`  | Conserve RAM for other services    |
| **Alloy: Scrape Interval**   | 15s       | 60s       | `monitoring/alloy/config.alloy`   | Reduce CPU and I/O load            |
| **Loki: Retention**          | 168h (7d) | 72h (3d)  | `monitoring/loki/loki.yaml:35`    | Conserve disk space                |

> **Note:** The index cache value in the table above shows the recommended NAS value (128MB).
> However, the current configuration uses 512MB as specified in line 37 of
`monitoring/mimir/mimir.yaml`. If you experience memory pressure, consider reducing this to 128MB.

#### Related Issues

These optimizations address the following performance issues:

- [#363](https://github.com/username/scanium/issues/363) - Tempo storage pool workers
- [#364](https://github.com/username/scanium/issues/364) - Prometheus scrape intervals

#### Verifying NAS-Optimized Configuration

After deploying, verify the tuned values are in effect:

```bash
# Check Tempo workers (should be 4)
sudo docker exec scanium-tempo grep -A2 "pool:" /etc/tempo/tempo.yaml

# Check Mimir query parallelism (should be 4)
sudo docker exec scanium-mimir grep "max_query_parallelism" /etc/mimir/mimir.yaml

# Check Alloy scrape interval (should be 60s)
sudo docker exec scanium-alloy grep "scrape_interval" /etc/alloy/config.alloy

# Check Loki retention (should be 72h)
sudo docker exec scanium-loki grep "retention_period" /etc/loki/loki.yaml
```

#### Performance Monitoring

Monitor resource usage to ensure the stack runs within NAS constraints:

```bash
# Check container memory usage (should stay under limits)
sudo docker stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}\t{{.CPUPerc}}"

# Monitor system resources
free -h  # Check available RAM
df -h    # Check disk space
```

Expected resource usage:

- **Total Memory:** ~2.2GB across all services
- **CPU Usage:** <50% under normal load
- **Disk Growth:** ~100MB/day with 3-day retention

#### Troubleshooting Performance Issues

**High CPU Usage (>70%):**

- Increase Alloy scrape intervals further (e.g., 120s)
- Reduce Mimir compaction frequency
- Consider disabling Tempo if traces aren't needed

**Memory Pressure (>5GB used):**

- Reduce Mimir index cache to 128MB
- Reduce Loki/Tempo retention periods
- Check for metric/log cardinality explosion

**Disk Space Issues:**

- Reduce retention periods further
- Enable compaction for Loki/Tempo
- Archive old data to external storage

## Maintenance

### Viewing Logs

```bash
# Backend logs
sudo docker logs scanium-api --tail 100 -f

# PostgreSQL logs
sudo docker logs scanium-postgres --tail 100 -f

# Grafana logs
sudo docker logs scanium-grafana --tail 100 -f

# All monitoring stack
sudo docker compose -f docker-compose.nas.monitoring.yml logs -f
```

### Restarting Services

```bash
# Restart single service
sudo docker restart scanium-api

# Restart entire stack
sudo docker compose -f docker-compose.nas.backend.yml --env-file backend.env restart
```

### Updating Images

```bash
# Pull latest images
sudo docker compose -f docker-compose.nas.monitoring.yml pull

# Recreate with new images
sudo docker compose -f docker-compose.nas.monitoring.yml --env-file monitoring.env up -d
```

### Backup Data

Important directories to backup:

- `/volume1/docker/scanium/postgres/` - Database
- `/volume1/docker/scanium/monitoring/grafana/` - Grafana data (dashboards, etc.)
- `/volume1/docker/scanium/*.env` - Environment files (store securely!)

```bash
# Example backup command
tar -czvf scanium-backup-$(date +%Y%m%d).tar.gz \
  /volume1/docker/scanium/postgres \
  /volume1/docker/scanium/monitoring/grafana
```

---

## Common Failures & Troubleshooting

### Container Fails to Start

**Symptom:** Container status shows "Exited" or keeps restarting

```bash
# Check logs
sudo docker logs scanium-api --tail 100

# Check if image exists
sudo docker images | grep scanium-api
```

**Solutions:**

- Missing image: Build or load the image (Step 2)
- Missing env file: Ensure `.env` files exist and have required values
- Missing network: Create with `docker network create scanium-observability`

### Port Conflicts

**Symptom:** "Address already in use" error

```bash
# Find what's using the port
sudo netstat -tlnp | grep :8080
```

**Solutions:**

- Change the port in `.env` file (e.g., `API_HOST_PORT=8081`)
- Stop conflicting service

### Permission Denied on Volumes

**Symptom:** "Permission denied" errors in container logs

```bash
# Fix permissions
sudo chmod -R 777 /volume1/docker/scanium
```

**Note:** Some containers run as root (`user: "0"`) to handle Synology's permission model.

### DATABASE_URL Connection Failed

**Symptom:** "Connection refused" or "Authentication failed"

```bash
# Check postgres is running
sudo docker ps | grep postgres

# Check postgres logs
sudo docker logs scanium-postgres

# Verify DATABASE_URL format
echo $DATABASE_URL
# Should be: postgresql://scanium:PASSWORD@scanium-postgres:5432/scanium
```

**Solutions:**

- Ensure `POSTGRES_PASSWORD` in env matches password in `DATABASE_URL`
- Use container name (`scanium-postgres`) not `localhost` in DATABASE_URL
- Ensure both containers are on same network

### ARM/x86 Image Mismatch

**Symptom:** "exec format error" when starting container

```bash
# Check image architecture
sudo docker inspect scanium-api | grep Architecture
```

**Solutions:**

- DS418play is x86_64 - ensure images are built for amd64
- Rebuild on an x86 machine, not Apple Silicon
- Use `--platform linux/amd64` when building on Apple Silicon:
  ```bash
  docker build --platform linux/amd64 -t scanium-api:latest .
  ```

### Out of Memory (OOM)

**Symptom:** Containers killed unexpectedly, "OOM killed" in logs

```bash
# Check memory usage
sudo docker stats --no-stream

# Check system memory
free -h
```

**Solutions:**

- Reduce memory limits in docker-compose
- Reduce Loki/Mimir retention periods
- Disable services not needed (e.g., Tempo if not using traces)
- Add more RAM to NAS

### Grafana Login Invalid (Wrong Password)

**Symptom:** "Invalid username or password" even with correct `monitoring.env` values

**Cause:** Grafana persists credentials in its SQLite database after first startup. Changing env
vars doesn't update stored credentials.

```bash
# Check what user Grafana has stored
sudo docker exec scanium-grafana sqlite3 /var/lib/grafana/grafana.db "SELECT login FROM user WHERE is_admin=1;"
```

**Solutions:**

1. **Reset password via CLI (preserves data):**
   ```bash
   sudo docker exec scanium-grafana grafana-cli admin reset-admin-password YOUR_NEW_PASSWORD
   sudo docker restart scanium-grafana
   ```

2. **Nuclear option (loses manual changes):**
   ```bash
   sudo docker compose -f docker-compose.nas.monitoring.yml down
   sudo rm -f ${NAS_DATA_PATH}/monitoring/grafana/grafana.db
   sudo docker compose -f docker-compose.nas.monitoring.yml --env-file monitoring.env up -d
   ```

### Grafana Can't Connect to Datasources

**Symptom:** "Bad Gateway" errors in Grafana

```bash
# Check datasource containers are healthy
sudo docker ps --filter "name=scanium-"

# Test from Grafana container
sudo docker exec scanium-grafana wget -qO- http://loki:3100/ready
```

**Solutions:**

- Ensure all monitoring services are on `scanium-observability` network
- Wait for services to become healthy (can take 1-2 minutes)
- Check datasource URLs use container names, not localhost

### Containers Healthy But No Data in Grafana

**Symptom:** All containers show "healthy" but Grafana dashboards are empty

**Troubleshooting steps:**

1. **Check time range in Grafana:** Default is "Last 6 hours". Select "Last 5 minutes" if you just
   started sending data.

2. **Verify OTLP ingestion:**
   ```bash
   # Check if Alloy is receiving data
   curl -s http://localhost:12345/metrics | grep otelcol_receiver_accepted
   # Should show non-zero values
   ```

3. **Check exporter health:**
   ```bash
   curl -s http://localhost:12345/metrics | grep otelcol_exporter_send_failed
   # Should be 0
   ```

4. **Test from app device:**
   ```bash
   # From device network, test OTLP endpoint
   curl -X POST http://NAS_IP:4318/v1/logs \
     -H "Content-Type: application/json" \
     -d '{"resourceLogs":[]}'
   # Should return empty 200 response
   ```

### Container Health Check Failing

**Symptom:** Container shows "(unhealthy)" status

```bash
# Run health check manually
sudo docker exec scanium-api node -e "require('http').get('http://localhost:8080/health', (r) => {console.log(r.statusCode)})"
```

**Solutions:**

- Check application logs for errors
- Ensure `start_period` is long enough for slow NAS
- Check container has network access

---

## Quick Reference

### Useful Commands

```bash
# View all Scanium containers
sudo docker ps --filter "name=scanium-"

# View container resource usage
sudo docker stats --filter "name=scanium-" --no-stream

# Enter container shell
sudo docker exec -it scanium-api sh

# View real-time logs
sudo docker logs -f scanium-api

# Check Docker disk usage
sudo docker system df

# Clean up unused images
sudo docker image prune -a
```

### Service URLs

| Service     | URL                  | Notes             |
|-------------|----------------------|-------------------|
| Backend API | `http://NAS_IP:8080` | Health: `/health` |
| Grafana     | `http://NAS_IP:3000` | Login required    |
| OTLP HTTP   | `http://NAS_IP:4318` | For app telemetry |
| OTLP gRPC   | `http://NAS_IP:4317` | For app telemetry |

### File Locations

| File                  | Purpose                     |
|-----------------------|-----------------------------|
| `backend.env`         | Backend secrets & config    |
| `monitoring.env`      | Grafana password & settings |
| `postgres/`           | PostgreSQL database files   |
| `monitoring/grafana/` | Grafana dashboards & data   |
