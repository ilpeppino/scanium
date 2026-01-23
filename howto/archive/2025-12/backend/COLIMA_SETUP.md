> Archived on 2025-12-20: backend notes kept for reference; see docs/ARCHITECTURE.md for current
> state.

# Colima Setup Guide for Scanium Backend

Quick reference for using Colima (Docker Desktop alternative) on macOS.

## 🐳 Colima Overview

Colima is a lightweight Docker runtime for macOS that's free and open-source. It uses Docker Compose
V2, which means you'll use `docker compose` (with space) instead of `docker-compose` (with hyphen).

## ✅ Prerequisites

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
   # Check if docker compose is available
   docker compose version

   # If not found, install it
   brew install docker-compose
   ```

## 🚀 Start Colima

### First Time Setup

```bash
# Start Colima with recommended settings
colima start --cpu 4 --memory 8 --disk 60

# Verify it's running
colima status
```

**Expected output:**

```
INFO[0000] colima is running
```

### Check Docker is Working

```bash
# Should show Docker version
docker version

# Should show Docker Compose version
docker compose version
```

## 📦 Backend Setup with Colima

### 1. Navigate to Project

```bash
cd /Users/family/dev/scanium/backend
```

### 2. Start PostgreSQL

```bash
# Use 'docker compose' (with space)
docker compose up -d postgres

# Check it's running
docker ps
```

**Expected output:**

```
CONTAINER ID   IMAGE              COMMAND                  STATUS
abc123def456   postgres:16-alpine "docker-entrypoint.s…"   Up 10 seconds
```

### 3. Check Logs

```bash
docker logs scanium-postgres
```

Should see: `database system is ready to accept connections`

### 4. Continue with Backend Setup

Now follow `LOCAL_DEV_GUIDE.md` from Step 3 onwards.

## 🔄 Common Colima Commands

### Start/Stop Colima

```bash
# Start
colima start

# Stop
colima stop

# Restart
colima restart

# Check status
colima status
```

### Docker Compose Commands

```bash
# Start all services
docker compose up -d

# Start specific service
docker compose up -d postgres

# Stop all services
docker compose down

# Restart specific service
docker compose restart postgres

# View logs
docker compose logs -f api
docker compose logs -f postgres

# Check running containers
docker ps

# Check all containers (including stopped)
docker ps -a
```

### Troubleshooting

```bash
# If containers won't start, try restarting Colima
colima restart

# If Docker commands fail, ensure Colima is running
colima status

# If ports are in use, check what's using them
lsof -i :8080
lsof -i :5432
```

## 🔧 Colima Configuration

### View Current Settings

```bash
colima status
```

### Adjust Resources (if needed)

```bash
# Stop Colima
colima stop

# Start with different settings
colima start --cpu 4 --memory 8 --disk 100

# Or edit config file
nano ~/.colima/default/colima.yaml
```

## 🐛 Common Issues

### "Cannot connect to Docker daemon"

**Problem:** Colima isn't running

**Fix:**

```bash
colima start
```

### "docker compose: command not found"

**Problem:** Docker Compose V2 not installed

**Fix:**

```bash
# Install Docker Compose
brew install docker-compose

# Or use as Docker plugin
mkdir -p ~/.docker/cli-plugins/
ln -sfn /opt/homebrew/bin/docker-compose ~/.docker/cli-plugins/docker-compose
```

### Port already in use

**Problem:** Port 5432 or 8080 already bound

**Fix:**

```bash
# Find what's using the port
lsof -i :5432
lsof -i :8080

# Kill the process (replace PID with actual process ID)
kill -9 PID

# Or change the port in docker-compose.yml
```

### PostgreSQL won't start

**Problem:** Volume issues or previous data

**Fix:**

```bash
# Stop everything
docker compose down

# Remove volumes (⚠️ this deletes data)
docker compose down -v

# Start fresh
docker compose up -d postgres
```

## 📊 Monitor Resources

### Check Colima Resource Usage

```bash
# CPU and memory usage
colima status

# Docker stats for all containers
docker stats

# Specific container
docker stats scanium-postgres
```

## 🔄 Daily Workflow

### Morning Startup

```bash
# 1. Start Colima (if not running)
colima start

# 2. Start backend services
cd /Users/family/dev/scanium/backend
docker compose up -d postgres

# 3. Start ngrok (separate terminal)
ngrok http 8080

# 4. Start backend (separate terminal)
npm run dev
```

### End of Day

```bash
# 1. Stop backend: Ctrl+C
# 2. Stop ngrok: Ctrl+C
# 3. Stop Docker services
docker compose down

# 4. (Optional) Stop Colima to free resources
colima stop
```

## 🎯 Quick Reference

| Old Command              | New Command (Colima)     |
|--------------------------|--------------------------|
| `docker-compose up`      | `docker compose up`      |
| `docker-compose down`    | `docker compose down`    |
| `docker-compose logs`    | `docker compose logs`    |
| `docker-compose ps`      | `docker compose ps`      |
| `docker-compose restart` | `docker compose restart` |

**Key Difference:** Space instead of hyphen!

## 🔗 Useful Links

- [Colima GitHub](https://github.com/abiosoft/colima)
- [Docker Compose V2 Docs](https://docs.docker.com/compose/)
- [Colima Configuration](https://github.com/abiosoft/colima/blob/main/docs/CONFIGURATION.md)

## ✅ Verification Checklist

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
