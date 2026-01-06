/**
 * Vision Insights Routes
 *
 * Provides a dedicated endpoint for immediate vision extraction on scanned items.
 * This endpoint is designed for fast prefill of item attributes (OCR, brand, colors)
 * immediately after capture, without waiting for full classification.
 *
 * ## Endpoint
 * POST /v1/vision/insights
 *
 * ## Request
 * Multipart form-data with:
 * - `image`: JPEG/PNG file (max 2MB)
 * - `itemId` (optional): Associate results with an item ID
 *
 * ## Response
 * ```json
 * {
 *   "success": true,
 *   "ocrSnippets": ["text1", "text2"],
 *   "logoHints": [{"name": "Brand", "confidence": 0.95}],
 *   "dominantColors": [{"name": "blue", "hex": "#1E40AF", "pct": 45}],
 *   "labelHints": ["Label1", "Label2"],
 *   "suggestedLabel": "Brand Model",
 *   "categoryHint": "electronics",
 *   "extractionMeta": { "provider": "google-vision", "timingsMs": { "total": 500 } }
 * }
 * ```
 */

import { randomUUID } from 'node:crypto';
import { FastifyPluginAsync, FastifyRequest } from 'fastify';
import { Config } from '../../config/index.js';
import { ApiKeyManager } from '../classifier/api-key-manager.js';
import {
  VisionExtractor,
  MockVisionExtractor,
  VisualFactsCache,
  buildCacheKey,
  computeImageHash,
} from './index.js';
import { sanitizeImageBuffer, isSupportedImage } from '../classifier/utils/image.js';
import {
  SlidingWindowRateLimiter,
  RedisClient,
} from '../../infra/rate-limit/sliding-window-limiter.js';
import {
  recordVisionExtraction,
  recordRateLimitHit,
} from '../../infra/observability/metrics.js';
import { collectAttributeCandidates } from './attribute-resolver.js';

type RouteOpts = { config: Config };

// Image upload limits
const IMAGE_LIMITS = {
  MAX_FILE_SIZE_BYTES: 2 * 1024 * 1024, // 2MB
  ALLOWED_MIME_TYPES: ['image/jpeg', 'image/png'] as const,
};

type VisionInsightsResponse = {
  success: true;
  requestId: string;
  correlationId: string;
  /** Top OCR text snippets extracted from the image */
  ocrSnippets: string[];
  /** Detected logos/brands with confidence scores */
  logoHints: Array<{ name: string; confidence: number }>;
  /** Dominant colors from the image */
  dominantColors: Array<{ name: string; hex: string; pct: number }>;
  /** Label hints from image classification */
  labelHints: string[];
  /** Suggested label/name based on OCR + logos */
  suggestedLabel: string | null;
  /** Category hint based on labels */
  categoryHint: string | null;
  /** Extraction metadata */
  extractionMeta: {
    provider: string;
    timingsMs: { total: number };
    cacheHit: boolean;
  };
};

type VisionInsightsErrorResponse = {
  success: false;
  error: {
    code: string;
    message: string;
    correlationId: string;
  };
};

export const visionInsightsRoutes: FastifyPluginAsync<RouteOpts> = async (
  fastify,
  opts
) => {
  const { config } = opts;
  const apiKeyManager = new ApiKeyManager(config.classifier.apiKeys);

  // Initialize VisionExtractor
  const visionExtractor =
    config.vision.provider === 'google'
      ? new VisionExtractor({
          timeoutMs: config.vision.timeoutMs,
          maxRetries: config.vision.maxRetries,
          enableLogoDetection: config.vision.enableLogos,
          maxOcrSnippetLength: config.vision.maxOcrSnippetLength,
          minOcrConfidence: config.vision.minOcrConfidence,
          minLabelConfidence: config.vision.minLabelConfidence,
          minLogoConfidence: config.vision.minLogoConfidence,
        })
      : new MockVisionExtractor();

  const visionCache = new VisualFactsCache({
    ttlMs: config.vision.cacheTtlSeconds * 1000,
    maxEntries: config.vision.cacheMaxEntries,
  });

  // Build feature key for cache
  const visionFeatureKey = [
    config.vision.enableOcr ? `ocr:${config.vision.ocrMode}` : null,
    config.vision.enableLabels ? 'labels' : null,
    config.vision.enableLogos ? 'logos' : null,
    config.vision.enableColors ? 'colors' : null,
  ]
    .filter(Boolean)
    .join('|') || 'none';

  // Rate limiting
  const redisClient = await createRedisClient(
    config.classifier.rateLimitRedisUrl,
    fastify.log
  );

  const windowMs = config.classifier.rateLimitWindowSeconds * 1000;
  const baseBackoffMs = config.classifier.rateLimitBackoffSeconds * 1000;
  const maxBackoffMs = config.classifier.rateLimitBackoffMaxSeconds * 1000;

  const ipRateLimiter = new SlidingWindowRateLimiter({
    windowMs,
    max: config.classifier.ipRateLimitPerMinute * 2, // Allow higher rate for vision-only
    baseBackoffMs,
    maxBackoffMs,
    prefix: 'rl:vision:ip',
    redis: redisClient,
  });

  const apiKeyRateLimiter = new SlidingWindowRateLimiter({
    windowMs,
    max: config.classifier.rateLimitPerMinute * 2, // Allow higher rate for vision-only
    baseBackoffMs,
    maxBackoffMs,
    prefix: 'rl:vision:api',
    redis: redisClient,
  });

  fastify.addHook('onClose', async () => {
    visionCache.stop();
    await redisClient?.quit?.();
  });

  /**
   * POST /v1/vision/insights
   *
   * Extract visual facts from an image for immediate prefill.
   */
  fastify.post('/vision/insights', async (request, reply) => {
    const requestId = randomUUID();
    const correlationId = request.correlationId ?? requestId;

    // API key validation
    const apiKey = extractApiKey(request);
    if (!apiKey || !apiKeyManager.validateKey(apiKey)) {
      return reply.status(401).send({
        success: false,
        error: {
          code: 'UNAUTHORIZED',
          message: 'Missing or invalid API key',
          correlationId,
        },
      } as VisionInsightsErrorResponse);
    }

    // Multipart validation
    if (!request.isMultipart()) {
      return reply.status(400).send({
        success: false,
        error: {
          code: 'VALIDATION_ERROR',
          message: 'multipart/form-data required',
          correlationId,
        },
      } as VisionInsightsErrorResponse);
    }

    // Rate limiting - IP
    const ipLimit = await ipRateLimiter.consume(request.ip);
    if (!ipLimit.allowed) {
      recordRateLimitHit('ip', '/vision/insights');
      return reply
        .status(429)
        .header('Retry-After', String(ipLimit.retryAfterSeconds))
        .send({
          success: false,
          error: {
            code: 'RATE_LIMITED',
            message: 'Too many requests. Please retry after the cooldown.',
            correlationId,
          },
        } as VisionInsightsErrorResponse);
    }

    // Rate limiting - API key
    const apiKeyLimit = await apiKeyRateLimiter.consume(apiKey);
    if (!apiKeyLimit.allowed) {
      recordRateLimitHit('api_key', '/vision/insights');
      return reply
        .status(429)
        .header('Retry-After', String(apiKeyLimit.retryAfterSeconds))
        .send({
          success: false,
          error: {
            code: 'RATE_LIMITED',
            message: 'Too many requests. Please retry after the cooldown.',
            correlationId,
          },
        } as VisionInsightsErrorResponse);
    }

    try {
      // Parse multipart
      const file = await request.file();
      if (!file) {
        return reply.status(400).send({
          success: false,
          error: {
            code: 'VALIDATION_ERROR',
            message: 'image file is required',
            correlationId,
          },
        } as VisionInsightsErrorResponse);
      }

      // Validate MIME type
      if (!isSupportedImage(file.mimetype)) {
        return reply.status(400).send({
          success: false,
          error: {
            code: 'VALIDATION_ERROR',
            message: 'Unsupported image type. Use JPEG or PNG.',
            correlationId,
          },
        } as VisionInsightsErrorResponse);
      }

      // Read file with size validation
      const buffer = await readFileWithSizeValidation(
        file,
        IMAGE_LIMITS.MAX_FILE_SIZE_BYTES
      );

      // Sanitize image
      let sanitized: { buffer: Buffer; normalizedType: string };
      try {
        sanitized = await sanitizeImageBuffer(buffer, file.mimetype);
      } catch (error) {
        request.log.warn(
          { correlationId, error: error instanceof Error ? error.message : 'Unknown' },
          'Invalid image data rejected'
        );
        return reply.status(400).send({
          success: false,
          error: {
            code: 'INVALID_IMAGE',
            message: 'Invalid or corrupted image data.',
            correlationId,
          },
        } as VisionInsightsErrorResponse);
      }

      // Extract item ID from form fields (optional)
      const itemId = extractFieldValue(file.fields, 'itemId') ?? requestId;

      // Check cache
      const base64Data = sanitized.buffer.toString('base64');
      const imageHash = computeImageHash(base64Data);
      const cacheKey = buildCacheKey([imageHash], { featureVersion: visionFeatureKey, mode: 'insights' });

      const cachedFacts = visionCache.get(cacheKey);
      if (cachedFacts) {
        request.log.info(
          { correlationId, itemId, cacheHit: true },
          'Vision insights cache hit'
        );

        const response = buildResponse(
          cachedFacts,
          requestId,
          correlationId,
          config.vision.provider,
          true
        );
        return reply.status(200).send(response);
      }

      // Extract visual facts
      const startTime = performance.now();
      const extractionResult = await visionExtractor.extractVisualFacts(
        itemId,
        [
          {
            base64Data,
            mimeType: sanitized.normalizedType as 'image/jpeg' | 'image/png',
            filename: file.filename,
          },
        ],
        {
          enableOcr: config.vision.enableOcr,
          enableLabels: config.vision.enableLabels,
          enableLogos: config.vision.enableLogos,
          enableColors: config.vision.enableColors,
          maxOcrSnippets: config.vision.maxOcrSnippets,
          maxLabelHints: config.vision.maxLabelHints,
          maxLogoHints: config.vision.maxLogoHints,
          maxColors: config.vision.maxColors,
          ocrMode: config.vision.ocrMode,
        }
      );

      const latencyMs = Math.round(performance.now() - startTime);

      if (!extractionResult.success || !extractionResult.facts) {
        request.log.warn(
          {
            correlationId,
            itemId,
            error: extractionResult.error,
            errorCode: extractionResult.errorCode,
            latencyMs,
          },
          'Vision extraction failed'
        );
        recordVisionExtraction(config.vision.provider, 'error', latencyMs, []);

        return reply.status(503).send({
          success: false,
          error: {
            code: extractionResult.errorCode ?? 'VISION_UNAVAILABLE',
            message: extractionResult.error ?? 'Vision extraction failed',
            correlationId,
          },
        } as VisionInsightsErrorResponse);
      }

      // Cache the result
      visionCache.set(cacheKey, extractionResult.facts);

      // Record metrics
      const features: string[] = [];
      if (config.vision.enableOcr) features.push('ocr');
      if (config.vision.enableLabels) features.push('labels');
      if (config.vision.enableLogos) features.push('logos');
      if (config.vision.enableColors) features.push('colors');
      recordVisionExtraction(config.vision.provider, 'success', latencyMs, features);

      request.log.info(
        {
          correlationId,
          itemId,
          latencyMs,
          ocrSnippets: extractionResult.facts.ocrSnippets.length,
          logos: extractionResult.facts.logoHints?.length ?? 0,
          colors: extractionResult.facts.dominantColors.length,
          labels: extractionResult.facts.labelHints.length,
        },
        'Vision insights extracted'
      );

      const response = buildResponse(
        extractionResult.facts,
        requestId,
        correlationId,
        config.vision.provider,
        false
      );
      return reply.status(200).send(response);
    } catch (error) {
      // Handle file size errors
      if (error instanceof Error && error.message.includes('File size exceeds')) {
        return reply.status(413).send({
          success: false,
          error: {
            code: 'FILE_TOO_LARGE',
            message: `Image exceeds maximum size of ${IMAGE_LIMITS.MAX_FILE_SIZE_BYTES / 1024 / 1024}MB`,
            correlationId,
          },
        } as VisionInsightsErrorResponse);
      }

      request.log.error({ correlationId, error }, 'Vision insights error');
      throw error;
    }
  });
};

/**
 * Build the response from visual facts.
 */
function buildResponse(
  facts: import('./types.js').VisualFacts,
  requestId: string,
  correlationId: string,
  _provider: string,
  cacheHit: boolean
): VisionInsightsResponse {
  // Extract attribute candidates
  const { brandCandidates, modelCandidates } = collectAttributeCandidates(facts);

  // Build suggested label from brand + model or OCR
  let suggestedLabel: string | null = null;
  if (brandCandidates.length > 0) {
    if (modelCandidates.length > 0) {
      suggestedLabel = `${brandCandidates[0]} ${modelCandidates[0]}`;
    } else {
      suggestedLabel = brandCandidates[0];
    }
  } else if (facts.ocrSnippets.length > 0) {
    // Use first meaningful OCR snippet as fallback
    const firstSnippet = facts.ocrSnippets[0].text.trim();
    if (firstSnippet.length >= 3 && firstSnippet.length <= 50) {
      suggestedLabel = firstSnippet;
    }
  }

  // Derive category hint from labels
  const categoryHint = deriveCategoryHint(facts.labelHints);

  return {
    success: true,
    requestId,
    correlationId,
    ocrSnippets: facts.ocrSnippets.map((s) => s.text).slice(0, 5),
    logoHints: (facts.logoHints ?? []).map((l) => ({
      name: l.brand,
      confidence: l.score,
    })),
    dominantColors: facts.dominantColors.map((c) => ({
      name: c.name,
      hex: c.rgbHex,
      pct: c.pct,
    })),
    labelHints: facts.labelHints.map((l) => l.label).slice(0, 5),
    suggestedLabel,
    categoryHint,
    extractionMeta: {
      provider: facts.extractionMeta.provider,
      timingsMs: { total: facts.extractionMeta.timingsMs.total },
      cacheHit,
    },
  };
}

/**
 * Derive a category hint from label hints.
 * Maps common Vision API labels to our domain categories.
 */
function deriveCategoryHint(
  labelHints: Array<{ label: string; score: number }>
): string | null {
  const labelMap: Record<string, string> = {
    // Electronics
    electronics: 'electronics',
    computer: 'electronics',
    laptop: 'electronics',
    phone: 'electronics',
    mobile: 'electronics',
    camera: 'electronics',
    television: 'electronics',
    tv: 'electronics',
    // Furniture
    furniture: 'furniture',
    chair: 'furniture',
    table: 'furniture',
    sofa: 'furniture',
    couch: 'furniture',
    desk: 'furniture',
    shelf: 'furniture',
    cabinet: 'furniture',
    // Clothing
    clothing: 'clothing',
    shirt: 'clothing',
    pants: 'clothing',
    jacket: 'clothing',
    dress: 'clothing',
    shoe: 'clothing',
    footwear: 'clothing',
    // Home & Kitchen
    kitchenware: 'home_kitchen',
    cookware: 'home_kitchen',
    appliance: 'home_kitchen',
    'kitchen appliance': 'home_kitchen',
    // Books & Media
    book: 'books_media',
    magazine: 'books_media',
    dvd: 'books_media',
    cd: 'books_media',
    // Sports
    'sports equipment': 'sports',
    bicycle: 'sports',
    ball: 'sports',
    // Toys & Games
    toy: 'toys_games',
    game: 'toys_games',
    puzzle: 'toys_games',
    // Cosmetics
    cosmetics: 'cosmetics',
    makeup: 'cosmetics',
    skincare: 'cosmetics',
    lipstick: 'cosmetics',
    // Pet
    pet: 'pet_supplies',
    'pet supplies': 'pet_supplies',
    'dog food': 'pet_supplies',
    'cat food': 'pet_supplies',
  };

  for (const hint of labelHints) {
    const normalized = hint.label.toLowerCase();
    for (const [keyword, category] of Object.entries(labelMap)) {
      if (normalized.includes(keyword)) {
        return category;
      }
    }
  }

  return null;
}

function extractApiKey(request: FastifyRequest): string | undefined {
  const header = request.headers['x-api-key'];
  if (!header) return undefined;
  return Array.isArray(header) ? header[0] : header;
}

function extractFieldValue(
  fields: Record<string, unknown> | undefined,
  name: string
): string | undefined {
  if (!fields) return undefined;
  const field = fields[name];
  if (!field) return undefined;

  // Handle array of field objects
  if (Array.isArray(field)) {
    const entry = field[0] as { value?: unknown };
    return entry?.value ? String(entry.value) : undefined;
  }

  // Handle single field object
  const entry = field as { value?: unknown };
  return entry?.value ? String(entry.value) : undefined;
}

type RedisClientWithLifecycle = RedisClient & {
  connect?: () => Promise<void>;
  quit?: () => Promise<void>;
  on?: (event: string, listener: (error: unknown) => void) => void;
};

async function createRedisClient(
  url: string | undefined,
  logger: { info: (msg: string) => void; warn: (payload: unknown, msg: string) => void }
): Promise<RedisClientWithLifecycle | undefined> {
  if (!url) return undefined;

  try {
    const redisModule = (await import('ioredis')) as {
      default: new (connectionString: string, opts?: unknown) => RedisClientWithLifecycle;
    };

    const client = new redisModule.default(url, { lazyConnect: true });

    client.on?.('error', (error) => {
      logger.warn({ error }, 'Vision rate limit Redis connection error');
    });

    await client.connect?.();
    logger.info('Vision rate limit Redis connection established');
    return client;
  } catch (error) {
    logger.warn({ error }, 'Falling back to in-memory rate limiting for vision');
    return undefined;
  }
}

async function readFileWithSizeValidation(
  file: { file: AsyncIterable<Buffer> },
  maxBytes: number
): Promise<Buffer> {
  const chunks: Buffer[] = [];
  let totalBytes = 0;

  try {
    for await (const chunk of file.file) {
      totalBytes += chunk.length;
      if (totalBytes > maxBytes) {
        chunks.length = 0;
        throw new Error(
          `File size exceeds maximum allowed size of ${maxBytes} bytes`
        );
      }
      chunks.push(chunk);
    }
    return Buffer.concat(chunks);
  } catch (error) {
    chunks.length = 0;
    throw error;
  }
}
