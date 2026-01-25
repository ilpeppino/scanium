import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { FastifyInstance } from 'fastify';
import { buildApp } from '../../app.js';
import { loadConfig } from '../../config/index.js';

describe('Pricing V3 Routes', () => {
  let app: FastifyInstance;

  beforeEach(async () => {
    // Load test config
    process.env.PRICING_V3_ENABLED = 'false'; // Disabled for tests (no OpenAI calls)
    process.env.SCANIUM_API_KEYS = 'test-key-123';

    const config = loadConfig();
    app = await buildApp(config);
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
  });

  describe('POST /v1/pricing/v3', () => {
    it('should return 401 when API key is missing', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/pricing/v3',
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

    it('should return 401 when API key is invalid', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/pricing/v3',
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

    it('should return 400 when request body is invalid', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/pricing/v3',
        headers: {
          'x-api-key': 'test-key-123',
        },
        payload: {
          itemId: 'test-123',
          // Missing required fields
        },
      });

      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.body);
      expect(body.success).toBe(false);
      expect(body.error.code).toBe('INVALID_REQUEST');
    });

    it('should return 400 when condition is invalid', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/pricing/v3',
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

    it('should return 400 when countryCode is invalid', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/pricing/v3',
        headers: {
          'x-api-key': 'test-key-123',
        },
        payload: {
          itemId: 'test-123',
          brand: 'Apple',
          productType: 'electronics_smartphone',
          model: 'iPhone 13 Pro',
          condition: 'GOOD',
          countryCode: 'USA', // Should be 2-letter code
        },
      });

      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.body);
      expect(body.success).toBe(false);
      expect(body.error.code).toBe('INVALID_REQUEST');
    });

    it('should return 503 when pricing feature is disabled', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/pricing/v3',
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

      expect(response.statusCode).toBe(503);
      const body = JSON.parse(response.body);
      expect(body.success).toBe(false);
      expect(body.pricing.status).toBe('DISABLED');
    });

    it('should include promptVersion in response', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/pricing/v3',
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
      expect(body).toHaveProperty('promptVersion');
      expect(body.promptVersion).toBe('1.0.0');
    });

    it('should include processingTimeMs in response', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/pricing/v3',
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

    it('should accept all valid condition values', async () => {
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
          url: '/v1/pricing/v3',
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

        expect(response.statusCode).toBe(503); // Disabled in tests
        const body = JSON.parse(response.body);
        expect(body.pricing.status).toBe('DISABLED');
      }
    });
  });
});
