import { FastifySchema } from 'fastify';

export const postMobileTelemetrySchema: FastifySchema = {
  body: {
    type: 'object',
    required: ['event_name', 'platform', 'app_version'],
    properties: {
      event_name: { type: 'string', minLength: 1, maxLength: 64 },
      platform: { type: 'string', enum: ['android', 'ios'] },
      app_version: { type: 'string', minLength: 1 },
      env: { type: 'string', enum: ['dev', 'stage', 'prod'] },
      session_id: { type: 'string' },
      properties: {
        type: 'object',
        additionalProperties: true,
      },
      count: { type: 'number' },
      duration_ms: { type: 'number' },
    },
  },
  response: {
    202: {
      type: 'object',
      properties: {
        success: { type: 'boolean' },
      },
    },
  },
};
