import { FastifyPluginAsync } from 'fastify';
import { Config } from '../../config/index.js';
import { usageStore } from '../usage/usage-store.js';
import { ApiKeyManager } from '../classifier/api-key-manager.js';

type RouteOpts = { config: Config };

/**
 * Mask an IP address by replacing the last octet (IPv4) or last hextet (IPv6).
 */
function maskIp(ip: string): string {
  if (!ip) return 'unknown';
  // IPv4
  if (ip.includes('.') && !ip.includes(':')) {
    const parts = ip.split('.');
    if (parts.length === 4) {
      parts[3] = 'xxx';
      return parts.join('.');
    }
  }
  // IPv6 (including IPv4-mapped like ::ffff:1.2.3.4)
  if (ip.includes(':')) {
    const ipv4Match = ip.match(/::ffff:(\d+\.\d+\.\d+\.)\d+$/i);
    if (ipv4Match) {
      return `::ffff:${ipv4Match[1]}xxx`;
    }
    const parts = ip.split(':');
    if (parts.length > 0) {
      parts[parts.length - 1] = 'xxxx';
      return parts.join(':');
    }
  }
  return ip;
}

/**
 * Extract client IP, preferring Cloudflare headers.
 */
function getClientIp(request: { headers: Record<string, string | string[] | undefined>; ip: string }): string {
  const cfIp = request.headers['cf-connecting-ip'];
  if (typeof cfIp === 'string' && cfIp) return cfIp;

  const xff = request.headers['x-forwarded-for'];
  if (typeof xff === 'string' && xff) {
    const first = xff.split(',')[0]?.trim();
    if (first) return first;
  }

  return request.ip;
}

export const adminRoutes: FastifyPluginAsync<RouteOpts> = async (fastify, opts) => {
  const { config } = opts;

  // Use assistant API keys for debug/auth endpoint validation (matches /v1/assist/chat)
  // Fall back to classifier keys for backward compatibility
  const debugApiKeys =
    config.assistant?.apiKeys?.length
      ? config.assistant.apiKeys
      : config.classifier.apiKeys;
  const apiKeyManager = new ApiKeyManager(debugApiKeys);

  fastify.get('/usage', async (request, reply) => {
    if (!config.admin.enabled) {
      return reply.status(404).send({ error: { code: 'NOT_FOUND', message: 'Not found' } });
    }
    const headerKey = request.headers['x-admin-key'];
    const provided = typeof headerKey === 'string' ? headerKey : '';
    if (!config.admin.adminKey || provided !== config.admin.adminKey) {
      return reply
        .status(403)
        .send({ error: { code: 'FORBIDDEN', message: 'Invalid admin key' } });
    }

    return reply.status(200).send({
      correlationId: request.correlationId,
      usage: usageStore.snapshot(),
    });
  });

  /**
   * GET /debug/auth
   * Safe debug endpoint to verify API key header presence end-to-end through Cloudflare.
   * Protected by x-api-key (classifier API keys).
   * Does NOT return full API key - only prefix for verification.
   */
  fastify.get('/debug/auth', async (request, reply) => {
    const apiKeyHeader = request.headers['x-api-key'];
    const apiKey = typeof apiKeyHeader === 'string' ? apiKeyHeader : null;

    if (!apiKey || !apiKeyManager.validateKey(apiKey)) {
      return reply.status(401).send({
        error: {
          code: 'UNAUTHORIZED',
          message: 'Missing or invalid API key',
        },
      });
    }

    const clientIp = getClientIp(request);
    const cfRay = request.headers['cf-ray'];

    return reply.status(200).send({
      hasApiKeyHeader: true,
      apiKeyPrefix: apiKey.substring(0, 6),
      correlationId: request.correlationId ?? null,
      requestId: request.id ?? null,
      userAgent: request.headers['user-agent'] ?? null,
      cfRay: typeof cfRay === 'string' ? cfRay : null,
      clientIp: maskIp(clientIp),
    });
  });
};
