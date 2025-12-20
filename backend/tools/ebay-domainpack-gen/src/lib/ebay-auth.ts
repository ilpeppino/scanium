/**
 * eBay OAuth Client Credentials Flow
 * Implements application token generation for Taxonomy API access
 */

import type { EBayAuthConfig, EBayTokenResponse } from '../types/ebay.js';

const OAUTH_ENDPOINTS = {
  sandbox: 'https://api.sandbox.ebay.com/identity/v1/oauth2/token',
  production: 'https://api.ebay.com/identity/v1/oauth2/token',
} as const;

export class EBayAuthClient {
  private config: EBayAuthConfig;
  private cachedToken: { token: string; expiresAt: number } | null = null;

  constructor(config: EBayAuthConfig) {
    this.config = config;
  }

  /**
   * Get a valid application access token
   * Uses cached token if still valid, otherwise requests a new one
   */
  async getAccessToken(): Promise<string> {
    // Return cached token if still valid (with 5-minute buffer)
    if (this.cachedToken && this.cachedToken.expiresAt > Date.now() + 5 * 60 * 1000) {
      return this.cachedToken.token;
    }

    // Request new token
    const token = await this.requestAccessToken();
    return token;
  }

  /**
   * Request a new application access token using client credentials
   */
  private async requestAccessToken(): Promise<string> {
    const endpoint = OAUTH_ENDPOINTS[this.config.environment];
    const credentials = Buffer.from(
      `${this.config.clientId}:${this.config.clientSecret}`
    ).toString('base64');

    const response = await fetch(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${credentials}`,
      },
      body: new URLSearchParams({
        grant_type: 'client_credentials',
        scope: 'https://api.ebay.com/oauth/api_scope',
      }),
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(
        `eBay OAuth failed (${response.status}): ${errorText}`
      );
    }

    const data = (await response.json()) as EBayTokenResponse;

    // Cache the token
    this.cachedToken = {
      token: data.access_token,
      expiresAt: Date.now() + data.expires_in * 1000,
    };

    return data.access_token;
  }

  /**
   * Clear cached token (useful for testing)
   */
  clearCache(): void {
    this.cachedToken = null;
  }
}
