# Scanium Grafana Dashboards

This document describes the Grafana dashboards provisioned for Scanium observability, including the expected metrics/labels and how to adapt queries if your naming conventions differ.

## Dashboard Overview

| Dashboard | UID | Purpose | Primary Datasource |
|-----------|-----|---------|-------------------|
| [System Overview (RED)](#system-overview-red) | `scanium-system-overview` | Rate, Errors, Duration overview with saturation | Mimir |
| [Backend API Performance](#backend-api-performance) | `scanium-backend-api-perf` | Latency, throughput, errors by route | Mimir |
| [Backend Errors](#backend-errors) | `scanium-backend-errors` | 4xx vs 5xx analysis, error messages, trace links | Mimir, Loki, Tempo |
| [Logs Explorer](#logs-explorer) | `scanium-logs-explorer` | Log exploration, error rates, pattern analysis | Loki |
| [Traces Drilldown](#traces-drilldown) | `scanium-traces-drilldown` | Slow traces, span analysis, service map | Tempo, Mimir |
| [LGTM Stack Health](#lgtm-stack-health) | `scanium-lgtm-health` | Observability infrastructure health | Mimir |
| [Ops Overview](#ops-overview-mobile) | `scanium-ops-overview` | Mobile app executive summary | Loki, Mimir |
| [App Health](#app-health-mobile) | `scanium-app-health` | Mobile app functional correctness | Loki |
| [Scan Performance](#scan-performance-mobile) | `scanium-scan-performance` | ML inference latency | Mimir |
| [Errors](#errors-mobile) | `scanium-errors` | Mobile app error analysis | Loki |
| [Pipeline Health](#pipeline-health) | `scanium-pipeline-health` | Telemetry pipeline metrics | Loki, Mimir |

---

## Metric & Label Inventory

### Backend HTTP Metrics (OpenTelemetry Semantic Conventions)

The backend dashboards expect metrics following [OpenTelemetry HTTP semantic conventions](https://opentelemetry.io/docs/specs/semconv/http/http-metrics/).

#### Primary Metric: `http_server_request_duration_seconds`

**Type:** Histogram (with `_bucket`, `_count`, `_sum` suffixes)

**Expected Labels:**

| Label | Description | Example Values |
|-------|-------------|----------------|
| `service_name` | Name of the service | `api-gateway`, `user-service` |
| `deployment_environment` | Environment | `dev`, `stage`, `prod` |
| `http_request_method` | HTTP method | `GET`, `POST`, `PUT`, `DELETE` |
| `http_route` | Route pattern (not path) | `/api/v1/users/{id}`, `/health` |
| `http_response_status_code` | HTTP status code | `200`, `400`, `500` |

**Example metric:**
```
http_server_request_duration_seconds_bucket{
  service_name="api-gateway",
  deployment_environment="prod",
  http_request_method="GET",
  http_route="/api/v1/users/{id}",
  http_response_status_code="200",
  le="0.1"
} 1234
```

#### Saturation Metric: `http_server_active_requests`

**Type:** Gauge

**Expected Labels:**
- `service_name`
- `deployment_environment`

### Trace Span Metrics (Tempo-generated)

Tempo's metrics generator exports span metrics to Mimir.

#### Metric: `traces_spanmetrics_latency`

**Type:** Histogram

**Expected Labels:**

| Label | Description |
|-------|-------------|
| `service` | Service name |
| `span_name` | Name of the span |
| `span_kind` | `SPAN_KIND_SERVER`, `SPAN_KIND_CLIENT`, etc. |
| `status_code` | `STATUS_CODE_OK`, `STATUS_CODE_ERROR`, `STATUS_CODE_UNSET` |

#### Metric: `traces_spanmetrics_calls_total`

**Type:** Counter

Same labels as `traces_spanmetrics_latency`.

### Log Labels (Loki)

#### Backend Logs

| Label | Description | Example Values |
|-------|-------------|----------------|
| `source` | Log source identifier | `scanium-backend`, `scanium-mobile` |
| `env` | Environment | `dev`, `stage`, `prod` |
| `service_name` | Service name (optional) | `api-gateway` |

**Expected JSON fields in log body:**

| Field | Description |
|-------|-------------|
| `level` | Log level: `debug`, `info`, `warn`, `error`, `fatal` |
| `error_message` | Error message text |
| `event_name` | Event type (for mobile) |
| `trace_id` | Trace correlation ID |

#### Mobile App Logs

| Label | Description | Example Values |
|-------|-------------|----------------|
| `source` | Always `scanium-mobile` | `scanium-mobile` |
| `platform` | Mobile platform | `android`, `ios` |
| `env` | Environment | `dev`, `stage`, `prod` |
| `app_version` | Semantic version | `1.2.3` |

### Pipeline Self-Observability Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `up{source="pipeline"}` | Gauge | Service availability (1=up, 0=down) |
| `otelcol_receiver_accepted_log_records` | Counter | Logs accepted by Alloy |
| `otelcol_receiver_accepted_metric_points` | Counter | Metrics accepted by Alloy |
| `otelcol_receiver_accepted_spans` | Counter | Spans accepted by Alloy |
| `otelcol_exporter_sent_*` | Counter | Records sent to backends |
| `otelcol_exporter_send_failed_*` | Counter | Failed exports |
| `otelcol_exporter_queue_size` | Gauge | Current queue depth |
| `otelcol_exporter_queue_capacity` | Gauge | Maximum queue capacity |
| `loki_ingester_memory_streams` | Gauge | Active Loki streams |
| `tempo_ingester_live_traces` | Gauge | Live Tempo traces |
| `cortex_ingester_memory_series` | Gauge | Active Mimir series |

---

## Adapting Queries for Different Naming Conventions

If your metrics use different names or labels, here's how to adapt:

### Different Metric Names

**Scenario:** Your HTTP metrics are named `http_requests_total` instead of `http_server_request_duration_seconds`

1. Open the dashboard in Grafana
2. Go to Settings → Variables
3. Update variable queries to use your metric name
4. Edit each panel query:
   - Find: `http_server_request_duration_seconds`
   - Replace: `http_requests_total` (or your metric name)

### Different Label Names

**Scenario:** You use `environment` instead of `deployment_environment`

1. Update dashboard variables to use your label:
   ```promql
   # Original
   label_values(http_server_request_duration_seconds_bucket, deployment_environment)
   # Modified
   label_values(http_server_request_duration_seconds_bucket, environment)
   ```

2. Update panel queries:
   ```promql
   # Original
   {deployment_environment=~"$env"}
   # Modified
   {environment=~"$env"}
   ```

### Common Label Mapping

| OpenTelemetry Convention | Common Alternative | Dashboard Variable |
|-------------------------|-------------------|-------------------|
| `service_name` | `service`, `app` | `$service` |
| `deployment_environment` | `env`, `environment` | `$env` |
| `http_route` | `route`, `path`, `endpoint` | `$route` |
| `http_request_method` | `method` | `$method` |
| `http_response_status_code` | `status_code`, `status` | `$status_code` |

### Non-Histogram Metrics

If your latency metrics are gauges or summaries instead of histograms:

**Original (histogram):**
```promql
histogram_quantile(0.95, sum by (le) (rate(http_server_request_duration_seconds_bucket[5m])))
```

**For summary metrics:**
```promql
http_request_duration_seconds{quantile="0.95"}
```

**For gauge (avg only):**
```promql
avg(http_request_duration_seconds)
```

---

## Dashboard Variables

All backend dashboards use these standard variables:

| Variable | Type | Query | Description |
|----------|------|-------|-------------|
| `$service` | Query | `label_values(http_server_request_duration_seconds_bucket, service_name)` | Filter by service |
| `$env` | Custom | `dev,stage,prod` | Filter by environment |
| `$route` | Query | `label_values(...{service_name=~"$service"}, http_route)` | Filter by API route |
| `$method` | Query | `label_values(..., http_request_method)` | Filter by HTTP method |

Mobile dashboards use:

| Variable | Type | Query | Description |
|----------|------|-------|-------------|
| `$platform` | Custom | `android,ios` | Filter by platform |
| `$env` | Custom | `dev,stage,prod` | Filter by environment |
| `$app_version` | Query | `label_values({source="scanium-mobile"}, app_version)` | Filter by app version |

---

## Dashboard Details

### System Overview (RED)

**Purpose:** Executive summary using RED methodology (Rate, Errors, Duration) plus saturation.

**Key Panels:**
- Request rate (req/s)
- Error rate (% 5xx)
- Latency (p50, p95, p99)
- Active requests (saturation)
- Apdex score (satisfaction index)
- Status code distribution

### Backend API Performance

**Purpose:** Deep dive into API latency and throughput per route.

**Key Panels:**
- Latency percentiles over time
- Latency by route (p95)
- Throughput by route
- Slowest routes table
- Latency heatmap

### Backend Errors

**Purpose:** Error investigation with logs and trace correlation.

**Key Panels:**
- 4xx vs 5xx breakdown
- Error rate by route
- Top error messages (from logs)
- Recent error logs
- Error traces with drill-down links

### Logs Explorer

**Purpose:** General-purpose log exploration and pattern analysis.

**Key Panels:**
- Log volume by level
- Error rate over time
- Top error types
- Live log stream (filterable)
- Anomaly detection (rate vs moving average)

### Traces Drilldown

**Purpose:** Distributed tracing analysis and performance investigation.

**Key Panels:**
- Span latency percentiles
- Slowest spans
- Most called spans
- Error rate by span
- Service dependency graph
- Slow/error trace tables with links

### LGTM Stack Health

**Purpose:** Monitor the observability infrastructure itself.

**Key Panels:**
- Service status (Alloy, Loki, Tempo, Mimir)
- OTLP ingest rates
- Export rates
- Queue utilization
- Backend health (streams, traces, series)
- Resource usage (CPU, memory)

---

## Alert Rules

Alert rules are provisioned in `/monitoring/grafana/provisioning/alerting/rules.yaml`.

### Backend Alerts (Recommended)

| Alert | Metric | Condition | Severity |
|-------|--------|-----------|----------|
| Backend 5xx Error Rate Spike | `http_server_request_duration_seconds_count{http_response_status_code=~"5.."}` | > 5% for 5m | Critical |
| Sustained High Latency (p95) | `histogram_quantile(0.95, ...)` | > 500ms for 10m | Warning |
| API Availability | `up{job="backend"}` | == 0 for 2m | Critical |

### Infrastructure Alerts (Existing)

| Alert | Condition | Severity |
|-------|-----------|----------|
| Alloy Down | `up{job="alloy"} == 0` for 2m | Critical |
| Loki Down | `up{job="loki"} == 0` for 2m | Critical |
| Tempo Down | `up{job="tempo"} == 0` for 2m | Critical |
| Mimir Down | `up{job="mimir"} == 0` for 2m | Critical |
| Export Failures | Any `otelcol_exporter_send_failed_*` > 0 for 5m | Critical |
| Queue Backpressure | Queue > 80% capacity for 5m | Warning |

---

## Usage Tips

### Correlating Signals

1. **Metrics → Logs:** Click on a spike in error rate, then use the time range to filter logs
2. **Logs → Traces:** Click on `trace_id` in log details to jump to Tempo
3. **Traces → Metrics:** Use trace exemplars in Mimir to find specific requests

### Time Range Recommendations

| Dashboard | Recommended Range | Use Case |
|-----------|------------------|----------|
| System Overview | 1h - 6h | Real-time monitoring |
| API Performance | 1h - 24h | Performance analysis |
| Errors | 15m - 1h | Incident investigation |
| Logs Explorer | 5m - 1h | Debugging |
| Traces | 5m - 1h | Request tracing |
| LGTM Health | 1h - 24h | Infrastructure monitoring |

### Panel Links

Many panels include drill-down links:
- **Tables:** Click row to view trace in Tempo
- **Graphs:** Click point to correlate with time range
- **Stats:** Most have related detail dashboards

---

## Troubleshooting

### No Data in Backend Dashboards

1. **Check datasource:** Ensure Mimir datasource is configured with correct URL
2. **Check metrics exist:** Use Explore → Mimir → Metrics browser
3. **Check label names:** Your labels may differ from expected (see adaptation guide)

### Missing Span Metrics

1. **Enable Tempo metrics generator:** Check `tempo.yaml` has `metrics_generator` enabled
2. **Check Mimir target:** Tempo should export to Mimir's remote write endpoint
3. **Wait for data:** Span metrics may take a few minutes to appear

### Log Queries Return Empty

1. **Check source label:** Ensure logs have `source="scanium-backend"` label
2. **Check JSON parsing:** Logs must be valid JSON for `| json` parsing
3. **Check level field:** Ensure logs have a `level` field

---

## Adding Custom Dashboards

To add a new dashboard:

1. Create JSON file in `/monitoring/grafana/dashboards/`
2. Use UID format: `scanium-<name>`
3. Include standard variables (service, env, route)
4. Use consistent panel styling
5. Add dashboard description

Dashboard will be auto-provisioned on Grafana restart.

---

## References

- [OpenTelemetry HTTP Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/http/http-metrics/)
- [Grafana Dashboard Best Practices](https://grafana.com/docs/grafana/latest/dashboards/build-dashboards/best-practices/)
- [PromQL Query Examples](https://prometheus.io/docs/prometheus/latest/querying/examples/)
- [LogQL Query Language](https://grafana.com/docs/loki/latest/logql/)
- [TraceQL Query Language](https://grafana.com/docs/tempo/latest/traceql/)
