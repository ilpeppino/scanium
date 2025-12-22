import { FastifyPluginAsync } from 'fastify';
import { Config } from '../../config/index.js';
import { usageStore } from '../usage/usage-store.js';

type RouteOpts = { config: Config };

export const adminRoutes: FastifyPluginAsync<RouteOpts> = async (fastify, opts) => {
  const { config } = opts;

  fastify.get('/usage', async (request, reply) => {
    if (!config.admin.enabled) {
      return reply.status(404).send({ error: { code: 'NOT_FOUND', message: 'Not found' } });
    }
    const headerKey = request.headers['x-admin-key'];
    const provided = typeof headerKey === 'string' ? headerKey : '';
    if (!config.admin.adminKey || provided !== config.admin.adminKey) {
      return reply
        .status(403)
        .send({ error: { code: 'FORBIDDEN', message: 'Invalid admin key' } });
    }

    return reply.status(200).send({
      correlationId: request.correlationId,
      usage: usageStore.snapshot(),
    });
  });
};
