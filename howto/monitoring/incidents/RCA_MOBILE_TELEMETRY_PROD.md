***REMOVED*** RCA: Mobile Telemetry Production Implementation
**Date:** 2026-01-12
**Dashboard:** Scanium - Mobile App Health (uid: scanium-mobile-app-health)
**Status:** INVESTIGATION → IMPLEMENTATION

***REMOVED******REMOVED*** Dashboard Requirements Analysis

***REMOVED******REMOVED******REMOVED*** Expected Loki Log Labels
All mobile telemetry logs MUST have these labels:
- **source:** `scanium-mobile` (REQUIRED - primary filter)
- **platform:** `android` | `ios` (REQUIRED - template variable)
- **build_type:** `dev` | `beta` | `prod` (REQUIRED - template variable, note: dashboard calls it "build_type" not "flavor")
- **app_version:** String (e.g., "1.2.3") (REQUIRED - dynamic template variable)
- **event_name:** Event type (REQUIRED - used in aggregations)
- **session_id:** UUID-v4 (OPTIONAL - used with | json parser for session counting)

***REMOVED******REMOVED******REMOVED*** Expected Event Names (Catalog)
The dashboard expects these event_name values:
1. `app_launch` - App opened
2. `scan_started` - User initiated scan
3. `scan_completed` - Scan finished successfully
4. `assist_clicked` - AI assistant opened
5. `share_started` - User initiated share/export
6. `error_shown` - Error displayed to user
7. `crash_marker` - App crash detected

***REMOVED******REMOVED******REMOVED*** Dashboard Panels and Queries

| Panel ID | Title | Type | Query Pattern |
|----------|-------|------|---------------|
| 1 | Mobile Telemetry Overview | Text | Static markdown |
| 2 | Event Rate by Type | Timeseries | `sum by (event_name) (count_over_time({source="scanium-mobile", platform=~"$platform", build_type=~"$build_type", app_version=~"$app_version"} [1m]))` |
| 3 | Platform Distribution | Piechart | `sum by (platform) (count_over_time({source="scanium-mobile", build_type=~"$build_type", app_version=~"$app_version"} [1h]))` |
| 4 | Version Adoption | Piechart | `sum by (app_version) (count_over_time({source="scanium-mobile", platform=~"$platform", build_type=~"$build_type"} [1h]))` |
| 5 | Scan Funnel | Timeseries | Separate queries for `event_name="scan_started"` and `event_name="scan_completed"` |
| 6 | Scan Success Rate | Stat | `scan_completed [1h] / scan_started [1h]` |
| 7 | AI Assistant Usage | Stat | `count_over_time({... event_name="assist_clicked"} [1h])` |
| 8 | Error Rate | Timeseries | `event_name="error_shown"` and `event_name="crash_marker"` |
| 9 | Session Activity | Stat | `count(count by (session_id) (...  json  ... [1h]))` |
| 10 | Recent Events | Logs | `{source="scanium-mobile", ...}` |

***REMOVED******REMOVED******REMOVED*** Datasource Configuration
- **Type:** Loki
- **UID:** `LOKI` (must match provisioned datasource)

***REMOVED******REMOVED******REMOVED*** Key Technical Observations
1. **Label-based filtering:** All queries use label matchers, NOT field extraction (except session_id)
2. **JSON parsing:** Panel 9 (Session Activity) uses `| json` to extract `session_id` from log body
3. **No Mimir queries:** Dashboard is 100% LogQL (Loki), no PromQL metrics queries
4. **Template variables:** Dynamic filtering by platform, build_type, app_version
5. **Time ranges:** Queries use [1m], [5m], [1h] windows

***REMOVED******REMOVED*** Symptom

Dashboard "Scanium - Mobile App Health" exists but shows no data.

***REMOVED******REMOVED*** Phase 1 Test Results

***REMOVED******REMOVED******REMOVED*** Test 1: Loki Label Check
```bash
ssh nas "curl -sf 'http://127.0.0.1:3100/loki/api/v1/labels'"
```

**Expected:** If mobile telemetry exists, should see labels: `source`, `platform`, `build_type`, `app_version`, `event_name`, `session_id`

**Result:** ✅ Labels exist: `["app_version","build_type","env","event_name","exporter","job","level","platform","source"]`

**Analysis:** Schema is known to Loki (labels exist), but this doesn't mean data exists.

***REMOVED******REMOVED******REMOVED*** Test 2: Loki Query for Mobile Events
```bash
ssh nas "curl -G -s 'http://127.0.0.1:3100/loki/api/v1/query' --data-urlencode 'query={source=\"scanium-mobile\"}' | jq"
```

**Expected:** If mobile telemetry exists, should return log streams

**Result:** ❌ EMPTY - `"result":[]`, `totalLinesProcessed: 0`

**Analysis:** NO mobile telemetry logs exist in Loki with `source="scanium-mobile"`.

***REMOVED******REMOVED******REMOVED*** Test 3: Mimir Metrics Check (if applicable)
```bash
ssh nas "curl -sf 'http://127.0.0.1:9009/prometheus/api/v1/query?query=scanium_mobile_events_total' | jq"
```

**Note:** Dashboard doesn't query Mimir, but we may add metrics for additional observability.

**Result:** ❌ EMPTY - `"result":[]`

**Analysis:** NO mobile metrics exist in Mimir.

***REMOVED******REMOVED*** Root Cause

**CONFIRMED:** No telemetry ingestion path exists.

1. Dashboard exists and expects `source="scanium-mobile"` logs with specific labels
2. Loki schema includes mobile labels (from other log sources or previous attempts)
3. Zero mobile telemetry logs exist in Loki
4. Zero mobile metrics exist in Mimir
5. No backend endpoint exists to receive mobile events

**Conclusion:** Mobile app has no way to send events, and backend has no endpoint to receive them. Dashboard is orphaned.

***REMOVED******REMOVED*** Implementation Plan

***REMOVED******REMOVED******REMOVED*** Phase 2: Backend Endpoint
1. Add `POST /v1/mobile/events` endpoint
2. Schema validation (strict, allowlist-based)
3. Rate limiting (60 req/min per IP)
4. Emit structured logs to Loki with correct labels
5. Add Mimir metrics (optional enhancement)

***REMOVED******REMOVED******REMOVED*** Phase 3: Dashboard Updates
1. Verify datasource UID matches `LOKI`
2. Add telemetry status panel
3. Validate all queries work with new log structure

***REMOVED******REMOVED******REMOVED*** Phase 4: Synthetic Data & Proof
1. Create `generate-mobile-events.sh`
2. Run synthetic events through backend
3. Verify Loki receives logs with correct labels
4. Verify dashboard displays data

***REMOVED******REMOVED******REMOVED*** Phase 5: Regression Tests
1. Create `prove-mobile-telemetry.sh`
2. Integrate into `e2e-monitoring.sh`
3. Verify persistence across restarts

***REMOVED******REMOVED******REMOVED*** Phase 6: Deployment & Validation
1. Deploy to NAS
2. Test locally via ssh
3. Test remotely via grafana.gtemp1.com

***REMOVED******REMOVED*** Implementation Summary

***REMOVED******REMOVED******REMOVED*** Commits
1. **6a2d4d2** - `feat(telemetry): add prod-ready mobile events endpoint + fix Mobile App Health dashboard + e2e tests`
   - Added batch event support to `/v1/telemetry/mobile`
   - Implemented strict validation, rate limiting, and Prometheus metrics
   - Updated dashboard with Telemetry Status panel
   - Added test scripts: `generate-mobile-events.sh`, `prove-mobile-telemetry.sh`

2. **d6d5f17** - `fix(telemetry): switch mobile events to Pino OTLP transport + add backend log processor`
   - Fixed unreliable Docker log scraping by switching to Pino OTLP transport
   - Added `otelcol.processor.attributes.backend_logs` to extract mobile labels
   - Improved reliability: Backend → Pino OTLP → Alloy:4319 → Loki

***REMOVED******REMOVED******REMOVED*** Architecture (Final)

```
Mobile App
    ↓ HTTPS POST /v1/telemetry/mobile
Backend API (validates, sanitizes)
    ↓ request.log.info() via Pino
Pino OTLP Transport
    ↓ OTLP HTTP :4319
Alloy (otelcol.receiver.otlp.backend_http)
    ↓ processor.batch.backend
    ↓ processor.attributes.backend_logs (extracts labels)
    ↓ exporter.loki.backend
Loki (source=scanium-mobile, event_name, platform, app_version, build_type, env)
    ↑ LogQL queries
Grafana Dashboard (Scanium - Mobile App Health)
```

***REMOVED******REMOVED******REMOVED*** Key Design Decisions

1. **OTLP over Docker Log Scraping**
   - Docker log scraping was unreliable (container lifecycle issues, old timestamps)
   - Pino OTLP transport provides native OpenTelemetry integration
   - More reliable, structured, and production-ready

2. **Label Extraction in Alloy**
   - Added `processor.attributes.backend_logs` to promote fields to Loki labels
   - Uses `loki.attribute.labels` mechanism to map OTLP attributes to Loki labels
   - All backend OTLP logs flow through this processor

3. **Batch Support**
   - Endpoint accepts 1-50 events per request
   - Reduces HTTP overhead for mobile clients
   - Timestamp validation prevents Loki rejections

***REMOVED******REMOVED*** Validation Results

***REMOVED******REMOVED******REMOVED*** Test 1: Backend Event Acceptance ✅
```bash
curl -X POST https://scanium.gtemp1.com/v1/telemetry/mobile \
  -H "Content-Type: application/json" \
  -d '{"events": [...]}'
```
**Result:** `{"accepted":10,"rejected":0}` - All events accepted

***REMOVED******REMOVED******REMOVED*** Test 2: Loki Ingestion ✅
```bash
curl -G -s 'http://localhost:3100/loki/api/v1/query_range' \
  --data-urlencode 'query={source="scanium-mobile"}' \
  --data-urlencode 'start=...' --data-urlencode 'end=...'
```
**Result:** `"totalLinesProcessed":78` - Logs successfully ingested

***REMOVED******REMOVED******REMOVED*** Test 3: Label Values ✅
```bash
curl -s 'http://localhost:3100/loki/api/v1/label/source/values'
```
**Result:** `["docker","scanium-backend","scanium-mobile"]` - Label correctly set

***REMOVED******REMOVED******REMOVED*** Test 4: Dashboard Access ✅
- Local: http://nas:3000/d/scanium-mobile-app-health
- Remote: https://grafana.gtemp1.com/d/scanium-mobile-app-health
**Result:** Dashboard loads, queries work, telemetry status shows "ACTIVE"

***REMOVED******REMOVED******REMOVED*** Test 5: Remote Grafana Health ✅
```bash
curl -sf "https://grafana.gtemp1.com/api/health"
```
**Result:** `{"database":"ok","version":"10.3.1"}` - Healthy

***REMOVED******REMOVED*** Success Criteria

- [x] Loki shows `{source="scanium-mobile"}` logs after event generation (78 lines)
- [x] Dashboard "Scanium - Mobile App Health" displays data in all panels
- [x] Dashboard accessible remotely via grafana.gtemp1.com
- [x] Configuration persists across container restarts (verified volumes)
- [ ] `prove-mobile-telemetry.sh` passes (script exists, not yet run)
- [ ] Prometheus metrics in Mimir (endpoint implemented, scraping not yet verified)

***REMOVED******REMOVED*** Outstanding Items

1. **Metrics Scraping**: Backend exposes `/metrics` endpoint with `scanium_mobile_events_total`, but Alloy scraping needs verification
2. **E2E Test**: Run `prove-mobile-telemetry.sh` to validate end-to-end pipeline
3. **Monitoring Release Tag**: Create git tag for known-good monitoring state

***REMOVED******REMOVED*** References

- Dashboard: `monitoring/grafana/dashboards/mobile-app-health.json`
- Runtime Inventory: `monitoring/rca/runtime-inventory-mobile.md`
- Backend Controller: `backend/src/modules/mobile-telemetry/controller.ts`
- Alloy Config: `monitoring/alloy/alloy.hcl`
- Implementation Commits: `6a2d4d2`, `d6d5f17`
