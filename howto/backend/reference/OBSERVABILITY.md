# Backend Observability Guide

Complete guide to metrics, logs, and traces in the Scanium backend.

## Table of Contents

- [Overview](#overview)
- [Metrics](#metrics)
- [Traces](#traces)
- [Logs](#logs)
- [Best Practices](#best-practices)

## Overview

The Scanium backend is fully instrumented with OpenTelemetry for comprehensive observability:

- **Metrics**: Business and infrastructure metrics exported to Prometheus/Mimir
- **Traces**: Distributed traces for request flows exported to Tempo
- **Logs**: Structured JSON logs exported to Loki

All telemetry flows through Grafana Alloy to the LGTM stack (Loki, Grafana, Tempo, Mimir).

## Metrics

### Available Metrics

#### HTTP Metrics

- `scanium_http_requests_total` - Total HTTP requests by method, route, status code
- `scanium_http_request_duration_ms` - HTTP request duration histogram

#### Enrichment Pipeline

- `scanium_enrich_requests_total` - Enrichment requests by status and error type
- `scanium_enrich_stage_transitions_total` - Stage transitions (vision → attributes → draft)
- `scanium_enrich_stage_latency_seconds` - Latency for each enrichment stage
- `scanium_enrich_completions_total` - Completed enrichments with success/failure breakdown
- `scanium_enrich_vision_cache_total` - Vision cache hits and misses
- `scanium_enrich_active_jobs` - Current number of active enrichment jobs
- `scanium_enrich_total_latency_seconds` - End-to-end enrichment latency

#### Assistant/LLM

- `scanium_assistant_requests_total` - Assistant requests by provider, status, cache hit
- `scanium_assistant_request_latency_ms` - Assistant request latency
- `scanium_assistant_errors_total` - Assistant errors by provider, error type, reason code
- `scanium_assistant_tokens_used` - Token usage histogram by provider and type
- `scanium_assistant_cache_hit_rate` - Cache hit rate gauge (0-1)

#### Classification

- `scanium_classifier_requests_total` - Classification requests by provider, status, category
- `scanium_classifier_request_latency_ms` - Classification latency
- `scanium_classifier_confidence` - Confidence score distribution

#### Vision Extraction

- `scanium_vision_requests_total` - Vision API requests by provider and status
- `scanium_vision_extraction_latency_ms` - Vision extraction latency by feature
- `scanium_vision_cost_estimate_millicents` - Estimated API cost tracking
- `scanium_vision_cache_size` - Current vision cache entries

#### Business Metrics

- `scanium_items_by_category_total` - Items processed by eBay category
- `scanium_api_quota_usage_total` - API quota usage by key, endpoint, status
- `scanium_api_quota_remaining` - Remaining quota per API key
- `scanium_cache_operations_total` - Cache operations (get/set/delete) with hit/miss
- `scanium_business_errors_total` - Business logic errors by type, operation, severity

#### Database

- `scanium_database_queries_total` - Database queries by operation, table, status
- `scanium_database_query_duration_ms` - Query duration histogram

#### External APIs

- `scanium_external_api_calls_total` - External API calls by service, operation, status
- `scanium_external_api_duration_ms` - External API call duration

#### Infrastructure

- `scanium_rate_limit_hits_total` - Rate limit hits by limiter type and endpoint
- `scanium_quota_exhausted_total` - Daily quota exhaustions by type
- `scanium_circuit_breaker_state` - Circuit breaker state (0=closed, 1=half-open, 2=open)
- `scanium_circuit_breaker_transitions_total` - Circuit breaker state transitions

All Node.js process metrics are also collected with the `scanium_` prefix (memory, CPU, event loop,
etc.).

### Using Metrics

#### Recording Business Metrics

```typescript
import {
  recordItemByCategory,
  recordApiQuotaUsage,
  recordCacheOperation,
  recordBusinessError,
  recordDatabaseQuery,
  recordExternalApiCall,
} from './infra/observability/metrics.js';

// Track items by category
recordItemByCategory('12345', 'Electronics > Phones');

// Track API usage
recordApiQuotaUsage('key_abc123', '/v1/items/enrich', 'success');

// Track cache operations
recordCacheOperation('vision-cache', 'get', 'hit');
recordCacheOperation('vision-cache', 'set', 'success');

// Track business errors
recordBusinessError('INVALID_CATEGORY', 'classification', 'medium');

// Track database queries
const start = Date.now();
const result = await db.item.findMany({ where: { userId } });
recordDatabaseQuery('select', 'item', Date.now() - start, 'success');

// Track external API calls
const apiStart = Date.now();
const response = await openai.chat.completions.create(/* ... */);
recordExternalApiCall('openai', 'chat.completions', Date.now() - apiStart, 'success');
```

#### Recording Enrichment Metrics

Enrichment metrics are automatically recorded by the enrichment manager, but you can also record
them manually:

```typescript
import {
  recordEnrichRequest,
  recordEnrichStage,
  recordEnrichCompletion,
  recordVisionCache,
  updateActiveJobs,
} from './infra/telemetry/enrich-metrics.js';

// Record enrichment request
recordEnrichRequest({ status: 'success' });
recordEnrichRequest({ status: 'error', errorType: 'INVALID_IMAGE' });

// Record stage transitions with latency
recordEnrichStage({ stage: 'vision', latencySeconds: 1.234 });
recordEnrichStage({ stage: 'attributes', latencySeconds: 0.456 });
recordEnrichStage({ stage: 'draft', latencySeconds: 2.789 });

// Record completion
recordEnrichCompletion({
  success: true,
  hasVision: true,
  hasDraft: true,
  totalLatencySeconds: 4.5,
});

// Track vision cache
recordVisionCache(true);  // hit
recordVisionCache(false); // miss

// Update active jobs gauge
updateActiveJobs(5);
```

## Traces

### Automatic Tracing

HTTP requests are automatically traced by `HttpInstrumentation` and `FastifyInstrumentation`:

- Incoming HTTP requests generate root spans
- Outgoing HTTP requests generate child spans
- Spans include method, URL, status code, duration

### Custom Tracing

Use the tracing utilities for adding custom spans to business flows:

```typescript
import {
  withSpan,
  withSpanSync,
  addSpanEvent,
  addSpanAttribute,
  traceVisionExtraction,
  traceAttributeNormalization,
  traceDraftGeneration,
  traceClassification,
  traceDatabase,
  traceExternalApi,
} from './infra/telemetry/tracing.js';

// Generic custom span (async)
const result = await withSpan('business.operation', async (span) => {
  span.setAttribute('user.id', userId);
  span.setAttribute('item.count', items.length);

  const data = await processItems(items);

  span.addEvent('processing.complete', { processed: data.length });
  return data;
}, { attributes: { operation: 'batch' } });

// Generic custom span (sync)
const validated = withSpanSync('validation.check', (span) => {
  span.setAttribute('rules.count', rules.length);
  return validateRules(rules);
});

// Add event to current span
addSpanEvent('cache.miss', { key: cacheKey });
addSpanEvent('validation.passed', { rules: 5 });

// Add attribute to current span
addSpanAttribute('user.tier', 'premium');
addSpanAttribute('items.count', 42);
```

### Specialized Tracing Helpers

#### Vision Extraction

```typescript
const visionData = await traceVisionExtraction(
  'google',
  imageBuffer.length,
  ['ocr', 'labels', 'logos'],
  async () => {
    return await visionClient.extract(image);
  }
);
```

#### Classification

```typescript
const classification = await traceClassification(
  'openai',
  'Electronics',
  async () => {
    return await classifier.classify(item);
  }
);
```

#### Draft Generation

```typescript
const draft = await traceDraftGeneration(
  'gpt-4o-mini',
  contextTokens,
  async () => {
    return await llm.generateDraft(context);
  }
);
```

#### Database Operations

```typescript
const users = await traceDatabase('select', 'user', async () => {
  return await db.user.findMany({ where: { active: true } });
});

await traceDatabase('update', 'item', async () => {
  return await db.item.update({
    where: { id: itemId },
    data: { status: 'processed' },
  });
});
```

#### External API Calls

```typescript
const completion = await traceExternalApi(
  'openai',
  'https://api.openai.com/v1/chat/completions',
  'POST',
  async () => {
    return await openai.chat.completions.create({ /* ... */ });
  }
);
```

### Trace Context Propagation

Traces automatically propagate through:

- HTTP requests (via headers)
- Async operations (via context API)
- Child spans (via parent context)

```typescript
import { getCurrentContext } from './infra/telemetry/tracing.js';

// Get current context for passing to workers
const ctx = getCurrentContext();

// Pass context to async worker
await queueJob({ data, context: ctx });
```

## Logs

### Structured Logging

All logs are structured JSON with automatic redaction of sensitive data:

```typescript
app.log.info({
  msg: 'User action',
  userId: user.id,
  action: 'item.create',
  itemId: item.id,
});

app.log.error({
  msg: 'External API error',
  service: 'openai',
  error: error.message,
  statusCode: error.statusCode,
});
```

### Log Levels

- `trace` - Very detailed debugging
- `debug` - Development debugging
- `info` - General information
- `warn` - Warning conditions
- `error` - Error conditions
- `fatal` - Fatal errors

### Automatic Redaction

The following headers are automatically redacted:

- `authorization`
- `cookie`
- `x-api-key`
- `x-admin-key`
- `x-scanium-device-id`

### Correlation IDs

Use the `x-scanium-correlation-id` header to track requests across services:

```typescript
const correlationId = request.headers['x-scanium-correlation-id'] as string || 'unknown';

app.log.info({
  msg: 'Processing request',
  correlationId,
  itemId: item.id,
});
```

### OpenTelemetry Integration

Logs are automatically sent to OpenTelemetry via `pino-opentelemetry-transport`:

- Logs flow: Pino → OTLP → Alloy → Loki
- Automatic trace correlation (trace_id, span_id added to logs)
- Structured attributes preserved

## Best Practices

### Metrics

1. **Use Labels Wisely**: Keep label cardinality low (< 1000 unique combinations)
    - ✅ Good: `category_name="Electronics"`
    - ❌ Bad: `user_email="scanium@gtemp1.com"` (high cardinality)

2. **Increment Counters at Business Events**:
   ```typescript
   recordEnrichRequest({ status: 'success' });
   recordItemByCategory(categoryId, categoryName);
   ```

3. **Use Histograms for Duration**:
   ```typescript
   const start = Date.now();
   await operation();
   recordDatabaseQuery('select', 'item', Date.now() - start, 'success');
   ```

4. **Update Gauges for Current State**:
   ```typescript
   updateActiveJobs(queue.length);
   updateApiQuotaRemaining(keyId, remaining);
   ```

### Traces

1. **Add Meaningful Spans**: Span operations that take >10ms
   ```typescript
   await withSpan('enrichment.vision.extract', async (span) => {
     // Vision extraction logic
   });
   ```

2. **Add Context with Attributes**:
   ```typescript
   span.setAttribute('image.size_bytes', imageSize);
   span.setAttribute('vision.provider', 'google');
   span.setAttribute('cache.hit', true);
   ```

3. **Mark Important Events**:
   ```typescript
   span.addEvent('cache.miss', { key: cacheKey });
   span.addEvent('validation.failed', { reason: 'invalid_format' });
   ```

4. **Propagate Context**: Ensure parent-child relationships
   ```typescript
   const result = await withSpan('parent', async (parentSpan) => {
     return await withSpan('child', async (childSpan) => {
       // Child span automatically linked to parent
     });
   });
   ```

### Logs

1. **Use Structured Logging**:
   ```typescript
   // ✅ Good
   app.log.info({ msg: 'User created', userId: user.id, email: user.email });

   // ❌ Bad
   app.log.info(`User created: ${user.id} (${user.email})`);
   ```

2. **Include Context**:
   ```typescript
   app.log.error({
     msg: 'Failed to process item',
     itemId: item.id,
     correlationId,
     error: error.message,
     stack: error.stack,
   });
   ```

3. **Use Appropriate Levels**:
    - `error` for errors that need attention
    - `warn` for warnings that don't require immediate action
    - `info` for significant business events
    - `debug` for detailed debugging information

4. **Avoid Sensitive Data**:
   ```typescript
   // ✅ Good
   app.log.info({ msg: 'Auth success', userId: user.id });

   // ❌ Bad
   app.log.info({ msg: 'Auth success', apiKey: apiKey });
   ```

## Dashboards

Pre-built Grafana dashboards are available in `monitoring/grafana/dashboards/`:

- `backend-health.json` - Backend RED metrics (Rate, Errors, Duration)
- `backend-api-performance.json` - API performance and latency
- `backend-errors.json` - Error tracking and analysis
- `lgtm-stack-health.json` - Observability infrastructure health
- `logs-explorer.json` - Log search and analysis
- `traces-drilldown.json` - Trace exploration and service map

## Verification

Use the monitoring scripts to verify telemetry:

```bash
# Comprehensive verification with proof of data
bash ./scripts/monitoring/prove-telemetry.sh

# CI/CD regression guard
bash ./scripts/monitoring/verify-ingestion.sh
bash ./scripts/monitoring/verify-ingestion.sh --strict
```

## Troubleshooting

### No Metrics Showing

1. Check if metrics endpoint is accessible:
   ```bash
   curl http://localhost:8080/metrics
   ```

2. Verify Alloy is scraping:
   ```bash
   docker logs scanium-alloy | grep backend
   ```

3. Check Mimir for data:
   ```bash
   curl "http://localhost:9009/prometheus/api/v1/label/job/values"
   ```

### No Logs Appearing

1. Check Pino is using OTLP transport (in production):
   ```bash
   docker logs scanium-backend | head -20
   ```

2. Verify Alloy is receiving logs:
   ```bash
   docker logs scanium-alloy | grep -i log
   ```

3. Check Loki for labels:
   ```bash
   curl http://localhost:3100/loki/api/v1/labels
   ```

### No Traces Appearing

1. Verify OpenTelemetry is initialized:
   ```bash
   docker logs scanium-backend | grep "OpenTelemetry initialized"
   ```

2. Check Alloy trace receiver:
   ```bash
   docker logs scanium-alloy | grep -i trace
   ```

3. Query Tempo for services:
   ```bash
   curl http://localhost:3200/api/search/tag/service.name/values
   ```

## Further Reading

- [OpenTelemetry JavaScript Documentation](https://opentelemetry.io/docs/instrumentation/js/)
- [Prometheus Best Practices](https://prometheus.io/docs/practices/)
- [Grafana Loki Documentation](https://grafana.com/docs/loki/)
- [Grafana Tempo Documentation](https://grafana.com/docs/tempo/)
