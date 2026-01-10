# Scanium Repository Review Report

**Review Date:** 2026-01-05
**Target Environment:** Synology DS418play NAS (Intel Celeron J3455, 2 cores, 6GB RAM)
**Branch:** main
**Commit:** 95fffce (clean working tree)
**Reviewer:** Automated Codex CLI Review

---

## A) Executive Summary - Top 10 Risks

| # | Risk | Severity | Category | GitHub Issue | Status |
|---|------|----------|----------|--------------|--------|
| 1 | **Public port exposure on NAS** (OTLP 4317/4318) | P0 | Security | #360 | OPEN |
| 2 | **Grafana anonymous admin access** in dev compose | P0 | Security | #359 | OPEN |
| 3 | **NAS monitoring containers restart: "no"** | P1 | Ops | #361 | NEW |
| 4 | **NAS Grafana auth not explicitly enforced** | P2 | Security | #367 | NEW |
| 5 | **Alloy admin UI port 12345 exposed on NAS** | P2 | Security | #362 | NEW |
| 6 | **Tempo storage pool 100 workers on 2-core NAS** | P2 | Performance | #363 | NEW |
| 7 | **Prometheus scrape interval 15s too aggressive** | P2 | Performance | #364 | NEW |
| 8 | **Backend vitest not runnable locally** | P2 | Testing | #366 | NEW |
| 9 | **Architecture docs say Express.js, code uses Fastify** | P2 | Docs | #365 | NEW |
| 10 | **Missing NAS-specific config overlays** | P3 | Ops | #368 | NEW |

**Summary:** 2 P0 issues (pre-existing), 1 P1 issue (new), 7 P2/P3 issues (6 new)

---

## B) Pre-Flight & Environment Status

### Repository State
- **Branch:** main (up to date with origin)
- **Git Status:** Clean working tree
- **Remote:** git@github.com:ilpeppino/scanium.git

### GitHub CLI
- **Version:** 2.83.2
- **Auth:** Authenticated as ilpeppino
- **Issue Creation:** Enabled

### Components Inventory
| Component | Location | Technology |
|-----------|----------|------------|
| Android App | `androidApp/`, `android-*/` | Kotlin, Compose, ML Kit, Hilt |
| Shared Modules | `shared/core-*` | Kotlin Multiplatform |
| Backend API | `backend/` | Node.js 20, Fastify, Prisma |
| Database | Docker | PostgreSQL 16 |
| Monitoring | `monitoring/` | Grafana, Loki, Tempo, Mimir, Alloy |
| NAS Deploy | `deploy/nas/` | Docker Compose, Cloudflare Tunnel |

### Baseline Signals
| Check | Result | Notes |
|-------|--------|-------|
| `git status` | Clean | No uncommitted changes |
| Backend `npm test` | FAILED | vitest not in PATH |
| Android `./gradlew test` | Not run | Android SDK not available |

---

## C) Architecture & System Review

### Current Architecture (Verified)

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

### Architecture Drift Findings

| Area | Documentation Says | Code Reality | Impact | Issue |
|------|-------------------|--------------|--------|-------|
| HTTP Framework | Express.js | Fastify | P2 - Docs misleading | #365 |
| Health Endpoints | `/healthz`, `/readyz` | Correctly implemented | None | - |
| Restart Policy | Not specified | `restart: "no"` in NAS | P1 - No auto-restart | #361 |

### Key Positive Findings
- Security headers implemented correctly (CSP, HSTS, X-Frame-Options)
- API key validation with rate limiting
- CORS validation rejects wildcards
- Session secret validation rejects weak values
- Cloudflare Tunnel for production exposure (no direct port forwarding)

---

## D) Security Findings

### P0 - CRITICAL (Pre-existing)

| ID | Finding | Issue | Status |
|----|---------|-------|--------|
| SEC-001 | Public port exposure (OTLP 4317/4318) | #360 | OPEN |
| SEC-002 | Grafana anonymous admin in dev compose | #359 | OPEN |

### P1 - HIGH

| ID | Finding | File:Line | Issue |
|----|---------|-----------|-------|
| SEC-003 | NAS containers won't restart after failure | `docker-compose.nas.monitoring.yml:5,29,39,49,62` | #361 |

### P2 - MEDIUM

| ID | Finding | File:Line | Issue |
|----|---------|-----------|-------|
| SEC-004 | NAS Grafana auth not explicitly enforced | `docker-compose.nas.monitoring.yml:3-10` | #367 |
| SEC-005 | Alloy admin UI (12345) exposed on NAS | `docker-compose.nas.monitoring.yml:68` | #362 |

### Security Strengths Observed
- Strong configuration validation with Zod schemas
- API keys required for protected endpoints
- In-memory rate limiting per identity
- HTTPS enforcement in production
- HSTS headers with preload
- CSP, X-Content-Type-Options, X-Frame-Options set
- Weak session secret detection
- CORS origin validation (no wildcards)

---

## E) Performance & Reliability Findings

### NAS-Specific Concerns

| ID | Finding | Current Value | Recommended | Issue |
|----|---------|---------------|-------------|-------|
| PERF-001 | Tempo storage workers | 100 | 4 | #363 |
| PERF-002 | Prometheus scrape interval | 15s | 60s | #364 |
| PERF-003 | Mimir query parallelism | 32 | 4 | #368 |
| PERF-004 | Mimir index cache | 512MB | 128MB | #368 |

### Retention Settings (Reasonable)

| Service | Retention | Assessment |
|---------|-----------|------------|
| Loki (logs) | 168h (7 days) | Acceptable for NAS |
| Tempo (traces) | 168h (7 days) | Acceptable for NAS |
| Mimir (metrics) | 360h (15 days) | Slightly high, consider 7 days |

### Reliability Concerns

| Issue | Impact | Mitigation |
|-------|--------|------------|
| `restart: "no"` on NAS monitoring | Services won't recover after crash/reboot | Change to `unless-stopped` |
| In-memory rate limit state | Lost on restart | Consider Redis for distributed state |
| No health checks in NAS monitoring | Failures may go undetected | Add healthchecks to NAS compose |

---

## F) Maintainability & Future-Proofing

### Test Coverage

| Area | Status | Notes |
|------|--------|-------|
| Shared modules (core-*) | Unknown | JVM tests available |
| Android app | Unknown | Requires Android SDK |
| Backend | BLOCKED | vitest not runnable (#366) |

### Documentation Quality

| Doc | Accuracy | Notes |
|-----|----------|-------|
| ARCHITECTURE.md | 95% | Express vs Fastify drift |
| DEV_GUIDE.md | Good | Comprehensive |
| CODEX_CONTEXT.md | Good | Feature routing accurate |
| SECURITY.md | Basic | Could be expanded |
| NAS README | Good | Detailed deployment guide |

### Readiness Assessment

| Feature | Status | Gaps |
|---------|--------|------|
| On-device CLIP expansion | Foundation ready | No active implementation |
| Cloud classification | Implemented | Google Vision integration |
| B2B / inventory | Not started | Export-first approach active |
| iOS client | Foundation ready | KMP modules prepared |

---

## G) Recommendations

### Immediate (This Week)

1. **Address P0 security issues** (#359, #360)
   - Bind OTLP ports to localhost or add auth
   - Disable Grafana anonymous access in prod

2. **Fix NAS reliability** (#361)
   - Change `restart: "no"` to `restart: unless-stopped`

3. **Fix backend tests** (#366)
   - Ensure vitest is properly installed/linked

### Short-Term (2 Weeks)

4. Update architecture docs (#365)
5. Tune NAS performance settings (#363, #364)
6. Enforce Grafana auth on NAS (#367)
7. Create NAS config overlays (#368)

### Medium-Term (2 Months)

8. Add distributed rate limiting (Redis)
9. Implement alerting (SLOs, disk usage)
10. Document backup/restore procedures
11. Penetration testing for production deployment

---

## H) Issues Created in This Review

| Issue | Title | Priority | Category |
|-------|-------|----------|----------|
| #361 | NAS monitoring containers use 'restart: no' policy | P1 | Ops |
| #362 | NAS Alloy admin UI port 12345 exposed externally | P2 | Security |
| #363 | Tempo storage pool max_workers too high for NAS | P2 | Performance |
| #364 | Prometheus scrape interval 15s too aggressive for NAS | P2 | Performance |
| #365 | Architecture documentation says Express.js but backend uses Fastify | P2 | Docs |
| #366 | Backend tests cannot run - vitest not in PATH | P2 | Testing |
| #367 | NAS monitoring Grafana missing authentication enforcement | P2 | Security |
| #368 | Create NAS-specific configuration overlays for monitoring stack | P3 | Ops |

---

## I) Summary Statistics

| Category | P0 | P1 | P2 | P3 | Total |
|----------|----|----|----|----|-------|
| Security | 2 | 0 | 2 | 0 | 4 |
| Performance | 0 | 0 | 2 | 0 | 2 |
| Operations | 0 | 1 | 0 | 1 | 2 |
| Documentation | 0 | 0 | 1 | 0 | 1 |
| Testing | 0 | 0 | 1 | 0 | 1 |
| **Total** | **2** | **1** | **6** | **1** | **10** |

**New Issues Created:** 8
**Pre-existing Issues Referenced:** 2 (#359, #360)

---

## J) Checks Not Performed

| Check | Reason |
|-------|--------|
| `./gradlew test` | Android SDK not available |
| `./gradlew assembleDebug` | Android SDK not available |
| Backend integration tests | vitest not runnable |
| Docker container startup | Review is read-only |
| Network security scan | Out of scope for static review |

---

*Report generated by automated repository review (Codex CLI). All findings require human verification before remediation.*
