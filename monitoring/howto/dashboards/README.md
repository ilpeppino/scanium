# Dashboard Troubleshooting Guides

This directory contains detailed guides for fixing "No data" issues and troubleshooting Grafana dashboards.

## Available Guides

### [OpenAI Runtime Dashboard](./openai-runtime-dashboard.md)
**Dashboard:** Scanium - OpenAI Runtime
**Metrics:** Request rate, latency, token usage
**Fixed:** HTTP 403 blocking /metrics endpoint, token tracking implementation

### [Backend API Performance Dashboard](./backend-api-performance-dashboard.md)
**Dashboard:** Scanium - Backend API Performance
**Metrics:** HTTP request rate, latency, error rate by endpoint

### [Backend Errors Dashboard](./backend-errors-dashboard.md)
**Dashboard:** Scanium - Backend Errors
**Metrics:** Error rate, error types, error distribution

### [Errors and Failures Dashboard](./errors-and-failures-dashboard.md)
**Dashboard:** Scanium - Errors & Failures
**Metrics:** System-wide error tracking across mobile and backend

## Common Dashboard Issues

### "No data" in panels
1. Check if metrics exist in Mimir (see parent README)
2. Verify datasource UID matches in dashboard JSON
3. Check metric label names match queries
4. Ensure scrape targets are UP

### Incorrect time series
1. Verify scrape interval in Alloy config
2. Check retention settings in Mimir
3. Validate time range selector in dashboard

### Variables not populating
1. Check variable query syntax (PromQL/LogQL)
2. Verify label names exist on metrics
3. Ensure regex filters are correct
