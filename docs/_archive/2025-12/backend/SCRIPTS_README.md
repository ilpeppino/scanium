> Archived on 2025-12-20: backend notes kept for reference; see docs/ARCHITECTURE.md for current state.
***REMOVED*** Development Scripts

Quick-start scripts for Scanium backend development.

***REMOVED******REMOVED*** 🚀 Quick Start

***REMOVED******REMOVED******REMOVED*** Start All Services

```bash
./start-dev.sh
```

This single command will:
1. ✅ Check prerequisites (Node.js, Docker/Colima, ngrok)
2. ✅ Start Colima (if using Colima and not running)
3. ✅ Start PostgreSQL in Docker
4. ✅ Wait for PostgreSQL to be ready
5. ✅ Start backend server
6. ✅ Wait for backend to be ready
7. ✅ Start ngrok tunnel
8. ✅ Display ngrok URL
9. ✅ Check if `.env` needs updating
10. ✅ Show quick test commands

**Press Ctrl+C to stop all services** - they will be gracefully shut down.

***REMOVED******REMOVED******REMOVED*** Stop All Services

```bash
./stop-dev.sh
```

Cleanly stops:
- Backend server (port 8080)
- ngrok tunnel
- PostgreSQL container

Optionally deletes log files.

---

***REMOVED******REMOVED*** 📋 What Each Script Does

***REMOVED******REMOVED******REMOVED*** `start-dev.sh`

**Features:**
- **Prerequisite checks**: Verifies Node.js, Docker, ngrok are installed
- **Colima support**: Auto-starts Colima and switches Docker context
- **Port conflict handling**: Detects and offers to kill processes on port 8080
- **Health checks**: Waits for services to be healthy before proceeding
- **ngrok URL detection**: Automatically extracts ngrok URL from logs
- **`.env` update**: Detects URL changes and offers to update `.env`
- **Error handling**: Exits gracefully on errors with helpful messages
- **Graceful shutdown**: Ctrl+C cleanly stops all services
- **Helpful output**: Shows test commands, URLs, and next steps

**Output includes:**
- ✅ Status of each service
- 🌐 ngrok public URL
- 📝 Quick test commands
- 🔧 Mobile app configuration instructions
- ⚠️ Warnings if RuName needs updating

**Logs:**
- Backend logs: `.dev-server.log`
- ngrok logs: `.ngrok.log`

***REMOVED******REMOVED******REMOVED*** `stop-dev.sh`

**Features:**
- Stops backend server (finds by port)
- Stops ngrok (finds by process name)
- Stops PostgreSQL container
- Optional log file cleanup
- Handles "service not running" gracefully

---

***REMOVED******REMOVED*** 🎯 Usage Examples

***REMOVED******REMOVED******REMOVED*** Daily Development Workflow

**Morning startup:**
```bash
cd backend
./start-dev.sh
***REMOVED*** Wait for services to start
***REMOVED*** Copy ngrok URL if changed
```

**End of day:**
```bash
./stop-dev.sh
***REMOVED*** Or just Ctrl+C in start-dev.sh terminal
```

***REMOVED******REMOVED******REMOVED*** Testing After Code Changes

Backend auto-reloads with `tsx watch`, so just save your files. ngrok URL stays the same.

***REMOVED******REMOVED******REMOVED*** ngrok URL Changed

The script will detect this and prompt you:

```
⚠️  ngrok URL has changed!
Old URL: https://old-url.ngrok-free.dev
New URL: https://new-url.ngrok-free.dev

ACTION REQUIRED:
1. Update .env file: PUBLIC_BASE_URL=https://new-url.ngrok-free.dev
2. Update eBay RuName redirect URL in developer portal
3. Update mobile app SettingsScreen.kt with new URL
4. Restart backend server

Update .env automatically? (y/n)
```

If you answer `y`, it will:
- Backup `.env` to `.env.backup`
- Update `PUBLIC_BASE_URL`
- Restart backend server

You still need to manually update:
- eBay RuName in developer portal
- Mobile app `SettingsScreen.kt`

---

***REMOVED******REMOVED*** 🐛 Troubleshooting

***REMOVED******REMOVED******REMOVED*** Script won't start

**Check permissions:**
```bash
chmod +x start-dev.sh stop-dev.sh
```

***REMOVED******REMOVED******REMOVED*** "ngrok is not authenticated"

```bash
ngrok config add-authtoken YOUR_TOKEN
```

Get token from: [dashboard.ngrok.com/get-started/your-authtoken](https://dashboard.ngrok.com/get-started/your-authtoken)

***REMOVED******REMOVED******REMOVED*** Backend fails to start

Check `.dev-server.log`:
```bash
tail -50 .dev-server.log
```

Common issues:
- Missing dependencies: `npm install`
- Missing `.env`: `cp .env.example .env`
- Missing Prisma client: `npm run prisma:generate`

***REMOVED******REMOVED******REMOVED*** PostgreSQL fails to start

Check logs:
```bash
docker logs scanium-postgres
```

Restart PostgreSQL:
```bash
docker compose restart postgres
```

***REMOVED******REMOVED******REMOVED*** Port 8080 already in use

The script will detect this and offer to kill the process. If declined, it will exit.

Manual cleanup:
```bash
lsof -i :8080
kill <PID>
```

***REMOVED******REMOVED******REMOVED*** ngrok URL not detected

Check `.ngrok.log`:
```bash
cat .ngrok.log
```

If ngrok crashed, check if authenticated:
```bash
ngrok config check
```

---

***REMOVED******REMOVED*** 📝 Log Files

Both scripts create log files for debugging:

***REMOVED******REMOVED******REMOVED*** `.dev-server.log`
Contains backend server output (stdout + stderr)

**View in real-time:**
```bash
tail -f .dev-server.log
```

***REMOVED******REMOVED******REMOVED*** `.ngrok.log`
Contains ngrok tunnel output

**View in real-time:**
```bash
tail -f .ngrok.log
```

**Delete logs:**
```bash
rm .dev-server.log .ngrok.log
```

Or let `stop-dev.sh` prompt you.

---

***REMOVED******REMOVED*** 🔧 Customization

***REMOVED******REMOVED******REMOVED*** Change ngrok region

Edit `start-dev.sh`, line ~200:
```bash
ngrok http 8080 --region us --log=stdout > .ngrok.log 2>&1 &
```

Available regions: `us`, `eu`, `ap`, `au`, `sa`, `jp`, `in`

***REMOVED******REMOVED******REMOVED*** Change backend port

1. Update `PORT` in `.env`
2. Update script port checks (search for `8080`)
3. Update ngrok command: `ngrok http NEW_PORT`

***REMOVED******REMOVED******REMOVED*** Skip Colima checks

Remove lines 44-68 in `start-dev.sh` if you're using Docker Desktop.

---

***REMOVED******REMOVED*** ⚙️ Advanced Usage

***REMOVED******REMOVED******REMOVED*** Run services individually

**PostgreSQL only:**
```bash
docker compose up -d postgres
```

**Backend only:**
```bash
npm run dev
```

**ngrok only:**
```bash
ngrok http 8080
```

***REMOVED******REMOVED******REMOVED*** Background mode

Start in background and detach:
```bash
nohup ./start-dev.sh > start.log 2>&1 &
```

Stop with:
```bash
./stop-dev.sh
```

***REMOVED******REMOVED******REMOVED*** Multiple ngrok URLs (paid plan)

If you have a static ngrok domain:

Edit `start-dev.sh`, replace:
```bash
ngrok http 8080 --log=stdout > .ngrok.log 2>&1 &
```

With:
```bash
ngrok http 8080 --domain=your-static-domain.ngrok-free.app --log=stdout > .ngrok.log 2>&1 &
```

Then ngrok URL will never change!

---

***REMOVED******REMOVED*** 🎨 Script Output

***REMOVED******REMOVED******REMOVED*** Successful Startup

```
╔═══════════════════════════════════════════╗
║   Scanium Backend Development Startup    ║
╚═══════════════════════════════════════════╝

ℹ️  Working directory: /Users/family/dev/scanium/backend

ℹ️  Checking prerequisites...
✅ Node.js v20.11.0
✅ Docker installed
✅ Colima is running
✅ ngrok installed

ℹ️  Checking .env file...
✅ .env file exists

ℹ️  Checking ports...

ℹ️  Starting PostgreSQL...
✅ PostgreSQL is ready
✅ PostgreSQL connection verified

ℹ️  Starting backend server...
ℹ️  Backend server started (PID: 12345)
ℹ️  Waiting for server to be ready...
✅ Backend server is ready
✅ Health check passed

ℹ️  Starting ngrok tunnel...
ℹ️  ngrok started (PID: 12346)
ℹ️  Waiting for ngrok tunnel...
✅ ngrok tunnel established

ℹ️  Testing ngrok endpoint...
✅ ngrok tunnel working

╔═══════════════════════════════════════════╗
║          Services Running                 ║
╚═══════════════════════════════════════════╝

PostgreSQL:      localhost:5432 (Container: scanium-postgres)
Backend Server:  http://localhost:8080 (PID: 12345)
ngrok Tunnel:    https://abc123.ngrok-free.dev (PID: 12346)

╔═══════════════════════════════════════════╗
║          Quick Test Commands              ║
╚═══════════════════════════════════════════╝

Health check:
  curl http://localhost:8080/healthz

eBay OAuth start:
  curl -X POST https://abc123.ngrok-free.dev/auth/ebay/start

eBay connection status:
  curl https://abc123.ngrok-free.dev/auth/ebay/status

View backend logs:
  tail -f .dev-server.log

View ngrok logs:
  tail -f .ngrok.log

ngrok web interface:
  http://localhost:4040

╔═══════════════════════════════════════════╗
║          Mobile App Configuration         ║
╚═══════════════════════════════════════════╝

Update your mobile app with this URL:
https://abc123.ngrok-free.dev

In SettingsScreen.kt, update:
  ScaniumApi("https://abc123.ngrok-free.dev")

Press Ctrl+C to stop all services
```

---

***REMOVED******REMOVED*** 📚 Related Documentation

- [Local Development Guide](LOCAL_DEV_GUIDE.md) - Complete setup walkthrough
- [Backend README](README.md) - Development documentation
- [Colima Setup](COLIMA_SETUP.md) - Docker Desktop alternative

---

**Happy coding! 🚀**
