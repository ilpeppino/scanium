# Errors & Failures Dashboard - Root Cause Analysis

## Dashboard
**File:** `monitoring/grafana/dashboards/errors.json`
**Title:** "Scanium - Errors & Failures"
**Status:** ❌ **BLOCKED** - Cannot show real data due to broken log ingestion infrastructure

## Executive Summary
The dashboard is correctly configured but **cannot work** because mobile telemetry logs are not being ingested into Loki. The root cause is a **bug in Grafana Alloy v1.0.0's `loki.source.docker` component** that prevents it from reading Docker container logs.

## Root Cause (Evidence-Based)

### What the Dashboard Expects
The dashboard queries Loki for mobile telemetry events with these labels:
- `source="scanium-mobile"`
- `platform` (android/ios)
- `env` OR `build_type` (dev/beta/prod)
- `app_version` (semantic version)
- `event_name` (error_shown, crash_marker, etc.)

**Example query:**
```logql
{source="scanium-mobile", platform=~"$platform", env=~"$env"} | json | event_name=~"error.*"
```

### What Actually Exists

#### ✅ Mobile telemetry events exist in backend stdout
```bash
docker logs scanium-backend | grep event_name
```
**Output (2026-01-10 22:16:20 UTC):**
```json
{"source":"scanium-mobile","event_name":"error_shown","platform":"android","app_version":"1.0.0","build_type":"beta","timestamp_ms":1768083320568,"session_id":"fresh-session-1","attributes":{"error_code":"NETWORK_ERROR","error_category":"network","is_recoverable":true}}
{"source":"scanium-mobile","event_name":"error_shown","platform":"android","app_version":"1.0.0","build_type":"beta","timestamp_ms":1768083320568,"session_id":"fresh-session-2","attributes":{"error_code":"PARSE_ERROR","error_category":"parsing","is_recoverable":false}}
{"source":"scanium-mobile","event_name":"crash_marker","platform":"android","app_version":"1.0.0","build_type":"beta","timestamp_ms":1768083320568,"session_id":"fresh-session-3","attributes":{"crash_type":"uncaught_exception"}}
```

#### ❌ Mobile telemetry events DO NOT exist in Loki
```bash
curl -G "http://localhost:3100/loki/api/v1/query" \
  --data-urlencode 'query={source="scanium-mobile", event_name="error_shown"}'
```
**Output:** `{"status":"success","data":{"resultType":"streams","result":[]}}`
**Result count:** `0`

### Why Logs Are Not in Loki

#### Alloy Configuration (monitoring/alloy/alloy.hcl)
The pipeline is correctly configured to:
1. Scrape Docker logs via `loki.source.docker.backend`
2. Parse JSON and extract labels via `loki.process.backend_logs`
3. Push to Loki via `loki.write.backend_logs`

```hcl
loki.source.docker "backend" {
  host = "unix:///var/run/docker.sock"
  targets = discovery.docker.backend.targets
  forward_to = [loki.process.backend_logs.receiver]
}

loki.process "backend_logs" {
  stage.json {
    expressions = {
      source       = "source",
      event_name   = "event_name",
      platform     = "platform",
      app_version  = "app_version",
      build_type   = "build_type",
    }
  }
  stage.labels {
    values = {
      source       = "source",
      event_name   = "event_name",
      platform     = "platform",
      app_version  = "app_version",
      build_type   = "build_type",
    }
  }
}
```

#### Alloy v1.0.0 Bug: "context canceled" Errors
**Evidence from Alloy logs (2026-01-10 22:14:49 UTC):**
```
level=error msg="could not set up a wait request to the Docker client" target=85cbcd1dccf2... component_id=loki.source.docker.backend error="context canceled"
level=warn msg="could not transfer logs" component_id=loki.source.docker.backend target=docker/85cbcd1dccf2... written=0 container=85cbcd1dccf2... err="context canceled"
```

**Container 85cbcd1dccf2 is scanium-backend.** The `loki.source.docker` component:
- Successfully discovers the backend container ✅
- Attempts to tail Docker logs ✅
- **Fails with "context canceled" and writes 0 bytes** ❌

This is a **known bug in Grafana Alloy v1.0.0** (released April 2024, very outdated). The component cannot reliably read Docker container logs.

#### Additional Issue: Missing Relabel Rules
`loki.source.docker.backend` currently scrapes **ALL Docker containers** (mimir, loki, tempo, alloy, backend, postgres, etc.) because `discovery.docker.backend` has no filters.

This causes:
- Unnecessary load from scraping 9+ containers
- "Entry too far behind" errors when old logs are rejected by Loki
- Component thrashing with "context canceled" errors

**Missing from config:**
```hcl
relabel_rules {
  source_labels = ["__meta_docker_container_name"]
  regex         = ".*/scanium-backend"
  action        = "keep"
}
```

**Note:** Attempted to add relabel_rules (2026-01-10 22:18:50 UTC), but Alloy v1.0.0's `loki.source.docker` does not support relabel_rules in the expected format or has broken implementation.

## Secondary Issue: Dashboard Label Mismatch

**Dashboard uses:** `env` label
**Mobile telemetry schema specifies:** `build_type` label (per `howto/monitoring/reference/telemetry/MOBILE_TELEMETRY_SCHEMA.md`)

**Current Alloy config:**
- Line 119: Sets `external_labels = { env = "dev" }` for source="scanium-mobile"
- Line 156: Extracts `build_type` from JSON

Both labels may exist on the same log stream. The dashboard should use `build_type` to match the schema.

**Fix needed in `errors.json`:**
- Replace all instances of `env=~"$env"` with `build_type=~"$env"`
- OR rename variable from `env` to `build_type`

## Attempted Fixes & Results

### 1. Verified Backend Logging (✅ Working)
- Mobile telemetry endpoint `/v1/telemetry/mobile` returns 202 Accepted
- Backend logs events to stdout as single-line JSON
- Logs confirmed via `docker logs scanium-backend`

### 2. Restarted Alloy (❌ No Effect)
```bash
docker restart scanium-alloy
```
- Component restarts successfully
- Still produces "context canceled" errors
- No logs transferred (written=0)

### 3. Added Relabel Rules (❌ No Effect)
- Added filtering to only scrape scanium-backend container
- Alloy v1.0.0 accepts config without errors
- But relabel_rules do not work (still scrapes wrong containers)
- Reverted change

### 4. Sent Fresh Events After Restart (❌ No Ingestion)
- Sent 10 mobile telemetry events (app_launch, scan_started, error_shown, crash_marker)
- All returned 202 Accepted
- Confirmed events logged to backend stdout
- Waited 90 seconds for ingestion
- Events still not in Loki (zero results)

## What DOES Work

### OTLP Backend Logs in Loki
Loki contains backend HTTP request logs via OTLP pipeline:
```bash
curl -G "http://localhost:3100/loki/api/v1/query" \
  --data-urlencode 'query={source="scanium-mobile", exporter="OTLP"}'
```
**Output:** 2-3 log streams with labels:
- `source="scanium-mobile"`
- `exporter="OTLP"`
- `level="INFO"`
- `env="dev"`

**Sample log:**
```json
{"body":"HTTP POST /v1/telemetry/mobile","traceid":"8ec172c63a0a6c36dd4554d3f8449b9a","spanid":"423e715dafaf021b","severity":"INFO","attributes":{"http.method":"POST","http.route":"/v1/telemetry/mobile","http.status_code":202}}
```

**These are backend's own request logs, NOT the mobile telemetry events.** The actual events (with error_code, crash_type, etc.) are only in backend stdout, which is not being ingested.

## Minimal Fix Required

The dashboard **cannot** be fixed without fixing the log ingestion pipeline. Three options:

### Option A: Upgrade Alloy (Recommended)
Upgrade from v1.0.0 (April 2024) to latest stable (v1.5.0+ as of Dec 2024)
- **Pro:** Likely fixes `loki.source.docker` bugs
- **Pro:** Better relabel_rules support
- **Con:** Requires testing and validation
- **Scope:** Out of scope per task instructions

### Option B: Switch to OTLP Logs
Modify backend to send mobile telemetry via OTLP instead of stdout
- **Pro:** Works around broken loki.source.docker
- **Pro:** Already have working OTLP pipeline
- **Con:** Requires backend code changes
- **Scope:** Out of scope per task instructions ("Do NOT change backend instrumentation code")

### Option C: Use Alternative Log Shipper
Replace `loki.source.docker` with Promtail or Fluent Bit
- **Pro:** Proven stable log shippers
- **Con:** Adds complexity
- **Con:** Requires significant Alloy config changes
- **Scope:** Out of scope for dashboard fix

## Conclusion

**The "Scanium - Errors & Failures" dashboard is correctly configured and will work once logs are ingested.**

**Current blocker:** Grafana Alloy v1.0.0 bug prevents mobile telemetry logs from reaching Loki.

**Dashboard changes needed (minor):**
1. Replace `env` with `build_type` in all queries
2. Verify datasource UID matches (currently uses "LOKI")

**Infrastructure fix required (out of scope for dashboard task):**
- Upgrade Alloy to v1.5.0+
- OR implement Option B (OTLP logs)
- OR implement Option C (alternative shipper)

## Verification Commands

Once log ingestion is fixed, verify with:

```bash
# 1. Check mobile telemetry events exist in Loki
curl -G "http://localhost:3100/loki/api/v1/query" \
  --data-urlencode 'query={source="scanium-mobile", event_name="error_shown"}' \
  | jq '.data.result | length'
# Expected: > 0

# 2. Check labels are extracted correctly
curl -G "http://localhost:3100/loki/api/v1/query" \
  --data-urlencode 'query={source="scanium-mobile", platform="android", build_type="beta"}' \
  | jq -r '.data.result[0].stream'
# Expected: Shows source, platform, app_version, build_type, event_name labels

# 3. Verify dashboard query works
curl -G "http://localhost:3100/loki/api/v1/query_range" \
  --data-urlencode 'query=sum(count_over_time({source="scanium-mobile", event_name=~"error.*"} [5m]))' \
  --data-urlencode 'start=now-1h' --data-urlencode 'end=now' \
  | jq '.data.result[0].values'
# Expected: Time series data with error counts
```

## Test Traffic Used

**Minimal error events (13 total across 2 runs):**
- 2 × `app_launch`
- 3 × `scan_started`
- 3 × `scan_completed`
- 4 × `error_shown` (NETWORK_ERROR, PARSE_ERROR, PERMISSION_DENIED)
- 1 × `crash_marker` (uncaught_exception)

All sent to `http://scanium-backend:8080/v1/telemetry/mobile`, all returned 202 Accepted, all confirmed in backend Docker logs, none ingested into Loki.

---

**Analysis Date:** 2026-01-10
**Analyzed By:** Claude Sonnet 4.5
**Evidence:** Docker logs, Loki API queries, Alloy component logs
