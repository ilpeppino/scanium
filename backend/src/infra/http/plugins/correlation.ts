import { FastifyPluginAsync } from 'fastify';
import fp from 'fastify-plugin';
import { randomUUID, randomBytes } from 'node:crypto';

/**
 * Parsed W3C trace context from traceparent header.
 * Format: 00-{traceId}-{spanId}-{flags}
 */
interface TraceContext {
  traceId: string;      // 32 hex chars (16 bytes)
  parentSpanId: string; // 16 hex chars (8 bytes) - mobile's span ID
  flags: string;        // 2 hex chars
}

/**
 * Parses W3C traceparent header.
 *
 * Format: version-traceId-spanId-flags
 * Example: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
 *
 * @param header The traceparent header value
 * @returns Parsed trace context or null if invalid
 */
function parseTraceparent(header: string): TraceContext | null {
  const parts = header.trim().split('-');
  if (parts.length !== 4) {
    return null;
  }

  const [version, traceId, spanId, flags] = parts;

  // Only support version 00
  if (version !== '00') {
    return null;
  }

  // Validate lengths
  if (traceId.length !== 32) {
    return null; // traceId must be 16 bytes (32 hex chars)
  }
  if (spanId.length !== 16) {
    return null; // spanId must be 8 bytes (16 hex chars)
  }

  // Validate hex format
  if (!/^[0-9a-f]+$/i.test(traceId) || !/^[0-9a-f]+$/i.test(spanId)) {
    return null;
  }

  return {
    traceId,
    parentSpanId: spanId,
    flags,
  };
}

/**
 * Generates a new span ID for backend operations.
 * Format: 8 bytes (16 hex chars)
 *
 * @returns A new span ID
 */
function generateSpanId(): string {
  return randomBytes(8).toString('hex');
}

/**
 * Correlation and trace context plugin.
 *
 * This plugin:
 * 1. Extracts W3C trace context from incoming traceparent header
 * 2. Generates a new span ID for this backend operation
 * 3. Maintains backward compatibility with X-Scanium-Correlation-Id
 * 4. Enriches logs with trace context
 * 5. Propagates trace context in response headers
 *
 * The trace context enables distributed tracing from mobile app through
 * backend to external services (Google Vision, OpenAI).
 */
const plugin: FastifyPluginAsync = async (fastify) => {
  // Decorate request with correlation and trace context
  fastify.decorateRequest('correlationId', '');
  fastify.decorateRequest('traceId', '');
  fastify.decorateRequest('spanId', '');
  fastify.decorateRequest('parentSpanId', '');

  fastify.addHook('onRequest', async (request, reply) => {
    // Extract W3C trace context from traceparent header
    const traceparentHeader = request.headers['traceparent'];
    let traceContext: TraceContext | null = null;

    if (typeof traceparentHeader === 'string') {
      traceContext = parseTraceparent(traceparentHeader);
      if (!traceContext) {
        request.log.warn(
          { traceparent: traceparentHeader },
          'Invalid traceparent header format'
        );
      }
    }

    // Extract or generate correlation ID (backward compatibility)
    const incoming = request.headers['x-scanium-correlation-id'];
    const correlationId =
      typeof incoming === 'string' && incoming.trim().length > 0
        ? incoming.trim()
        : randomUUID();

    // If we have valid trace context, use it
    if (traceContext) {
      const backendSpanId = generateSpanId();

      request.traceId = traceContext.traceId;
      request.spanId = backendSpanId;
      request.parentSpanId = traceContext.parentSpanId;

      // Enrich logger with trace context
      request.log = request.log.child({
        correlationId,
        traceId: traceContext.traceId,
        spanId: backendSpanId,
        parentSpanId: traceContext.parentSpanId,
      });

      // Return traceparent in response for clients to continue trace
      const responseTraceparent = `00-${traceContext.traceId}-${backendSpanId}-${traceContext.flags}`;
      reply.header('traceparent', responseTraceparent);
    } else {
      // No trace context - just use correlation ID
      request.log = request.log.child({ correlationId });
    }

    request.correlationId = correlationId;
    reply.header('X-Scanium-Correlation-Id', correlationId);
  });
};

// Export wrapped with fastify-plugin to avoid encapsulation
export const correlationPlugin = fp(plugin, {
  name: 'correlation-plugin',
  fastify: '5.x',
});
