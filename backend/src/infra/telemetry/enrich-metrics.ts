/**
 * Enrichment Metrics
 *
 * Prometheus-style metrics for enrichment pipeline observability.
 */

import { Counter, Histogram, Gauge } from 'prom-client';

// Request counter
const enrichRequestCounter = new Counter({
  name: 'scanium_enrich_requests_total',
  help: 'Total enrichment requests',
  labelNames: ['status', 'errorType'] as const,
});

// Stage counter
const enrichStageCounter = new Counter({
  name: 'scanium_enrich_stage_transitions_total',
  help: 'Total enrichment stage transitions',
  labelNames: ['stage'] as const,
});

// Stage latency histogram
const enrichStageLatency = new Histogram({
  name: 'scanium_enrich_stage_latency_seconds',
  help: 'Enrichment stage latency in seconds',
  labelNames: ['stage'] as const,
  buckets: [0.1, 0.5, 1, 2, 5, 10, 30],
});

// Completion counter
const enrichCompletionCounter = new Counter({
  name: 'scanium_enrich_completions_total',
  help: 'Total enrichment completions',
  labelNames: ['success', 'hasVision', 'hasDraft'] as const,
});

// Vision cache counter
const visionCacheCounter = new Counter({
  name: 'scanium_enrich_vision_cache_total',
  help: 'Vision cache hits and misses',
  labelNames: ['result'] as const,
});

// Active jobs gauge
const enrichActiveJobsGauge = new Gauge({
  name: 'scanium_enrich_active_jobs',
  help: 'Current number of active enrichment jobs',
});

// Total latency histogram
const enrichTotalLatency = new Histogram({
  name: 'scanium_enrich_total_latency_seconds',
  help: 'Total enrichment latency in seconds',
  buckets: [1, 2, 5, 10, 30, 60, 120],
});

/**
 * Record an enrichment request.
 */
export function recordEnrichRequest(params: {
  status: 'success' | 'error';
  errorType?: string;
}): void {
  enrichRequestCounter.inc({
    status: params.status,
    errorType: params.errorType ?? 'none',
  });
}

/**
 * Record a stage transition.
 */
export function recordEnrichStage(params: {
  stage: string;
  latencySeconds?: number;
}): void {
  enrichStageCounter.inc({ stage: params.stage });

  if (params.latencySeconds !== undefined) {
    enrichStageLatency.observe({ stage: params.stage }, params.latencySeconds);
  }
}

/**
 * Record enrichment completion.
 */
export function recordEnrichCompletion(params: {
  success: boolean;
  hasVision: boolean;
  hasDraft: boolean;
  totalLatencySeconds: number;
}): void {
  enrichCompletionCounter.inc({
    success: String(params.success),
    hasVision: String(params.hasVision),
    hasDraft: String(params.hasDraft),
  });

  enrichTotalLatency.observe(params.totalLatencySeconds);
}

/**
 * Record vision cache result.
 */
export function recordVisionCache(hit: boolean): void {
  visionCacheCounter.inc({ result: hit ? 'hit' : 'miss' });
}

/**
 * Update active jobs gauge.
 */
export function updateActiveJobs(count: number): void {
  enrichActiveJobsGauge.set(count);
}
