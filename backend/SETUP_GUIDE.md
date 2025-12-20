***REMOVED*** Scanium Backend Setup Guide

Complete step-by-step guide for deploying the Scanium backend on a Synology NAS with Cloudflare Tunnel.

***REMOVED******REMOVED*** 📋 Prerequisites

- Synology NAS with Container Manager installed
- Domain name with DNS managed by Cloudflare
- eBay Developer account
- Node.js 20 (for local development)

---

***REMOVED******REMOVED*** 🌐 Part A: Cloudflare Tunnel Setup

***REMOVED******REMOVED******REMOVED*** 1. Create Cloudflare Tunnel

1. Log into [Cloudflare Zero Trust Dashboard](https://one.dash.cloudflare.com/)
2. Navigate to **Networks > Tunnels**
3. Click **Create a tunnel**
4. Select **Cloudflared** connector type
5. Name your tunnel (e.g., `scanium-api`)
6. Click **Next**

***REMOVED******REMOVED******REMOVED*** 2. Get Tunnel Token

1. On the **Install and run a connector** page, select **Docker**
2. Copy the tunnel token from the command:
   ```bash
   docker run cloudflare/cloudflared:latest tunnel --no-autoupdate run --token <YOUR_TOKEN>
   ```
3. Save this token - you'll need it for `CLOUDFLARED_TOKEN` environment variable

***REMOVED******REMOVED******REMOVED*** 3. Configure Public Hostname

1. Click **Next** to go to **Route tunnel**
2. Add a public hostname:
   - **Subdomain**: `api` (or your choice)
   - **Domain**: Select your domain (e.g., `yourdomain.com`)
   - **Path**: Leave blank
   - **Type**: `HTTP`
   - **URL**: `api:8080`
     - `api` is the Docker service name from docker-compose.yml
     - `8080` is the internal port
3. Click **Save tunnel**

Your API will be accessible at: `https://api.yourdomain.com`

---

***REMOVED******REMOVED*** 🌍 Part B: DNS Configuration

***REMOVED******REMOVED******REMOVED*** Verify DNS Setup

1. Go to **Cloudflare Dashboard > DNS > Records**
2. You should see a new CNAME record:
   - **Type**: CNAME
   - **Name**: `api` (or your subdomain)
   - **Target**: `<uuid>.cfargotunnel.com`
   - **Proxy status**: Proxied (orange cloud)

This is automatically created by the tunnel. No manual DNS changes needed!

---

***REMOVED******REMOVED*** 🛒 Part C: eBay Developer Credentials

***REMOVED******REMOVED******REMOVED*** 1. Create eBay Developer Account

1. Go to [eBay Developers Program](https://developer.ebay.com/)
2. Sign in with your eBay account (or create one)
3. Complete the registration

***REMOVED******REMOVED******REMOVED*** 2. Create Application Keys (Sandbox)

1. Go to [My Account > Application Keys](https://developer.ebay.com/my/keys)
2. Under **Sandbox Keys**, click **Create a Keyset**
3. Note down:
   - **App ID (Client ID)**
   - **Cert ID (Client Secret)**

***REMOVED******REMOVED******REMOVED*** 3. Configure OAuth Redirect URL (RuName)

1. Still on the Application Keys page, scroll to **User Tokens**
2. Click **Get a Token from eBay**
3. You'll need to create an **RuName** (Redirect URL Name):
   - Click **Add RuName**
   - **Your Privacy Policy URL**: Enter any valid URL (e.g., `https://yourdomain.com/privacy`)
   - **Your Auth Accepted URL**: `https://api.yourdomain.com/auth/ebay/callback`
     - ⚠️ This MUST match your `PUBLIC_BASE_URL + EBAY_REDIRECT_PATH`
   - Click **Save**

***REMOVED******REMOVED******REMOVED*** 4. Grant Application Access

1. After creating RuName, click **Get a Token from eBay via Your Application**
2. Select your RuName from dropdown
3. Click **Sign in to Production** (or Sandbox for testing)
4. Authorize the application
5. You don't need the token shown - this is just to activate the RuName

***REMOVED******REMOVED******REMOVED*** 5. Required OAuth Scopes

When configuring `EBAY_SCOPES`, include these for listing functionality:

```
https://api.ebay.com/oauth/api_scope https://api.ebay.com/oauth/api_scope/sell.inventory https://api.ebay.com/oauth/api_scope/sell.fulfillment https://api.ebay.com/oauth/api_scope/sell.account
```

**Scope Breakdown:**
- `api_scope` - Basic eBay API access
- `sell.inventory` - Create and manage listings
- `sell.fulfillment` - Order fulfillment and shipping
- `sell.account` - Account and payment policies

[Full scope reference](https://developer.ebay.com/api-docs/static/oauth-scopes.html)

***REMOVED******REMOVED******REMOVED*** 6. Production Keys (Later)

To use production eBay:

1. Go to **Production Keys** section
2. Click **Create a Keyset**
3. Repeat RuName configuration for production
4. Update `.env` to use production keys and set `EBAY_ENV=production`

---

***REMOVED******REMOVED*** 🔐 Part D: Environment Variables Setup

***REMOVED******REMOVED******REMOVED*** 1. Create `.env` File

On your NAS, create `backend/.env`:

```bash
cd /path/to/scanium/backend
nano .env
```

***REMOVED******REMOVED******REMOVED*** 2. Fill in Environment Variables

```bash
***REMOVED*** Application
NODE_ENV=production
PORT=8080

***REMOVED*** Public URL - MUST match your Cloudflare Tunnel hostname
PUBLIC_BASE_URL=https://api.yourdomain.com

***REMOVED*** Database
DATABASE_URL=postgresql://scanium:CHANGE_ME_STRONG_PASSWORD@postgres:5432/scanium

***REMOVED*** eBay OAuth
EBAY_ENV=sandbox
EBAY_CLIENT_ID=your_ebay_app_id_from_developer_portal
EBAY_CLIENT_SECRET=your_ebay_cert_id_from_developer_portal
EBAY_TOKEN_ENCRYPTION_KEY=change_me_to_32+_char_secret_for_tokens
EBAY_REDIRECT_PATH=/auth/ebay/callback
EBAY_SCOPES=https://api.ebay.com/oauth/api_scope https://api.ebay.com/oauth/api_scope/sell.inventory https://api.ebay.com/oauth/api_scope/sell.fulfillment https://api.ebay.com/oauth/api_scope/sell.account

***REMOVED*** Session Security - Generate with: openssl rand -base64 64
SESSION_SIGNING_SECRET=CHANGE_ME_GENERATE_RANDOM_64_CHARS

***REMOVED*** CORS Origins - Include your app scheme
CORS_ORIGINS=scanium://,http://localhost:3000

***REMOVED*** Postgres Credentials
POSTGRES_USER=scanium
POSTGRES_PASSWORD=CHANGE_ME_STRONG_PASSWORD
POSTGRES_DB=scanium

***REMOVED*** Cloudflare Tunnel Token (from Part A, step 2)
CLOUDFLARED_TOKEN=your_tunnel_token_here
```

***REMOVED******REMOVED******REMOVED*** 3. Generate Secure Secrets

```bash
***REMOVED*** Generate session secret
openssl rand -base64 64

***REMOVED*** Generate PostgreSQL password
openssl rand -base64 24
```

Replace `CHANGE_ME_*` values with generated secrets.

---

***REMOVED******REMOVED*** 🐳 Part E: Deploy on Synology NAS

***REMOVED******REMOVED******REMOVED*** Method 1: Container Manager UI (Recommended)

1. **Upload Project**:
   - Open **File Station**
   - Navigate to `/docker/projects/` (or create it)
   - Upload the entire `backend/` folder
   - Result: `/docker/projects/scanium-backend/`

2. **Open Container Manager**:
   - Open **Container Manager** app on NAS
   - Go to **Project** tab
   - Click **Create**

3. **Configure Project**:
   - **Project Name**: `scanium`
   - **Path**: Select `/docker/projects/scanium-backend`
   - **Source**: `docker-compose.yml`
   - Click **Next**

4. **Set Environment Variables** (if not using .env file):
   - Container Manager will show all services
   - Click on **General Settings**
   - Add environment variables from `.env`
   - OR: Ensure `.env` file is present in project folder

5. **Start Project**:
   - Review configuration
   - Click **Done**
   - Project will build and start automatically

***REMOVED******REMOVED******REMOVED*** Method 2: SSH/Terminal

1. **SSH into NAS**:
   ```bash
   ssh admin@your-nas-ip
   ```

2. **Navigate to project**:
   ```bash
   cd /volume1/docker/scanium-backend
   ```

3. **Start services**:
   ```bash
   sudo docker-compose up -d
   ```

4. **Run database migrations**:
   ```bash
   sudo docker exec -it scanium-api sh
   npx prisma migrate deploy
   exit
   ```

***REMOVED******REMOVED******REMOVED*** Initial Database Migration

After first deployment:

```bash
***REMOVED*** Connect to API container
docker exec -it scanium-api sh

***REMOVED*** Run migrations
npx prisma migrate deploy

***REMOVED*** Exit
exit
```

---

***REMOVED******REMOVED*** ✅ Part F: Verify Deployment

***REMOVED******REMOVED******REMOVED*** 1. Check Container Status

In Container Manager or via SSH:

```bash
docker ps
```

You should see three containers running:
- `scanium-postgres`
- `scanium-api`
- `scanium-cloudflared`

***REMOVED******REMOVED******REMOVED*** 2. Check Logs

```bash
***REMOVED*** API logs
docker logs scanium-api

***REMOVED*** Cloudflared logs
docker logs scanium-cloudflared

***REMOVED*** Postgres logs
docker logs scanium-postgres
```

***REMOVED******REMOVED******REMOVED*** 3. Test Endpoints

**Health Check**:
```bash
curl https://api.yourdomain.com/healthz
```

Expected response:
```json
{
  "status": "ok",
  "timestamp": "2024-..."
}
```

**Readiness Check** (Database connectivity):
```bash
curl https://api.yourdomain.com/readyz
```

Expected response:
```json
{
  "status": "ok",
  "database": "connected",
  "timestamp": "2024-..."
}
```

**API Info**:
```bash
curl https://api.yourdomain.com/
```

**eBay Connection Status**:
```bash
curl https://api.yourdomain.com/auth/ebay/status
```

Expected response (before OAuth):
```json
{
  "connected": false,
  "environment": null,
  "scopes": null,
  "expiresAt": null
}
```

---

***REMOVED******REMOVED*** 📱 Part G: Mobile App Integration (Summary)

See [MOBILE_APP_INTEGRATION.md](../md/backend/MOBILE_APP_INTEGRATION.md) for detailed Android implementation.

**Quick Summary:**

1. **Add API Client** to Android app
2. **Create Settings Screen** with "Connect eBay" button
3. **Implement OAuth Flow**:
   - Call `POST /auth/ebay/start`
   - Open returned URL in Custom Tab
   - Poll `GET /auth/ebay/status` to confirm connection
4. **Update API Base URL**:
   ```kotlin
   val api = ScaniumApi("https://api.yourdomain.com")
   ```

---

***REMOVED******REMOVED*** 🔧 Troubleshooting

***REMOVED******REMOVED******REMOVED*** Container won't start

**Check logs**:
```bash
docker logs scanium-api
```

**Common issues**:
- Missing environment variables
- Database not ready (check postgres logs)
- Port conflict (ensure 8080 is free)

***REMOVED******REMOVED******REMOVED*** Database connection failed

**Check DATABASE_URL**:
- Host must be `postgres` (Docker service name)
- Port must be `5432`
- Credentials must match POSTGRES_* env vars

**Manually connect**:
```bash
docker exec -it scanium-postgres psql -U scanium -d scanium
```

***REMOVED******REMOVED******REMOVED*** Cloudflare Tunnel not connecting

**Check token**:
- Verify `CLOUDFLARED_TOKEN` is correct
- No extra spaces or quotes

**View tunnel status**:
- Cloudflare Dashboard > Networks > Tunnels
- Should show "HEALTHY" status

**Check logs**:
```bash
docker logs scanium-cloudflared
```

***REMOVED******REMOVED******REMOVED*** OAuth fails with "redirect_uri_mismatch"

**Verify RuName configuration**:
- eBay Developer Portal > Application Keys > User Tokens
- RuName redirect URL must exactly match: `https://api.yourdomain.com/auth/ebay/callback`
- No trailing slash

**Check environment**:
- `PUBLIC_BASE_URL` must match tunnel URL
- `EBAY_REDIRECT_PATH` default is `/auth/ebay/callback`

***REMOVED******REMOVED******REMOVED*** OAuth state mismatch

**Cause**: Cookie issues or multiple OAuth attempts

**Fix**:
- Clear browser cookies
- Retry from fresh OAuth start
- Check that `SESSION_SIGNING_SECRET` is set

***REMOVED******REMOVED******REMOVED*** Can't access API from internet

**Check Cloudflare Tunnel**:
- Tunnel status should be "HEALTHY"
- Public hostname configured correctly
- DNS propagated (can take a few minutes)

**Test DNS**:
```bash
nslookup api.yourdomain.com
```

---

***REMOVED******REMOVED*** 🔄 Updating the Backend

***REMOVED******REMOVED******REMOVED*** Pull new code:
```bash
cd /volume1/docker/scanium-backend
git pull origin main
```

***REMOVED******REMOVED******REMOVED*** Rebuild and restart:
```bash
sudo docker-compose down
sudo docker-compose up -d --build
```

***REMOVED******REMOVED******REMOVED*** Run new migrations (if any):
```bash
docker exec -it scanium-api npx prisma migrate deploy
```

---

***REMOVED******REMOVED*** 🛡️ Security Checklist

- [ ] Strong `SESSION_SIGNING_SECRET` (min 32 chars)
- [ ] Strong `POSTGRES_PASSWORD`
- [ ] `CLOUDFLARED_TOKEN` kept secret
- [ ] eBay credentials kept secret
- [ ] `.env` file not committed to git
- [ ] HTTPS only (via Cloudflare)
- [ ] CORS origins restricted
- [ ] NAS firewall configured (block direct port access)
- [ ] Regular backups of PostgreSQL data
- [ ] Monitor logs for suspicious activity

---

***REMOVED******REMOVED*** 📊 Monitoring

***REMOVED******REMOVED******REMOVED*** Check system resources:
```bash
docker stats
```

***REMOVED******REMOVED******REMOVED*** View real-time logs:
```bash
docker-compose logs -f api
```

***REMOVED******REMOVED******REMOVED*** Database backup:
```bash
docker exec scanium-postgres pg_dump -U scanium scanium > backup_$(date +%Y%m%d).sql
```

---

***REMOVED******REMOVED*** 🎯 Next Steps

1. ✅ Backend deployed and accessible
2. ✅ eBay OAuth working
3. 🔜 Integrate Android app with backend
4. 🔜 Test OAuth flow end-to-end
5. 🔜 Implement eBay listing creation (future)
6. 🔜 Add image upload functionality (future)

---

***REMOVED******REMOVED*** 📚 Additional Resources

- [Backend README](README.md)
- [Mobile App Integration Guide](../md/backend/MOBILE_APP_INTEGRATION.md)
- [eBay OAuth API Documentation](src/modules/auth/ebay/README.md)
- [Cloudflare Tunnel Docs](https://developers.cloudflare.com/cloudflare-one/connections/connect-apps/)
- [eBay Developer Portal](https://developer.ebay.com/)
- [Prisma Documentation](https://www.prisma.io/docs)

---

***REMOVED******REMOVED*** 🆘 Getting Help

If you encounter issues:

1. Check logs: `docker logs scanium-api`
2. Verify environment variables
3. Check Cloudflare Tunnel status
4. Review eBay Developer Portal configuration
5. Test endpoints with curl
6. Check NAS system resources

For development support, consult the main project documentation.
