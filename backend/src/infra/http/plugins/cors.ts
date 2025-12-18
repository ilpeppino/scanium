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
  await fastify.register(fastifyCors, {
    origin: opts.config.corsOrigins,
    credentials: true,
    methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization', 'X-API-Key'],
    exposedHeaders: ['Content-Type', 'X-API-Key'],
  });
};
