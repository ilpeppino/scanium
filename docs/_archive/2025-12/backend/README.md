> Archived on 2025-12-20: backend notes kept for reference; see docs/ARCHITECTURE.md for current state.
***REMOVED*** Scanium Backend – Vision Proxy

Cloud classification proxy for Scanium mobile apps. Provides `/v1/classify` that forwards images to Google Cloud Vision (or deterministic mock), maps signals to Scanium’s domain pack, and returns a normalized payload. Images are processed **in-memory only** and EXIF is stripped on ingestion.

***REMOVED******REMOVED*** What it ships
- `GET /health` – liveness with version
- `POST /v1/classify` – multipart image → Vision/mock → domain category/attributes
- API key auth via `X-API-Key`
- Per-key rate limit + per-key concurrency gate
- Mock mode for offline/local dev (no cloud dependency)
- Domain pack mapper (home_resale JSON) with configurable path
- Request size guard (default 5 MB) and EXIF stripping via in-memory re-encode
- Legacy eBay OAuth routes remain available under `/auth/ebay/*` for future listing flows

***REMOVED******REMOVED*** Quickstart (mock mode)
```bash
cd backend
npm install
cp .env.example .env
***REMOVED*** set SCANIUM_API_KEYS=dev-key
npm run dev

***REMOVED*** Test health
curl http://localhost:8080/health

***REMOVED*** Classify (mock)
curl -X POST http://localhost:8080/v1/classify \
  -H "X-API-Key: dev-key" \
  -F "image=@fixtures/chair.jpg" \
  -F "domainPackId=home_resale"
```

***REMOVED******REMOVED*** Google Vision mode
Set in `.env`:
```
SCANIUM_CLASSIFIER_PROVIDER=google
SCANIUM_API_KEYS=your-key
GOOGLE_APPLICATION_CREDENTIALS=/secrets/vision-sa.json
VISION_FEATURE=LABEL_DETECTION   ***REMOVED*** or OBJECT_LOCALIZATION
```
Run: `npm run build && npm start`

***REMOVED******REMOVED*** Environment variables (required unless noted)
- `SCANIUM_API_KEYS` – comma-separated API keys for `X-API-Key`
- `SCANIUM_CLASSIFIER_PROVIDER` – `mock` (default) | `google`
- `VISION_FEATURE` – `LABEL_DETECTION` (default) | `OBJECT_LOCALIZATION`
- `GOOGLE_APPLICATION_CREDENTIALS` – path to Service Account JSON (google mode)
- `MAX_UPLOAD_BYTES` – request file cap (default 5 MB)
- `CLASSIFIER_RATE_LIMIT_PER_MINUTE` – per-key limit (default 60)
- `CLASSIFIER_CONCURRENCY_LIMIT` – per-key in-flight cap (default 2)
- `DOMAIN_PACK_ID` / `DOMAIN_PACK_PATH` – active pack + JSON file path
- `CLASSIFIER_RETAIN_UPLOADS` – keep false; future opt-in to persist uploads
- `SESSION_SIGNING_SECRET` – required for Fastify cookies
- `PUBLIC_BASE_URL`, `DATABASE_URL`, `EBAY_*`, `CORS_ORIGINS` – kept for legacy eBay auth endpoints

***REMOVED******REMOVED*** API reference (classifier)
**POST /v1/classify**
- Multipart: `image` (jpg/png/webp, required), `domainPackId` (optional, defaults to env), `hints` (JSON string, optional)
- Headers: `X-API-Key: <key>`
- Response:
```json
{
  "requestId": "uuid",
  "domainPackId": "home_resale",
  "domainCategoryId": "chair",
  "confidence": 0.82,
  "attributes": {"segment": "seating"},
  "provider": "google-vision",
  "timingsMs": { "total": 180, "vision": 120, "mapping": 5 }
}
```
- Errors: `401` (missing/invalid key), `400` (bad multipart/unsupported file), `429` (per-key concurrency or rate limit)

**GET /health**
```json
{ "status": "ok", "ts": "2024-01-01T00:00:00Z", "version": "1.0.0" }
```

***REMOVED******REMOVED*** Build, test, and lint
```bash
npm test          ***REMOVED*** vitest (mock mode, includes request validation + mapper)
npm run build     ***REMOVED*** tsc
npm run start     ***REMOVED*** runs dist/main.js
```

***REMOVED******REMOVED*** Docker (NAS-friendly)
```bash
docker build -t scanium-backend .
docker-compose up -d
```
- Health check hits `/health`
- API key + rate limit enforced; images never persisted
- Cloudflare Tunnel supported via `cloudflared` service in compose

***REMOVED******REMOVED*** Deployment notes
- Run behind Cloudflare Tunnel; keep `/v1/classify` private via API key
- Use Service Account credentials mounted at `GOOGLE_APPLICATION_CREDENTIALS`
- Only one Vision feature enabled by default to control cost; change via `VISION_FEATURE`
- Logs (pino) include `requestId`, provider, timings; raw images are never logged

***REMOVED******REMOVED*** Legacy eBay flow
Routes under `/auth/ebay/*` stay intact; Postgres/Prisma remain in the stack for future listing work. They do not block classifier startup when unused.
   - Go to **User Tokens > Get a Token from eBay**
   - Add redirect URL: `https://api.yourdomain.com/auth/ebay/callback`

***REMOVED******REMOVED******REMOVED*** Step 5: Configure eBay Scopes

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

***REMOVED******REMOVED******REMOVED*** Step 6: Deploy on Synology NAS

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
   ***REMOVED*** Via UI: Click "Start"
   ***REMOVED*** OR via SSH:
   cd /path/to/backend
   docker-compose up -d
   ```

6. **Run Database Migrations**:
   ```bash
   ***REMOVED*** Connect to API container
   docker exec -it scanium-api sh

   ***REMOVED*** Run migrations
   npx prisma migrate deploy
   ```

***REMOVED******REMOVED******REMOVED*** Step 7: Verify Deployment

```bash
***REMOVED*** Check health
curl https://api.yourdomain.com/healthz

***REMOVED*** Check readiness (DB connectivity)
curl https://api.yourdomain.com/readyz

***REMOVED*** Check eBay connection status
curl https://api.yourdomain.com/auth/ebay/status
```

***REMOVED******REMOVED*** 🔐 Security Checklist

- [ ] Strong `SESSION_SIGNING_SECRET` (min 64 chars, random)
- [ ] Strong `POSTGRES_PASSWORD`
- [ ] `CLOUDFLARED_TOKEN` kept secret (not in git)
- [ ] eBay credentials kept secret (not in git)
- [ ] HTTPS only in production (via Cloudflare)
- [ ] Cookies set with `secure` flag in production
- [ ] CORS origins restricted to app scheme + trusted domains
- [ ] Firewall rules on NAS (block direct access to ports)

***REMOVED******REMOVED*** 📊 Database Management

***REMOVED******REMOVED******REMOVED*** View data:
```bash
npm run prisma:studio
```

***REMOVED******REMOVED******REMOVED*** Create migration:
```bash
npm run prisma:migrate
```

***REMOVED******REMOVED******REMOVED*** Deploy migrations (production):
```bash
docker exec -it scanium-api npx prisma migrate deploy
```

***REMOVED******REMOVED*** 🧪 Available Scripts

```bash
npm run dev           ***REMOVED*** Start dev server with hot reload
npm run build         ***REMOVED*** Build TypeScript to dist/
npm run start         ***REMOVED*** Run built application
npm run lint          ***REMOVED*** Lint TypeScript
npm run format        ***REMOVED*** Format code with Prettier
npm run typecheck     ***REMOVED*** Type check without building
npm run prisma:generate  ***REMOVED*** Generate Prisma client
npm run prisma:migrate   ***REMOVED*** Run migrations (dev)
npm run prisma:deploy    ***REMOVED*** Deploy migrations (prod)
npm run prisma:studio    ***REMOVED*** Open Prisma Studio (DB GUI)
npm run test          ***REMOVED*** Run tests
npm run test:watch    ***REMOVED*** Run tests in watch mode
```

***REMOVED******REMOVED*** 🔧 Configuration Reference

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
- `EBAY_TOKEN_ENCRYPTION_KEY`
- `SESSION_SIGNING_SECRET`
- `CORS_ORIGINS`

**Optional (for NAS deployment):**
- `CLOUDFLARED_TOKEN`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `POSTGRES_DB`

***REMOVED******REMOVED*** 📱 Mobile App Integration

***REMOVED******REMOVED******REMOVED*** eBay OAuth Flow

1. **User taps "Connect eBay" in app**
2. App calls `POST /auth/ebay/start`
3. App opens `authorizeUrl` in Custom Tab (Android) / Safari View Controller (iOS)
4. User logs into eBay and authorizes
5. eBay redirects to `/auth/ebay/callback`
6. Backend exchanges code for tokens and stores them
7. Browser shows success page
8. App polls `GET /auth/ebay/status` to confirm connection

See `src/modules/auth/ebay/README.md` for detailed API documentation.

***REMOVED******REMOVED*** 🚧 Future Enhancements

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

***REMOVED******REMOVED*** 🐛 Troubleshooting

***REMOVED******REMOVED******REMOVED*** Database connection failed

Check `DATABASE_URL` and ensure PostgreSQL is running:
```bash
docker-compose ps postgres
docker-compose logs postgres
```

***REMOVED******REMOVED******REMOVED*** OAuth state mismatch

- Clear cookies and retry
- Ensure `PUBLIC_BASE_URL` matches the actual URL
- Check that cookies are enabled in browser

***REMOVED******REMOVED******REMOVED*** Cloudflare Tunnel not connecting

- Verify `CLOUDFLARED_TOKEN` is correct
- Check tunnel status in Cloudflare Dashboard
- View logs: `docker-compose logs cloudflared`

***REMOVED******REMOVED******REMOVED*** eBay token exchange failed

- Verify `EBAY_CLIENT_ID` and `EBAY_CLIENT_SECRET`
- Ensure redirect URI matches eBay app configuration
- Check `EBAY_ENV` (sandbox vs production)
- View logs: `docker-compose logs api`

***REMOVED******REMOVED*** 📝 License

UNLICENSED - Proprietary to Scanium
