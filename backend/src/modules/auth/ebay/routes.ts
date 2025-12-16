import { FastifyPluginAsync } from 'fastify';
import { z } from 'zod';
import { Config } from '../../../config/index.js';
import {
  OAuthStateMismatchError,
  ValidationError,
} from '../../../shared/errors/index.js';
import {
  buildAuthorizationUrl,
  exchangeCodeForTokens,
  generateNonce,
  generateState,
} from './oauth-flow.js';
import {
  getEbayConnectionStatus,
  storeEbayTokens,
} from './token-storage.js';

const COOKIE_MAX_AGE = 60 * 10; // 10 minutes

/**
 * eBay OAuth routes
 */
export const ebayAuthRoutes: FastifyPluginAsync<{ config: Config }> = async (
  fastify,
  opts
) => {
  const config = opts.config;

  /**
   * POST /auth/ebay/start
   * Initiates eBay OAuth flow
   * Returns authorization URL for mobile app to open in browser
   */
  fastify.post('/start', async (request, reply) => {
    // Generate state and nonce for CSRF protection
    const state = generateState();
    const nonce = generateNonce();

    // Store state and nonce in signed cookies
    reply
      .setCookie('oauth_state', state, {
        signed: true,
        httpOnly: true,
        secure: config.nodeEnv === 'production',
        sameSite: 'lax',
        maxAge: COOKIE_MAX_AGE,
        path: '/',
      })
      .setCookie('oauth_nonce', nonce, {
        signed: true,
        httpOnly: true,
        secure: config.nodeEnv === 'production',
        sameSite: 'lax',
        maxAge: COOKIE_MAX_AGE,
        path: '/',
      });

    // Build authorization URL
    const authorizeUrl = buildAuthorizationUrl(config, state, nonce);

    request.log.info({ state }, 'OAuth flow initiated');

    return reply.status(200).send({
      authorizeUrl,
    });
  });

  /**
   * GET /auth/ebay/callback
   * Receives authorization code from eBay
   * Exchanges code for tokens and stores them
   */
  fastify.get('/callback', async (request, reply) => {
    // Parse query parameters
    const querySchema = z.object({
      code: z.string().min(1),
      state: z.string().min(1),
    });

    const parseResult = querySchema.safeParse(request.query);

    if (!parseResult.success) {
      throw new ValidationError('Missing code or state parameter', {
        errors: parseResult.error.errors,
      });
    }

    const { code, state } = parseResult.data;

    // Validate state against cookie
    const storedState = request.unsignCookie(
      request.cookies.oauth_state ?? ''
    );

    if (!storedState.valid || storedState.value !== state) {
      request.log.warn(
        { receivedState: state, storedState: storedState.value },
        'OAuth state mismatch'
      );
      throw new OAuthStateMismatchError();
    }

    // Clear cookies
    reply.clearCookie('oauth_state').clearCookie('oauth_nonce');

    // Exchange code for tokens
    const tokens = await exchangeCodeForTokens(config, code);

    // Store tokens in database
    await storeEbayTokens(config, tokens);

    request.log.info(
      {
        environment: config.ebay.env,
        scopes: tokens.scope,
      },
      'eBay OAuth successful'
    );

    // Check Accept header to determine response format
    const acceptsJson = request.headers.accept?.includes('application/json');

    if (acceptsJson) {
      return reply.status(200).send({
        success: true,
        message: 'eBay account connected successfully',
        environment: config.ebay.env,
      });
    }

    // Return HTML success page for browser
    const html = `
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>eBay Connected - Scanium</title>
  <style>
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      margin: 0;
      background: linear-gradient(135deg, ***REMOVED***667eea 0%, ***REMOVED***764ba2 100%);
      color: white;
    }
    .container {
      text-align: center;
      padding: 2rem;
      background: rgba(255, 255, 255, 0.1);
      border-radius: 16px;
      backdrop-filter: blur(10px);
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
    }
    .icon {
      font-size: 64px;
      margin-bottom: 1rem;
    }
    h1 {
      margin: 0 0 0.5rem 0;
      font-size: 28px;
    }
    p {
      margin: 0;
      opacity: 0.9;
      font-size: 16px;
    }
    .env-badge {
      display: inline-block;
      margin-top: 1rem;
      padding: 0.5rem 1rem;
      background: rgba(255, 255, 255, 0.2);
      border-radius: 8px;
      font-size: 14px;
      font-weight: 600;
    }
  </style>
</head>
<body>
  <div class="container">
    <div class="icon">✅</div>
    <h1>eBay Connected!</h1>
    <p>Your eBay account has been successfully connected.</p>
    <p>You can now return to the Scanium app.</p>
    <div class="env-badge">${config.ebay.env.toUpperCase()}</div>
  </div>
</body>
</html>
`;

    return reply.type('text/html').send(html);
  });

  /**
   * GET /auth/ebay/status
   * Returns eBay connection status
   * Used by mobile app to check if OAuth is connected
   */
  fastify.get('/status', async (request, reply) => {
    const status = await getEbayConnectionStatus(config);

    return reply.status(200).send({
      connected: status.connected,
      environment: status.environment,
      scopes: status.scopes,
      expiresAt: status.expiresAt?.toISOString(),
    });
  });
};
