import 'fastify';

declare module 'fastify' {
  interface FastifyRequest {
    // Legacy correlation ID (backward compatibility)
    correlationId?: string;

    // W3C Trace Context (distributed tracing)
    traceId?: string;      // 32 hex chars - identifies the entire trace
    spanId?: string;       // 16 hex chars - identifies this backend operation
    parentSpanId?: string; // 16 hex chars - identifies the parent span (mobile)
  }
}
