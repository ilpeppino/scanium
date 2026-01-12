import { FastifyReply, FastifyRequest } from 'fastify';
import prom from 'prom-client';

/**
 * Mobile telemetry ingestion endpoint (batch support)
 *
 * Flow: Mobile App → HTTPS Backend → Structured JSON logs (stdout) → Alloy docker pipeline → Loki
 *
 * This implementation:
 * 1. Validates batch of events against schema (with event_name allowlist)
 * 2. Validates timestamp bounds (within +-24h to prevent Loki rejections)
 * 3. Redacts any PII-like patterns
 * 4. Logs ONE structured JSON line per event to stdout
 * 5. Records Prometheus metrics for each event
 * 6. Docker log driver captures logs
 * 7. Alloy processes and sends to Loki with labels
 * 8. Grafana queries Loki via {source="scanium-mobile"}
 *
 * See: docs/telemetry/MOBILE_TELEMETRY_SCHEMA.md
 */

interface MobileTelemetryEvent {
  event_name: string;
  platform: string;
  app_version: string;
  build_type: string;
  timestamp_ms: number;
  session_id?: string;
  request_id?: string;
  result?: 'ok' | 'fail';
  error_code?: string;
  latency_ms?: number;
  attributes?: Record<string, any>;
}

interface MobileTelemetryBatch {
  events: MobileTelemetryEvent[];
}

// Prometheus metrics
const mobileEventsTotal = new prom.Counter({
  name: 'scanium_mobile_events_total',
  help: 'Total number of mobile telemetry events received',
  labelNames: ['event_name', 'platform', 'app_version', 'build_type', 'result'],
});

const mobileErrorsTotal = new prom.Counter({
  name: 'scanium_mobile_errors_total',
  help: 'Total number of mobile telemetry errors',
  labelNames: ['event_name', 'error_code', 'platform', 'app_version', 'build_type'],
});

const mobileEventLatency = new prom.Histogram({
  name: 'scanium_mobile_event_latency_ms',
  help: 'Mobile event latency in milliseconds',
  labelNames: ['event_name', 'platform', 'app_version', 'build_type'],
  buckets: [50, 100, 200, 500, 1000, 2000, 5000, 10000],
});

/**
 * Disallowed attribute keys (PII/high-cardinality)
 */
const DISALLOWED_ATTRIBUTES = [
  'user_id', 'email', 'phone', 'device_id', 'imei', 'android_id',
  'gps', 'latitude', 'longitude', 'location', 'ip_address', 'city',
  'item_name', 'barcode', 'receipt_text', 'prompt', 'photo',
  'token', 'password', 'api_key', 'secret'
];

/**
 * Validate timestamp is within sane bounds (+-24h) to prevent Loki rejections
 */
function isTimestampValid(timestamp_ms: number): boolean {
  const now = Date.now();
  const dayInMs = 24 * 60 * 60 * 1000;
  return timestamp_ms > now - dayInMs && timestamp_ms < now + dayInMs;
}

/**
 * Validate and sanitize event attributes
 */
function sanitizeAttributes(attributes?: Record<string, any>): Record<string, any> {
  if (!attributes) return {};

  const sanitized: Record<string, any> = {};
  for (const [key, value] of Object.entries(attributes)) {
    // Reject disallowed keys
    if (DISALLOWED_ATTRIBUTES.some((blocked) => key.toLowerCase().includes(blocked))) {
      continue; // Skip this attribute
    }

    // Only allow primitive types (no nested objects, arrays, etc.)
    if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
      sanitized[key] = value;
    }
  }

  return sanitized;
}

export async function postMobileTelemetryHandler(
  request: FastifyRequest<{
    Body: MobileTelemetryBatch;
  }>,
  reply: FastifyReply
) {
  const { events } = request.body;

  let accepted = 0;
  let rejected = 0;

  for (const event of events) {
    const {
      event_name,
      platform,
      app_version,
      build_type,
      timestamp_ms,
      session_id,
      request_id,
      result,
      error_code,
      latency_ms,
      attributes,
    } = event;

    // Validate timestamp bounds
    if (!isTimestampValid(timestamp_ms)) {
      request.log.warn(
        {
          event_name,
          timestamp_ms,
          now: Date.now(),
        },
        'Mobile telemetry event rejected: timestamp out of bounds'
      );
      rejected++;
      continue;
    }

    // Sanitize attributes (remove PII, high-cardinality data)
    const sanitizedAttributes = sanitizeAttributes(attributes);

    // Log via Pino (goes to OTLP transport → Alloy → Loki with proper labels)
    request.log.info(
      {
        source: 'scanium-mobile',
        event_name,
        platform,
        app_version,
        build_type,
        env: process.env.NODE_ENV || 'development',
        ...(session_id && { session_id }),
        ...(request_id && { request_id }),
        ...(result && { result }),
        ...(error_code && { error_code }),
        ...(latency_ms !== undefined && { latency_ms }),
        ...(Object.keys(sanitizedAttributes).length > 0 && { attributes: sanitizedAttributes }),
      },
      'Mobile telemetry event'
    );

    // Record Prometheus metrics
    mobileEventsTotal.inc({
      event_name,
      platform,
      app_version,
      build_type,
      result: result || 'ok',
    });

    if (result === 'fail' && error_code) {
      mobileErrorsTotal.inc({
        event_name,
        error_code,
        platform,
        app_version,
        build_type,
      });
    }

    if (latency_ms !== undefined) {
      mobileEventLatency.observe(
        {
          event_name,
          platform,
          app_version,
          build_type,
        },
        latency_ms
      );
    }

    accepted++;
  }

  // Log batch summary via Fastify logger for debugging
  request.log.info(
    {
      accepted,
      rejected,
      total: events.length,
    },
    'Mobile telemetry batch processed'
  );

  return reply.status(200).send({ accepted, rejected });
}
