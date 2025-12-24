import { FastifyPluginAsync } from 'fastify';
import { randomUUID } from 'node:crypto';
import { z } from 'zod';
import { Config } from '../../config/index.js';
import { ApiKeyManager } from '../classifier/api-key-manager.js';
import { AssistantService } from './service.js';
import { MockAssistantProvider } from './provider.js';
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

type RouteOpts = { config: Config };

const itemAttributeSchema = z.object({
  key: z.string(),
  value: z.string(),
  confidence: z.number().optional().nullable(),
});

const itemContextSchema = z.object({
  itemId: z.string(),
  title: z.string().optional().nullable(),
  description: z.string().optional().nullable(),
  category: z.string().optional().nullable(),
  confidence: z.number().optional().nullable(),
  attributes: z.array(itemAttributeSchema).optional(),
  priceEstimate: z.number().optional().nullable(),
  photosCount: z.number().int().optional(),
  exportProfileId: z.string().optional(),
});

const messageSchema = z.object({
  role: z.enum(['USER', 'ASSISTANT', 'SYSTEM']),
  content: z.string(),
  timestamp: z.number(),
  itemContextIds: z.array(z.string()).optional(),
});

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
});

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
  const provider = new MockAssistantProvider();
  const breaker = new CircuitBreaker({
    failureThreshold: config.assistant.circuitBreakerFailureThreshold,
    cooldownMs: config.assistant.circuitBreakerCooldownSeconds * 1000,
    minimumRequests: config.assistant.circuitBreakerMinimumRequests,
  });
  const service = new AssistantService(provider, { breaker, retries: 2 });

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
    quotaStore.stop();
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
          safety: buildSafetyResponse(true, 'QUOTA_EXCEEDED', requestId),
        });
    }

    // Schema validation
    const parsed = requestSchema.safeParse(request.body);
    if (!parsed.success) {
      request.log.warn({ correlationId }, 'Request validation failed');
      return reply.status(400).send({
        error: {
          code: 'VALIDATION_ERROR',
          message: 'Message could not be processed',
          correlationId,
        },
        safety: buildSafetyResponse(true, 'VALIDATION_ERROR', requestId),
      });
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

    // Log request metadata (NOT content by default)
    const logData: Record<string, unknown> = {
      correlationId,
      requestId,
      itemCount: sanitizedRequest.items.length,
      messageLength: sanitizedRequest.message.length,
      historyLength: sanitizedRequest.history?.length ?? 0,
      piiRedacted,
      quotaRemaining: quotaResult.remaining,
    };

    // Only log content if explicitly enabled (for debugging only)
    if (config.assistant.logContent) {
      logData.message = sanitizedRequest.message.slice(0, 100) + '...';
    }

    request.log.info(logData, 'Assistant request');

    // Call assistant service
    try {
      const response = await service.respond(sanitizedRequest);
      usageStore.recordAssistant(apiKey, false);

      return reply.status(200).send({
        reply: response.content,
        actions: response.actions,
        citationsMetadata: response.citationsMetadata,
        safety: buildSafetyResponse(false, null, requestId),
        correlationId,
      });
    } catch (error) {
      request.log.error({ correlationId, error }, 'Assistant service error');
      usageStore.recordAssistant(apiKey, true);

      return reply.status(503).send({
        error: {
          code: 'PROVIDER_UNAVAILABLE',
          message: 'Assistant temporarily unavailable',
          correlationId,
        },
        safety: buildSafetyResponse(true, 'PROVIDER_UNAVAILABLE', requestId),
      });
    }
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
