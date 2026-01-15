import { FastifyPluginAsync, FastifyRequest } from 'fastify';
import fp from 'fastify-plugin';
import { verifySession } from '../../../modules/auth/google/session-service.js';
import { AuthRequiredError, AuthInvalidError } from '../../../shared/errors/index.js';

declare module 'fastify' {
  interface FastifyRequest {
    userId?: string;
    hadAuthAttempt?: boolean; // Track if auth was attempted but failed
  }
}

const authMiddlewarePlugin: FastifyPluginAsync = async (fastify) => {
  fastify.addHook('onRequest', async (request) => {
    const authHeader = request.headers.authorization;

    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return; // No auth header, continue without userId
    }

    // Mark that an auth attempt was made
    request.hadAuthAttempt = true;

    const token = authHeader.substring(7);
    const userId = await verifySession(token);

    if (userId) {
      request.userId = userId;
    }
    // If userId is null but hadAuthAttempt is true, token was invalid/expired
  });
};

// Use fastify-plugin to skip encapsulation and make hooks available globally
export const authMiddleware = fp(authMiddlewarePlugin, {
  name: 'auth-middleware',
});

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
