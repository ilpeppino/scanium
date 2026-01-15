import { FastifyPluginAsync, FastifyRequest } from 'fastify';
import { verifySession } from '../../../modules/auth/google/session-service.js';
import { AuthRequiredError, AuthInvalidError } from '../../../shared/errors/index.js';

declare module 'fastify' {
  interface FastifyRequest {
    userId?: string;
    hadAuthAttempt?: boolean; // Track if auth was attempted but failed
  }
}

export const authMiddleware: FastifyPluginAsync = async (fastify) => {
  fastify.addHook('onRequest', async (request) => {
    const authHeader = request.headers.authorization;

    // Debug logging
    const hasAuth = !!authHeader;
    const isBearerAuth = authHeader?.startsWith('Bearer ') ?? false;
    fastify.log.info({ hasAuth, isBearerAuth, url: request.url }, 'Auth middleware check');

    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return; // No auth header, continue without userId
    }

    // Mark that an auth attempt was made
    request.hadAuthAttempt = true;

    const token = authHeader.substring(7);
    fastify.log.info({ tokenLength: token.length, tokenPrefix: token.substring(0, 8) }, 'Auth token received');

    const userId = await verifySession(token);
    fastify.log.info({ userId: userId ?? 'null', verified: !!userId }, 'Session verification result');

    if (userId) {
      request.userId = userId;
    }
    // If userId is null but hadAuthAttempt is true, token was invalid/expired
  });
};

/**
 * Phase B: Require authentication for protected endpoints
 * Throws appropriate error based on authentication state:
 * - AUTH_REQUIRED: No auth provided
 * - AUTH_INVALID: Auth provided but invalid/expired
 */
export function requireAuth(request: FastifyRequest): string {
  if (!request.userId) {
    // Check if auth was attempted but failed
    if (request.hadAuthAttempt) {
      throw new AuthInvalidError();
    }
    // No auth attempt at all
    throw new AuthRequiredError();
  }
  return request.userId;
}
