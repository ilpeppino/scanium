/**
 * Custom Tracing Utilities
 *
 * Helpers for adding custom spans to business-critical flows.
 * These spans provide deep visibility into the enrichment pipeline,
 * classification, and other key operations.
 */

import { trace, Span, SpanStatusCode, context, Context } from '@opentelemetry/api';

const tracer = trace.getTracer('scanium-backend-business');

export interface SpanOptions {
  attributes?: Record<string, string | number | boolean>;
  kind?: 'internal' | 'server' | 'client';
}

/**
 * Execute a function within a custom span.
 * Automatically handles span lifecycle and error propagation.
 *
 * @example
 * const result = await withSpan('enrichment.vision.extract', async (span) => {
 *   span.setAttribute('image.size', imageSize);
 *   return await extractVision(image);
 * }, { attributes: { provider: 'google' } });
 */
export async function withSpan<T>(
  name: string,
  fn: (span: Span) => Promise<T>,
  options?: SpanOptions
): Promise<T> {
  const span = tracer.startSpan(name, {
    attributes: options?.attributes,
  });

  try {
    // Execute function in span context
    const result = await context.with(trace.setSpan(context.active(), span), () => fn(span));
    span.setStatus({ code: SpanStatusCode.OK });
    return result;
  } catch (error) {
    span.setStatus({
      code: SpanStatusCode.ERROR,
      message: error instanceof Error ? error.message : String(error),
    });
    span.recordException(error as Error);
    throw error;
  } finally {
    span.end();
  }
}

/**
 * Execute a synchronous function within a custom span.
 */
export function withSpanSync<T>(
  name: string,
  fn: (span: Span) => T,
  options?: SpanOptions
): T {
  const span = tracer.startSpan(name, {
    attributes: options?.attributes,
  });

  try {
    const result = context.with(trace.setSpan(context.active(), span), () => fn(span));
    span.setStatus({ code: SpanStatusCode.OK });
    return result;
  } catch (error) {
    span.setStatus({
      code: SpanStatusCode.ERROR,
      message: error instanceof Error ? error.message : String(error),
    });
    span.recordException(error as Error);
    throw error;
  } finally {
    span.end();
  }
}

/**
 * Add event to current span.
 * Useful for marking significant points within a span.
 *
 * @example
 * addSpanEvent('cache.miss', { key: cacheKey });
 * addSpanEvent('validation.passed', { rules: 5 });
 */
export function addSpanEvent(name: string, attributes?: Record<string, string | number | boolean>): void {
  const span = trace.getActiveSpan();
  if (span) {
    span.addEvent(name, attributes);
  }
}

/**
 * Add attribute to current span.
 *
 * @example
 * addSpanAttribute('user.tier', 'premium');
 * addSpanAttribute('items.count', 42);
 */
export function addSpanAttribute(key: string, value: string | number | boolean): void {
  const span = trace.getActiveSpan();
  if (span) {
    span.setAttribute(key, value);
  }
}

/**
 * Get current span context for propagation.
 * Useful when you need to pass context to async workers or external systems.
 */
export function getCurrentContext(): Context {
  return context.active();
}

/**
 * Enrichment Pipeline Tracing Helpers
 *
 * Specialized helpers for tracing the enrichment pipeline stages.
 */

/**
 * Trace vision extraction stage.
 */
export async function traceVisionExtraction<T>(
  provider: string,
  imageSize: number,
  features: string[],
  fn: () => Promise<T>
): Promise<T> {
  return withSpan(
    'enrichment.vision.extract',
    async (span) => {
      span.setAttribute('vision.provider', provider);
      span.setAttribute('image.size_bytes', imageSize);
      span.setAttribute('vision.features', features.join(','));

      const result = await fn();

      span.addEvent('vision.extraction.complete');
      return result;
    },
    { kind: 'client' }
  );
}

/**
 * Trace attribute normalization stage.
 */
export async function traceAttributeNormalization<T>(
  attributeCount: number,
  fn: () => Promise<T>
): Promise<T> {
  return withSpan(
    'enrichment.attributes.normalize',
    async (span) => {
      span.setAttribute('attributes.input_count', attributeCount);

      const result = await fn();

      span.addEvent('attribute.normalization.complete');
      return result;
    }
  );
}

/**
 * Trace draft generation stage.
 */
export async function traceDraftGeneration<T>(
  model: string,
  contextLength: number,
  fn: () => Promise<T>
): Promise<T> {
  return withSpan(
    'enrichment.draft.generate',
    async (span) => {
      span.setAttribute('llm.model', model);
      span.setAttribute('llm.context_length', contextLength);

      const result = await fn();

      span.addEvent('draft.generation.complete');
      return result;
    },
    { kind: 'client' }
  );
}

/**
 * Trace classification request.
 */
export async function traceClassification<T>(
  provider: string,
  category: string | undefined,
  fn: () => Promise<T>
): Promise<T> {
  return withSpan(
    'classification.classify',
    async (span) => {
      span.setAttribute('classifier.provider', provider);
      if (category) {
        span.setAttribute('classifier.category', category);
      }

      const result = await fn();

      span.addEvent('classification.complete');
      return result;
    },
    { kind: 'client' }
  );
}

/**
 * Trace database operation.
 * Useful for tracking slow queries and database bottlenecks.
 */
export async function traceDatabase<T>(
  operation: string,
  table: string,
  fn: () => Promise<T>
): Promise<T> {
  return withSpan(
    `db.${operation}`,
    async (span) => {
      span.setAttribute('db.operation', operation);
      span.setAttribute('db.table', table);
      span.setAttribute('db.system', 'postgresql');

      const startTime = Date.now();
      const result = await fn();
      const duration = Date.now() - startTime;

      span.setAttribute('db.duration_ms', duration);
      span.addEvent('db.query.complete', { duration_ms: duration });

      return result;
    },
    { kind: 'client' }
  );
}

/**
 * Trace external API call.
 */
export async function traceExternalApi<T>(
  service: string,
  endpoint: string,
  method: string,
  fn: () => Promise<T>
): Promise<T> {
  return withSpan(
    `external.${service}.${method}`,
    async (span) => {
      span.setAttribute('http.method', method);
      span.setAttribute('http.url', endpoint);
      span.setAttribute('external.service', service);

      const startTime = Date.now();
      const result = await fn();
      const duration = Date.now() - startTime;

      span.setAttribute('http.duration_ms', duration);
      span.addEvent('external.api.complete', { duration_ms: duration });

      return result;
    },
    { kind: 'client' }
  );
}
