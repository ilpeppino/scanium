import { FastifyPluginAsync } from 'fastify';
import { z } from 'zod';
import { Config } from '../../config/index.js';
import { requireAuth } from '../../infra/http/plugins/auth-middleware.js';
import { ValidationError } from '../../shared/errors/index.js';
import {
  deleteUserAccount,
  getDeletionStatus,
  requestDeletionByEmail,
  confirmDeletionByToken,
} from './deletion-service.js';

const deleteByEmailRequestSchema = z.object({
  email: z.string().email(),
});

const confirmDeletionSchema = z.object({
  token: z.string().min(1),
});

export const accountRoutes: FastifyPluginAsync<{ config: Config }> = async (
  fastify,
  opts
) => {
  const config = opts.config;

  /**
   * POST /v1/account/delete
   * Delete the authenticated user's account and all associated data
   * Requires authentication
   */
  fastify.post('/delete', async (request, reply) => {
    const userId = requireAuth(request);

    try {
      await deleteUserAccount(userId);

      request.log.info(
        {
          event: 'account_deleted',
          userId,
          correlationId: request.correlationId,
        },
        'User account deleted successfully'
      );

      return reply.status(200).send({
        status: 'DELETED',
        message: 'Your account and all associated data have been permanently deleted',
        correlationId: request.correlationId,
      });
    } catch (error) {
      request.log.error(
        {
          event: 'account_deletion_failed',
          userId,
          error: error instanceof Error ? error.message : 'Unknown error',
          correlationId: request.correlationId,
        },
        'Failed to delete user account'
      );

      return reply.status(500).send({
        error: {
          code: 'DELETION_FAILED',
          message: 'Failed to delete account. Please try again later.',
          correlationId: request.correlationId,
        },
      });
    }
  });

  /**
   * GET /v1/account/deletion-status
   * Get the deletion status of the authenticated user's account
   * Requires authentication
   */
  fastify.get('/deletion-status', async (request, reply) => {
    const userId = requireAuth(request);

    try {
      const status = await getDeletionStatus(userId);

      return reply.status(200).send({
        ...status,
        correlationId: request.correlationId,
      });
    } catch (error) {
      request.log.error(
        {
          event: 'deletion_status_check_failed',
          userId,
          error: error instanceof Error ? error.message : 'Unknown error',
          correlationId: request.correlationId,
        },
        'Failed to check deletion status'
      );

      return reply.status(500).send({
        error: {
          code: 'STATUS_CHECK_FAILED',
          message: 'Failed to check deletion status',
          correlationId: request.correlationId,
        },
      });
    }
  });

  /**
   * POST /v1/account/delete-by-email
   * Request account deletion via email (unauthenticated, requires rate limiting)
   * Returns a verification token that must be confirmed to complete deletion
   */
  fastify.post(
    '/delete-by-email',
    {
      config: {
        rateLimit: {
          max: 3, // Max 3 requests
          timeWindow: '15 minutes', // Per 15 minutes per IP
        },
      },
    },
    async (request, reply) => {
      const parseResult = deleteByEmailRequestSchema.safeParse(request.body);

      if (!parseResult.success) {
        throw new ValidationError('Invalid request body', {
          errors: parseResult.error.errors,
        });
      }

      const { email } = parseResult.data;

      // Generate verification token
      const { verificationToken, expiresAt } = requestDeletionByEmail(email);

      // In production, send email with verification link here
      // For now, we return the token (caller can construct the verification URL)
      const verificationUrl = `${config.publicBaseUrl}/account/delete/confirm?token=${verificationToken}`;

      request.log.info(
        {
          event: 'account_deletion_requested',
          email,
          expiresAt: expiresAt.toISOString(),
          correlationId: request.correlationId,
        },
        'Account deletion verification requested'
      );

      // Return success with verification URL
      // In production, this would trigger an email and not return the URL directly
      return reply.status(200).send({
        message: 'Verification email sent. Please check your email to confirm account deletion.',
        verificationUrl, // REMOVE THIS IN PRODUCTION (only for testing)
        expiresAt: expiresAt.toISOString(),
        correlationId: request.correlationId,
      });
    }
  );

  /**
   * POST /v1/account/delete/confirm
   * Confirm account deletion using verification token (web flow)
   */
  fastify.post('/delete/confirm', async (request, reply) => {
    const parseResult = confirmDeletionSchema.safeParse(request.body);

    if (!parseResult.success) {
      throw new ValidationError('Invalid request body', {
        errors: parseResult.error.errors,
      });
    }

    const { token } = parseResult.data;

    const result = await confirmDeletionByToken(token);

    if (!result.success) {
      request.log.warn(
        {
          event: 'account_deletion_confirmation_failed',
          reason: result.reason,
          correlationId: request.correlationId,
        },
        'Account deletion confirmation failed'
      );

      return reply.status(400).send({
        error: {
          code: 'CONFIRMATION_FAILED',
          message: result.reason || 'Failed to confirm account deletion',
          correlationId: request.correlationId,
        },
      });
    }

    request.log.info(
      {
        event: 'account_deleted_via_email',
        email: result.email,
        correlationId: request.correlationId,
      },
      'Account deleted via email verification'
    );

    return reply.status(200).send({
      success: true,
      message: 'Your account and all associated data have been permanently deleted',
      correlationId: request.correlationId,
    });
  });
};
