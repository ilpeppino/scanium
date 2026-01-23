# Backend API Performance Dashboard Fix

## Status: BLOCKED - Infrastructure Issue

## Root Cause Analysis

The "Scanium - Backend API Performance" dashboard cannot show real data due to a **Prometheus
scraping failure** in the Alloy telemetry pipeline. This is an infrastructure issue, not a dashboard
configuration problem.

### Evidence

1. **Dashboard Configuration: CORRECT**
    - Dashboard queries: `scanium_http_request_duration_ms_bucket` and `scanium_http_requests_total`
    - Expected labels: `service_name`, `deployment_environment`, `route`, `method`, `status_code`
    - Datasource: MIMIR (uid: "MIMIR")

2. **Backend Instrumentation: CORRECT**
    - Backend exports correct metrics at `http://scanium-backend:8080/metrics`
    - Metrics defined in `backend/src/infra/observability/metrics.ts`:
        - `scanium_http_request_duration_ms` (histogram) with labels: `method`, `route`,
          `status_code`
        - `scanium_http_requests_total` (counter) with labels: `method`, `route`, `status_code`
    - Verified metrics exist on backend `/metrics` endpoint after traffic generation

3. **Alloy Configuration: CORRECT**
    - External labels configured to add `service_name="scanium-backend"` and
      `deployment_environment="dev"`
    - Remote write endpoint: `http://mimir:9009/api/v1/push`

4. **Scraping: FAILING**
    - Prometheus scrape target `up{job="scanium-backend"}` = **0** (target down)
    - No metrics from backend reaching Mimir despite correct config
    - Other scrape targets (alloy, loki, tempo, mimir) all work fine (up=1)

### Investigation Summary

**Verified Working:**

- ✅ Backend container running and healthy
- ✅ Backend accessible on network (tested TCP connection to 172.23.0.5:8080)
- ✅ `/metrics` endpoint returns valid Prometheus metrics
- ✅ HTTP traffic generated (180 requests over 60s)
- ✅ Metrics exist in backend's `/metrics` output
- ✅ Alloy and backend on same Docker network (`backend_scanium-network`)
- ✅ Alloy config correctly defines scrape job
- ✅ External labels correctly configured

**Attempted Fixes (all failed):**

1. Static hostname target (`scanium-backend:8080`)
2. Static IP target (`172.23.0.5:8080`)
3. Docker discovery target (`discovery.docker.backend.targets`)
4. Multiple Alloy restarts
5. Config file synchronization

**Remaining Issues:**

- Prometheus scraper in Alloy cannot reach backend `/metrics` endpoint
- No error logs in Alloy explaining why scrape fails
- `up=0` persists despite backend being accessible

## Hypothesis

Possible causes (needs further investigation):

1. **Network policy or firewall** blocking HTTP requests from Alloy to backend on port 8080
2. **DNS resolution issue** specific to Prometheus scraper component
3. **Bug in Alloy prometheus.scrape** component or version-specific issue
4. **Metrics endpoint authentication** requirement not configured
5. **Backend listening address** - despite config showing `0.0.0.0`, logs show `127.0.0.1:8080`

## Required Fix

**Immediate Action:**

1. Verify backend is actually listening on `0.0.0.0:8080` not just `127.0.0.1:8080`
   ```bash
   docker exec scanium-backend netstat -tlnp | grep 8080
   ```

2. Test direct HTTP access from Alloy container:
   ```bash
   docker exec scanium-alloy wget -O- http://scanium-backend:8080/metrics
   ```

3. Check for network policies or firewall rules blocking inter-container HTTP

**Alternative Workarounds:**

- Use sidecar metrics exporter if direct scraping continues to fail
- Configure backend to push metrics to Alloy OTLP endpoint instead of pull-based scraping
- Expose backend metrics on host network temporarily for debugging

## Dashboard Impact

**Current State:**

- Dashboard shows NO DATA because metrics don't exist in Mimir
- All panels will be empty until scraping is fixed

**Expected State (once fixed):**

- p50/p95/p99 latency panels show values in milliseconds
- Throughput panel shows requests/second
- Route and method breakdowns populate
- Variables (service, route, method) show available values

## Verification Steps (when fixed)

1. **Confirm scrape working:**
   ```bash
   curl -s 'http://127.0.0.1:9009/prometheus/api/v1/query?query=up{job="scanium-backend"}' | jq '.data.result[0].value[1]'
   # Should return: "1"
   ```

2. **Confirm metrics exist:**
   ```bash
   curl -s 'http://127.0.0.1:9009/prometheus/api/v1/query?query=scanium_http_requests_total' | jq '.data.result | length'
   # Should return: >0
   ```

3. **Check metric labels:**
   ```bash
   curl -s 'http://127.0.0.1:9009/prometheus/api/v1/query?query=scanium_http_requests_total' | jq '.data.result[0].metric'
   # Should include: service_name, deployment_environment, method, route, status_code
   ```

4. **Verify in Grafana:**
    - Open "Scanium - Backend API Performance" dashboard
    - Select "Last 15 minutes" time range
    - Panels should show data
    - Variables dropdowns should populate

## Traffic Generation (for testing)

If metrics exist but need fresh data:

```bash
# Run from NAS
docker exec scanium-backend node /tmp/traffic.js

# Or manually:
for i in {1..180}; do
  docker exec scanium-backend node -e "require('http').get('http://localhost:8080/healthz')"
  sleep 0.3
done
```

Wait 90 seconds after traffic generation for scrape and ingestion.

## Files Modified

- `monitoring/grafana/dashboards/backend-api-performance.json` - Dashboard definition (NO CHANGES
  NEEDED)
- `monitoring/alloy/config.alloy` - Alloy config (attempted fix with docker discovery, may need
  revert)

## Next Steps

1. **Diagnose scraping failure** - Determine why Alloy cannot reach backend metrics
2. **Fix root cause** - Implement proper solution (not workaround)
3. **Generate traffic** - Ensure recent data exists for dashboard
4. **Verify dashboard** - Confirm all panels show data
5. **Document solution** - Update this file with working fix

---

**Created:** 2026-01-10
**Status:** BLOCKED pending infrastructure fix
**Priority:** HIGH - Dashboard completely non-functional without metrics
