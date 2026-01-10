# Incident Report: Dashboard Data Feed Issues - 2026-01-10

**Status:** RESOLVED (Metrics), DOCUMENTED (Logs)
**Date:** 2026-01-10
**Duration:** ~3 hours investigation + remediation
**Severity:** High (Complete loss of dashboard visibility)

## Summary

Grafana dashboards showed no data due to two critical issues:
1. **Mimir metrics**: Orphaned container + misconfigured querier (FIXED)
2. **Loki logs**: Docker source not ingesting logs (DOCUMENTED)

## Root Causes

### 1. Mimir Metrics Issue (FIXED)

#### Problem Chain
1. **Orphaned Container**: Old `c8a6ca9c65cf_scanium-mimir` container remained on `compose_scanium_net` network after previous deployments
2. **DNS Resolution**: Alloy resolved `mimir` hostname to old container (172.21.0.7) instead of new container (172.24.0.4)
3. **No Data Flow**: Alloy remote_write sent metrics to old/stopped container, new Mimir received 0 samples/sec
4. **Querier Misconfiguration**: Even if data reached Mimir, querier wasn't configured to read from ingesters for recent data

#### Evidence
- **PHASE 0 Baseline**:
  - Queries returned empty despite series existing in label index
  - `up{job="scanium-backend"}` → 0 results
  - `/prometheus/api/v1/series` showed series existed

- **PHASE 1 Investigation**:
  - Mimir config missing `querier:` section
  - Ingester ring healthy and ACTIVE
  - `cortex_distributor_ingestion_rate_samples_per_second: 0`

- **PHASE 2 Discovery**:
  - Two Mimir containers found: `scanium-mimir` (new) and `c8a6ca9c65cf_scanium-mimir` (old)
  - Alloy resolving to old container at 172.21.0.7
  - Alloy WAL had 15k+ samples but wasn't sending to Mimir

#### Solution Applied
1. **Removed orphaned containers**:
   ```bash
   docker rm -f c8a6ca9c65cf_scanium-mimir 655e9cd7ee4d_scanium-loki cc72f69bcfa6_scanium-tempo
   ```

2. **Added querier CLI flags** to `monitoring/docker-compose.yml`:
   ```yaml
   command:
     - -config.file=/etc/mimir/config.yaml
     - -target=all
     - -querier.query-ingesters-within=12h
     - -querier.query-store-after=0s
   ```

3. **Connected Mimir to compose_scanium_net**:
   ```yaml
   networks:
     - observability
     - compose_scanium_net
   ```

4. **Fixed Alloy healthcheck endpoint**: `/ready` → `/-/ready`

5. **Restarted services**: Mimir → Alloy (to trigger WAL replay)

#### Verification (POST-FIX)
```bash
# Ingestion restored
curl http://127.0.0.1:9009/metrics | grep cortex_distributor_ingestion_rate
cortex_distributor_ingestion_rate_samples_per_second 354.7

# Queries now work
curl "http://127.0.0.1:9009/prometheus/api/v1/query?query=up%7Bjob%3D%22scanium-backend%22%7D"
# Returns: {"status":"success","data":{"result":[...]}}

# Backend metrics available
curl "http://127.0.0.1:9009/prometheus/api/v1/query?query=scanium_http_requests_total"
# Returns: 7+ series with detailed route/method/status breakdowns
```

### 2. Loki Logs Issue (DOCUMENTED - Not Fixed)

#### Problem
- Loki has zero labels and returns no data for all LogQL queries
- `loki_source_docker_target_entries_total: 0` (Alloy docker source sent 0 entries)
- Manual push to Loki API failed with JSON parsing error

#### Investigation Findings
- Docker socket accessible in Alloy container (verified)
- `loki.source.docker.backend` component healthy (no errors in logs)
- Backend container producing logs (verified via `docker logs`)
- Loki `/api/v1/labels` returns `{"status":"success"}` with no data array

#### Hypothesis
- Docker source configuration may need adjustment (container label selectors, target filters)
- OR Loki API version incompatibility (manual push failed with parse error)
- OR Docker event stream not being monitored correctly

#### Recommended Next Steps
1. Enable Alloy debug logging for `loki.source.docker` component
2. Test with OTLP logs export from backend instead of Docker source
3. Verify Loki 2.9.3 API compatibility with Alloy v1.0.0
4. Consider upgrading to `loki.source.docker` with explicit event filtering

## Impact Assessment

### Fixed (Metrics)
✅ **Backend API Performance Dashboard**
- `up{job="scanium-backend"}` → Working
- `scanium_http_requests_total` → 7+ series with route/method/status breakdowns
- Request rates, error rates, latency available

✅ **Backend Health Dashboard**
- All health check metrics queryable
- Uptime tracking restored

✅ **System Overview (RED) Dashboard**
- `up{source="pipeline"}` → All 4 components (alloy, loki, mimir, tempo) showing
- Monitoring stack self-observability restored

### Known Limitation (Logs)
❌ **Logs Explorer Dashboard**
- Loki has no data (0 labels, 0 streams)
- Log-based alerts not functioning
- Requires separate investigation

## Files Changed

1. `monitoring/docker-compose.yml`
   - Added Mimir querier CLI flags
   - Connected Mimir to `compose_scanium_net`
   - Fixed Alloy healthcheck endpoint

2. `monitoring/incident_data/baseline_phase0.txt`
   - Baseline evidence capture

3. `monitoring/incident_data/INCIDENT_DASHBOARDS_NO_DATA_20260110.md`
   - This report

## Lessons Learned

1. **Container Lifecycle Management**: Orphaned containers from `docker-compose up -d --force-recreate` can persist if not explicitly pruned. Use `docker-compose down && docker-compose up -d` or `docker system prune` regularly.

2. **Network Topology Matters**: When services span multiple compose files, ensure all required networks are declared. Alloy needed access to both observability stack AND backend network.

3. **Querier Configuration Critical**: Mimir's default querier settings may not query ingesters, making recent data invisible even when ingestion succeeds.

4. **DNS Resolution in Docker**: Multiple containers with similar names can cause DNS resolution to pick stale containers. Always verify with `docker exec <container> getent hosts <target>`.

5. **Healthcheck Endpoints**: Verify exact endpoint paths (`/-/ready` vs `/ready`) against official documentation.

## Prevention Measures

1. Add pre-deployment cleanup step:
   ```bash
   docker ps -a --filter "status=exited" --filter "name=scanium-" -q | xargs -r docker rm
   ```

2. Add network connectivity tests to deployment pipeline

3. Document required Mimir querier flags in compose file with comments

4. Consider using explicit container IPs in scrape configs instead of DNS

## Related Issues

- Alloy unhealthy status (healthcheck endpoint) - FIXED
- Old Mimir/Loki/Tempo containers not cleaned up - FIXED
- Loki ingestion from Docker source - OPEN (see hypothesis section)

## Sign-off

**Root Cause Confirmed**: Yes (orphaned container + querier misconfiguration)
**Fix Validated**: Yes (354.7 samples/sec ingestion, queries returning data)
**Rollback Plan**: Not needed (fix is minimal config change)
**Production Impact**: Medium (dashboards restored, logs still unavailable)
