import { FastifyInstance } from 'fastify';
import { postMobileTelemetryHandler } from './controller.js';
import { postMobileTelemetrySchema } from './schema.js';

export async function mobileTelemetryRoutes(app: FastifyInstance) {
  app.post(
    '/telemetry/mobile',
    {
      schema: postMobileTelemetrySchema,
      config: {
        rateLimit: {
          max: 100,
          timeWindow: '1 minute',
        },
      },
    },
    postMobileTelemetryHandler
  );
}
