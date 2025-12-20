import fastifyHelmet from '@fastify/helmet';
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

  const enableHsts = config.nodeEnv === 'production' && config.security.enableHsts;

  await fastify.register(fastifyHelmet, {
    contentSecurityPolicy: {
      directives: {
        defaultSrc: ["'self'"],
        baseUri: ["'self'"],
        formAction: ["'self'"],
        frameAncestors: ["'none'"],
      },
    },
    frameguard: { action: 'deny' },
    hsts: enableHsts
      ? {
          maxAge: 31536000,
          includeSubDomains: true,
          preload: true,
        }
      : false,
    referrerPolicy: { policy: 'strict-origin-when-cross-origin' },
  });

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

    // 2. Additional security headers not covered by helmet
    reply.header(
      'Permissions-Policy',
      'camera=(), microphone=(), geolocation=(), interest-cohort=()'
    );
  });
};
