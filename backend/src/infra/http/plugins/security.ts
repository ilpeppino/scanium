import { FastifyPluginAsync, FastifyRequest, FastifyReply } from 'fastify';
import { Config } from '../../../config/index.js';

/**
 * Security plugin for enforcing HTTPS and adding security headers
 * Implements recommendations from SEC-003
 */
export const securityPlugin: FastifyPluginAsync<{ config: Config }> = async (
  fastify,
  opts
) => {
  const { config } = opts;

  // Single onRequest hook for both HTTPS enforcement and security headers
  fastify.addHook('onRequest', async (request: FastifyRequest, reply: FastifyReply) => {
    // 1. Enforce HTTPS in production
    if (config.nodeEnv === 'production' && config.security.enforceHttps) {
      const proto = request.headers['x-forwarded-proto'] ||
                    ((request.raw.socket as any).encrypted ? 'https' : 'http');

      if (proto !== 'https') {
        request.log.warn(
          {
            url: request.url,
            method: request.method,
            protocol: proto,
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

    // 2. Set security headers
    // Strict Transport Security (HSTS)
    if (config.nodeEnv === 'production' && config.security.enableHsts) {
      reply.header(
        'Strict-Transport-Security',
        'max-age=31536000; includeSubDomains; preload'
      );
    }

    // Content Security Policy
    reply.header(
      'Content-Security-Policy',
      "default-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"
    );

    // X-Content-Type-Options
    reply.header('X-Content-Type-Options', 'nosniff');

    // X-Frame-Options
    reply.header('X-Frame-Options', 'DENY');

    // X-XSS-Protection
    reply.header('X-XSS-Protection', '1; mode=block');

    // Referrer-Policy
    reply.header('Referrer-Policy', 'strict-origin-when-cross-origin');

    // Permissions-Policy
    reply.header(
      'Permissions-Policy',
      'camera=(), microphone=(), geolocation=(), interest-cohort=()'
    );
  });
};
