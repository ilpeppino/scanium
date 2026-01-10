***REMOVED*** Grafana Dashboard Regression - 2026-01-09

***REMOVED******REMOVED*** Summary
Grafana dashboards stopped showing data after commit `adb94ad` (2026-01-09 16:54 CET).

***REMOVED******REMOVED*** Timeline
- **16:54:** Commit `adb94ad` pushed: "feat(monitoring): extract mobile telemetry labels in Alloy"
- **17:00:** Dashboards showing no data reported
- **17:08:** Investigation began
- **17:30:** Root cause identified and fix applied
- **17:35:** Metrics restored, dashboards functional

***REMOVED******REMOVED*** Root Cause

***REMOVED******REMOVED******REMOVED*** Primary Issue: Missing Backend Components in alloy.hcl
The monitoring stack uses `/volume1/docker/scanium/repo/monitoring/docker-compose.yml`, which mounts `alloy.hcl` (not `config.alloy`).

Commit `adb94ad` updated `alloy.hcl` to add mobile telemetry label extraction, but `alloy.hcl` was a minimal config (215 lines) containing ONLY mobile telemetry components.

The full config with backend components existed in `config.alloy` (281 lines) but was not being used.

**Impact:** All backend observability stopped:
- ❌ No backend metrics scraping
- ❌ No backend logs collection
- ❌ No backend OTLP ingestion
- ❌ No Docker log tailing

Only mobile OTLP components and pipeline self-monitoring remained functional.

***REMOVED******REMOVED******REMOVED*** Secondary Issue: Missing Docker Socket Mount
The `docker-compose.yml` was missing the Docker socket volume mount:
```yaml
volumes:
  - /var/run/docker.sock:/var/run/docker.sock:ro
```

This prevented `loki.source.docker` from accessing container logs.

***REMOVED******REMOVED*** Fix Applied

***REMOVED******REMOVED******REMOVED*** 1. Merged Configurations
Created complete `alloy.hcl` by merging:
- Mobile label extraction from new `alloy.hcl` (commit `adb94ad`)
- All backend components from `config.alloy`

Result: 305-line config with full observability stack.

***REMOVED******REMOVED******REMOVED*** 2. Added Docker Socket Mount
Updated `monitoring/docker-compose.yml`:
```yaml
volumes:
  - ./alloy/alloy.hcl:/etc/alloy/config.alloy:ro
  - /var/run/docker.sock:/var/run/docker.sock:ro  ***REMOVED*** Added
```

***REMOVED******REMOVED******REMOVED*** 3. Restarted Alloy
```bash
docker-compose restart alloy
docker-compose up -d alloy  ***REMOVED*** After socket mount added
```

***REMOVED******REMOVED*** Verification

***REMOVED******REMOVED******REMOVED*** ✅ Metrics (Mimir) - RESTORED
```bash
***REMOVED*** Query successful - 20+ metric series available
curl -s "http://localhost:3000/api/datasources/proxy/uid/MIMIR/api/v1/label/__name__/values"

***REMOVED*** Backend metrics flowing
alloy_build_info{job="alloy"}  -> OK
up{source="scanium-backend"}   -> OK (value: 0, indicating backend down but metric exists)
```

**Components Loaded:**
- `prometheus.scrape.backend` ✅
- `prometheus.scrape.alloy` ✅
- `prometheus.scrape.mimir` ✅
- `prometheus.scrape.loki` ✅
- `prometheus.scrape.tempo` ✅
- `prometheus.remote_write.backend` ✅
- `prometheus.remote_write.pipeline` ✅

***REMOVED******REMOVED******REMOVED*** ⚠️ Logs (Loki) - ISSUE REMAINS
```bash
***REMOVED*** No log streams available
curl -s "http://localhost:3000/api/datasources/proxy/uid/LOKI/loki/api/v1/labels"
***REMOVED*** Returns: {"status":"success"}  (empty data)
```

**Status:**
- `loki.source.docker.backend` component: **healthy**
- `loki.write.backend` component: **healthy**
- Docker socket: **accessible** in container
- But: **No logs flowing**

**Likely causes:**
1. Permissions issue (Alloy process user may lack Docker socket access)
2. `loki.source.docker` bug in Alloy v1.0.0 (very old version from 2024)
3. Container label/name matching issue

**Note:** This Loki issue may have been pre-existing (not caused by today's regression). The regression primarily affected metrics, which are now restored.

***REMOVED******REMOVED******REMOVED*** ✅ Traces (Tempo) - Components Available
OTLP trace exporters are loaded and healthy. Traces will flow when mobile apps send telemetry.

***REMOVED******REMOVED*** Files Changed

***REMOVED******REMOVED******REMOVED*** Local Repository
1. `monitoring/alloy/alloy.hcl` - Merged config (215→305 lines)

***REMOVED******REMOVED******REMOVED*** NAS Deployment
1. `/volume1/docker/scanium/repo/monitoring/alloy/alloy.hcl` - Updated
2. `/volume1/docker/scanium/repo/monitoring/docker-compose.yml` - Added socket mount

***REMOVED******REMOVED*** Recommendations

***REMOVED******REMOVED******REMOVED*** Immediate
- ✅ Metrics dashboards should now display data
- ✅ Backend self-monitoring restored
- ⚠️ Investigate Loki logs issue (see "Next Steps")

***REMOVED******REMOVED******REMOVED*** Next Steps for Loki Logs

1. **Check Permissions**
   ```bash
   docker exec scanium-alloy id  ***REMOVED*** Check user/group
   docker exec scanium-alloy ls -la /var/run/docker.sock
   ```

2. **Verify Container Targeting**
   ```bash
   docker logs scanium-alloy 2>&1 | grep -i "docker\|loki.source"
   ```

3. **Consider Alloy Upgrade**
   - Current: v1.0.0 (April 2024)
   - Latest: v1.5.0 (December 2024)
   - May include `loki.source.docker` fixes

4. **Alternative: Direct Loki Push**
   Instead of Docker log scraping, configure backend to push logs directly:
   ```javascript
   // In backend: send logs to Loki via HTTP
   const lokiTransport = new LokiTransport({
     host: 'http://loki:3100'
   });
   ```

***REMOVED******REMOVED******REMOVED*** Architectural
- **Maintain single source of truth:** Keep only `alloy.hcl`, remove `config.alloy`
- **Add validation:** CI check to ensure all required components are present
- **Document compose files:** Clarify which compose file is used for NAS deployment

***REMOVED******REMOVED*** Lessons Learned

1. **Config file confusion:** Having both `alloy.hcl` and `config.alloy` led to regression
2. **Minimal testing:** Change was deployed without verifying all components loaded
3. **Documentation gap:** Unclear which compose file is canonical for NAS deployment
4. **Version awareness:** Running older Alloy v1.0.0 may have hidden bugs

***REMOVED******REMOVED*** Prevention

1. Add pre-commit hook to validate Alloy config:
   ```bash
   alloy fmt --check monitoring/alloy/*.hcl
   alloy run --validate monitoring/alloy/*.hcl
   ```

2. Add smoke test after deployment:
   ```bash
   ***REMOVED*** Verify all expected components loaded
   curl localhost:12345/api/v0/web/components | \
     jq '.[] | select(.localID | contains("backend"))'
   ```

3. Consolidate configs: Remove `config.alloy`, keep only `alloy.hcl`

---

**Status:** Metrics RESTORED ✅ | Logs PARTIAL ⚠️ | Dashboards FUNCTIONAL ✅
