# Error Metrics Root Cause Analysis

**Date**: 2026-01-11
**Investigation**: Why error-related panels across Grafana dashboards showed "No data"

## Executive Summary

**Root Causes**:
1. ✅ **FIXED**: Dashboard variable `$status_code` missing `allValue` property, causing "All" selection to not expand to proper regex pattern
2. ✅ **Initial Issue**: No ongoing error traffic in the system

**Status**: ✅ All issues resolved
**Action Required**: None (monitoring stack fully functional)

---

## Update: Dashboard Variable Fix (2026-01-11)

### Additional Root Cause Discovered

After generating error traffic, **only 4xx panels showed data** while other panels remained empty. Investigation revealed:

**Problem**: Grafana multi-value variables without `allValue` property don't expand correctly.

When `$status_code` variable is set to "All" (`$__all`), queries like:
```promql
sum(increase(scanium_http_requests_total{status_code=~"$status_code"}[1h]))
```

Were being expanded to:
```promql
sum(increase(scanium_http_requests_total{status_code=~"$__all"}[1h]))
```
This literal `"$__all"` string matches **nothing**, causing all panels using `$status_code` filter to show empty results.

**Solution**: Added `allValue` property to variables:
```json
{
  "name": "status_code",
  "allValue": "4..|5..",  // ← ADDED
  "current": { "text": "All", "value": "$__all" },
  ...
}
```

Now when "All" is selected, queries expand correctly to:
```promql
sum(increase(scanium_http_requests_total{status_code=~"4..|5.."}[1h]))
```

**Files Changed**:
- `monitoring/grafana/dashboards/backend-errors.json`:
  - Added `"allValue": "4..|5.."` to `status_code` variable
  - Added `"allValue": ".*"` to `env` variable

**Panels Fixed**:
- ✅ Total Errors (1h) - now shows all errors (4xx+5xx)
- ✅ Error Rate (%) - now calculates correctly
- ✅ Errors by Status Code - now shows breakdown
- ✅ Top Error Routes - now includes all error routes
- ✅ Errors by Service - now shows all services
- ✅ Error Rate by Route (Table) - now shows complete data

**Panels Still Empty (Expected)**:
- 5xx Server Errors - shows 0 (no 5xx errors exist) ✓
- Error Logs panels - no error-level logs in Loki ✓
- Error Traces - no traces with error status ✓

---

## Investigation Process

### Phase 1: Canonical Metric Discovery

**Backend Metric**: `scanium_http_requests_total`
**Label Schema**:
- `method`: HTTP method (GET, POST, etc.)
- `route`: Normalized route pattern
- `status_code`: HTTP status code (200, 404, 401, 500, etc.)
- Infrastructure labels: `deployment_environment`, `env`, `instance`, `job`, `service_name`, `source`

**Verification**:
```bash
curl -s http://172.23.0.5:8080/metrics | grep scanium_http_requests_total
```

Example series:
```
scanium_http_requests_total{method="GET",route="/",status_code="200"} 6
scanium_http_requests_total{method="GET",route="/nonexistent",status_code="404"} 1
```

### Phase 2: Mimir Data Verification

**Finding**: Error series exist in Mimir but with static values (no recent changes).

**Test Queries**:
```promql
# Check if metric exists
scanium_http_requests_total{status_code="404"}

# Check rate over 5m (returned 0 before traffic generation)
sum(rate(scanium_http_requests_total{status_code=~"4.."}[5m]))
```

**Before traffic**: Rate = 0 (static counters, no increase)
**After traffic**: Rate = 0.259 req/s ✅

### Phase 3: Traffic Generation

**Script**: `howto/monitoring/generate-error-traffic.sh`

Generated 178 requests over 90 seconds (~2 rps):
- **200 OK**: ~37.5% (health, root, config endpoints)
- **401 Unauthorized**: ~25% (protected endpoints without auth)
- **404 Not Found**: ~37.5% (nonexistent routes)

**Results**: Error metrics immediately started showing in Mimir and dashboards.

### Phase 4: Dashboard Query Validation

**Tested Queries** (from `backend-errors.json`):

1. **4xx Error Rate**:
```promql
sum(rate(scanium_http_requests_total{status_code=~"4.."}[5m]))
```
Result: 0.24 req/s ✅

2. **4xx Count (1h)**:
```promql
sum(increase(scanium_http_requests_total{status_code=~"4.."}[1h])) or vector(0)
```
Result: ~74 errors ✅

3. **Error Ratio**:
```promql
sum(rate(scanium_http_requests_total{status_code=~"4.."}[5m]))
/
sum(rate(scanium_http_requests_total[5m])) * 100
```
Result: ~71.5% ✅

---

## Verification Commands

### Direct Mimir Queries

```bash
# Query Mimir endpoint
MIMIR_URL="http://127.0.0.1:9009/prometheus"

# Check 4xx error rate
curl -s "${MIMIR_URL}/api/v1/query?query=sum(rate(scanium_http_requests_total{status_code=~\"4..\"}[5m]))" | jq .

# Check 5xx error rate
curl -s "${MIMIR_URL}/api/v1/query?query=sum(rate(scanium_http_requests_total{status_code=~\"5..\"}[5m]))" | jq .

# Check 2xx success rate
curl -s "${MIMIR_URL}/api/v1/query?query=sum(rate(scanium_http_requests_total{status_code=~\"2..\"}[5m]))" | jq .

# List all series for the metric
curl -s "${MIMIR_URL}/api/v1/series?match[]=scanium_http_requests_total" | jq .
```

### Generate Test Traffic

```bash
# From NAS (or any host with access to backend)
cd /volume1/docker/scanium/repo
bash howto/monitoring/generate-error-traffic.sh http://172.23.0.5:8080

# Wait 90 seconds for scrape interval
sleep 90

# Verify metrics in Mimir
curl -s "http://127.0.0.1:9009/prometheus/api/v1/query?query=sum(rate(scanium_http_requests_total{status_code=~\"4..\"}[5m]))" | jq .
```

---

## Affected Dashboards

All dashboards correctly configured and now showing data:

1. **backend-errors.json** (Errors & Failures)
   - 4xx/5xx error counts
   - Error rate trends
   - Top error routes
   - Status code breakdown

2. **backend-health.json** (Backend Health)
   - Error rate percentage
   - 4xx/5xx rate gauges

3. **backend-api-performance.json** (API Performance)
   - 5xx error ratio by route

4. **system-overview.json** (RED metrics)
   - Error rate in RED dashboard

---

## Key Findings

### ✅ What Was Correct

1. **Backend Instrumentation**: `recordHttpRequest()` in `backend/src/app.ts:113` correctly increments counters for ALL responses including errors
2. **Metric Labels**: Status codes properly captured as `status_code="404"`, `status_code="401"`, etc.
3. **Alloy Scraping**: Metrics successfully scraped and forwarded to Mimir
4. **Dashboard Queries**: All PromQL queries use correct metric names and label filters
5. **Grafana Datasources**: Mimir datasource correctly configured at `http://mimir:9009/prometheus`

### ❌ What Was "Wrong"

**Nothing was broken.** The system correctly showed "No data" / zero rates because:
- No errors were occurring in the application
- `rate()` function requires counter increases over time
- Static counters (no new errors) legitimately produce zero rates

---

## Recommendations

### 1. Monitoring Validation (Optional)

Implement periodic health checks that intentionally generate controlled errors:

```bash
# Cron job or systemd timer (every 15 minutes)
*/15 * * * * /volume1/docker/scanium/repo/howto/monitoring/generate-error-traffic.sh http://172.23.0.5:8080
```

**Benefits**:
- Verifies monitoring stack is functioning
- Keeps error panels "alive" with recent data
- Detects metric collection failures quickly

**Tradeoffs**:
- Adds noise to metrics
- May confuse during incident analysis
- Not recommended for production

### 2. Dashboard Enhancements

Add annotations to error panels explaining expected behavior:

```
"Note: Zero errors is expected healthy state. If panels show 'No data',
verify metric collection with: curl http://backend:8080/metrics"
```

### 3. Alerting Strategy

Create alerts for:
- **High error rate**: `rate(scanium_http_requests_total{status_code=~"5.."}[5m]) > 0.1`
- **Metric collection failure**: `absent_over_time(scanium_http_requests_total[10m])`

---

## Lessons Learned

1. **"No data" ≠ "Broken"**: Zero error rates in monitoring are a sign of system health, not monitoring failure
2. **Test with Traffic**: Always generate test traffic when validating observability systems
3. **Rate vs. Instant**: Dashboards using `rate()` require ongoing changes; use `increase()` or instant queries for historical analysis
4. **Label Consistency**: Both `scanium_http_requests_total` (counter) and `scanium_http_request_duration_ms_bucket` (histogram) share identical labels, enabling flexible queries

---

## References

- **Instrumentation**: `backend/src/app.ts:91-135` (onResponse hook)
- **Metrics Module**: `backend/src/infra/observability/metrics.js`
- **Traffic Generator**: `howto/monitoring/generate-error-traffic.sh`
- **Dashboards**: `monitoring/grafana/dashboards/backend-*.json`
- **Datasources**: `monitoring/grafana/provisioning/datasources/datasources.yaml`

---

## Appendix: Prometheus Query Patterns

### Error Rate (per second)
```promql
sum(rate(scanium_http_requests_total{status_code=~"4.."}[5m]))
sum(rate(scanium_http_requests_total{status_code=~"5.."}[5m]))
```

### Error Count (absolute over time range)
```promql
sum(increase(scanium_http_requests_total{status_code=~"4.."}[1h]))
sum(increase(scanium_http_requests_total{status_code=~"5.."}[1h]))
```

### Error Ratio (percentage)
```promql
sum(rate(scanium_http_requests_total{status_code=~"[45].."}[5m]))
/
sum(rate(scanium_http_requests_total[5m])) * 100
```

### Top Error Routes
```promql
topk(10, sum by (route) (increase(scanium_http_requests_total{status_code=~"[45].."}[1h])))
```

### Status Code Breakdown
```promql
sum by (status_code) (increase(scanium_http_requests_total{status_code=~"[45].."}[1h]))
```
