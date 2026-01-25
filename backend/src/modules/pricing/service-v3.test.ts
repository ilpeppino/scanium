import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { PricingV3Service } from './service-v3.js';
import { PricingV3Request, PricingV3Config } from './types-v3.js';

describe('PricingV3Service', () => {
  let service: PricingV3Service;
  let config: PricingV3Config;

  beforeEach(() => {
    config = {
      enabled: true,
      timeoutMs: 15000,
      cacheTtlSeconds: 86400,
      dailyQuota: 1000,
      promptVersion: '1.0.0',
      openaiApiKey: 'test-key',
      openaiModel: 'gpt-4o-mini',
      catalogPath: 'config/marketplaces/marketplaces.eu.json',
    };
  });

  afterEach(() => {
    if (service) {
      service.stop();
    }
  });

  describe('buildCacheKey', () => {
    it('should generate consistent cache key for same input', () => {
      service = new PricingV3Service(config);

      const key1 = service.buildCacheKey({
        brand: 'Apple',
        productType: 'electronics_smartphone',
        model: 'iPhone 13 Pro',
        condition: 'GOOD',
        countryCode: 'NL',
      });

      const key2 = service.buildCacheKey({
        brand: 'Apple',
        productType: 'electronics_smartphone',
        model: 'iPhone 13 Pro',
        condition: 'GOOD',
        countryCode: 'NL',
      });

      expect(key1).toBe(key2);
      expect(key1).toMatch(/^pricing:v3:[a-f0-9]{64}$/);
    });

    it('should generate different cache keys for different inputs', () => {
      service = new PricingV3Service(config);

      const key1 = service.buildCacheKey({
        brand: 'Apple',
        productType: 'electronics_smartphone',
        model: 'iPhone 13 Pro',
        condition: 'GOOD',
        countryCode: 'NL',
      });

      const key2 = service.buildCacheKey({
        brand: 'Apple',
        productType: 'electronics_smartphone',
        model: 'iPhone 13 Pro',
        condition: 'LIKE_NEW', // Different condition
        countryCode: 'NL',
      });

      expect(key1).not.toBe(key2);
    });

    it('should normalize input (case insensitive, trimmed)', () => {
      service = new PricingV3Service(config);

      const key1 = service.buildCacheKey({
        brand: 'Apple',
        productType: 'electronics_smartphone',
        model: 'iPhone 13 Pro',
        condition: 'GOOD',
        countryCode: 'NL',
      });

      const key2 = service.buildCacheKey({
        brand: ' APPLE ',
        productType: ' ELECTRONICS_SMARTPHONE ',
        model: ' IPHONE 13 PRO ',
        condition: 'GOOD',
        countryCode: 'NL',
      });

      expect(key1).toBe(key2);
    });
  });

  describe('estimateResalePrice', () => {
    it('should return DISABLED when feature is disabled', async () => {
      config.enabled = false;
      service = new PricingV3Service(config);

      const request: PricingV3Request = {
        itemId: 'test-123',
        brand: 'Apple',
        productType: 'electronics_smartphone',
        model: 'iPhone 13 Pro',
        condition: 'GOOD',
        countryCode: 'NL',
      };

      const result = await service.estimateResalePrice(request);

      expect(result.status).toBe('DISABLED');
      expect(result.countryCode).toBe('NL');
    });

    it('should return ERROR when OpenAI is not configured', async () => {
      config.openaiApiKey = undefined;
      service = new PricingV3Service(config);

      const request: PricingV3Request = {
        itemId: 'test-123',
        brand: 'Apple',
        productType: 'electronics_smartphone',
        model: 'iPhone 13 Pro',
        condition: 'GOOD',
        countryCode: 'NL',
      };

      const result = await service.estimateResalePrice(request);

      expect(result.status).toBe('ERROR');
      expect(result.reason).toContain('OpenAI');
    });

    it('should return ERROR when country is not supported', async () => {
      service = new PricingV3Service(config);

      const request: PricingV3Request = {
        itemId: 'test-123',
        brand: 'Apple',
        productType: 'electronics_smartphone',
        model: 'iPhone 13 Pro',
        condition: 'GOOD',
        countryCode: 'XX', // Invalid country
      };

      const result = await service.estimateResalePrice(request);

      expect(result.status).toBe('ERROR');
      expect(result.reason).toContain('not supported');
    });
  });

  describe('getCacheStats', () => {
    it('should return cache statistics', () => {
      service = new PricingV3Service(config);

      const stats = service.getCacheStats();

      expect(stats).toHaveProperty('size');
      expect(stats).toHaveProperty('maxTtlSeconds');
      expect(stats.maxTtlSeconds).toBe(86400);
    });
  });

  describe('getPromptVersion', () => {
    it('should return prompt version', () => {
      service = new PricingV3Service(config);

      const version = service.getPromptVersion();

      expect(version).toBe('1.0.0');
    });
  });
});
