import { describe, expect, it } from 'vitest';
import { PricingV4Service } from './service-v4.js';
import { PricingV4Request } from './types-v4.js';
import { MarketplaceAdapter } from './types-v4.js';

const baseConfig = {
  pricing: {
    v4Enabled: true,
    v4AiNormEnabled: false,
    v4FallbackToV3: true,
    v4CacheTtlSeconds: 60,
    v4TimeoutMs: 20000,
    v3Enabled: true,
    v3TimeoutMs: 15000,
    v3CacheTtlSeconds: 86400,
    v3DailyQuota: 1000,
    v3PromptVersion: '1.0.0',
    openaiApiKey: 'test',
    openaiModel: 'gpt-4o-mini',
    catalogPath: 'config/marketplaces/marketplaces.eu.json',
  },
  ebay: {
    env: 'sandbox',
    clientId: 'id',
    clientSecret: 'secret',
    scopes: 'https://api.ebay.com/oauth/api_scope',
    redirectPath: '/auth/ebay/callback',
    tokenEncryptionKey: 'x'.repeat(32),
  },
} as any;

const request: PricingV4Request = {
  itemId: 'item-1',
  brand: 'Apple',
  productType: 'electronics_smartphone',
  model: 'iPhone 13',
  condition: 'GOOD',
  countryCode: 'NL',
};

describe('PricingV4Service', () => {
  it('falls back to V3 when adapters fail', async () => {
    const badAdapter: MarketplaceAdapter = {
      id: 'marktplaats',
      name: 'Marktplaats',
      fetchListings: async () => {
        throw new Error('boom');
      },
      buildSearchUrl: () => 'https://example.com',
      isHealthy: async () => true,
    };

    const v3Service = {
      estimateResalePrice: async () => ({
        status: 'OK',
        countryCode: 'NL',
        marketplacesUsed: [],
        range: { low: 100, high: 200, currency: 'EUR' },
        confidence: 'MED',
        resultCount: 5,
      }),
      stop: () => {},
    } as any;

    const service = new PricingV4Service(baseConfig, {
      adapters: [badAdapter],
      v3Service,
    });

    const result = await service.estimateVerifiableRange(request);
    expect(result.status).toBe('FALLBACK');
    expect(result.range?.median).toBe(150);
    expect(result.fallbackReason).toContain('V3');
  });

  it('returns OK when adapters provide listings', async () => {
    const goodAdapter: MarketplaceAdapter = {
      id: 'marktplaats',
      name: 'Marktplaats',
      fetchListings: async () => [
        {
          title: 'Apple iPhone 13',
          price: 120,
          currency: 'EUR',
          url: 'https://example.com/1',
          marketplace: 'marktplaats',
        },
        {
          title: 'Apple iPhone 13',
          price: 140,
          currency: 'EUR',
          url: 'https://example.com/2',
          marketplace: 'marktplaats',
        },
      ],
      buildSearchUrl: () => 'https://example.com',
      isHealthy: async () => true,
    };

    const service = new PricingV4Service(baseConfig, {
      adapters: [goodAdapter],
    });

    const result = await service.estimateVerifiableRange(request);
    expect(result.status).toBe('OK');
    expect(result.range?.median).toBeGreaterThan(0);
    expect(result.sources[0].listingCount).toBe(2);
  });
});
