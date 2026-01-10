***REMOVED*** Scanium Repository Review Report

**Review Date:** 2026-01-05
**Target Environment:** Synology DS418play NAS (Intel Celeron J3455, 2 cores, 6GB RAM)
**Branch:** main
**Commit:** 95fffce (clean working tree)
**Reviewer:** Automated Codex CLI Review

---

***REMOVED******REMOVED*** A) Executive Summary - Top 10 Risks

| ***REMOVED*** | Risk | Severity | Category | GitHub Issue | Status |
|---|------|----------|----------|--------------|--------|
| 1 | **Public port exposure on NAS** (OTLP 4317/4318) | P0 | Security | ***REMOVED***360 | OPEN |
| 2 | **Grafana anonymous admin access** in dev compose | P0 | Security | ***REMOVED***359 | OPEN |
| 3 | **NAS monitoring containers restart: "no"** | P1 | Ops | ***REMOVED***361 | NEW |
| 4 | **NAS Grafana auth not explicitly enforced** | P2 | Security | ***REMOVED***367 | NEW |
| 5 | **Alloy admin UI port 12345 exposed on NAS** | P2 | Security | ***REMOVED***362 | NEW |
| 6 | **Tempo storage pool 100 workers on 2-core NAS** | P2 | Performance | ***REMOVED***363 | NEW |
| 7 | **Prometheus scrape interval 15s too aggressive** | P2 | Performance | ***REMOVED***364 | NEW |
| 8 | **Backend vitest not runnable locally** | P2 | Testing | ***REMOVED***366 | NEW |
| 9 | **Architecture docs say Express.js, code uses Fastify** | P2 | Docs | ***REMOVED***365 | NEW |
| 10 | **Missing NAS-specific config overlays** | P3 | Ops | ***REMOVED***368 | NEW |

**Summary:** 2 P0 issues (pre-existing), 1 P1 issue (new), 7 P2/P3 issues (6 new)

---

***REMOVED******REMOVED*** B) Pre-Flight & Environment Status

***REMOVED******REMOVED******REMOVED*** Repository State
- **Branch:** main (up to date with origin)
- **Git Status:** Clean working tree
- **Remote:** git@github.com:ilpeppino/scanium.git

***REMOVED******REMOVED******REMOVED*** GitHub CLI
- **Version:** 2.83.2
- **Auth:** Authenticated as ilpeppino
- **Issue Creation:** Enabled

***REMOVED******REMOVED******REMOVED*** Components Inventory
| Component | Location | Technology |
|-----------|----------|------------|
| Android App | `androidApp/`, `android-*/` | Kotlin, Compose, ML Kit, Hilt |
| Shared Modules | `shared/core-*` | Kotlin Multiplatform |
| Backend API | `backend/` | Node.js 20, Fastify, Prisma |
| Database | Docker | PostgreSQL 16 |
| Monitoring | `monitoring/` | Grafana, Loki, Tempo, Mimir, Alloy |
| NAS Deploy | `deploy/nas/` | Docker Compose, Cloudflare Tunnel |

***REMOVED******REMOVED******REMOVED*** Baseline Signals
| Check | Result | Notes |
|-------|--------|-------|
| `git status` | Clean | No uncommitted changes |
| Backend `npm test` | FAILED | vitest not in PATH |
| Android `./gradlew test` | Not run | Android SDK not available |

---

***REMOVED******REMOVED*** C) Architecture & System Review

***REMOVED******REMOVED******REMOVED*** Current Architecture (Verified)

```
+-----------------------------------------------------------------+
|                       Android Application                        |
|  Modules: androidApp, core-*, shared/*, android-*-adapters      |
|  Tech: Kotlin 2.0, Compose, CameraX, ML Kit, Hilt DI            |
+----------------------------+------------------------------------+
                             | HTTPS/OTLP (Cloudflare Tunnel)
                             v
+-----------------------------------------------------------------+
|                      Backend (NAS Docker)                        |
|  Tech: Node.js 20, Fastify, Prisma, PostgreSQL 16               |
|  Endpoints: /v1/classify, /v1/assist, /auth/ebay, /healthz      |
+----------------------------+------------------------------------+
                             | OTLP
                             v
+-----------------------------------------------------------------+
|                 Observability Stack (NAS Docker)                 |
|  Alloy -> Loki (logs) / Tempo (traces) / Mimir (metrics)        |
|  -> Grafana (dashboards)                                         |
|  Network: scanium-observability (bridge)                         |
+-----------------------------------------------------------------+
```

***REMOVED******REMOVED******REMOVED*** Architecture Drift Findings

| Area | Documentation Says | Code Reality | Impact | Issue |
|------|-------------------|--------------|--------|-------|
| HTTP Framework | Express.js | Fastify | P2 - Docs misleading | ***REMOVED***365 |
| Health Endpoints | `/healthz`, `/readyz` | Correctly implemented | None | - |
| Restart Policy | Not specified | `restart: "no"` in NAS | P1 - No auto-restart | ***REMOVED***361 |

***REMOVED******REMOVED******REMOVED*** Key Positive Findings
- Security headers implemented correctly (CSP, HSTS, X-Frame-Options)
- API key validation with rate limiting
- CORS validation rejects wildcards
- Session secret validation rejects weak values
- Cloudflare Tunnel for production exposure (no direct port forwarding)

---

***REMOVED******REMOVED*** D) Security Findings

***REMOVED******REMOVED******REMOVED*** P0 - CRITICAL (Pre-existing)

| ID | Finding | Issue | Status |
|----|---------|-------|--------|
| SEC-001 | Public port exposure (OTLP 4317/4318) | ***REMOVED***360 | OPEN |
| SEC-002 | Grafana anonymous admin in dev compose | ***REMOVED***359 | OPEN |

***REMOVED******REMOVED******REMOVED*** P1 - HIGH

| ID | Finding | File:Line | Issue |
|----|---------|-----------|-------|
| SEC-003 | NAS containers won't restart after failure | `docker-compose.nas.monitoring.yml:5,29,39,49,62` | ***REMOVED***361 |

***REMOVED******REMOVED******REMOVED*** P2 - MEDIUM

| ID | Finding | File:Line | Issue |
|----|---------|-----------|-------|
| SEC-004 | NAS Grafana auth not explicitly enforced | `docker-compose.nas.monitoring.yml:3-10` | ***REMOVED***367 |
| SEC-005 | Alloy admin UI (12345) exposed on NAS | `docker-compose.nas.monitoring.yml:68` | ***REMOVED***362 |

***REMOVED******REMOVED******REMOVED*** Security Strengths Observed
- Strong configuration validation with Zod schemas
- API keys required for protected endpoints
- In-memory rate limiting per identity
- HTTPS enforcement in production
- HSTS headers with preload
- CSP, X-Content-Type-Options, X-Frame-Options set
- Weak session secret detection
- CORS origin validation (no wildcards)

---

***REMOVED******REMOVED*** E) Performance & Reliability Findings

***REMOVED******REMOVED******REMOVED*** NAS-Specific Concerns

| ID | Finding | Current Value | Recommended | Issue |
|----|---------|---------------|-------------|-------|
| PERF-001 | Tempo storage workers | 100 | 4 | ***REMOVED***363 |
| PERF-002 | Prometheus scrape interval | 15s | 60s | ***REMOVED***364 |
| PERF-003 | Mimir query parallelism | 32 | 4 | ***REMOVED***368 |
| PERF-004 | Mimir index cache | 512MB | 128MB | ***REMOVED***368 |

***REMOVED******REMOVED******REMOVED*** Retention Settings (Reasonable)

| Service | Retention | Assessment |
|---------|-----------|------------|
| Loki (logs) | 168h (7 days) | Acceptable for NAS |
| Tempo (traces) | 168h (7 days) | Acceptable for NAS |
| Mimir (metrics) | 360h (15 days) | Slightly high, consider 7 days |

***REMOVED******REMOVED******REMOVED*** Reliability Concerns

| Issue | Impact | Mitigation |
|-------|--------|------------|
| `restart: "no"` on NAS monitoring | Services won't recover after crash/reboot | Change to `unless-stopped` |
| In-memory rate limit state | Lost on restart | Consider Redis for distributed state |
| No health checks in NAS monitoring | Failures may go undetected | Add healthchecks to NAS compose |

---

***REMOVED******REMOVED*** F) Maintainability & Future-Proofing

***REMOVED******REMOVED******REMOVED*** Test Coverage

| Area | Status | Notes |
|------|--------|-------|
| Shared modules (core-*) | Unknown | JVM tests available |
| Android app | Unknown | Requires Android SDK |
| Backend | BLOCKED | vitest not runnable (***REMOVED***366) |

***REMOVED******REMOVED******REMOVED*** Documentation Quality

| Doc | Accuracy | Notes |
|-----|----------|-------|
| ARCHITECTURE.md | 95% | Express vs Fastify drift |
| DEV_GUIDE.md | Good | Comprehensive |
| CODEX_CONTEXT.md | Good | Feature routing accurate |
| SECURITY.md | Basic | Could be expanded |
| NAS README | Good | Detailed deployment guide |

***REMOVED******REMOVED******REMOVED*** Readiness Assessment

| Feature | Status | Gaps |
|---------|--------|------|
| On-device CLIP expansion | Foundation ready | No active implementation |
| Cloud classification | Implemented | Google Vision integration |
| B2B / inventory | Not started | Export-first approach active |
| iOS client | Foundation ready | KMP modules prepared |

---

***REMOVED******REMOVED*** G) Recommendations

***REMOVED******REMOVED******REMOVED*** Immediate (This Week)

1. **Address P0 security issues** (***REMOVED***359, ***REMOVED***360)
   - Bind OTLP ports to localhost or add auth
   - Disable Grafana anonymous access in prod

2. **Fix NAS reliability** (***REMOVED***361)
   - Change `restart: "no"` to `restart: unless-stopped`

3. **Fix backend tests** (***REMOVED***366)
   - Ensure vitest is properly installed/linked

***REMOVED******REMOVED******REMOVED*** Short-Term (2 Weeks)

4. Update architecture docs (***REMOVED***365)
5. Tune NAS performance settings (***REMOVED***363, ***REMOVED***364)
6. Enforce Grafana auth on NAS (***REMOVED***367)
7. Create NAS config overlays (***REMOVED***368)

***REMOVED******REMOVED******REMOVED*** Medium-Term (2 Months)

8. Add distributed rate limiting (Redis)
9. Implement alerting (SLOs, disk usage)
10. Document backup/restore procedures
11. Penetration testing for production deployment

---

***REMOVED******REMOVED*** H) Issues Created in This Review

| Issue | Title | Priority | Category |
|-------|-------|----------|----------|
| ***REMOVED***361 | NAS monitoring containers use 'restart: no' policy | P1 | Ops |
| ***REMOVED***362 | NAS Alloy admin UI port 12345 exposed externally | P2 | Security |
| ***REMOVED***363 | Tempo storage pool max_workers too high for NAS | P2 | Performance |
| ***REMOVED***364 | Prometheus scrape interval 15s too aggressive for NAS | P2 | Performance |
| ***REMOVED***365 | Architecture documentation says Express.js but backend uses Fastify | P2 | Docs |
| ***REMOVED***366 | Backend tests cannot run - vitest not in PATH | P2 | Testing |
| ***REMOVED***367 | NAS monitoring Grafana missing authentication enforcement | P2 | Security |
| ***REMOVED***368 | Create NAS-specific configuration overlays for monitoring stack | P3 | Ops |

---

***REMOVED******REMOVED*** I) Summary Statistics

| Category | P0 | P1 | P2 | P3 | Total |
|----------|----|----|----|----|-------|
| Security | 2 | 0 | 2 | 0 | 4 |
| Performance | 0 | 0 | 2 | 0 | 2 |
| Operations | 0 | 1 | 0 | 1 | 2 |
| Documentation | 0 | 0 | 1 | 0 | 1 |
| Testing | 0 | 0 | 1 | 0 | 1 |
| **Total** | **2** | **1** | **6** | **1** | **10** |

**New Issues Created:** 8
**Pre-existing Issues Referenced:** 2 (***REMOVED***359, ***REMOVED***360)

---

***REMOVED******REMOVED*** J) Checks Not Performed

| Check | Reason |
|-------|--------|
| `./gradlew test` | Android SDK not available |
| `./gradlew assembleDebug` | Android SDK not available |
| Backend integration tests | vitest not runnable |
| Docker container startup | Review is read-only |
| Network security scan | Out of scope for static review |

---

*Report generated by automated repository review (Codex CLI). All findings require human verification before remediation.*
