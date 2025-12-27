import { FastifyPluginAsync } from 'fastify';
import { createRequire } from 'module';
import { checkDatabaseConnection } from '../../infra/db/prisma.js';
import { assistantReadinessRegistry } from '../assistant/readiness-registry.js';

const require = createRequire(import.meta.url);
const packageJson = require('../../../package.json') as { version?: string };

/**
 * Health check routes
 * Required for NAS operations and monitoring
 */
export const healthRoutes: FastifyPluginAsync = async (fastify) => {
  /**
   * GET /health
   * Cloud-friendly health for the classifier proxy.
   * Now includes assistant readiness for mobile app integration.
   */
  fastify.get('/health', async (_request, reply) => {
    const assistantReadiness = assistantReadinessRegistry.getReadiness();

    return reply.status(200).send({
      status: 'ok',
      ts: new Date().toISOString(),
      version: packageJson.version ?? 'unknown',
      assistant: {
        providerConfigured: assistantReadiness.providerConfigured,
        providerReachable: assistantReadiness.providerReachable,
        state: assistantReadiness.state,
      },
    });
  });

  /**
   * GET /healthz
   * Basic liveness check - returns 200 if process is running
   */
  fastify.get('/healthz', async (_request, reply) => {
    return reply.status(200).send({
      status: 'ok',
      timestamp: new Date().toISOString(),
    });
  });

  /**
   * GET /readyz
   * Readiness check - returns 200 only if database is reachable
   * Used to verify database connectivity before routing traffic.
   * Includes detailed assistant readiness status for mobile app.
   */
  fastify.get('/readyz', async (_request, reply) => {
    const dbConnected = await checkDatabaseConnection();
    const assistantReadiness = assistantReadinessRegistry.getReadiness();

    if (!dbConnected) {
      return reply.status(503).send({
        status: 'error',
        message: 'Database not reachable',
        timestamp: new Date().toISOString(),
        assistant: {
          providerConfigured: assistantReadiness.providerConfigured,
          providerReachable: assistantReadiness.providerReachable,
          state: assistantReadiness.state,
          providerType: assistantReadiness.providerType,
          lastSuccessAt: assistantReadiness.lastSuccessAt,
          lastErrorAt: assistantReadiness.lastErrorAt,
        },
      });
    }

    return reply.status(200).send({
      status: 'ok',
      database: 'connected',
      timestamp: new Date().toISOString(),
      assistant: {
        providerConfigured: assistantReadiness.providerConfigured,
        providerReachable: assistantReadiness.providerReachable,
        state: assistantReadiness.state,
        providerType: assistantReadiness.providerType,
        lastSuccessAt: assistantReadiness.lastSuccessAt,
        lastErrorAt: assistantReadiness.lastErrorAt,
      },
    });
  });
};
