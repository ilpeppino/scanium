# OpenAI Monitoring Implementation

This document describes the OpenAI metrics monitoring implementation for Scanium, including runtime per-request metrics collection and visualization in Grafana.

## Overview

**Implementation Strategy: Runtime Per-Request Metrics (Option A)**

We've implemented real-time monitoring of OpenAI API calls by instrumenting the assistant module with OpenTelemetry metrics. This provides operational visibility into:

- Request rates and error rates
- Latency distribution (p50, p95, p99)
- Token consumption (input/output/total)
- Rate limit headroom

**No Usage API polling** was implemented (Option B skipped) because:
- Runtime metrics provide better operational insights
- OpenAI Usage API has 24-48h delay
- Cost tracking can be derived from token usage
- Keeps implementation simple and low-risk

## Architecture

```
OpenAI Provider (backend)
    ↓ (OpenTelemetry metrics)
Alloy (OTLP receiver: 4318/HTTP)
    ↓ (remote_write)
Mimir (Prometheus storage)
    ↓ (PromQL queries)
Grafana (visualization)
```

## What Was Implemented

### 1. Backend Changes

#### New Files
- `backend/src/infra/telemetry/index.ts` - OpenTelemetry initialization
- `backend/src/infra/telemetry/openai-metrics.ts` - OpenAI-specific metrics

#### Modified Files
- `backend/package.json` - Added OpenTelemetry dependencies
- `backend/src/main.ts` - Initialize telemetry on startup
- `backend/src/modules/assistant/openai-provider.ts` - Instrumented with metrics

#### Metrics Exposed

| Metric Name | Type | Labels | Description |
|-------------|------|--------|-------------|
| `openai_requests_total` | Counter | `model`, `status`, `error_type` | Total API requests |
| `openai_request_duration_seconds` | Histogram | `model`, `status` | Request latency |
| `openai_tokens_total` | Counter | `model`, `token_type` | Token consumption |
| `openai_rate_limit_requests_remaining` | Gauge | `model`, `limit_type` | Remaining requests |
| `openai_rate_limit_tokens_remaining` | Gauge | `model`, `limit_type` | Remaining tokens |

**Label Values:**
- `status`: `success`, `error`
- `error_type`: `UNAUTHORIZED`, `RATE_LIMITED`, `PROVIDER_UNAVAILABLE`, `PROVIDER_ERROR`
- `token_type`: `input`, `output`, `total`
- `model`: OpenAI model name (e.g., `gpt-4o-mini`)

### 2. Grafana Dashboard

**New Dashboard:** `monitoring/grafana/dashboards/openai-runtime.json`

**Dashboard UID:** `scanium-openai-runtime`

**Sections:**
1. **Overview** - Key metrics at a glance (req/s, p95 latency, error %, tokens/min)
2. **Request & Error Rates** - Request rate by model, errors by type
3. **Latency** - Latency percentiles (p50, p95, p99)
4. **Token Usage** - Token rate by type and model
5. **Rate Limits** - Remaining requests and tokens

## Environment Variables

### Required on NAS

The following environment variables must be set in the backend service:

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `OTEL_ENABLED` | Enable/disable telemetry | `true` | No |
| `OTEL_SERVICE_NAME` | Service name for metrics | `scanium-backend` | No |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OTLP endpoint URL | `http://localhost:4318` | Yes* |
| `OPENAI_API_KEY` | OpenAI API key | - | Yes** |
| `OPENAI_MODEL` | OpenAI model to use | `gpt-4o-mini` | No |

\* Must point to Alloy's HTTP endpoint (typically `http://scanium-alloy:4318` in Docker)
\*\* Only if using OpenAI assistant provider

### Example .env on NAS

```bash
# Telemetry
OTEL_ENABLED=true
OTEL_SERVICE_NAME=scanium-backend
OTEL_EXPORTER_OTLP_ENDPOINT=http://scanium-alloy:4318

# OpenAI (existing)
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-4o-mini
```

## Deployment Steps

### 1. Install Dependencies on NAS

SSH to NAS and navigate to the backend directory:

```bash
ssh nas
cd /volume1/docker/scanium/repo/backend
npm install
```

### 2. Build Backend

```bash
npm run build
```

### 3. Update Environment Variables

Edit the `.env` file in the compose directory:

```bash
cd /volume1/docker/scanium/repo/deploy/nas/compose
vi .env
```

Add the OTEL environment variables shown above.

### 4. Restart Backend Container

```bash
/usr/local/bin/docker restart scanium-backend
```

### 5. Verify Telemetry Initialization

Check backend logs for telemetry initialization:

```bash
/usr/local/bin/docker logs scanium-backend | grep -i "opentelemetry\|telemetry"
```

Expected output:
```
📊 OpenTelemetry initialized: scanium-backend (production)
   Exporting to: http://scanium-alloy:4318
```

### 6. Restart Grafana (to load new dashboard)

```bash
/usr/local/bin/docker restart scanium-grafana
```

## Validation

### 1. Verify Metrics in Grafana Explore

1. Open Grafana: `http://<nas-ip>:3000`
2. Navigate to **Explore** (compass icon)
3. Select datasource: **Mimir**
4. Run query: `openai_requests_total`

**Expected:** You should see metric series with labels like `model="gpt-4o-mini"`, `status="success"`

### 2. Generate Test Request

Make an OpenAI assistant request through the Scanium backend:

```bash
# From your dev machine or NAS
curl -X POST http://<nas-ip>:8080/api/assistant/chat \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Generate a listing title for this item",
    "items": [{
      "itemId": "test-123",
      "currentTitle": "Test Item"
    }]
  }'
```

### 3. Check Dashboard

1. Navigate to **Dashboards** → **Scanium** folder
2. Open **Scanium - OpenAI Runtime**
3. Verify panels show data (may take 30-60s for first metrics to appear)

### 4. Verify in Alloy Logs

Check that Alloy is receiving and forwarding metrics:

```bash
/usr/local/bin/docker logs scanium-alloy 2>&1 | grep -i "openai\|metrics"
```

### 5. Verify in Mimir Logs

Check that Mimir is ingesting metrics:

```bash
/usr/local/bin/docker logs scanium-mimir 2>&1 | tail -50
```

## Troubleshooting

### "No data" in Grafana Dashboard

**Possible causes:**

1. **No OpenAI requests made yet**
   - Generate a test request (see Validation section)
   - Wait 30-60 seconds for metrics export

2. **Telemetry disabled**
   - Check backend logs: `docker logs scanium-backend | grep "OpenTelemetry"`
   - Ensure `OTEL_ENABLED` is not set to `false`

3. **Wrong OTLP endpoint**
   - Verify `OTEL_EXPORTER_OTLP_ENDPOINT` points to Alloy
   - Default in Docker: `http://scanium-alloy:4318`
   - Check Alloy logs: `docker logs scanium-alloy | grep -i error`

4. **Alloy not forwarding to Mimir**
   - Check Alloy config: `/volume1/docker/scanium/repo/monitoring/alloy/config.alloy`
   - Verify `prometheus.remote_write` target points to Mimir
   - Check Alloy logs for forwarding errors

5. **Metric names changed**
   - Verify metrics exist: `openai_requests_total` in Grafana Explore
   - Check for typos in dashboard queries

### Backend not exporting metrics

1. **Check dependencies installed:**
   ```bash
   cd /volume1/docker/scanium/repo/backend
   npm list | grep opentelemetry
   ```

2. **Check for import errors:**
   ```bash
   docker logs scanium-backend | grep -i "error\|cannot find module"
   ```

3. **Verify telemetry initialization:**
   ```bash
   docker logs scanium-backend | grep "📊"
   ```

4. **Test OTLP endpoint connectivity:**
   ```bash
   docker exec scanium-backend wget -O- http://scanium-alloy:4318/v1/metrics --post-data=''
   ```

### Rate limit metrics not showing

**Note:** Rate limit metrics (`openai_rate_limit_*`) require OpenAI SDK to expose response headers. These may not be available in all SDK versions.

**To verify:**
- Check `backend/src/modules/assistant/openai-provider.ts` line 116
- The code attempts to read `x-ratelimit-*` headers from response
- If headers are not exposed, these metrics will remain empty (this is OK)

**Alternative:** Monitor rate limits via error rate spikes (`error_type="RATE_LIMITED"`)

## Cost Estimation

While we don't poll the Usage API, you can estimate costs from token metrics:

**Formula:**
```
cost = (input_tokens * input_price_per_1k) + (output_tokens * output_price_per_1k)
```

**Example PromQL for daily cost estimate (gpt-4o-mini):**
```promql
(
  sum(increase(openai_tokens_total{model="gpt-4o-mini", token_type="input"}[1d])) / 1000 * 0.00015
  +
  sum(increase(openai_tokens_total{model="gpt-4o-mini", token_type="output"}[1d])) / 1000 * 0.0006
)
```

*(Adjust prices based on current OpenAI pricing)*

## Maintenance

### Regular Checks

1. **Monitor error rate** - Alert if > 5%
2. **Monitor p95 latency** - Alert if > 5s
3. **Monitor rate limit headroom** - Alert if < 10% remaining
4. **Monitor token consumption trends** - Budget planning

### Dashboard Updates

To update the dashboard:

1. Edit `monitoring/grafana/dashboards/openai-runtime.json`
2. Commit changes
3. Pull on NAS: `cd /volume1/docker/scanium/repo && git pull`
4. Restart Grafana: `docker restart scanium-grafana`

*Note: With `allowUiUpdates: true`, you can also edit in Grafana UI, but changes will be overwritten on restart unless saved to JSON.*

## Future Enhancements

Potential improvements (not implemented):

1. **Usage API Polling (Option B)**
   - Add daily cost/usage metrics from OpenAI Usage API
   - Requires additional exporter service
   - Useful for precise billing reconciliation

2. **Alerting Rules**
   - High error rate alert
   - Rate limit approaching alert
   - High latency alert
   - Daily budget alert

3. **Trace Correlation**
   - Link OpenAI spans to Tempo traces
   - Requires adding span attributes to OpenAI calls

4. **Log Correlation**
   - Add OpenAI request IDs to logs
   - Link logs to Loki for debugging

## References

- OpenTelemetry Node SDK: https://opentelemetry.io/docs/languages/js/
- OpenAI API: https://platform.openai.com/docs/api-reference
- Grafana LGTM: https://grafana.com/docs/grafana-cloud/data-configuration/metrics/
- Alloy OTLP receiver: https://grafana.com/docs/alloy/latest/reference/components/otelcol/otelcol.receiver.otlp/
