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

type RouteOpts = { config: Config };

const itemAttributeSchema = z.object({
  key: z.string(),
  value: z.string(),
  confidence: z.number().optional().nullable(),
});

const itemContextSchema = z.object({
  itemId: z.string(),
  title: z.string().optional().nullable(),
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
    max: config.assistant.rateLimitPerMinute,
    baseBackoffMs,
    maxBackoffMs,
    prefix: 'rl:assist:device',
    redis: redisClient,
  });

  fastify.addHook('onClose', async () => {
    await redisClient?.quit?.();
  });

  fastify.post('/assist/chat', async (request, reply) => {
    const correlationId = request.correlationId ?? randomUUID();
    if (config.assistant.provider === 'disabled') {
      return reply.status(503).send({
        error: {
          code: 'ASSISTANT_DISABLED',
          message: 'Assistant is disabled',
          correlationId,
        },
      });
    }

    const apiKey = (request.headers['x-api-key'] as string | undefined)?.trim();
    if (!apiKey || !apiKeyManager.validateKey(apiKey)) {
      return reply.status(401).send({
        error: {
          code: 'UNAUTHORIZED',
          message: 'Missing or invalid API key',
          correlationId,
        },
      });
    }

    const ipLimit = await ipRateLimiter.consume(request.ip);
    if (!ipLimit.allowed) {
      return reply
        .status(429)
        .header('Retry-After', String(ipLimit.retryAfterSeconds))
        .send({
          error: {
            code: 'RATE_LIMIT',
            message: 'Too many requests from this IP. Please retry after the cooldown.',
            correlationId,
          },
        });
    }

    const apiKeyLimit = await apiKeyRateLimiter.consume(apiKey);
    if (!apiKeyLimit.allowed) {
      return reply
        .status(429)
        .header('Retry-After', String(apiKeyLimit.retryAfterSeconds))
        .send({
          error: {
            code: 'RATE_LIMIT',
            message: 'Too many requests for this API key. Please retry after the cooldown.',
            correlationId,
          },
        });
    }

    const deviceId = extractDeviceId(request);
    if (deviceId) {
      const deviceLimit = await deviceRateLimiter.consume(deviceId);
      if (!deviceLimit.allowed) {
        return reply
          .status(429)
          .header('Retry-After', String(deviceLimit.retryAfterSeconds))
          .send({
            error: {
              code: 'RATE_LIMIT',
              message: 'Too many requests for this device. Please retry after the cooldown.',
              correlationId,
            },
          });
      }
    }

    const parsed = requestSchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.status(400).send({
        error: {
          code: 'VALIDATION_ERROR',
          message: 'Invalid request payload',
          correlationId,
        },
      });
    }

    request.log.info(
      {
        correlationId,
        itemCount: parsed.data.items.length,
        messageLength: parsed.data.message.length,
      },
      'Assistant request'
    );
    const response = await service.respond(parsed.data);
    usageStore.recordAssistant(apiKey, false);
    return reply.status(200).send({ ...response, correlationId });
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
