import { randomUUID } from 'node:crypto';
import { FastifyPluginAsync } from 'fastify';
import { Config } from '../../../config/index.js';
import { prisma } from '../../../infra/db/prisma.js';
import { CorrectionPayload, CorrectionResponse } from './types.js';

type RouteOpts = {
  config: Config;
};

/**
 * Routes for classification corrections.
 * Allows users to report when classifications are wrong.
 */
export const correctionRoutes: FastifyPluginAsync<RouteOpts> = async (
  fastify,
  _opts
) => {

  /**
   * POST /corrections
   * Submit a classification correction.
   */
  fastify.post<{
    Body: CorrectionPayload;
  }>('/corrections', async (request, reply) => {
    const correlationId = request.correlationId ?? randomUUID();
    const body = request.body;

    // Validate required fields
    if (!body.imageHash || !body.correctedCategory) {
      return reply.status(400).send({
        error: {
          code: 'VALIDATION_ERROR',
          message: 'imageHash and correctedCategory required',
          correlationId,
        },
      });
    }

    // Extract userId from authenticated session (if available)
    // For now, we'll use the API key as a proxy for userId
    const apiKey = request.headers['x-api-key'] as string | undefined;
    const deviceId = request.headers['x-device-id'] as string | undefined;

    try {
      // Store correction in database
      const correction = await prisma.classificationCorrection.create({
        data: {
          userId: apiKey ? apiKey.substring(0, 32) : null, // Use API key prefix as userId proxy
          deviceId: deviceId ?? null,
          imageHash: body.imageHash,
          predictedCategory: body.predictedCategory ?? 'unknown',
          predictedConfidence: body.predictedConfidence ?? null,
          correctedCategory: body.correctedCategory,
          correctionMethod: body.correctionMethod,
          notes: body.notes ?? null,
          perceptionSnapshot: (body.perceptionSnapshot ?? undefined) as any,
        },
      });

      request.log.info(
        {
          correctionId: correction.id,
          predictedCategory: body.predictedCategory,
          correctedCategory: body.correctedCategory,
          correlationId,
        },
        'Classification correction stored'
      );

      const response: CorrectionResponse = {
        success: true,
        correctionId: correction.id,
        correlationId,
      };

      return reply.status(201).send(response);
    } catch (error) {
      request.log.error(
        {
          error: error instanceof Error ? error.message : 'Unknown error',
          correlationId,
        },
        'Failed to store classification correction'
      );

      return reply.status(500).send({
        error: {
          code: 'INTERNAL_ERROR',
          message: 'Failed to store correction',
          correlationId,
        },
      });
    }
  });

  /**
   * GET /corrections
   * Get correction history for the authenticated user.
   */
  fastify.get('/corrections', async (request, reply) => {
    const correlationId = request.correlationId ?? randomUUID();
    const apiKey = request.headers['x-api-key'] as string | undefined;

    if (!apiKey) {
      return reply.status(401).send({
        error: {
          code: 'UNAUTHORIZED',
          message: 'API key required',
          correlationId,
        },
      });
    }

    try {
      const userId = apiKey.substring(0, 32);
      const corrections = await prisma.classificationCorrection.findMany({
        where: { userId },
        orderBy: { createdAt: 'desc' },
        take: 50,
      });

      return reply.status(200).send({
        corrections,
        correlationId,
      });
    } catch (error) {
      request.log.error(
        {
          error: error instanceof Error ? error.message : 'Unknown error',
          correlationId,
        },
        'Failed to fetch corrections'
      );

      return reply.status(500).send({
        error: {
          code: 'INTERNAL_ERROR',
          message: 'Failed to fetch corrections',
          correlationId,
        },
      });
    }
  });
};
