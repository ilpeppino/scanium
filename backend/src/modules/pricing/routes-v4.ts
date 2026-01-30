/**
 * Pricing V4 Routes
 *
 * POST /v1/pricing/v4 - Verifiable price range based on marketplace listings
 */

import { FastifyInstance, FastifyPluginOptions, FastifyRequest, FastifyReply } from 'fastify';
import { Config } from '../../config/index.js';
import { ApiKeyManager } from '../classifier/api-key-manager.js';
import { PricingV4Service } from './service-v4.js';
import { pricingV4RequestSchema, PricingV4Response } from './types-v4.js';

let pricingV4Service: PricingV4Service | null = null;
let pricingV4ServiceOverride: PricingV4Service | null = null;
let apiKeyManager: ApiKeyManager | null = null;

export function setPricingV4ServiceForTesting(service: PricingV4Service | null): void {
  pricingV4ServiceOverride = service;
}

function getPricingV4Service(config: Config): PricingV4Service {
  if (pricingV4ServiceOverride) {
    return pricingV4ServiceOverride;
  }
  if (!pricingV4Service) {
    pricingV4Service = new PricingV4Service(config);
  }
  return pricingV4Service;
}

function getApiKeyManager(config: Config): ApiKeyManager {
  if (!apiKeyManager) {
    apiKeyManager = new ApiKeyManager(config.classifier.apiKeys);
  }
  return apiKeyManager;
}

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

export async function pricingV4Routes(
  app: FastifyInstance,
  options: FastifyPluginOptions & { config: Config }
): Promise<void> {
  const { config } = options;

  app.post('/pricing/v4', async (request: FastifyRequest, reply: FastifyReply) => {
    const startTime = Date.now();

    const apiKey = request.headers['x-api-key'] as string | undefined;
    if (!apiKey) {
      return errorResponse(reply, 401, 'UNAUTHORIZED', 'API key required');
    }

    const keyManager = getApiKeyManager(config);
    if (!keyManager.validateKey(apiKey)) {
      return errorResponse(reply, 401, 'UNAUTHORIZED', 'Invalid API key');
    }

    const validationResult = pricingV4RequestSchema.safeParse(request.body);
    if (!validationResult.success) {
      return errorResponse(
        reply,
        400,
        'INVALID_REQUEST',
        `Validation failed: ${validationResult.error.errors.map((e) => e.message).join(', ')}`
      );
    }

    const requestBody = validationResult.data;
    const service = getPricingV4Service(config);
    const cacheStats = service.getCacheStats();
    const wasCached = cacheStats.size > 0;

    try {
      const insights = await service.estimateVerifiableRange(requestBody);
      const processingTimeMs = Date.now() - startTime;

      app.log.info({
        msg: 'Pricing V4 request completed',
        itemId: requestBody.itemId,
        brand: requestBody.brand.length,
        productType: requestBody.productType,
        model: requestBody.model?.length ?? 0,
        condition: requestBody.condition,
        countryCode: requestBody.countryCode,
        status: insights.status,
        confidence: insights.confidence,
        processingTimeMs,
        cached: wasCached,
      });

      const response: PricingV4Response = {
        success: insights.status === 'OK' || insights.status === 'NO_RESULTS' || insights.status === 'FALLBACK',
        pricing: insights,
        cached: wasCached,
        processingTimeMs,
      };

      if (insights.status === 'TIMEOUT') {
        return reply.status(504).send(response);
      }

      if (insights.status === 'ERROR') {
        return reply.status(500).send(response);
      }

      return reply.status(200).send(response);
    } catch (error) {
      app.log.error({
        msg: 'Pricing V4 request failed',
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

  app.addHook('onClose', async () => {
    if (pricingV4Service) {
      pricingV4Service.stop();
    }
  });
}
