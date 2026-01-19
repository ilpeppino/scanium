***REMOVED*** Backend OTLP Instrumentation Investigation Report

**Date:** 2026-01-11
**Investigator:** Scanium Monitoring Agent
**Status:** Investigation Complete - Root Causes Identified

***REMOVED******REMOVED*** Executive Summary

Backend OTLP instrumentation IS working correctly for traces and logs. The issue is NOT with
instrumentation or data flow, but with:

1. Tempo metrics generator not enabled (requires overrides config)
2. Dashboards configured to use span metrics that don't exist yet
3. Local Mac repo out of sync with NAS (different Alloy config)

***REMOVED******REMOVED*** Investigation Findings

***REMOVED******REMOVED******REMOVED*** 1. OTLP Initialization - WORKING

**Evidence:**

```
Backend logs (scanium-backend):
📊 OpenTelemetry initialized: scanium-backend (production)
   Exporting to: http://alloy:4318
```

**Code Location:** `/Users/family/dev/scanium/backend/src/infra/telemetry/index.ts`

**Configuration:**

- Trace exporter: OTLPTraceExporter (HTTP)
- Metrics exporter: OTLPMetricExporter (HTTP) - 30s interval
- Log exporter: OTLPLogExporter (HTTP)
- Auto-instrumentation: HttpInstrumentation, FastifyInstrumentation
- Health/metrics endpoints excluded from tracing (line 78)

***REMOVED******REMOVED******REMOVED*** 2. Network Connectivity - WORKING

**Docker Networks:**

- Backend and Alloy both on `backend_scanium-network`
- DNS resolution: `alloy` resolves to `172.23.0.2` from backend container
- Alloy listening on ports 4317 (gRPC) and 4318 (HTTP)

**Container Status:**

```
scanium-backend         Up 13 minutes (healthy)
scanium-alloy           Up 15 minutes (healthy)
scanium-tempo           Up 11 hours (healthy)
scanium-loki            Up 11 hours (healthy)
scanium-mimir           Up 11 hours (healthy)
```

***REMOVED******REMOVED******REMOVED*** 3. Traces in Tempo - WORKING

**API Query Results:**

```bash
curl -s "http://localhost:3200/api/search?tags=service.name%3Dscanium-backend&limit=10"
```

**Response:**

```json
{
  "traces": [{
    "traceID": "fc1be8a9b56a5d6f5ed2f82f0a2be5c3",
    "rootServiceName": "scanium-backend",
    "rootTraceName": "HTTP GET /health",
    "startTimeUnixNano": "1768127943430000000",
    "durationMs": 244
  }],
  "metrics": {
    "inspectedTraces": 1,
    "inspectedBytes": "14443"
  }
}
```

**Available Tags:**

```json
["deployment.environment", "host.arch", "host.name", "http.method",
 "http.route", "http.status_code", "http.target", "process.command",
 "service.name", "telemetry.sdk.language", "telemetry.sdk.name"]
```

**Service Names:** `["scanium-backend"]`

***REMOVED******REMOVED******REMOVED*** 4. Logs in Loki (OTLP) - WORKING

**Query:**

```bash
curl -s "http://localhost:3100/loki/api/v1/query" -G \
  --data-urlencode "query={source=\"otlp\"}" \
  --data-urlencode "limit=10"
```

**Results:** 36 log entries in last 5 minutes

- Source label: `otlp`
- Levels: INFO, WARN
- Environment: `dev`
- Exporter: `OTLP`

**Sample Log Entry:**

```json
{
  "body": "incoming request",
  "severity": "info",
  "attributes": {
    "req": {
      "host": "localhost:8080",
      "method": "GET",
      "remoteAddress": "127.0.0.1",
      "url": "/health"
    },
    "reqId": "req-17"
  },
  "resources": {
    "service.name": "scanium-backend",
    "host.name": "615b34cc4f7b",
    "process.runtime.name": "nodejs"
  }
}
```

***REMOVED******REMOVED******REMOVED*** 5. Logs in Loki (Docker Source) - WORKING

**Query:**

```bash
curl -s "http://localhost:3100/loki/api/v1/query" -G \
  --data-urlencode "query={source=\"docker\"}" \
  --data-urlencode "limit=2"
```

**Results:** 1378 log lines from Docker containers

- Source label: `docker`
- Contains logs from all monitoring stack containers

***REMOVED******REMOVED******REMOVED*** 6. Metrics in Mimir - WORKING

**Backend Prometheus Scrape:**

```bash
curl -s "http://localhost:9009/prometheus/api/v1/query" \
  --data-urlencode "query=up{job=\"scanium-backend\"}"
```

**Result:**

```json
{
  "status": "success",
  "data": {
    "resultType": "vector",
    "result": [{
      "metric": {
        "__name__": "up",
        "job": "scanium-backend",
        "service_name": "scanium-backend",
        "source": "scanium-backend",
        "env": "dev"
      },
      "value": [1768128836, "1"]
    }]
  }
}
```

Backend `/metrics` endpoint is being scraped successfully by Alloy.

***REMOVED******REMOVED*** Root Causes Identified

***REMOVED******REMOVED******REMOVED*** Issue 1: Tempo Span Metrics Not Generated

**Problem:** Dashboards query `traces_spanmetrics_*` metrics that don't exist in Mimir.

**Root Cause:** Tempo metrics generator is configured but NOT enabled due to missing `overrides`
section.

**Evidence:**

1. Tempo config has `metrics_generator` section with `span_metrics: {}` and `service_graphs: {}`
2. Generator WAL directory is empty: `/var/tempo/generator/wal/` has no files
3. No span metrics in Mimir: `traces_spanmetrics_calls_total` returns empty result
4. Tempo successfully receiving traces (verified via API)

**Known Issue:** This is a documented Tempo problem in single-tenant mode:

- GitHub Issue ***REMOVED***5479 (Aug 2025): "Metrics Generator not processing spans in single-tenant mode"
- Affects v2.7.2, v2.8.1, and v2.3.1 (currently deployed)
- Requires explicit `overrides` section to enable metrics generation

**Fix Required:**
Add to `/volume1/docker/scanium/repo/monitoring/tempo/tempo.yaml`:

```yaml
overrides:
  metrics_generator_processors:
    - service-graphs
    - span-metrics
```

***REMOVED******REMOVED******REMOVED*** Issue 2: Dashboard Queries Depend on Span Metrics

**Affected Dashboards:**

- `traces-drilldown.json` - Uses `traces_spanmetrics_latency_bucket`,
  `traces_spanmetrics_calls_total`
- Any dashboard with trace count/latency panels

**Dashboard Variable Example:**

```json
{
  "name": "service",
  "type": "query",
  "datasource": { "type": "prometheus", "uid": "MIMIR" },
  "query": "label_values(traces_spanmetrics_latency_bucket, service)"
}
```

This query will fail until Tempo generates span metrics.

***REMOVED******REMOVED******REMOVED*** Issue 3: Mac/NAS Config Drift

**Local Mac Config:** `/Users/family/dev/scanium/monitoring/alloy/alloy.hcl`

- Has separate receivers: `mobile_http`, `mobile_grpc`, `backend_http`
- Backend receiver on port 4319

**NAS Config:** `/volume1/docker/scanium/repo/monitoring/alloy/alloy.hcl`

- Has shared receivers: `shared_http`, `shared_grpc`
- Both on ports 4318/4317

**Impact:** Confusion during debugging. Mac repo should be synced with NAS before making changes.

***REMOVED******REMOVED*** Verification Commands

***REMOVED******REMOVED******REMOVED*** Check Traces

```bash
***REMOVED*** Search for backend traces
ssh nas 'curl -s "http://localhost:3200/api/search?tags=service.name%3Dscanium-backend&limit=10"'

***REMOVED*** Get trace details
ssh nas 'curl -s "http://localhost:3200/api/traces/{traceId}"'

***REMOVED*** List service names
ssh nas 'curl -s "http://localhost:3200/api/search/tag/service.name/values"'
```

***REMOVED******REMOVED******REMOVED*** Check Logs (OTLP)

```bash
***REMOVED*** Query OTLP logs
ssh nas 'curl -s "http://localhost:3100/loki/api/v1/query" -G \
  --data-urlencode "query={source=\"otlp\"}" \
  --data-urlencode "limit=10"'

***REMOVED*** Count logs by level
ssh nas 'curl -s "http://localhost:3100/loki/api/v1/query" -G \
  --data-urlencode "query=count_over_time({source=\"otlp\"}[5m])"'
```

***REMOVED******REMOVED******REMOVED*** Check Metrics

```bash
***REMOVED*** Backend up metric
ssh nas 'curl -s "http://localhost:9009/prometheus/api/v1/query" \
  --data-urlencode "query=up{job=\"scanium-backend\"}"'

***REMOVED*** Check for span metrics (should be empty until fix)
ssh nas 'curl -s "http://localhost:9009/prometheus/api/v1/query" \
  --data-urlencode "query=traces_spanmetrics_calls_total"'
```

***REMOVED******REMOVED******REMOVED*** Check Tempo Generator WAL

```bash
***REMOVED*** Should have files after fix
ssh nas 'ls -la /volume1/docker/scanium/repo/monitoring/data/tempo/generator/wal/'
```

***REMOVED******REMOVED*** Recommendations

***REMOVED******REMOVED******REMOVED*** Immediate Actions

1. **Fix Tempo Span Metrics**
    - Add `overrides` section to Tempo config
    - Restart Tempo container
    - Verify WAL directory has files
    - Verify span metrics appear in Mimir within 5 minutes

2. **Sync Local Repo**
    - Pull latest from NAS or push Mac changes to NAS
    - Document which config is authoritative (NAS is source of truth)

3. **Update Dashboards**
    - Verify dashboard queries work after Tempo fix
    - Consider fallback queries for when no traces exist
    - Add annotations explaining metric sources

***REMOVED******REMOVED******REMOVED*** Preventive Measures

1. **Add Monitoring Tests**
    - Extend `scripts/monitoring/prove-telemetry.sh` to check span metrics
    - Add test for WAL directory contents
    - Verify dashboard queries return data (not just 0 results)

2. **Documentation**
    - Update `monitoring/INGESTION_RUNBOOK.md` with span metrics troubleshooting
    - Document Tempo overrides requirement
    - Add dashboard verification checklist

3. **Alerting**
    - Create alert for Tempo metrics generator failures
    - Alert on empty span metrics for >10 minutes when traces exist
    - Monitor WAL directory size

***REMOVED******REMOVED*** Summary

**What's Working:**

- Backend OTLP instrumentation (traces, logs, metrics export)
- Alloy OTLP receivers (4317 gRPC, 4318 HTTP)
- Trace ingestion into Tempo
- Log ingestion into Loki (both OTLP and Docker sources)
- Metrics scraping from backend `/metrics` endpoint
- Network connectivity between all containers

**What's Not Working:**

- Tempo span metrics generation (config issue)
- Dashboard panels that depend on span metrics
- Service graph metrics (same root cause)

**Resolution Path:**

1. Add Tempo overrides config (5 minutes)
2. Restart Tempo (1 minute)
3. Verify span metrics appear (5-10 minutes)
4. Test dashboards (2 minutes)

**Total Estimated Resolution Time:** 15-20 minutes

***REMOVED******REMOVED*** Related Documentation

- [Tempo Metrics Generator Troubleshooting](https://grafana.com/docs/tempo/latest/troubleshooting/metrics-generator/)
- [Tempo Span Metrics Configuration](https://grafana.com/docs/tempo/latest/metrics-from-traces/span-metrics/span-metrics-metrics-generator/)
- [GitHub Issue ***REMOVED***5479](https://github.com/grafana/tempo/issues/5479) - Metrics Generator not
  processing spans in single-tenant mode
- [Grafana Community Forum](https://community.grafana.com/t/no-metrics-being-generated/122800) - No
  metrics being generated

***REMOVED******REMOVED*** Files Referenced

***REMOVED******REMOVED******REMOVED*** Backend

- `/Users/family/dev/scanium/backend/src/infra/telemetry/index.ts` - OTLP initialization
- `/Users/family/dev/scanium/backend/src/main.ts` - Telemetry bootstrap
- `/Users/family/dev/scanium/backend/docker-compose.yml` - OTLP endpoint config

***REMOVED******REMOVED******REMOVED*** Monitoring Stack

- `/volume1/docker/scanium/repo/monitoring/tempo/tempo.yaml` - Needs fix
- `/volume1/docker/scanium/repo/monitoring/alloy/alloy.hcl` - OTLP receivers
- `/Users/family/dev/scanium/monitoring/grafana/dashboards/traces-drilldown.json` - Depends on span
  metrics

***REMOVED******REMOVED******REMOVED*** Scripts

- `/Users/family/dev/scanium/scripts/monitoring/prove-telemetry.sh` - Verification script
