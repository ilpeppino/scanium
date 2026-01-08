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
import { visionInsightsRoutes } from './modules/vision/routes.js';
import { adminRoutes } from './modules/admin/routes.js';
import { billingRoutes } from './modules/billing/billing.routes.js';
import { configRoutes } from './modules/config/config.routes.js';
import { enrichRoutes } from './modules/enrich/routes.js';
import { pricingRoutes } from './modules/pricing/routes.js';
import { apiGuardPlugin } from './infra/security/api-guard.js';
import { trace, SpanStatusCode } from '@opentelemetry/api';
import { recordHttpRequest } from './infra/observability/metrics.js';

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


  const tracer = trace.getTracer('scanium-backend');
  const ignoredMetricPaths = ['/health', '/healthz', '/readyz', '/metrics'];

  app.addHook('onRequest', (request, _reply, done) => {
    const url = request.raw.url ?? '';
    const span = tracer.startSpan(`HTTP ${request.method} ${url}`);
    span.setAttribute('http.method', request.method);
    span.setAttribute('http.target', url);
    (request as any).__scaniumRequestStart = process.hrtime.bigint();
    (request as any).__scaniumSpan = span;
    done();
  });

  app.addHook('onResponse', (request, reply, done) => {
    const start = (request as any).__scaniumRequestStart as bigint | undefined;
    const url = request.raw.url ?? '';
    const route =
      (request as any).routeOptions?.url ??
      (request as any).routerPath ??
      request.raw.url ??
      request.url ??
      'unknown';
    const span = (request as any).__scaniumSpan;

    if (span) {
      span.setAttribute('http.route', route);
      span.setAttribute('http.status_code', reply.statusCode);
      if (reply.statusCode >= 500) {
        span.setStatus({ code: SpanStatusCode.ERROR });
      }
      span.end();
    }

    if (start && !ignoredMetricPaths.some((path) => url.startsWith(path))) {
      const durationMs = Number(process.hrtime.bigint() - start) / 1e6;
      recordHttpRequest(request.method, route, reply.statusCode, durationMs);
    }
    done();
  });

  // Register error handler
  app.setErrorHandler(errorHandlerPlugin);

  await app.register(apiGuardPlugin, {
    protectedPrefixes: ['/v1/assist/', '/v1/classify', '/v1/vision/', '/v1/admin/', '/v1/items/enrich', '/v1/pricing/estimate'],
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

  // Vision insights for immediate prefill
  await app.register(visionInsightsRoutes, { prefix: '/v1', config });

  // Enrichment pipeline (scan → vision → attributes → draft)
  await app.register(enrichRoutes, { prefix: '/v1', config });

  // Pricing estimation (baseline prices from visual attributes)
  await app.register(pricingRoutes, { prefix: '/v1', config });

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
        enrich: {
          submit: 'POST /v1/items/enrich',
          status: 'GET /v1/items/enrich/status/:requestId',
        },
        pricing: {
          estimate: 'POST /v1/pricing/estimate',
          categories: 'GET /v1/pricing/categories',
          brands: 'GET /v1/pricing/brands/:brand',
          conditions: 'GET /v1/pricing/conditions',
        },
      },
    });
  });

  return app;
}
