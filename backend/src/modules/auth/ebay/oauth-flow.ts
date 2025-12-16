import crypto from 'crypto';
import { Config, getEbayAuthEndpoint, getEbayTokenEndpoint } from '../../../config/index.js';
import { OAuthTokenExchangeError } from '../../../shared/errors/index.js';

/**
 * Generate cryptographically strong random state
 */
export function generateState(): string {
  return crypto.randomBytes(32).toString('base64url');
}

/**
 * Generate cryptographically strong random nonce
 */
export function generateNonce(): string {
  return crypto.randomBytes(32).toString('base64url');
}

/**
 * Build eBay authorization URL
 */
export function buildAuthorizationUrl(
  config: Config,
  state: string,
  nonce: string
): string {
  const endpoint = getEbayAuthEndpoint(config);
  const redirectUri = `${config.publicBaseUrl}${config.ebay.redirectPath}`;

  const params = new URLSearchParams({
    client_id: config.ebay.clientId,
    redirect_uri: redirectUri,
    response_type: 'code',
    scope: config.ebay.scopes,
    state,
    prompt: 'login',
  });

  return `${endpoint}?${params.toString()}`;
}

/**
 * Exchange authorization code for tokens
 */
export async function exchangeCodeForTokens(
  config: Config,
  code: string
): Promise<{
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: string;
  scope: string;
}> {
  const endpoint = getEbayTokenEndpoint(config);
  const redirectUri = `${config.publicBaseUrl}${config.ebay.redirectPath}`;

  // Prepare Basic Auth header
  const credentials = Buffer.from(
    `${config.ebay.clientId}:${config.ebay.clientSecret}`
  ).toString('base64');

  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    code,
    redirect_uri: redirectUri,
  });

  try {
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${credentials}`,
      },
      body: body.toString(),
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new OAuthTokenExchangeError({
        status: response.status,
        body: errorText,
      });
    }

    const data = await response.json();

    return {
      accessToken: data.access_token,
      refreshToken: data.refresh_token,
      expiresIn: data.expires_in,
      tokenType: data.token_type,
      scope: data.scope || config.ebay.scopes,
    };
  } catch (error) {
    if (error instanceof OAuthTokenExchangeError) {
      throw error;
    }
    throw new OAuthTokenExchangeError({
      message: error instanceof Error ? error.message : 'Unknown error',
    });
  }
}
