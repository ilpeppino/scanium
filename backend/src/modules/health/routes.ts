import { FastifyPluginAsync } from 'fastify';
import { checkDatabaseConnection } from '../../infra/db/prisma.js';

/**
 * Health check routes
 * Required for NAS operations and monitoring
 */
export const healthRoutes: FastifyPluginAsync = async (fastify) => {
  /**
   * GET /healthz
   * Basic liveness check - returns 200 if process is running
   */
  fastify.get('/healthz', async (request, reply) => {
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
  fastify.get('/readyz', async (request, reply) => {
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
