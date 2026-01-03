import {
  Registry,
  Counter,
  Histogram,
  Gauge,
  collectDefaultMetrics,
} from 'prom-client';

/**
 * Centralized Prometheus metrics registry for Scanium backend.
 *
 * Metrics are organized by domain:
 * - scanium_assistant_* : Assistant/LLM metrics
 * - scanium_classifier_* : Classification pipeline metrics
 * - scanium_vision_* : Vision extraction metrics
 * - scanium_attribute_* : Attribute extraction metrics
 */

// Create a custom registry
export const metricsRegistry = new Registry();

// Collect default Node.js metrics (memory, CPU, event loop, etc.)
collectDefaultMetrics({
  register: metricsRegistry,
  prefix: 'scanium_',
});

// =============================================================================
// Assistant Metrics
// =============================================================================

/**
 * Histogram for assistant request latency in milliseconds.
 */
export const assistantLatencyHistogram = new Histogram({
  name: 'scanium_assistant_request_latency_ms',
  help: 'Assistant request latency in milliseconds',
  labelNames: ['provider', 'status'] as const,
  buckets: [50, 100, 250, 500, 1000, 2500, 5000, 10000, 30000],
  registers: [metricsRegistry],
});

/**
 * Counter for assistant requests.
 */
export const assistantRequestsCounter = new Counter({
  name: 'scanium_assistant_requests_total',
  help: 'Total number of assistant requests',
  labelNames: ['provider', 'status', 'cache_hit'] as const,
  registers: [metricsRegistry],
});

/**
 * Gauge for assistant cache hit rate.
 */
export const assistantCacheHitRateGauge = new Gauge({
  name: 'scanium_assistant_cache_hit_rate',
  help: 'Assistant response cache hit rate (0-1)',
  registers: [metricsRegistry],
});

/**
 * Counter for assistant errors by type.
 */
export const assistantErrorsCounter = new Counter({
  name: 'scanium_assistant_errors_total',
  help: 'Total number of assistant errors',
  labelNames: ['provider', 'error_type', 'reason_code'] as const,
  registers: [metricsRegistry],
});

/**
 * Histogram for assistant token usage.
 */
export const assistantTokensHistogram = new Histogram({
  name: 'scanium_assistant_tokens_used',
  help: 'Number of tokens used per assistant request',
  labelNames: ['provider', 'token_type'] as const,
  buckets: [50, 100, 250, 500, 1000, 2000, 4000],
  registers: [metricsRegistry],
});

// =============================================================================
// Classifier Metrics
// =============================================================================

/**
 * Histogram for classification latency in milliseconds.
 */
export const classifierLatencyHistogram = new Histogram({
  name: 'scanium_classifier_request_latency_ms',
  help: 'Classification request latency in milliseconds',
  labelNames: ['provider', 'status', 'cache_hit'] as const,
  buckets: [50, 100, 250, 500, 1000, 2500, 5000, 10000],
  registers: [metricsRegistry],
});

/**
 * Counter for classification requests.
 */
export const classifierRequestsCounter = new Counter({
  name: 'scanium_classifier_requests_total',
  help: 'Total number of classification requests',
  labelNames: ['provider', 'status', 'domain_category'] as const,
  registers: [metricsRegistry],
});

/**
 * Histogram for classification confidence scores.
 */
export const classifierConfidenceHistogram = new Histogram({
  name: 'scanium_classifier_confidence',
  help: 'Classification confidence score distribution',
  labelNames: ['provider', 'domain_category'] as const,
  buckets: [0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0],
  registers: [metricsRegistry],
});

// =============================================================================
// Vision Extraction Metrics
// =============================================================================

/**
 * Histogram for vision extraction latency in milliseconds.
 */
export const visionLatencyHistogram = new Histogram({
  name: 'scanium_vision_extraction_latency_ms',
  help: 'Vision extraction latency in milliseconds',
  labelNames: ['provider', 'feature', 'status'] as const,
  buckets: [50, 100, 250, 500, 1000, 2500, 5000, 10000],
  registers: [metricsRegistry],
});

/**
 * Counter for vision extraction requests.
 */
export const visionRequestsCounter = new Counter({
  name: 'scanium_vision_requests_total',
  help: 'Total number of vision extraction requests',
  labelNames: ['provider', 'status'] as const,
  registers: [metricsRegistry],
});

/**
 * Counter for vision API cost estimation (in millicents).
 */
export const visionCostCounter = new Counter({
  name: 'scanium_vision_cost_estimate_millicents',
  help: 'Estimated Vision API cost in millicents (1/1000 of a cent)',
  labelNames: ['feature'] as const,
  registers: [metricsRegistry],
});

/**
 * Gauge for vision cache statistics.
 */
export const visionCacheSizeGauge = new Gauge({
  name: 'scanium_vision_cache_size',
  help: 'Current number of entries in vision cache',
  registers: [metricsRegistry],
});

// =============================================================================
// Attribute Extraction Metrics
// =============================================================================

/**
 * Counter for extracted attributes by type.
 */
export const attributeExtractionCounter = new Counter({
  name: 'scanium_attribute_extractions_total',
  help: 'Total number of attributes extracted',
  labelNames: ['attribute_type', 'confidence', 'source'] as const,
  registers: [metricsRegistry],
});

/**
 * Histogram for attribute confidence distribution.
 */
export const attributeConfidenceHistogram = new Histogram({
  name: 'scanium_attribute_confidence',
  help: 'Attribute extraction confidence distribution',
  labelNames: ['attribute_type'] as const,
  buckets: [0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0],
  registers: [metricsRegistry],
});

/**
 * Counter for attribute extraction success/failure.
 */
export const attributeExtractionSuccessCounter = new Counter({
  name: 'scanium_attribute_extraction_success_total',
  help: 'Total successful attribute extractions by type',
  labelNames: ['attribute_type'] as const,
  registers: [metricsRegistry],
});

// =============================================================================
// Rate Limiting Metrics
// =============================================================================

/**
 * Counter for rate limit hits.
 */
export const rateLimitHitsCounter = new Counter({
  name: 'scanium_rate_limit_hits_total',
  help: 'Total number of rate limit hits',
  labelNames: ['limiter_type', 'endpoint'] as const,
  registers: [metricsRegistry],
});

/**
 * Counter for daily quota exhaustions.
 */
export const quotaExhaustedCounter = new Counter({
  name: 'scanium_quota_exhausted_total',
  help: 'Total number of daily quota exhaustions',
  labelNames: ['quota_type'] as const,
  registers: [metricsRegistry],
});

// =============================================================================
// Circuit Breaker Metrics
// =============================================================================

/**
 * Gauge for circuit breaker state (0=closed, 1=half-open, 2=open).
 */
export const circuitBreakerStateGauge = new Gauge({
  name: 'scanium_circuit_breaker_state',
  help: 'Circuit breaker state (0=closed, 1=half-open, 2=open)',
  labelNames: ['name'] as const,
  registers: [metricsRegistry],
});

/**
 * Counter for circuit breaker state transitions.
 */
export const circuitBreakerTransitionsCounter = new Counter({
  name: 'scanium_circuit_breaker_transitions_total',
  help: 'Total number of circuit breaker state transitions',
  labelNames: ['name', 'from_state', 'to_state'] as const,
  registers: [metricsRegistry],
});

// =============================================================================
// HTTP Request Metrics
// =============================================================================

/**
 * Histogram for HTTP request duration.
 */
export const httpRequestDurationHistogram = new Histogram({
  name: 'scanium_http_request_duration_ms',
  help: 'HTTP request duration in milliseconds',
  labelNames: ['method', 'route', 'status_code'] as const,
  buckets: [5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000],
  registers: [metricsRegistry],
});

/**
 * Counter for HTTP requests.
 */
export const httpRequestsCounter = new Counter({
  name: 'scanium_http_requests_total',
  help: 'Total number of HTTP requests',
  labelNames: ['method', 'route', 'status_code'] as const,
  registers: [metricsRegistry],
});

// =============================================================================
// Helper Functions
// =============================================================================

/**
 * Record assistant request metrics.
 */
export function recordAssistantRequest(
  provider: string,
  status: 'success' | 'error',
  latencyMs: number,
  cacheHit: boolean
): void {
  assistantLatencyHistogram.observe({ provider, status }, latencyMs);
  assistantRequestsCounter.inc({ provider, status, cache_hit: cacheHit ? 'true' : 'false' });
}

/**
 * Record assistant error metrics.
 */
export function recordAssistantError(
  provider: string,
  errorType: string,
  reasonCode: string
): void {
  assistantErrorsCounter.inc({ provider, error_type: errorType, reason_code: reasonCode });
}

/**
 * Record classification metrics.
 */
export function recordClassification(
  provider: string,
  status: 'success' | 'error',
  latencyMs: number,
  cacheHit: boolean,
  domainCategory?: string,
  confidence?: number
): void {
  classifierLatencyHistogram.observe({ provider, status, cache_hit: cacheHit ? 'true' : 'false' }, latencyMs);
  classifierRequestsCounter.inc({
    provider,
    status,
    domain_category: domainCategory ?? 'unknown',
  });

  if (confidence !== undefined && domainCategory) {
    classifierConfidenceHistogram.observe({ provider, domain_category: domainCategory }, confidence);
  }
}

/**
 * Record vision extraction metrics.
 */
export function recordVisionExtraction(
  provider: string,
  status: 'success' | 'error',
  latencyMs: number,
  features: string[]
): void {
  for (const feature of features) {
    visionLatencyHistogram.observe({ provider, feature, status }, latencyMs);
  }
  visionRequestsCounter.inc({ provider, status });
}

/**
 * Record vision API cost estimate.
 * Costs are approximate based on Google Cloud Vision pricing.
 */
export function recordVisionCost(features: {
  ocr?: boolean;
  labels?: boolean;
  logos?: boolean;
  colors?: boolean;
}): void {
  // Approximate costs in millicents (1/1000 of a cent)
  // Based on Google Cloud Vision pricing as of 2024
  if (features.ocr) visionCostCounter.inc({ feature: 'ocr' }, 150); // ~$1.50/1000
  if (features.labels) visionCostCounter.inc({ feature: 'labels' }, 150); // ~$1.50/1000
  if (features.logos) visionCostCounter.inc({ feature: 'logos' }, 150); // ~$1.50/1000
  if (features.colors) visionCostCounter.inc({ feature: 'colors' }, 0); // Included with other features
}

/**
 * Record attribute extraction metrics.
 */
export function recordAttributeExtraction(
  attributeType: string,
  confidence: 'HIGH' | 'MED' | 'LOW',
  source: string
): void {
  attributeExtractionCounter.inc({ attribute_type: attributeType, confidence, source });
  attributeExtractionSuccessCounter.inc({ attribute_type: attributeType });

  // Map confidence tier to numeric value for histogram
  const confidenceValue = confidence === 'HIGH' ? 0.9 : confidence === 'MED' ? 0.6 : 0.3;
  attributeConfidenceHistogram.observe({ attribute_type: attributeType }, confidenceValue);
}

/**
 * Record rate limit hit.
 */
export function recordRateLimitHit(limiterType: string, endpoint: string): void {
  rateLimitHitsCounter.inc({ limiter_type: limiterType, endpoint });
}

/**
 * Record quota exhaustion.
 */
export function recordQuotaExhausted(quotaType: string): void {
  quotaExhaustedCounter.inc({ quota_type: quotaType });
}

/**
 * Update circuit breaker state gauge.
 */
export function updateCircuitBreakerState(
  name: string,
  state: 'closed' | 'half-open' | 'open'
): void {
  const stateValue = state === 'closed' ? 0 : state === 'half-open' ? 1 : 2;
  circuitBreakerStateGauge.set({ name }, stateValue);
}

/**
 * Record circuit breaker state transition.
 */
export function recordCircuitBreakerTransition(
  name: string,
  fromState: string,
  toState: string
): void {
  circuitBreakerTransitionsCounter.inc({ name, from_state: fromState, to_state: toState });
}

/**
 * Record HTTP request metrics.
 */
export function recordHttpRequest(
  method: string,
  route: string,
  statusCode: number,
  durationMs: number
): void {
  httpRequestDurationHistogram.observe({ method, route, status_code: String(statusCode) }, durationMs);
  httpRequestsCounter.inc({ method, route, status_code: String(statusCode) });
}

/**
 * Get all metrics as Prometheus text format.
 */
export async function getMetrics(): Promise<string> {
  return metricsRegistry.metrics();
}

/**
 * Get metrics content type for HTTP response.
 */
export function getMetricsContentType(): string {
  return metricsRegistry.contentType;
}
