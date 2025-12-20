import fastifyCors from '@fastify/cors';
import { FastifyPluginAsync } from 'fastify';
import { Config } from '../../../config/index.js';

/**
 * CORS plugin
 * Configured from environment variables
 */
export const corsPlugin: FastifyPluginAsync<{ config: Config }> = async (
  fastify,
  opts
) => {
  const allowedOrigins = opts.config.corsOrigins;

  await fastify.register(fastifyCors, {
    origin(origin, callback) {
      if (!origin) {
        return callback(null, true);
      }

      if (allowedOrigins.includes(origin)) {
        return callback(null, origin);
      }

      fastify.log.warn({ origin }, 'CORS origin rejected');
      return callback(null, false);
    },
    credentials: true,
    methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization', 'X-API-Key'],
    exposedHeaders: ['Content-Type', 'X-API-Key'],
  });
};
