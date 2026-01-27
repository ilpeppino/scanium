import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { FastifyInstance } from 'fastify';
import { buildApp } from '../../app.js';
import { loadConfig } from '../../config/index.js';
import { PricingV4Service } from './service-v4.js';
import { setPricingV4ServiceForTesting } from './routes-v4.js';
import { MarketplaceAdapter } from './types-v4.js';
import { AiClusterer } from './normalization/ai-clusterer.js';

describe('Pricing V4 Routes', () => {
  let app: FastifyInstance;

  beforeEach(async () => {
    process.env.PRICING_V4_ENABLED = 'false';
    process.env.SCANIUM_API_KEYS = 'test-key-123';

    const config = loadConfig();
    app = await buildApp(config);
    await app.ready();
  });

  afterEach(async () => {
    setPricingV4ServiceForTesting(null);
    await app.close();
  });

  describe('POST /v1/pricing/v4', () => {
    it('returns 401 when API key is missing', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/pricing/v4',
        payload: {
          itemId: 'test-123',
          brand: 'Apple',
          productType: 'electronics_smartphone',
          model: 'iPhone 13 Pro',
          condition: 'GOOD',
          countryCode: 'NL',
        },
      });

      expect(response.statusCode).toBe(401);
      const body = JSON.parse(response.body);
      expect(body.success).toBe(false);
      expect(body.error.code).toBe('UNAUTHORIZED');
    });

    it('returns 401 when API key is invalid', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/pricing/v4',
        headers: {
          'x-api-key': 'invalid-key',
        },
        payload: {
          itemId: 'test-123',
          brand: 'Apple',
          productType: 'electronics_smartphone',
          model: 'iPhone 13 Pro',
          condition: 'GOOD',
          countryCode: 'NL',
        },
      });

      expect(response.statusCode).toBe(401);
      const body = JSON.parse(response.body);
      expect(body.success).toBe(false);
      expect(body.error.code).toBe('UNAUTHORIZED');
    });

    it('returns 400 when request body is invalid', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/pricing/v4',
        headers: {
          'x-api-key': 'test-key-123',
        },
        payload: {
          itemId: 'test-123',
        },
      });

      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.body);
      expect(body.success).toBe(false);
      expect(body.error.code).toBe('INVALID_REQUEST');
    });

    it('returns 400 when condition is invalid', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/pricing/v4',
        headers: {
          'x-api-key': 'test-key-123',
        },
        payload: {
          itemId: 'test-123',
          brand: 'Apple',
          productType: 'electronics_smartphone',
          model: 'iPhone 13 Pro',
          condition: 'INVALID_CONDITION',
          countryCode: 'NL',
        },
      });

      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.body);
      expect(body.success).toBe(false);
      expect(body.error.code).toBe('INVALID_REQUEST');
    });

    it('returns 400 when countryCode is invalid', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/pricing/v4',
        headers: {
          'x-api-key': 'test-key-123',
        },
        payload: {
          itemId: 'test-123',
          brand: 'Apple',
          productType: 'electronics_smartphone',
          model: 'iPhone 13 Pro',
          condition: 'GOOD',
          countryCode: 'USA',
        },
      });

      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.body);
      expect(body.success).toBe(false);
      expect(body.error.code).toBe('INVALID_REQUEST');
    });

    it('returns 500 when pricing v4 is disabled', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/pricing/v4',
        headers: {
          'x-api-key': 'test-key-123',
        },
        payload: {
          itemId: 'test-123',
          brand: 'Apple',
          productType: 'electronics_smartphone',
          model: 'iPhone 13 Pro',
          condition: 'GOOD',
          countryCode: 'NL',
        },
      });

      expect(response.statusCode).toBe(500);
      const body = JSON.parse(response.body);
      expect(body.success).toBe(false);
      expect(body.pricing.status).toBe('ERROR');
    });

    it('includes processingTimeMs in response', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/pricing/v4',
        headers: {
          'x-api-key': 'test-key-123',
        },
        payload: {
          itemId: 'test-123',
          brand: 'Apple',
          productType: 'electronics_smartphone',
          model: 'iPhone 13 Pro',
          condition: 'GOOD',
          countryCode: 'NL',
        },
      });

      const body = JSON.parse(response.body);
      expect(body).toHaveProperty('processingTimeMs');
      expect(typeof body.processingTimeMs).toBe('number');
      expect(body.processingTimeMs).toBeGreaterThanOrEqual(0);
    });

    it('accepts all valid condition values', async () => {
      const conditions = [
        'NEW_SEALED',
        'NEW_WITH_TAGS',
        'NEW_WITHOUT_TAGS',
        'LIKE_NEW',
        'GOOD',
        'FAIR',
        'POOR',
      ];

      for (const condition of conditions) {
        const response = await app.inject({
          method: 'POST',
          url: '/v1/pricing/v4',
          headers: {
            'x-api-key': 'test-key-123',
          },
          payload: {
            itemId: 'test-123',
            brand: 'Apple',
            productType: 'electronics_smartphone',
            model: 'iPhone 13 Pro',
            condition,
            countryCode: 'NL',
          },
        });

        expect(response.statusCode).toBe(500);
        const body = JSON.parse(response.body);
        expect(body.pricing.status).toBe('ERROR');
      }
    });

    it('returns 200 with OK status when adapters provide listings', async () => {
      process.env.PRICING_V4_ENABLED = 'true';
      await app.close();
      const config = loadConfig();
      app = await buildApp(config);
      await app.ready();
      const adapter: MarketplaceAdapter = {
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

      const service = new PricingV4Service(config, {
        adapters: [adapter],
        aiClusterer: new AiClusterer({
          enabled: false,
          openaiModel: 'gpt-4o-mini',
          timeoutMs: 10000,
        }),
      });
      setPricingV4ServiceForTesting(service);

      const response = await app.inject({
        method: 'POST',
        url: '/v1/pricing/v4',
        headers: {
          'x-api-key': 'test-key-123',
        },
        payload: {
          itemId: 'test-123',
          brand: 'Apple',
          productType: 'electronics_smartphone',
          model: 'iPhone 13 Pro',
          condition: 'GOOD',
          countryCode: 'NL',
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.pricing.status).toBe('OK');
      expect(body.pricing.range?.median).toBeGreaterThan(0);
      expect(body.pricing.sources[0].listingCount).toBe(2);
    });
  });
});
