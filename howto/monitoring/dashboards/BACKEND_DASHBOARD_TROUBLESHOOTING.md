***REMOVED*** Backend API Performance Dashboard - Troubleshooting Report

**Dashboard**: Scanium - Backend API Performance
**Status**: ⚠️ Partially Fixed - Metrics collecting but not queryable
**Date**: 2026-01-10

***REMOVED******REMOVED*** Summary

Fixed the network configuration preventing Alloy from scraping backend metrics. However, a deeper
Mimir query issue prevents metrics from being displayed in dashboards.

***REMOVED******REMOVED*** ✅ What Was Fixed

***REMOVED******REMOVED******REMOVED*** Network Connectivity Issue

**Problem**: Alloy and backend were on different Docker networks, preventing metric scraping.

**Root Cause**:

- Alloy was on networks: `scanium-observability` + `backend_scanium-network`
- Backend was on network: `compose_scanium_net`
- Alloy's scrape config used `scanium-backend:8080` but hostname didn't resolve

**Solution**:

- Added `compose_scanium_net` as external network in `monitoring/docker-compose.yml`
- Connected Alloy to `compose_scanium_net` network
- Restarted Alloy container

**Verification**:

```bash
***REMOVED*** DNS now resolves correctly
docker exec scanium-alloy getent hosts scanium-backend
***REMOVED*** Output: 172.21.0.3    scanium-backend

***REMOVED*** Alloy metrics show successful scraping
curl http://127.0.0.1:12345/metrics | grep prometheus_forwarded_samples_total.*backend
***REMOVED*** Output: prometheus_forwarded_samples_total{component_id="prometheus.scrape.backend"} 3430
```

***REMOVED******REMOVED*** ⚠️ Outstanding Issue: Metrics Not Queryable

***REMOVED******REMOVED******REMOVED*** Symptoms

- ✅ Backend exposes metrics at `/metrics` endpoint (verified)
- ✅ Alloy successfully scrapes backend (3430+ samples forwarded)
- ✅ Alloy writes samples to Mimir remote write (4268 total samples written)
- ✅ Series exist in Mimir index (`/api/v1/series` returns 7 series)
- ❌ **Instant queries return NO DATA** (`/api/v1/query` returns empty results)
- ❌ **Range queries return NO DATA** (`/api/v1/query_range` returns empty results)

***REMOVED******REMOVED******REMOVED*** Evidence

**Series exist**:

```bash
curl -G 'http://127.0.0.1:9009/prometheus/api/v1/series' \
  --data-urlencode 'match[]=scanium_http_requests_total' | jq '.data | length'
***REMOVED*** Output: 7
```

**But queries return nothing**:

```bash
curl -G 'http://127.0.0.1:9009/prometheus/api/v1/query' \
  --data-urlencode 'query=scanium_http_requests_total' | jq '.data.result | length'
***REMOVED*** Output: 0
```

**Comparison with working metrics**:

- Metrics with `source="pipeline"` (alloy, loki, tempo, mimir self-monitoring) ARE queryable ✅
- Metrics with `source="scanium-backend"` are NOT queryable ❌
- Both use identical remote write configuration
- Both write to same Mimir endpoint `http://mimir:9009/api/v1/push`

***REMOVED******REMOVED******REMOVED*** Expected Dashboard Metrics

The dashboard expects these prom-client metrics from the backend:

```promql
***REMOVED*** Request rate
scanium_http_requests_total{
  method="GET|POST",
  route="/v1/config|/v1/classify|...",
  status_code="200|401|500|..."
}

***REMOVED*** Latency histogram
scanium_http_request_duration_ms_bucket{
  method="GET|POST",
  route="/v1/config|/v1/classify|...",
  status_code="200|401|500|...",
  le="5|10|25|50|100|250|500|1000|..."
}
```

**Labels applied by Alloy remote write**:

- `source="scanium-backend"`
- `env="dev"`
- `service_name="scanium-backend"`
- `deployment_environment="dev"`
- `instance="scanium-backend"`
- `job="scanium-backend"`

***REMOVED******REMOVED*** Potential Root Causes

***REMOVED******REMOVED******REMOVED*** 1. Mimir Ingester/Querier Issue

- Data might be stuck in ingester memory/WAL and not flushed to queryable storage
- Querier might not be reading from the correct tenant/namespace
- Clock skew causing samples to have timestamps outside queryable range

***REMOVED******REMOVED******REMOVED*** 2. Label Cardinality or Filtering

- Something about the `source="scanium-backend"` label set prevents queries
- However, series endpoint DOES return the series, so indexing works

***REMOVED******REMOVED******REMOVED*** 3. Configuration Mismatch

- External labels might be interfering with query path
- Mimir might have different retention/query settings per label set

***REMOVED******REMOVED*** Next Steps to Investigate

***REMOVED******REMOVED******REMOVED*** 1. Check Mimir Logs for Ingestion Errors

```bash
docker logs scanium-mimir 2>&1 | grep -i "error\|reject\|fail" | grep backend
```

***REMOVED******REMOVED******REMOVED*** 2. Verify Timestamps

Check if scraped samples have valid timestamps:

```bash
***REMOVED*** Check backend's system time
docker exec scanium-backend date +%s

***REMOVED*** Check Mimir's system time
docker exec scanium-mimir date +%s

***REMOVED*** Compare with actual sample timestamps in Mimir WAL
```

***REMOVED******REMOVED******REMOVED*** 3. Test Direct Mimir Ingestion

Bypass Alloy and push a test sample directly to Mimir:

```bash
cat <<EOF | curl -X POST http://127.0.0.1:9009/api/v1/push \
  -H 'Content-Type: application/x-protobuf' \
  -H 'X-Prometheus-Remote-Write-Version: 0.1.0' \
  --data-binary @-
***REMOVED*** (Prometheus remote write protobuf payload)
EOF
```

***REMOVED******REMOVED******REMOVED*** 4. Check Mimir Configuration

Review `monitoring/mimir/mimir.yaml` for:

- Query limits
- Retention policies
- Tenant-specific settings
- Ingester flush intervals

***REMOVED******REMOVED******REMOVED*** 5. Enable Mimir Debug Logging

Temporarily increase Mimir log level to debug ingestion:

```yaml
***REMOVED*** mimir.yaml
common:
  log_level: debug
```

***REMOVED******REMOVED******REMOVED*** 6. Check WAL Files

Inspect Mimir WAL directory for backend metrics:

```bash
ls -lah monitoring/data/mimir/ingester/
***REMOVED*** Check if backend samples are physically written
```

***REMOVED******REMOVED*** Workaround Options

***REMOVED******REMOVED******REMOVED*** Option A: Use OTel Auto-Instrumented Metrics

The backend ALSO exports OTel metrics via OTLP (different from prom-client). Check if these are
queryable:

```bash
curl -G 'http://127.0.0.1:9009/prometheus/api/v1/query' \
  --data-urlencode 'query=http_server_duration_milliseconds_count'
```

If yes, update dashboard to use:

- `http_server_duration_milliseconds_bucket` for latency
- Convert to rate/quantile accordingly

***REMOVED******REMOVED******REMOVED*** Option B: Direct Prometheus Scrape

Run a standalone Prometheus instance to scrape backend directly (bypass Mimir):

- Simpler troubleshooting
- Isolates whether issue is Alloy→Mimir or Mimir itself

***REMOVED******REMOVED******REMOVED*** Option C: Grafana Agent

Replace Alloy with Grafana Agent for scraping:

- Different remote write implementation
- May handle labels differently

***REMOVED******REMOVED*** Dashboard Configuration

**File**: `monitoring/grafana/dashboards/backend-api-performance.json`
**Datasource**: Mimir (uid: `MIMIR`)
**Variables**:

- `service`: Queries `label_values(scanium_http_request_duration_ms_bucket, service_name)`
- `env`: Custom values: `dev,stage,prod`
- `route`: Queries
  `label_values(scanium_http_request_duration_ms_bucket{service_name=~"$service"}, route)`
- `method`: Queries `label_values(scanium_http_request_duration_ms_bucket, method)`

**Sample Panel Query**:

```promql
histogram_quantile(0.95,
  sum by (le) (
    rate(scanium_http_request_duration_ms_bucket{
      service_name=~"$service",
      deployment_environment=~"$env",
      route=~"$route",
      method=~"$method"
    }[5m])
  )
)
```

***REMOVED******REMOVED*** Files Modified

- `monitoring/docker-compose.yml`: Added `compose_scanium_net` network to Alloy

***REMOVED******REMOVED*** Commands for Quick Verification

```bash
***REMOVED*** 1. Check if Alloy is scraping
ssh nas "curl -s http://127.0.0.1:12345/metrics | grep 'prometheus_forwarded_samples_total.*backend'"

***REMOVED*** 2. Check if series exist in Mimir
ssh nas "curl -sG 'http://127.0.0.1:9009/prometheus/api/v1/series' \
  --data-urlencode 'match[]=scanium_http_requests_total' | jq '.data | length'"

***REMOVED*** 3. Try to query metrics
ssh nas "curl -sG 'http://127.0.0.1:9009/prometheus/api/v1/query' \
  --data-urlencode 'query=scanium_http_requests_total' | jq '.data.result'"

***REMOVED*** 4. Check backend metrics endpoint
ssh nas "curl -s http://172.21.0.3:8080/metrics | grep -c scanium_http_requests_total"

***REMOVED*** 5. Generate fresh traffic
ssh nas "for i in {1..50}; do curl -s http://172.21.0.3:8080/health > /dev/null; sleep 0.2; done"
```

***REMOVED******REMOVED*** References

- Alloy config: `monitoring/alloy/config.alloy`
- Mimir config: `monitoring/mimir/mimir.yaml`
- Backend metrics: `backend/src/infra/observability/metrics.ts`
- Backend telemetry: `backend/src/infra/telemetry/index.ts`
