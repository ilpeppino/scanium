***REMOVED*** Repository Review Action Backlog

**Last Updated:** 2026-01-05
**Source:** [REPO_REVIEW_REPORT.md](REPO_REVIEW_REPORT.md)

---

***REMOVED******REMOVED*** GitHub Issues Summary

| Priority | Count | Issues |
|----------|-------|--------|
| P0 | 2 | ***REMOVED***359, ***REMOVED***360 |
| P1 | 1 | ***REMOVED***361 |
| P2 | 6 | ***REMOVED***362, ***REMOVED***363, ***REMOVED***364, ***REMOVED***365, ***REMOVED***366, ***REMOVED***367 |
| P3 | 1 | ***REMOVED***368 |

---

***REMOVED******REMOVED*** Prioritized Action Items

| ID | Priority | Area | Title | Evidence | Recommendation | Effort | Risk | Issue |
|----|----------|------|-------|----------|----------------|--------|------|-------|
| 1 | P0 | Security | OTLP ports exposed without auth | `monitoring/docker-compose.yml:14-16` | Bind to 127.0.0.1 or add auth | 1h | CRITICAL | ***REMOVED***360 |
| 2 | P0 | Security | Grafana anonymous admin access | `monitoring/docker-compose.yml:103-108` | Disable anon auth, enable login | 30m | CRITICAL | ***REMOVED***359 |
| 3 | P1 | Ops | NAS monitoring restart policy | `docker-compose.nas.monitoring.yml:5,29,39,49,62` | Change to `unless-stopped` | 15m | HIGH | ***REMOVED***361 |
| 4 | P2 | Security | NAS Alloy admin UI exposed | `docker-compose.nas.monitoring.yml:68` | Bind port 12345 to localhost | 15m | MEDIUM | ***REMOVED***362 |
| 5 | P2 | Performance | Tempo workers too high for NAS | `tempo/tempo.yaml:40` max_workers: 100 | Reduce to 4 | 15m | MEDIUM | ***REMOVED***363 |
| 6 | P2 | Performance | Scrape interval too aggressive | `alloy/alloy.hcl:118` scrape_interval: 15s | Increase to 60s | 15m | MEDIUM | ***REMOVED***364 |
| 7 | P2 | Docs | Architecture says Express.js | `ARCHITECTURE.md:274` | Update to Fastify | 30m | LOW | ***REMOVED***365 |
| 8 | P2 | Testing | Backend tests not runnable | `npm test` fails - vitest not found | Fix npm dependencies | 30m | MEDIUM | ***REMOVED***366 |
| 9 | P2 | Security | NAS Grafana auth missing | `docker-compose.nas.monitoring.yml` | Add GF_AUTH_* env vars | 15m | MEDIUM | ***REMOVED***367 |
| 10 | P3 | Ops | NAS config overlays needed | Multiple configs need NAS tuning | Create overlay configs | 2h | LOW | ***REMOVED***368 |

---

***REMOVED******REMOVED*** Historical Items (From Previous Reviews)

The following items from the 2026-01-01 review may still be relevant:

| ID | Priority | Title | Status |
|----|----------|-------|--------|
| SEC-001 | P0 | Hardcoded secrets in git | Check if resolved |
| SEC-002 | P0 | Database default credentials | Check if resolved |
| SEC-005 | P1 | Database TLS disabled | Check if resolved |
| PERF-001 | P1 | Loki retention workers (150) | Check if resolved - Loki now 168h |
| MAINT-001 | P2 | Backend tests failing | Still an issue (***REMOVED***366) |

---

***REMOVED******REMOVED*** Filtering Views

***REMOVED******REMOVED******REMOVED*** By Priority

**P0 - Critical (2 items):**
- ***REMOVED***359: Grafana anonymous admin
- ***REMOVED***360: OTLP port exposure

**P1 - High (1 item):**
- ***REMOVED***361: NAS restart policy

**P2 - Medium (6 items):**
- ***REMOVED***362: Alloy admin UI
- ***REMOVED***363: Tempo workers
- ***REMOVED***364: Scrape interval
- ***REMOVED***365: Docs drift
- ***REMOVED***366: Backend tests
- ***REMOVED***367: NAS Grafana auth

**P3 - Low (1 item):**
- ***REMOVED***368: NAS config overlays

***REMOVED******REMOVED******REMOVED*** By Area

**Security (4 items):**
- ***REMOVED***359, ***REMOVED***360, ***REMOVED***362, ***REMOVED***367

**Operations (2 items):**
- ***REMOVED***361, ***REMOVED***368

**Performance (2 items):**
- ***REMOVED***363, ***REMOVED***364

**Documentation (1 item):**
- ***REMOVED***365

**Testing (1 item):**
- ***REMOVED***366

***REMOVED******REMOVED******REMOVED*** By Effort

**Quick Wins (< 30 min):**
- ***REMOVED***361 (15m) - Change restart policy
- ***REMOVED***362 (15m) - Bind Alloy port
- ***REMOVED***363 (15m) - Reduce Tempo workers
- ***REMOVED***364 (15m) - Increase scrape interval
- ***REMOVED***367 (15m) - Add Grafana auth vars

**Small (30 min - 1h):**
- ***REMOVED***359 (30m) - Disable anonymous access
- ***REMOVED***365 (30m) - Update docs
- ***REMOVED***366 (30m) - Fix npm/vitest

**Medium (1-2h):**
- ***REMOVED***360 (1h) - OTLP port binding
- ***REMOVED***368 (2h) - Config overlays

---

***REMOVED******REMOVED*** Sprint Planning

***REMOVED******REMOVED******REMOVED*** Sprint 1 (This Week) - Critical Security
Focus: Fix P0 and P1 issues

- [ ] ***REMOVED***359 - Grafana anonymous access (30m)
- [ ] ***REMOVED***360 - OTLP port exposure (1h)
- [ ] ***REMOVED***361 - NAS restart policy (15m)

**Total: ~2 hours**

***REMOVED******REMOVED******REMOVED*** Sprint 2 (Next Week) - NAS Hardening
Focus: Security and performance tuning

- [ ] ***REMOVED***362 - Alloy admin UI (15m)
- [ ] ***REMOVED***363 - Tempo workers (15m)
- [ ] ***REMOVED***364 - Scrape interval (15m)
- [ ] ***REMOVED***367 - NAS Grafana auth (15m)
- [ ] ***REMOVED***366 - Backend tests (30m)

**Total: ~1.5 hours**

***REMOVED******REMOVED******REMOVED*** Sprint 3 (Following) - Documentation
Focus: Cleanup and documentation

- [ ] ***REMOVED***365 - Express vs Fastify docs (30m)
- [ ] ***REMOVED***368 - NAS config overlays (2h)

**Total: ~2.5 hours**

---

***REMOVED******REMOVED*** Related Pre-Existing Issues

| Issue | Title | Priority |
|-------|-------|----------|
| ***REMOVED***238 | Backend authentication and authorization | P0 |
| ***REMOVED***239 | Production observability: alerting and SLOs | P0 |
| ***REMOVED***240 | PostgreSQL backup and disaster recovery | P0 |
| ***REMOVED***241 | Environment separation: dev/staging/production | P0 |
| ***REMOVED***244 | Production TLS/SSL configuration | P0 |
| ***REMOVED***245 | Backend integration tests and E2E suite | P1 |

---

***REMOVED******REMOVED*** Definition of Done

For each item to be considered complete:

1. **Code/Config Change** implemented
2. **Tests** pass (for code changes)
3. **Documentation** updated (if behavior changes)
4. **Deployed** to NAS environment (for infra changes)
5. **Verified** via manual or automated check
6. **PR** merged with review approval
7. **GitHub Issue** closed

---

*Backlog updated from 2026-01-05 automated review. Priorities may need adjustment based on business context.*
