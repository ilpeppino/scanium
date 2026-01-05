import { metrics } from '@opentelemetry/api';

/**
 * OpenAI-specific metrics for monitoring API usage
 *
 * Metrics exposed:
 * - openai_requests_total: Total requests by model, status, error_type
 * - openai_request_duration_seconds: Request latency histogram
 * - openai_tokens_total: Total tokens consumed by model and type
 * - openai_rate_limit_remaining: Current rate limit headroom
 */

const meter = metrics.getMeter('scanium-openai', '1.0.0');

// Request counter
const requestCounter = meter.createCounter('openai_requests_total', {
  description: 'Total number of OpenAI API requests',
  unit: '1',
});

// Latency histogram
const latencyHistogram = meter.createHistogram('openai_request_duration_seconds', {
  description: 'OpenAI API request duration in seconds',
  unit: 's',
});

// Token counter
const tokenCounter = meter.createCounter('openai_tokens_total', {
  description: 'Total tokens consumed from OpenAI API',
  unit: '1',
});

// Rate limit gauges (updated from response headers)
const rateLimitState: Map<string, { requests: number; tokens: number }> = new Map();

const rateLimitRequestsGauge = meter.createObservableGauge('openai_rate_limit_requests_remaining', {
  description: 'Remaining requests before rate limit',
  unit: '1',
});

rateLimitRequestsGauge.addCallback((observableResult) => {
  for (const [model, limits] of rateLimitState.entries()) {
    observableResult.observe(limits.requests, { model, limit_type: 'requests' });
  }
});

const rateLimitTokensGauge = meter.createObservableGauge('openai_rate_limit_tokens_remaining', {
  description: 'Remaining tokens before rate limit',
  unit: '1',
});

rateLimitTokensGauge.addCallback((observableResult) => {
  for (const [model, limits] of rateLimitState.entries()) {
    observableResult.observe(limits.tokens, { model, limit_type: 'tokens' });
  }
});

export interface OpenAIMetricsLabels {
  model: string;
  status: 'success' | 'error';
  errorType?: string;
}

export interface OpenAITokenMetrics {
  model: string;
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
}

export interface OpenAIRateLimitInfo {
  model: string;
  remainingRequests?: number;
  remainingTokens?: number;
}

/**
 * Record an OpenAI request completion
 */
export function recordOpenAIRequest(labels: OpenAIMetricsLabels, durationSeconds: number): void {
  requestCounter.add(1, {
    model: labels.model,
    status: labels.status,
    ...(labels.errorType && { error_type: labels.errorType }),
  });

  latencyHistogram.record(durationSeconds, {
    model: labels.model,
    status: labels.status,
  });
}

/**
 * Record token usage from OpenAI response
 */
export function recordOpenAITokens(metrics: OpenAITokenMetrics): void {
  tokenCounter.add(metrics.inputTokens, {
    model: metrics.model,
    token_type: 'input',
  });

  tokenCounter.add(metrics.outputTokens, {
    model: metrics.model,
    token_type: 'output',
  });

  tokenCounter.add(metrics.totalTokens, {
    model: metrics.model,
    token_type: 'total',
  });
}

/**
 * Update rate limit state from OpenAI response headers
 *
 * OpenAI returns headers like:
 * - x-ratelimit-remaining-requests
 * - x-ratelimit-remaining-tokens
 */
export function updateRateLimitState(info: OpenAIRateLimitInfo): void {
  const current = rateLimitState.get(info.model) || { requests: 0, tokens: 0 };

  if (info.remainingRequests !== undefined) {
    current.requests = info.remainingRequests;
  }

  if (info.remainingTokens !== undefined) {
    current.tokens = info.remainingTokens;
  }

  rateLimitState.set(info.model, current);
}
