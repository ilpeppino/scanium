/**
 * Enrichment Routes
 *
 * POST /v1/items/enrich - Submit item for enrichment
 * GET /v1/items/enrich/status/:requestId - Get enrichment status
 */

import { FastifyInstance, FastifyPluginOptions, FastifyRequest, FastifyReply } from 'fastify';
import { z } from 'zod';
import { Config } from '../../config/index.js';
import { ApiKeyManager } from '../classifier/api-key-manager.js';
import { getEnrichmentManager, shutdownEnrichmentManager } from './enrich-manager.js';
import { EnrichRequest } from './types.js';
import {
  recordEnrichRequest,
  recordEnrichStage,
} from '../../infra/telemetry/enrich-metrics.js';

// Zod schemas for request validation
const EnrichRequestBodySchema = z.object({
  itemId: z.string().min(1).max(100),
  itemContext: z
    .object({
      title: z.string().max(200).optional(),
      category: z.string().max(100).optional(),
      condition: z.string().max(50).optional(),
      priceCents: z.number().int().positive().optional(),
    })
    .optional(),
});

const EnrichStatusParamsSchema = z.object({
  requestId: z.string().uuid(),
});

// Error response helper
function errorResponse(
  reply: FastifyReply,
  statusCode: number,
  code: string,
  message: string,
  correlationId?: string
): FastifyReply {
  return reply.status(statusCode).send({
    success: false,
    error: { code, message, correlationId },
  });
}

// Shared API key manager instance
let apiKeyManager: ApiKeyManager | null = null;

function getApiKeyManager(config: Config): ApiKeyManager {
  if (!apiKeyManager) {
    apiKeyManager = new ApiKeyManager(config.classifier.apiKeys);
  }
  return apiKeyManager;
}

/**
 * Register enrichment routes.
 */
export async function enrichRoutes(
  app: FastifyInstance,
  options: FastifyPluginOptions & { config: Config }
): Promise<void> {
  const { config } = options;

  // Initialize enrichment manager
  const enrichManager = getEnrichmentManager(app.log, {
    enableDraftGeneration: true,
    llmModel: config.assistant.openaiModel || 'gpt-4o-mini',
  });

  // Cleanup on shutdown
  app.addHook('onClose', async () => {
    shutdownEnrichmentManager();
  });

  /**
   * POST /items/enrich
   *
   * Submit an item for enrichment processing.
   * Accepts multipart form with image + JSON body.
   */
  app.post('/items/enrich', async (request: FastifyRequest, reply: FastifyReply) => {
    const correlationId = (request.headers['x-scanium-correlation-id'] as string) || 'unknown';
    const startTime = Date.now();

    // Validate API key
    const apiKey = request.headers['x-api-key'] as string | undefined;
    if (!apiKey) {
      recordEnrichRequest({ status: 'error', errorType: 'UNAUTHORIZED' });
      return errorResponse(reply, 401, 'UNAUTHORIZED', 'API key required', correlationId);
    }

    const keyManager = getApiKeyManager(config);
    if (!keyManager.validateKey(apiKey)) {
      recordEnrichRequest({ status: 'error', errorType: 'UNAUTHORIZED' });
      return errorResponse(reply, 401, 'UNAUTHORIZED', 'Invalid API key', correlationId);
    }

    // Log API key usage
    keyManager.logUsage({
      apiKey,
      timestamp: new Date(),
      endpoint: '/v1/items/enrich',
      method: 'POST',
      success: true,
      ip: request.ip,
      userAgent: request.headers['user-agent'],
    });

    try {
      // Parse multipart form data
      const data = await request.file();
      if (!data) {
        recordEnrichRequest({ status: 'error', errorType: 'INVALID_REQUEST' });
        return errorResponse(reply, 400, 'INVALID_REQUEST', 'Image file required', correlationId);
      }

      // Read image data
      const imageBuffer = await data.toBuffer();
      const imageBase64 = imageBuffer.toString('base64');
      const imageMimeType = data.mimetype as 'image/jpeg' | 'image/png';

      // Validate mime type
      if (!['image/jpeg', 'image/png'].includes(imageMimeType)) {
        recordEnrichRequest({ status: 'error', errorType: 'INVALID_REQUEST' });
        return errorResponse(
          reply,
          400,
          'INVALID_REQUEST',
          'Only JPEG and PNG images are supported',
          correlationId
        );
      }

      // Parse JSON fields from form data
      const fields: Record<string, string> = {};
      if (data.fields) {
        for (const [key, field] of Object.entries(data.fields)) {
          if (field && typeof field === 'object' && 'value' in field) {
            fields[key] = (field as { value: string }).value;
          }
        }
      }

      // Parse and validate body
      let body: z.infer<typeof EnrichRequestBodySchema>;
      try {
        const rawBody = fields.data ? JSON.parse(fields.data) : { itemId: fields.itemId };
        body = EnrichRequestBodySchema.parse(rawBody);
      } catch (e) {
        recordEnrichRequest({ status: 'error', errorType: 'INVALID_REQUEST' });
        return errorResponse(
          reply,
          400,
          'INVALID_REQUEST',
          'Invalid request body: itemId is required',
          correlationId
        );
      }

      // Build enrichment request
      const enrichRequest: EnrichRequest = {
        itemId: body.itemId,
        imageBase64,
        imageMimeType,
        itemContext: body.itemContext,
      };

      // Submit to enrichment manager
      const result = await enrichManager.submit(enrichRequest, correlationId);

      recordEnrichRequest({ status: 'success' });

      app.log.info({
        msg: 'Enrichment request accepted',
        requestId: result.requestId,
        correlationId: result.correlationId,
        itemId: body.itemId,
        latencyMs: Date.now() - startTime,
      });

      return reply.status(202).send({
        success: true,
        requestId: result.requestId,
        correlationId: result.correlationId,
      });
    } catch (err) {
      app.log.error({
        msg: 'Enrichment submission error',
        correlationId,
        error: err instanceof Error ? err.message : String(err),
      });

      recordEnrichRequest({ status: 'error', errorType: 'INTERNAL_ERROR' });
      return errorResponse(
        reply,
        500,
        'INTERNAL_ERROR',
        'Failed to process enrichment request',
        correlationId
      );
    }
  });

  /**
   * GET /items/enrich/status/:requestId
   *
   * Get the status of an enrichment request.
   */
  app.get('/items/enrich/status/:requestId', async (request: FastifyRequest, reply: FastifyReply) => {
    const correlationId = (request.headers['x-scanium-correlation-id'] as string) || 'unknown';

    // Validate API key
    const apiKey = request.headers['x-api-key'] as string | undefined;
    if (!apiKey) {
      return errorResponse(reply, 401, 'UNAUTHORIZED', 'API key required', correlationId);
    }

    const keyManager = getApiKeyManager(config);
    if (!keyManager.validateKey(apiKey)) {
      return errorResponse(reply, 401, 'UNAUTHORIZED', 'Invalid API key', correlationId);
    }

    // Parse and validate params
    let params: z.infer<typeof EnrichStatusParamsSchema>;
    try {
      params = EnrichStatusParamsSchema.parse(request.params);
    } catch (e) {
      return errorResponse(reply, 400, 'INVALID_REQUEST', 'Invalid request ID format', correlationId);
    }

    // Get status from manager
    const status = enrichManager.getStatus(params.requestId);

    if (!status) {
      return errorResponse(reply, 404, 'NOT_FOUND', 'Request not found', correlationId);
    }

    // Record stage metric
    recordEnrichStage({ stage: status.stage });

    return reply.status(200).send({
      success: true,
      status: {
        requestId: status.requestId,
        correlationId: status.correlationId,
        itemId: status.itemId,
        stage: status.stage,
        visionFacts: status.visionFacts,
        normalizedAttributes: status.normalizedAttributes,
        draft: status.draft,
        error: status.error,
        createdAt: status.createdAt,
        updatedAt: status.updatedAt,
        timings: status.timings,
      },
    });
  });

  /**
   * GET /items/enrich/metrics
   *
   * Get enrichment manager metrics (for monitoring).
   */
  app.get('/items/enrich/metrics', async (_request: FastifyRequest, reply: FastifyReply) => {
    // No API key required for basic metrics (health-check style)
    const metrics = enrichManager.getMetrics();

    return reply.status(200).send({
      success: true,
      metrics,
    });
  });
}
