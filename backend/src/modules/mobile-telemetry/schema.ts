import { FastifySchema } from 'fastify';

/**
 * Schema for mobile telemetry ingestion endpoint
 * Follows the schema defined in docs/telemetry/MOBILE_TELEMETRY_SCHEMA.md
 */
export const postMobileTelemetrySchema: FastifySchema = {
  body: {
    type: 'object',
    required: ['event_name', 'platform', 'app_version', 'build_type', 'timestamp_ms'],
    properties: {
      event_name: {
        type: 'string',
        minLength: 1,
        maxLength: 64,
        description: 'Event identifier from fixed set (app_launch, scan_started, etc.)'
      },
      platform: {
        type: 'string',
        enum: ['android', 'ios'],
        description: 'Mobile platform'
      },
      app_version: {
        type: 'string',
        minLength: 1,
        pattern: '^\\d+\\.\\d+\\.\\d+',
        description: 'Semantic version (e.g., 1.2.3)'
      },
      build_type: {
        type: 'string',
        enum: ['dev', 'beta', 'prod'],
        description: 'Build type'
      },
      session_id: {
        type: 'string',
        description: 'Random UUID per app launch (optional)'
      },
      request_id: {
        type: 'string',
        description: 'Request correlation ID (optional)'
      },
      timestamp_ms: {
        type: 'number',
        description: 'Client timestamp (milliseconds since epoch)'
      },
      attributes: {
        type: 'object',
        additionalProperties: true,
        description: 'Event-specific metadata (limited keys, no PII)'
      },
    },
  },
  response: {
    202: {
      type: 'object',
      properties: {
        success: { type: 'boolean' },
      },
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
