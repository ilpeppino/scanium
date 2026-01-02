import fp from 'fastify-plugin';
import { FastifyPluginAsync, FastifyRequest, FastifyReply } from 'fastify';
import { Config } from '../../../config/index.js';

/**
 * Paths exempt from HTTPS enforcement (for Docker healthchecks).
 */
const HTTPS_EXEMPT_PATHS = ['/health', '/healthz', '/readyz'];

/**
 * Check if a request path is exempt from HTTPS enforcement.
 */
function isHttpsExempt(url: string): boolean {
  // Extract path without query string
  const path = url.split('?')[0];
  return HTTPS_EXEMPT_PATHS.includes(path);
}

/**
 * Detect if request is over HTTPS via proxy headers.
 * Supports x-forwarded-proto and Cloudflare's cf-visitor header.
 */
function isHttps(request: FastifyRequest): boolean {
  // Check x-forwarded-proto first (standard proxy header)
  const proto = request.headers['x-forwarded-proto'];
  if (proto === 'https') return true;
  if (proto === 'http') return false;

  // Check Cloudflare cf-visitor header: {"scheme":"https"}
  const cfVisitor = request.headers['cf-visitor'];
  if (typeof cfVisitor === 'string') {
    try {
      const parsed = JSON.parse(cfVisitor);
      if (parsed.scheme === 'https') return true;
    } catch {
      // Ignore parse errors
    }
  }

  // Fallback: check socket encryption
  return !!(request.raw.socket as { encrypted?: boolean }).encrypted;
}

/**
 * Security plugin for enforcing HTTPS and adding security headers.
 * Uses fastify-plugin to break encapsulation and apply hooks globally.
 */
const securityPluginImpl: FastifyPluginAsync<{ config: Config }> = async (
  fastify,
  opts
) => {
  const { config } = opts;
  const isProduction = config.nodeEnv === 'production';
  const enableHsts = isProduction && config.security.enableHsts;

  // HTTPS enforcement hook (runs before request handling)
  fastify.addHook('onRequest', async (request: FastifyRequest, reply: FastifyReply) => {
    // Only enforce in production when enabled
    if (isProduction && config.security.enforceHttps) {
      // Skip enforcement for health check endpoints (Docker healthchecks)
      if (isHttpsExempt(request.url)) {
        return;
      }

      if (!isHttps(request)) {
        request.log.warn(
          {
            url: request.url,
            method: request.method,
            ip: request.ip,
          },
          'HTTPS required - rejecting HTTP request'
        );

        return reply.status(403).send({
          error: {
            code: 'HTTPS_REQUIRED',
            message: 'HTTPS is required for this endpoint',
          },
        });
      }
    }
  });

  // Security headers hook (runs after response is ready)
  // Set headers explicitly for deterministic behavior
  fastify.addHook('onSend', async (_request: FastifyRequest, reply: FastifyReply) => {
    // HSTS - only in production
    if (enableHsts) {
      reply.header(
        'Strict-Transport-Security',
        'max-age=31536000; includeSubDomains; preload'
      );
    }

    // CSP
    reply.header(
      'Content-Security-Policy',
      "default-src 'self'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"
    );

    // Standard security headers
    reply.header('X-Content-Type-Options', 'nosniff');
    reply.header('X-Frame-Options', 'DENY');
    reply.header('X-XSS-Protection', '1; mode=block');
    reply.header('Referrer-Policy', 'strict-origin-when-cross-origin');

    // Permissions-Policy
    reply.header(
      'Permissions-Policy',
      'camera=(), microphone=(), geolocation=(), interest-cohort=()'
    );
  });
};

// Export wrapped with fastify-plugin to break encapsulation
export const securityPlugin = fp(securityPluginImpl, {
  name: 'security-plugin',
});
