#!/bin/bash
# Create prioritized GitHub issues for Scanium go-live readiness
# Prerequisites: Run CREATE_LABELS.sh first
# Usage: bash docs/go-live/CREATE_ISSUES.sh

set -e

echo "🚀 Creating go-live issues in priority order..."
echo ""

###########################################
# P0 CRITICAL - Must be done before go-live
###########################################

echo "Creating P0 severity:critical issues..."

gh issue create \
  --title "[GO-LIVE][CRITICAL] Backend production deployment configuration missing" \
  --label "severity:critical,epic:backend,area:backend,priority:p0" \
  --body "## Epic
epic:backend

## Priority
**P0** – must be done before any real go-live

## Problem Statement
Backend currently runs in development mode only. No production deployment configuration exists for Kubernetes, Cloud Run, or containerized environments.

**Why it matters:** Cannot deploy backend to production without containerization, health checks, resource limits, and scaling policies.

## Evidence
- \`docs/ARCHITECTURE.md:470\` - Roadmap mentions \"Production deployment configuration (Kubernetes/Cloud Run)\" as TODO
- \`backend/src/main.ts\` - Only development setup
- No Dockerfile for backend application
- No Kubernetes/Cloud Run manifests
- \`backend/.env.example\` has dev defaults only

## Acceptance Criteria
- [ ] Create production Dockerfile with multi-stage build
- [ ] Create docker-compose.yml for full-stack local testing
- [ ] Document deployment options (K8s/Cloud Run/Docker Swarm)
- [ ] Create K8s manifests (deployment, service, ingress) OR Cloud Run config
- [ ] Configure environment-specific settings (dev/staging/prod)
- [ ] Set resource limits and HPA policies
- [ ] Configure readiness/liveness probes (/healthz, /readyz)
- [ ] Document SSL/TLS termination strategy
- [ ] Document secret management (K8s secrets, Cloud Secret Manager)
- [ ] Test deployment to staging environment

## Verification Steps
\`\`\`bash
docker build -t scanium-backend:prod -f backend/Dockerfile .
docker run -p 8080:8080 --env-file backend/.env.prod scanium-backend:prod
curl http://localhost:8080/healthz  # 200 OK
kubectl apply -f k8s/staging/ && kubectl get pods -n scanium-staging
\`\`\`"

gh issue create \
  --title "[GO-LIVE][CRITICAL] Implement backend authentication and authorization" \
  --label "severity:critical,epic:backend,area:backend,area:auth,priority:p0" \
  --body "## Epic
epic:backend

## Priority
**P0** – must be done before any real go-live

## Problem Statement
Backend API endpoints have NO authentication or authorization beyond optional API keys for specific routes (/v1/classify, /v1/assist). All other endpoints are publicly accessible. User data (eBay tokens, listings) is not protected.

**Why it matters:** Production API would allow anyone to:
- Access user eBay OAuth tokens from database
- Create/modify listings for other users
- Exhaust rate limits and quotas
- Violate GDPR/privacy regulations

## Evidence
- \`backend/src/app.ts\` - Routes registered without auth middleware
- \`backend/prisma/schema.prisma\` - User/EbayConnection models exist but no session/JWT auth
- \`docs/ARCHITECTURE.md:471\` - \"Backend API authentication and authorization\" in roadmap
- \`backend/src/config/index.ts\` - SESSION_SIGNING_SECRET configured but unused for auth
- No JWT or session middleware in \`backend/src/infra/http/plugins/\`

## Acceptance Criteria
- [ ] Choose auth strategy: JWT (stateless) vs sessions (stateful with Redis)
- [ ] Implement auth middleware (verify JWT/session on protected routes)
- [ ] Create auth endpoints: POST /v1/auth/register, POST /v1/auth/login, POST /v1/auth/logout
- [ ] Add user registration and login flows (email/password or OAuth)
- [ ] Protect user-specific routes (eBay status, listings, assistant)
- [ ] Add authorization checks (users can only access their own data)
- [ ] Add rate limiting per authenticated user (not just per IP)
- [ ] Document auth flow in API docs
- [ ] Add auth tests (unit + integration)
- [ ] Update mobile app to handle auth tokens (store in secure storage)

## Verification Steps
\`\`\`bash
# Unauthenticated request should fail
curl -X GET http://localhost:8080/auth/ebay/status
# Expected: 401 Unauthorized

# Register user
curl -X POST http://localhost:8080/v1/auth/register -d '{\"email\":\"test@example.com\",\"password\":\"secure123\"}'
# Expected: 201 Created with JWT token

# Authenticated request with token
curl -X GET http://localhost:8080/auth/ebay/status -H \"Authorization: Bearer <jwt_token>\"
# Expected: 200 OK with user's eBay connection status

# User A cannot access User B's data
curl -X GET http://localhost:8080/auth/ebay/status -H \"Authorization: Bearer <userB_token>\"
# Expected: 403 Forbidden
\`\`\`"

gh issue create \
  --title "[GO-LIVE][CRITICAL] Production observability: alerting and SLOs" \
  --label "severity:critical,epic:observability,area:backend,priority:p0" \
  --body "## Epic
epic:observability

## Priority
**P0** – must be done before any real go-live

## Problem Statement
Monitoring stack (Grafana, Loki, Tempo, Mimir) exists for DEV only with:
- Anonymous Grafana access (no auth)
- Local filesystem storage (no persistence, no backups)
- No alerting rules configured
- No SLOs/SLAs defined
- No on-call rotation or incident response process

**Why it matters:** Production outages would go undetected. No way to measure reliability or respond to incidents.

## Evidence
- \`monitoring/docker-compose.yml\` - Dev setup with anonymous auth
- \`monitoring/grafana/provisioning/\` - No alerting rules configured
- \`docs/ARCHITECTURE.md:73\` - Retention: 14d logs, 7d traces, 15d metrics (dev defaults)
- No Grafana Cloud, Datadog, or production LGTM deployment documented
- \`docs/DEV_GUIDE.md:353\` - \"Anonymous admin access for local dev (disable in production)\"

## Acceptance Criteria
- [ ] Configure Grafana authentication (OAuth, LDAP, or Grafana Cloud)
- [ ] Define SLOs: API latency (p95 < 500ms), error rate (< 1%), uptime (99.9%)
- [ ] Create alerting rules for: API down, high error rate, high latency, database connection failures
- [ ] Configure alert destinations (PagerDuty, Slack, email)
- [ ] Set up on-call rotation and escalation policy
- [ ] Document incident response playbook (triage, mitigation, postmortem)
- [ ] Configure production data retention (30d+ logs, 14d+ traces, 90d+ metrics)
- [ ] Test alerting: simulate API failure and verify alerts fire
- [ ] Create runbook for common incidents (database down, out of memory, high CPU)
- [ ] Deploy monitoring stack to production (Grafana Cloud or self-hosted HA setup)

## Verification Steps
\`\`\`bash
# Trigger test alert
curl -X POST http://localhost:8080/_test/trigger-alert

# Verify alert fires in Grafana
# Check PagerDuty/Slack for notification

# Simulate API downtime
kubectl scale deployment scanium-backend --replicas=0 -n scanium-prod

# Verify \"API Down\" alert fires within 60 seconds
# Verify on-call engineer receives page

# Check SLO dashboard
# Navigate to Grafana -> SLO Dashboard
# Verify p95 latency, error budget tracking
\`\`\`"

gh issue create \
  --title "[GO-LIVE][CRITICAL] PostgreSQL backup and disaster recovery strategy" \
  --label "severity:critical,epic:backend,area:backend,priority:p0" \
  --body "## Epic
epic:backend

## Priority
**P0** – must be done before any real go-live

## Problem Statement
PostgreSQL database has NO backup strategy. User data (eBay tokens, listings) would be lost permanently if database corrupts or container is destroyed.

**Why it matters:** Data loss = loss of all user eBay connections, listings, and trust. Violates basic reliability expectations.

## Evidence
- \`backend/docker-compose.yml\` - PostgreSQL container with no backup volumes
- No pg_dump scheduled jobs
- No point-in-time recovery (PITR) configured
- No replication or high availability setup
- \`docs/ARCHITECTURE.md\` - No backup strategy documented

## Acceptance Criteria
- [ ] Configure automated daily backups (pg_dump or WAL archiving)
- [ ] Store backups in durable remote storage (S3, GCS, or managed DB backups)
- [ ] Configure point-in-time recovery (PITR) with WAL archiving
- [ ] Document backup retention policy (e.g., 30 daily, 12 monthly)
- [ ] Test backup restoration process (restore to staging from backup)
- [ ] Set up replication for high availability (primary + standby replica)
- [ ] Configure automated backup verification (restore test every week)
- [ ] Document disaster recovery RTO/RPO targets (e.g., RTO 1h, RPO 15min)
- [ ] Create disaster recovery runbook (restore from backup, failover to replica)
- [ ] Monitor backup job success/failure with alerts

## Verification Steps
\`\`\`bash
# Verify backup job ran
ls -lh /backups/postgres/scanium-2025-01-01.dump

# Test restore to staging
createdb scanium_restore_test
pg_restore -d scanium_restore_test /backups/postgres/scanium-2025-01-01.dump
psql -d scanium_restore_test -c \"SELECT COUNT(*) FROM users;\"

# Simulate disaster: delete production DB
docker stop scanium-postgres && docker rm scanium-postgres

# Restore from latest backup
./scripts/restore-from-backup.sh --backup-file /backups/postgres/latest.dump

# Verify data integrity
psql -d scanium -c \"SELECT COUNT(*) FROM users;\"  # Should match pre-disaster count
\`\`\`"

gh issue create \
  --title "[GO-LIVE][CRITICAL] Environment separation: dev/staging/production" \
  --label "severity:critical,epic:backend,area:backend,area:ci,priority:p0" \
  --body "## Epic
epic:backend

## Priority
**P0** – must be done before any real go-live

## Problem Statement
No environment separation exists. Single \`.env\` file mixes dev and would-be-prod config. No staging environment to test changes before production deployment.

**Why it matters:** Deploying untested code directly to production = high risk of outages. Cannot safely test database migrations, API changes, or eBay integrations.

## Evidence
- \`backend/.env.example\` - Single environment file
- \`backend/src/config/index.ts:41\` - \`nodeEnv\` enum has 'development'|'production'|'test' but no staging
- No \`.env.development\`, \`.env.staging\`, \`.env.production\` files
- No environment-specific Kubernetes namespaces or Cloud Run services
- \`docs/CI_CD.md\` - No staging deployment workflow

## Acceptance Criteria
- [ ] Create environment-specific config files: \`.env.development\`, \`.env.staging\`, \`.env.production\`
- [ ] Configure environment-specific resources:
  - Dev: Local Docker containers, anonymous Grafana, mock eBay, mock Google Vision
  - Staging: Cloud-hosted DB, authenticated Grafana, sandbox eBay API, real Google Vision
  - Production: Managed DB with backups, production eBay API, alerts, rate limits
- [ ] Deploy staging environment (same infra as prod, separate namespace/project)
- [ ] Configure CI/CD to auto-deploy main branch to staging
- [ ] Require manual approval for production deployments
- [ ] Document promotion process: dev → staging → prod
- [ ] Configure separate Grafana organizations/folders per environment
- [ ] Use environment-specific API keys and secrets
- [ ] Test full flow: commit → staging deployment → verification → prod deployment

## Verification Steps
\`\`\`bash
# Deploy to staging
git push origin main  # Auto-deploys to staging via CI
kubectl get pods -n scanium-staging
curl https://staging-api.scanium.com/healthz

# Verify staging uses sandbox eBay
curl https://staging-api.scanium.com/ | jq '.environment'  # \"sandbox\"

# Deploy to production (manual approval required)
kubectl apply -f k8s/production/
kubectl get pods -n scanium-production
curl https://api.scanium.com/healthz

# Verify production uses production eBay
curl https://api.scanium.com/ | jq '.environment'  # \"production\"
\`\`\`"

gh issue create \
  --title "[GO-LIVE][HIGH] Verify Android release signing configuration" \
  --label "severity:high,epic:mobile,area:android,area:ci,priority:p0" \
  --body "## Epic
epic:mobile

## Priority
**P0** – required before any release build

## Problem Statement
Android release signing is configured in \`local.properties\` but not verified or documented. CI workflow only builds debug APK. No release APK workflow exists.

**From Security Assessment (SEC-015):** \"No signing config verification\" - Release signing not enforced in build.

**Why it matters:** Cannot publish to Play Store or distribute signed APKs without verified signing. Risk of losing keystore or publishing with wrong key.

## Evidence
- \`docs/RELEASE_CHECKLIST.md:9-31\` - Keystore setup documented but not enforced
- \`.github/workflows/android-debug-apk.yml\` - Only debug builds in CI
- \`androidApp/build.gradle.kts\` - Signing config reads from local.properties (not in CI)
- \`docs/_archive/2025-12/security/SECURITY_RISK_ASSESSMENT.md:1727\` - SEC-015 issue (P0)
- No \`.github/workflows/android-release-apk.yml\`

## Acceptance Criteria
- [ ] Document keystore location and backup strategy (encrypted off-site storage)
- [ ] Verify keystore file exists and is backed up (multiple secure locations)
- [ ] Verify signing config in \`androidApp/build.gradle.kts\` is correct
- [ ] Create CI workflow for release builds (manual trigger, requires secrets)
- [ ] Configure GitHub Actions secrets: KEYSTORE_FILE (base64), KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
- [ ] Test release build in CI: \`./gradlew assembleRelease bundleRelease\`
- [ ] Verify release APK is signed: \`jarsigner -verify -verbose app-release.apk\`
- [ ] Document Play Store upload process
- [ ] Create release checklist (version bump, changelog, signing, upload)
- [ ] Test end-to-end: build release APK → verify signature → install on device

## Verification Steps
\`\`\`bash
# Verify keystore exists
ls -l /secure/keystore/release.jks

# Build release APK locally
./gradlew assembleRelease

# Verify APK is signed
jarsigner -verify -verbose -certs androidApp/build/outputs/apk/release/androidApp-release.apk
# Expected: jar verified

# Check signature details
keytool -printcert -jarfile androidApp/build/outputs/apk/release/androidApp-release.apk
# Verify: CN=Scanium, correct validity dates

# Trigger CI release build (manual workflow)
gh workflow run android-release-apk.yml

# Download release artifact and verify
gh run download <run-id>
jarsigner -verify androidApp-release.apk
\`\`\`"

echo ""
echo "Creating P0 severity:high issues..."

gh issue create \
  --title "[GO-LIVE][HIGH] Implement backend rate limiting and cost controls" \
  --label "severity:high,epic:backend,area:backend,priority:p0" \
  --body "## Epic
epic:backend

## Priority
**P0** – must be done before any real go-live (cost and abuse prevention)

## Problem Statement
Rate limiting exists per-API-key and per-IP, but NO cost controls or user quotas to prevent:
- Runaway Google Vision API costs (current limit: 60 requests/min/key, no daily cap)
- Runaway OpenAI API costs (current limit: 200 requests/day/device, no spend cap)
- Abuse via rotating IPs or device IDs

**Why it matters:** Single malicious user could generate thousands in API costs. No kill switch to stop runaway usage.

## Evidence
- \`backend/src/config/index.ts:60-61\` - \`rateLimitPerMinute: 60\`, no daily limit for classifier
- \`backend/src/config/index.ts:110\` - \`dailyQuota: 200\` for assistant but stored in-memory (resets on restart)
- \`backend/src/modules/usage/usage-store.ts\` - In-memory usage tracking (not persistent)
- No cost monitoring for Google Vision or OpenAI API spend
- No circuit breaker for total daily spend

## Acceptance Criteria
- [ ] Implement persistent quota tracking (PostgreSQL or Redis)
- [ ] Add daily spend limits per user/API key (e.g., $10/day Google Vision)
- [ ] Add monthly spend limits (e.g., $500/month total budget)
- [ ] Create admin dashboard to view usage and costs per user/API key
- [ ] Configure cost alerts (email/Slack when 50%, 80%, 100% of budget consumed)
- [ ] Implement kill switch to disable expensive APIs when budget exceeded
- [ ] Add \"overage protection\" mode: switch to mock providers when quota exceeded
- [ ] Document quota policies for users (X free requests/day, then pay-as-you-go)
- [ ] Test: Simulate 1000 classification requests → verify quota enforcement
- [ ] Monitor actual costs in Google Cloud Console and OpenAI dashboard

## Verification Steps
\`\`\`bash
# Configure cost limits
export GOOGLE_VISION_DAILY_BUDGET_USD=10
export OPENAI_MONTHLY_BUDGET_USD=500

# Simulate high usage
for i in {1..1000}; do
  curl -X POST http://localhost:8080/v1/classify \\
    -H \"X-API-Key: dev-key\" \\
    -F \"image=@test.jpg\" \\
    -F \"domainPackId=home_resale\"
done

# Verify quota enforcement
curl http://localhost:8080/v1/classify -H \"X-API-Key: dev-key\" -F \"image=@test.jpg\"
# Expected after daily quota: 429 Too Many Requests \"Daily quota exceeded\"

# Check admin dashboard
curl http://localhost:8080/v1/admin/usage -H \"X-Admin-Key: admin-key\" | jq
# Expected: { \"today_cost\": 10.00, \"budget\": 10.00, \"status\": \"quota_exceeded\" }

# Verify alert fired
# Check email/Slack for cost alert notification
\`\`\`"

gh issue create \
  --title "[GO-LIVE][HIGH] Production TLS/SSL configuration and certificate management" \
  --label "severity:high,epic:backend,area:backend,area:network,priority:p0" \
  --body "## Epic
epic:backend

## Priority
**P0** – required before public internet exposure

## Problem Statement
Backend binds to HTTP only (port 8080). No TLS termination configured. Security config enforces HTTPS (\`enforceHttps: true\`) but TLS termination strategy is undocumented.

**Why it matters:** Cannot expose backend to public internet without TLS. eBay OAuth callbacks and API keys would be transmitted in plaintext.

## Evidence
- \`backend/src/main.ts:23-26\` - Listens on port 8080 HTTP only
- \`backend/src/config/index.ts:209\` - \`security.enforceHttps: true\` configured
- \`docs/ARCHITECTURE.md\` - No TLS termination documented
- \`backend/.env.example:8\` - \`PUBLIC_BASE_URL=http://localhost:8080\` (HTTP)

## Acceptance Criteria
- [ ] Choose TLS termination strategy:
  - Option A: Load balancer termination (AWS ALB, GCP Load Balancer, Nginx, Traefik)
  - Option B: Application-level TLS (Fastify with \`https.createServer\`)
- [ ] Configure SSL/TLS certificates:
  - Production: Let's Encrypt via cert-manager (K8s) or managed certificates (Cloud Run)
  - Staging: Let's Encrypt or self-signed
- [ ] Update \`PUBLIC_BASE_URL\` to HTTPS in production config
- [ ] Configure HTTPS redirect (HTTP → HTTPS) in load balancer or app
- [ ] Enable HSTS headers (already configured via Helmet)
- [ ] Test TLS configuration: \`ssl-tester\` or SSL Labs
- [ ] Document certificate renewal process (auto-renewal for Let's Encrypt)
- [ ] Configure certificate expiry monitoring (alert 30 days before expiration)
- [ ] Test eBay OAuth callback with HTTPS URL
- [ ] Verify security headers: HSTS, CSP, X-Frame-Options

## Verification Steps
\`\`\`bash
# Production URL should use HTTPS
curl -I https://api.scanium.com/healthz
# Expected: HTTP/2 200 with HSTS header

# Verify TLS version and cipher
openssl s_client -connect api.scanium.com:443 -tls1_3
# Expected: TLSv1.3, strong cipher suite

# Test SSL configuration
curl https://www.ssllabs.com/ssltest/analyze.html?d=api.scanium.com
# Expected: A+ rating

# Verify HSTS header
curl -I https://api.scanium.com/healthz | grep Strict-Transport-Security
# Expected: max-age=15552000; includeSubDomains

# Test HTTP → HTTPS redirect
curl -I http://api.scanium.com/healthz
# Expected: 301 Moved Permanently, Location: https://api.scanium.com/healthz
\`\`\`"

echo ""
echo "Creating P1 severity:high issues..."

gh issue create \
  --title "[GO-LIVE][HIGH] Backend integration tests and E2E test suite" \
  --label "severity:high,epic:backend,area:backend,area:ci,priority:p1" \
  --body "## Epic
epic:backend

## Priority
**P1** – required shortly after beta/early launch

## Problem Statement
Backend has unit tests (Vitest) but NO integration tests or end-to-end tests. Cannot verify:
- API endpoints work end-to-end (HTTP request → database → response)
- eBay OAuth flow works (redirect → callback → token exchange → database)
- Classification pipeline works (image upload → Google Vision → response)
- Error handling (400, 401, 429, 500 responses)

**Why it matters:** Deployments could break API contracts, OAuth flows, or database interactions without detection.

## Evidence
- \`backend/package.json:21-22\` - Only \`vitest run\` for unit tests
- \`backend/src/**/*.test.ts\` - Unit tests only (mocked dependencies)
- No \`backend/tests/integration/\` or \`backend/tests/e2e/\` directories
- \`.github/workflows/\` - No backend test CI workflow

## Acceptance Criteria
- [ ] Create integration test suite (tests with real Prisma + test database)
- [ ] Create E2E test suite (Supertest or similar for full HTTP flows)
- [ ] Test coverage:
  - [ ] Health endpoints (GET /healthz, /readyz)
  - [ ] eBay OAuth flow (start → callback → status)
  - [ ] Classification (POST /v1/classify with multipart image)
  - [ ] Assistant (POST /v1/assist/chat with context items)
  - [ ] Auth (register → login → protected endpoint → logout)
  - [ ] Rate limiting (exceed limit → 429 response)
  - [ ] Error cases (invalid image, missing API key, DB down)
- [ ] Configure test database (separate from dev database)
- [ ] Add CI workflow: \`.github/workflows/backend-tests.yml\`
- [ ] Run tests on every PR and push to main
- [ ] Achieve 80%+ code coverage
- [ ] Add E2E smoke tests for staging/production deployment verification

## Verification Steps
\`\`\`bash
# Run integration tests locally
npm run test:integration

# Run E2E tests locally
npm run test:e2e

# Verify coverage
npm run test:coverage
# Expected: Statements 80%+, Branches 75%+, Functions 80%+, Lines 80%+

# CI: Push code and verify tests pass
git push origin feature-branch
gh pr checks  # All tests should pass

# Staging deployment verification
npm run test:e2e:staging
# Hits https://staging-api.scanium.com endpoints
\`\`\`"

gh issue create \
  --title "[GO-LIVE][HIGH] Crash reporting and error tracking (Sentry integration)" \
  --label "severity:high,epic:mobile,area:android,area:logging,priority:p1" \
  --body "## Epic
epic:mobile

## Priority
**P1** – required shortly after beta/early launch

## Problem Statement
Android app has Sentry DSN configured in BuildConfig but unclear if Sentry is actually initialized or operational. No error tracking for production crashes.

**Why it matters:** Production crashes would be invisible. Cannot diagnose user-reported issues without crash reports and stack traces.

## Evidence
- Sentry referenced in git commit history (TLS pinning for cloud classifier)
- No \`androidApp/src/main/java/com/scanium/app/SentryInitializer.kt\` or similar
- \`androidApp/build.gradle.kts\` - Needs verification if Sentry dependency exists
- \`docs/DEV_GUIDE.md:182\` mentions crash test triggers in Developer Options but no Sentry integration documented

## Acceptance Criteria
- [ ] Add Sentry Android SDK dependency (\`io.sentry:sentry-android\`)
- [ ] Initialize Sentry in \`MainActivity.onCreate()\` or \`ScaniumApp.onCreate()\`
- [ ] Configure Sentry DSN from BuildConfig (\`BuildConfig.SENTRY_DSN\`)
- [ ] Enable debug vs release environments in Sentry
- [ ] Configure ProGuard/R8 mapping upload (for deobfuscated stack traces)
- [ ] Test crash reporting: Trigger crash → verify appears in Sentry dashboard
- [ ] Configure Sentry tags: app version, build type, device model, Android version
- [ ] Set up Sentry alerts (email/Slack on new crash types)
- [ ] Document Sentry workflow (viewing crashes, assigning, resolving)
- [ ] Add Sentry performance monitoring (optional: track app start time, screen load time)

## Verification Steps
\`\`\`bash
# Build release APK with Sentry enabled
./gradlew assembleRelease

# Install on device
adb install androidApp/build/outputs/apk/release/androidApp-release.apk

# Trigger test crash (Developer Options → Crash Test)
adb shell am start -n com.scanium.app/.MainActivity
# Tap Settings → Developer Options → Send Test Exception to Sentry

# Verify crash appears in Sentry dashboard
# Navigate to sentry.io → Scanium project → Issues
# Expected: New issue \"Test Exception from Developer Options\"

# Verify ProGuard mappings uploaded
# Sentry dashboard → Settings → ProGuard
# Expected: Mapping file for versionCode X uploaded
\`\`\`"

gh issue create \
  --title "[GO-LIVE][HIGH] Backend API documentation (OpenAPI/Swagger)" \
  --label "severity:high,epic:backend,epic:docs,area:backend,area:docs,priority:p1" \
  --body "## Epic
epic:backend, epic:docs

## Priority
**P1** – required shortly after beta (for third-party integrations)

## Problem Statement
Backend API endpoints exist (\`/auth/ebay/*\`, \`/v1/classify\`, \`/v1/assist/chat\`) but NO API documentation. Mobile developers and future integrators must read source code to understand contracts.

**Why it matters:** Cannot onboard new developers or support third-party integrations without API docs.

## Evidence
- \`backend/src/app.ts:103-120\` - Root endpoint lists routes but no schema
- No \`backend/docs/api/\` or OpenAPI spec file
- No Swagger UI or Redoc deployment
- \`docs/ARCHITECTURE.md:302-307\` - Endpoints listed but not documented

## Acceptance Criteria
- [ ] Generate OpenAPI 3.0 specification from Zod schemas
- [ ] Use \`@fastify/swagger\` or \`fastify-zod-openapi\` plugin
- [ ] Document all endpoints:
  - [ ] Health: GET /healthz, GET /readyz
  - [ ] eBay Auth: POST /auth/ebay/start, GET /auth/ebay/callback, GET /auth/ebay/status
  - [ ] Classification: POST /v1/classify
  - [ ] Assistant: POST /v1/assist/chat
  - [ ] Config: GET /v1/config
  - [ ] Billing: POST /v1/billing/verify
  - [ ] Admin: GET /v1/admin/usage
- [ ] Include request/response schemas, auth requirements, rate limits, error codes
- [ ] Deploy Swagger UI at \`/docs\` endpoint (disabled in production, enabled in dev/staging)
- [ ] Export OpenAPI spec to \`backend/docs/openapi.yaml\`
- [ ] Create API changelog (version, date, breaking changes)
- [ ] Document authentication (API key in X-API-Key header, JWT in Authorization)

## Verification Steps
\`\`\`bash
# Start dev server
npm run dev

# Access Swagger UI
open http://localhost:8080/docs

# Verify all endpoints documented
# Try \"POST /v1/classify\" endpoint in Swagger UI with example image

# Export OpenAPI spec
curl http://localhost:8080/docs/json > backend/docs/openapi.json

# Validate spec
npx @redocly/cli lint backend/docs/openapi.json
# Expected: No errors

# Generate API client (example)
npx @openapitools/openapi-generator-cli generate \\
  -i backend/docs/openapi.json \\
  -g typescript-fetch \\
  -o generated/api-client
\`\`\`"

echo ""
echo "Creating P1 severity:medium issues..."

gh issue create \
  --title "[GO-LIVE][MEDIUM] Feature flags system for gradual rollout" \
  --label "severity:medium,epic:mobile,epic:backend,area:android,area:backend,priority:p1" \
  --body "## Epic
epic:mobile, epic:backend

## Priority
**P1** – required for safe feature rollouts

## Problem Statement
No feature flag system exists. All features are code-driven (hard-coded enabled/disabled). Cannot:
- Gradually roll out new features (e.g., 10% → 50% → 100% of users)
- Kill switch for problematic features without app update
- A/B test features
- Enable features for beta users only

**Why it matters:** Risky features (e.g., new cloud classifier, AI assistant changes) require controlled rollout. Cannot disable without emergency app update.

## Evidence
- \`docs/PRODUCT.md:20-22\` - \"No other runtime feature-flag system is present; changes are code-driven\"
- \`backend/src/modules/config/config.routes.ts\` - Remote config endpoint exists but not used
- No LaunchDarkly, Firebase Remote Config, or custom feature flag service

## Acceptance Criteria
- [ ] Choose feature flag provider: Firebase Remote Config (free), LaunchDarkly, or custom service
- [ ] Implement backend feature flag service (GET /v1/config/features)
- [ ] Return feature flags: \`{ \"assistant_enabled\": true, \"cloud_classifier_v2\": false }\`
- [ ] Add user targeting: enable for % of users, specific user IDs, or beta testers
- [ ] Implement mobile client: fetch flags on app start, cache for 24h
- [ ] Add flag evaluation: \`if (featureFlags.isEnabled(\"assistant_enabled\")) { ... }\`
- [ ] Document flag lifecycle: create → test → rollout → remove
- [ ] Add flag override in Developer Options (force enable/disable for testing)
- [ ] Create admin dashboard to manage flags (enable, disable, set rollout %)
- [ ] Test gradual rollout: enable for 10% → verify only 10% see feature

## Verification Steps
\`\`\`bash
# Backend: Set flag to 10% rollout
curl -X POST http://localhost:8080/v1/admin/features \\
  -H \"X-Admin-Key: admin-key\" \\
  -d '{\"flag\":\"cloud_classifier_v2\",\"enabled\":true,\"rollout_percent\":10}'

# Mobile: Fetch flags
# App fetches on startup
# 10 requests to /v1/config/features → ~1 should get cloud_classifier_v2=true

# Verify in logs
adb logcat | grep FeatureFlags
# Expected: 10% of app starts see cloud_classifier_v2=true

# Developer Options: Force enable flag
# Settings → Developer Options → Feature Flags → Enable cloud_classifier_v2
# Verify feature is enabled regardless of rollout %

# Kill switch: Disable problematic feature
curl -X POST http://localhost:8080/v1/admin/features \\
  -d '{\"flag\":\"buggy_feature\",\"enabled\":false}'
# All users should see feature disabled within 24h (or on next app restart)
\`\`\`"

gh issue create \
  --title "[GO-LIVE][MEDIUM] Backend CI/CD pipeline for automated deployments" \
  --label "severity:medium,epic:backend,area:backend,area:ci,priority:p1" \
  --body "## Epic
epic:backend

## Priority
**P1** – required for reliable deployments

## Problem Statement
Backend has NO CI/CD pipeline. Deployments are manual. No automated testing, building, or deployment to staging/production.

**Why it matters:** Manual deployments = human error, forgotten migrations, untested code reaching production.

## Evidence
- \`.github/workflows/\` - Only Android workflows exist
- No backend test, build, or deploy workflows
- \`backend/package.json\` - \`build\` and \`start\` scripts exist but not used in CI
- No deployment documentation

## Acceptance Criteria
- [ ] Create \`.github/workflows/backend-ci.yml\`:
  - Run on PR: typecheck, lint, unit tests, integration tests
  - Fail PR if tests fail or coverage < 80%
- [ ] Create \`.github/workflows/backend-deploy-staging.yml\`:
  - Trigger: push to main
  - Build Docker image, push to registry (GCR, ECR, Docker Hub)
  - Deploy to staging environment (K8s, Cloud Run)
  - Run smoke tests against staging API
  - Notify Slack on success/failure
- [ ] Create \`.github/workflows/backend-deploy-production.yml\`:
  - Trigger: manual approval or git tag (e.g., v1.0.0)
  - Build production Docker image
  - Deploy to production environment
  - Run E2E smoke tests
  - Rollback on failure
- [ ] Configure GitHub environments (staging, production) with required approvals
- [ ] Add deployment secrets: DOCKER_REGISTRY_TOKEN, K8S_CONFIG, etc.
- [ ] Document deployment process (PR → staging → approval → production)
- [ ] Add rollback procedure (revert deployment, restore from backup)

## Verification Steps
\`\`\`bash
# Open PR
gh pr create --title \"Test backend CI\"

# Verify CI workflow runs
gh pr checks
# Expected: backend-ci ✓ All tests passed

# Merge PR
gh pr merge

# Verify staging deployment
gh run list --workflow=backend-deploy-staging.yml
gh run view <run-id>
# Expected: ✓ Deploy to staging, ✓ Smoke tests passed

# Verify staging API
curl https://staging-api.scanium.com/healthz
# Expected: 200 OK

# Trigger production deployment (manual approval)
gh workflow run backend-deploy-production.yml --ref main

# Approve deployment
gh run list --workflow=backend-deploy-production.yml
gh run view <run-id>
# Approve in GitHub UI

# Verify production API
curl https://api.scanium.com/healthz
# Expected: 200 OK
\`\`\`"

gh issue create \
  --title "[GO-LIVE][MEDIUM] Privacy policy and terms of service" \
  --label "severity:medium,epic:docs,area:privacy,area:docs,priority:p1" \
  --body "## Epic
epic:docs

## Priority
**P1** – required before Play Store submission

## Problem Statement
No privacy policy or terms of service exist. Required by:
- Google Play Store (mandatory for all apps)
- GDPR (EU users)
- CCPA (California users)
- eBay API terms of service

**From Security Assessment (SEC-012):** \"No privacy policy (required for Google Play)\"

**Why it matters:** Cannot publish to Play Store without privacy policy. Legal liability for GDPR/CCPA violations.

## Evidence
- \`docs/_archive/2025-12/security/SECURITY_RISK_ASSESSMENT.md:1284\` - SEC-012 (P2)
- No \`docs/PRIVACY_POLICY.md\` or \`docs/TERMS_OF_SERVICE.md\`
- No privacy policy URL in app Settings or About screen

## Acceptance Criteria
- [ ] Create privacy policy covering:
  - [ ] Data collection (camera frames, ML detections, eBay tokens, usage metrics)
  - [ ] Data usage (on-device ML, optional cloud classification, eBay API)
  - [ ] Data retention (session-based, cloud images discarded immediately)
  - [ ] Third-party sharing (Google Vision, OpenAI, eBay)
  - [ ] User rights (access, deletion, export under GDPR)
  - [ ] Contact information (privacy@scanium.com)
- [ ] Create terms of service covering:
  - [ ] Acceptable use (no automated scraping, no abuse)
  - [ ] eBay API compliance (seller responsibilities)
  - [ ] Disclaimers (accuracy of ML classifications)
  - [ ] Termination policy
- [ ] Host privacy policy at https://scanium.com/privacy (or in-app web view)
- [ ] Add privacy policy link to:
  - [ ] App Settings → About → Privacy Policy
  - [ ] First-time user onboarding (consent flow)
  - [ ] Play Store listing
- [ ] Add terms of service link to Settings → About → Terms
- [ ] Get legal review (GDPR/CCPA compliance)
- [ ] Implement consent flow for cloud features (opt-in for Google Vision, eBay)

## Verification Steps
\`\`\`bash
# Verify privacy policy URL
curl https://scanium.com/privacy
# Expected: 200 OK, privacy policy HTML

# Verify app links to privacy policy
# Settings → About → Privacy Policy
# Tap → Opens web view or browser with privacy policy

# Verify first-time consent
# Uninstall app, reinstall
# On first launch → \"Allow cloud classification?\" with link to privacy policy
# User must consent before cloud features enabled

# Play Store submission
# Google Play Console → Store Listing → Privacy Policy
# Enter: https://scanium.com/privacy
# Expected: No validation errors
\`\`\`"

echo ""
echo "Creating P2 severity:medium issues..."

gh issue create \
  --title "[GO-LIVE][MEDIUM] Production log retention and archival policy" \
  --label "severity:medium,epic:observability,area:backend,area:logging,priority:p2" \
  --body "## Epic
epic:observability

## Priority
**P2** – scale-up / future-proofing

## Problem Statement
Current log retention: 14 days (Loki), 7 days (Tempo), 15 days (Mimir). For production:
- Compliance may require 30-90 day retention (GDPR, SOC 2)
- Cost optimization needed (compress old logs, archive to S3)
- No log archival strategy for long-term storage

**Why it matters:** May violate compliance requirements. Cannot investigate incidents older than 14 days.

## Evidence
- \`docs/ARCHITECTURE.md:69-71\` - Dev retention: 14d logs, 7d traces, 15d metrics
- \`monitoring/loki/loki.yaml\` - retention_period: 336h (14 days)
- No S3/GCS archival configured

## Acceptance Criteria
- [ ] Define retention policy:
  - Hot storage (queryable): 30 days
  - Warm storage (archived, slower queries): 90 days
  - Cold storage (compliance archive): 1 year
- [ ] Configure Loki to archive logs to S3/GCS after 30 days
- [ ] Update Tempo retention to 30 days
- [ ] Update Mimir retention to 90 days
- [ ] Implement log compression (gzip or zstd)
- [ ] Document archival process (how to query old logs from S3)
- [ ] Configure lifecycle policies (delete logs > 1 year automatically)
- [ ] Estimate storage costs (X GB/day * retention days)
- [ ] Add monitoring for storage usage (alert if approaching quota)
- [ ] Test log restoration from archive (query logs from 60 days ago)

## Verification Steps
\`\`\`bash
# Verify retention config
kubectl exec -it loki-0 -n scanium-observability -- cat /etc/loki/loki.yaml | grep retention
# Expected: retention_period: 720h (30 days)

# Verify S3 archival
aws s3 ls s3://scanium-logs/archive/2025/01/
# Expected: Compressed log files older than 30 days

# Query archived logs (via Loki)
curl -G 'http://localhost:3100/loki/api/v1/query_range' \\
  --data-urlencode 'query={app=\"scanium-backend\"}' \\
  --data-urlencode 'start=2024-11-01T00:00:00Z' \\
  --data-urlencode 'end=2024-11-02T00:00:00Z'
# Expected: Results from archived logs (slower query)

# Check storage costs
aws s3 ls s3://scanium-logs/ --recursive --summarize
# Expected: Total size, estimate monthly cost
\`\`\`"

gh issue create \
  --title "[GO-LIVE][MEDIUM] Android performance monitoring (baseline metrics)" \
  --label "severity:medium,epic:mobile,area:android,area:logging,priority:p2" \
  --body "## Epic
epic:mobile

## Priority
**P2** – scale-up / performance tracking

## Problem Statement
No performance monitoring for Android app. Cannot measure:
- App start time (cold start, warm start)
- Frame rate / jank (screen stutters)
- Memory usage (detect leaks)
- Network latency (API calls)
- ML inference time (object detection)

**Why it matters:** Cannot detect performance regressions or optimize user experience.

## Evidence
- \`docs/ARCHITECTURE.md\` - No performance monitoring documented
- No Firebase Performance Monitoring or custom metrics
- \`androidApp/src/main/java/com/scanium/app/ml/DetectionLogger.kt\` - Logs detection events but no performance metrics

## Acceptance Criteria
- [ ] Add Firebase Performance Monitoring SDK (or custom metrics)
- [ ] Track app start time (cold and warm start)
- [ ] Track screen load time (CameraScreen, ItemsListScreen)
- [ ] Track ML inference time (object detection, barcode scanning)
- [ ] Track API latency (classification, assistant requests)
- [ ] Monitor frame rate (target: 60fps, alert if < 50fps)
- [ ] Monitor memory usage (alert if > 500MB on mid-tier devices)
- [ ] Set performance baselines (e.g., app start < 2s, inference < 300ms)
- [ ] Configure alerts for performance regressions
- [ ] Create performance dashboard (Firebase Console or Grafana)

## Verification Steps
\`\`\`bash
# Build release APK with performance monitoring
./gradlew assembleRelease

# Install on device
adb install androidApp/build/outputs/apk/release/androidApp-release.apk

# Launch app and measure start time
adb shell am start -W -n com.scanium.app/.MainActivity
# Expected: TotalTime < 2000ms

# View performance metrics
# Firebase Console → Performance → Scanium → App Start
# Expected: p95 cold start < 2s, p95 warm start < 1s

# Check frame rate
adb shell dumpsys gfxinfo com.scanium.app
# Expected: 95th percentile frame time < 16.6ms (60fps)

# Verify alerts
# Trigger performance regression (add 5s sleep in CameraScreen)
# Rebuild and deploy
# Expected: Alert fires \"App start time p95 > 3s (threshold: 2s)\"
\`\`\`"

gh issue create \
  --title "[GO-LIVE][MEDIUM] Remaining security issues from assessment (7 issues)" \
  --label "severity:medium,epic:security,area:android,area:backend,priority:p2" \
  --body "## Epic
epic:security

## Priority
**P2** – medium priority security hardening

## Problem Statement
Security Risk Assessment completed in Dec 2025 identified 18 issues. 9 fixed, 7 remain:
- **SEC-014** (P1): No root/tamper detection
- **SEC-018** (P1): No image encryption for captured images
- **SEC-009** (P2): Certificate pinning guidance needed
- **SEC-004** (P2): OAuth/auth implementation guidance
- **SEC-020** (P2): Cryptography implementation guidance
- **SEC-012** (P2): Privacy policy (separate issue created)
- **SEC-001** (P3): API key storage guidance

**Why it matters:** Remaining P1 issues leave app vulnerable to tampering and data leakage. P2 issues are documentation gaps that could lead to insecure implementations.

## Evidence
- \`docs/_archive/2025-12/security/SECURITY_RISK_ASSESSMENT.md:1773-1789\` - Remediation status
- \`docs/_archive/2025-12/security/SECURITY_RISK_ASSESSMENT.md:1263-1286\` - Prioritized risk backlog

## Acceptance Criteria
**SEC-014 (P1): Root/Tamper Detection**
- [ ] Integrate RootBeer library or custom root detection
- [ ] Show warning dialog on rooted devices (\"Running on rooted device may compromise security\")
- [ ] Optional: Disable sensitive features (eBay OAuth) on rooted devices
- [ ] Detect Xposed/Frida hooking frameworks
- [ ] Test on rooted device (Magisk) and verify detection

**SEC-018 (P1): Image Encryption**
- [ ] Use Jetpack Security EncryptedFile for captured images
- [ ] Encrypt images at-rest in cache directory (AES-256-GCM)
- [ ] Decrypt on-demand when displaying in UI
- [ ] Test: extract encrypted image from cache → verify not readable as JPEG

**SEC-009 (P2): Certificate Pinning Guidance**
- [ ] Document that cert pinning is NOT recommended per Android guidance (brittle, rotation issues)
- [ ] Document exceptions: Only use if you control backend AND have backup pins AND pin rotation strategy
- [ ] Add warning in docs: Incorrect pinning = app broken after cert rotation

**SEC-004 (P2): OAuth/Auth Guidance**
- [ ] Document OAuth 2.0 + PKCE strategy for future backend user auth
- [ ] Document token storage (Android Keystore with hardware-backing if available)
- [ ] Document session management (JWT vs sessions, refresh token rotation)
- [ ] Create \`docs/security/AUTH_IMPLEMENTATION_GUIDE.md\`

**SEC-020 (P2): Cryptography Guidance**
- [ ] Document use of Jetpack Security library for all crypto needs
- [ ] Document key management (MasterKey with AES256_GCM scheme)
- [ ] Document NEVER hardcode keys, NEVER use ECB mode, NEVER static IVs
- [ ] Create \`docs/security/CRYPTO_IMPLEMENTATION_GUIDE.md\`

**SEC-001 (P3): API Key Storage Guidance**
- [ ] Document secure API key storage (EncryptedSharedPreferences)
- [ ] Update \`docs/DEV_GUIDE.md\` with cloud classifier API key security best practices

## Verification Steps
\`\`\`bash
# SEC-014: Test root detection
# Install on rooted device (Magisk)
adb install app-release.apk
adb shell am start -n com.scanium.app/.MainActivity
# Expected: Warning dialog \"Device appears to be rooted. This may compromise security.\"

# SEC-018: Test image encryption
adb pull /data/data/com.scanium.app/cache/captured_image_123.enc
file captured_image_123.enc
# Expected: Binary data, NOT \"JPEG image data\"
\`\`\`"

echo ""
echo "Creating P2 severity:low/future issues..."

gh issue create \
  --title "[GO-LIVE][LOW] Incident response runbook and postmortem process" \
  --label "severity:low,epic:observability,epic:docs,area:docs,priority:p2" \
  --body "## Epic
epic:observability, epic:docs

## Priority
**P2** – operational maturity

## Problem Statement
No incident response process documented. When production outage occurs:
- No clear triage process
- No communication plan (status page, user notifications)
- No postmortem template or review process

**Why it matters:** Chaotic incident response leads to longer outages and repeated mistakes.

## Evidence
- \`docs/\` - No incident response runbook
- No postmortem template or examples

## Acceptance Criteria
- [ ] Create incident response runbook (\`docs/operations/INCIDENT_RESPONSE.md\`)
  - Severity levels (P0 = full outage, P1 = degraded, P2 = minor)
  - Triage checklist (check health endpoints, review alerts, check logs)
  - Escalation policy (when to page on-call engineer)
  - Communication plan (update status page, notify users)
- [ ] Create common incident playbooks:
  - Database down: check connection, restart container, restore from backup
  - API high latency: check resource usage, scale up, check slow queries
  - Out of memory: check memory leaks, increase limits, restart
- [ ] Create postmortem template (\`docs/operations/POSTMORTEM_TEMPLATE.md\`)
  - Timeline, root cause, impact, resolution, action items
- [ ] Document postmortem process (review within 48h, publish to team)
- [ ] Create status page (statuspage.io or custom) for user-facing outages
- [ ] Test incident response: simulate outage → follow runbook → write postmortem

## Verification Steps
\`\`\`bash
# Simulate incident: Scale backend to 0 replicas
kubectl scale deployment scanium-backend --replicas=0 -n scanium-prod

# Follow runbook
# 1. Alert fires: \"API Down\"
# 2. On-call engineer pages
# 3. Triage: Check health endpoint (fails), review logs (no pods running)
# 4. Resolution: Scale back to 3 replicas
kubectl scale deployment scanium-backend --replicas=3 -n scanium-prod

# 5. Communication: Update status page \"Investigating API outage\"
# 6. Resolution: Update status page \"Resolved\"

# 7. Postmortem (within 48h):
# Write postmortem using template
# Review with team
# Create action items (add pod disruption budget, improve monitoring)
\`\`\`"

gh issue create \
  --title "[GO-LIVE][LOW] iOS app development (KMP shared brain)" \
  --label "severity:low,epic:scale-ios,area:backend,priority:p2" \
  --body "## Epic
epic:scale-ios

## Priority
**P2** – future platform expansion

## Problem Statement
iOS app does not exist. KMP preparation is complete (shared/core-* modules are Android-free), but no iOS implementation started.

**Why it matters:** Limited to Android users only. Cannot reach iOS market (significant user base).

## Evidence
- \`docs/CODEX_CONTEXT.md:120-153\` - \"KMP/iOS Porting Status\" shows Phase 1 complete, Phase 2 not started
- \`docs/_archive/2025-12/kmp-migration/PLAN.md\` - KMP migration plan exists but iOS not implemented
- \`docs/_archive/2025-12/parity/\` - iOS parity plans from Dec 2025
- \`shared/core-models\`, \`shared/core-tracking\` - Android-free but not converted to KMP yet

## Acceptance Criteria
**Phase 1: Convert shared modules to KMP**
- [ ] Convert \`shared/core-models\` to KMP with commonMain/androidMain/iosMain
- [ ] Convert \`shared/core-tracking\` to KMP
- [ ] Implement platform actuals:
  - Logger: IOSLogger wrapping NSLog/os_log
  - ImageRef: iOS UIImage implementation
- [ ] Verify Android builds still pass after KMP conversion

**Phase 2: iOS App Shell**
- [ ] Create \`:iosApp\` module with SwiftUI
- [ ] Implement iOS camera provider (AVFoundation)
- [ ] Implement iOS ML provider (Vision framework or Core ML)
- [ ] Wire iOS app to shared tracking/aggregation logic
- [ ] Build iOS app and verify object detection works

**Phase 3: Feature Parity**
- [ ] Implement iOS UI (SwiftUI equivalent of Compose screens)
- [ ] Implement iOS eBay OAuth flow
- [ ] Implement iOS cloud classification
- [ ] Achieve feature parity with Android (see \`docs/_archive/2025-12/parity/GAP_MATRIX.md\`)

**Phase 4: Release**
- [ ] iOS CI pipeline (fastlane, TestFlight)
- [ ] App Store submission
- [ ] Beta testing
- [ ] Public release

## Verification Steps
\`\`\`bash
# Phase 1: Verify KMP conversion
./gradlew :shared:core-models:build
./gradlew :shared:core-tracking:build
./gradlew :androidApp:assembleDebug  # Should still work

# Phase 2: Build iOS app
cd iosApp
xcodebuild -scheme ScaniumApp -sdk iphonesimulator build
# Run on iOS simulator and verify object detection

# Phase 3: Feature parity check
# Compare Android and iOS side-by-side
# Verify: Camera, detection, tracking, eBay OAuth, cloud classification all work
\`\`\`"

gh issue create \
  --title "[GO-LIVE][LOW] End-to-end testing framework (mobile + backend)" \
  --label "severity:low,epic:mobile,epic:backend,area:ci,priority:p2" \
  --body "## Epic
epic:mobile, epic:backend

## Priority
**P2** – test automation for confidence

## Problem Statement
No end-to-end tests exist. Cannot test full user flows:
- Mobile app → Backend API → Database → Response
- Camera scan → ML detection → Cloud classification → Item saved
- eBay OAuth: App → Backend → eBay → Callback → Token stored

**Why it matters:** Integration bugs slip through (e.g., API contract mismatch, OAuth callback broken).

## Evidence
- \`docs/CI_CD.md\` - Only unit tests and Android Debug APK workflow
- No E2E test framework (Detox, Maestro, Appium)
- No \`androidApp/src/androidTest/e2e/\` directory

## Acceptance Criteria
- [ ] Choose E2E framework: Maestro (recommended), Detox, or Appium
- [ ] Set up E2E test environment:
  - Staging backend with test database
  - Test eBay sandbox account
  - Mock Google Vision (deterministic results)
- [ ] Create E2E test scenarios:
  - [ ] Full flow: Launch app → Scan object → Cloud classify → View in items list
  - [ ] eBay OAuth: Start OAuth → Redirect → Callback → View connection status
  - [ ] Export: Select items → Export CSV → Verify file contents
  - [ ] Settings: Change classification mode → Verify backend receives correct mode
- [ ] Run E2E tests in CI on every release candidate
- [ ] Generate E2E test reports (screenshots, videos on failure)
- [ ] Document E2E test writing guide

## Verification Steps
\`\`\`bash
# Run E2E tests locally
npm run test:e2e:android

# Output:
# ✓ Scan object and view in list (45s)
# ✓ eBay OAuth flow (30s)
# ✓ Export items to CSV (20s)
# 3 tests passed

# CI: Trigger E2E tests
gh workflow run e2e-tests.yml --ref main

# View results
gh run view <run-id>
# Download failure screenshots/videos if tests fail
\`\`\`"

echo ""
echo "✅ All issues created!"
echo ""
echo "Summary:"
echo "- P0 severity:critical: 6 issues (backend deployment, auth, observability, backups, environments, signing)"
echo "- P0 severity:high: 2 issues (rate limits, TLS)"
echo "- P1 severity:high: 3 issues (backend tests, crash reporting, API docs)"
echo "- P1 severity:medium: 3 issues (feature flags, CI/CD, privacy policy)"
echo "- P2 severity:medium: 3 issues (log retention, performance monitoring, security)"
echo "- P2 severity:low: 3 issues (incident response, iOS, E2E tests)"
echo ""
echo "Total: 20 issues created"
echo ""
echo "View issues: gh issue list --label priority:p0"
echo "Or visit: https://github.com/$(gh repo view --json nameWithOwner -q .nameWithOwner)/issues"
