import { randomUUID } from 'node:crypto';
import { FastifyBaseLogger, FastifyPluginAsync, FastifyRequest } from 'fastify';
import { MultipartFile } from '@fastify/multipart';
import { ClassifierService } from './service.js';
import { sanitizeImageBuffer, isSupportedImage } from './utils/image.js';
import { ClassificationHints, ClassificationRequest } from './types.js';
import { Config } from '../../config/index.js';
import { ApiKeyManager } from './api-key-manager.js';
import {
  RedisClient,
  SlidingWindowRateLimiter,
} from '../../infra/rate-limit/sliding-window-limiter.js';
import { sha256Hex } from '../../infra/observability/hash.js';
import { ClassifierCache } from './cache.js';
import { usageStore } from '../usage/usage-store.js';

type RouteOpts = { config: Config };

const inFlightByKey = new Map<string, number>();

export const classifierRoutes: FastifyPluginAsync<RouteOpts> = async (
  fastify,
  opts
) => {
  const { config } = opts;
  const apiKeyManager = new ApiKeyManager(config.classifier.apiKeys);
  const service = new ClassifierService({
    config,
    logger: fastify.log,
  });
  const redisClient = await createRedisClient(
    config.classifier.rateLimitRedisUrl,
    fastify.log
  );
  const cache = new ClassifierCache(
    config.classifier.cacheTtlSeconds * 1000,
    config.classifier.cacheMaxEntries
  );

  const windowMs = config.classifier.rateLimitWindowSeconds * 1000;
  const baseBackoffMs = config.classifier.rateLimitBackoffSeconds * 1000;
  const maxBackoffMs = config.classifier.rateLimitBackoffMaxSeconds * 1000;

  const ipRateLimiter = new SlidingWindowRateLimiter({
    windowMs,
    max: config.classifier.ipRateLimitPerMinute,
    baseBackoffMs,
    maxBackoffMs,
    prefix: 'rl:ip',
    redis: redisClient,
  });

  const apiKeyRateLimiter = new SlidingWindowRateLimiter({
    windowMs,
    max: config.classifier.rateLimitPerMinute,
    baseBackoffMs,
    maxBackoffMs,
    prefix: 'rl:api',
    redis: redisClient,
  });

  const deviceRateLimiter = new SlidingWindowRateLimiter({
    windowMs,
    max: config.classifier.rateLimitPerMinute,
    baseBackoffMs,
    maxBackoffMs,
    prefix: 'rl:device',
    redis: redisClient,
  });

  fastify.addHook('onClose', async () => {
    await redisClient?.quit?.();
  });

  fastify.post('/classify', async (request, reply) => {
    const apiKey = extractApiKey(request);
    const correlationId = request.correlationId ?? randomUUID();

    // Validate API key with rotation and expiration support
    if (!apiKey || !apiKeyManager.validateKey(apiKey)) {
      // Log failed authentication attempt
      if (config.security.logApiKeyUsage && apiKey) {
        apiKeyManager.logUsage({
          apiKey: apiKey.substring(0, 8) + '***', // Partial key for logging
          timestamp: new Date(),
          endpoint: request.url,
          method: request.method,
          success: false,
          ip: request.ip,
          userAgent: request.headers['user-agent'],
          errorCode: 'UNAUTHORIZED',
        });

        request.log.warn(
          {
            apiKeyPrefix: apiKey.substring(0, 8),
            ip: request.ip,
            endpoint: request.url,
          },
          'Invalid or expired API key attempt'
        );
      }

      return reply.status(401).send({
        error: {
          code: 'UNAUTHORIZED',
          message: 'Missing or invalid API key',
          correlationId,
        },
      });
    }

    if (!request.isMultipart()) {
      return reply.status(400).send({
        error: {
          code: 'VALIDATION_ERROR',
          message: 'multipart/form-data required',
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

    if (isOverConcurrentLimit(apiKey, config.classifier.concurrentLimit)) {
      return reply
        .status(429)
        .header('Retry-After', '1')
        .send({
          error: {
            code: 'RATE_LIMIT',
            message: 'Too many concurrent requests',
            correlationId,
          },
        });
    }

    try {
      const payload = await parseMultipartPayload(request);
      if (!payload.file) {
        return reply.status(400).send({
          error: {
            code: 'VALIDATION_ERROR',
            message: 'image file is required',
            correlationId,
          },
        });
      }

      if (!isSupportedImage(payload.file.mimetype)) {
        return reply.status(400).send({
          error: {
            code: 'VALIDATION_ERROR',
            message: 'Unsupported content type',
            correlationId,
          },
        });
      }

      const domainPackId =
        payload.fields.domainPackId?.toString().trim() || config.classifier.domainPackId;
      if (domainPackId !== config.classifier.domainPackId) {
        return reply.status(400).send({
          error: {
            code: 'VALIDATION_ERROR',
            message: 'Unknown domainPackId',
            correlationId,
          },
        });
      }

      let hints: ClassificationHints | undefined;
      if (payload.fields.hints) {
        try {
          const parsed = JSON.parse(payload.fields.hints);
          if (parsed && typeof parsed === 'object') {
            hints = parsed as ClassificationHints;
          } else {
            throw new Error('invalid hints');
          }
        } catch (error) {
          return reply.status(400).send({
            error: {
              code: 'VALIDATION_ERROR',
              message: 'Invalid hints JSON',
              correlationId,
            },
          });
        }
      }

      // SEC-004: Validate file size BEFORE reading into buffer to prevent memory exhaustion
      const buffer = await readFileWithSizeValidation(
        payload.file,
        config.classifier.maxUploadBytes
      );
      const sanitized = await sanitizeImageBuffer(buffer, payload.file.mimetype);
      const imageHash = sha256Hex(sanitized.buffer);
      const cacheKey = `${config.classifier.provider}:${domainPackId}:${imageHash}`;
      const cached = cache.get(cacheKey);
      if (cached) {
        request.log.info(
          {
            requestId: cached.requestId,
            correlationId,
            cacheHit: true,
            provider: cached.provider,
          },
          'Classifier cache hit'
        );
        usageStore.recordClassification(apiKey, config.classifier.visionFeature, false);
        return reply.status(200).send({ ...cached, cacheHit: true, correlationId });
      }

      // Images stay in-memory only. Future retention requires explicit opt-in via config.
      const requestId = randomUUID();
      const classificationRequest: ClassificationRequest = {
        requestId,
        correlationId,
        imageHash,
        buffer: sanitized.buffer,
        contentType: sanitized.normalizedType,
        fileName: payload.file.filename ?? 'upload',
        domainPackId,
        hints,
      };

      const result = await service.classify(classificationRequest);
      cache.set(cacheKey, result);
      usageStore.recordClassification(apiKey, config.classifier.visionFeature, false);

      // Log successful API key usage
      if (config.security.logApiKeyUsage) {
        apiKeyManager.logUsage({
          apiKey: apiKey.substring(0, 8) + '***', // Partial key for logging
          timestamp: new Date(),
          endpoint: request.url,
          method: request.method,
          success: true,
          ip: request.ip,
          userAgent: request.headers['user-agent'],
        });

        request.log.info(
          {
            apiKeyPrefix: apiKey.substring(0, 8),
            ip: request.ip,
            endpoint: request.url,
            requestId,
          },
          'API key usage: classification request successful'
        );
      }

      return reply.status(200).send({ ...result, cacheHit: false, correlationId });
    } catch (error) {
      // SEC-004: Handle file size validation errors with appropriate response
      if (error instanceof Error && error.message.includes('File size exceeds')) {
        request.log.warn(
          {
            ip: request.ip,
            apiKeyPrefix: apiKey?.substring(0, 8),
            error: error.message,
          },
          'File upload rejected: size limit exceeded'
        );

        if (config.security.logApiKeyUsage && apiKey) {
          apiKeyManager.logUsage({
            apiKey: apiKey.substring(0, 8) + '***',
            timestamp: new Date(),
            endpoint: request.url,
            method: request.method,
            success: false,
            ip: request.ip,
            userAgent: request.headers['user-agent'],
            errorCode: 'FILE_TOO_LARGE',
          });
        }
        if (apiKey) {
          usageStore.recordClassification(apiKey, config.classifier.visionFeature, true);
        }

        return reply.status(413).send({
          error: {
            code: 'FILE_TOO_LARGE',
            message: `File size exceeds maximum allowed size of ${config.classifier.maxUploadBytes} bytes`,
            correlationId,
          },
        });
      }

      // Log failed request for other errors
      if (config.security.logApiKeyUsage && apiKey) {
        apiKeyManager.logUsage({
          apiKey: apiKey.substring(0, 8) + '***',
          timestamp: new Date(),
          endpoint: request.url,
          method: request.method,
          success: false,
          ip: request.ip,
          userAgent: request.headers['user-agent'],
          errorCode: 'CLASSIFICATION_ERROR',
        });
      }
      if (apiKey) {
        usageStore.recordClassification(apiKey, config.classifier.visionFeature, true);
      }
      throw error;
    } finally {
      decrementConcurrent(apiKey);
    }
  });
};

async function parseMultipartPayload(request: FastifyRequest) {
  const file = await request.file();
  const fields: Record<string, string> = {};

  const rawFields = file?.fields as Record<string, unknown> | undefined;
  if (rawFields) {
    for (const [key, value] of Object.entries(rawFields)) {
      if (Array.isArray(value)) {
        const entry = value[0] as { value?: unknown };
        fields[key] = String(entry?.value ?? '');
      } else {
        const entry = value as { value?: unknown };
        fields[key] = String(entry?.value ?? '');
      }
    }
  }

  return { file, fields };
}

function extractApiKey(request: FastifyRequest): string | undefined {
  const header = request.headers['x-api-key'];
  if (!header) return undefined;
  return Array.isArray(header) ? header[0] : header;
}

function extractDeviceId(request: FastifyRequest): string | null {
  const header = request.headers['x-scanium-device-id'];
  if (!header) return null;
  const raw = Array.isArray(header) ? header[0] : header;
  if (!raw) return null;
  const trimmed = raw.trim();
  return trimmed ? sha256Hex(trimmed) : null;
}

type RedisClientWithLifecycle = RedisClient & {
  connect?: () => Promise<void>;
  quit?: () => Promise<void>;
  on?: (event: string, listener: (error: unknown) => void) => void;
};

async function createRedisClient(
  url: string | undefined,
  logger: FastifyBaseLogger
): Promise<RedisClientWithLifecycle | undefined> {
  if (!url) return undefined;

  try {
    const redisModule = (await import('ioredis')) as {
      default: new (connectionString: string, opts?: unknown) => RedisClientWithLifecycle;
    };

    const client = new redisModule.default(url, { lazyConnect: true });

    client.on?.('error', (error) => {
      logger.error({ error }, 'Rate limit Redis connection error');
    });

    await client.connect?.();
    logger.info('Rate limit Redis connection established');
    return client;
  } catch (error) {
    logger.warn({ error }, 'Falling back to in-memory rate limiting');
    return undefined;
  }
}

function isOverConcurrentLimit(apiKey: string, limit: number): boolean {
  const current = inFlightByKey.get(apiKey) ?? 0;
  if (current >= limit) {
    return true;
  }
  inFlightByKey.set(apiKey, current + 1);
  return false;
}

function decrementConcurrent(apiKey?: string) {
  if (!apiKey) return;
  const current = inFlightByKey.get(apiKey) ?? 0;
  const next = Math.max(0, current - 1);
  if (next === 0) {
    inFlightByKey.delete(apiKey);
  } else {
    inFlightByKey.set(apiKey, next);
  }
}

/**
 * SEC-004: Read file with progressive size validation to prevent memory exhaustion
 *
 * This function reads the file stream incrementally, validating size as bytes
 * are received rather than buffering the entire file first. This prevents
 * memory exhaustion attacks from large file uploads.
 *
 * @param file - Multipart file from request
 * @param maxBytes - Maximum allowed file size in bytes
 * @returns Buffer containing the file data
 * @throws Error if file exceeds size limit
 */
async function readFileWithSizeValidation(
  file: MultipartFile,
  maxBytes: number
): Promise<Buffer> {
  const chunks: Buffer[] = [];
  let totalBytes = 0;

  try {
    // Read file stream with progressive size checking
    for await (const chunk of file.file) {
      totalBytes += chunk.length;

      // Check size BEFORE adding to memory
      if (totalBytes > maxBytes) {
        // Clean up any buffered chunks immediately
        chunks.length = 0;
        throw new Error(
          `File size exceeds maximum allowed size of ${maxBytes} bytes (received ${totalBytes} bytes)`
        );
      }

      chunks.push(chunk);
    }

    // Concatenate chunks only after validating total size
    return Buffer.concat(chunks);
  } catch (error) {
    // Clean up memory on any error
    chunks.length = 0;
    throw error;
  }
}
