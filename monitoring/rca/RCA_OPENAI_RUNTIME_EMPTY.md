# RCA: OpenAI Runtime Dashboard Empty

**Date:** 2026-01-12
**Status:** RESOLVED
**Severity:** Medium

## Symptoms

- Dashboard "Scanium - OpenAI Runtime" (openai-runtime.json) shows no data
- All panels empty (request rate, latency, tokens, errors)
- Rate limit panels also empty

## Investigation Timeline

### Phase 1: Data Existence Testing

**Backend Status:**
- Backend healthy and running on port 8080
- Metrics endpoint `/metrics` accessible and properly configured
- Prometheus metrics registry includes all expected assistant metrics:
  - `scanium_assistant_requests_total{provider, status, cache_hit}`
  - `scanium_assistant_request_latency_ms_bucket{provider, status}`
  - `scanium_assistant_tokens_used{provider, token_type}`
  - `scanium_assistant_errors_total{provider, error_type, reason_code}`

**Metrics Data:**
```bash
$ curl http://localhost:8080/metrics | grep scanium_assistant
# All metrics defined but showing 0 values - NO TRAFFIC YET
scanium_assistant_cache_hit_rate 0
```

**Alloy Scraping:**
```alloy
// WRONG - Line 242 in config.alloy
__address__ = "scanium-backend:8080"

// Backend container network alias is "backend" not "scanium-backend"
// This causes scraping to fail silently
```

**Mimir Query Results:**
```bash
$ curl 'http://localhost:9009/prometheus/api/v1/query?query=scanium_assistant_requests_total'
# Returns empty result []
```

## Root Cause

**TWO issues identified:**

1. **Alloy Scraping Misconfiguration (PRIMARY)**
   - File: `monitoring/alloy/config.alloy:242`
   - Issue: Uses hostname `scanium-backend:8080` which doesn't resolve
   - Backend container has network alias `backend` (not `scanium-backend`)
   - Result: Prometheus scraping fails, no metrics ingested into Mimir

2. **No Traffic Generated (SECONDARY)**
   - No OpenAI assistant requests have been made since deployment
   - Metrics exist but show zero values
   - Need to generate test traffic to validate full pipeline

## Evidence

### Network Configuration
```bash
$ docker inspect scanium-backend --format '{{range $net, $conf := .NetworkSettings.Networks}}{{$net}}: {{range $conf.Aliases}}{{.}} {{end}}{{end}}'
backend_scanium-network: backend d71803cc5041
compose_scanium_net: backend d71803cc5041
```

### Dashboard Queries
Dashboard `openai-runtime.json` expects:
- `scanium_assistant_requests_total{provider=~"$provider"}` - Panel ID 2, 11
- `scanium_assistant_request_latency_ms_bucket{provider=~"$provider"}` - Panel ID 3, 21
- `scanium_assistant_tokens_used_sum{provider=~"$provider", token_type}` - Panel ID 5, 31
- `openai_rate_limit_requests_remaining{provider=~"$provider"}` - Panel ID 41
- `openai_rate_limit_tokens_remaining{provider=~"$provider"}` - Panel ID 42

All these metrics are properly defined in `backend/src/infra/observability/metrics.ts` and recorded in `backend/src/modules/assistant/openai-provider.ts`.

## Fix Implementation

### 1. Fix Alloy Scraping Configuration
**File:** `monitoring/alloy/config.alloy:242`

**Change:**
```diff
- __address__      = "scanium-backend:8080",
+ __address__      = "backend:8080",
```

### 2. Generate Test Traffic
**Script:** `scripts/monitoring/generate-openai-traffic.sh`
- Generate 10-20 successful OpenAI assistant requests
- Generate 2-3 controlled error scenarios (invalid input, auth test)
- Duration: 60-90 seconds
- No sensitive data logged

### 3. Verify Pipeline
- Metrics appear in Mimir after scrape interval (~60s)
- Logs appear in Loki with proper labels
- Dashboard panels populate with data

### 4. Add Regression Tests
**Script:** `scripts/monitoring/prove-openai-dashboard.sh`
- Query Mimir for `scanium_assistant_requests_total` > 0
- Verify dashboard panels can query successfully
- Integrate into `scripts/monitoring/e2e-monitoring.sh`

## Validation Steps

### Local (NAS)
```bash
# 1. Deploy fix
cd /volume1/docker/scanium/repo
docker-compose -f monitoring/docker-compose.yml restart alloy

# 2. Wait for scrape interval (60s)
sleep 70

# 3. Verify metrics in Mimir
curl -s 'http://localhost:9009/prometheus/api/v1/query?query=up{job="scanium-backend"}' | jq .

# 4. Generate traffic
bash scripts/monitoring/generate-openai-traffic.sh

# 5. Run proof script
bash scripts/monitoring/prove-openai-dashboard.sh

# 6. Open Grafana locally
# Navigate to "Scanium - OpenAI Runtime" dashboard
# Verify panels show data in last 15m
```

### Remote (grafana.gtemp1.com)
```bash
# 1. Check cloudflared status
curl -sf https://grafana.gtemp1.com/api/health

# 2. Open dashboard on mobile
# - Navigate to grafana.gtemp1.com
# - Select "Scanium - OpenAI Runtime"
# - Verify data appears in panels
```

## Prevention

1. **Network Alias Documentation**
   - Backend always uses alias `backend` on shared networks
   - Monitoring uses `scanium-*` container names but `backend`/`loki`/`mimir` etc. for aliases

2. **Scraping Health Checks**
   - Monitor `up{job="scanium-backend"}` metric in pipeline dashboard
   - Alert if scraping fails for > 5 minutes

3. **Traffic Generation in E2E**
   - `e2e-monitoring.sh` now includes OpenAI traffic generation
   - Validates full pipeline end-to-end

4. **Dashboard Variable Defaults**
   - Ensure `provider` variable defaults to `All` or `openai`
   - Prevent empty results from restrictive filters

## Related Issues

- Backend/Mobile dashboards were similarly affected (fixed in e75ba9b)
- Used same systematic approach: test → generate → fix → validate

## Timeline

- **2026-01-12 20:00 UTC**: Issue reported (dashboard empty)
- **2026-01-12 20:08 UTC**: Root cause identified (Alloy config + no traffic)
- **2026-01-12 20:15 UTC**: Fix implemented and validated

## Status

✅ **RESOLVED** - Dashboard now shows data after Alloy config fix and traffic generation
