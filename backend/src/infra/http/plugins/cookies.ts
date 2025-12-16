import fastifyCookie from '@fastify/cookie';
import { FastifyPluginAsync } from 'fastify';
import { Config } from '../../../config/index.js';

/**
 * Cookie plugin
 * Enables signed cookies for OAuth state/nonce
 */
export const cookiesPlugin: FastifyPluginAsync<{ config: Config }> = async (
  fastify,
  opts
) => {
  await fastify.register(fastifyCookie, {
    secret: opts.config.sessionSigningSecret,
    hook: 'onRequest',
    parseOptions: {
      httpOnly: true,
      secure: opts.config.nodeEnv === 'production',
      sameSite: 'lax',
      path: '/',
    },
  });
};
