***REMOVED*** Backend HOWTO

***REMOVED******REMOVED*** 1. Overview

The backend is a Node.js + TypeScript Fastify service that provides cloud classification, assistant chat, vision insights, enrichment, pricing APIs, and remote config. It runs behind Docker (and Cloudflare Tunnel on NAS) and persists data in PostgreSQL for auth tokens. Telemetry is exported via OpenTelemetry (OTLP) to the monitoring stack.

Primary responsibilities for the Android app:

- Cloud classification (`/v1/classify`) and vision insights (`/v1/vision/insights`).
- Assistant chat and warmup (`/v1/assist/chat`, `/v1/assist/warmup`).
- Enrichment pipeline (`/v1/items/enrich` + `/v1/items/enrich/status/:requestId`).
- Remote config (`/v1/config`).
- Health and readiness (`/health`, `/healthz`, `/readyz`).

***REMOVED******REMOVED*** 2. Architecture

Runtime and entry points:

- Node.js 20 + TypeScript, Fastify.
- Entry: `backend/src/main.ts` -> `buildApp()` in `backend/src/app.ts`.
- Telemetry initialization: `backend/src/infra/telemetry/index.ts`.

Request flow (simplified):

1. Fastify app initializes logging, OpenTelemetry, and security plugins (`app.ts`).
2. `apiGuardPlugin` enforces `X-API-Key` for protected prefixes and rate limits per identity.
3. Correlation IDs are injected via `correlationPlugin`.
4. Routes are registered with `/v1` and `/auth/ebay` prefixes.

Key modules:

- Classifier: `backend/src/modules/classifier/` (cloud classification + vision attribute enrichment).
- Assistant: `backend/src/modules/assistant/` (LLM providers, safety filters, caching).
- Vision insights: `backend/src/modules/vision/` (fast OCR/logo/color extraction).
- Enrichment: `backend/src/modules/enrich/` (vision + attribute + draft pipeline).
- Pricing: `backend/src/modules/pricing/` (estimate endpoints and pricing logic).
- Config: `backend/src/modules/config/` (remote config payloads).
- Billing: `backend/src/modules/billing/` (stub verification).
- Health: `backend/src/modules/health/` (health + metrics endpoints).
- Admin: `backend/src/modules/admin/` (usage/debug, gated by admin key).

***REMOVED******REMOVED*** 3. API Surface

Public endpoints (current implementation in `backend/src/app.ts`):

- Health:
  - `GET /health`, `GET /healthz`, `GET /readyz`, `GET /metrics`
- Auth (eBay OAuth):
  - `POST /auth/ebay/start`, `GET /auth/ebay/callback`, `GET /auth/ebay/status`
- Classification:
  - `POST /v1/classify` (multipart image, optional `domainPackId`, `enrichAttributes`)
- Vision insights:
  - `POST /v1/vision/insights` (multipart image, optional `itemId`)
- Enrichment:
  - `POST /v1/items/enrich` (multipart image + JSON fields)
  - `GET /v1/items/enrich/status/:requestId`
  - `GET /v1/items/enrich/metrics`
- Assistant:
  - `POST /v1/assist/chat`
  - `POST /v1/assist/warmup`
  - `GET /v1/assist/chat/status/:requestId`
  - `GET /v1/assist/cache/stats`
- Pricing:
  - `POST /v1/pricing/estimate`
  - `POST /v1/pricing/estimate/v2`
  - `GET /v1/pricing/categories`
  - `GET /v1/pricing/brands/:brand`
  - `GET /v1/pricing/conditions`
  - `GET /v1/pricing/regions`
- Remote config:
  - `GET /v1/config`
- Billing (stub):
  - `POST /v1/billing/verify/google`
- Admin (gated):
  - `GET /v1/admin/usage`
  - `GET /v1/admin/debug/auth`
- Mobile telemetry ingestion:
  - `POST /v1/telemetry/mobile`

Authentication model:

- Protected routes require `X-API-Key` (enforced by `apiGuardPlugin` and per-module checks).
- Admin endpoints require `X-Admin-Key` and `admin.enabled` config.
- `apiGuardPlugin` does per-container, in-memory rate limiting based on `X-API-Key` or IP.

Rate limiting:

- Classifier and assistant use sliding-window limiters with optional Redis for shared state (`backend/src/infra/rate-limit/`).
- Per-IP, per-API key, and per-device limits are enforced at the route level.

***REMOVED******REMOVED*** 4. Business Logic

Classification (cloud mode):

- `ClassifierService` maps provider responses to domain categories via `backend/src/modules/classifier/domain/`.
- Attribute enrichment (OCR, logos, colors) is optional and uses `VisionExtractor` when enabled.

Pricing:

- Pricing logic lives in `backend/src/modules/pricing/` and includes baseline and V2 market-aware estimators.
- Category and brand tier lookups are handled via `category-pricing.ts` and `brand-tiers.ts`.

Assistant:

- Providers: mock, OpenAI, Claude (see `backend/src/modules/assistant/`).
- Safety: prompt and input sanitation in `assistant/safety.ts`.
- Caching: response cache and in-flight request dedup in `infra/cache/unified-cache.ts`.

***REMOVED******REMOVED*** 5. Data Handling

Storage layers:

- PostgreSQL (Prisma): primarily used for eBay OAuth token storage (`backend/src/modules/auth/ebay/token-storage.ts`).
- In-memory caches: classification cache, vision facts cache, assistant cache.
- Usage counters: in-memory `UsageStore` (`backend/src/modules/usage/usage-store.ts`).

File system usage:

- Images are sanitized in-memory; classifier utilities explicitly avoid disk writes (`backend/src/modules/classifier/utils/image.ts`).
- Docker volumes mount Postgres data and optional secrets/config files (see `backend/docker-compose.yml`).

Retention policies:

- Backend retention is primarily governed by DB and cache TTLs; no explicit long-term file retention is implemented for uploads.
- For app data retention policies, see `howto/infra/security/SECURITY.md`.

***REMOVED******REMOVED*** 6. Security Model

Authentication and authorization:

- API key validation is enforced in modules via `ApiKeyManager`.
- Admin routes require `X-Admin-Key` and `admin.enabled`.

Transport and headers:

- HTTPS enforcement and security headers via `securityPlugin` (`backend/src/infra/http/plugins/security.ts`).
- CORS enforced by `corsPlugin` using `CORS_ORIGINS` (`backend/src/infra/http/plugins/cors.ts`).

Input validation:

- Zod schemas enforce structured inputs (classifier, assistant, pricing, enrich).
- Multipart validation for image endpoints.

PII/logging:

- Logging redacts sensitive headers (`app.ts` logger config).
- `MobileTelemetry` endpoint expects sanitized, low-cardinality events.

Limitations:

- Android `RequestSigner` headers are not currently validated by backend routes. Treat signature headers as best-effort until server-side validation exists.

***REMOVED******REMOVED*** 7. Performance Considerations

- Classifier concurrency and rate limits are configurable (`CLASSIFIER_CONCURRENCY_LIMIT`, `CLASSIFIER_RATE_LIMIT_PER_MINUTE`).
- Assistant provider timeouts and quota limits are configurable in `config/index.ts`.
- OTLP metrics export runs on a 30s interval (`infra/telemetry/index.ts`).
- NAS deployments tune CPU/memory limits in `deploy/nas/compose/` files.

***REMOVED******REMOVED*** 8. Interaction With Android App

Expected contracts (as used in Android):

- `/v1/classify`: multipart image + `domainPackId` -> classification result with `domainCategoryId`, `confidence`, `label`, `attributes`, `requestId`.
- `/v1/vision/insights`: multipart image -> OCR/labels/logos/colors + category hint.
- `/v1/items/enrich`: multipart image + `itemId` -> async requestId, then `/status` to fetch result.
- `/v1/assist/chat`: JSON or multipart -> assistant response + optional `assistantError`.
- `/v1/assist/warmup`: preflight readiness.
- `/v1/config`: remote config payload used by `AndroidRemoteConfigProvider`.

Known gaps:

- Android health checks mention `/v1/preflight` and `/v1/assist/status`, but backend does not implement these routes. The backend implements `/v1/assist/warmup` and `/v1/assist/chat/status/:requestId` instead.

Versioning strategy:

- API routes use `/v1` prefixes but no explicit version negotiation. Android and backend are expected to move in lockstep.

***REMOVED******REMOVED*** 9. Interaction With Monitoring

Backend telemetry:

- OTLP logs, traces, and metrics are exported from `infra/telemetry/index.ts` to Alloy.
- HTTP request spans and metrics are emitted in `app.ts`.
- `/metrics` exposes Prometheus-formatted metrics used by Alloy scrapes.

***REMOVED******REMOVED*** 10. Deployment Notes (NAS)

Local dev (Docker):

- `backend/docker-compose.yml` runs PostgreSQL, backend API, and Cloudflared.
- Backend listens on port `8080` inside the container; exposed via Cloudflared in NAS scenarios.

NAS deployment:

- Compose files under `deploy/nas/compose/` define backend + monitoring stacks.
- Key mounts:
  - Postgres data: `/volume1/docker/scanium/postgres`.
  - Secrets: `/volume1/docker/scanium/secrets/vision-sa.json` for Google Vision.
  - Remote config file mounted to `/app/config/remote-config.json`.
- OTLP endpoint points to `scanium-alloy` on the monitoring network.

Assumptions:

- Cloudflare tunnel routes are configured for the backend in NAS setups.
- `PUBLIC_BASE_URL` and API keys are present in `backend/.env` (not committed).
