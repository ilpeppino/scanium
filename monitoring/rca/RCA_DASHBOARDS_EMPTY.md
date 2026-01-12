***REMOVED*** RCA: Empty Backend and Mobile Dashboards

**Incident:** Backend and Mobile dashboards showing no data in Grafana
**Severity:** High (blocks release)
**Date:** 2026-01-12
**Engineer:** Claude (Observability Agent)

---

***REMOVED******REMOVED*** Executive Summary

Backend and mobile dashboards are empty due to **network isolation between backend and monitoring stack**, causing metrics scraping to fail, plus **docker log collection errors** preventing log ingestion. Mobile dashboards are also empty due to absence of mobile telemetry ingestion.

---

***REMOVED******REMOVED*** Symptoms

1. **Backend Dashboards Empty:**
   - `backend-health.json` panels show no data
   - `backend-api-performance.json` panels show no data
   - `backend-errors.json` panels show no data

2. **Mobile Dashboards Empty:**
   - `mobile-app-health.json` panels show no data

3. **Grafana Health:** ✅ OK (10.3.1, database OK)

4. **All LGTM containers:** ✅ Healthy (Loki, Mimir, Tempo, Alloy)

---

***REMOVED******REMOVED*** Timeline

- **2026-01-09 16:52:** Loki data directory last updated (initial deployment)
- **2026-01-11 11:36:** Alloy data directory last updated
- **2026-01-12 11:00:** Backend container restarted (Up 8 hours)
- **2026-01-12 19:30:** Investigation started, repo alignment completed
- **2026-01-12 19:33:** Metrics pipeline proven broken (up=0, 0 samples)
- **2026-01-12 19:35:** RCA completed

---

***REMOVED******REMOVED*** Evidence

***REMOVED******REMOVED******REMOVED*** 1. Metrics Pipeline (Backend → Mimir)

***REMOVED******REMOVED******REMOVED******REMOVED*** Query: `up` metric for all targets
```bash
curl 'http://127.0.0.1:9009/prometheus/api/v1/query?query=up'
```

**Result:**
| Target | Job | Instance | Status |
|--------|-----|----------|--------|
| scanium-backend | scanium-backend | scanium-backend | **0 (DOWN)** ❌ |
| alloy | alloy | scanium-alloy | 1 (UP) ✅ |
| loki | loki | scanium-loki | 1 (UP) ✅ |
| mimir | mimir | scanium-mimir | 1 (UP) ✅ |
| tempo | tempo | scanium-tempo | 1 (UP) ✅ |

***REMOVED******REMOVED******REMOVED******REMOVED*** Query: Backend scrape metadata
```bash
curl 'http://127.0.0.1:9009/prometheus/api/v1/query?query={job="scanium-backend"}'
```

**Result:**
- `scrape_samples_scraped`: **0** (no samples collected!) ❌
- `scrape_samples_post_metric_relabeling`: **0**
- `scrape_series_added`: **0**
- `scrape_duration_seconds`: 0.037s (scrape attempt succeeds, but gets nothing)

***REMOVED******REMOVED******REMOVED******REMOVED*** Verification: Backend /metrics endpoint
```bash
curl http://172.21.0.3:8080/metrics
```

**Result:** ✅ **Backend metrics ARE available!**
```
***REMOVED*** HELP scanium_process_cpu_user_seconds_total Total user CPU time spent in seconds.
***REMOVED*** TYPE scanium_process_cpu_user_seconds_total counter
scanium_process_cpu_user_seconds_total 103.065
...
(many more metrics)
```

***REMOVED******REMOVED******REMOVED*** 2. Logs Pipeline (Backend → Loki)

***REMOVED******REMOVED******REMOVED******REMOVED*** Query: Loki labels
```bash
curl 'http://127.0.0.1:3100/loki/api/v1/labels'
```

**Result:**
```json
{"status":"success","data":["env","source"]}
```

***REMOVED******REMOVED******REMOVED******REMOVED*** Query: Source label values
```bash
curl 'http://127.0.0.1:3100/loki/api/v1/label/source/values'
```

**Result:**
```json
{"status":"success","data":["docker"]}
```

❌ **Expected:** `source=scanium-backend` (per alloy.hcl line 131)
❌ **Actual:** `source=docker` (default docker driver label, not our custom label)

***REMOVED******REMOVED******REMOVED******REMOVED*** Query: Backend logs
```bash
curl 'http://127.0.0.1:3100/loki/api/v1/query' --data-urlencode 'query={source="scanium-backend"}' --data-urlencode 'limit=5'
```

**Result:**
```json
{"result":[],"stats":{"totalLinesProcessed":0}}
```

**No backend logs found!** ❌

***REMOVED******REMOVED******REMOVED******REMOVED*** Alloy Docker Log Collection Errors
```
ts=2026-01-12T19:32:49.838726325Z level=error
  msg="could not set up a wait request to the Docker client"
  component_id=loki.source.docker.backend
  error="context canceled"

ts=2026-01-12T19:32:49.839163935Z level=warn
  msg="could not transfer logs"
  component_id=loki.source.docker.backend
  written=0
  err="context canceled"
```

**Docker log collection is failing continuously.**

***REMOVED******REMOVED******REMOVED*** 3. Network Topology Analysis

From `monitoring/rca/runtime-inventory.md`:

| Container | Networks |
|-----------|----------|
| **scanium-backend** | `compose_scanium_net` (172.21.0.0/16) only |
| **scanium-alloy** | `backend_scanium-network` (172.23.0.0/16) + `scanium-observability` (172.25.0.0/16) |
| **scanium-grafana** | `backend_scanium-network` (172.23.0.0/16) + `scanium-observability` (172.25.0.0/16) |
| **scanium-loki** | `scanium-observability` (172.25.0.0/16) only |
| **scanium-mimir** | `scanium-observability` (172.25.0.0/16) only |
| **scanium-tempo** | `scanium-observability` (172.25.0.0/16) only |

**CRITICAL FINDING:** Backend and Alloy are on **different, non-overlapping networks!**

- Backend: `compose_scanium_net` (managed by backend compose)
- Alloy: `backend_scanium-network` (managed by monitoring compose)

**Alloy config (alloy.hcl:283):**
```hcl
prometheus.scrape "backend" {
  targets = [{
    __address__ = "scanium-backend:8080"  // ❌ Hostname unresolvable from Alloy!
  }]
}
```

***REMOVED******REMOVED******REMOVED*** 4. Dashboard Query Requirements

***REMOVED******REMOVED******REMOVED******REMOVED*** Backend Dashboards (`backend-health.json`, `backend-api-performance.json`)

**Metrics Queries:**
- `scanium_http_requests_total{service_name=~"$service", env=~"$env"}`
- `scanium_http_request_duration_ms_bucket{service_name=~"$service", env=~"$env"}`

**Expected Labels:**
- `service_name` (from scraping)
- `env`

**Logs Queries:**
- `{source="scanium-backend", env=~"$env", level=~"ERROR|WARN"} | json`

**Expected Labels:**
- `source="scanium-backend"`
- `env`
- `level` (extracted from log JSON)

***REMOVED******REMOVED******REMOVED******REMOVED*** Mobile Dashboards (`mobile-app-health.json`)

**Logs Queries:**
- `{source="scanium-mobile", platform=~"$platform", build_type=~"$build_type", app_version=~"$app_version"}`

**Expected Labels:**
- `source="scanium-mobile"`
- `platform` (android/ios)
- `build_type` (debug/release)
- `app_version`
- `event_name`

**Current State:** ❌ No mobile telemetry exists in Loki at all.

---

***REMOVED******REMOVED*** Root Causes

***REMOVED******REMOVED******REMOVED*** RC1: Network Isolation (Backend Metrics)

**Problem:** Backend container is on `compose_scanium_net`, Alloy is on `backend_scanium-network`. These are separate Docker networks with no connectivity.

**Impact:**
- Alloy cannot resolve hostname `scanium-backend:8080`
- Prometheus scraping fails (up=0, 0 samples)
- All backend metric panels are empty

**Why it happened:**
- Backend is deployed via `deploy/nas/compose/docker-compose.nas.backend.yml` (separate compose project)
- Monitoring is deployed via `monitoring/docker-compose.yml` (separate compose project)
- The `backend_scanium-network` was created by monitoring compose, but backend never joined it
- Backend remains on its own `compose_scanium_net`

***REMOVED******REMOVED******REMOVED*** RC2: Docker Log Collection Failure (Backend Logs)

**Problem:** Alloy's `loki.source.docker.backend` component is experiencing "context canceled" errors when trying to collect logs from Docker containers.

**Impact:**
- Docker logs are not being forwarded to Loki
- `source="scanium-backend"` label never gets applied
- Only internal Loki logs exist (with `source="docker"`)
- All backend log panels are empty

**Suspected Causes:**
- Healthcheck causing container restarts/signals
- Docker API client timeout issues
- Missing relabeling causing label mismatch

***REMOVED******REMOVED******REMOVED*** RC3: No Mobile Telemetry Ingestion (Mobile Dashboards)

**Problem:** No mobile telemetry is currently being ingested to Loki.

**Impact:**
- All mobile dashboard panels are empty
- Cannot validate dashboard correctness

**Why:**
- Mobile app OTLP ingestion via Alloy HTTP (port 4318) is configured but no mobile app is actively sending telemetry during testing

---

***REMOVED******REMOVED*** Contributing Factors

1. **No E2E Tests:** No automated validation that dashboards show data after deployment
2. **No Smoke Tests:** No synthetic telemetry generation for deterministic validation
3. **Network Complexity:** Backend and monitoring stacks in separate compose projects with implicit network assumptions
4. **No Rollback Verification:** Rollback script doesn't validate monitoring works after rollback

---

***REMOVED******REMOVED*** Corrective Actions

***REMOVED******REMOVED******REMOVED*** Fix 1: Connect Backend to Alloy Network

**Action:** Add backend to `backend_scanium-network` in the backend compose file.

**Files:**
- `deploy/nas/compose/docker-compose.nas.backend.yml`

**Change:**
```yaml
services:
  backend:
    networks:
      - scanium_net
      - scanium-network  ***REMOVED*** ADD THIS

networks:
  scanium_net:
    driver: bridge
  scanium-network:     ***REMOVED*** ADD THIS
    external: true     ***REMOVED*** Reference the monitoring network
    name: backend_scanium-network
```

***REMOVED******REMOVED******REMOVED*** Fix 2: Fix Docker Log Collection (Troubleshooting Required)

**Immediate Actions:**
1. Verify Alloy healthcheck isn't causing signal interrupts
2. Add explicit relabeling in `loki.source.docker.backend` to ensure labels are applied
3. If docker scraping remains unstable, switch to OTLP logging from backend instead

**Alternative:** Backend emits OTLP logs to Alloy HTTP endpoint (more reliable than docker log scraping).

***REMOVED******REMOVED******REMOVED*** Fix 3: Add Synthetic Mobile Telemetry for Testing

**Action:** Create `scripts/monitoring/emit-mobile-test-log.sh` that pushes a test log entry to Loki with mobile labels.

**Purpose:** Deterministic E2E validation without requiring live mobile app.

***REMOVED******REMOVED******REMOVED*** Fix 4: Dashboard UID/Datasource Verification

**Action:** Verify all dashboards reference correct datasource UIDs (MIMIR for metrics, LOKI for logs).

***REMOVED******REMOVED******REMOVED*** Fix 5: Image Fallback for Grafana

**Action:** Create `monitoring/grafana/Dockerfile` that bakes in dashboards/provisioning so rollback doesn't lose config.

---

***REMOVED******REMOVED*** Preventative Measures

***REMOVED******REMOVED******REMOVED*** Test 1: Metrics Pipeline E2E (`scripts/monitoring/prove-metrics.sh`)
- Generate backend traffic (200, 400, 404, 500 responses)
- Query Mimir: `up{job="scanium-backend"}` must be 1
- Query Mimir: `scanium_http_requests_total` must increase

***REMOVED******REMOVED******REMOVED*** Test 2: Logs Pipeline E2E (`scripts/monitoring/prove-logs.sh`)
- Trigger backend logs (via API calls)
- Query Loki: `{source="scanium-backend"}` must return entries

***REMOVED******REMOVED******REMOVED*** Test 3: Mobile Dashboard Wiring (`scripts/monitoring/prove-mobile-dashboard-wiring.sh`)
- Run `emit-mobile-test-log.sh`
- Query Loki: `{source="scanium-mobile"}` must return test entry

***REMOVED******REMOVED******REMOVED*** Test 4: Grafana Dashboards (`scripts/monitoring/prove-grafana-dashboards.sh`)
- Use Grafana API to list dashboards
- For target dashboards, verify datasource UIDs exist and are reachable

***REMOVED******REMOVED******REMOVED*** Test 5: Remote Access (`scripts/monitoring/prove-remote-access.sh`)
- Curl `https://grafana.gtemp1.com/api/health`
- Verify NO 502

***REMOVED******REMOVED******REMOVED*** Test 6: E2E Runner (`scripts/monitoring/e2e-monitoring.sh`)
- Runs all proof scripts
- Exits non-zero on any failure
- Used in rollback validation

---

***REMOVED******REMOVED*** Success Criteria

- [ ] Backend dashboards show metrics (request rate, latency, errors)
- [ ] Backend dashboards show logs (ERROR/WARN events)
- [ ] Mobile dashboards show "No telemetry" OR test log entry (proves wiring)
- [ ] `e2e-monitoring.sh` passes on NAS
- [ ] `grafana.gtemp1.com` returns non-502
- [ ] Rollback to tagged release passes `e2e-monitoring.sh`

---

***REMOVED******REMOVED*** Next Steps

1. **PHASE 3:** Implement fixes (network, logs, dashboards, image fallback)
2. **PHASE 4:** Implement E2E tests and proof scripts
3. **PHASE 5:** Deploy and verify locally + remotely
4. **PHASE 6:** Commit, tag, test rollback

---

***REMOVED******REMOVED*** Appendix: Useful Commands

***REMOVED******REMOVED******REMOVED*** Check Mimir metrics
```bash
ssh nas "curl -sf 'http://127.0.0.1:9009/prometheus/api/v1/query?query=up'"
```

***REMOVED******REMOVED******REMOVED*** Check Loki logs
```bash
ssh nas 'curl -G -s "http://127.0.0.1:3100/loki/api/v1/query" --data-urlencode "query={source=\"scanium-backend\"}" --data-urlencode "limit=5"'
```

***REMOVED******REMOVED******REMOVED*** Check Alloy logs
```bash
ssh nas "/usr/local/bin/docker logs scanium-alloy 2>&1 | tail -50"
```

***REMOVED******REMOVED******REMOVED*** Restart monitoring stack
```bash
ssh nas "cd /volume1/docker/scanium/repo/monitoring && docker-compose down && docker-compose up -d"
```
