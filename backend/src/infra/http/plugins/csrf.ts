import { randomBytes, timingSafeEqual } from 'node:crypto';
import { FastifyPluginAsync } from 'fastify';
import { Config } from '../../../config/index.js';

const CSRF_COOKIE_NAME = 'csrf_token';
const CSRF_HEADER_NAME = 'x-csrf-token';
const CSRF_BODY_FIELD = '_csrf';
const PROTECTED_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

const toBuffer = (value: string) => Buffer.from(value, 'utf8');

const secureCompare = (a?: string, b?: string) => {
  if (!a || !b) return false;
  const aBuffer = toBuffer(a);
  const bBuffer = toBuffer(b);

  return aBuffer.length === bBuffer.length && timingSafeEqual(aBuffer, bBuffer);
};

export const csrfProtectionPlugin: FastifyPluginAsync<{ config: Config }> = async (
  fastify,
  opts
) => {
  const { config } = opts;

  fastify.addHook('preHandler', async (request, reply) => {
    const existingToken = request.cookies[CSRF_COOKIE_NAME];
    const csrfToken = existingToken ?? randomBytes(32).toString('hex');

    if (!existingToken) {
      reply.setCookie(CSRF_COOKIE_NAME, csrfToken, {
        path: '/',
        sameSite: 'lax',
        secure: config.nodeEnv === 'production',
        httpOnly: true,
      });
    }

    reply.header('X-CSRF-Token', csrfToken);

    if (!PROTECTED_METHODS.has(request.method)) {
      return;
    }

    const headerValue = request.headers[CSRF_HEADER_NAME];
    const headerToken = Array.isArray(headerValue) ? headerValue[0] : headerValue;
    const bodyToken = (request.body as { [CSRF_BODY_FIELD]?: unknown } | undefined)?.[
      CSRF_BODY_FIELD
    ];

    const incomingToken = typeof headerToken === 'string'
      ? headerToken
      : typeof bodyToken === 'string'
        ? bodyToken
        : undefined;

    if (secureCompare(incomingToken, csrfToken)) {
      return;
    }

    request.log.warn({ url: request.url, method: request.method }, 'CSRF validation failed');

    return reply.status(403).send({
      error: {
        code: 'CSRF_VALIDATION_FAILED',
        message: 'CSRF token missing or invalid',
      },
    });
  });
};
