***REMOVED*** Scanium Grafana Dashboards

This document describes the Grafana dashboards provisioned for Scanium observability, including the expected metrics/labels and how to adapt queries if your naming conventions differ.

***REMOVED******REMOVED*** Dashboard Overview

| Dashboard | UID | Purpose | Primary Datasource |
|-----------|-----|---------|-------------------|
| [System Overview (RED)](***REMOVED***system-overview-red) | `scanium-system-overview` | Rate, Errors, Duration overview with saturation | Mimir |
| [Backend Health](***REMOVED***backend-health) | `scanium-backend-health` | Backend RED summary + recent errors | Mimir, Loki |
| [Backend API Performance](***REMOVED***backend-api-performance) | `scanium-backend-api-perf` | Latency, throughput, errors by route | Mimir |
| [Backend Errors](***REMOVED***backend-errors) | `scanium-backend-errors` | 4xx vs 5xx analysis, error messages, trace links | Mimir, Loki, Tempo |
| [Logs Explorer](***REMOVED***logs-explorer) | `scanium-logs-explorer` | Log exploration, error rates, pattern analysis | Loki |
| [Traces Drilldown](***REMOVED***traces-drilldown) | `scanium-traces-drilldown` | Slow traces, span analysis, service map | Tempo, Mimir |
| [LGTM Stack Health](***REMOVED***lgtm-stack-health) | `scanium-lgtm-health` | Observability infrastructure health | Mimir |
| [Ops Overview](***REMOVED***ops-overview-mobile) | `scanium-ops-overview` | Mobile app executive summary | Loki, Mimir |
| [Mobile App Health](***REMOVED***mobile-app-health) | `scanium-mobile-app-health` | Mobile app functional correctness | Loki |
| [Scan Performance](***REMOVED***scan-performance-mobile) | `scanium-scan-performance` | ML inference latency | Mimir |
| [Errors](***REMOVED***errors-mobile) | `scanium-errors` | Mobile app error analysis | Loki |
| [Pipeline Health](***REMOVED***pipeline-health) | `scanium-pipeline-health` | Telemetry pipeline metrics | Loki, Mimir |

---

***REMOVED******REMOVED*** Metric & Label Inventory

***REMOVED******REMOVED******REMOVED*** Backend HTTP Metrics (Prometheus via prom-client)

The backend dashboards expect Scanium-prefixed metrics emitted by the backend service and scraped by Alloy.

***REMOVED******REMOVED******REMOVED******REMOVED*** Primary Metrics

- `scanium_http_requests_total` (Counter)
- `scanium_http_request_duration_ms_bucket` (Histogram)

**Expected Labels:**

| Label | Description | Example Values |
|-------|-------------|----------------|
| `service_name` | Name of the service | `scanium-backend` |
| `env` | Environment | `dev`, `stage`, `prod` |
| `deployment_environment` | Environment (alias) | `dev`, `stage`, `prod` |
| `method` | HTTP method | `GET`, `POST` |
| `route` | Route pattern | `/v1/assist/chat`, `/healthz` |
| `status_code` | HTTP status code | `200`, `401`, `500` |
| `source` | Log/metric source | `scanium-backend` |

**Example metric:**
```
scanium_http_request_duration_ms_bucket{
  service_name="scanium-backend",
  env="dev",
  method="GET",
  route="/v1/config",
  status_code="401",
  le="250"
} 12
```

***REMOVED******REMOVED******REMOVED*** Assistant / Vision / Classifier Metrics

Scanium backend emits domain metrics for assistant, vision, and classifier pipelines (see `monitoring/grafana/OPENAI_MONITORING.md`).

***REMOVED******REMOVED******REMOVED*** Log Labels (Loki)

***REMOVED******REMOVED******REMOVED******REMOVED*** Backend Logs

| Label | Description | Example Values |
|-------|-------------|----------------|
| `source` | Log source identifier | `scanium-backend` |
| `env` | Environment | `dev`, `stage`, `prod` |
| `service_name` | Service name | `scanium-backend` |
| `level` | Severity | `INFO`, `WARN`, `ERROR` |

**Expected JSON fields in log body:**

| Field | Description |
|-------|-------------|
| `severity` | Severity string |
| `body` | Log message |
| `traceid` | Trace correlation ID |
| `attributes.http.route` | Route |

***REMOVED******REMOVED******REMOVED******REMOVED*** Mobile App Logs (OTLP events)

| Label | Description | Example Values |
|-------|-------------|----------------|
| `source` | Always `scanium-mobile` | `scanium-mobile` |
| `platform` | Mobile platform | `android`, `ios` |
| `env` | Environment | `dev`, `stage`, `prod` |
| `app_version` | Semantic version | `1.2.3` |

**Required JSON fields for mobile dashboards:**

| Field | Description | Example |
|-------|-------------|---------|
| `event_name` | Event type | `scan.session_started`, `scan.candidate_created` |
| `scan_mode` | Scan mode | `barcode`, `camera`, `manual` |
| `duration_ms` | Session duration (ms) | `1234` |
| `session_id` | Session identifier | `sess_abc123` |

**Recommended event schema + cardinality constraints:**
- Keep `session_id` stable per session but avoid device IDs or PII.
- Avoid high-cardinality attributes (full URLs, raw SKUs, free-form user input).
- Use controlled vocabularies for `event_name`, `scan_mode`, and `platform`.
- Prefer short enums over arbitrary strings for new attributes.

***REMOVED******REMOVED******REMOVED*** Pipeline Self-Observability Metrics

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

***REMOVED******REMOVED*** Adapting Queries for Different Naming Conventions

If your metrics use different names or labels, here is how to adapt:

***REMOVED******REMOVED******REMOVED*** Different Metric Names

**Scenario:** Your HTTP metrics are named `http_requests_total` instead of `scanium_http_requests_total`

1. Open the dashboard in Grafana
2. Go to Settings → Variables
3. Update variable queries to use your metric name
4. Edit each panel query:
   - Find: `scanium_http_requests_total`
   - Replace: `http_requests_total` (or your metric name)

***REMOVED******REMOVED******REMOVED*** Different Label Names

**Scenario:** You use `environment` instead of `env`

1. Update dashboard variables to use your label:
   ```promql
   ***REMOVED*** Original
   label_values(scanium_http_requests_total, env)
   ***REMOVED*** Modified
   label_values(scanium_http_requests_total, environment)
   ```

2. Update panel queries:
   ```promql
   ***REMOVED*** Original
   {env=~"$env"}
   ***REMOVED*** Modified
   {environment=~"$env"}
   ```

***REMOVED******REMOVED******REMOVED*** Common Label Mapping

| Expected Label | Common Alternative | Dashboard Variable |
|----------------|--------------------|-------------------|
| `service_name` | `service`, `app` | `$service` |
| `env` | `environment` | `$env` |
| `route` | `path`, `endpoint` | `$route` |
| `method` | `http_method` | `$method` |
| `status_code` | `status` | `$status_code` |

***REMOVED******REMOVED******REMOVED*** Non-Histogram Metrics

If your latency metrics are gauges or summaries instead of histograms:

**Original (histogram):**
```promql
histogram_quantile(0.95, sum by (le) (rate(scanium_http_request_duration_ms_bucket[5m])))
```

**For summary metrics:**
```promql
scanium_http_request_duration_ms{quantile="0.95"}
```

**For gauge (avg only):**
```promql
avg(scanium_http_request_duration_ms)
```

---

***REMOVED******REMOVED*** Dashboard Variables

All backend dashboards use these standard variables:

| Variable | Type | Query | Description |
|----------|------|-------|-------------|
| `$service` | Query | `label_values(scanium_http_requests_total, service_name)` | Filter by service |
| `$env` | Query | `label_values(scanium_http_requests_total, env)` | Filter by environment |
| `$route` | Query | `label_values(scanium_http_request_duration_ms_bucket{service_name=~"$service"}, route)` | Filter by API route |
| `$method` | Query | `label_values(scanium_http_request_duration_ms_bucket, method)` | Filter by HTTP method |

Mobile dashboards use:

| Variable | Type | Query | Description |
|----------|------|-------|-------------|
| `$platform` | Custom | `android,ios` | Filter by platform |
| `$env` | Custom | `dev,stage,prod` | Filter by environment |
| `$app_version` | Query | `label_values({source="scanium-mobile"}, app_version)` | Filter by app version |

---

***REMOVED******REMOVED*** Dashboard Details

***REMOVED******REMOVED******REMOVED*** System Overview (RED)

**Purpose:** Executive summary using RED methodology (Rate, Errors, Duration) plus saturation.

**Key Panels:**
- Request rate (req/s)
- Error rate (% 5xx)
- Latency (p50, p95, p99)
- Active requests (saturation)
- Apdex score (satisfaction index)
- Status code distribution

***REMOVED******REMOVED******REMOVED*** Backend Health

**Purpose:** Backend RED summary plus recent error logs.

**Key Panels:**
- Request rate (req/s)
- Error rate (% 5xx)
- p95 latency
- Requests by route
- 4xx vs 5xx over time
- Recent error logs

***REMOVED******REMOVED******REMOVED*** Backend API Performance

**Purpose:** Deep dive into API latency and throughput per route.

**Key Panels:**
- Latency percentiles over time
- Latency by route (p95)
- Throughput by route
- Slowest routes table
- Latency heatmap

***REMOVED******REMOVED******REMOVED*** Backend Errors

**Purpose:** Error investigation with logs and trace correlation.

**Key Panels:**
- 4xx vs 5xx breakdown
- Error rate by route
- Top error messages (from logs)
- Recent error logs
- Error traces with drill-down links

***REMOVED******REMOVED******REMOVED*** Logs Explorer

**Purpose:** General-purpose log exploration and pattern analysis.

**Key Panels:**
- Log volume by level
- Error rate over time
- Top error types
- Live log stream (filterable)
- Anomaly detection (rate vs moving average)

***REMOVED******REMOVED******REMOVED*** Traces Drilldown

**Purpose:** Distributed tracing analysis and performance investigation.

**Key Panels:**
- Span latency percentiles
- Slowest spans
- Most called spans
- Error rate by span
- Service dependency graph
- Slow/error trace tables with links

***REMOVED******REMOVED******REMOVED*** LGTM Stack Health

**Purpose:** Monitor the observability infrastructure itself.

**Key Panels:**
- Service status (Alloy, Loki, Tempo, Mimir)
- OTLP ingest rates
- Export rates
- Queue utilization
- Backend health (streams, traces, series)
- Resource usage (CPU, memory)

***REMOVED******REMOVED******REMOVED*** Mobile App Health

**Purpose:** Mobile app functional correctness and usage funnel.

**Key Panels:**
- Session started → candidate created → confirmed
- Confirmation ratio
- Scan mode usage
- Session duration distribution
- Session drilldown logs

**Note:** Panels are empty until mobile OTLP logs are shipped with `source="scanium-mobile"` and the required labels or fields.

---

***REMOVED******REMOVED*** Alert Rules

Alert rules are provisioned in `/monitoring/grafana/provisioning/alerting/rules.yaml`.

***REMOVED******REMOVED******REMOVED*** Backend Alerts (Recommended)

| Alert | Metric | Condition | Severity |
|-------|--------|-----------|----------|
| Backend 5xx Error Rate Spike | `scanium_http_requests_total{status_code=~"5.."}` | > 5% for 5m | Critical |
| Sustained High Latency (p95) | `histogram_quantile(0.95, ...)` | > 500ms for 10m | Warning |
| API Availability | `up{job="scanium-backend"}` | == 0 for 2m | Critical |

***REMOVED******REMOVED******REMOVED*** Infrastructure Alerts (Existing)

| Alert | Condition | Severity |
|-------|-----------|----------|
| Alloy Down | `up{job="alloy"} == 0` for 2m | Critical |
| Loki Down | `up{job="loki"} == 0` for 2m | Critical |
| Tempo Down | `up{job="tempo"} == 0` for 2m | Critical |
| Mimir Down | `up{job="mimir"} == 0` for 2m | Critical |
| Export Failures | Any `otelcol_exporter_send_failed_*` > 0 for 5m | Critical |
| Queue Backpressure | Queue > 80% capacity for 5m | Warning |

---

***REMOVED******REMOVED*** Usage Tips

***REMOVED******REMOVED******REMOVED*** Correlating Signals

1. **Metrics → Logs:** Click on a spike in error rate, then use the time range to filter logs
2. **Logs → Traces:** Click on `trace_id` in log details to jump to Tempo
3. **Traces → Metrics:** Use trace exemplars in Mimir to find specific requests

***REMOVED******REMOVED******REMOVED*** Time Range Recommendations

| Dashboard | Recommended Range | Use Case |
|-----------|------------------|----------|
| System Overview | 1h - 6h | Real-time monitoring |
| Backend Health | 1h - 6h | Operational pulse |
| API Performance | 1h - 24h | Performance analysis |
| Errors | 15m - 1h | Incident investigation |
| Logs Explorer | 5m - 1h | Debugging |
| Traces | 5m - 1h | Request tracing |
| LGTM Health | 1h - 24h | Infrastructure monitoring |

***REMOVED******REMOVED******REMOVED*** Panel Links

Many panels include drill-down links:
- **Tables:** Click row to view trace in Tempo
- **Graphs:** Click point to correlate with time range
- **Stats:** Most have related detail dashboards

---

***REMOVED******REMOVED*** Troubleshooting

***REMOVED******REMOVED******REMOVED*** No Data in Backend Dashboards

1. **Check datasource:** Ensure Mimir datasource is configured with correct URL
2. **Check metrics exist:** Use Explore → Mimir → Metrics browser
3. **Check label names:** Your labels may differ from expected (see adaptation guide)

***REMOVED******REMOVED******REMOVED*** Missing Span Metrics

1. **Enable Tempo metrics generator:** Check `tempo.yaml` has `metrics_generator` enabled
2. **Check Mimir target:** Tempo should export to Mimir's remote write endpoint
3. **Wait for data:** Span metrics may take a few minutes to appear

***REMOVED******REMOVED******REMOVED*** Log Queries Return Empty

1. **Check source label:** Ensure logs have `source="scanium-backend"` label
2. **Check JSON parsing:** Logs must be valid JSON for `| json` parsing
3. **Check level field:** Ensure logs have a `level` field

---

***REMOVED******REMOVED*** Adding Custom Dashboards

To add a new dashboard:

1. Create JSON file in `/monitoring/grafana/dashboards/`
2. Use UID format: `scanium-<name>`
3. Include standard variables (service, env, route)
4. Use consistent panel styling
5. Add dashboard description

Dashboard will be auto-provisioned on Grafana restart.

---

***REMOVED******REMOVED*** Telemetry Inventory Script

The `scripts/monitoring/inventory-telemetry.sh` script discovers what telemetry actually exists in your running LGTM stack and generates an inventory report.

***REMOVED******REMOVED******REMOVED*** Running the Inventory Script

```bash
***REMOVED*** Basic usage (requires monitoring stack to be running)
./howto/monitoring/scripts/verify-monitoring.sh

***REMOVED*** With custom Grafana URL
./howto/monitoring/scripts/verify-monitoring.sh --grafana-url http://localhost:3000

***REMOVED*** With API token (if anonymous auth is disabled)
./howto/monitoring/scripts/verify-monitoring.sh --token "your-grafana-api-token"
```

***REMOVED******REMOVED******REMOVED*** What It Discovers

1. **Datasources:** Lists all configured datasources with UIDs (Loki, Tempo, Mimir)
2. **Metrics (Mimir):** Sample metric names and their labels
3. **Logs (Loki):** Label keys and sample label values
4. **Traces (Tempo):** Service names and trace attributes (if available)
5. **Dashboards:** Currently provisioned dashboards

***REMOVED******REMOVED******REMOVED*** Output Files

- `monitoring/grafana/telemetry-inventory.json` - Machine-readable inventory
- `monitoring/grafana/telemetry-inventory.md` - Human-readable summary

***REMOVED******REMOVED******REMOVED*** Safety Measures

The script is designed to be safe for production use:
- Uses small time ranges (last 15m) to avoid heavy queries
- Limits label value queries to prevent cardinality explosions
- Timeouts prevent hung requests
- Read-only operations (no modifications to the stack)

---

***REMOVED******REMOVED*** Regenerating Dashboards

***REMOVED******REMOVED******REMOVED*** When to Regenerate

Regenerate dashboards when:
- Metric or label names change
- New telemetry sources are added
- Dashboard queries need updating

***REMOVED******REMOVED******REMOVED*** Workflow

1. **Discover current telemetry:**
   ```bash
   ./howto/monitoring/scripts/verify-monitoring.sh
   ```

2. **Review the inventory:**
   ```bash
   cat monitoring/grafana/telemetry-inventory.md
   ```

3. **Update dashboard JSON files** based on discovered metrics or labels

4. **Restart Grafana** to reload dashboards:
   ```bash
   docker compose -p scanium-monitoring restart grafana
   ```

5. **Verify dashboards** via Grafana UI or API:
   ```bash
   curl -s localhost:3000/api/search | jq ".[].title"
   ```

---

***REMOVED******REMOVED*** Validation Checklist

After making changes to dashboards, verify:

***REMOVED******REMOVED******REMOVED*** Stack Health
- [ ] All services are running: `docker compose -p scanium-monitoring ps`
- [ ] Grafana is healthy: `curl localhost:3000/api/health`
- [ ] Datasources are configured: `curl localhost:3000/api/datasources`

***REMOVED******REMOVED******REMOVED*** Dashboard Loading
- [ ] Dashboards appear in Grafana UI
- [ ] No "dashboard not found" errors
- [ ] Dashboard JSON is valid (no parse errors)

***REMOVED******REMOVED******REMOVED*** Data Presence
- [ ] **LGTM Stack Health:** Shows UP status for all services
- [ ] **System Overview:** Shows request rate (requires backend traffic)
- [ ] **Backend Health:** Shows request rate and error logs
- [ ] **Logs Explorer:** Shows log volume (requires log ingestion)
- [ ] **Traces Drilldown:** Shows traces (requires trace ingestion)

***REMOVED******REMOVED******REMOVED*** Panel Functionality
- [ ] Variables populate correctly
- [ ] Panels show data or appropriate "No data" message
- [ ] Cross-datasource links work (logs→traces, metrics→traces)

***REMOVED******REMOVED******REMOVED*** No Data Troubleshooting

If dashboards show "No data":

1. **Check time range:** Ensure it covers when data was generated
2. **Check variables:** Variables may need to be refreshed
3. **Check datasource:** Verify datasource UID matches (LOKI, TEMPO, MIMIR)
4. **Check query syntax:** Use Explore to test queries manually
5. **Check data exists:** Query backends directly to confirm data presence

---

***REMOVED******REMOVED*** References

- [OpenTelemetry HTTP Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/http/http-metrics/)
- [Grafana Dashboard Best Practices](https://grafana.com/docs/grafana/latest/dashboards/build-dashboards/best-practices/)
- [PromQL Query Examples](https://prometheus.io/docs/prometheus/latest/querying/examples/)
- [LogQL Query Language](https://grafana.com/docs/loki/latest/logql/)
- [TraceQL Query Language](https://grafana.com/docs/tempo/latest/traceql/)
