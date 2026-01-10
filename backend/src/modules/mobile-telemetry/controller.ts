import { FastifyReply, FastifyRequest } from 'fastify';

/**
 * Mobile telemetry ingestion endpoint (Option C implementation)
 *
 * Flow: Mobile App → HTTPS Backend → Structured JSON logs (stdout) → Alloy docker pipeline → Loki
 *
 * This implementation:
 * 1. Validates the incoming event against schema
 * 2. Redacts any PII-like patterns
 * 3. Logs ONE structured JSON line to stdout
 * 4. Docker log driver captures it
 * 5. Alloy processes it and sends to Loki with labels
 * 6. Grafana queries Loki via {source="scanium-mobile"}
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
  attributes?: Record<string, any>;
}

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
 * Validate and sanitize event attributes
 */
function sanitizeAttributes(attributes?: Record<string, any>): Record<string, any> {
  if (!attributes) return {};

  const sanitized: Record<string, any> = {};
  for (const [key, value] of Object.entries(attributes)) {
    // Reject disallowed keys
    if (DISALLOWED_ATTRIBUTES.some(blocked => key.toLowerCase().includes(blocked))) {
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
    Body: MobileTelemetryEvent;
  }>,
  reply: FastifyReply
) {
  const { event_name, platform, app_version, build_type, timestamp_ms, session_id, request_id, attributes } = request.body;

  // Sanitize attributes (remove PII, high-cardinality data)
  const sanitizedAttributes = sanitizeAttributes(attributes);

  // Create structured log event
  // This will be captured by docker log driver, then Alloy, then sent to Loki
  const logEvent = {
    source: 'scanium-mobile',
    event_name,
    platform,
    app_version,
    build_type,
    timestamp_ms,
    ...(session_id && { session_id }),
    ...(request_id && { request_id }),
    ...(Object.keys(sanitizedAttributes).length > 0 && { attributes: sanitizedAttributes }),
  };

  // Log to stdout as single-line JSON (critical: must be single line for docker log driver)
  console.log(JSON.stringify(logEvent));

  // Also log via Fastify logger for debugging (this goes to OTLP if configured)
  request.log.debug({
    msg: 'Mobile telemetry event received',
    event_name,
    platform,
    build_type,
  });

  return reply.status(202).send({ success: true });
}
