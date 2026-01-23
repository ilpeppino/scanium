# Dashboard Troubleshooting Guides

Detailed guides for fixing "No data" issues and troubleshooting Grafana dashboards in the Scanium
monitoring stack.

## Recently Fixed Dashboards

### [OpenAI Runtime Dashboard](./openai-runtime-dashboard.md) ✅

- **Dashboard:** Scanium - OpenAI Runtime
- **File:** `monitoring/grafana/dashboards/openai-runtime.json`
- **Metrics:** Request rate, latency, token usage (input/output/total)
- **Issues Fixed:**
    - HTTP 403 blocking /metrics endpoint (added `/metrics` to HTTPS exempt paths)
    - Token tracking implementation for Prometheus metrics
    - Backend metrics scraping via Alloy
- **Status:** Fully operational with all panels showing data

### [Backend API Performance Dashboard](./backend-api-performance-dashboard.md)

- **Dashboard:** Scanium - Backend API Performance
- **File:** `monitoring/grafana/dashboards/backend-api-performance.json`
- **Metrics:** HTTP request rate, latency, error rate by endpoint

### [Backend Errors Dashboard](./backend-errors-dashboard.md)

- **Dashboard:** Scanium - Backend Errors
- **File:** `monitoring/grafana/dashboards/backend-errors.json`
- **Metrics:** Error rate, error types, error distribution

### [Errors and Failures Dashboard](./errors-and-failures-dashboard.md)

- **Dashboard:** Scanium - Errors & Failures
- **File:** `monitoring/grafana/dashboards/errors.json`
- **Metrics:** System-wide error tracking across mobile and backend

## Common Dashboard Issues

### "No data" in panels

**Diagnosis checklist:**

1. **Verify metrics exist in Mimir:**
   ```bash
   ssh nas
   curl -sG 'http://127.0.0.1:9009/prometheus/api/v1/query' \
     --data-urlencode 'query=your_metric_name'
   ```

2. **Check datasource UID in dashboard JSON:**
    - Expected UIDs: `LOKI`, `TEMPO`, `MIMIR`
    - Defined in: `monitoring/grafana/provisioning/datasources/datasources.yaml`

3. **Verify label names match queries:**
   ```bash
   # List all labels for a metric
   curl -sG 'http://127.0.0.1:9009/prometheus/api/v1/series' \
     --data-urlencode 'match[]=your_metric_name'
   ```

4. **Ensure scrape targets are UP:**
   ```bash
   curl -sG 'http://127.0.0.1:9009/prometheus/api/v1/query' \
     --data-urlencode 'query=up{job="scanium-backend"}'
   # Expected: value = "1"
   ```

### Incorrect time series

1. **Verify scrape interval:** Check `monitoring/alloy/config.alloy` for `scrape_interval` settings
2. **Check retention:** See `monitoring/mimir/mimir.yaml` for retention period
3. **Validate time range:** Dashboard time picker must match data availability (default is last 6
   hours)

### Variables not populating

**Common causes:**

- Variable query syntax errors (PromQL/LogQL)
- Label names don't exist on metrics
- Regex filters too restrictive
- No data in selected time range

**Debug variable queries:**

1. Copy variable query from dashboard JSON
2. Test in Grafana Explore with same datasource
3. Check for errors in browser console (F12)
4. Verify labels exist: `curl -s 'http://127.0.0.1:9009/prometheus/api/v1/labels'`

## Testing Tools

### Traffic Generator

Generate test traffic for OpenAI/Assistant metrics:

```bash
ssh nas
cd /volume1/docker/scanium/repo

# Get API key and backend IP
API_KEY=$(grep SCANIUM_API_KEYS backend/.env | cut -d= -f2 | cut -d, -f1)
BACKEND_IP=$(docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' scanium-backend)

# Run traffic generator (3 requests + 1 error over ~60 seconds)
SCANIUM_API_KEY=$API_KEY bash howto/monitoring/testing/generate-openai-traffic.sh http://$BACKEND_IP:8080
```

See [../testing/README.md](../testing/README.md) for more testing tools.

## Dashboard File Locations

All dashboard JSON files are in `monitoring/grafana/dashboards/`:

- `openai-runtime.json` - OpenAI/Assistant API metrics
- `backend-api-performance.json` - Backend HTTP performance
- `backend-errors.json` - Backend error tracking
- `backend-health.json` - Backend service health
- `errors.json` - System-wide errors
- `lgtm-stack-health.json` - LGTM stack monitoring
- `logs-explorer.json` - Log exploration
- `mobile-app-health.json` - Mobile app metrics
- `ops-overview.json` - Executive summary
- `pipeline-health.json` - Pipeline self-observability
- `scan-performance.json` - Scan performance metrics
- `system-overview.json` - System overview
- `traces-drilldown.json` - Trace analysis

## Related Documentation

- **Main Monitoring Guide:** [../README.md](../README.md)
- **Telemetry Setup:** [../telemetry/](../telemetry/)
- **Testing Tools:** [../testing/](../testing/)
- **Incident Reports:** [../incidents/](../incidents/)
- **Reference Docs:** [../reference/](../reference/)
- **Operational Runbooks:** [../runbooks/](../runbooks/)
- **Monitoring Scripts:** [../scripts/](../scripts/)
