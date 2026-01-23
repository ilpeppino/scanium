import { describe, it, expect, beforeEach } from 'vitest';
import {
  metricsRegistry,
  recordAssistantRequest,
  recordAssistantError,
  recordClassification,
  recordVisionExtraction,
  recordVisionCost,
  recordAttributeExtraction,
  recordRateLimitHit,
  recordQuotaExhausted,
  updateCircuitBreakerState,
  recordCircuitBreakerTransition,
  recordHttpRequest,
  getMetrics,
  getMetricsContentType,
  assistantLatencyHistogram,
  assistantRequestsCounter,
  assistantErrorsCounter,
  classifierLatencyHistogram,
  classifierRequestsCounter,
  classifierConfidenceHistogram,
  visionLatencyHistogram,
  visionRequestsCounter,
  visionCostCounter,
  attributeExtractionCounter,
  attributeConfidenceHistogram,
  rateLimitHitsCounter,
  quotaExhaustedCounter,
  circuitBreakerStateGauge,
  circuitBreakerTransitionsCounter,
  httpRequestDurationHistogram,
  httpRequestsCounter,
} from './metrics.js';

describe('Metrics Module', () => {
  beforeEach(() => {
    // Reset all metrics before each test
    metricsRegistry.resetMetrics();
  });

  describe('metricsRegistry', () => {
    it('contains all registered metrics', async () => {
      const output = await getMetrics();

      expect(output).toContain('scanium_assistant_request_latency_ms');
      expect(output).toContain('scanium_assistant_requests_total');
      expect(output).toContain('scanium_classifier_request_latency_ms');
      expect(output).toContain('scanium_vision_extraction_latency_ms');
      expect(output).toContain('scanium_attribute_extractions_total');
      expect(output).toContain('scanium_rate_limit_hits_total');
    });

    it('returns correct content type', () => {
      const contentType = getMetricsContentType();
      expect(contentType).toContain('text/plain');
    });
  });

  describe('recordAssistantRequest', () => {
    it('records latency histogram observation', async () => {
      recordAssistantRequest('claude', 'success', 150, false);

      const metrics = await getMetrics();
      expect(metrics).toContain('scanium_assistant_request_latency_ms');
      expect(metrics).toContain('provider="claude"');
      expect(metrics).toContain('status="success"');
    });

    it('increments request counter with cache_hit label', async () => {
      recordAssistantRequest('claude', 'success', 100, true);

      const metrics = await getMetrics();
      expect(metrics).toContain('scanium_assistant_requests_total');
      expect(metrics).toContain('cache_hit="true"');
    });

    it('records error status correctly', async () => {
      recordAssistantRequest('claude', 'error', 500, false);

      const metrics = await getMetrics();
      expect(metrics).toContain('status="error"');
    });
  });

  describe('recordAssistantError', () => {
    it('increments error counter with type and reason code', async () => {
      recordAssistantError('claude', 'timeout', 'PROVIDER_UNAVAILABLE');

      const metrics = await getMetrics();
      expect(metrics).toContain('scanium_assistant_errors_total');
      expect(metrics).toContain('error_type="timeout"');
      expect(metrics).toContain('reason_code="PROVIDER_UNAVAILABLE"');
    });
  });

  describe('recordClassification', () => {
    it('records latency with cache_hit label', async () => {
      recordClassification('google', 'success', 250, true, 'electronics_laptop', 0.85);

      const metrics = await getMetrics();
      expect(metrics).toContain('scanium_classifier_request_latency_ms');
      expect(metrics).toContain('cache_hit="true"');
    });

    it('records domain category in counter', async () => {
      recordClassification('google', 'success', 200, false, 'furniture_chair', 0.9);

      const metrics = await getMetrics();
      expect(metrics).toContain('domain_category="furniture_chair"');
    });

    it('records confidence in histogram when provided', async () => {
      recordClassification('google', 'success', 200, false, 'electronics', 0.85);

      const metrics = await getMetrics();
      expect(metrics).toContain('scanium_classifier_confidence');
    });

    it('uses "unknown" for missing domain category', async () => {
      recordClassification('google', 'success', 200, false, undefined, undefined);

      const metrics = await getMetrics();
      expect(metrics).toContain('domain_category="unknown"');
    });
  });

  describe('recordVisionExtraction', () => {
    it('records latency for each feature', async () => {
      recordVisionExtraction('google', 'success', 300, ['ocr', 'labels', 'logos']);

      const metrics = await getMetrics();
      expect(metrics).toContain('scanium_vision_extraction_latency_ms');
      expect(metrics).toContain('feature="ocr"');
      expect(metrics).toContain('feature="labels"');
      expect(metrics).toContain('feature="logos"');
    });

    it('increments request counter', async () => {
      recordVisionExtraction('google', 'success', 200, ['ocr']);

      const metrics = await getMetrics();
      expect(metrics).toContain('scanium_vision_requests_total');
    });
  });

  describe('recordVisionCost', () => {
    it('records cost for OCR feature', async () => {
      recordVisionCost({ ocr: true });

      const metrics = await getMetrics();
      expect(metrics).toContain('scanium_vision_cost_estimate_millicents');
      expect(metrics).toContain('feature="ocr"');
    });

    it('records cost for multiple features', async () => {
      recordVisionCost({ ocr: true, labels: true, logos: true });

      const metrics = await getMetrics();
      expect(metrics).toContain('feature="ocr"');
      expect(metrics).toContain('feature="labels"');
      expect(metrics).toContain('feature="logos"');
    });

    it('does not record cost for disabled features', async () => {
      recordVisionCost({ ocr: false, colors: true });

      const metrics = await getMetrics();
      // Colors have 0 cost, OCR is disabled
      expect(metrics).toContain('feature="colors"');
    });
  });

  describe('recordAttributeExtraction', () => {
    it('records attribute type and confidence', async () => {
      recordAttributeExtraction('brand', 'HIGH', 'logo');

      const metrics = await getMetrics();
      expect(metrics).toContain('scanium_attribute_extractions_total');
      expect(metrics).toContain('attribute_type="brand"');
      expect(metrics).toContain('confidence="HIGH"');
      expect(metrics).toContain('source="logo"');
    });

    it('records confidence in histogram', async () => {
      recordAttributeExtraction('color', 'MED', 'color_extraction');

      const metrics = await getMetrics();
      expect(metrics).toContain('scanium_attribute_confidence');
    });

    it('increments success counter', async () => {
      recordAttributeExtraction('model', 'LOW', 'ocr');

      const metrics = await getMetrics();
      expect(metrics).toContain('scanium_attribute_extraction_success_total');
    });
  });

  describe('recordRateLimitHit', () => {
    it('increments rate limit counter with limiter type and endpoint', async () => {
      recordRateLimitHit('ip', '/v1/classify');

      const metrics = await getMetrics();
      expect(metrics).toContain('scanium_rate_limit_hits_total');
      expect(metrics).toContain('limiter_type="ip"');
      expect(metrics).toContain('endpoint="/v1/classify"');
    });

    it('handles different limiter types', async () => {
      recordRateLimitHit('api_key', '/assist/chat');
      recordRateLimitHit('device', '/assist/chat');

      const metrics = await getMetrics();
      expect(metrics).toContain('limiter_type="api_key"');
      expect(metrics).toContain('limiter_type="device"');
    });
  });

  describe('recordQuotaExhausted', () => {
    it('increments quota exhausted counter', async () => {
      recordQuotaExhausted('daily');

      const metrics = await getMetrics();
      expect(metrics).toContain('scanium_quota_exhausted_total');
      expect(metrics).toContain('quota_type="daily"');
    });
  });

  describe('updateCircuitBreakerState', () => {
    it('sets gauge for closed state', async () => {
      updateCircuitBreakerState('vision', 'closed');

      const metrics = await getMetrics();
      expect(metrics).toContain('scanium_circuit_breaker_state');
      expect(metrics).toContain('name="vision"');
    });

    it('sets correct values for different states', async () => {
      // Test all states
      updateCircuitBreakerState('assistant', 'closed');
      updateCircuitBreakerState('classifier', 'half-open');
      updateCircuitBreakerState('vision', 'open');

      const metrics = await getMetrics();
      expect(metrics).toContain('scanium_circuit_breaker_state');
    });
  });

  describe('recordCircuitBreakerTransition', () => {
    it('increments transition counter', async () => {
      recordCircuitBreakerTransition('vision', 'closed', 'open');

      const metrics = await getMetrics();
      expect(metrics).toContain('scanium_circuit_breaker_transitions_total');
      expect(metrics).toContain('from_state="closed"');
      expect(metrics).toContain('to_state="open"');
    });
  });

  describe('recordHttpRequest', () => {
    it('records request duration histogram', async () => {
      recordHttpRequest('POST', '/v1/classify', 200, 150);

      const metrics = await getMetrics();
      expect(metrics).toContain('scanium_http_request_duration_ms');
      expect(metrics).toContain('method="POST"');
      expect(metrics).toContain('route="/v1/classify"');
      expect(metrics).toContain('status_code="200"');
    });

    it('increments request counter', async () => {
      recordHttpRequest('GET', '/health', 200, 5);

      const metrics = await getMetrics();
      expect(metrics).toContain('scanium_http_requests_total');
    });
  });

  describe('getMetrics', () => {
    it('returns Prometheus text format', async () => {
      // Record some metrics
      recordAssistantRequest('claude', 'success', 100, false);
      recordClassification('google', 'success', 200, false);

      const metrics = await getMetrics();

      // Check Prometheus format markers
      expect(metrics).toContain('# HELP');
      expect(metrics).toContain('# TYPE');
    });
  });
});
