# No Data RCA Report - Grafana Dashboards

**Date:** 2026-01-14
**Investigator:** Claude (Observability Agent)
**Stack:** Grafana + Mimir + Loki + Tempo + Alloy

---

## Executive Summary

Five dashboards were investigated for "No data" issues. All root causes fall into **Category D (Data
not being ingested)** - the datasource wiring and queries are correct, but the underlying telemetry
data does not exist.

| Dashboard                       | Status  | Root Cause Category                       |
|---------------------------------|---------|-------------------------------------------|
| Scanium - Traces Drilldown      | No Data | D - Missing span metrics + Tempo issues   |
| Scanium - Errors & Failures     | No Data | D - Mobile telemetry not shipped          |
| Scanium - Mobile App Health     | No Data | D - Mobile telemetry not shipped          |
| Scanium - Performance & Latency | No Data | D - Missing mobile metrics + Tempo issues |
| Scanium - Ops Overview          | No Data | D - Mobile telemetry not shipped          |

**Fixes Applied:** None (all issues require instrumentation/infrastructure changes)
**GitHub Issues Created:** 4

- [#406](https://github.com/ilpeppino/scanium/issues/406) - Mobile telemetry not being shipped
- [#407](https://github.com/ilpeppino/scanium/issues/407) - ML inference latency metric not
  instrumented
- [#408](https://github.com/ilpeppino/scanium/issues/408) - Span metrics not configured in Alloy
- [#409](https://github.com/ilpeppino/scanium/issues/409) - Tempo permission errors

---

## Phase 1: Stack Health

### Container Status (NAS)

```
scanium-backend       Up 14 hours (healthy)
scanium-grafana       Up 14 hours (healthy)
scanium-alloy         Up 14 hours (unhealthy)
scanium-tempo         Up 14 hours (healthy)
scanium-mimir         Up 14 hours (healthy)
scanium-loki          Up 14 hours (healthy)
```

**Issues Found:**

- **Alloy (unhealthy):** Rejecting old log entries ("entry too far behind")
- **Tempo:** Permission denied errors during compaction

### Datasource UIDs (Verified Correct)

| Name  | UID   | Type       | URL                          |
|-------|-------|------------|------------------------------|
| Mimir | MIMIR | prometheus | http://mimir:9009/prometheus |
| Loki  | LOKI  | loki       | http://loki:3100             |
| Tempo | TEMPO | tempo      | http://tempo:3200            |

### Log Evidence

**Alloy Logs (unhealthy reason):**

```
level=error msg="final error sending batch" component=loki.write.backend_logs
status=400 error="entry with timestamp 2026-01-12 22:26:02 ignored,
reason: 'entry too far behind, oldest acceptable timestamp is: 2026-01-14T20:26:07Z'"
```

**Tempo Logs (compaction errors):**

```
level=error msg="error during compaction cycle"
err="error shipping block to backend, blockID b42d2deb-caf2-4b30-af87-0780f2d0d543:
error completing block: mkdir /var/tempo/blocks/single-tenant/...: permission denied"
```

---

## Phase 2: Dashboard Query Analysis

### 1. Scanium - Traces Drilldown (`traces-drilldown.json`)

| Panel                    | Datasource | Query Type | Signal Dependency                                                 |
|--------------------------|------------|------------|-------------------------------------------------------------------|
| Trace Count (5m)         | MIMIR      | PromQL     | `traces_spanmetrics_calls_total`                                  |
| Error Traces (5m)        | MIMIR      | PromQL     | `traces_spanmetrics_calls_total{status_code="STATUS_CODE_ERROR"}` |
| Avg Span Duration        | MIMIR      | PromQL     | `traces_spanmetrics_latency_*`                                    |
| p99 Span Duration        | MIMIR      | PromQL     | `traces_spanmetrics_latency_bucket`                               |
| Span Latency Over Time   | MIMIR      | PromQL     | `traces_spanmetrics_latency_bucket`                               |
| Slowest Spans (p99)      | MIMIR      | PromQL     | `traces_spanmetrics_latency_bucket`                               |
| Most Called Spans        | MIMIR      | PromQL     | `traces_spanmetrics_calls_total`                                  |
| Span Duration by Service | MIMIR      | PromQL     | `traces_spanmetrics_latency_bucket`                               |
| Error Rate by Span       | MIMIR      | PromQL     | `traces_spanmetrics_calls_total`                                  |
| Slow Traces              | TEMPO      | TraceQL    | `{resource.service.name =~ ".*" && duration > ${min_duration}ms}` |
| Error Traces             | TEMPO      | TraceQL    | `{status = error}`                                                |
| Service Dependency Graph | TEMPO      | serviceMap | n/a                                                               |
| Span Latency Heatmap     | MIMIR      | PromQL     | `traces_spanmetrics_latency_bucket`                               |

### 2. Scanium - Errors & Failures (`errors.json`)

| Panel                   | Datasource | Query Type | Signal Dependency                                               |
|-------------------------|------------|------------|-----------------------------------------------------------------|
| Overall Error Rate      | LOKI       | LogQL      | `{source="scanium-mobile", platform=~"$platform", env=~"$env"}` |
| Errors by App Version   | LOKI       | LogQL      | `{source="scanium-mobile"}                                      | json | event_name=~"error.*"` |
| Top Error Types (Trend) | LOKI       | LogQL      | `{source="scanium-mobile"}                                      | json | event_name=~"error.*"` |
| Recent Error Events     | LOKI       | LogQL      | `{source="scanium-mobile"}                                      | json | event_name=~"error.*"` |

### 3. Scanium - Mobile App Health (`mobile-app-health.json`)

| Panel                 | Datasource | Query Type | Signal Dependency                                        |
|-----------------------|------------|------------|----------------------------------------------------------|
| Telemetry Status      | LOKI       | LogQL      | `{source="scanium-mobile"}`                              |
| Event Rate by Type    | LOKI       | LogQL      | `{source="scanium-mobile", platform=~"$platform"}`       |
| Platform Distribution | LOKI       | LogQL      | `{source="scanium-mobile"}`                              |
| Version Adoption      | LOKI       | LogQL      | `{source="scanium-mobile"}`                              |
| Scan Funnel           | LOKI       | LogQL      | `{source="scanium-mobile", event_name="scan_started"}`   |
| Scan Success Rate     | LOKI       | LogQL      | `{source="scanium-mobile", event_name="scan_completed"}` |
| AI Assistant Usage    | LOKI       | LogQL      | `{source="scanium-mobile", event_name="assist_clicked"}` |
| Error Rate            | LOKI       | LogQL      | `{source="scanium-mobile", event_name="error_shown"}`    |
| Session Activity      | LOKI       | LogQL      | `{source="scanium-mobile"}                               | json` |
| Recent Events         | LOKI       | LogQL      | `{source="scanium-mobile"}`                              |

### 4. Scanium - Performance & Latency (`scan-performance.json`)

| Panel                           | Datasource | Query Type | Signal Dependency                                         |
|---------------------------------|------------|------------|-----------------------------------------------------------|
| Inference Latency (p50/p95/p99) | MIMIR      | PromQL     | `ml_inference_latency_ms_bucket{source="scanium-mobile"}` |
| Latency by App Version (p95)    | MIMIR      | PromQL     | `ml_inference_latency_ms_bucket{source="scanium-mobile"}` |
| Trace Count (Tempo)             | TEMPO      | TraceQL    | `{ span.http.method =~ "POST" }`                          |

### 5. Scanium - Ops Overview (`ops-overview.json`)

| Panel                   | Datasource | Query Type | Signal Dependency                                         |
|-------------------------|------------|------------|-----------------------------------------------------------|
| Sessions Started Rate   | LOKI       | LogQL      | `{source="scanium-mobile"}                                | json | event_name="scan.session_started"` |
| Error Rate              | LOKI       | LogQL      | `{source="scanium-mobile"}                                | json | event_name=~"error.*"` |
| Telemetry Drop Detector | LOKI       | LogQL      | `{source="scanium-mobile"}`                               |
| Inference Latency (p95) | MIMIR      | PromQL     | `ml_inference_latency_ms_bucket{source="scanium-mobile"}` |
| Top 10 Error Types      | LOKI       | LogQL      | `{source="scanium-mobile"}                                | json | event_name=~"error.*"` |

---

## Phase 3: Ground-Truth Data Existence

### Mimir (Metrics)

**Available metrics (scanium-related):**

```
scanium_http_requests_total
scanium_http_request_duration_ms_bucket
scanium_assistant_requests_total
scanium_assistant_request_latency_ms_bucket
scanium_classifier_requests_total
scanium_classifier_request_latency_ms_bucket
scanium_enrich_requests_total
scanium_enrich_total_latency_seconds_bucket
scanium_nodejs_*
scanium_process_*
```

**Missing metrics:**

- `traces_spanmetrics_*` - NOT FOUND (span metrics not configured)
- `ml_inference_latency_ms_bucket` - NOT FOUND (not instrumented)

**Label values:**

- `source`: `scanium-backend`, `pipeline`
- `env`: `dev`
- `job`: `scanium-backend`, `alloy`, `loki`, `mimir`, `tempo`

### Loki (Logs)

**Query:** `curl -s "http://127.0.0.1:3100/loki/api/v1/labels"`

**Available labels:**

```json
{"status":"success","data":["env","source"]}
```

**Label values:**

- `source`: `scanium-backend` ONLY
- `env`: `dev` ONLY

**Missing:**

- `source="scanium-mobile"` - NOT FOUND

### Tempo (Traces)

**Query:** `curl -s "http://127.0.0.1:3200/api/search/tag/resource.service.name/values"`

**Result:**

```json
{"tagValues":[],"metrics":{}}
```

**Status:** Tempo returns NO trace services. Compaction errors (permission denied) may be preventing
traces from becoming searchable.

---

## Phase 4: Root Cause Classification

### Root Cause A: Wrong datasource UID

**Status:** NOT FOUND - All datasource UIDs are correct (MIMIR, LOKI, TEMPO)

### Root Cause B: Wrong query language/syntax

**Status:** NOT FOUND - All queries are syntactically correct

### Root Cause C: Wrong metric name/label keys/variables

**Status:** NOT FOUND - Queries match expected schema, but data doesn't exist

### Root Cause D: Data not being ingested (FOUND)

| Signal                                            | Expected Source                   | Actual Status                                          |
|---------------------------------------------------|-----------------------------------|--------------------------------------------------------|
| Mobile logs (`source="scanium-mobile"`)           | Mobile app → OTLP → Alloy → Loki  | **NOT INGESTED** - App not sending telemetry           |
| Mobile metrics (`ml_inference_latency_ms_bucket`) | Mobile app → OTLP → Alloy → Mimir | **NOT INGESTED** - Metric not instrumented             |
| Span metrics (`traces_spanmetrics_*`)             | Alloy spanmetrics connector       | **NOT CONFIGURED** - No spanmetrics in Alloy           |
| Traces (Tempo searchable)                         | Backend → OTLP → Alloy → Tempo    | **PARTIALLY BROKEN** - Permission errors in compaction |

### Root Cause E: Time range/step/aggregation

**Status:** NOT FOUND

### Root Cause F: Expected no data

**Status:** PARTIAL - Mobile telemetry was designed but not yet implemented in app

---

## Phase 5: GitHub Issues Created

### Issue #406: Mobile telemetry not being shipped to LGTM stack

**Link:** https://github.com/ilpeppino/scanium/issues/406

**Affected Dashboards:**

- Scanium - Errors & Failures (all panels)
- Scanium - Mobile App Health (all panels)
- Scanium - Ops Overview (4/5 panels)

**Evidence:**

```bash
$ curl -s "http://127.0.0.1:3100/loki/api/v1/label/source/values"
{"status":"success","data":["scanium-backend"]}
# "scanium-mobile" is missing
```

**Root Cause:** The Android app is not instrumented to send telemetry to the Alloy OTLP endpoint.

**Proposed Fix:**

1. Implement OTLP telemetry in the Android app
2. Configure the app to send logs/metrics to `alloy:4318` (OTLP HTTP)
3. Add event tracking for: `app_launch`, `scan_started`, `scan_completed`, `assist_clicked`,
   `error_shown`, `crash_marker`

### Issue #407: ML inference latency metric not instrumented

**Link:** https://github.com/ilpeppino/scanium/issues/407

**Affected Dashboards:**

- Scanium - Performance & Latency (2/3 panels)
- Scanium - Ops Overview (1/5 panels)

**Evidence:**

```bash
$ curl -s "http://127.0.0.1:9009/prometheus/api/v1/query" --data-urlencode 'query=ml_inference_latency_ms_bucket'
{"status":"success","data":{"resultType":"vector","result":[]}}
# Metric does not exist
```

**Root Cause:** The `ml_inference_latency_ms_bucket` histogram metric is not being emitted by any
service.

**Proposed Fix:**

1. Instrument ML inference in the mobile app or backend
2. Emit histogram metric with buckets for latency distribution
3. Include labels: `source`, `env`, `platform`, `app_version`

### Issue #408: Span metrics not configured in Alloy

**Link:** https://github.com/ilpeppino/scanium/issues/408

**Affected Dashboards:**

- Scanium - Traces Drilldown (10/14 panels using `traces_spanmetrics_*`)

**Evidence:**

```bash
$ grep -i "spanmetrics" monitoring/alloy/config.alloy
# No results - spanmetrics connector not configured
```

**Root Cause:** Alloy is forwarding traces to Tempo but not generating span metrics.

**Proposed Fix:**

1. Add `otelcol.connector.spanmetrics` to Alloy config
2. Route traces through the connector before exporting to Tempo
3. Forward generated metrics to Mimir

### Issue #409: Tempo trace search broken (permission errors)

**Link:** https://github.com/ilpeppino/scanium/issues/409

**Affected Dashboards:**

- Scanium - Traces Drilldown (3/14 panels using Tempo directly)
- Scanium - Performance & Latency (1/3 panels)

**Evidence:**

```
level=error msg="error during compaction cycle"
err="mkdir /var/tempo/blocks/single-tenant/...: permission denied"
```

**Root Cause:** Tempo cannot create directories in `/var/tempo/blocks` due to permission issues,
preventing trace compaction and making traces unsearchable.

**Proposed Fix:**

1. Fix volume permissions: `chown -R 10001:10001 /path/to/tempo/data`
2. Or run Tempo as root (not recommended)
3. Or adjust docker-compose volume mapping

---

## Validation Checklist

| Item                       | Status               |
|----------------------------|----------------------|
| Datasource UIDs correct    | VERIFIED             |
| Query syntax correct       | VERIFIED             |
| Mobile logs exist in Loki  | NOT FOUND - Issue #1 |
| ML inference metric exists | NOT FOUND - Issue #2 |
| Span metrics exist         | NOT FOUND - Issue #3 |
| Tempo trace search works   | BROKEN - Issue #4    |

---

## Appendix: Alloy Pipeline Configuration

```
Mobile App → OTLP HTTP (4318) → otelcol.receiver.otlp "mobile_http"
                                        ↓
                              otelcol.processor.batch "mobile"
                                   ↓         ↓         ↓
                          logs→Loki   metrics→Mimir  traces→Tempo
                          (source=    (source=        (no spanmetrics)
                          scanium-    scanium-
                          mobile)     mobile)

Backend → OTLP HTTP (4319) → otelcol.receiver.otlp "backend_http"
                                        ↓
                              otelcol.processor.batch "backend"
                                   ↓         ↓         ↓
                          logs→Loki   metrics→Mimir  traces→Tempo

Backend → Docker logs → loki.source.docker "backend" → Loki
Backend → /metrics → prometheus.scrape "backend" → Mimir
```
