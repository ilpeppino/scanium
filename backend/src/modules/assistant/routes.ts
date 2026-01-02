import { FastifyPluginAsync, FastifyRequest } from 'fastify';
import { randomUUID } from 'node:crypto';
import { z } from 'zod';
import { Config } from '../../config/index.js';
import { ApiKeyManager } from '../classifier/api-key-manager.js';
import { AssistantService } from './service.js';
import { MockAssistantProvider, GroundedMockAssistantProvider } from './provider.js';
import {
  RedisClient,
  SlidingWindowRateLimiter,
} from '../../infra/rate-limit/sliding-window-limiter.js';
import { sha256Hex } from '../../infra/observability/hash.js';
import { CircuitBreaker } from '../../infra/resilience/circuit-breaker.js';
import { usageStore } from '../usage/usage-store.js';
import {
  shouldRefuse,
  getRefusalReasonCode,
  refusalResponse,
  validateAndSanitizeRequest,
  redactRequestPii,
  detectInjection,
  type SecurityReasonCode,
} from './safety.js';
import { DailyQuotaStore } from './quota-store.js';
import { assistantReadinessRegistry } from './readiness-registry.js';
import {
  ItemImageMetadata,
  AssistantChatRequestWithVision,
  AssistantResponse,
  AssistantErrorPayload,
  AssistantReasonCode,
} from './types.js';
import {
  VisionExtractor,
  MockVisionExtractor,
  computeImageHash,
  VisualFactsCache,
  buildCacheKey,
  VisionImageInput,
  VisualFacts,
} from '../vision/index.js';
import {
  UnifiedCache,
  buildAssistantCacheKey,
  buildItemSnapshotHash,
  normalizeQuestion,
} from '../../infra/cache/unified-cache.js';
import {
  StagedRequestManager,
  buildVisionPreview,
  StagedRequestStatus,
} from './staged-request.js';

type RouteOpts = { config: Config };

const itemAttributeSchema = z.object({
  key: z.string(),
  value: z.string(),
  confidence: z.number().optional().nullable().transform((v) => v ?? undefined),
});

const itemContextSchema = z.object({
  itemId: z.string(),
  title: z.string().optional().nullable().transform((v) => v ?? undefined),
  description: z.string().optional().nullable().transform((v) => v ?? undefined),
  category: z.string().optional().nullable().transform((v) => v ?? undefined),
  confidence: z.number().optional().nullable().transform((v) => v ?? undefined),
  attributes: z.array(itemAttributeSchema).optional(),
  priceEstimate: z.number().optional().nullable().transform((v) => v ?? undefined),
  photosCount: z.number().int().optional(),
  exportProfileId: z.string().optional(),
});

const messageSchema = z.object({
  role: z.enum(['USER', 'ASSISTANT', 'SYSTEM']),
  content: z.string(),
  timestamp: z.number(),
  itemContextIds: z.array(z.string()).optional(),
});

const assistantPrefsSchema = z.object({
  language: z.string().optional(),
  tone: z.enum(['NEUTRAL', 'FRIENDLY', 'PROFESSIONAL']).optional(),
  region: z.enum(['NL', 'DE', 'BE', 'FR', 'UK', 'US', 'EU']).optional(),
  units: z.enum(['METRIC', 'IMPERIAL']).optional(),
  verbosity: z.enum(['CONCISE', 'NORMAL', 'DETAILED']).optional(),
}).optional();

const requestSchema = z.object({
  items: z.array(itemContextSchema),
  history: z.array(messageSchema).optional(),
  message: z.string().min(1),
  exportProfile: z
    .object({
      id: z.string(),
      displayName: z.string(),
    })
    .optional(),
  assistantPrefs: assistantPrefsSchema,
});

// Image upload limits
const IMAGE_LIMITS = {
  MAX_IMAGES_PER_ITEM: 3,
  MAX_TOTAL_IMAGES: 10,
  MAX_FILE_SIZE_BYTES: 2 * 1024 * 1024, // 2MB
  ALLOWED_MIME_TYPES: ['image/jpeg', 'image/png'] as const,
};

type AllowedMimeType = (typeof IMAGE_LIMITS.ALLOWED_MIME_TYPES)[number];

/**
 * Field naming scheme for multipart image uploads:
 * - `itemImages[<itemId>]` - maps image to specific item
 * - Example: `itemImages[abc123]` uploads image for item "abc123"
 */
const ITEM_IMAGE_FIELD_PATTERN = /^itemImages\[(.+)\]$/;

/**
 * Build safety response object for API responses.
 * Uses stable reason codes that don't reveal internal details.
 */
function buildSafetyResponse(
  blocked: boolean,
  reasonCode: SecurityReasonCode | null,
  requestId: string
): { blocked: boolean; reasonCode: string | null; requestId: string } {
  return {
    blocked,
    reasonCode: reasonCode ?? null,
    requestId,
  };
}

export const assistantRoutes: FastifyPluginAsync<RouteOpts> = async (fastify, opts) => {
  const { config } = opts;
  const apiKeyManager = new ApiKeyManager(config.assistant.apiKeys);

  // Use GroundedMockAssistantProvider for vision-enabled responses
  const provider = new GroundedMockAssistantProvider();
  const breaker = new CircuitBreaker({
    failureThreshold: config.assistant.circuitBreakerFailureThreshold,
    cooldownMs: config.assistant.circuitBreakerCooldownSeconds * 1000,
    minimumRequests: config.assistant.circuitBreakerMinimumRequests,
  });
  const service = new AssistantService(provider, {
    breaker,
    retries: 2,
    providerType: config.assistant.provider,
  });

  // Register the service with the readiness registry for health checks
  assistantReadinessRegistry.register(() => service.getReadiness());

  // Vision extraction setup
  const visionConfig = config.vision;
  const visionExtractor =
    visionConfig.provider === 'google'
      ? new VisionExtractor({
          timeoutMs: visionConfig.timeoutMs,
          maxRetries: visionConfig.maxRetries,
          enableLogoDetection: visionConfig.enableLogos,
          maxOcrSnippetLength: visionConfig.maxOcrSnippetLength,
          minOcrConfidence: visionConfig.minOcrConfidence,
          minLabelConfidence: visionConfig.minLabelConfidence,
          minLogoConfidence: visionConfig.minLogoConfidence,
        })
      : new MockVisionExtractor();

  const visionCache = new VisualFactsCache({
    ttlMs: visionConfig.cacheTtlSeconds * 1000,
    maxEntries: visionConfig.cacheMaxEntries,
  });

  // Response cache for assistant answers
  const responseCache = new UnifiedCache<AssistantResponse>({
    ttlMs: config.assistant.responseCacheTtlSeconds * 1000,
    maxEntries: config.assistant.responseCacheMaxEntries,
    name: 'assistant-response',
    enableDedup: config.assistant.enableRequestDedup,
  });

  // Usage accounting callback
  responseCache.setUsageCallback((event) => {
    fastify.log.debug(
      {
        cacheEvent: event.type,
        cacheName: event.cacheName,
        cacheKey: event.cacheKey.slice(0, 16),
        ...event.metadata,
      },
      'Cache event'
    );
  });

  // Staged request manager for async processing
  const stagedRequestManager = new StagedRequestManager({
    timeoutMs: config.assistant.stagedResponseTimeoutMs,
    maxConcurrent: 100,
    cleanupIntervalMs: 30000,
    resultRetentionMs: 300000,
  });

  // Daily quota store (in-memory, resets at midnight UTC)
  const quotaStore = new DailyQuotaStore({
    dailyLimit: config.assistant.dailyQuota,
  });

  const redisClient = await createRedisClient(
    config.assistant.rateLimitRedisUrl,
    fastify.log
  );
  const windowMs = config.assistant.rateLimitWindowSeconds * 1000;
  const baseBackoffMs = config.assistant.rateLimitBackoffSeconds * 1000;
  const maxBackoffMs = config.assistant.rateLimitBackoffMaxSeconds * 1000;

  const ipRateLimiter = new SlidingWindowRateLimiter({
    windowMs,
    max: config.assistant.ipRateLimitPerMinute,
    baseBackoffMs,
    maxBackoffMs,
    prefix: 'rl:assist:ip',
    redis: redisClient,
  });

  const apiKeyRateLimiter = new SlidingWindowRateLimiter({
    windowMs,
    max: config.assistant.rateLimitPerMinute,
    baseBackoffMs,
    maxBackoffMs,
    prefix: 'rl:assist:api',
    redis: redisClient,
  });

  const deviceRateLimiter = new SlidingWindowRateLimiter({
    windowMs,
    max: config.assistant.deviceRateLimitPerMinute,
    baseBackoffMs,
    maxBackoffMs,
    prefix: 'rl:assist:device',
    redis: redisClient,
  });

  // Validation limits from config
  const validationLimits = {
    maxInputChars: config.assistant.maxInputChars,
    maxContextItems: config.assistant.maxContextItems,
    maxAttributesPerItem: config.assistant.maxAttributesPerItem,
  };

  fastify.addHook('onClose', async () => {
    assistantReadinessRegistry.unregister();
    quotaStore.stop();
    visionCache.stop();
    responseCache.stop();
    stagedRequestManager.stop();
    await redisClient?.quit?.();
  });

  fastify.post('/assist/chat', async (request, reply) => {
    const requestId = randomUUID();
    const correlationId = request.correlationId ?? requestId;

    // Check if assistant is disabled
    if (config.assistant.provider === 'disabled') {
      return reply.status(503).send({
        error: {
          code: 'ASSISTANT_DISABLED',
          message: 'Assistant is disabled',
          correlationId,
        },
        assistantError: buildAssistantError('provider_unavailable', 'policy', false, 'PROVIDER_NOT_CONFIGURED', undefined, 'Assistant disabled'),
        safety: buildSafetyResponse(false, 'PROVIDER_NOT_CONFIGURED', requestId),
      });
    }

    // API key authentication
    const apiKey = (request.headers['x-api-key'] as string | undefined)?.trim();
    if (!apiKey || !apiKeyManager.validateKey(apiKey)) {
      return reply.status(401).send({
        error: {
          code: 'UNAUTHORIZED',
          message: 'Missing or invalid API key',
          correlationId,
        },
        assistantError: buildAssistantError('unauthorized', 'auth', false, 'UNAUTHORIZED', undefined, 'Missing or invalid API key'),
        safety: buildSafetyResponse(true, null, requestId),
      });
    }

    // IP rate limiting
    const ipLimit = await ipRateLimiter.consume(request.ip);
    if (!ipLimit.allowed) {
      request.log.warn({ ip: request.ip, correlationId }, 'IP rate limit exceeded');
      return reply
        .status(429)
        .header('Retry-After', String(ipLimit.retryAfterSeconds))
        .send({
          error: {
            code: 'RATE_LIMITED',
            message: 'Please wait before sending another message',
            correlationId,
          },
          assistantError: buildAssistantError(
            'rate_limited',
            'policy',
            true,
            'RATE_LIMITED',
            ipLimit.retryAfterSeconds,
            'Rate limit reached'
          ),
          safety: buildSafetyResponse(true, 'RATE_LIMITED', requestId),
        });
    }

    // API key rate limiting
    const apiKeyLimit = await apiKeyRateLimiter.consume(apiKey);
    if (!apiKeyLimit.allowed) {
      request.log.warn({ correlationId }, 'API key rate limit exceeded');
      return reply
        .status(429)
        .header('Retry-After', String(apiKeyLimit.retryAfterSeconds))
        .send({
          error: {
            code: 'RATE_LIMITED',
            message: 'Please wait before sending another message',
            correlationId,
          },
          assistantError: buildAssistantError(
            'rate_limited',
            'policy',
            true,
            'RATE_LIMITED',
            apiKeyLimit.retryAfterSeconds,
            'Rate limit reached'
          ),
          safety: buildSafetyResponse(true, 'RATE_LIMITED', requestId),
        });
    }

    // Device rate limiting
    const deviceId = extractDeviceId(request);
    if (deviceId) {
      const deviceLimit = await deviceRateLimiter.consume(deviceId);
      if (!deviceLimit.allowed) {
        request.log.warn({ deviceIdHash: deviceId.slice(0, 8), correlationId }, 'Device rate limit exceeded');
        return reply
          .status(429)
          .header('Retry-After', String(deviceLimit.retryAfterSeconds))
          .send({
            error: {
              code: 'RATE_LIMITED',
              message: 'Please wait before sending another message',
              correlationId,
            },
            assistantError: buildAssistantError(
              'rate_limited',
              'policy',
              true,
              'RATE_LIMITED',
              deviceLimit.retryAfterSeconds,
              'Rate limit reached'
            ),
            safety: buildSafetyResponse(true, 'RATE_LIMITED', requestId),
          });
      }
    }

    // Daily quota enforcement (use device ID if available, otherwise API key hash)
    const quotaKey = deviceId ?? sha256Hex(apiKey).slice(0, 16);
    const quotaResult = quotaStore.consume(quotaKey);
    if (!quotaResult.allowed) {
      const resetIn = Math.ceil((quotaResult.resetAt - Date.now()) / 1000);
      request.log.warn(
        { quotaKey: quotaKey.slice(0, 8), remaining: 0, correlationId },
        'Daily quota exceeded'
      );
      return reply
        .status(429)
        .header('Retry-After', String(resetIn))
        .send({
          error: {
            code: 'QUOTA_EXCEEDED',
            message: 'Daily message limit reached. Try again tomorrow.',
            correlationId,
          },
          assistantError: buildAssistantError(
            'rate_limited',
            'policy',
            true,
            'QUOTA_EXCEEDED',
            resetIn,
            'Daily quota exceeded'
          ),
          safety: buildSafetyResponse(true, 'QUOTA_EXCEEDED', requestId),
        });
    }

    // Determine content type and parse request body
    const contentType = request.headers['content-type'] ?? '';
    const isMultipart = contentType.includes('multipart/form-data');

    let requestBody: unknown;
    let attachedImages = new Map<string, ItemImageMetadata[]>();

    if (isMultipart) {
      const multipartResult = await parseMultipartRequest(request);
      if (!multipartResult.success) {
        request.log.warn({ correlationId, error: multipartResult.error }, 'Multipart parsing failed');
        return reply.status(400).send({
          error: {
            code: 'VALIDATION_ERROR',
            message: multipartResult.error,
            correlationId,
          },
          assistantError: buildAssistantError(
            'validation_error',
            'policy',
            false,
            'VALIDATION_ERROR',
            undefined,
            multipartResult.error
          ),
          safety: buildSafetyResponse(true, 'VALIDATION_ERROR', requestId),
        });
      }
      requestBody = multipartResult.jsonData;
      attachedImages = multipartResult.images;
    } else {
      // JSON body (backward compatible)
      requestBody = request.body;
    }

    // Schema validation
    const parsed = requestSchema.safeParse(requestBody);
    if (!parsed.success) {
      request.log.warn({ correlationId }, 'Request validation failed');
      return reply.status(400).send({
        error: {
          code: 'VALIDATION_ERROR',
          message: 'Message could not be processed',
          correlationId,
        },
        assistantError: buildAssistantError(
          'validation_error',
          'policy',
          false,
          'VALIDATION_ERROR',
          undefined,
          'Validation failed'
        ),
        safety: buildSafetyResponse(true, 'VALIDATION_ERROR', requestId),
      });
    }

    // Merge images into items if multipart request
    if (isMultipart && attachedImages.size > 0) {
      parsed.data.items = mergeImagesIntoItems(parsed.data.items, attachedImages);

      // Log image attachment metadata
      const imageCount = Array.from(attachedImages.values()).reduce((sum, arr) => sum + arr.length, 0);
      request.log.info(
        { correlationId, imageCount, itemsWithImages: attachedImages.size },
        'Images attached to assistant request'
      );
    }

    // Handle empty items when feature flag is enabled
    if (parsed.data.items.length === 0) {
      if (config.assistant.allowEmptyItems) {
        // Return helpful response for empty items when flag is ON
        request.log.info({ correlationId }, 'Empty items allowed by feature flag');
        return reply.status(200).send({
          reply: 'Assistant is enabled. Add an item to get listing advice.',
          actions: [],
          citationsMetadata: {},
          safety: buildSafetyResponse(false, null, requestId),
          correlationId,
        });
      }
      // Default behavior: proceed to provider which will return the standard message
    }

    // Apply security limits and normalize input
    const validated = validateAndSanitizeRequest(parsed.data, validationLimits);
    if (!validated.valid) {
      request.log.warn({ correlationId, error: validated.error }, 'Input validation failed');
      return reply.status(400).send({
        error: {
          code: 'VALIDATION_ERROR',
          message: 'Message could not be processed',
          correlationId,
        },
        assistantError: buildAssistantError(
          'validation_error',
          'policy',
          false,
          'VALIDATION_ERROR',
          undefined,
          validated.error
        ),
        safety: buildSafetyResponse(true, 'VALIDATION_ERROR', requestId),
      });
    }

    // Prompt injection detection
    const injection = detectInjection(validated.request.message);
    if (shouldRefuse(validated.request.message)) {
      const reasonCode = getRefusalReasonCode(validated.request.message);
      request.log.warn(
        {
          correlationId,
          reasonCode,
          injectionCategory: injection.category,
        },
        'Policy violation detected'
      );

      // Return safe refusal - don't reveal what triggered it
      const refusal = refusalResponse();
      return reply.status(200).send({
        reply: refusal.content,
        actions: refusal.actions,
        safety: buildSafetyResponse(true, reasonCode, requestId),
        correlationId,
      });
    }

    // PII redaction before processing
    const { request: sanitizedRequest, piiRedacted } = redactRequestPii(validated.request);

    // Vision extraction for items with images
    const visualFactsMap = new Map<string, VisualFacts>();
    let visionCacheHits = 0;
    let visionExtractions = 0;
    let visionErrors = 0;

    if (visionConfig.enabled) {
      for (const item of sanitizedRequest.items) {
        if (!item.itemImages || item.itemImages.length === 0) continue;

        // Compute image hashes for cache key
        const imageHashes = item.itemImages.map((img) => computeImageHash(img.base64Data));
        const cacheKey = buildCacheKey(imageHashes, { featureVersion: 'v1' });

        // Check cache first
        const cachedFacts = visionCache.get(cacheKey);
        if (cachedFacts) {
          visualFactsMap.set(item.itemId, cachedFacts);
          visionCacheHits++;
          request.log.debug(
            { correlationId, itemId: item.itemId, cacheKey: cacheKey.slice(0, 16) },
            'Vision cache hit'
          );
          continue;
        }

        // Extract visual facts
        try {
          const images: VisionImageInput[] = item.itemImages.map((img) => ({
            base64Data: img.base64Data,
            mimeType: img.mimeType,
            filename: img.filename,
          }));

          const result = await visionExtractor.extractVisualFacts(item.itemId, images, {
            enableOcr: visionConfig.enableOcr,
            enableLabels: visionConfig.enableLabels,
            enableLogos: visionConfig.enableLogos,
            enableColors: visionConfig.enableColors,
            maxOcrSnippets: visionConfig.maxOcrSnippets,
            maxLabelHints: visionConfig.maxLabelHints,
            maxLogoHints: visionConfig.maxLogoHints,
            maxColors: visionConfig.maxColors,
          });

          if (result.success && result.facts) {
            visualFactsMap.set(item.itemId, result.facts);
            visionCache.set(cacheKey, result.facts);
            visionExtractions++;
            request.log.info(
              {
                correlationId,
                itemId: item.itemId,
                imageCount: images.length,
                timingsMs: result.facts.extractionMeta.timingsMs,
                colorsFound: result.facts.dominantColors.length,
                ocrSnippets: result.facts.ocrSnippets.length,
                labelsFound: result.facts.labelHints.length,
                logosFound: result.facts.logoHints?.length ?? 0,
              },
              'Vision extraction completed'
            );
          } else {
            visionErrors++;
            request.log.warn(
              { correlationId, itemId: item.itemId, error: result.error, errorCode: result.errorCode },
              'Vision extraction failed'
            );
          }
        } catch (error) {
          visionErrors++;
          request.log.error(
            { correlationId, itemId: item.itemId, error },
            'Vision extraction error'
          );
        }
      }
    }

    // Log request metadata (NOT content by default)
    const logData: Record<string, unknown> = {
      correlationId,
      requestId,
      itemCount: sanitizedRequest.items.length,
      messageLength: sanitizedRequest.message.length,
      historyLength: sanitizedRequest.history?.length ?? 0,
      piiRedacted,
      quotaRemaining: quotaResult.remaining,
      visionCacheHits,
      visionExtractions,
      visionErrors,
    };

    // Only log content if explicitly enabled (for debugging only)
    if (config.assistant.logContent) {
      logData.message = sanitizedRequest.message.slice(0, 100) + '...';
    }

    request.log.info(logData, 'Assistant request');

    // Build request with visual facts
    const requestWithVision: AssistantChatRequestWithVision = {
      ...sanitizedRequest,
      visualFacts: visualFactsMap.size > 0 ? visualFactsMap : undefined,
    };

    // Build response cache key
    const primaryItem = sanitizedRequest.items[0];
    const itemSnapshotHash = primaryItem
      ? buildItemSnapshotHash({
          itemId: primaryItem.itemId,
          title: primaryItem.title,
          category: primaryItem.category,
          attributes: primaryItem.attributes,
        })
      : '';
    const imageHashes = visualFactsMap.size > 0
      ? [...visualFactsMap.values()].flatMap((f) => f.extractionMeta.imageHashes)
      : [];
    const responseCacheKey = buildAssistantCacheKey({
      promptVersion: 'v1',
      question: sanitizedRequest.message,
      itemSnapshotHash,
      imageHashes,
      providerId: config.assistant.provider,
    });

    // Check response cache (with deduplication)
    try {
      const response = await responseCache.getOrCompute(responseCacheKey, async () => {
        return await service.respond(requestWithVision);
      });

      usageStore.recordAssistant(apiKey, false);

      // Check if this was a cache hit
      const cacheStats = responseCache.getStats();
      const fromCache = cacheStats.hits > 0;

      request.log.info(
        { correlationId, fromCache, cacheKey: responseCacheKey.slice(0, 16) },
        'Assistant response'
      );

      return reply.status(200).send({
        reply: response.content,
        actions: response.actions,
        citationsMetadata: {
          ...response.citationsMetadata,
          ...(fromCache ? { fromCache: 'true' } : {}),
        },
        assistantError: buildAssistantErrorIfNeeded(
          response.assistantError,
          visionConfig.enabled,
          sanitizedRequest.items,
          visionErrors,
          visualFactsMap.size
        ),
        confidenceTier: response.confidenceTier,
        evidence: response.evidence,
        suggestedAttributes: response.suggestedAttributes,
        suggestedDraftUpdates: response.suggestedDraftUpdates,
        suggestedNextPhoto: response.suggestedNextPhoto,
        safety: buildSafetyResponse(false, null, requestId),
        correlationId,
      });
    } catch (error) {
      const errorDetails = {
        message: error instanceof Error ? error.message : String(error),
        stack: error instanceof Error ? error.stack : undefined,
        name: error instanceof Error ? error.name : typeof error,
      };
      request.log.error({ correlationId, error: errorDetails }, 'Assistant service error');
      usageStore.recordAssistant(apiKey, true);

      const isTimeout =
        error instanceof Error &&
        (error.name === 'AbortError' || error.message.toLowerCase().includes('timeout'));
      const fallbackError = isTimeout
        ? buildAssistantError('network_timeout', 'temporary', true, 'PROVIDER_UNAVAILABLE', undefined, 'Assistant request timed out')
        : buildAssistantError('provider_unavailable', 'temporary', true, 'PROVIDER_UNAVAILABLE', undefined, 'Assistant provider unavailable');

      // Return graceful fallback instead of 503
      return reply.status(200).send({
        reply: 'I\'m having trouble processing your request right now. I can still help with general listing guidance. Try asking about improving your title, description, or what details to add.',
        actions: [],
        confidenceTier: 'LOW',
        evidence: [],
        citationsMetadata: { providerUnavailable: 'true' },
        assistantError: fallbackError,
        safety: buildSafetyResponse(false, 'PROVIDER_UNAVAILABLE', requestId),
        correlationId,
      });
    }
  });

  // Staged request status endpoint for polling
  fastify.get('/assist/chat/status/:requestId', async (request, reply) => {
    const { requestId } = request.params as { requestId: string };

    const status = stagedRequestManager.getStatus(requestId);
    if (!status) {
      return reply.status(404).send({
        error: {
          code: 'NOT_FOUND',
          message: 'Request not found or expired',
        },
      });
    }

    return reply.status(200).send({
      requestId: status.requestId,
      stage: status.stage,
      visionPreview: status.visionPreview,
      response: status.response,
      error: status.error,
      correlationId: status.correlationId,
    });
  });

  // Cache stats endpoint for monitoring
  fastify.get('/assist/cache/stats', async (request, reply) => {
    const apiKey = (request.headers['x-api-key'] as string | undefined)?.trim();
    if (!apiKey || !apiKeyManager.validateKey(apiKey)) {
      return reply.status(401).send({
        error: {
          code: 'UNAUTHORIZED',
          message: 'Missing or invalid API key',
          correlationId: request.correlationId,
        },
        assistantError: buildAssistantError('unauthorized', 'auth', false, 'UNAUTHORIZED', undefined, 'Missing or invalid API key'),
      });
    }

    const visionStats = visionCache.stats();
    const responseStats = responseCache.getStats();
    const stagedStats = stagedRequestManager.getStats();

    return reply.status(200).send({
      vision: {
        size: visionStats.size,
        maxEntries: visionStats.maxEntries,
        ttlMs: visionStats.ttlMs,
      },
      response: {
        size: responseStats.size,
        hits: responseStats.hits,
        misses: responseStats.misses,
        coalescedRequests: responseStats.coalescedRequests,
        evictions: responseStats.evictions,
      },
      staged: stagedStats,
    });
  });
};

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
      logger.warn({ error }, 'Rate limit Redis connection error');
    });

    await client.connect?.();
    logger.info('Rate limit Redis connection established');
    return client;
  } catch (error) {
    logger.warn({ error }, 'Falling back to in-memory rate limiting');
    return undefined;
  }
}

function extractDeviceId(request: { headers: Record<string, unknown> }): string | null {
  const header = request.headers['x-scanium-device-id'];
  if (!header) return null;
  const raw = Array.isArray(header) ? header[0] : header;
  if (typeof raw !== 'string') return null;
  const trimmed = raw.trim();
  return trimmed ? sha256Hex(trimmed) : null;
}

type MultipartParseResult =
  | { success: true; jsonData: unknown; images: Map<string, ItemImageMetadata[]> }
  | { success: false; error: string };

/**
 * Parse multipart form data and extract JSON payload + images.
 * Field naming scheme: `itemImages[<itemId>]` maps images to specific items.
 */
async function parseMultipartRequest(request: FastifyRequest): Promise<MultipartParseResult> {
  const images = new Map<string, ItemImageMetadata[]>();
  let jsonData: unknown = null;
  let totalImageCount = 0;
  const perItemCount = new Map<string, number>();

  try {
    const parts = request.parts();

    for await (const part of parts) {
      if (part.type === 'field') {
        // Handle JSON payload field
        if (part.fieldname === 'payload') {
          try {
            jsonData = JSON.parse(part.value as string);
          } catch {
            return { success: false, error: 'Invalid JSON in payload field' };
          }
        }
      } else if (part.type === 'file') {
        // Handle image file
        const match = ITEM_IMAGE_FIELD_PATTERN.exec(part.fieldname);
        if (!match) {
          // Consume the stream to prevent hanging
          await part.toBuffer();
          continue;
        }

        const itemId = match[1];

        // Validate MIME type
        if (!IMAGE_LIMITS.ALLOWED_MIME_TYPES.includes(part.mimetype as AllowedMimeType)) {
          await part.toBuffer();
          return { success: false, error: `Unsupported image type: ${part.mimetype}` };
        }

        // Check total image limit
        if (totalImageCount >= IMAGE_LIMITS.MAX_TOTAL_IMAGES) {
          await part.toBuffer();
          return { success: false, error: `Maximum ${IMAGE_LIMITS.MAX_TOTAL_IMAGES} images allowed total` };
        }

        // Check per-item limit
        const currentItemCount = perItemCount.get(itemId) ?? 0;
        if (currentItemCount >= IMAGE_LIMITS.MAX_IMAGES_PER_ITEM) {
          await part.toBuffer();
          return { success: false, error: `Maximum ${IMAGE_LIMITS.MAX_IMAGES_PER_ITEM} images allowed per item` };
        }

        // Read file data
        const buffer = await part.toBuffer();

        // Double-check size (fastify/multipart should enforce, but be safe)
        if (buffer.length > IMAGE_LIMITS.MAX_FILE_SIZE_BYTES) {
          return { success: false, error: `Image exceeds ${IMAGE_LIMITS.MAX_FILE_SIZE_BYTES / 1024 / 1024}MB limit` };
        }

        const imageMetadata: ItemImageMetadata = {
          filename: part.filename,
          mimeType: part.mimetype as AllowedMimeType,
          sizeBytes: buffer.length,
          base64Data: buffer.toString('base64'),
        };

        // Add to images map
        const existingImages = images.get(itemId) ?? [];
        existingImages.push(imageMetadata);
        images.set(itemId, existingImages);

        totalImageCount++;
        perItemCount.set(itemId, currentItemCount + 1);
      }
    }

    if (jsonData === null) {
      return { success: false, error: 'Missing payload field in multipart request' };
    }

    return { success: true, jsonData, images };
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Unknown error';
    return { success: false, error: `Multipart parsing failed: ${message}` };
  }
}

/**
 * Merge images into item contexts by itemId.
 * Only attaches images to items present in the request; orphan images are dropped.
 */
function mergeImagesIntoItems<T extends { itemId: string }>(
  items: T[],
  images: Map<string, ItemImageMetadata[]>
): (T & { itemImages?: ItemImageMetadata[] })[] {
  return items.map((item) => {
    const itemImages = images.get(item.itemId);
    if (itemImages && itemImages.length > 0) {
      return { ...item, itemImages };
    }
    return item;
  });
}

function buildAssistantError(
  type: AssistantErrorPayload['type'],
  category: AssistantErrorPayload['category'],
  retryable: boolean,
  reasonCode: AssistantReasonCode,
  retryAfterSeconds?: number,
  message?: string
): AssistantErrorPayload {
  return {
    type,
    category,
    retryable,
    retryAfterSeconds,
    message,
    reasonCode,
  };
}

function buildAssistantErrorIfNeeded(
  responseError: AssistantErrorPayload | undefined,
  visionEnabled: boolean,
  items: Array<{ itemImages?: ItemImageMetadata[] }>,
  visionErrors: number,
  visualFactsSize: number
): AssistantErrorPayload | undefined {
  if (responseError) return responseError;
  const hasImages = items.some((item) => (item.itemImages?.length ?? 0) > 0);
  if (visionEnabled && hasImages && visionErrors > 0 && visualFactsSize === 0) {
    return buildAssistantError(
      'vision_unavailable',
      'temporary',
      true,
      'VISION_UNAVAILABLE',
      undefined,
      'Vision extraction unavailable'
    );
  }
  return undefined;
}
