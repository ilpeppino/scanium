import type { FastifyPluginAsync } from 'fastify';
import { getRequestIdentity } from './request-identity.js';

type GuardOptions = {
  // List of prefixes that must have an API key
  protectedPrefixes: string[];
  // Requests per window per identity (apiKey or ip)
  maxRequests: number;
  windowMs: number;
};

type Counter = { n: number; resetAt: number };

export const apiGuardPlugin: FastifyPluginAsync<GuardOptions> = async (fastify, opts) => {
  const counters = new Map<string, Counter>();

  function isProtected(path: string): boolean {
    return opts.protectedPrefixes.some((p) => path.startsWith(p));
  }

  fastify.addHook('onRequest', async (request, reply) => {
    const path = request.raw.url ?? request.url;

    if (!isProtected(path)) return;

    const id = getRequestIdentity(request);

    // 1) Enforce header presence (for protected routes)
    if (!id.hasApiKey) {
      fastify.log.warn({ path, ip: request.ip }, 'Missing X-API-Key');
      reply.code(401).send({
        error: { code: 'UNAUTHORIZED', message: 'Missing or invalid API key' },
      });
      return;
    }

    // 2) Simple in-memory rate limit (per container)
    const now = Date.now();
    const key = `${id.kind}:${id.id}`;
    const cur = counters.get(key);

    if (!cur || now >= cur.resetAt) {
      counters.set(key, { n: 1, resetAt: now + opts.windowMs });
      return;
    }

    cur.n += 1;
    if (cur.n > opts.maxRequests) {
      const retryAfterSec = Math.max(1, Math.ceil((cur.resetAt - now) / 1000));
      reply
        .header('Retry-After', String(retryAfterSec))
        .code(429)
        .send({
          error: { code: 'RATE_LIMITED', message: 'Too many requests' },
        });
      return;
    }
  });
};