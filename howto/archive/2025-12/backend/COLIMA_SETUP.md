> Archived on 2025-12-20: backend notes kept for reference; see docs/ARCHITECTURE.md for current state.
***REMOVED*** Colima Setup Guide for Scanium Backend

Quick reference for using Colima (Docker Desktop alternative) on macOS.

***REMOVED******REMOVED*** 🐳 Colima Overview

Colima is a lightweight Docker runtime for macOS that's free and open-source. It uses Docker Compose V2, which means you'll use `docker compose` (with space) instead of `docker-compose` (with hyphen).

***REMOVED******REMOVED*** ✅ Prerequisites

1. **Install Colima:**
   ```bash
   brew install colima
   ```

2. **Install Docker CLI:**
   ```bash
   brew install docker
   ```

3. **Install Docker Compose (if not included):**
   ```bash
   ***REMOVED*** Check if docker compose is available
   docker compose version

   ***REMOVED*** If not found, install it
   brew install docker-compose
   ```

***REMOVED******REMOVED*** 🚀 Start Colima

***REMOVED******REMOVED******REMOVED*** First Time Setup

```bash
***REMOVED*** Start Colima with recommended settings
colima start --cpu 4 --memory 8 --disk 60

***REMOVED*** Verify it's running
colima status
```

**Expected output:**
```
INFO[0000] colima is running
```

***REMOVED******REMOVED******REMOVED*** Check Docker is Working

```bash
***REMOVED*** Should show Docker version
docker version

***REMOVED*** Should show Docker Compose version
docker compose version
```

***REMOVED******REMOVED*** 📦 Backend Setup with Colima

***REMOVED******REMOVED******REMOVED*** 1. Navigate to Project

```bash
cd /Users/family/dev/scanium/backend
```

***REMOVED******REMOVED******REMOVED*** 2. Start PostgreSQL

```bash
***REMOVED*** Use 'docker compose' (with space)
docker compose up -d postgres

***REMOVED*** Check it's running
docker ps
```

**Expected output:**
```
CONTAINER ID   IMAGE              COMMAND                  STATUS
abc123def456   postgres:16-alpine "docker-entrypoint.s…"   Up 10 seconds
```

***REMOVED******REMOVED******REMOVED*** 3. Check Logs

```bash
docker logs scanium-postgres
```

Should see: `database system is ready to accept connections`

***REMOVED******REMOVED******REMOVED*** 4. Continue with Backend Setup

Now follow `LOCAL_DEV_GUIDE.md` from Step 3 onwards.

***REMOVED******REMOVED*** 🔄 Common Colima Commands

***REMOVED******REMOVED******REMOVED*** Start/Stop Colima

```bash
***REMOVED*** Start
colima start

***REMOVED*** Stop
colima stop

***REMOVED*** Restart
colima restart

***REMOVED*** Check status
colima status
```

***REMOVED******REMOVED******REMOVED*** Docker Compose Commands

```bash
***REMOVED*** Start all services
docker compose up -d

***REMOVED*** Start specific service
docker compose up -d postgres

***REMOVED*** Stop all services
docker compose down

***REMOVED*** Restart specific service
docker compose restart postgres

***REMOVED*** View logs
docker compose logs -f api
docker compose logs -f postgres

***REMOVED*** Check running containers
docker ps

***REMOVED*** Check all containers (including stopped)
docker ps -a
```

***REMOVED******REMOVED******REMOVED*** Troubleshooting

```bash
***REMOVED*** If containers won't start, try restarting Colima
colima restart

***REMOVED*** If Docker commands fail, ensure Colima is running
colima status

***REMOVED*** If ports are in use, check what's using them
lsof -i :8080
lsof -i :5432
```

***REMOVED******REMOVED*** 🔧 Colima Configuration

***REMOVED******REMOVED******REMOVED*** View Current Settings

```bash
colima status
```

***REMOVED******REMOVED******REMOVED*** Adjust Resources (if needed)

```bash
***REMOVED*** Stop Colima
colima stop

***REMOVED*** Start with different settings
colima start --cpu 4 --memory 8 --disk 100

***REMOVED*** Or edit config file
nano ~/.colima/default/colima.yaml
```

***REMOVED******REMOVED*** 🐛 Common Issues

***REMOVED******REMOVED******REMOVED*** "Cannot connect to Docker daemon"

**Problem:** Colima isn't running

**Fix:**
```bash
colima start
```

***REMOVED******REMOVED******REMOVED*** "docker compose: command not found"

**Problem:** Docker Compose V2 not installed

**Fix:**
```bash
***REMOVED*** Install Docker Compose
brew install docker-compose

***REMOVED*** Or use as Docker plugin
mkdir -p ~/.docker/cli-plugins/
ln -sfn /opt/homebrew/bin/docker-compose ~/.docker/cli-plugins/docker-compose
```

***REMOVED******REMOVED******REMOVED*** Port already in use

**Problem:** Port 5432 or 8080 already bound

**Fix:**
```bash
***REMOVED*** Find what's using the port
lsof -i :5432
lsof -i :8080

***REMOVED*** Kill the process (replace PID with actual process ID)
kill -9 PID

***REMOVED*** Or change the port in docker-compose.yml
```

***REMOVED******REMOVED******REMOVED*** PostgreSQL won't start

**Problem:** Volume issues or previous data

**Fix:**
```bash
***REMOVED*** Stop everything
docker compose down

***REMOVED*** Remove volumes (⚠️ this deletes data)
docker compose down -v

***REMOVED*** Start fresh
docker compose up -d postgres
```

***REMOVED******REMOVED*** 📊 Monitor Resources

***REMOVED******REMOVED******REMOVED*** Check Colima Resource Usage

```bash
***REMOVED*** CPU and memory usage
colima status

***REMOVED*** Docker stats for all containers
docker stats

***REMOVED*** Specific container
docker stats scanium-postgres
```

***REMOVED******REMOVED*** 🔄 Daily Workflow

***REMOVED******REMOVED******REMOVED*** Morning Startup

```bash
***REMOVED*** 1. Start Colima (if not running)
colima start

***REMOVED*** 2. Start backend services
cd /Users/family/dev/scanium/backend
docker compose up -d postgres

***REMOVED*** 3. Start ngrok (separate terminal)
ngrok http 8080

***REMOVED*** 4. Start backend (separate terminal)
npm run dev
```

***REMOVED******REMOVED******REMOVED*** End of Day

```bash
***REMOVED*** 1. Stop backend: Ctrl+C
***REMOVED*** 2. Stop ngrok: Ctrl+C
***REMOVED*** 3. Stop Docker services
docker compose down

***REMOVED*** 4. (Optional) Stop Colima to free resources
colima stop
```

***REMOVED******REMOVED*** 🎯 Quick Reference

| Old Command | New Command (Colima) |
|-------------|---------------------|
| `docker-compose up` | `docker compose up` |
| `docker-compose down` | `docker compose down` |
| `docker-compose logs` | `docker compose logs` |
| `docker-compose ps` | `docker compose ps` |
| `docker-compose restart` | `docker compose restart` |

**Key Difference:** Space instead of hyphen!

***REMOVED******REMOVED*** 🔗 Useful Links

- [Colima GitHub](https://github.com/abiosoft/colima)
- [Docker Compose V2 Docs](https://docs.docker.com/compose/)
- [Colima Configuration](https://github.com/abiosoft/colima/blob/main/docs/CONFIGURATION.md)

***REMOVED******REMOVED*** ✅ Verification Checklist

Before starting backend development:

- [ ] Colima installed: `brew install colima`
- [ ] Docker CLI installed: `brew install docker`
- [ ] Colima running: `colima status`
- [ ] Docker working: `docker version`
- [ ] Docker Compose working: `docker compose version`
- [ ] PostgreSQL started: `docker compose up -d postgres`
- [ ] PostgreSQL running: `docker ps`
- [ ] PostgreSQL healthy: `docker logs scanium-postgres`

Now proceed with `LOCAL_DEV_GUIDE.md`! 🚀
