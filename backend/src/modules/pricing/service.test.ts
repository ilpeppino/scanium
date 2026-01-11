import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { PricingService } from './service.js';
import { writeFileSync, unlinkSync, mkdirSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { clearCatalogCache } from '../marketplaces/loader.js';

const TEST_DIR = join(process.cwd(), 'test-tmp-pricing');
const TEST_CATALOG_PATH = join(TEST_DIR, 'test-catalog.json');

const validCatalog = {
  version: 1,
  countries: [
    {
      code: 'NL',
      defaultCurrency: 'EUR',
      marketplaces: [
        { id: 'bol', name: 'Bol.com', domains: ['bol.com'], type: 'marketplace' },
        { id: 'marktplaats', name: 'Marktplaats', domains: ['marktplaats.nl'], type: 'classifieds' },
      ],
    },
    {
      code: 'DE',
      defaultCurrency: 'EUR',
      marketplaces: [
        { id: 'amazon', name: 'Amazon', domains: ['amazon.de'], type: 'global' },
      ],
    },
  ],
};

describe('PricingService', () => {
  beforeEach(() => {
    mkdirSync(TEST_DIR, { recursive: true });
    clearCatalogCache();
  });

  afterEach(() => {
    rmSync(TEST_DIR, { recursive: true, force: true });
  });

  describe('computePricingInsights', () => {
    it('returns DISABLED when pricing is disabled', async () => {
      writeFileSync(TEST_CATALOG_PATH, JSON.stringify(validCatalog));

      const service = new PricingService({
        enabled: false,
        timeoutMs: 5000,
        cacheTtlMs: 1000,
        catalogPath: TEST_CATALOG_PATH,
      });

      const result = await service.computePricingInsights(
        {
          itemId: 'test-1',
          title: 'Nike Air Max 90',
          category: 'Shoes',
          attributes: [
            { key: 'brand', value: 'Nike' },
            { key: 'model', value: 'Air Max 90' },
          ],
        },
        {
          countryCode: 'NL',
          maxResults: 5,
        }
      );

      expect(result.status).toBe('DISABLED');
      expect(result.countryCode).toBe('NL');

      service.stop();
    });

    it('returns NOT_SUPPORTED when pricing is enabled (Phase 2)', async () => {
      writeFileSync(TEST_CATALOG_PATH, JSON.stringify(validCatalog));

      const service = new PricingService({
        enabled: true,
        timeoutMs: 5000,
        cacheTtlMs: 1000,
        catalogPath: TEST_CATALOG_PATH,
      });

      const result = await service.computePricingInsights(
        {
          itemId: 'test-1',
          title: 'Nike Air Max 90',
          category: 'Shoes',
          attributes: [
            { key: 'brand', value: 'Nike' },
            { key: 'model', value: 'Air Max 90' },
          ],
        },
        {
          countryCode: 'NL',
          maxResults: 5,
        }
      );

      expect(result.status).toBe('NOT_SUPPORTED');
      expect(result.countryCode).toBe('NL');
      expect(result.errorCode).toBe('NO_TOOLING');
      expect(result.marketplacesUsed).toHaveLength(2);
      expect(result.marketplacesUsed[0].id).toBe('bol');
      expect(result.querySummary).toContain('Nike Air Max 90');

      service.stop();
    });

    it('returns ERROR for unsupported country', async () => {
      writeFileSync(TEST_CATALOG_PATH, JSON.stringify(validCatalog));

      const service = new PricingService({
        enabled: true,
        timeoutMs: 5000,
        cacheTtlMs: 1000,
        catalogPath: TEST_CATALOG_PATH,
      });

      const result = await service.computePricingInsights(
        {
          itemId: 'test-1',
          title: 'Test Item',
        },
        {
          countryCode: 'US',
          maxResults: 5,
        }
      );

      expect(result.status).toBe('ERROR');
      expect(result.errorCode).toBe('VALIDATION');

      service.stop();
    });

    it('filters marketplaces when specific ones requested', async () => {
      writeFileSync(TEST_CATALOG_PATH, JSON.stringify(validCatalog));

      const service = new PricingService({
        enabled: true,
        timeoutMs: 5000,
        cacheTtlMs: 1000,
        catalogPath: TEST_CATALOG_PATH,
      });

      const result = await service.computePricingInsights(
        {
          itemId: 'test-1',
          title: 'Test Item',
        },
        {
          countryCode: 'NL',
          maxResults: 5,
          marketplaces: ['bol'],
        }
      );

      expect(result.status).toBe('NOT_SUPPORTED');
      expect(result.marketplacesUsed).toHaveLength(1);
      expect(result.marketplacesUsed[0].id).toBe('bol');

      service.stop();
    });

    it('caches pricing results', async () => {
      writeFileSync(TEST_CATALOG_PATH, JSON.stringify(validCatalog));

      const service = new PricingService({
        enabled: true,
        timeoutMs: 5000,
        cacheTtlMs: 60000, // 1 minute
        catalogPath: TEST_CATALOG_PATH,
      });

      const item = {
        itemId: 'test-1',
        title: 'Nike Air Max 90',
      };

      const prefs = {
        countryCode: 'NL' as const,
        maxResults: 5,
      };

      // First call
      const result1 = await service.computePricingInsights(item, prefs);
      expect(result1.status).toBe('NOT_SUPPORTED');

      // Second call should be cached
      const result2 = await service.computePricingInsights(item, prefs);
      expect(result2.status).toBe('NOT_SUPPORTED');
      expect(result2).toEqual(result1);

      // Cache stats should show 1 entry
      const stats = service.getCacheStats();
      expect(stats.size).toBe(1);

      service.stop();
    });
  });
});
