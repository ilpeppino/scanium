***REMOVED*** Scanium Go-Live Backlog Summary

**Generated:** 2025-12-26
**Purpose:** Prepare Scanium for production go-live based on repository state, documentation, and
industry best practices (OWASP Mobile Top 10, MASVS, NIST SP 800-163r1)

---

***REMOVED******REMOVED*** Executive Summary

Scanium is a **full-stack mobile application** with:

- ✅ **Android app**: Multi-module architecture, on-device ML Kit, cloud classification (optional),
  KMP-ready shared modules
- ✅ **Backend API**: Node.js 20 + TypeScript + Fastify + Prisma + PostgreSQL, comprehensive security
  middleware
- ✅ **Observability**: LGTM stack (Loki, Grafana, Tempo, Mimir, Alloy) with OpenTelemetry
  instrumentation

**Current Maturity:**

- **Mobile**: Production-ready for Android with 9/18 security issues fixed, code coverage 75-85%,
  accessibility compliant
- **Backend**: Feature-complete but **DEV-ONLY** - No production deployment, no auth, no backups, no
  staging environment
- **Observability**: Dev-only monitoring stack with anonymous access and local storage

**Go-Live Readiness:** **NOT READY**

**Blockers (P0 Critical):**

1. Backend cannot be deployed to production (no deployment config)
2. Backend API is unauthenticated (anyone can access user data)
3. No production observability (alerting, SLOs)
4. PostgreSQL has no backup strategy (data loss risk)
5. No environment separation (dev/staging/prod)
6. Android release signing not verified (cannot publish)
7. No rate limiting cost controls (runaway API costs)
8. No TLS/SSL configuration (backend HTTP only)

---

***REMOVED******REMOVED*** Total Issues Created

**20 GitHub issues** created in priority order:

| Priority | Severity | Count | Description                              |
|----------|----------|-------|------------------------------------------|
| **P0**   | Critical | 6     | Must be done before any real go-live     |
| **P0**   | High     | 2     | Critical for production safety           |
| **P1**   | High     | 3     | Required shortly after beta/early launch |
| **P1**   | Medium   | 3     | Important for safe rollouts              |
| **P2**   | Medium   | 3     | Scale-up and operational maturity        |
| **P2**   | Low      | 3     | Future-proofing and platform expansion   |

***REMOVED******REMOVED******REMOVED*** Issues by Epic

| Epic                   | Issues | Key Focus                                                       |
|------------------------|--------|-----------------------------------------------------------------|
| **epic:backend**       | 10     | Deployment, auth, tests, CI/CD, rate limits, TLS                |
| **epic:mobile**        | 5      | Signing, crash reporting, performance, feature flags, E2E tests |
| **epic:observability** | 4      | Production alerting, SLOs, log retention, incident response     |
| **epic:security**      | 2      | Remaining security assessment items                             |
| **epic:docs**          | 2      | Privacy policy, incident runbooks                               |
| **epic:scale-ios**     | 1      | iOS app development (KMP)                                       |

---

***REMOVED******REMOVED*** Top 10 Go-Live Risks

Ordered by **exploitability × impact × urgency**:

***REMOVED******REMOVED******REMOVED*** 1. 🔴 **Unauthenticated Backend API** (Issue ***REMOVED***238)

- **Risk:** Anyone can access user eBay OAuth tokens, listings, and personal data from database
- **Impact:** Data breach, GDPR violation, loss of user trust
- **Exploitability:** Trivial (no authentication required)
- **Blocker:** P0 - Must fix before go-live
- **Evidence:** `backend/src/app.ts` - Routes registered without auth middleware

***REMOVED******REMOVED******REMOVED*** 2. 🔴 **No PostgreSQL Backups** (Issue ***REMOVED***240)

- **Risk:** Database corruption or container deletion = permanent data loss (all users, eBay
  connections, listings)
- **Impact:** Catastrophic data loss, business-ending event
- **Exploitability:** High (accidental or malicious deletion)
- **Blocker:** P0 - Must fix before go-live
- **Evidence:** No `pg_dump` jobs, no WAL archiving, no replication

***REMOVED******REMOVED******REMOVED*** 3. 🔴 **No Production Deployment Configuration** (Issue ***REMOVED***237)

- **Risk:** Backend cannot be deployed to production at all
- **Impact:** Cannot launch backend services
- **Exploitability:** N/A (blocking prerequisite)
- **Blocker:** P0 - Must fix before go-live
- **Evidence:** No Dockerfile, no K8s manifests, dev-only setup

***REMOVED******REMOVED******REMOVED*** 4. 🔴 **No Production Observability** (Issue ***REMOVED***239)

- **Risk:** Production outages go undetected, no way to measure reliability or respond to incidents
- **Impact:** Prolonged downtime, user attrition, missed SLAs
- **Exploitability:** N/A (operational risk)
- **Blocker:** P0 - Must fix before go-live
- **Evidence:** Anonymous Grafana, no alerting rules, no SLOs

***REMOVED******REMOVED******REMOVED*** 5. 🔴 **No Environment Separation** (Issue ***REMOVED***241)

- **Risk:** Testing changes directly in production, no staging to catch bugs
- **Impact:** Production outages from untested code
- **Exploitability:** High (human error)
- **Blocker:** P0 - Must fix before go-live
- **Evidence:** Single `.env` file, no staging deployment

***REMOVED******REMOVED******REMOVED*** 6. 🟠 **No Rate Limiting Cost Controls** (Issue ***REMOVED***243)

- **Risk:** Single malicious user generates $1000s in Google Vision/OpenAI API costs
- **Impact:** Financial loss, service degradation
- **Exploitability:** Medium (requires bypassing IP/key limits via rotation)
- **Blocker:** P0 - Must fix before accepting public traffic
- **Evidence:** `rateLimitPerMinute: 60` but no daily spend cap

***REMOVED******REMOVED******REMOVED*** 7. 🟠 **Backend HTTP Only (No TLS)** (Issue ***REMOVED***244)

- **Risk:** eBay OAuth callbacks and API keys transmitted in plaintext
- **Impact:** Credential theft, man-in-the-middle attacks
- **Exploitability:** Medium (requires network access)
- **Blocker:** P0 - Must fix before public internet exposure
- **Evidence:** `backend/src/main.ts` - Port 8080 HTTP, no TLS config

***REMOVED******REMOVED******REMOVED*** 8. 🟠 **Android Release Signing Not Verified** (Issue ***REMOVED***242)

- **Risk:** Cannot publish to Play Store, risk of losing keystore
- **Impact:** Cannot distribute signed APKs, Play Store rejection
- **Exploitability:** N/A (publishing blocker)
- **Blocker:** P0 - Required before any release build
- **Evidence:** No CI release workflow, signing config not enforced

***REMOVED******REMOVED******REMOVED*** 9. 🟡 **No Crash Reporting** (Issue ***REMOVED***246)

- **Risk:** Production crashes invisible, cannot diagnose user-reported issues
- **Impact:** Poor user experience, unresolved bugs
- **Exploitability:** N/A (operational gap)
- **Blocker:** P1 - Required shortly after beta launch
- **Evidence:** Sentry DSN exists but integration unclear

***REMOVED******REMOVED******REMOVED*** 10. 🟡 **No Backend Integration/E2E Tests** (Issue ***REMOVED***245)

- **Risk:** Deployments break API contracts, OAuth flows, or database interactions without detection
- **Impact:** Production bugs, rollbacks, user-facing errors
- **Exploitability:** High (deployment errors)
- **Blocker:** P1 - Required for safe deployments
- **Evidence:** Unit tests only, no integration or E2E tests

---

***REMOVED******REMOVED*** What Blocks Go-Live Right Now

***REMOVED******REMOVED******REMOVED*** ❌ **Absolute Blockers (P0 - Cannot go live without these)**

1. **Backend Deployment** (Issue ***REMOVED***237)
    - Cannot deploy backend to production without Dockerfile, K8s manifests, environment config
    - **ETA:** 2-3 days (Dockerfile + K8s + staging deploy)

2. **Backend Authentication** (Issue ***REMOVED***238)
    - API is publicly accessible, user data unprotected
    - **ETA:** 3-5 days (auth middleware + JWT/sessions + mobile integration)

3. **Production Observability** (Issue ***REMOVED***239)
    - No alerting, no SLOs, no incident response
    - **ETA:** 2-3 days (alert rules + PagerDuty + SLO dashboard)

4. **PostgreSQL Backups** (Issue ***REMOVED***240)
    - No backup strategy = data loss risk
    - **ETA:** 1-2 days (automated backups + test restore)

5. **Environment Separation** (Issue ***REMOVED***241)
    - No staging to test changes
    - **ETA:** 2-3 days (staging deploy + CI integration)

6. **Android Release Signing** (Issue ***REMOVED***242)
    - Cannot publish to Play Store
    - **ETA:** 4-8 hours (verify keystore + CI workflow)

7. **Rate Limiting & Cost Controls** (Issue ***REMOVED***243)
    - Runaway API costs risk
    - **ETA:** 1-2 days (persistent quotas + cost alerts + kill switch)

8. **TLS/SSL Configuration** (Issue ***REMOVED***244)
    - Backend HTTP only, cannot expose to internet
    - **ETA:** 1 day (TLS termination + Let's Encrypt)

**Total Estimated Time to Unblock:** **2-3 weeks** (parallelizable)

---

***REMOVED******REMOVED*** Recommended Execution Order

***REMOVED******REMOVED******REMOVED*** Week 1: Critical Infrastructure (P0 - 8 issues)

**Focus:** Make backend deployable and secure

| Day | Issue | Epic          | Area         | Effort  | Owner   |
|-----|-------|---------------|--------------|---------|---------|
| Mon | ***REMOVED***237  | Backend       | Deployment   | 2 days  | DevOps  |
| Mon | ***REMOVED***241  | Backend       | Environments | 2 days  | DevOps  |
| Mon | ***REMOVED***242  | Mobile        | Signing      | 4 hours | Mobile  |
| Tue | ***REMOVED***240  | Backend       | Backups      | 1 day   | DevOps  |
| Wed | ***REMOVED***238  | Backend       | Auth         | 3 days  | Backend |
| Wed | ***REMOVED***244  | Backend       | TLS          | 1 day   | DevOps  |
| Thu | ***REMOVED***239  | Observability | Alerting     | 2 days  | SRE     |
| Fri | ***REMOVED***243  | Backend       | Rate Limits  | 1 day   | Backend |

**End of Week 1:**

- ✅ Backend deployable to staging and production
- ✅ PostgreSQL backed up daily
- ✅ TLS configured (HTTPS)
- ✅ Auth protecting user data
- ✅ Alerting and SLOs configured
- ✅ Cost controls in place
- ✅ Android release builds signed

**Risk Level:** MEDIUM-HIGH → **MEDIUM**

---

***REMOVED******REMOVED******REMOVED*** Week 2: Testing & Reliability (P1 - 6 issues)

**Focus:** Automated testing and production reliability

| Day | Issue | Epic           | Area            | Effort  | Owner         |
|-----|-------|----------------|-----------------|---------|---------------|
| Mon | ***REMOVED***245  | Backend        | Tests           | 2 days  | Backend       |
| Tue | ***REMOVED***249  | Backend        | CI/CD           | 2 days  | DevOps        |
| Wed | ***REMOVED***246  | Mobile         | Crash Reporting | 1 day   | Mobile        |
| Thu | ***REMOVED***247  | Backend        | API Docs        | 1 day   | Backend       |
| Fri | ***REMOVED***248  | Mobile/Backend | Feature Flags   | 2 days  | Full-stack    |
| Fri | ***REMOVED***250  | Docs           | Privacy Policy  | 4 hours | Legal/Product |

**End of Week 2:**

- ✅ Backend integration tests passing in CI
- ✅ Automated deployments to staging/production
- ✅ Crash reporting operational (Sentry)
- ✅ API documentation published
- ✅ Feature flags system live
- ✅ Privacy policy published

**Risk Level:** MEDIUM → **MEDIUM-LOW**

---

***REMOVED******REMOVED******REMOVED*** Week 3: Hardening & Scale-Up (P2 - 6 issues)

**Focus:** Operational maturity and future-proofing

| Day | Issue | Epic          | Area                   | Effort   | Owner      |
|-----|-------|---------------|------------------------|----------|------------|
| Mon | ***REMOVED***251  | Observability | Log Retention          | 1 day    | SRE        |
| Tue | ***REMOVED***252  | Mobile        | Performance Monitoring | 1 day    | Mobile     |
| Wed | ***REMOVED***253  | Security      | Remaining Issues       | 2 days   | Security   |
| Thu | ***REMOVED***254  | Observability | Incident Response      | 4 hours  | SRE        |
| Fri | ***REMOVED***255  | Mobile        | iOS (Plan)             | Planning | Leadership |
| Fri | ***REMOVED***256  | Testing       | E2E Tests              | 2 days   | QA         |

**End of Week 3:**

- ✅ Production log retention (30d hot, 90d warm)
- ✅ Performance baselines established
- ✅ 7 remaining security issues addressed
- ✅ Incident response runbook complete
- ✅ iOS roadmap finalized
- ✅ E2E test framework setup

**Risk Level:** MEDIUM-LOW → **LOW**

---

***REMOVED******REMOVED*** Current State Assessment

***REMOVED******REMOVED******REMOVED*** Mobile (Android) - ✅ **Production-Ready**

**Strengths:**

- ✅ Multi-module architecture with KMP-ready shared modules
- ✅ On-device ML Kit detection (objects, barcodes, OCR)
- ✅ Cloud classification via backend (optional, config-driven)
- ✅ Security assessment completed (9/18 issues fixed)
- ✅ Code coverage: 85% shared modules, 75% androidApp
- ✅ WCAG 2.1 accessibility compliance
- ✅ TLS certificate pinning for cloud classifier
- ✅ Network security config (cleartext blocked)
- ✅ Code obfuscation enabled (R8)
- ✅ Debug logging stripped in release
- ✅ OWASP Dependency-Check + CycloneDX SBOM in CI
- ✅ FLAG_SECURE on sensitive screens

**Gaps:**

- ❌ Release signing not verified (P0)
- ❌ Crash reporting unclear (P1)
- ❌ Performance monitoring missing (P2)
- ❌ Feature flags system missing (P1)
- ❌ 7 remaining security issues (P1-P2)
- ❌ iOS app not started (P2)

***REMOVED******REMOVED******REMOVED*** Backend - ⚠️ **Feature-Complete but DEV-ONLY**

**Strengths:**

- ✅ Fastify + TypeScript + Prisma ORM
- ✅ PostgreSQL 16 database
- ✅ Comprehensive Zod configuration validation
- ✅ eBay OAuth flow (sandbox + production)
- ✅ Cloud classification proxy (Google Vision + mock)
- ✅ Assistant API (OpenAI + mock)
- ✅ Security middleware (CORS, CSRF, Helmet, correlation IDs)
- ✅ Rate limiting (sliding window, per-IP + per-key)
- ✅ Circuit breakers
- ✅ API key management
- ✅ Token encryption (AES-256-GCM)
- ✅ Health + readiness endpoints
- ✅ Pino structured logging with redaction

**Critical Gaps:**

- ❌ **No production deployment** (P0) - Dev-only setup
- ❌ **No authentication/authorization** (P0) - API publicly accessible
- ❌ **No PostgreSQL backups** (P0) - Data loss risk
- ❌ **No environment separation** (P0) - Single dev config
- ❌ **No cost controls** (P0) - Runaway API costs
- ❌ **HTTP only** (P0) - No TLS configured
- ❌ **No integration/E2E tests** (P1) - Unit tests only
- ❌ **No CI/CD pipeline** (P1) - Manual deployments
- ❌ **No API documentation** (P1) - No OpenAPI spec

***REMOVED******REMOVED******REMOVED*** Observability - ⚠️ **DEV-ONLY**

**Strengths:**

- ✅ LGTM stack (Loki, Grafana, Tempo, Mimir, Alloy)
- ✅ OpenTelemetry instrumentation (backend)
- ✅ Integrated dev startup scripts
- ✅ Pre-provisioned dashboards
- ✅ Structured logging with Pino

**Critical Gaps:**

- ❌ **No production alerting** (P0) - Dev monitoring only
- ❌ **No SLOs/SLAs** (P0) - Cannot measure reliability
- ❌ **Anonymous Grafana** (P0) - No auth configured
- ❌ **Local storage** (P0) - No persistence/backups
- ❌ **No incident response process** (P2)
- ❌ **Short retention** (P2) - 14d logs, 7d traces
- ❌ **No mobile telemetry** - Backend only

---

***REMOVED******REMOVED*** Issues by Area

| Area             | Issues | Key Concerns                                              |
|------------------|--------|-----------------------------------------------------------|
| **area:backend** | 11     | Deployment, auth, tests, CI/CD, rate limits, TLS, backups |
| **area:android** | 5      | Signing, crash reporting, performance, security           |
| **area:ci**      | 4      | Release workflows, E2E tests, deployment pipelines        |
| **area:docs**    | 3      | Privacy policy, incident runbooks, API docs               |
| **area:logging** | 3      | Crash reporting, performance monitoring, log retention    |
| **area:network** | 2      | TLS, API docs                                             |
| **area:auth**    | 2      | Backend auth, OAuth guidance                              |
| **area:privacy** | 1      | Privacy policy                                            |

---

***REMOVED******REMOVED*** Cost Estimates (AWS/GCP - Monthly)

**Assumptions:** 1,000 active users, 10,000 classifications/day, 5,000 assistant requests/day

| Service           | Resource                       | Cost                 |
|-------------------|--------------------------------|----------------------|
| **Compute**       | 3x t3.medium (backend)         | $75                  |
| **Database**      | db.t3.small (PostgreSQL)       | $30                  |
| **Storage**       | 100GB EBS + 50GB backups       | $15                  |
| **Load Balancer** | Application LB                 | $20                  |
| **Observability** | Grafana Cloud (100GB logs)     | $50                  |
| **Google Vision** | 10k classifications × $1.50/1k | $15                  |
| **OpenAI**        | 5k requests × $0.002/req       | $10                  |
| **Bandwidth**     | 500GB egress                   | $45                  |
| **Sentry**        | 50k events/month               | $26 (Developer plan) |
| **Misc**          | Secrets Manager, CloudWatch    | $10                  |
| **Total**         |                                | **~$296/month**      |

**At scale (10,000 users):** ~$500-800/month (scale compute, add Redis, increase quotas)

---

***REMOVED******REMOVED*** Compliance Checklist

***REMOVED******REMOVED******REMOVED*** GDPR (EU Users)

| Requirement                     | Status     | Issue         |
|---------------------------------|------------|---------------|
| Privacy policy                  | ❌          | ***REMOVED***250          |
| User consent for cloud features | ⚠️ Partial | ***REMOVED***250          |
| Data retention limits           | ✅          | Implemented   |
| Right to deletion               | ❌          | Backend issue |
| Right to data export            | ❌          | Backend issue |

***REMOVED******REMOVED******REMOVED*** OWASP Mobile Top 10 (2024)

| Risk                                      | Status     | Issue                                           |
|-------------------------------------------|------------|-------------------------------------------------|
| M1: Improper Credential Usage             | ✅          | Fixed (no hardcoded secrets)                    |
| M2: Inadequate Supply Chain Security      | ✅          | Fixed (SBOM + CVE scanning)                     |
| M3: Insecure Authentication/Authorization | ❌          | ***REMOVED***238 (backend auth)                             |
| M4: Insufficient Input/Output Validation  | ✅          | Fixed (OCR limits, listing validation)          |
| M5: Insecure Communication                | ⚠️ Partial | ***REMOVED***244 (TLS), ✅ Android (network security config) |
| M6: Inadequate Privacy Controls           | ⚠️ Partial | ***REMOVED***250 (privacy policy), ✅ FLAG_SECURE            |
| M7: Insufficient Binary Protections       | ⚠️ Partial | ✅ Obfuscation, ❌ Root detection (***REMOVED***253)          |
| M8: Security Misconfiguration             | ✅          | Fixed (backup disabled, debug logs stripped)    |
| M9: Insecure Data Storage                 | ⚠️ Partial | ***REMOVED***240 (backend backups), ***REMOVED***253 (image encryption) |
| M10: Insufficient Cryptography            | ✅          | Good (Jetpack Security guidance)                |

***REMOVED******REMOVED******REMOVED*** MASVS Compliance

| Category         | Status     | Notes                                           |
|------------------|------------|-------------------------------------------------|
| MASVS-STORAGE    | ⚠️ Partial | ✅ Backup disabled, ❌ Image encryption (***REMOVED***253)    |
| MASVS-CRYPTO     | ✅ Pass     | No insecure crypto                              |
| MASVS-AUTH       | ❌ Fail     | ***REMOVED***238 (backend auth missing)                     |
| MASVS-NETWORK    | ⚠️ Partial | ✅ Android (NSC), ❌ Backend TLS (***REMOVED***244)           |
| MASVS-PLATFORM   | ✅ Pass     | Proper permissions, no over-exported components |
| MASVS-CODE       | ✅ Pass     | Dependency scanning in CI                       |
| MASVS-RESILIENCE | ⚠️ Partial | ✅ Obfuscation, ❌ Root detection (***REMOVED***253)          |
| MASVS-PRIVACY    | ⚠️ Partial | ✅ FLAG_SECURE, ❌ Privacy policy (***REMOVED***250)          |

---

***REMOVED******REMOVED*** Success Criteria for Go-Live

***REMOVED******REMOVED******REMOVED*** Minimum Viable Production (MVP)

All **P0** issues resolved:

- ✅ Backend deployable to production with Dockerfile + K8s
- ✅ Backend authentication protecting user data
- ✅ Production observability with alerting and SLOs
- ✅ PostgreSQL backups with tested restore process
- ✅ Dev/staging/production environment separation
- ✅ Android release signing verified
- ✅ Rate limiting cost controls preventing runaway spend
- ✅ TLS/SSL configured for HTTPS

***REMOVED******REMOVED******REMOVED*** Production-Ready (Beyond MVP)

All **P0 + P1** issues resolved (MVP + Week 2):

- ✅ Backend integration tests in CI
- ✅ Automated CI/CD to staging/production
- ✅ Crash reporting operational
- ✅ API documentation published
- ✅ Feature flags for gradual rollouts
- ✅ Privacy policy published

***REMOVED******REMOVED******REMOVED*** Enterprise-Ready (Full Maturity)

All **P0 + P1 + P2** issues resolved (MVP + Week 2 + Week 3):

- ✅ Production log retention and archival
- ✅ Performance monitoring and baselines
- ✅ All 18 security issues resolved
- ✅ Incident response runbook
- ✅ E2E test framework
- ✅ iOS roadmap and plan

---

***REMOVED******REMOVED*** Risk Mitigation Strategies

***REMOVED******REMOVED******REMOVED*** High-Risk Areas

1. **Backend Authentication (***REMOVED***238)**
    - **Risk:** Massive data breach if deployed without auth
    - **Mitigation:** Block production deploy until auth complete, require PR review from security
      team
    - **Rollback Plan:** Not applicable (must fix before deploy)

2. **PostgreSQL Backups (***REMOVED***240)**
    - **Risk:** Permanent data loss
    - **Mitigation:** Daily automated backups to S3, weekly restore tests, replication for HA
    - **Rollback Plan:** Restore from latest backup (RPO 24h max)

3. **Rate Limiting (***REMOVED***243)**
    - **Risk:** $1000s in runaway API costs
    - **Mitigation:** Daily spend caps, cost alerts at 50%/80%/100%, kill switch to mock providers
    - **Rollback Plan:** Disable Google Vision/OpenAI, switch to mock until quotas reset

***REMOVED******REMOVED******REMOVED*** Medium-Risk Areas

4. **TLS Configuration (***REMOVED***244)**
    - **Risk:** Credential theft via MITM
    - **Mitigation:** Let's Encrypt auto-renewal, certificate expiry monitoring
    - **Rollback Plan:** Load balancer handles TLS termination (fallback if app-level TLS fails)

5. **Crash Reporting (***REMOVED***246)**
    - **Risk:** Invisible production crashes
    - **Mitigation:** Sentry integration, ProGuard mapping upload, alerts on new crash types
    - **Rollback Plan:** N/A (monitoring only, no functional impact)

---

***REMOVED******REMOVED*** Next Steps

***REMOVED******REMOVED******REMOVED*** Immediate Actions (Today)

1. ✅ **Review this backlog** with team (product, engineering, SRE)
2. ✅ **Assign owners** to P0 issues (***REMOVED***237-***REMOVED***244)
3. ✅ **Create project board** (GitHub Projects) with columns: Backlog, In Progress, Review, Done
4. ✅ **Schedule daily standups** for Week 1 (critical path items)
5. ✅ **Block production deploy** until P0 issues resolved

***REMOVED******REMOVED******REMOVED*** Week 1 Kickoff (Monday)

1. ✅ **Backend team**: Start ***REMOVED***237 (deployment) and ***REMOVED***241 (environments) in parallel
2. ✅ **Mobile team**: Verify ***REMOVED***242 (release signing)
3. ✅ **SRE team**: Start ***REMOVED***240 (backups) and ***REMOVED***239 (observability)
4. ✅ **Security review**: Review ***REMOVED***238 (auth) design before implementation

***REMOVED******REMOVED******REMOVED*** Milestone: Beta Launch (End of Week 2)

- ✅ All P0 + P1 issues resolved
- ✅ Beta deployed to production with limited user base (100 users)
- ✅ Monitor for 1 week, fix critical bugs
- ✅ Gradual rollout: 100 → 500 → 2,000 → public

***REMOVED******REMOVED******REMOVED*** Milestone: Public Launch (End of Week 4)

- ✅ All P0 + P1 + critical P2 issues resolved
- ✅ 2,000 beta users with no major incidents
- ✅ SLOs met for 7 consecutive days (99.9% uptime, p95 latency < 500ms)
- ✅ Play Store submission approved
- ✅ Public launch announcement

---

***REMOVED******REMOVED*** Appendix A: Issue Reference

| Issue | Title                         | Epic           | Priority | Severity | Estimated Effort |
|-------|-------------------------------|----------------|----------|----------|------------------|
| ***REMOVED***237  | Backend production deployment | Backend        | P0       | Critical | 2 days           |
| ***REMOVED***238  | Backend authentication        | Backend        | P0       | Critical | 3 days           |
| ***REMOVED***239  | Production observability      | Observability  | P0       | Critical | 2 days           |
| ***REMOVED***240  | PostgreSQL backups            | Backend        | P0       | Critical | 1 day            |
| ***REMOVED***241  | Environment separation        | Backend        | P0       | Critical | 2 days           |
| ***REMOVED***242  | Android release signing       | Mobile         | P0       | High     | 4 hours          |
| ***REMOVED***243  | Rate limiting cost controls   | Backend        | P0       | High     | 1 day            |
| ***REMOVED***244  | TLS/SSL configuration         | Backend        | P0       | High     | 1 day            |
| ***REMOVED***245  | Backend integration tests     | Backend        | P1       | High     | 2 days           |
| ***REMOVED***246  | Crash reporting (Sentry)      | Mobile         | P1       | High     | 1 day            |
| ***REMOVED***247  | API documentation             | Backend        | P1       | High     | 1 day            |
| ***REMOVED***248  | Feature flags system          | Mobile/Backend | P1       | Medium   | 2 days           |
| ***REMOVED***249  | Backend CI/CD pipeline        | Backend        | P1       | Medium   | 2 days           |
| ***REMOVED***250  | Privacy policy                | Docs           | P1       | Medium   | 4 hours          |
| ***REMOVED***251  | Log retention policy          | Observability  | P2       | Medium   | 1 day            |
| ***REMOVED***252  | Performance monitoring        | Mobile         | P2       | Medium   | 1 day            |
| ***REMOVED***253  | Remaining security issues     | Security       | P2       | Medium   | 2 days           |
| ***REMOVED***254  | Incident response runbook     | Observability  | P2       | Low      | 4 hours          |
| ***REMOVED***255  | iOS app development           | Scale-iOS      | P2       | Low      | TBD              |
| ***REMOVED***256  | E2E testing framework         | Mobile/Backend | P2       | Low      | 2 days           |

**Total Estimated Effort (sequential):** ~25-30 days
**Total Estimated Effort (parallelized with 3-4 engineers):** ~2-3 weeks

---

***REMOVED******REMOVED*** Appendix B: Labels Created

```bash
***REMOVED*** Severity labels
severity:critical  ***REMOVED*** Critical issue blocking go-live
severity:high      ***REMOVED*** High priority issue for go-live
severity:medium    ***REMOVED*** Medium priority issue
severity:low       ***REMOVED*** Low priority issue

***REMOVED*** Epic labels
epic:backend        ***REMOVED*** Backend API and services
epic:mobile         ***REMOVED*** Mobile application (Android/iOS)
epic:observability  ***REMOVED*** Monitoring, logging, alerting
epic:security       ***REMOVED*** Security and compliance
epic:docs           ***REMOVED*** Documentation
epic:scale-ios      ***REMOVED*** iOS platform support

***REMOVED*** Area labels
area:android, area:backend, area:network, area:auth, area:ml,
area:camera, area:logging, area:ci, area:privacy, area:docs

***REMOVED*** Priority labels
priority:p0  ***REMOVED*** Must be done before go-live
priority:p1  ***REMOVED*** Required shortly after beta/early launch
priority:p2  ***REMOVED*** Scale-up and future-proofing
```

---

***REMOVED******REMOVED*** Appendix C: Useful Commands

```bash
***REMOVED*** View all go-live issues
gh issue list --label priority:p0,priority:p1,priority:p2

***REMOVED*** View P0 blockers only
gh issue list --label priority:p0

***REMOVED*** View issues by epic
gh issue list --label epic:backend
gh issue list --label epic:mobile
gh issue list --label epic:observability

***REMOVED*** Create project board
gh project create --title "Scanium Go-Live" --body "Production readiness backlog"

***REMOVED*** Add issues to project
gh project item-add <project-number> --owner @me --url https://github.com/ilpeppino/scanium/issues/237
```

---

**Document Version:** 1.0
**Last Updated:** 2025-12-26
**Next Review:** After Week 1 (2025-01-02)
