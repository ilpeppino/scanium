import crypto from 'node:crypto';
import type { FastifyRequest } from 'fastify';

export type RequestIdentity = {
  kind: 'apiKey' | 'ip';
  id: string;          // stable ID (hashed for apiKey)
  hasApiKey: boolean;
};

export function getRequestIdentity(request: FastifyRequest): RequestIdentity {
  const rawKey = request.headers['x-api-key'];
  const apiKey = Array.isArray(rawKey) ? rawKey[0] : rawKey;

  if (typeof apiKey === 'string' && apiKey.trim().length > 0) {
    const hash = crypto.createHash('sha256').update(apiKey).digest('hex');
    return { kind: 'apiKey', id: `k_${hash.slice(0, 12)}`, hasApiKey: true };
  }

  const ip =
    (request.headers['cf-connecting-ip'] as string | undefined) ||
    (request.headers['x-forwarded-for'] as string | undefined)?.split(',')[0]?.trim() ||
    request.ip ||
    'unknown';

  return { kind: 'ip', id: `ip_${ip}`, hasApiKey: false };
}