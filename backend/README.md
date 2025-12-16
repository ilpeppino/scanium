# Scanium Backend API

Production-grade TypeScript backend for Scanium mobile app, providing eBay OAuth and marketplace integration.

## 🏗️ Architecture

- **Runtime**: Node.js 20 LTS
- **Framework**: Fastify (high-performance HTTP server)
- **Language**: TypeScript (strict mode)
- **Database**: PostgreSQL 16 + Prisma ORM
- **Validation**: Zod
- **Logging**: Pino (structured logging)
- **Container**: Docker + Docker Compose
- **Deployment**: Synology NAS + Cloudflare Tunnel

## 📁 Project Structure

```
backend/
├── src/
│   ├── config/           # Environment validation (Zod)
│   ├── infra/
│   │   ├── db/           # Prisma client wrapper
│   │   └── http/plugins/ # Fastify plugins (CORS, cookies, errors)
│   ├── modules/
│   │   ├── health/       # Health check endpoints
│   │   ├── auth/ebay/    # eBay OAuth flow
│   │   ├── marketplaces/ # Marketplace adapter interfaces
│   │   ├── listings/     # Future: listing creation
│   │   └── media/        # Future: image upload
│   ├── shared/           # Shared utilities and error types
│   ├── app.ts            # Fastify app builder
│   └── main.ts           # Application entrypoint
├── prisma/
│   ├── schema.prisma     # Database schema
│   └── migrations/       # Database migrations
├── Dockerfile            # Multi-stage production build
├── docker-compose.yml    # NAS deployment stack
└── package.json          # Dependencies and scripts
```

## 🚀 Local Development

### Prerequisites

- Node.js 20 LTS
- Docker + Docker Compose
- PostgreSQL (via Docker)

### Setup

1. **Install dependencies:**
   ```bash
   cd backend
   npm install
   ```

2. **Create `.env` file:**
   ```bash
   cp .env.example .env
   # Edit .env with your configuration
   ```

3. **Start PostgreSQL:**
   ```bash
   docker-compose up postgres -d
   ```

4. **Run database migrations:**
   ```bash
   npm run prisma:migrate
   ```

5. **Generate Prisma client:**
   ```bash
   npm run prisma:generate
   ```

6. **Start development server:**
   ```bash
   npm run dev
   ```

Server will start on `http://localhost:8080`

### Testing Endpoints

```bash
# Health check
curl http://localhost:8080/healthz

# Readiness check (DB connectivity)
curl http://localhost:8080/readyz

# Start OAuth flow
curl -X POST http://localhost:8080/auth/ebay/start

# Check eBay connection status
curl http://localhost:8080/auth/ebay/status
```

## 🐳 Docker Build

### Build image:
```bash
docker build -t scanium-backend .
```

### Run with Docker Compose (local testing):
```bash
docker-compose up
```

## 📦 NAS Deployment

### Step 1: Prepare Environment

1. Create `.env` file with production values (see `.env.example`)
2. Ensure `PUBLIC_BASE_URL` matches your Cloudflare Tunnel URL
3. Set strong `SESSION_SIGNING_SECRET` (min 32 chars):
   ```bash
   openssl rand -base64 32
   ```

### Step 2: Setup Cloudflare Tunnel

1. Go to Cloudflare Zero Trust Dashboard
2. Navigate to **Access > Tunnels**
3. Click **Create a tunnel**
4. Choose **Docker** connector
5. Name it (e.g., "scanium-api")
6. Copy the tunnel token
7. Add to `.env` as `CLOUDFLARED_TOKEN=...`

### Step 3: Configure Public Hostname

In Cloudflare Tunnel configuration:

1. Click **Add a public hostname**
2. **Subdomain**: `api` (or your choice)
3. **Domain**: Your domain (e.g., `yourdomain.com`)
4. **Service Type**: `HTTP`
5. **URL**: `api:8080` (Docker service name + port)
6. Save

Your API will be accessible at `https://api.yourdomain.com`

### Step 4: Get eBay Credentials

1. Go to [eBay Developer Portal](https://developer.ebay.com/my/keys)
2. Create Application Keyset (Sandbox or Production)
3. Copy **Client ID** and **Client Secret**
4. Add to `.env`:
   ```
   EBAY_CLIENT_ID=your_client_id
   EBAY_CLIENT_SECRET=your_client_secret
   EBAY_ENV=sandbox  # or production
   ```

5. Configure **RuName** (Redirect URL):
   - Go to **User Tokens > Get a Token from eBay**
   - Add redirect URL: `https://api.yourdomain.com/auth/ebay/callback`

### Step 5: Configure eBay Scopes

Add required scopes to `.env`:

```bash
EBAY_SCOPES=https://api.ebay.com/oauth/api_scope https://api.ebay.com/oauth/api_scope/sell.inventory https://api.ebay.com/oauth/api_scope/sell.fulfillment https://api.ebay.com/oauth/api_scope/sell.account
```

**Scope Reference:**
- `https://api.ebay.com/oauth/api_scope` - Basic eBay access
- `https://api.ebay.com/oauth/api_scope/sell.inventory` - Create/manage listings
- `https://api.ebay.com/oauth/api_scope/sell.fulfillment` - Order management
- `https://api.ebay.com/oauth/api_scope/sell.account` - Account preferences

Find all scopes: [eBay OAuth Scopes](https://developer.ebay.com/api-docs/static/oauth-scopes.html)

### Step 6: Deploy on Synology NAS

1. **Upload backend folder** to NAS (via File Station or SSH)

2. **Open Container Manager** on Synology

3. **Create Project**:
   - Name: `scanium`
   - Path: `/path/to/backend`
   - Source: `docker-compose.yml`

4. **Set Environment Variables**:
   - In project settings, add all variables from `.env`
   - OR: Synology will read `.env` file automatically

5. **Start Project**:
   ```bash
   # Via UI: Click "Start"
   # OR via SSH:
   cd /path/to/backend
   docker-compose up -d
   ```

6. **Run Database Migrations**:
   ```bash
   # Connect to API container
   docker exec -it scanium-api sh

   # Run migrations
   npx prisma migrate deploy
   ```

### Step 7: Verify Deployment

```bash
# Check health
curl https://api.yourdomain.com/healthz

# Check readiness (DB connectivity)
curl https://api.yourdomain.com/readyz

# Check eBay connection status
curl https://api.yourdomain.com/auth/ebay/status
```

## 🔐 Security Checklist

- [ ] Strong `SESSION_SIGNING_SECRET` (min 32 chars)
- [ ] Strong `POSTGRES_PASSWORD`
- [ ] `CLOUDFLARED_TOKEN` kept secret (not in git)
- [ ] eBay credentials kept secret (not in git)
- [ ] HTTPS only in production (via Cloudflare)
- [ ] Cookies set with `secure` flag in production
- [ ] CORS origins restricted to app scheme + trusted domains
- [ ] Firewall rules on NAS (block direct access to ports)

## 📊 Database Management

### View data:
```bash
npm run prisma:studio
```

### Create migration:
```bash
npm run prisma:migrate
```

### Deploy migrations (production):
```bash
docker exec -it scanium-api npx prisma migrate deploy
```

## 🧪 Available Scripts

```bash
npm run dev           # Start dev server with hot reload
npm run build         # Build TypeScript to dist/
npm run start         # Run built application
npm run lint          # Lint TypeScript
npm run format        # Format code with Prettier
npm run typecheck     # Type check without building
npm run prisma:generate  # Generate Prisma client
npm run prisma:migrate   # Run migrations (dev)
npm run prisma:deploy    # Deploy migrations (prod)
npm run prisma:studio    # Open Prisma Studio (DB GUI)
npm run test          # Run tests
npm run test:watch    # Run tests in watch mode
```

## 🔧 Configuration Reference

See `.env.example` for all environment variables.

**Required:**
- `NODE_ENV`
- `PORT`
- `PUBLIC_BASE_URL`
- `DATABASE_URL`
- `EBAY_ENV`
- `EBAY_CLIENT_ID`
- `EBAY_CLIENT_SECRET`
- `EBAY_SCOPES`
- `SESSION_SIGNING_SECRET`
- `CORS_ORIGINS`

**Optional (for NAS deployment):**
- `CLOUDFLARED_TOKEN`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `POSTGRES_DB`

## 📱 Mobile App Integration

### eBay OAuth Flow

1. **User taps "Connect eBay" in app**
2. App calls `POST /auth/ebay/start`
3. App opens `authorizeUrl` in Custom Tab (Android) / Safari View Controller (iOS)
4. User logs into eBay and authorizes
5. eBay redirects to `/auth/ebay/callback`
6. Backend exchanges code for tokens and stores them
7. Browser shows success page
8. App polls `GET /auth/ebay/status` to confirm connection

See `src/modules/auth/ebay/README.md` for detailed API documentation.

## 🚧 Future Enhancements

- [ ] Token refresh logic (automatic renewal)
- [ ] Multi-user authentication (replace default user)
- [ ] Token encryption at rest
- [ ] eBay listing creation endpoints (Sell API)
- [ ] Image upload and processing
- [ ] Job queue for async tasks (BullMQ)
- [ ] Additional marketplace adapters (Mercari, Facebook, etc.)
- [ ] OpenAPI/Swagger documentation
- [ ] Rate limiting
- [ ] API versioning

## 🐛 Troubleshooting

### Database connection failed

Check `DATABASE_URL` and ensure PostgreSQL is running:
```bash
docker-compose ps postgres
docker-compose logs postgres
```

### OAuth state mismatch

- Clear cookies and retry
- Ensure `PUBLIC_BASE_URL` matches the actual URL
- Check that cookies are enabled in browser

### Cloudflare Tunnel not connecting

- Verify `CLOUDFLARED_TOKEN` is correct
- Check tunnel status in Cloudflare Dashboard
- View logs: `docker-compose logs cloudflared`

### eBay token exchange failed

- Verify `EBAY_CLIENT_ID` and `EBAY_CLIENT_SECRET`
- Ensure redirect URI matches eBay app configuration
- Check `EBAY_ENV` (sandbox vs production)
- View logs: `docker-compose logs api`

## 📝 License

UNLICENSED - Proprietary to Scanium
