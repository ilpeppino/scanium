import { FastifyPluginAsync } from 'fastify';
import { createRequire } from 'module';
import { checkDatabaseConnection } from '../../infra/db/prisma.js';

const require = createRequire(import.meta.url);
const packageJson = require('../../../package.json') as { version?: string };

/**
 * Health check routes
 * Required for NAS operations and monitoring
 */
export const healthRoutes: FastifyPluginAsync = async (fastify) => {
  /**
   * GET /health
   * Cloud-friendly health for the classifier proxy
   */
  fastify.get('/health', async (_request, reply) => {
    return reply.status(200).send({
      status: 'ok',
      ts: new Date().toISOString(),
      version: packageJson.version ?? 'unknown',
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
   * Used to verify database connectivity before routing traffic
   */
  fastify.get('/readyz', async (_request, reply) => {
    const dbConnected = await checkDatabaseConnection();

    if (!dbConnected) {
      return reply.status(503).send({
        status: 'error',
        message: 'Database not reachable',
        timestamp: new Date().toISOString(),
      });
    }

    return reply.status(200).send({
      status: 'ok',
      database: 'connected',
      timestamp: new Date().toISOString(),
    });
  });
};
