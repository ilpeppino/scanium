# Repository Review Action Backlog

**Last Updated:** 2026-01-05
**Source:** [REPO_REVIEW_REPORT.md](REPO_REVIEW_REPORT.md)

---

## GitHub Issues Summary

| Priority | Count | Issues |
|----------|-------|--------|
| P0 | 2 | #359, #360 |
| P1 | 1 | #361 |
| P2 | 6 | #362, #363, #364, #365, #366, #367 |
| P3 | 1 | #368 |

---

## Prioritized Action Items

| ID | Priority | Area | Title | Evidence | Recommendation | Effort | Risk | Issue |
|----|----------|------|-------|----------|----------------|--------|------|-------|
| 1 | P0 | Security | OTLP ports exposed without auth | `monitoring/docker-compose.yml:14-16` | Bind to 127.0.0.1 or add auth | 1h | CRITICAL | #360 |
| 2 | P0 | Security | Grafana anonymous admin access | `monitoring/docker-compose.yml:103-108` | Disable anon auth, enable login | 30m | CRITICAL | #359 |
| 3 | P1 | Ops | NAS monitoring restart policy | `docker-compose.nas.monitoring.yml:5,29,39,49,62` | Change to `unless-stopped` | 15m | HIGH | #361 |
| 4 | P2 | Security | NAS Alloy admin UI exposed | `docker-compose.nas.monitoring.yml:68` | Bind port 12345 to localhost | 15m | MEDIUM | #362 |
| 5 | P2 | Performance | Tempo workers too high for NAS | `tempo/tempo.yaml:40` max_workers: 100 | Reduce to 4 | 15m | MEDIUM | #363 |
| 6 | P2 | Performance | Scrape interval too aggressive | `alloy/alloy.hcl:118` scrape_interval: 15s | Increase to 60s | 15m | MEDIUM | #364 |
| 7 | P2 | Docs | Architecture says Express.js | `ARCHITECTURE.md:274` | Update to Fastify | 30m | LOW | #365 |
| 8 | P2 | Testing | Backend tests not runnable | `npm test` fails - vitest not found | Fix npm dependencies | 30m | MEDIUM | #366 |
| 9 | P2 | Security | NAS Grafana auth missing | `docker-compose.nas.monitoring.yml` | Add GF_AUTH_* env vars | 15m | MEDIUM | #367 |
| 10 | P3 | Ops | NAS config overlays needed | Multiple configs need NAS tuning | Create overlay configs | 2h | LOW | #368 |

---

## Historical Items (From Previous Reviews)

The following items from the 2026-01-01 review may still be relevant:

| ID | Priority | Title | Status |
|----|----------|-------|--------|
| SEC-001 | P0 | Hardcoded secrets in git | Check if resolved |
| SEC-002 | P0 | Database default credentials | Check if resolved |
| SEC-005 | P1 | Database TLS disabled | Check if resolved |
| PERF-001 | P1 | Loki retention workers (150) | Check if resolved - Loki now 168h |
| MAINT-001 | P2 | Backend tests failing | Still an issue (#366) |

---

## Filtering Views

### By Priority

**P0 - Critical (2 items):**
- #359: Grafana anonymous admin
- #360: OTLP port exposure

**P1 - High (1 item):**
- #361: NAS restart policy

**P2 - Medium (6 items):**
- #362: Alloy admin UI
- #363: Tempo workers
- #364: Scrape interval
- #365: Docs drift
- #366: Backend tests
- #367: NAS Grafana auth

**P3 - Low (1 item):**
- #368: NAS config overlays

### By Area

**Security (4 items):**
- #359, #360, #362, #367

**Operations (2 items):**
- #361, #368

**Performance (2 items):**
- #363, #364

**Documentation (1 item):**
- #365

**Testing (1 item):**
- #366

### By Effort

**Quick Wins (< 30 min):**
- #361 (15m) - Change restart policy
- #362 (15m) - Bind Alloy port
- #363 (15m) - Reduce Tempo workers
- #364 (15m) - Increase scrape interval
- #367 (15m) - Add Grafana auth vars

**Small (30 min - 1h):**
- #359 (30m) - Disable anonymous access
- #365 (30m) - Update docs
- #366 (30m) - Fix npm/vitest

**Medium (1-2h):**
- #360 (1h) - OTLP port binding
- #368 (2h) - Config overlays

---

## Sprint Planning

### Sprint 1 (This Week) - Critical Security
Focus: Fix P0 and P1 issues

- [ ] #359 - Grafana anonymous access (30m)
- [ ] #360 - OTLP port exposure (1h)
- [ ] #361 - NAS restart policy (15m)

**Total: ~2 hours**

### Sprint 2 (Next Week) - NAS Hardening
Focus: Security and performance tuning

- [ ] #362 - Alloy admin UI (15m)
- [ ] #363 - Tempo workers (15m)
- [ ] #364 - Scrape interval (15m)
- [ ] #367 - NAS Grafana auth (15m)
- [ ] #366 - Backend tests (30m)

**Total: ~1.5 hours**

### Sprint 3 (Following) - Documentation
Focus: Cleanup and documentation

- [ ] #365 - Express vs Fastify docs (30m)
- [ ] #368 - NAS config overlays (2h)

**Total: ~2.5 hours**

---

## Related Pre-Existing Issues

| Issue | Title | Priority |
|-------|-------|----------|
| #238 | Backend authentication and authorization | P0 |
| #239 | Production observability: alerting and SLOs | P0 |
| #240 | PostgreSQL backup and disaster recovery | P0 |
| #241 | Environment separation: dev/staging/production | P0 |
| #244 | Production TLS/SSL configuration | P0 |
| #245 | Backend integration tests and E2E suite | P1 |

---

## Definition of Done

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
