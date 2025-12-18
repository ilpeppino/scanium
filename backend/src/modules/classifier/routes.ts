import { randomUUID } from 'node:crypto';
import { FastifyPluginAsync, FastifyRequest } from 'fastify';
import { ClassifierService } from './service.js';
import { sanitizeImageBuffer, isSupportedImage } from './utils/image.js';
import { ClassificationHints, ClassificationRequest } from './types.js';
import { Config } from '../../config/index.js';

type RouteOpts = { config: Config };

const inFlightByKey = new Map<string, number>();

export const classifierRoutes: FastifyPluginAsync<RouteOpts> = async (
  fastify,
  opts
) => {
  const { config } = opts;
  const apiKeys = new Set(config.classifier.apiKeys);
  const service = new ClassifierService({ config });

  fastify.post('/classify', async (request, reply) => {
    const apiKey = extractApiKey(request);
    if (!apiKey || !apiKeys.has(apiKey)) {
      return reply.status(401).send({
        error: { code: 'UNAUTHORIZED', message: 'Missing or invalid API key' },
      });
    }

    if (!request.isMultipart()) {
      return reply.status(400).send({
        error: { code: 'VALIDATION_ERROR', message: 'multipart/form-data required' },
      });
    }

    if (isOverConcurrentLimit(apiKey, config.classifier.concurrentLimit)) {
      return reply
        .status(429)
        .header('Retry-After', '1')
        .send({ error: { code: 'RATE_LIMIT', message: 'Too many concurrent requests' } });
    }

    try {
      const payload = await parseMultipartPayload(request);
      if (!payload.file) {
        return reply.status(400).send({
          error: { code: 'VALIDATION_ERROR', message: 'image file is required' },
        });
      }

      if (!isSupportedImage(payload.file.mimetype)) {
        return reply.status(400).send({
          error: { code: 'VALIDATION_ERROR', message: 'Unsupported content type' },
        });
      }

      const domainPackId =
        payload.fields.domainPackId?.toString().trim() || config.classifier.domainPackId;
      if (domainPackId !== config.classifier.domainPackId) {
        return reply.status(400).send({
          error: { code: 'VALIDATION_ERROR', message: 'Unknown domainPackId' },
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
            error: { code: 'VALIDATION_ERROR', message: 'Invalid hints JSON' },
          });
        }
      }

      const buffer = await payload.file.toBuffer();
      const sanitized = await sanitizeImageBuffer(buffer, payload.file.mimetype);

      // Images stay in-memory only. Future retention requires explicit opt-in via config.
      const requestId = randomUUID();
      const classificationRequest: ClassificationRequest = {
        requestId,
        buffer: sanitized.buffer,
        contentType: sanitized.normalizedType,
        fileName: payload.file.filename ?? 'upload',
        domainPackId,
        hints,
      };

      const result = await service.classify(classificationRequest);

      return reply.status(200).send(result);
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
