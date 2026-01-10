# Backend Errors Dashboard Fix

## Date: 2026-01-10

## Root Cause

The "Scanium - Backend Errors" dashboard panel #2 "Total Errors (1h)" was missing the `or vector(0)` fallback in its PromQL query. When no error data existed or Mimir returned empty results, the panel showed "No data" instead of showing 0.

Additionally, Mimir has block consistency issues with older blocks (2-3 hours old) that cause some queries requiring series expansion (template variables, `sum by (label)` aggregations) to fail with 500 errors. However, instant queries without label expansion work correctly for recent data.

## Evidence

### Metrics Exist
```bash
# 4xx errors in last hour
curl -s http://localhost:9009/prometheus/api/v1/query \
  --data-urlencode "query=sum(increase(scanium_http_requests_total{status_code=~\"4..\"}[1h]))" \
  | jq .
# Returns: ~1.08 errors

# Current status codes
curl -s http://localhost:9009/prometheus/api/v1/query \
  --data-urlencode "query=sum by(status_code)(scanium_http_requests_total)" \
  | jq .
# Returns: 404 (144), 401 (33), 200 (23), 202 (3)
```

### Dashboard Query Works
```bash
# Test through Grafana API
NOW=$(date +%s)
FROM=$((NOW - 3600))
curl -s -u admin:admin -X POST http://localhost:3000/api/ds/query \
  -H "Content-Type: application/json" \
  -d "{
    \"queries\": [{
      \"refId\": \"A\",
      \"datasource\": {\"type\": \"prometheus\", \"uid\": \"MIMIR\"},
      \"expr\": \"sum(increase(scanium_http_requests_total{status_code=~\\\"4..\\\" }[1h])) or vector(0)\",
      \"instant\": true
    }],
    \"from\": \"${FROM}000\",
    \"to\": \"${NOW}000\"
  }" | jq .
# Returns: 1.08 errors
```

### Label Schema Matches
```bash
# Check metric labels
curl -s http://localhost:9009/prometheus/api/v1/query \
  --data-urlencode "query=scanium_http_requests_total" \
  | jq '.data.result[0].metric'
# Confirms: service_name, deployment_environment, route, status_code match dashboard
```

## Changes Made

### File Modified
- `monitoring/grafana/dashboards/backend-errors.json`

### Change
Panel #2 "Total Errors (1h)" query updated:
```diff
- "expr": "sum(increase(scanium_http_requests_total{...}[1h]))"
+ "expr": "sum(increase(scanium_http_requests_total{...}[1h])) or vector(0)"
```

This ensures the panel shows 0 instead of "No data" when no errors exist.

## Verification Commands

Run these commands from NAS to verify the fix:

### 1. Check Mimir has error data
```bash
ssh nas 'curl -s http://localhost:9009/prometheus/api/v1/query \
  --data-urlencode "query=sum(increase(scanium_http_requests_total{status_code=~\"4..\"}[1h]))" \
  | jq ".data.result[0].value[1]"'
# Should return a number (e.g., "1.08") or empty array if no errors
```

### 2. Check dashboard query through Grafana
```bash
ssh nas 'NOW=$(date +%s); FROM=$((NOW - 3600)); curl -s -u admin:admin -X POST http://localhost:3000/api/ds/query \
  -H "Content-Type: application/json" \
  -d "{
    \"queries\": [{
      \"refId\": \"A\",
      \"datasource\": {\"type\": \"prometheus\", \"uid\": \"MIMIR\"},
      \"expr\": \"sum(increase(scanium_http_requests_total{status_code=~\\\"4..\\\" }[1h])) or vector(0)\",
      \"instant\": true
    }],
    \"from\": \"${FROM}000\",
    \"to\": \"${NOW}000\"
  }" | jq ".results.A.frames[0].data.values[1][0]"'
# Should return the same number or 0
```

### 3. Check dashboard is loaded
```bash
ssh nas 'curl -s -u admin:admin http://localhost:3000/api/dashboards/uid/scanium-backend-errors \
  | jq ".dashboard.title, .dashboard.panels | length"'
# Should return: "Scanium - Backend Errors" and 17 (panel count)
```

## Known Limitations

1. **Mimir Block Consistency Issues**: Some queries (template variables, `sum by (label)`) fail with 500 errors when accessing blocks 2-3 hours old. This affects:
   - Template variable dropdowns (service/route selection may be incomplete)
   - Panels #8, #9, #10, #12 (pie chart, bar charts, table) when time range spans corrupted blocks

2. **Workaround**: Limit dashboard time range to last 30 minutes or use instant queries (already applied where possible).

3. **Root Fix**: The Mimir block issue requires separate investigation (store-gateway missing blocks). This is outside the scope of dashboard fixes.

## Success Criteria

After the fix:
- Panel #2 "Total Errors (1h)" shows a number (0 or error count) instead of "No data"
- Panels #3, #4, #5 (4xx/5xx/Error Rate) continue to work with vector(0) fallbacks
- Dashboard loads without errors
- Stat panels show data for time windows with error traffic

## Deployment

```bash
# On NAS
cd /volume1/docker/scanium/repo/monitoring
/usr/local/bin/docker-compose restart grafana
# Wait ~10s for restart
curl -s -u admin:admin http://localhost:3000/api/health | jq .
```

## Rollback

If the fix causes issues:
```bash
git checkout HEAD~1 monitoring/grafana/dashboards/backend-errors.json
cd /volume1/docker/scanium/repo/monitoring
/usr/local/bin/docker-compose restart grafana
```
