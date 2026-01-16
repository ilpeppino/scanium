/**
 * Pricing Regression Guards
 *
 * Prevents backend from emitting hardcoded English utility strings that belong
 * to Android resources, not backend responses.
 *
 * This test ensures:
 * 1) Runtime responses don't contain prohibited hardcoded English phrases
 * 2) Structured pricing payloads are complete and properly formatted
 * 3) Backend maintains API contract for localization-ready responses
 */

import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import Fastify, { FastifyInstance } from 'fastify';
import { pricingRoutes } from './routes.js';

// Mock config for testing
const mockConfig = {
  classifier: {
    apiKeys: ['test-api-key-12345'],
  },
};

// Prohibited phrases that belong to Android resources, not backend
const PROHIBITED_PHRASES = [
  'Typical resale value',
  'Based on',
  'market conditions',
];

describe('Pricing Regression Guards', () => {
  let app: FastifyInstance;

  beforeAll(async () => {
    app = Fastify({ logger: false });
    await app.register(pricingRoutes, { prefix: '/v1', config: mockConfig as any });
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
  });

  describe('No Hardcoded English Utility Strings', () => {
    it('should not emit prohibited phrases in Phase 1 estimate response', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/pricing/estimate',
        headers: { 'x-api-key': 'test-api-key-12345' },
        payload: {
          itemId: 'test-regression-1',
          category: 'consumer_electronics_portable',
          brand: 'APPLE',
          condition: 'GOOD',
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      const responseText = JSON.stringify(body);

      // Check for prohibited phrases
      for (const phrase of PROHIBITED_PHRASES) {
        expect(responseText).not.toContain(phrase);
      }
    });

    it('should not emit prohibited phrases in Phase 2 estimate response', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/pricing/estimate/v2',
        headers: { 'x-api-key': 'test-api-key-12345' },
        payload: {
          itemId: 'test-regression-2',
          category: 'furniture',
          brand: 'IKEA',
          condition: 'LIKE_NEW',
          marketContext: {
            region: 'US',
            isUrban: true,
          },
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      const responseText = JSON.stringify(body);

      // Check for prohibited phrases
      for (const phrase of PROHIBITED_PHRASES) {
        expect(responseText).not.toContain(phrase);
      }
    });

    it('should not emit prohibited phrases in explanation array', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/pricing/estimate',
        headers: { 'x-api-key': 'test-api-key-12345' },
        payload: {
          itemId: 'test-regression-3',
          category: 'kitchen_appliance',
          brand: 'LE_CREUSET',
          condition: 'NEW_WITH_TAGS',
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      const explanation = body.estimate.explanation || [];

      // Each explanation line should not contain prohibited phrases
      for (const line of explanation) {
        for (const phrase of PROHIBITED_PHRASES) {
          expect(line).not.toContain(phrase);
        }
      }
    });

    it('should not emit prohibited phrases in caveats', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/pricing/estimate',
        headers: { 'x-api-key': 'test-api-key-12345' },
        payload: {
          itemId: 'test-regression-4',
          // Minimal input to potentially trigger caveats
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      const caveats = body.estimate.caveats || [];

      // Each caveat should not contain prohibited phrases
      for (const caveat of caveats) {
        for (const phrase of PROHIBITED_PHRASES) {
          expect(caveat).not.toContain(phrase);
        }
      }
    });

    it('should not emit prohibited phrases in calculation steps (debug mode)', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/pricing/estimate',
        headers: {
          'x-api-key': 'test-api-key-12345',
          'x-debug': 'true',
        },
        payload: {
          itemId: 'test-regression-5',
          category: 'textile',
          condition: 'FAIR',
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      const steps = body.debug?.calculationSteps || [];

      // Each step description should not contain prohibited phrases
      for (const step of steps) {
        const stepText = step.description || '';
        for (const phrase of PROHIBITED_PHRASES) {
          expect(stepText).not.toContain(phrase);
        }
      }
    });
  });

  describe('Structured Pricing Payload Validation', () => {
    it('should include complete pricing fields in Phase 1 response', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/pricing/estimate',
        headers: { 'x-api-key': 'test-api-key-12345' },
        payload: {
          itemId: 'test-structure-1',
          category: 'consumer_electronics_portable',
          brand: 'SAMSUNG',
          condition: 'GOOD',
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      const estimate = body.estimate;

      // Validate structured pricing fields
      expect(estimate).toHaveProperty('priceEstimateMinCents');
      expect(estimate).toHaveProperty('priceEstimateMaxCents');
      expect(estimate).toHaveProperty('priceEstimateMin');
      expect(estimate).toHaveProperty('priceEstimateMax');
      expect(estimate).toHaveProperty('priceRangeFormatted');
      expect(estimate).toHaveProperty('confidence');

      // Validate types
      expect(typeof estimate.priceEstimateMinCents).toBe('number');
      expect(typeof estimate.priceEstimateMaxCents).toBe('number');
      expect(typeof estimate.priceEstimateMin).toBe('number');
      expect(typeof estimate.priceEstimateMax).toBe('number');
      expect(typeof estimate.priceRangeFormatted).toBe('string');
      expect(['LOW', 'MEDIUM', 'HIGH']).toContain(estimate.confidence);

      // Validate logical constraints
      expect(estimate.priceEstimateMinCents).toBeGreaterThan(0);
      expect(estimate.priceEstimateMaxCents).toBeGreaterThanOrEqual(estimate.priceEstimateMinCents);
      expect(estimate.priceEstimateMin).toBeGreaterThan(0);
      expect(estimate.priceEstimateMax).toBeGreaterThanOrEqual(estimate.priceEstimateMin);
    });

    it('should include input summary for localization context', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/pricing/estimate',
        headers: { 'x-api-key': 'test-api-key-12345' },
        payload: {
          itemId: 'test-structure-2',
          category: 'drinkware',
          brand: 'NESPRESSO',
          condition: 'NEW_SEALED',
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      const summary = body.estimate.inputSummary;

      // Input summary should be present for localization
      expect(summary).toBeDefined();
      expect(summary).toHaveProperty('category');
      expect(summary).toHaveProperty('categoryLabel');
      expect(summary).toHaveProperty('brand');
      expect(summary).toHaveProperty('brandTier');
      expect(summary).toHaveProperty('condition');
      expect(summary).toHaveProperty('completenessScore');
    });

    it('should include complete pricing fields in Phase 2 response', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/pricing/estimate/v2',
        headers: { 'x-api-key': 'test-api-key-12345' },
        payload: {
          itemId: 'test-structure-3',
          category: 'furniture',
          brand: 'IKEA',
          condition: 'GOOD',
          marketContext: {
            region: 'NL',
            isUrban: false,
          },
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      const estimate = body.estimate;

      // Phase 2 includes all Phase 1 fields plus market awareness
      expect(estimate).toHaveProperty('priceEstimateMinCents');
      expect(estimate).toHaveProperty('priceEstimateMaxCents');
      expect(estimate).toHaveProperty('priceEstimateMin');
      expect(estimate).toHaveProperty('priceEstimateMax');
      expect(estimate).toHaveProperty('priceRangeFormatted');
      expect(estimate).toHaveProperty('currency');
      expect(estimate).toHaveProperty('currencySymbol');
      expect(estimate).toHaveProperty('confidence');
      expect(estimate).toHaveProperty('marketContext');
      expect(estimate).toHaveProperty('inputSummary');

      // Validate market context fields
      expect(typeof estimate.currency).toBe('string');
      expect(typeof estimate.currencySymbol).toBe('string');
      expect(typeof estimate.marketContext).toBe('string');
    });

    it('should maintain API contract with array fields', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/pricing/estimate',
        headers: { 'x-api-key': 'test-api-key-12345' },
        payload: {
          itemId: 'test-structure-4',
          category: 'kitchen_appliance',
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      const estimate = body.estimate;

      // Explanation should be an array
      expect(Array.isArray(estimate.explanation)).toBe(true);
      expect(estimate.explanation.length).toBeGreaterThan(0);

      // Each explanation line should be a string
      for (const line of estimate.explanation) {
        expect(typeof line).toBe('string');
      }

      // Caveats should be optional array if present
      if (estimate.caveats !== undefined) {
        expect(Array.isArray(estimate.caveats)).toBe(true);
        for (const caveat of estimate.caveats) {
          expect(typeof caveat).toBe('string');
        }
      }
    });

    it('should maintain consistency between price formats', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/pricing/estimate',
        headers: { 'x-api-key': 'test-api-key-12345' },
        payload: {
          itemId: 'test-structure-5',
          category: 'consumer_electronics_stationary',
          brand: 'SONY',
          condition: 'LIKE_NEW',
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      const estimate = body.estimate;

      // Cents and dollars should be consistent
      expect(estimate.priceEstimateMin).toBeCloseTo(estimate.priceEstimateMinCents / 100, 2);
      expect(estimate.priceEstimateMax).toBeCloseTo(estimate.priceEstimateMaxCents / 100, 2);

      // Formatted range should contain dollar amounts
      expect(estimate.priceRangeFormatted).toMatch(/\$/);
    });
  });
});
