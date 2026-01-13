import { FastifyPluginAsync } from 'fastify';
import { z } from 'zod';
import { Config } from '../../../config/index.js';
import { GoogleOAuth2Verifier } from './token-verifier.js';
import { createSession, refreshSession, revokeSession } from './session-service.js';
import { prisma } from '../../../infra/db/prisma.js';
import { ValidationError } from '../../../shared/errors/index.js';
import { requireAuth } from '../../../infra/http/plugins/auth-middleware.js';
import {
  recordAuthLogin,
  recordAuthRefresh,
  recordAuthLogout,
  recordAuthInvalid,
} from '../../../infra/observability/metrics.js';

const requestSchema = z.object({
  idToken: z.string().min(1),
});

export const googleAuthRoutes: FastifyPluginAsync<{ config: Config }> = async (
  fastify,
  opts
) => {
  const config = opts.config;

  if (!config.auth) {
    throw new Error('Auth config not provided');
  }

  const verifier = new GoogleOAuth2Verifier(config.auth.googleClientId);

  /**
   * POST /v1/auth/google
   * Exchange Google ID token for Scanium session token
   */
  fastify.post('/google', async (request, reply) => {
    const parseResult = requestSchema.safeParse(request.body);

    if (!parseResult.success) {
      throw new ValidationError('Invalid request body', {
        errors: parseResult.error.errors,
      });
    }

    const { idToken } = parseResult.data;

    // Verify Google ID token
    let payload;
    try {
      payload = await verifier.verify(idToken);
    } catch (error) {
      // Phase C: Record login failure metric and structured log
      recordAuthLogin('failure');
      recordAuthInvalid('invalid');
      request.log.warn(
        {
          event: 'auth_login_failure',
          reason: 'invalid_google_token',
          correlationId: request.correlationId,
        },
        'Google token verification failed'
      );
      return reply.status(401).send({
        error: {
          code: 'INVALID_TOKEN',
          message: 'Invalid Google ID token',
          correlationId: request.correlationId,
        },
      });
    }

    // Get or create user
    let user = await prisma.user.findUnique({
      where: { googleSub: payload.sub },
    });

    let isNewUser = false;
    if (!user) {
      isNewUser = true;
      user = await prisma.user.create({
        data: {
          googleSub: payload.sub,
          email: payload.email,
          displayName: payload.name,
          pictureUrl: payload.picture,
          lastLoginAt: new Date(),
        },
      });
      request.log.info({ userId: user.id, googleSub: payload.sub }, 'New user created');
    } else {
      user = await prisma.user.update({
        where: { id: user.id },
        data: {
          lastLoginAt: new Date(),
          // Update profile info in case it changed
          email: payload.email,
          displayName: payload.name,
          pictureUrl: payload.picture,
        },
      });
    }

    // Create session (Phase C: with refresh token)
    const sessionInfo = await createSession(
      user.id,
      config.auth.sessionExpirySeconds,
      config.auth.refreshTokenExpirySeconds
    );

    // Phase C: Record login success metric and structured log
    recordAuthLogin('success');
    request.log.info(
      {
        event: 'auth_login_success',
        userId: user.id,
        email: user.email,
        isNewUser,
        correlationId: request.correlationId,
      },
      'Google auth successful'
    );

    return reply.status(200).send({
      ...sessionInfo,
      correlationId: request.correlationId,
    });
  });

  /**
   * Phase C: POST /v1/auth/refresh
   * Refresh access token using refresh token
   */
  const refreshRequestSchema = z.object({
    refreshToken: z.string().min(1),
  });

  fastify.post('/refresh', async (request, reply) => {
    const parseResult = refreshRequestSchema.safeParse(request.body);

    if (!parseResult.success) {
      throw new ValidationError('Invalid request body', {
        errors: parseResult.error.errors,
      });
    }

    const { refreshToken } = parseResult.data;

    const tokens = await refreshSession(
      refreshToken,
      config.auth.sessionExpirySeconds,
      config.auth.refreshTokenExpirySeconds
    );

    if (!tokens) {
      // Phase C: Record refresh failure metric and structured log
      recordAuthRefresh('failure');
      recordAuthInvalid('expired');
      request.log.warn(
        {
          event: 'auth_refresh_failure',
          reason: 'invalid_or_expired_refresh_token',
          correlationId: request.correlationId,
        },
        'Refresh token invalid or expired'
      );
      return reply.status(401).send({
        error: {
          code: 'AUTH_INVALID',
          message: 'Refresh token is invalid or expired',
          correlationId: request.correlationId,
        },
      });
    }

    // Phase C: Record refresh success metric and structured log
    recordAuthRefresh('success');
    request.log.info(
      {
        event: 'auth_refresh_success',
        correlationId: request.correlationId,
      },
      'Token refresh successful'
    );

    return reply.status(200).send({
      ...tokens,
      tokenType: 'Bearer',
      correlationId: request.correlationId,
    });
  });

  /**
   * Phase C: POST /v1/auth/logout
   * Revoke current session
   */
  fastify.post('/logout', async (request, reply) => {
    // Require authentication
    const userId = requireAuth(request);

    const authHeader = request.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return reply.status(401).send({
        error: {
          code: 'AUTH_REQUIRED',
          message: 'Authorization header required',
          correlationId: request.correlationId,
        },
      });
    }

    const token = authHeader.substring(7);
    const revoked = await revokeSession(token);

    // Phase C: Record logout metric and structured log
    recordAuthLogout();
    request.log.info(
      {
        event: 'auth_logout',
        userId,
        sessionRevoked: revoked,
        correlationId: request.correlationId,
      },
      'Session revoked (logout)'
    );

    return reply.status(200).send({
      success: true,
      correlationId: request.correlationId,
    });
  });

  /**
   * Phase C: GET /v1/auth/me
   * Get current user info
   */
  fastify.get('/me', async (request, reply) => {
    const userId = requireAuth(request);

    const user = await prisma.user.findUnique({
      where: { id: userId },
      select: {
        id: true,
        email: true,
        displayName: true,
        pictureUrl: true,
        createdAt: true,
        lastLoginAt: true,
      },
    });

    if (!user) {
      return reply.status(404).send({
        error: {
          code: 'USER_NOT_FOUND',
          message: 'User not found',
          correlationId: request.correlationId,
        },
      });
    }

    request.log.info({ userId }, 'User profile retrieved');

    return reply.status(200).send({
      user,
      correlationId: request.correlationId,
    });
  });
};
