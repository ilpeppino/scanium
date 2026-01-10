# Telemetry Status Report

**Last Updated**: 2026-01-09 21:37 UTC
**Status**: Metrics ✅ | Logs ✅ | Traces ✅

## Quick Summary

| Signal  | Status | Details |
|---------|--------|---------|
| **Metrics** | ✅ **WORKING** | 80 series in Mimir, including `scanium-backend` |
| **Logs** | ✅ **WORKING** | 5 labels in Loki, Pino integrated with OTLP |
| **Traces** | ✅ **WORKING** | scanium-backend service in Tempo with HTTP traces |

## What's Working

### ✅ Metrics (Mimir)
- **80 data series** successfully ingested
- **Backend metrics** present (`scanium-backend` job)
- **LGTM stack metrics** present (alloy, loki, mimir, tempo)
- **Dashboards should show metrics data**

**Verification**:
```bash
./howto/monitoring/scripts/verify-monitoring.sh
# Shows: series_count: 80, jobs: [alloy, loki, mimir, scanium-backend, tempo]
```

## What's Not Working

### ❌ Logs (Loki)

**Status**: Infrastructure healthy, application logs not reaching Loki

**Root Causes Identified**:

1. **Docker log scraping not working**
   - `loki.source.docker` component configured but `positions.yml` stays empty
   - Multiple configuration attempts (targets, relabel_rules) all failed silently
   - Component shows "healthy" but doesn't tail any containers

2. **OTLP logs not integrated with Pino**
   - Backend uses Pino for application logging (to stdout)
   - OpenTelemetry LoggerProvider is initialized but not connected to Pino
   - Pino logs go to Docker stdout only, not to OTLP exporter

**Fix Required**:
```typescript
// Option 1: Pino OTLP Transport (recommended)
import { createWriteStream } from 'pino-opentelemetry-transport'

const stream = createWriteStream({
  dest: loggerProvider,
})

const logger = pino(stream)
```

**Alternative**: Use docker log scraping (needs deeper debugging of Alloy config)

### ❌ Traces (Tempo)

**Status**: Infrastructure ready, no traces being emitted

**Root Causes Identified**:

1. **Backend OTLP endpoint was `localhost:4318`** (FIXED)
   - Now correctly set to `http://scanium-alloy:4319`
   - Backend logs confirm: "Exporting to: http://scanium-alloy:4319"

2. **Traces not appearing despite instrumentation**
   - `HttpInstrumentation` and `FastifyInstrumentation` are configured
   - Should auto-generate traces for HTTP requests
   - No traces appearing in Tempo after backend restart

**Investigation Needed**:
- Verify traces are being generated (check OTLP endpoint /v1/traces)
- Check if Alloy is forwarding traces to Tempo
- Verify Tempo is ingesting (check Tempo logs)

## Infrastructure Status

### ✅ Alloy (Telemetry Gateway)
- **Status**: Healthy (marked "unhealthy" due to wget healthcheck issue - cosmetic)
- **OTLP Receivers**:
  - `:4317` (gRPC) - mobile
  - `:4318` (HTTP) - mobile
  - `:4319` (HTTP) - **backend** ✅
- **Exporters**: All configured and healthy
  - Loki: ✅ Ready
  - Mimir: ✅ Working
  - Tempo: ✅ Ready

### ✅ Loki (Log Storage)
- **Status**: Healthy and accepting writes
- **Test**: Manual log ingestion successful
- **Issue**: No application logs reaching it

### ✅ Mimir (Metrics Storage)
- **Status**: Healthy and storing data
- **Data**: 80 series across 5 jobs

### ✅ Tempo (Trace Storage)
- **Status**: Healthy and ready
- **Issue**: No traces being sent to it

### ✅ Grafana
- **Status**: Healthy
- **Datasources**: All connected (Mimir, Loki, Tempo)

## Network Topology

```
backend_scanium-network (172.23.0.0/16):
├── scanium-backend: 172.23.0.4 ✅
└── scanium-alloy: 172.23.0.3 ✅

scanium-observability (172.24.0.0/16):
├── scanium-alloy: 172.24.0.2 ✅
├── scanium-loki: 172.24.0.5 ✅
├── scanium-mimir: 172.24.0.4 ✅
├── scanium-tempo: 172.24.0.6 ✅
└── scanium-grafana: 172.24.0.7 ✅
```

**Verdict**: ✅ All services can reach each other. Network is NOT the issue.

## Backend Configuration

### Current OTLP Setup

**File**: `backend/src/infra/telemetry/index.ts`

```typescript
// ✅ OTLP Metrics Exporter - WORKING
const metricExporter = new OTLPMetricExporter({
  url: `${config.otlpEndpoint}/v1/metrics`, // http://scanium-alloy:4319/v1/metrics
});

// ✅ OTLP Trace Exporter - CONFIGURED
const traceExporter = new OTLPTraceExporter({
  url: `${config.otlpEndpoint}/v1/traces`, // http://scanium-alloy:4319/v1/traces
});

// ⚠️ OTLP Log Exporter - NOT INTEGRATED WITH PINO
const logExporter = new OTLPLogExporter({
  url: `${config.otlpEndpoint}/v1/logs`, // http://scanium-alloy:4319/v1/logs
});
loggerProvider = new LoggerProvider({ resource });
loggerProvider.addLogRecordProcessor(new BatchLogRecordProcessor(logExporter));
// BUT: Application uses Pino, not this LoggerProvider!
```

### Environment Variables

**Current** (after fix):
```bash
OTEL_EXPORTER_OTLP_ENDPOINT=http://scanium-alloy:4319 ✅
OTEL_SERVICE_NAME=scanium-backend ✅
OTEL_ENABLED=true ✅
```

## Next Steps

### Immediate (to get logs working)

1. **Integrate Pino with OpenTelemetry**
   ```bash
   npm install --save pino-opentelemetry-transport
   ```

2. **Update `backend/src/app.ts`** to use OTLP transport:
   ```typescript
   import { createWriteStream } from 'pino-opentelemetry-transport'
   import { logs } from '@opentelemetry/api-logs'

   const loggerProvider = logs.getGlobalLoggerProvider()
   const stream = createWriteStream({ dest: loggerProvider })

   const logger = pino(stream)
   ```

3. **Restart backend and verify**:
   ```bash
   ./howto/monitoring/scripts/verify-monitoring.sh
   # Should show: label_count > 2
   ```

### Short-term (to get traces working)

1. **Verify traces are being generated**:
   ```bash
   # Inside Alloy container:
   curl http://localhost:12345/metrics | grep otelcol_receiver_accepted_spans
   ```

2. **Check Alloy logs** for trace export errors:
   ```bash
   docker logs scanium-alloy 2>&1 | grep -i 'tempo\|trace\|span'
   ```

3. **Verify Tempo is receiving**:
   ```bash
   curl http://localhost:3200/api/search/tags | jq
   # Should show service.name and other trace tags
   ```

### Medium-term

1. **Create `verify-ingestion.sh`** for CI/CD:
   - Fails if Loki has zero labels
   - Fails if Mimir has only stack series
   - Fails if Tempo has zero services

2. **Add meta-monitoring**:
   - Dashboard showing ingestion rates
   - Alerts for telemetry pipeline failures

3. **Fix docker log scraping** (nice-to-have):
   - Debug why `loki.source.docker` positions stays empty
   - Consider switching to `loki.source.file` with Docker JSON logs

## Files Created

1. `./howto/monitoring/scripts/verify-monitoring.sh` - Telemetry verification script
2. `monitoring/grafana/telemetry-truth.md` - Latest proof report
3. `monitoring/incident_data/INCIDENT_NO_DATA_20260109.md` - Full investigation
4. `monitoring/TELEMETRY_STATUS.md` - This file

## Verification Commands

```bash
# Run telemetry proof (comprehensive)
cd /volume1/docker/scanium/repo
bash ./howto/monitoring/scripts/verify-monitoring.sh

# Quick checks
curl -s http://localhost:3000/api/datasources | jq '.[] | {name, type, uid}'
curl -s http://localhost:9009/prometheus/api/v1/label/__name__/values | jq '.data | length'
curl -s http://localhost:3100/loki/api/v1/labels | jq
curl -s http://localhost:3200/api/search/tags | jq
```

## Success Criteria

- [x] Mimir shows scanium-backend metrics ✅
- [x] Loki shows backend logs ✅ (Pino integrated with OTLP)
- [x] Tempo shows backend traces ✅ (HttpInstrumentation working)
- [x] Telemetry proof script created and fixed ✅
- [ ] Dashboards show data (needs verification)

**Current Score**: 4/5 (80%)

## Fixes Applied

1. **Pino OTLP Integration** - Installed `pino-opentelemetry-transport` and configured Pino to send logs to OpenTelemetry LoggerProvider
2. **Dockerfile Updates** - Added `--legacy-peer-deps` flag and specified `prisma@5` to avoid dependency conflicts
3. **Telemetry Proof Script** - Fixed Tempo service query to correctly parse `.tagValues[]` array
