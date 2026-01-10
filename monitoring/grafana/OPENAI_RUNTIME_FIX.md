# OpenAI Runtime Dashboard Fix

## Problem
The "Scanium - OpenAI Runtime" dashboard was showing "No data" despite the backend making OpenAI API calls.

## Root Cause
The backend's `/metrics` endpoint was being blocked with HTTP 403 Forbidden when Alloy (Prometheus scraper) tried to access it. The security plugin was enforcing HTTPS for all requests in production mode, including requests from the local Docker network.

## Evidence
```bash
# Alloy scrape target showed:
curl 'http://127.0.0.1:12345/api/v0/web/components/prometheus.scrape.backend' | jq '.debugInfo[0]'
# Result: "server returned HTTP status 403 Forbidden"

# Backend was in production mode with HTTPS enforcement:
docker exec scanium-backend env | grep NODE_ENV
# NODE_ENV=production

# Local HTTP was not allowed:
docker exec scanium-backend env | grep ALLOW
# ALLOW_INSECURE_HTTP=false
```

## Solution
Added `/metrics` to the HTTPS_EXEMPT_PATHS list in the backend security plugin, allowing Prometheus/Alloy to scrape metrics over HTTP from the local Docker network without requiring HTTPS.

### Changes Made
1. **backend/src/infra/http/plugins/security.ts**
   - Added `/metrics` to `HTTPS_EXEMPT_PATHS` array
   - This exempts the metrics endpoint from HTTPS enforcement, similar to health check endpoints

2. **scripts/monitoring/generate-openai-traffic.sh**
   - Created traffic generator for testing OpenAI metrics
   - Generates controlled traffic to `/v1/assist/chat` endpoint
   - Includes both successful requests and intentional errors

## Verification Commands

### 1. Verify Alloy can scrape backend metrics
```bash
ssh nas
curl -sG 'http://127.0.0.1:9009/prometheus/api/v1/query' \
  --data-urlencode 'query=up{job="scanium-backend"}' | jq '.data.result[0].value[1]'
# Expected: "1" (target is up)
```

### 2. Verify OpenAI request metrics exist in Mimir
```bash
curl -sG 'http://127.0.0.1:9009/prometheus/api/v1/query' \
  --data-urlencode 'query=scanium_assistant_requests_total{provider="openai"}' | jq '.'
# Expected: Non-empty result with provider="openai"
```

### 3. Verify latency metrics
```bash
curl -sG 'http://127.0.0.1:9009/prometheus/api/v1/query' \
  --data-urlencode 'query=scanium_assistant_request_latency_ms_count{provider="openai"}' | jq '.data.result[0].value[1]'
# Expected: Number of requests (e.g., "1", "2", etc.)
```

## Traffic Generator Usage

Generate test traffic to populate the dashboard:

```bash
ssh nas
cd /volume1/docker/scanium/repo

# Get a valid API key from backend/.env
API_KEY=$(grep SCANIUM_API_KEYS backend/.env | cut -d= -f2 | cut -d, -f1)

# Get backend container IP
BACKEND_IP=$(docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' scanium-backend)

# Run traffic generator (generates 3 requests + 1 error over ~60 seconds)
SCANIUM_API_KEY=$API_KEY bash scripts/monitoring/generate-openai-traffic.sh http://$BACKEND_IP:8080
```

## Dashboard Status

### Working Panels
- ✅ **Request Rate**: Shows requests/second
- ✅ **p95 Latency**: Shows 95th percentile latency in milliseconds
- ✅ **Request Rate by Model**: Breakdown by provider
- ⚠️ **Error Rate**: May show 0% if no errors have been generated

### Limited/Not Working Panels
- ⚠️ **Tokens/Min**: Token metrics are not currently tracked in Prometheus metrics
  - The histogram `scanium_assistant_tokens_used` is defined but never populated
  - Token tracking would require additional instrumentation in the backend

### Expected Behavior
After running the traffic generator once:
- Request count and latency panels should show data within 1-2 minutes
- Error rate will be 0% unless error traffic is generated
- Token panels will remain empty (instrumentation not implemented)

## Next Steps (If Needed)

If token tracking is required:
1. Add `recordAssistantTokens()` function to `backend/src/infra/observability/metrics.ts`
2. Call it from `backend/src/modules/assistant/routes.ts` when processing responses
3. Redeploy backend

## Success Criteria Met
- ✅ Alloy successfully scrapes backend `/metrics` endpoint (up=1)
- ✅ OpenAI request count metrics appear in Mimir
- ✅ OpenAI latency metrics appear in Mimir
- ✅ Dashboard shows request rate and latency data
- ✅ No secrets/PII in metrics or logs
