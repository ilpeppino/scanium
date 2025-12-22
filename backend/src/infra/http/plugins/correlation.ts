import { FastifyPluginAsync } from 'fastify';
import { randomUUID } from 'node:crypto';

export const correlationPlugin: FastifyPluginAsync = async (fastify) => {
  fastify.decorateRequest('correlationId', '');

  fastify.addHook('onRequest', async (request, reply) => {
    const incoming = request.headers['x-scanium-correlation-id'];
    const correlationId =
      typeof incoming === 'string' && incoming.trim().length > 0
        ? incoming.trim()
        : randomUUID();

    request.correlationId = correlationId;
    request.log = request.log.child({ correlationId });
    reply.header('X-Scanium-Correlation-Id', correlationId);
  });
};
