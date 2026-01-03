import Fastify, { FastifyInstance } from 'fastify';
import formbody from '@fastify/formbody';
import fastifyCookie from '@fastify/cookie';
import fastifyMultipart from '@fastify/multipart';
import { Config } from './config/index.js';
import { errorHandlerPlugin } from './infra/http/plugins/error-handler.js';
import { corsPlugin } from './infra/http/plugins/cors.js';
import { csrfProtectionPlugin } from './infra/http/plugins/csrf.js';
import { securityPlugin } from './infra/http/plugins/security.js';
import { correlationPlugin } from './infra/http/plugins/correlation.js';
import { healthRoutes } from './modules/health/routes.js';
import { ebayAuthRoutes } from './modules/auth/ebay/routes.js';
import { classifierRoutes } from './modules/classifier/routes.js';
import { assistantRoutes } from './modules/assistant/routes.js';
import { adminRoutes } from './modules/admin/routes.js';
import { billingRoutes } from './modules/billing/billing.routes.js';
import { configRoutes } from './modules/config/config.routes.js';
import { apiGuardPlugin } from './infra/security/api-guard.js';

/**
 * Build Fastify application instance
 * Registers all plugins and routes
 */
export async function buildApp(config: Config): Promise<FastifyInstance> {
  const app = Fastify({
    trustProxy: true,
    logger: {
      level: config.nodeEnv === 'development' ? 'debug' : 'info',
      redact: {
        paths: [
          'req.headers.authorization',
          'req.headers.cookie',
          'req.headers["x-api-key"]',
          'req.headers["x-admin-key"]',
          'req.headers["x-scanium-device-id"]',
        ],
        censor: '[REDACTED]',
      },
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

  await app.register(apiGuardPlugin, {
    protectedPrefixes: ['/v1/assist/', '/v1/classify', '/v1/admin/'],
    maxRequests: Number(process.env.SECURITY_RATE_LIMIT_MAX ?? 30),
    windowMs: Number(process.env.SECURITY_RATE_LIMIT_WINDOW_MS ?? 10_000),
  });

  // Correlation IDs
  await app.register(correlationPlugin);

  // Register security plugin (HTTPS enforcement, security headers)
  await app.register(securityPlugin, { config });

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

  // Register CSRF protection
  await app.register(csrfProtectionPlugin, { config });

  // Register health routes
  await app.register(healthRoutes);

  // Register eBay auth routes
  await app.register(ebayAuthRoutes, { prefix: '/auth/ebay', config });

  // Cloud classification proxy
  await app.register(classifierRoutes, { prefix: '/v1', config });

  // Assistant chat proxy
  await app.register(assistantRoutes, { prefix: '/v1', config });

  // Billing verification stub
  await app.register(billingRoutes, { prefix: '/v1' });

  // Admin usage endpoints (disabled by default)
  await app.register(adminRoutes, { prefix: '/v1/admin', config });

  // Remote Config
  await app.register(configRoutes, { prefix: '/v1', config });

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
        assistant: {
          chat: 'POST /v1/assist/chat',
        },
      },
    });
  });

  return app;
}
