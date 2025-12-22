import { FastifyPluginAsync } from 'fastify';
import { z } from 'zod';
import { Config } from '../../config/index.js';
import { ApiKeyManager } from '../classifier/api-key-manager.js';
import { AssistantService } from './service.js';
import { MockAssistantProvider } from './provider.js';

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
  const service = new AssistantService(provider);

  fastify.post('/assist/chat', async (request, reply) => {
    if (config.assistant.provider === 'disabled') {
      return reply.status(503).send({
        error: { code: 'ASSISTANT_DISABLED', message: 'Assistant is disabled' },
      });
    }

    const apiKey = (request.headers['x-api-key'] as string | undefined)?.trim();
    if (!apiKey || !apiKeyManager.validateKey(apiKey)) {
      return reply.status(401).send({
        error: { code: 'UNAUTHORIZED', message: 'Missing or invalid API key' },
      });
    }

    const parsed = requestSchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.status(400).send({
        error: { code: 'VALIDATION_ERROR', message: 'Invalid request payload' },
      });
    }

    const response = await service.respond(parsed.data);
    return reply.status(200).send(response);
  });
};
