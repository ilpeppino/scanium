/**
 * Pricing V3 Routes
 *
 * POST /v1/pricing/v3 - Get resale price estimate for an item (manual trigger)
 */

import { FastifyInstance, FastifyPluginOptions, FastifyRequest, FastifyReply } from 'fastify';
import { Config } from '../../config/index.js';
import { ApiKeyManager } from '../classifier/api-key-manager.js';
import { PricingV3Service } from './service-v3.js';
import { pricingV3RequestSchema, PricingV3Response } from './types-v3.js';

// Shared service instance
let pricingV3Service: PricingV3Service | null = null;
let apiKeyManager: ApiKeyManager | null = null;

function getPricingV3Service(config: Config): PricingV3Service {
  if (!pricingV3Service) {
    pricingV3Service = new PricingV3Service({
      enabled: config.pricing.v3Enabled ?? false,
      timeoutMs: config.pricing.v3TimeoutMs ?? 15000,
      cacheTtlSeconds: config.pricing.v3CacheTtlSeconds ?? 86400, // 24h default
      dailyQuota: config.pricing.v3DailyQuota ?? 1000,
      promptVersion: config.pricing.v3PromptVersion ?? '1.0.0',
      openaiApiKey: config.pricing.openaiApiKey,
      openaiModel: config.pricing.openaiModel,
      catalogPath: config.pricing.catalogPath,
    });
  }
  return pricingV3Service;
}

function getApiKeyManager(config: Config): ApiKeyManager {
  if (!apiKeyManager) {
    apiKeyManager = new ApiKeyManager(config.classifier.apiKeys);
  }
  return apiKeyManager;
}

/**
 * Error response helper
 */
function errorResponse(
  reply: FastifyReply,
  statusCode: number,
  code: string,
  message: string
): FastifyReply {
  return reply.status(statusCode).send({
    success: false,
    error: { code, message },
  });
}

/**
 * Register pricing V3 routes
 */
export async function pricingV3Routes(
  app: FastifyInstance,
  options: FastifyPluginOptions & { config: Config }
): Promise<void> {
  const { config } = options;

  /**
   * POST /pricing/v3
   *
   * Get resale price estimate for an item based on brand, model, condition, and region.
   *
   * Request body:
   * - itemId: string - Item ID for tracking
   * - brand: string - Brand name
   * - productType: string - Product type from domainCategoryId
   * - model: string - Model name/number
   * - condition: ItemCondition - Item condition (7 values)
   * - countryCode: string - ISO 2-letter country code
   * - year?: number - Optional year of manufacture
   * - hasAccessories?: boolean - Optional accessories flag
   *
   * Response:
   * - success: boolean
   * - pricing: PricingV3Insights
   * - cached: boolean
   * - processingTimeMs: number
   * - promptVersion: string
   */
  app.post('/pricing/v3', async (request: FastifyRequest, reply: FastifyReply) => {
    const startTime = Date.now();

    // Validate API key
    const apiKey = request.headers['x-api-key'] as string | undefined;
    if (!apiKey) {
      return errorResponse(reply, 401, 'UNAUTHORIZED', 'API key required');
    }

    const keyManager = getApiKeyManager(config);
    if (!keyManager.validateKey(apiKey)) {
      return errorResponse(reply, 401, 'UNAUTHORIZED', 'Invalid API key');
    }

    // Parse and validate body
    const validationResult = pricingV3RequestSchema.safeParse(request.body);
    if (!validationResult.success) {
      return errorResponse(
        reply,
        400,
        'INVALID_REQUEST',
        `Validation failed: ${validationResult.error.errors.map((e) => e.message).join(', ')}`
      );
    }

    const requestBody = validationResult.data;

    // Get pricing service
    const service = getPricingV3Service(config);

    const cacheStats = service.getCacheStats();
    const wasCached = cacheStats.size > 0; // Simple heuristic

    // Estimate price
    try {
      const insights = await service.estimateResalePrice(requestBody);

      const processingTimeMs = Date.now() - startTime;

      // Log request
      app.log.info({
        msg: 'Pricing V3 request completed',
        itemId: requestBody.itemId,
        brand: requestBody.brand.length, // Length only, not value (privacy)
        productType: requestBody.productType,
        model: requestBody.model.length,
        condition: requestBody.condition,
        countryCode: requestBody.countryCode,
        status: insights.status,
        confidence: insights.confidence,
        processingTimeMs,
        cached: wasCached,
      });

      // Build response
      const response: PricingV3Response = {
        success: insights.status === 'OK' || insights.status === 'NO_RESULTS',
        pricing: insights,
        cached: wasCached,
        processingTimeMs,
        promptVersion: service.getPromptVersion(),
      };

      // Return appropriate status code
      if (insights.status === 'RATE_LIMITED') {
        return reply.status(429).send(response);
      }

      if (insights.status === 'TIMEOUT') {
        return reply.status(504).send(response);
      }

      if (insights.status === 'DISABLED') {
        return reply.status(503).send(response);
      }

      if (insights.status === 'ERROR') {
        return reply.status(500).send(response);
      }

      return reply.status(200).send(response);
    } catch (error) {
      app.log.error({
        msg: 'Pricing V3 request failed',
        itemId: requestBody.itemId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });

      return errorResponse(
        reply,
        500,
        'INTERNAL_ERROR',
        error instanceof Error ? error.message : 'Unknown error'
      );
    }
  });

  // Cleanup on app close
  app.addHook('onClose', async () => {
    if (pricingV3Service) {
      pricingV3Service.stop();
    }
  });
}
