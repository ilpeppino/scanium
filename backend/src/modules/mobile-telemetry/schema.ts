import { FastifySchema } from 'fastify';

/**
 * Schema for mobile telemetry ingestion endpoint (batch support)
 * Follows the schema defined in docs/telemetry/MOBILE_TELEMETRY_SCHEMA.md
 *
 * Supports batch ingestion: { events: [...] }
 * Each event follows the single-event schema
 */
export const postMobileTelemetrySchema: FastifySchema = {
  body: {
    type: 'object',
    required: ['events'],
    properties: {
      events: {
        type: 'array',
        minItems: 1,
        maxItems: 50,
        description: 'Array of mobile telemetry events (1-50 events per batch)',
        items: {
          type: 'object',
          required: ['event_name', 'platform', 'app_version', 'build_type', 'timestamp_ms'],
          properties: {
            event_name: {
              type: 'string',
              enum: [
                'app_launch',
                'scan_started',
                'scan_completed',
                'assist_clicked',
                'share_started',
                'error_shown',
                'crash_marker',
              ],
              description: 'Event identifier from fixed allowlist',
            },
            platform: {
              type: 'string',
              enum: ['android', 'ios'],
              description: 'Mobile platform',
            },
            app_version: {
              type: 'string',
              minLength: 1,
              maxLength: 32,
              pattern: '^\\d+\\.\\d+\\.\\d+',
              description: 'Semantic version (e.g., 1.2.3)',
            },
            build_type: {
              type: 'string',
              enum: ['dev', 'beta', 'prod'],
              description: 'Build type',
            },
            session_id: {
              type: 'string',
              maxLength: 64,
              description: 'Random UUID per app launch (optional)',
            },
            request_id: {
              type: 'string',
              maxLength: 64,
              description: 'Request correlation ID (optional)',
            },
            timestamp_ms: {
              type: 'number',
              minimum: 0,
              description: 'Client timestamp (milliseconds since epoch)',
            },
            result: {
              type: 'string',
              enum: ['ok', 'fail'],
              description: 'Event result (optional)',
            },
            error_code: {
              type: 'string',
              maxLength: 32,
              description: 'Error code if result=fail (optional)',
            },
            latency_ms: {
              type: 'number',
              minimum: 0,
              description: 'Operation latency in milliseconds (optional)',
            },
            attributes: {
              type: 'object',
              additionalProperties: true,
              description: 'Event-specific metadata (limited keys, no PII)',
            },
          },
        },
      },
    },
  },
  response: {
    200: {
      type: 'object',
      properties: {
        accepted: {
          type: 'number',
          description: 'Number of events accepted',
        },
        rejected: {
          type: 'number',
          description: 'Number of events rejected',
        },
      },
      required: ['accepted', 'rejected'],
    },
    400: {
      type: 'object',
      properties: {
        statusCode: { type: 'number' },
        error: { type: 'string' },
        message: { type: 'string' },
      },
    },
    429: {
      type: 'object',
      properties: {
        statusCode: { type: 'number' },
        error: { type: 'string' },
        message: { type: 'string' },
      },
    },
  },
};
