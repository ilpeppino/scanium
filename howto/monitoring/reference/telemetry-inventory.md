***REMOVED*** Telemetry Inventory Report

> Auto-generated reference based on configuration analysis.
> Re-run `./howto/monitoring/scripts/verify-monitoring.sh` with running stack for live data.

**Generated:** 2025-12-30 (configuration-based, not live)

***REMOVED******REMOVED*** Datasources

| Name | Type | UID | Default |
|------|------|-----|---------|
| Loki | loki | `LOKI` | false |
| Tempo | tempo | `TEMPO` | false |
| Mimir | prometheus | `MIMIR` | true |

***REMOVED******REMOVED*** Metrics (Mimir)

***REMOVED******REMOVED******REMOVED*** Expected Backend HTTP Metrics

When the backend is running and sending telemetry via OTLP, expect:

**Primary metric:** `http_server_request_duration_seconds` (histogram)

**Expected labels:**
- `service_name` - Name of the backend service
- `deployment_environment` - Environment (dev, stage, prod)
- `http_request_method` - HTTP method (GET, POST, PUT, DELETE)
- `http_route` - Route pattern (e.g., `/api/v1/users/{id}`)
- `http_response_status_code` - HTTP status code (200, 400, 500, etc.)

***REMOVED******REMOVED******REMOVED*** Pipeline Self-Observability Metrics

Alloy scrapes metrics from LGTM components with `source="pipeline"` label.

**Jobs scraped:**
- `alloy` - Grafana Alloy (localhost:12345)
- `loki` - Loki log storage (loki:3100)
- `tempo` - Tempo trace storage (tempo:3200)
- `mimir` - Mimir metrics storage (mimir:9009)

**Key metrics:**
| Category | Metrics |
|----------|---------|
| OTLP Receiver | `otelcol_receiver_accepted_log_records`, `otelcol_receiver_accepted_metric_points`, `otelcol_receiver_accepted_spans` |
| OTLP Exporter | `otelcol_exporter_sent_*`, `otelcol_exporter_send_failed_*` |
| Queue Health | `otelcol_exporter_queue_size`, `otelcol_exporter_queue_capacity` |
| Loki | `loki_ingester_memory_streams`, `loki_request_duration_seconds_count` |
| Tempo | `tempo_ingester_live_traces`, `tempo_request_duration_seconds_count` |
| Mimir | `cortex_ingester_memory_series`, `cortex_request_duration_seconds_count` |
| Process | `up`, `process_cpu_seconds_total`, `process_resident_memory_bytes` |

***REMOVED******REMOVED******REMOVED*** Mobile App Metrics

When the mobile app sends telemetry via OTLP:

**External labels (from Alloy):**
- `source` = `scanium-mobile`
- `env` = `dev`

***REMOVED******REMOVED*** Logs (Loki)

***REMOVED******REMOVED******REMOVED*** Expected Labels

**From Alloy external_labels:**
- `source` - Source identifier (`scanium-mobile`, `scanium-backend`)
- `env` - Environment (`dev`, `stage`, `prod`)

**From OTLP resource attributes:**
- `service_name` - Service name
- `deployment_environment` - Environment

***REMOVED******REMOVED******REMOVED*** Expected JSON Fields in Log Body

| Field | Description | Example Values |
|-------|-------------|----------------|
| `level` | Log level | `debug`, `info`, `warn`, `error`, `fatal` |
| `error_message` | Error message text | Error description |
| `trace_id` | Trace correlation ID | Hexadecimal trace ID |
| `event_name` | Event type (mobile) | Event identifier |

***REMOVED******REMOVED******REMOVED*** Sample LogQL Queries

```logql
***REMOVED*** All error logs
{source="scanium-backend"} |= `error` | json | level = `error`

***REMOVED*** Logs with trace correlation
{source="scanium-backend"} | json | trace_id != ""

***REMOVED*** Mobile app events
{source="scanium-mobile"} | json | event_name != ""
```

***REMOVED******REMOVED*** Traces (Tempo)

***REMOVED******REMOVED******REMOVED*** Expected Resource Attributes

- `service.name` - Service name
- `deployment.environment` - Environment
- `telemetry.sdk.language` - SDK language (e.g., `nodejs`, `kotlin`)
- `telemetry.sdk.name` - SDK name (e.g., `opentelemetry`)
- `telemetry.sdk.version` - SDK version

***REMOVED******REMOVED******REMOVED*** Expected Span Attributes

- `http.method` - HTTP method
- `http.route` - HTTP route pattern
- `http.status_code` - HTTP status code
- `http.url` - Full URL
- `db.system` - Database system (e.g., `postgresql`)
- `db.statement` - Database query

***REMOVED******REMOVED******REMOVED*** Tempo Metrics Generator

Tempo exports span metrics to Mimir:

**Enabled processors:**
- `service_graphs` - Service dependency graph
- `span_metrics` - Latency and call count by span

**Exported metrics:**
- `traces_spanmetrics_latency` (histogram)
- `traces_spanmetrics_calls_total` (counter)

**Labels:**
- `service` - Service name
- `span_name` - Span/operation name
- `span_kind` - `SPAN_KIND_SERVER`, `SPAN_KIND_CLIENT`, etc.
- `status_code` - `STATUS_CODE_OK`, `STATUS_CODE_ERROR`, `STATUS_CODE_UNSET`

***REMOVED******REMOVED******REMOVED*** Sample TraceQL Queries

```traceql
***REMOVED*** Slow HTTP spans (>500ms)
{ span.http.status_code >= 200 } | duration > 500ms

***REMOVED*** Error spans
{ status = error }

***REMOVED*** Specific service
{ resource.service.name = "scanium-backend" }
```

***REMOVED******REMOVED*** Existing Dashboards

| Title | UID | Tags |
|-------|-----|------|
| Scanium - System Overview (RED) | `scanium-system-overview` | scanium, red, overview, backend |
| Scanium - Backend API Performance | `scanium-backend-api-perf` | scanium, backend, api, performance |
| Scanium - Backend Errors | `scanium-backend-errors` | scanium, backend, errors |
| Scanium - Logs Explorer | `scanium-logs-explorer` | scanium, logs, loki |
| Scanium - Traces Drilldown | `scanium-traces-drilldown` | scanium, traces, tempo |
| Scanium - LGTM Stack Health | `scanium-lgtm-health` | scanium, lgtm, infrastructure, health |
| Scanium - Ops Overview (Mobile) | `scanium-ops-overview` | scanium, mobile, overview |
| Scanium - App Health (Mobile) | `scanium-app-health` | scanium, mobile, health |
| Scanium - Scan Performance (Mobile) | `scanium-scan-performance` | scanium, mobile, performance |
| Scanium - Errors (Mobile) | `scanium-errors` | scanium, mobile, errors |
| Scanium - Pipeline Health | `scanium-pipeline-health` | scanium, pipeline, health |

***REMOVED******REMOVED*** Dashboard Data Requirements

***REMOVED******REMOVED******REMOVED*** Backend Dashboards (require backend telemetry)

These dashboards require the backend to be running and sending OTLP data:

| Dashboard | Required Metrics | Status |
|-----------|-----------------|--------|
| System Overview (RED) | `http_server_request_duration_seconds_*` | Needs backend |
| Backend API Performance | `http_server_request_duration_seconds_*` | Needs backend |
| Backend Errors | `http_server_request_duration_seconds_*`, Loki logs | Needs backend |

***REMOVED******REMOVED******REMOVED*** Infrastructure Dashboards (self-contained)

These dashboards work with just the LGTM stack running:

| Dashboard | Required Metrics | Status |
|-----------|-----------------|--------|
| LGTM Stack Health | `up{source="pipeline"}`, `otelcol_*`, `loki_*`, `tempo_*`, `cortex_*` | Self-sufficient |
| Pipeline Health | `otelcol_*` metrics | Self-sufficient |

***REMOVED******REMOVED******REMOVED*** Mobile Dashboards (require mobile app telemetry)

These dashboards require the mobile app to be sending OTLP data:

| Dashboard | Required Data | Status |
|-----------|--------------|--------|
| Ops Overview (Mobile) | `{source="scanium-mobile"}` logs/metrics | Needs mobile app |
| App Health (Mobile) | `{source="scanium-mobile"}` logs | Needs mobile app |
| Scan Performance (Mobile) | Mobile metrics | Needs mobile app |
| Errors (Mobile) | `{source="scanium-mobile"}` error logs | Needs mobile app |

***REMOVED******REMOVED*** Validation Checklist

After starting the monitoring stack, verify:

- [ ] Run inventory script: `./howto/monitoring/scripts/verify-monitoring.sh`
- [ ] Restart Grafana: `docker compose -p scanium-monitoring restart grafana`
- [ ] Verify dashboards appear: `curl -s localhost:3000/api/search | jq`
- [ ] Check LGTM Stack Health dashboard (should show UP status for all services)
- [ ] Start backend and verify System Overview dashboard shows request data
- [ ] Generate some traffic and verify latency/error panels populate

---

*To regenerate this report with live data:*
```bash
./howto/monitoring/scripts/verify-monitoring.sh
```
