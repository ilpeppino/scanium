***REMOVED*** OpenAI Runtime Dashboard Fix

***REMOVED******REMOVED*** Problem
The "Scanium - OpenAI Runtime" dashboard was showing "No data" despite the backend making OpenAI API calls.

***REMOVED******REMOVED*** Root Cause
The backend's `/metrics` endpoint was being blocked with HTTP 403 Forbidden when Alloy (Prometheus scraper) tried to access it. The security plugin was enforcing HTTPS for all requests in production mode, including requests from the local Docker network.

***REMOVED******REMOVED*** Evidence
```bash
***REMOVED*** Alloy scrape target showed:
curl 'http://127.0.0.1:12345/api/v0/web/components/prometheus.scrape.backend' | jq '.debugInfo[0]'
***REMOVED*** Result: "server returned HTTP status 403 Forbidden"

***REMOVED*** Backend was in production mode with HTTPS enforcement:
docker exec scanium-backend env | grep NODE_ENV
***REMOVED*** NODE_ENV=production

***REMOVED*** Local HTTP was not allowed:
docker exec scanium-backend env | grep ALLOW
***REMOVED*** ALLOW_INSECURE_HTTP=false
```

***REMOVED******REMOVED*** Solution
Added `/metrics` to the HTTPS_EXEMPT_PATHS list in the backend security plugin, allowing Prometheus/Alloy to scrape metrics over HTTP from the local Docker network without requiring HTTPS.

***REMOVED******REMOVED******REMOVED*** Changes Made
1. **backend/src/infra/http/plugins/security.ts**
   - Added `/metrics` to `HTTPS_EXEMPT_PATHS` array
   - This exempts the metrics endpoint from HTTPS enforcement, similar to health check endpoints

2. **scripts/monitoring/generate-openai-traffic.sh**
   - Created traffic generator for testing OpenAI metrics
   - Generates controlled traffic to `/v1/assist/chat` endpoint
   - Includes both successful requests and intentional errors

***REMOVED******REMOVED*** Verification Commands

***REMOVED******REMOVED******REMOVED*** 1. Verify Alloy can scrape backend metrics
```bash
ssh nas
curl -sG 'http://127.0.0.1:9009/prometheus/api/v1/query' \
  --data-urlencode 'query=up{job="scanium-backend"}' | jq '.data.result[0].value[1]'
***REMOVED*** Expected: "1" (target is up)
```

***REMOVED******REMOVED******REMOVED*** 2. Verify OpenAI request metrics exist in Mimir
```bash
curl -sG 'http://127.0.0.1:9009/prometheus/api/v1/query' \
  --data-urlencode 'query=scanium_assistant_requests_total{provider="openai"}' | jq '.'
***REMOVED*** Expected: Non-empty result with provider="openai"
```

***REMOVED******REMOVED******REMOVED*** 3. Verify latency metrics
```bash
curl -sG 'http://127.0.0.1:9009/prometheus/api/v1/query' \
  --data-urlencode 'query=scanium_assistant_request_latency_ms_count{provider="openai"}' | jq '.data.result[0].value[1]'
***REMOVED*** Expected: Number of requests (e.g., "1", "2", etc.)
```

***REMOVED******REMOVED******REMOVED*** 4. Verify token metrics
```bash
curl -sG 'http://127.0.0.1:9009/prometheus/api/v1/query' \
  --data-urlencode 'query=scanium_assistant_tokens_used_sum{provider="openai"}' | jq '.data.result[] | {token_type: .metric.token_type, value: .value[1]}'
***REMOVED*** Expected: Three results showing input, output, and total token counts
```

***REMOVED******REMOVED*** Traffic Generator Usage

Generate test traffic to populate the dashboard:

```bash
ssh nas
cd /volume1/docker/scanium/repo

***REMOVED*** Get a valid API key from backend/.env
API_KEY=$(grep SCANIUM_API_KEYS backend/.env | cut -d= -f2 | cut -d, -f1)

***REMOVED*** Get backend container IP
BACKEND_IP=$(docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' scanium-backend)

***REMOVED*** Run traffic generator (generates 3 requests + 1 error over ~60 seconds)
SCANIUM_API_KEY=$API_KEY bash scripts/monitoring/generate-openai-traffic.sh http://$BACKEND_IP:8080
```

***REMOVED******REMOVED*** Dashboard Status

***REMOVED******REMOVED******REMOVED*** Working Panels
- ✅ **Request Rate**: Shows requests/second
- ✅ **p95 Latency**: Shows 95th percentile latency in milliseconds
- ✅ **Request Rate by Model**: Breakdown by provider
- ✅ **Tokens/Min**: Shows token usage rate by type (input/output/total)
- ✅ **Token Breakdown**: Shows token distribution by type
- ⚠️ **Error Rate**: May show 0% if no errors have been generated

***REMOVED******REMOVED******REMOVED*** Expected Behavior
After running the traffic generator once:
- Request count and latency panels should show data within 1-2 minutes
- Token panels will show data after 2+ scrapes (rate calculation requires time series)
- Error rate will be 0% unless error traffic is generated

***REMOVED******REMOVED*** Token Tracking Implementation

Token tracking was implemented in commit `e3e3394`:

1. **Added `recordAssistantTokens()` function** in `backend/src/infra/observability/metrics.ts`
   - Records input, output, and total tokens to Prometheus histogram
   - Uses labels: `provider` and `token_type` (input/output/total)

2. **Instrumented OpenAI provider** (`backend/src/modules/assistant/openai-provider.ts`)
   - Extracts token usage from OpenAI API response
   - Records to both OpenTelemetry (existing) and Prometheus (new)

3. **Instrumented Claude provider** (`backend/src/modules/assistant/claude-provider.ts`)
   - Extracts token usage from Anthropic API response
   - Records to Prometheus metrics

***REMOVED******REMOVED*** Success Criteria Met
- ✅ Alloy successfully scrapes backend `/metrics` endpoint (up=1)
- ✅ OpenAI request count metrics appear in Mimir
- ✅ OpenAI latency metrics appear in Mimir
- ✅ Dashboard shows request rate and latency data
- ✅ No secrets/PII in metrics or logs
