/**
 * Pricing Routes
 *
 * POST /v1/pricing/estimate - Get price estimate for an item
 * GET /v1/pricing/categories - List supported categories with price ranges
 * GET /v1/pricing/brands/:brand - Check brand tier
 */

import { FastifyInstance, FastifyPluginOptions, FastifyRequest, FastifyReply } from 'fastify';
import { z } from 'zod';
import { Config } from '../../config/index.js';
import { ApiKeyManager } from '../classifier/api-key-manager.js';
import { estimatePrice, formatPriceRange } from './estimator.js';
import { CATEGORY_PRICING } from './category-pricing.js';
import { getBrandTier, getBrandTierLabel, isKnownBrand } from './brand-tiers.js';
import { parseCondition } from './condition-modifiers.js';
import { PriceEstimateInput, ItemCondition } from './types.js';

// Zod schemas for request validation
const PriceEstimateBodySchema = z.object({
  itemId: z.string().min(1).max(100),
  category: z.string().max(100).optional(),
  segment: z.string().max(50).optional(),
  brand: z.string().max(100).optional(),
  brandConfidence: z.enum(['HIGH', 'MED', 'LOW']).optional(),
  productType: z.string().max(100).optional(),
  condition: z.string().max(50).optional(),
  completeness: z
    .object({
      hasOriginalBox: z.boolean().optional(),
      hasTags: z.boolean().optional(),
      isSealed: z.boolean().optional(),
      hasAccessories: z.boolean().optional(),
      hasDocumentation: z.boolean().optional(),
    })
    .optional(),
  material: z.string().max(50).optional(),
  visionHints: z
    .object({
      ocrSnippets: z.array(z.string()).optional(),
      labels: z.array(z.string()).optional(),
    })
    .optional(),
});

const BrandCheckParamsSchema = z.object({
  brand: z.string().min(1).max(100),
});

// Error response helper
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

// Shared API key manager instance
let apiKeyManager: ApiKeyManager | null = null;

function getApiKeyManager(config: Config): ApiKeyManager {
  if (!apiKeyManager) {
    apiKeyManager = new ApiKeyManager(config.classifier.apiKeys);
  }
  return apiKeyManager;
}

/**
 * Register pricing routes.
 */
export async function pricingRoutes(
  app: FastifyInstance,
  options: FastifyPluginOptions & { config: Config }
): Promise<void> {
  const { config } = options;

  /**
   * POST /pricing/estimate
   *
   * Get a price estimate for an item based on its attributes.
   */
  app.post('/pricing/estimate', async (request: FastifyRequest, reply: FastifyReply) => {
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
    let body: z.infer<typeof PriceEstimateBodySchema>;
    try {
      body = PriceEstimateBodySchema.parse(request.body);
    } catch (e) {
      const zodError = e as z.ZodError;
      return errorResponse(
        reply,
        400,
        'INVALID_REQUEST',
        `Invalid request body: ${zodError.errors.map((err) => err.message).join(', ')}`
      );
    }

    // Build pricing input
    const input: PriceEstimateInput = {
      itemId: body.itemId,
      category: body.category,
      segment: body.segment,
      brand: body.brand,
      brandConfidence: body.brandConfidence,
      productType: body.productType,
      condition: body.condition ? parseCondition(body.condition) : undefined,
      completeness: body.completeness,
      material: body.material,
      visionHints: body.visionHints,
    };

    // Get price estimate
    const result = estimatePrice(input);

    app.log.info({
      msg: 'Price estimate generated',
      itemId: body.itemId,
      category: body.category,
      brand: body.brand,
      condition: body.condition,
      priceRange: formatPriceRange(result.priceEstimateMinCents, result.priceEstimateMaxCents),
      confidence: result.confidence,
    });

    return reply.status(200).send({
      success: true,
      estimate: {
        priceEstimateMinCents: result.priceEstimateMinCents,
        priceEstimateMaxCents: result.priceEstimateMaxCents,
        priceEstimateMin: result.priceEstimateMinCents / 100,
        priceEstimateMax: result.priceEstimateMaxCents / 100,
        priceRangeFormatted: formatPriceRange(
          result.priceEstimateMinCents,
          result.priceEstimateMaxCents
        ),
        confidence: result.confidence,
        explanation: result.explanation,
        caveats: result.caveats,
        inputSummary: result.inputSummary,
      },
      // Include calculation steps in debug mode
      ...(request.headers['x-debug'] === 'true' && {
        debug: {
          calculationSteps: result.calculationSteps,
        },
      }),
    });
  });

  /**
   * GET /pricing/categories
   *
   * List all supported categories with their price ranges.
   */
  app.get('/pricing/categories', async (_request: FastifyRequest, reply: FastifyReply) => {
    const categories = Object.values(CATEGORY_PRICING).map((cat) => ({
      id: cat.id,
      label: cat.label,
      baseRangeMin: cat.baseRangeCents[0] / 100,
      baseRangeMax: cat.baseRangeCents[1] / 100,
      maxCap: cat.maxCapCents / 100,
      minFloor: cat.minFloorCents / 100,
      notes: cat.notes,
    }));

    return reply.status(200).send({
      success: true,
      categories,
      count: categories.length,
    });
  });

  /**
   * GET /pricing/brands/:brand
   *
   * Check the tier classification for a brand.
   */
  app.get('/pricing/brands/:brand', async (request: FastifyRequest, reply: FastifyReply) => {
    // Parse and validate params
    let params: z.infer<typeof BrandCheckParamsSchema>;
    try {
      params = BrandCheckParamsSchema.parse(request.params);
    } catch (e) {
      return errorResponse(reply, 400, 'INVALID_REQUEST', 'Invalid brand parameter');
    }

    const tier = getBrandTier(params.brand);
    const known = isKnownBrand(params.brand);

    return reply.status(200).send({
      success: true,
      brand: {
        name: params.brand,
        tier,
        tierLabel: getBrandTierLabel(tier),
        isKnown: known,
      },
    });
  });

  /**
   * GET /pricing/conditions
   *
   * List all condition levels with their multipliers.
   */
  app.get('/pricing/conditions', async (_request: FastifyRequest, reply: FastifyReply) => {
    const conditions: Array<{
      value: ItemCondition;
      label: string;
      multiplier: number;
      description: string;
    }> = [
      {
        value: 'NEW_SEALED',
        label: 'New, Factory Sealed',
        multiplier: 1.6,
        description: 'Factory sealed, never opened',
      },
      {
        value: 'NEW_WITH_TAGS',
        label: 'New with Tags',
        multiplier: 1.4,
        description: 'New with original tags attached',
      },
      {
        value: 'NEW_WITHOUT_TAGS',
        label: 'New without Tags',
        multiplier: 1.2,
        description: 'New, no tags, never used',
      },
      {
        value: 'LIKE_NEW',
        label: 'Like New',
        multiplier: 1.1,
        description: 'Used briefly, no visible wear',
      },
      {
        value: 'GOOD',
        label: 'Good',
        multiplier: 1.0,
        description: 'Normal use, minor wear',
      },
      {
        value: 'FAIR',
        label: 'Fair',
        multiplier: 0.7,
        description: 'Noticeable wear, fully functional',
      },
      {
        value: 'POOR',
        label: 'Poor',
        multiplier: 0.4,
        description: 'Heavy wear, may have defects',
      },
    ];

    return reply.status(200).send({
      success: true,
      conditions,
    });
  });
}
