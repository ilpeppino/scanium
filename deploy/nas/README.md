***REMOVED*** Scanium NAS Deployment Guide

Deploy Scanium backend and monitoring stack on a Synology NAS (DS418play or similar x86_64 NAS) using Docker Compose via Synology Container Manager.

***REMOVED******REMOVED*** Table of Contents

1. [Overview](***REMOVED***overview)
2. [Prerequisites](***REMOVED***prerequisites)
3. [Directory Structure](***REMOVED***directory-structure)
4. [Step 1: Prepare the NAS](***REMOVED***step-1-prepare-the-nas)
5. [Step 2: Build the Backend Image](***REMOVED***step-2-build-the-backend-image)
6. [Step 3: Deploy Monitoring Stack](***REMOVED***step-3-deploy-monitoring-stack)
7. [Step 4: Deploy Backend Stack](***REMOVED***step-4-deploy-backend-stack)
8. [Step 5: Database Migrations](***REMOVED***step-5-database-migrations)
9. [Verification Checklist](***REMOVED***verification-checklist)
10. [Exposing Services Securely](***REMOVED***exposing-services-securely)
11. [Maintenance](***REMOVED***maintenance)
12. [Common Failures & Troubleshooting](***REMOVED***common-failures--troubleshooting)

---

***REMOVED******REMOVED*** Overview

***REMOVED******REMOVED******REMOVED*** Architecture

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

***REMOVED******REMOVED******REMOVED*** Resource Requirements

Target: Synology DS418play (Intel Celeron J3455, 2 cores, 6GB RAM)

| Service    | Memory Limit | CPU Limit | Notes                    |
|------------|--------------|-----------|--------------------------|
| PostgreSQL | 512 MB       | -         | Database                 |
| Backend API| 512 MB       | 1 core    | Node.js application      |
| Grafana    | 256 MB       | 0.5 core  | Dashboard UI             |
| Alloy      | 128 MB       | 0.25 core | Telemetry collector      |
| Loki       | 256 MB       | 0.5 core  | Log storage              |
| Tempo      | 256 MB       | 0.5 core  | Trace storage            |
| Mimir      | 256 MB       | 0.5 core  | Metrics storage          |
| **Total**  | **~2.2 GB**  | -         | Leaves ~3.8GB for system |

---

***REMOVED******REMOVED*** Prerequisites

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

***REMOVED******REMOVED*** Directory Structure

On your NAS, create this structure:

```
/volume1/docker/scanium/
├── backend.env              ***REMOVED*** Backend environment variables (from env/backend.env.example)
├── monitoring.env           ***REMOVED*** Monitoring environment variables (from env/monitoring.env.example)
├── postgres/                ***REMOVED*** PostgreSQL data (auto-created)
├── secrets/                 ***REMOVED*** Optional: Google Vision credentials
│   └── vision-sa.json
└── monitoring/
    ├── alloy/
    │   └── alloy.hcl        ***REMOVED*** Copy from repo: monitoring/alloy/alloy.hcl
    ├── loki/
    │   └── loki.yaml        ***REMOVED*** Copy from repo: monitoring/loki/loki.yaml
    ├── tempo/
    │   └── tempo.yaml       ***REMOVED*** Copy from repo: monitoring/tempo/tempo.yaml
    ├── mimir/
    │   └── mimir.yaml       ***REMOVED*** Copy from repo: monitoring/mimir/mimir.yaml
    └── grafana/
        ├── provisioning/    ***REMOVED*** Copy from repo: monitoring/grafana/provisioning/
        └── dashboards/      ***REMOVED*** Copy from repo: monitoring/grafana/dashboards/
```

---

***REMOVED******REMOVED*** Step 1: Prepare the NAS

***REMOVED******REMOVED******REMOVED*** 1.1 Create Directory Structure

SSH into your NAS:

```bash
ssh admin@YOUR_NAS_IP
```

Create directories:

```bash
***REMOVED*** Create base directory
sudo mkdir -p /volume1/docker/scanium

***REMOVED*** Create subdirectories
sudo mkdir -p /volume1/docker/scanium/{postgres,secrets}
sudo mkdir -p /volume1/docker/scanium/monitoring/{alloy,loki,tempo,mimir,grafana}
sudo mkdir -p /volume1/docker/scanium/monitoring/grafana/{provisioning,dashboards}

***REMOVED*** Set permissions (allow Docker to write)
sudo chmod -R 777 /volume1/docker/scanium
```

***REMOVED******REMOVED******REMOVED*** 1.2 Copy Configuration Files

From your development machine, copy the monitoring configs:

```bash
***REMOVED*** From the repo root directory
NAS_IP="YOUR_NAS_IP"
NAS_PATH="/volume1/docker/scanium"

***REMOVED*** Copy monitoring configs
scp monitoring/alloy/alloy.hcl admin@$NAS_IP:$NAS_PATH/monitoring/alloy/
scp monitoring/loki/loki.yaml admin@$NAS_IP:$NAS_PATH/monitoring/loki/
scp monitoring/tempo/tempo.yaml admin@$NAS_IP:$NAS_PATH/monitoring/tempo/
scp monitoring/mimir/mimir.yaml admin@$NAS_IP:$NAS_PATH/monitoring/mimir/

***REMOVED*** Copy Grafana provisioning
scp -r monitoring/grafana/provisioning/* admin@$NAS_IP:$NAS_PATH/monitoring/grafana/provisioning/
scp -r monitoring/grafana/dashboards/* admin@$NAS_IP:$NAS_PATH/monitoring/grafana/dashboards/
```

***REMOVED******REMOVED******REMOVED*** 1.3 Create Environment Files

Copy example files and edit them:

```bash
***REMOVED*** Copy env examples to NAS
scp deploy/nas/env/backend.env.example admin@$NAS_IP:$NAS_PATH/backend.env
scp deploy/nas/env/monitoring.env.example admin@$NAS_IP:$NAS_PATH/monitoring.env
```

SSH into NAS and edit the env files:

```bash
ssh admin@YOUR_NAS_IP
cd /volume1/docker/scanium

***REMOVED*** Edit backend.env - fill in all CHANGE_ME values
nano backend.env

***REMOVED*** Edit monitoring.env - set Grafana password
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

***REMOVED******REMOVED******REMOVED*** 1.4 Create Docker Network

```bash
***REMOVED*** SSH into NAS
ssh admin@YOUR_NAS_IP

***REMOVED*** Create shared network for backend and monitoring
sudo docker network create scanium-observability
```

---

***REMOVED******REMOVED*** Step 2: Build the Backend Image

The backend needs to be built as a Docker image. You have two options:

***REMOVED******REMOVED******REMOVED*** Option A: Build on Development Machine (Recommended)

Build on a faster machine and transfer to NAS:

```bash
***REMOVED*** From the repo root, on your Mac/Linux machine
cd backend

***REMOVED*** Build the image
docker build -t scanium-api:latest .

***REMOVED*** Save image to file
docker save scanium-api:latest | gzip > scanium-api.tar.gz

***REMOVED*** Copy to NAS
scp scanium-api.tar.gz admin@YOUR_NAS_IP:/volume1/docker/scanium/

***REMOVED*** SSH to NAS and load image
ssh admin@YOUR_NAS_IP
sudo docker load < /volume1/docker/scanium/scanium-api.tar.gz

***REMOVED*** Verify
sudo docker images | grep scanium-api
```

***REMOVED******REMOVED******REMOVED*** Option B: Build Directly on NAS (Slower)

Copy the backend source and build on NAS:

```bash
***REMOVED*** From repo root on your machine
scp -r backend admin@YOUR_NAS_IP:/volume1/docker/scanium/backend-src

***REMOVED*** SSH to NAS
ssh admin@YOUR_NAS_IP
cd /volume1/docker/scanium/backend-src
sudo docker build -t scanium-api:latest .

***REMOVED*** Clean up source after build
rm -rf /volume1/docker/scanium/backend-src
```

---

***REMOVED******REMOVED*** Step 3: Deploy Monitoring Stack

***REMOVED******REMOVED******REMOVED*** 3.1 Via Synology Container Manager UI

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

***REMOVED******REMOVED******REMOVED*** 3.2 Via SSH (Alternative)

```bash
ssh admin@YOUR_NAS_IP
cd /volume1/docker/scanium

***REMOVED*** Copy compose file
***REMOVED*** (You should have already copied this, or copy now from your machine)

***REMOVED*** Set environment variable for data path
export NAS_DATA_PATH=/volume1/docker/scanium
export NAS_CONFIG_PATH=/volume1/docker/scanium

***REMOVED*** Start monitoring stack
sudo docker compose -f docker-compose.nas.monitoring.yml \
  --env-file monitoring.env up -d

***REMOVED*** Check status
sudo docker compose -f docker-compose.nas.monitoring.yml ps
```

***REMOVED******REMOVED******REMOVED*** 3.3 Verify Monitoring Stack

Wait 1-2 minutes for services to start, then:

```bash
***REMOVED*** Check all containers are healthy
sudo docker ps --format "table {{.Names}}\t{{.Status}}"

***REMOVED*** Expected output:
***REMOVED*** NAMES              STATUS
***REMOVED*** scanium-grafana    Up X minutes (healthy)
***REMOVED*** scanium-alloy      Up X minutes (healthy)
***REMOVED*** scanium-loki       Up X minutes (healthy)
***REMOVED*** scanium-tempo      Up X minutes (healthy)
***REMOVED*** scanium-mimir      Up X minutes (healthy)
```

Access Grafana at `http://YOUR_NAS_IP:3000`
- Login with credentials from `monitoring.env`

---

***REMOVED******REMOVED*** Step 4: Deploy Backend Stack

***REMOVED******REMOVED******REMOVED*** 4.1 Via Synology Container Manager UI

1. Open **Container Manager** in DSM
2. Go to **Project** → **Create**
3. Project name: `scanium-backend`
4. Path: `/volume1/docker/scanium`
5. Source: **Upload docker-compose.yml**
   - Upload `deploy/nas/compose/docker-compose.nas.backend.yml`
6. Click **Next**
7. Environment file: Select `/volume1/docker/scanium/backend.env`
8. Review and click **Done**

***REMOVED******REMOVED******REMOVED*** 4.2 Via SSH (Alternative)

```bash
ssh admin@YOUR_NAS_IP
cd /volume1/docker/scanium

***REMOVED*** Set environment variables
export NAS_DATA_PATH=/volume1/docker/scanium

***REMOVED*** Start backend stack
sudo docker compose -f docker-compose.nas.backend.yml \
  --env-file backend.env up -d

***REMOVED*** Check status
sudo docker compose -f docker-compose.nas.backend.yml ps
```

---

***REMOVED******REMOVED*** Step 5: Database Migrations

After the backend container starts for the first time, you need to run Prisma migrations:

***REMOVED******REMOVED******REMOVED*** 5.1 Check Migration Status

```bash
ssh admin@YOUR_NAS_IP

***REMOVED*** Check current migration status
sudo docker exec scanium-api npx prisma migrate status
```

***REMOVED******REMOVED******REMOVED*** 5.2 Run Migrations

```bash
***REMOVED*** Deploy pending migrations
sudo docker exec scanium-api npx prisma migrate deploy

***REMOVED*** Verify
sudo docker exec scanium-api npx prisma migrate status
```

**Expected output:**
```
Database schema is up to date!
```

***REMOVED******REMOVED******REMOVED*** 5.3 (If needed) Generate Prisma Client

The Dockerfile already generates the Prisma client during build. If you need to regenerate:

```bash
sudo docker exec scanium-api npx prisma generate
```

---

***REMOVED******REMOVED*** Verification Checklist

***REMOVED******REMOVED******REMOVED*** Backend Verification

```bash
***REMOVED*** 1. Check container health
sudo docker ps --filter name=scanium-api --format "{{.Status}}"
***REMOVED*** Expected: Up X minutes (healthy)

***REMOVED*** 2. Check health endpoint
curl -s http://YOUR_NAS_IP:8080/health
***REMOVED*** Expected: {"status":"ok","timestamp":"..."}

***REMOVED*** 3. Check API is responding (should return 401 without API key)
curl -s -o /dev/null -w "%{http_code}" http://YOUR_NAS_IP:8080/api/v1/classifier
***REMOVED*** Expected: 401

***REMOVED*** 4. Check database connection
sudo docker exec scanium-api npx prisma migrate status
***REMOVED*** Expected: "Database schema is up to date!"

***REMOVED*** 5. View logs
sudo docker logs scanium-api --tail 50
```

***REMOVED******REMOVED******REMOVED*** Monitoring Verification

```bash
***REMOVED*** 1. Check all monitoring containers
sudo docker ps --filter "name=scanium-" --format "table {{.Names}}\t{{.Status}}"

***REMOVED*** 2. Check Grafana health
curl -s http://YOUR_NAS_IP:3000/api/health
***REMOVED*** Expected: {"commit":"...","database":"ok","version":"..."}

***REMOVED*** 3. Check Alloy OTLP endpoint is listening
curl -s -o /dev/null -w "%{http_code}" http://YOUR_NAS_IP:4318/v1/traces
***REMOVED*** Expected: 405 (Method Not Allowed - but shows it's listening)

***REMOVED*** 4. Check Loki is ready
curl -s http://localhost:3100/ready
***REMOVED*** Expected: ready (from NAS itself)

***REMOVED*** 5. Access Grafana UI
***REMOVED*** Open http://YOUR_NAS_IP:3000 in browser
***REMOVED*** Login with admin credentials from monitoring.env
```

***REMOVED******REMOVED******REMOVED*** Local Mac Dry-Run

Before deploying to NAS, test locally:

```bash
cd deploy/nas/compose

***REMOVED*** Create test env files
cp ../env/backend.env.example ../env/backend.env
cp ../env/monitoring.env.example ../env/monitoring.env
***REMOVED*** Edit files with test values

***REMOVED*** Create local data directory
mkdir -p /tmp/scanium-test

***REMOVED*** Override NAS paths for local test
export NAS_DATA_PATH=/tmp/scanium-test
export NAS_CONFIG_PATH=/tmp/scanium-test

***REMOVED*** Copy configs locally (from repo root)
mkdir -p /tmp/scanium-test/monitoring/{alloy,loki,tempo,mimir,grafana}
cp ../../../monitoring/alloy/alloy.hcl /tmp/scanium-test/monitoring/alloy/
cp ../../../monitoring/loki/loki.yaml /tmp/scanium-test/monitoring/loki/
cp ../../../monitoring/tempo/tempo.yaml /tmp/scanium-test/monitoring/tempo/
cp ../../../monitoring/mimir/mimir.yaml /tmp/scanium-test/monitoring/mimir/
cp -r ../../../monitoring/grafana/provisioning /tmp/scanium-test/monitoring/grafana/
cp -r ../../../monitoring/grafana/dashboards /tmp/scanium-test/monitoring/grafana/

***REMOVED*** Create network
docker network create scanium-observability || true

***REMOVED*** Start monitoring (in a separate terminal)
docker compose -f docker-compose.nas.monitoring.yml --env-file ../env/monitoring.env up

***REMOVED*** Build and start backend (in another terminal)
cd ../../../backend && docker build -t scanium-api:latest .
cd ../deploy/nas/compose
docker compose -f docker-compose.nas.backend.yml --env-file ../env/backend.env up

***REMOVED*** Test health endpoint
curl http://localhost:8080/health

***REMOVED*** Test Grafana
open http://localhost:3000

***REMOVED*** Cleanup
docker compose -f docker-compose.nas.backend.yml --env-file ../env/backend.env down
docker compose -f docker-compose.nas.monitoring.yml --env-file ../env/monitoring.env down
```

---

***REMOVED******REMOVED*** Exposing Services Securely

***REMOVED******REMOVED******REMOVED*** LAN-Only Access (Default)

By default, services are only accessible on your local network:
- Backend API: `http://YOUR_NAS_IP:8080`
- Grafana: `http://YOUR_NAS_IP:3000`
- OTLP endpoint: `http://YOUR_NAS_IP:4318`

This is safe for home use where your network is trusted.

***REMOVED******REMOVED******REMOVED*** External Access Options

***REMOVED******REMOVED******REMOVED******REMOVED*** Option 1: Cloudflare Tunnel (Recommended)

Provides secure HTTPS access without port forwarding:

1. Create a Cloudflare Tunnel at [dash.cloudflare.com](https://dash.cloudflare.com) → Zero Trust → Networks → Tunnels
2. Get the tunnel token
3. Edit `backend.env`:
   ```
   CLOUDFLARED_TOKEN=your_tunnel_token_here
   ```
4. Uncomment the `cloudflared` service in `docker-compose.nas.backend.yml`
5. Restart the backend stack

***REMOVED******REMOVED******REMOVED******REMOVED*** Option 2: Reverse Proxy with SSL

Use Synology's built-in reverse proxy:

1. Control Panel → Login Portal → Advanced → Reverse Proxy
2. Create rules for each service with SSL certificate
3. Use Synology's DDNS or your own domain

***REMOVED******REMOVED******REMOVED******REMOVED*** Option 3: VPN (Most Secure)

Use Synology VPN Server or WireGuard:

1. Install VPN Server package
2. Configure OpenVPN or L2TP
3. Access NAS services through VPN tunnel

**Do NOT** expose ports directly to the internet without authentication/encryption.

---

***REMOVED******REMOVED*** Maintenance

***REMOVED******REMOVED******REMOVED*** Viewing Logs

```bash
***REMOVED*** Backend logs
sudo docker logs scanium-api --tail 100 -f

***REMOVED*** PostgreSQL logs
sudo docker logs scanium-postgres --tail 100 -f

***REMOVED*** Grafana logs
sudo docker logs scanium-grafana --tail 100 -f

***REMOVED*** All monitoring stack
sudo docker compose -f docker-compose.nas.monitoring.yml logs -f
```

***REMOVED******REMOVED******REMOVED*** Restarting Services

```bash
***REMOVED*** Restart single service
sudo docker restart scanium-api

***REMOVED*** Restart entire stack
sudo docker compose -f docker-compose.nas.backend.yml --env-file backend.env restart
```

***REMOVED******REMOVED******REMOVED*** Updating Images

```bash
***REMOVED*** Pull latest images
sudo docker compose -f docker-compose.nas.monitoring.yml pull

***REMOVED*** Recreate with new images
sudo docker compose -f docker-compose.nas.monitoring.yml --env-file monitoring.env up -d
```

***REMOVED******REMOVED******REMOVED*** Backup Data

Important directories to backup:
- `/volume1/docker/scanium/postgres/` - Database
- `/volume1/docker/scanium/monitoring/grafana/` - Grafana data (dashboards, etc.)
- `/volume1/docker/scanium/*.env` - Environment files (store securely!)

```bash
***REMOVED*** Example backup command
tar -czvf scanium-backup-$(date +%Y%m%d).tar.gz \
  /volume1/docker/scanium/postgres \
  /volume1/docker/scanium/monitoring/grafana
```

---

***REMOVED******REMOVED*** Common Failures & Troubleshooting

***REMOVED******REMOVED******REMOVED*** Container Fails to Start

**Symptom:** Container status shows "Exited" or keeps restarting

```bash
***REMOVED*** Check logs
sudo docker logs scanium-api --tail 100

***REMOVED*** Check if image exists
sudo docker images | grep scanium-api
```

**Solutions:**
- Missing image: Build or load the image (Step 2)
- Missing env file: Ensure `.env` files exist and have required values
- Missing network: Create with `docker network create scanium-observability`

***REMOVED******REMOVED******REMOVED*** Port Conflicts

**Symptom:** "Address already in use" error

```bash
***REMOVED*** Find what's using the port
sudo netstat -tlnp | grep :8080
```

**Solutions:**
- Change the port in `.env` file (e.g., `API_HOST_PORT=8081`)
- Stop conflicting service

***REMOVED******REMOVED******REMOVED*** Permission Denied on Volumes

**Symptom:** "Permission denied" errors in container logs

```bash
***REMOVED*** Fix permissions
sudo chmod -R 777 /volume1/docker/scanium
```

**Note:** Some containers run as root (`user: "0"`) to handle Synology's permission model.

***REMOVED******REMOVED******REMOVED*** DATABASE_URL Connection Failed

**Symptom:** "Connection refused" or "Authentication failed"

```bash
***REMOVED*** Check postgres is running
sudo docker ps | grep postgres

***REMOVED*** Check postgres logs
sudo docker logs scanium-postgres

***REMOVED*** Verify DATABASE_URL format
echo $DATABASE_URL
***REMOVED*** Should be: postgresql://scanium:PASSWORD@scanium-postgres:5432/scanium
```

**Solutions:**
- Ensure `POSTGRES_PASSWORD` in env matches password in `DATABASE_URL`
- Use container name (`scanium-postgres`) not `localhost` in DATABASE_URL
- Ensure both containers are on same network

***REMOVED******REMOVED******REMOVED*** ARM/x86 Image Mismatch

**Symptom:** "exec format error" when starting container

```bash
***REMOVED*** Check image architecture
sudo docker inspect scanium-api | grep Architecture
```

**Solutions:**
- DS418play is x86_64 - ensure images are built for amd64
- Rebuild on an x86 machine, not Apple Silicon
- Use `--platform linux/amd64` when building on Apple Silicon:
  ```bash
  docker build --platform linux/amd64 -t scanium-api:latest .
  ```

***REMOVED******REMOVED******REMOVED*** Out of Memory (OOM)

**Symptom:** Containers killed unexpectedly, "OOM killed" in logs

```bash
***REMOVED*** Check memory usage
sudo docker stats --no-stream

***REMOVED*** Check system memory
free -h
```

**Solutions:**
- Reduce memory limits in docker-compose
- Reduce Loki/Mimir retention periods
- Disable services not needed (e.g., Tempo if not using traces)
- Add more RAM to NAS

***REMOVED******REMOVED******REMOVED*** Grafana Can't Connect to Datasources

**Symptom:** "Bad Gateway" errors in Grafana

```bash
***REMOVED*** Check datasource containers are healthy
sudo docker ps --filter "name=scanium-"

***REMOVED*** Test from Grafana container
sudo docker exec scanium-grafana wget -qO- http://loki:3100/ready
```

**Solutions:**
- Ensure all monitoring services are on `scanium-observability` network
- Wait for services to become healthy (can take 1-2 minutes)
- Check datasource URLs use container names, not localhost

***REMOVED******REMOVED******REMOVED*** Container Health Check Failing

**Symptom:** Container shows "(unhealthy)" status

```bash
***REMOVED*** Run health check manually
sudo docker exec scanium-api node -e "require('http').get('http://localhost:8080/health', (r) => {console.log(r.statusCode)})"
```

**Solutions:**
- Check application logs for errors
- Ensure `start_period` is long enough for slow NAS
- Check container has network access

---

***REMOVED******REMOVED*** Quick Reference

***REMOVED******REMOVED******REMOVED*** Useful Commands

```bash
***REMOVED*** View all Scanium containers
sudo docker ps --filter "name=scanium-"

***REMOVED*** View container resource usage
sudo docker stats --filter "name=scanium-" --no-stream

***REMOVED*** Enter container shell
sudo docker exec -it scanium-api sh

***REMOVED*** View real-time logs
sudo docker logs -f scanium-api

***REMOVED*** Check Docker disk usage
sudo docker system df

***REMOVED*** Clean up unused images
sudo docker image prune -a
```

***REMOVED******REMOVED******REMOVED*** Service URLs

| Service     | URL                         | Notes                    |
|-------------|-----------------------------|-----------------------------|
| Backend API | `http://NAS_IP:8080`        | Health: `/health`        |
| Grafana     | `http://NAS_IP:3000`        | Login required           |
| OTLP HTTP   | `http://NAS_IP:4318`        | For app telemetry        |
| OTLP gRPC   | `http://NAS_IP:4317`        | For app telemetry        |

***REMOVED******REMOVED******REMOVED*** File Locations

| File                  | Purpose                          |
|-----------------------|----------------------------------|
| `backend.env`         | Backend secrets & config         |
| `monitoring.env`      | Grafana password & settings      |
| `postgres/`           | PostgreSQL database files        |
| `monitoring/grafana/` | Grafana dashboards & data        |
