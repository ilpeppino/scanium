# Repository Review Action Backlog

**Generated:** 2026-01-01
**Source:** [REPO_REVIEW_REPORT.md](REPO_REVIEW_REPORT.md)

---

## Prioritized Action Items

| ID | Priority | Area | Title | Evidence | Recommendation | Effort | Risk if Unresolved | Owner |
|----|----------|------|-------|----------|----------------|--------|-------------------|-------|
| SEC-001 | P0 | Security | Hardcoded secrets in git | `backend/.env`, `.env.backup`, `.env.last` tracked | Revoke keys, scrub history, add to gitignore | 4h | **CRITICAL** - Credential theft | - |
| SEC-002 | P0 | Security | Database default credentials | `docker-compose.yml:10-12` defaults to `scanium:scanium` | Remove defaults, require explicit password | 1h | **CRITICAL** - DB compromise | - |
| SEC-003 | P1 | Security | Grafana anonymous admin | `monitoring/docker-compose.yml:103-108` | Disable anon auth, enable login | 30m | Data exposure, config tampering | - |
| SEC-004 | P1 | Security | OTLP ports exposed without auth | Ports 4317/4318 bound to 0.0.0.0 | Bind to 127.0.0.1 or add auth | 1h | Log injection, DoS | - |
| SEC-005 | P1 | Security | Database TLS disabled | `.env.backup:13` uses `sslmode=disable` | Set `sslmode=require` everywhere | 1h | Data interception | - |
| PERF-001 | P1 | Performance | Loki retention workers overwhelm CPU | `loki.yaml:48` sets 150 workers | Set to 2-4 workers | 15m | NAS CPU exhaustion | - |
| PERF-002 | P1 | Performance | Log retention fills NAS storage | 14-day retention = 40-50GB | Reduce to 72h (3 days) | 30m | Storage exhaustion | - |
| SEC-006 | P2 | Security | No rate limit on admin endpoints | `modules/admin/routes.ts:10-26` | Add IP rate limit (10/min) | 2h | Brute force attacks | - |
| SEC-007 | P2 | Security | No JSON body size limit | `app.ts` missing bodyLimit | Add `bodyLimit: '1mb'` | 30m | Memory exhaustion DoS | - |
| SEC-008 | P2 | Security | Partial API key in logs | `routes.ts:84` logs first 8 chars | Hash key before logging | 1h | Key correlation | - |
| SEC-009 | P2 | Security | HTTPS enforcement optional | `plugins/security.ts:40` | Make mandatory in production | 1h | MitM attacks | - |
| PERF-003 | P2 | Performance | Scrape interval too aggressive | `alloy.hcl:118` at 15s | Increase to 60s | 15m | CPU overhead | - |
| PERF-004 | P2 | Performance | No DB connection pool limit | DATABASE_URL missing param | Add `?connection_limit=5` | 15m | Connection exhaustion | - |
| PERF-005 | P2 | Reliability | Healthcheck wrong endpoint | Uses `/health` not `/readyz` | Change to `/readyz` | 15m | DB failures undetected | - |
| PERF-006 | P2 | Reliability | Readiness check no timeout | `health/routes.ts:51-52` | Add 5s timeout wrapper | 30m | Hung health checks | - |
| MAINT-001 | P2 | Maintainability | Backend tests failing | 4 suites, missing unified-cache | Create missing module | 2h | CI unreliable | - |
| MAINT-002 | P2 | Maintainability | Domain pack test failure | `home-resale-domain-pack.test.ts:527` | Fix priority resolution logic | 1h | Incorrect categorization | - |
| PERF-007 | P2 | Reliability | Rate limit state lost on restart | In-memory Map only | Consider Redis for distributed | 4h | Rate limits reset | - |
| PERF-008 | P2 | Reliability | No restart backoff policy | Docker `restart: unless-stopped` | Use `on-failure:5:30s` | 15m | Restart loops | - |
| SEC-010 | P3 | Security | eBay sandbox creds in repo | `backend/.env:39` | Rotate sandbox credentials | 1h | Test env abuse | - |
| SEC-011 | P3 | Security | Default passwords in examples | `monitoring.env.example:6` | Document secure generation | 30m | Weak passwords used | - |
| SEC-012 | P3 | Security | Custom CORS scheme accepted | `config/index.ts:25` scanium:// | Validate app signature | 2h | CORS bypass | - |
| MAINT-003 | P3 | Maintainability | NAS deployment docs scattered | Multiple files | Create unified checklist | 2h | Misconfiguration | - |
| MAINT-004 | P3 | Maintainability | No backup procedure documented | Not documented | Add backup script + docs | 4h | Data loss | - |

---

## Filtering Views

### By Priority

**P0 - Critical (2 items):**
- SEC-001, SEC-002

**P1 - High (5 items):**
- SEC-003, SEC-004, SEC-005, PERF-001, PERF-002

**P2 - Medium (13 items):**
- SEC-006, SEC-007, SEC-008, SEC-009
- PERF-003, PERF-004, PERF-005, PERF-006, PERF-007, PERF-008
- MAINT-001, MAINT-002

**P3 - Low (5 items):**
- SEC-010, SEC-011, SEC-012, MAINT-003, MAINT-004

### By Area

**Security (12 items):**
- SEC-001 through SEC-012

**Performance (6 items):**
- PERF-001 through PERF-006

**Reliability (2 items):**
- PERF-007, PERF-008

**Maintainability (4 items):**
- MAINT-001 through MAINT-004

### By Effort

**Quick Wins (< 1h):**
- PERF-001 (15m), PERF-002 (30m), PERF-003 (15m), PERF-004 (15m)
- PERF-005 (15m), PERF-006 (30m), PERF-008 (15m)
- SEC-003 (30m), SEC-007 (30m), SEC-011 (30m)

**Medium Effort (1-2h):**
- SEC-002 (1h), SEC-004 (1h), SEC-005 (1h), SEC-008 (1h), SEC-009 (1h)
- SEC-010 (1h), SEC-012 (2h), MAINT-002 (1h), MAINT-003 (2h)

**Higher Effort (> 2h):**
- SEC-001 (4h), SEC-006 (2h), MAINT-001 (2h), PERF-007 (4h), MAINT-004 (4h)

---

## Sprint Planning Suggestions

### Sprint 1 (Security Focus) - 16h estimated
- [ ] SEC-001: Revoke credentials, scrub history (4h)
- [ ] SEC-002: Remove default credentials (1h)
- [ ] SEC-003: Disable Grafana anon access (30m)
- [ ] SEC-004: Bind OTLP to localhost (1h)
- [ ] SEC-005: Enable database TLS (1h)
- [ ] PERF-001: Fix Loki retention workers (15m)
- [ ] PERF-002: Reduce retention periods (30m)

### Sprint 2 (NAS Optimization) - 12h estimated
- [ ] PERF-003: Increase scrape interval (15m)
- [ ] PERF-004: Add DB connection limit (15m)
- [ ] PERF-005: Fix healthcheck endpoint (15m)
- [ ] PERF-006: Add readiness timeout (30m)
- [ ] PERF-008: Add restart backoff (15m)
- [ ] SEC-006: Rate limit admin endpoints (2h)
- [ ] SEC-007: Add JSON body limit (30m)
- [ ] MAINT-001: Fix backend tests (2h)
- [ ] MAINT-002: Fix domain pack test (1h)

### Backlog (When Capacity Allows)
- SEC-008: Hash API keys in logs
- SEC-009: Mandatory HTTPS in prod
- SEC-010, SEC-011, SEC-012: Low priority security
- PERF-007: Distributed rate limiting
- MAINT-003, MAINT-004: Documentation

---

## Definition of Done

For each item to be considered complete:

1. **Code/Config Change** implemented (if applicable)
2. **Tests** pass (for code changes)
3. **Documentation** updated (if behavior changes)
4. **Deployed** to NAS environment (for infra changes)
5. **Verified** via manual or automated check
6. **PR** merged with review approval

---

*This backlog is auto-generated. Manually adjust priorities based on business context.*
