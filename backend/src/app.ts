import Fastify, { FastifyInstance } from 'fastify';
import formbody from '@fastify/formbody';
import fastifyCookie from '@fastify/cookie';
import fastifyRateLimit from '@fastify/rate-limit';
import fastifyMultipart from '@fastify/multipart';
import { Config } from './config/index.js';
import { errorHandlerPlugin } from './infra/http/plugins/error-handler.js';
import { corsPlugin } from './infra/http/plugins/cors.js';
import { healthRoutes } from './modules/health/routes.js';
import { ebayAuthRoutes } from './modules/auth/ebay/routes.js';
import { classifierRoutes } from './modules/classifier/routes.js';

/**
 * Build Fastify application instance
 * Registers all plugins and routes
 */
export async function buildApp(config: Config): Promise<FastifyInstance> {
  const app = Fastify({
    logger: {
      level: config.nodeEnv === 'development' ? 'debug' : 'info',
      transport:
        config.nodeEnv === 'development'
          ? {
              target: 'pino-pretty',
              options: {
                translateTime: 'HH:MM:ss Z',
                ignore: 'pid,hostname',
                colorize: true,
              },
            }
          : undefined,
    },
  });

  // Register error handler
  app.setErrorHandler(errorHandlerPlugin);

  await app.register(fastifyRateLimit, {
    max: config.classifier.rateLimitPerMinute,
    timeWindow: '1 minute',
    keyGenerator: (req) => {
      const header = req.headers['x-api-key'];
      if (Array.isArray(header)) return header[0] ?? 'anon';
      return header ?? req.ip;
    },
    allowList: ['/health', '/healthz', '/readyz'],
    ban: 0,
  });

  await app.register(fastifyMultipart, {
    limits: {
      fileSize: config.classifier.maxUploadBytes,
      files: 1,
    },
  });

  // Register form body parser (for OAuth callback)
  await app.register(formbody);

  // Register CORS
  await app.register(corsPlugin, { config });

  // Register cookies
  await app.register(fastifyCookie, {
    secret: config.sessionSigningSecret,
  });

  // Register health routes
  await app.register(healthRoutes);

  // Register eBay auth routes
  await app.register(ebayAuthRoutes, { prefix: '/auth/ebay', config });

  // Cloud classification proxy
  await app.register(classifierRoutes, { prefix: '/v1', config });

  // Root endpoint
  app.get('/', async (_request, reply) => {
    return reply.status(200).send({
      name: 'Scanium Backend API',
      version: '1.0.0',
      environment: config.ebay.env,
      endpoints: {
        health: '/healthz',
        readiness: '/readyz',
        ebayAuth: {
          start: 'POST /auth/ebay/start',
          callback: 'GET /auth/ebay/callback',
          status: 'GET /auth/ebay/status',
        },
      },
    });
  });

  return app;
}
