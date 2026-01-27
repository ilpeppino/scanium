/**
 * Pricing Variant Schema Routes
 *
 * GET /v1/pricing/variant-schema?productType=...
 */

import { FastifyInstance, FastifyPluginOptions, FastifyRequest, FastifyReply } from 'fastify';
import { Config } from '../../config/index.js';
import { ApiKeyManager } from '../classifier/api-key-manager.js';
import { getVariantSchema } from './variant-schemas.js';

let apiKeyManager: ApiKeyManager | null = null;

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

export async function pricingVariantSchemaRoutes(
  app: FastifyInstance,
  options: FastifyPluginOptions & { config: Config }
): Promise<void> {
  const { config } = options;

  app.get('/pricing/variant-schema', async (request: FastifyRequest, reply: FastifyReply) => {
    const apiKey = request.headers['x-api-key'] as string | undefined;
    if (!apiKey) {
      return errorResponse(reply, 401, 'UNAUTHORIZED', 'API key required');
    }

    const keyManager = getApiKeyManager(config);
    if (!keyManager.validateKey(apiKey)) {
      return errorResponse(reply, 401, 'UNAUTHORIZED', 'Invalid API key');
    }

    const productType = (request.query as { productType?: string | undefined })?.productType;
    const schema = getVariantSchema(productType);

    return reply.status(200).send({
      success: true,
      fields: schema.fields,
      completenessOptions: schema.completenessOptions,
    });
  });
}
