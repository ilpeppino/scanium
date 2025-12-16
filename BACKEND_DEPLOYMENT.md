# Scanium Backend - Complete Deployment Guide

This document provides the complete manual setup steps for deploying the Scanium backend on your Synology NAS.

## 📋 What Was Built

A production-grade TypeScript backend with:

✅ **Fastify HTTP Server** - High-performance API framework
✅ **PostgreSQL Database** - Persistent storage with Prisma ORM
✅ **eBay OAuth Integration** - Server-side authorization flow
✅ **Docker Containerization** - Multi-stage builds, non-root user
✅ **Cloudflare Tunnel** - Secure public access without port forwarding
✅ **Health Endpoints** - `/healthz` and `/readyz` for monitoring
✅ **Modular Architecture** - Ready for multiple marketplace adapters
✅ **Strict Validation** - Zod schemas for config and requests
✅ **Structured Logging** - Pino logger with request correlation
✅ **Error Handling** - Consistent JSON error responses
✅ **Type Safety** - Full TypeScript with strict mode

## 🗂️ Project Structure

```
backend/
├── src/
│   ├── config/              # Environment validation (Zod)
│   │   └── index.ts         # Load & validate env vars
│   ├── infra/
│   │   ├── db/
│   │   │   └── prisma.ts    # Prisma client wrapper
│   │   └── http/plugins/
│   │       ├── cors.ts      # CORS configuration
│   │       ├── cookies.ts   # Cookie signing
│   │       └── error-handler.ts
│   ├── modules/
│   │   ├── health/
│   │   │   └── routes.ts    # Health check endpoints
│   │   ├── auth/ebay/
│   │   │   ├── routes.ts    # OAuth endpoints
│   │   │   ├── oauth-flow.ts
│   │   │   ├── token-storage.ts
│   │   │   └── README.md    # API documentation
│   │   ├── marketplaces/
│   │   │   └── adapter.ts   # Marketplace interface
│   │   ├── listings/        # Future: eBay Sell APIs
│   │   └── media/           # Future: image upload
│   ├── shared/
│   │   ├── errors/          # Custom error types
│   │   └── types/           # Shared type definitions
│   ├── app.ts               # Fastify app builder
│   └── main.ts              # Application entrypoint
├── prisma/
│   ├── schema.prisma        # Database schema
│   └── migrations/          # Migration history
├── Dockerfile               # Multi-stage production build
├── docker-compose.yml       # NAS deployment stack
├── SETUP_GUIDE.md          # Complete deployment guide
└── README.md                # Development documentation
```

## 🎯 Implemented Endpoints

### Health & Status
- `GET /healthz` - Liveness check (200 if process running)
- `GET /readyz` - Readiness check (200 if DB connected)
- `GET /` - API info and endpoint listing

### eBay OAuth (Server-Side)
- `POST /auth/ebay/start` - Returns eBay authorization URL
- `GET /auth/ebay/callback` - Handles OAuth callback, exchanges code for tokens
- `GET /auth/ebay/status` - Returns connection status

---

## 🚀 MANUAL SETUP STEPS

## A) Cloudflare Tunnel Setup

### 1. Create Tunnel

1. Log into [Cloudflare Zero Trust Dashboard](https://one.dash.cloudflare.com/)
2. Navigate to **Networks > Tunnels**
3. Click **Create a tunnel**
4. Select **Cloudflared** connector
5. Name: `scanium-api`
6. Click **Save tunnel**

### 2. Get Tunnel Token

1. On the tunnel install page, select **Docker**
2. Copy the token from the docker command:
   ```
   docker run cloudflare/cloudflared:latest tunnel --no-autoupdate run --token eyJh...
   ```
3. **Save this token** - you'll add it to `.env` as `CLOUDFLARED_TOKEN`

### 3. Configure Public Hostname

1. In the tunnel configuration, click **Public Hostname**
2. Click **Add a public hostname**
3. Configure:
   - **Subdomain**: `api`
   - **Domain**: Your domain (e.g., `yourdomain.com`)
   - **Path**: (leave blank)
   - **Type**: `HTTP`
   - **URL**: `api:8080`
     - `api` = Docker service name from docker-compose.yml
     - `8080` = internal container port
4. Click **Save hostname**

✅ **Result**: API accessible at `https://api.yourdomain.com`

---

## B) DNS Configuration

### Verify DNS (Automatic)

1. Go to **Cloudflare Dashboard > DNS > Records**
2. Verify CNAME record exists:
   - **Type**: CNAME
   - **Name**: `api`
   - **Target**: `<uuid>.cfargotunnel.com`
   - **Proxy**: Enabled (orange cloud)

✅ **No manual DNS changes needed** - Cloudflare Tunnel creates this automatically!

---

## C) eBay Developer Credentials

### 1. Create Developer Account

1. Go to [eBay Developers Program](https://developer.ebay.com/)
2. Sign in with eBay account (or create one)
3. Complete registration

### 2. Create Application Keyset (Sandbox)

1. Go to [My Keys](https://developer.ebay.com/my/keys)
2. Under **Sandbox Keys**, click **Create a keyset**
3. Note down:
   - **App ID (Client ID)**
   - **Cert ID (Client Secret)**

### 3. Configure RuName (Redirect URL)

1. On Application Keys page, scroll to **User Tokens**
2. Click **Get a Token from eBay**
3. Click **Add RuName**
4. Fill in:
   - **Your Privacy Policy URL**: Any valid URL (e.g., `https://yourdomain.com/privacy`)
   - **Your Auth Accepted URL**: `https://api.yourdomain.com/auth/ebay/callback`
     - ⚠️ **MUST MATCH** `PUBLIC_BASE_URL + EBAY_REDIRECT_PATH`
5. Click **Save**

### 4. Grant Application Access

1. After creating RuName, click **Get a Token from eBay via Your Application**
2. Select your RuName
3. Click **Sign in to Sandbox**
4. Authorize (this activates the RuName)

### 5. Configure Scopes

Required scopes for selling functionality:

```
https://api.ebay.com/oauth/api_scope
https://api.ebay.com/oauth/api_scope/sell.inventory
https://api.ebay.com/oauth/api_scope/sell.fulfillment
https://api.ebay.com/oauth/api_scope/sell.account
```

**Scope breakdown:**
- `api_scope` - Basic API access
- `sell.inventory` - Create/manage listings
- `sell.fulfillment` - Order management
- `sell.account` - Account settings

[Full scope reference](https://developer.ebay.com/api-docs/static/oauth-scopes.html)

### 6. Production Keys (Later)

For production eBay:
1. Switch to **Production Keys** tab
2. Create keyset
3. Repeat RuName configuration
4. Update `.env`: `EBAY_ENV=production`

---

## D) NAS Deployment

### 1. Prepare Environment Variables

On your NAS, create `backend/.env`:

```bash
# Navigate to project directory
cd /volume1/docker/scanium-backend

# Create .env file
nano .env
```

**Required configuration:**

```bash
# Application
NODE_ENV=production
PORT=8080

# CRITICAL: Must match Cloudflare Tunnel URL
PUBLIC_BASE_URL=https://api.yourdomain.com

# Database (password MUST match POSTGRES_PASSWORD below)
DATABASE_URL=postgresql://scanium:STRONG_PASSWORD_HERE@postgres:5432/scanium

# eBay OAuth
EBAY_ENV=sandbox
EBAY_CLIENT_ID=your_app_id_from_ebay_developer_portal
EBAY_CLIENT_SECRET=your_cert_id_from_ebay_developer_portal
EBAY_REDIRECT_PATH=/auth/ebay/callback
EBAY_SCOPES=https://api.ebay.com/oauth/api_scope https://api.ebay.com/oauth/api_scope/sell.inventory https://api.ebay.com/oauth/api_scope/sell.fulfillment https://api.ebay.com/oauth/api_scope/sell.account

# Session Security (Generate: openssl rand -base64 32)
SESSION_SIGNING_SECRET=GENERATE_RANDOM_32_CHAR_SECRET_HERE

# CORS Origins (Include app scheme)
CORS_ORIGINS=scanium://,http://localhost:3000

# PostgreSQL Credentials
POSTGRES_USER=scanium
POSTGRES_PASSWORD=STRONG_PASSWORD_HERE
POSTGRES_DB=scanium

# Cloudflare Tunnel Token (from step A.2)
CLOUDFLARED_TOKEN=your_tunnel_token_from_cloudflare_dashboard
```

### 2. Generate Secrets

```bash
# Generate session secret (min 32 chars)
openssl rand -base64 32

# Generate PostgreSQL password
openssl rand -base64 24
```

Replace `GENERATE_*` and `STRONG_PASSWORD_HERE` with generated values.

### 3. Deploy via Container Manager (UI)

1. **Upload Project**:
   - Open **File Station** on NAS
   - Navigate to `/docker/` (or create it)
   - Create folder: `scanium-backend`
   - Upload entire `backend/` folder contents
   - Result: `/docker/scanium-backend/`

2. **Open Container Manager**:
   - Open **Container Manager** app
   - Go to **Project** tab
   - Click **Create**

3. **Configure Project**:
   - **Project Name**: `scanium`
   - **Path**: `/docker/scanium-backend`
   - **Source**: Use existing `docker-compose.yml`
   - Click **Next**

4. **Environment Variables**:
   - Ensure `.env` file exists in project folder
   - OR: Add variables manually in Container Manager
   - Click **Next**

5. **Review & Start**:
   - Review configuration
   - Click **Done**
   - Project builds and starts automatically

### 4. Deploy via SSH (Alternative)

```bash
# SSH into NAS
ssh admin@your-nas-ip

# Navigate to project
cd /volume1/docker/scanium-backend

# Start services
sudo docker-compose up -d

# View logs
sudo docker-compose logs -f
```

### 5. Run Database Migrations

```bash
# Connect to API container
docker exec -it scanium-api sh

# Run migrations
npx prisma migrate deploy

# Exit
exit
```

---

## E) Verification

### 1. Check Container Status

In Container Manager or via SSH:

```bash
docker ps
```

**Expected output:** 3 running containers
- `scanium-postgres`
- `scanium-api`
- `scanium-cloudflared`

### 2. Check Logs

```bash
# API logs
docker logs scanium-api

# Cloudflared logs
docker logs scanium-cloudflared

# Postgres logs
docker logs scanium-postgres
```

**API should show:**
```
✅ Configuration loaded (env: production)
✅ Application built
✅ Server listening on http://0.0.0.0:8080
🌍 Public URL: https://api.yourdomain.com
🏪 eBay environment: sandbox
```

**Cloudflared should show:**
```
Connection registered
```

### 3. Test Endpoints

**Health check:**
```bash
curl https://api.yourdomain.com/healthz
```

**Expected:**
```json
{
  "status": "ok",
  "timestamp": "2024-12-12T..."
}
```

**Readiness check:**
```bash
curl https://api.yourdomain.com/readyz
```

**Expected:**
```json
{
  "status": "ok",
  "database": "connected",
  "timestamp": "2024-12-12T..."
}
```

**API info:**
```bash
curl https://api.yourdomain.com/
```

**eBay status (before OAuth):**
```bash
curl https://api.yourdomain.com/auth/ebay/status
```

**Expected:**
```json
{
  "connected": false,
  "environment": null,
  "scopes": null,
  "expiresAt": null
}
```

✅ **If all endpoints return 200 OK, deployment is successful!**

---

## F) Mobile App Follow-Up

### 1. Android Integration (Summary)

See [MOBILE_APP_INTEGRATION.md](md/backend/MOBILE_APP_INTEGRATION.md) for complete implementation.

**Required changes in Scanium Android app:**

1. **Add dependencies:**
   ```kotlin
   // app/build.gradle.kts
   implementation("com.squareup.okhttp3:okhttp:4.12.0")
   implementation("androidx.browser:browser:1.7.0")
   implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
   ```

2. **Create API client:**
   ```kotlin
   // app/src/main/java/com/scanium/app/api/ScaniumApi.kt
   class ScaniumApi(private val baseUrl: String) {
       suspend fun startOAuth(): Result<OAuthStartResponse>
       suspend fun getConnectionStatus(): Result<ConnectionStatusResponse>
   }
   ```

3. **Add Settings screen:**
   ```kotlin
   // app/src/main/java/com/scanium/app/settings/EbayOAuthScreen.kt
   @Composable
   fun EbayOAuthScreen() {
       // Button to start OAuth
       // Opens Custom Tab with eBay authorization URL
       // Polls status endpoint after browser returns
   }
   ```

4. **Configure API base URL:**
   ```kotlin
   // Use BuildConfig for environment-specific URLs
   val api = ScaniumApi("https://api.yourdomain.com")
   ```

### 2. OAuth Flow from App

```
1. User taps "Connect eBay" in Settings
   ↓
2. App calls POST /auth/ebay/start
   ↓
3. App receives { authorizeUrl: "https://auth.sandbox.ebay.com/..." }
   ↓
4. App opens URL in Custom Tab (Android) / Safari View (iOS)
   ↓
5. User logs into eBay and authorizes app
   ↓
6. eBay redirects to https://api.yourdomain.com/auth/ebay/callback
   ↓
7. Backend exchanges code for tokens and stores in DB
   ↓
8. Browser shows "✅ eBay Connected!" success page
   ↓
9. Custom Tab closes automatically
   ↓
10. App polls GET /auth/ebay/status every 2 seconds
   ↓
11. When connected=true, show success in app
```

---

## 🔐 Security Checklist

Before going live:

- [ ] Strong `SESSION_SIGNING_SECRET` (min 32 chars)
- [ ] Strong `POSTGRES_PASSWORD`
- [ ] `CLOUDFLARED_TOKEN` kept secret (not in git)
- [ ] eBay credentials kept secret (not in git)
- [ ] `.env` file not committed to git (in `.gitignore`)
- [ ] HTTPS only (via Cloudflare Tunnel)
- [ ] Cookies set with `secure` flag in production
- [ ] CORS origins restricted to app scheme
- [ ] NAS firewall configured (block direct port access)
- [ ] Regular PostgreSQL backups enabled
- [ ] Monitor logs for suspicious activity

---

## 🐛 Troubleshooting

### Container won't start

**Check logs:**
```bash
docker logs scanium-api
```

**Common causes:**
- Missing environment variables (check `.env`)
- Database not ready (wait for postgres health check)
- Port 8080 already in use

### Database connection failed

**Verify DATABASE_URL:**
- Host must be `postgres` (Docker service name, not localhost)
- Port must be `5432`
- Credentials must match `POSTGRES_*` env vars

**Test connection:**
```bash
docker exec -it scanium-postgres psql -U scanium -d scanium
```

### Cloudflare Tunnel not connecting

**Check token:**
```bash
docker logs scanium-cloudflared
```

**Common causes:**
- Invalid `CLOUDFLARED_TOKEN`
- Token has spaces/quotes (should be raw token)
- Tunnel deleted in Cloudflare Dashboard

**Verify tunnel status:**
- Cloudflare Dashboard > Networks > Tunnels
- Should show "HEALTHY"

### OAuth redirect_uri_mismatch

**Verify RuName:**
- eBay Developer Portal > Application Keys > User Tokens
- RuName URL must exactly match: `https://api.yourdomain.com/auth/ebay/callback`
- No trailing slash
- Check `PUBLIC_BASE_URL` in `.env`

### OAuth state mismatch

**Causes:**
- Cookies blocked/cleared
- Multiple OAuth attempts

**Fix:**
- Clear browser cookies
- Retry OAuth from fresh start
- Verify `SESSION_SIGNING_SECRET` is set

---

## 📊 Monitoring

### View logs in real-time:
```bash
docker-compose logs -f api
```

### Check resource usage:
```bash
docker stats scanium-api scanium-postgres scanium-cloudflared
```

### Database backup:
```bash
docker exec scanium-postgres pg_dump -U scanium scanium > backup_$(date +%Y%m%d).sql
```

### Restore backup:
```bash
cat backup_20241212.sql | docker exec -i scanium-postgres psql -U scanium scanium
```

---

## 🎯 Next Steps

1. ✅ Backend deployed on NAS
2. ✅ Cloudflare Tunnel configured
3. ✅ eBay OAuth endpoints working
4. ✅ Health checks passing
5. 🔜 Integrate Android app with backend
6. 🔜 Test end-to-end OAuth flow
7. 🔜 Implement eBay listing creation (Sell API)
8. 🔜 Add image upload functionality
9. 🔜 Implement token refresh logic
10. 🔜 Add multi-user authentication

---

## 📚 Documentation Index

- [Backend README](backend/README.md) - Development guide
- [Setup Guide](backend/SETUP_GUIDE.md) - Detailed deployment steps
- [Mobile App Integration](md/backend/MOBILE_APP_INTEGRATION.md) - Android implementation
- [eBay OAuth API](backend/src/modules/auth/ebay/README.md) - Endpoint documentation

---

## ✅ Deployment Complete!

Your Scanium backend is now:

- ✅ Running on your Synology NAS
- ✅ Accessible publicly via Cloudflare Tunnel
- ✅ Connected to PostgreSQL database
- ✅ Ready for eBay OAuth integration
- ✅ Prepared for future marketplace adapters
- ✅ Containerized and production-ready

**API URL:** `https://api.yourdomain.com`

**Test it now:**
```bash
curl https://api.yourdomain.com/healthz
```

🎉 **Success!**
