import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { MarketplacesService } from './service.js';
import { writeFileSync, unlinkSync, mkdirSync, rmSync } from 'node:fs';
import { join } from 'node:path';

const TEST_DIR = join(process.cwd(), 'test-tmp-marketplaces');
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

describe('MarketplacesService', () => {
  beforeEach(async () => {
    // Create test directory
    mkdirSync(TEST_DIR, { recursive: true });
    // Clear any cached catalog from loader
    const { clearCatalogCache } = await import('./loader.js');
    clearCatalogCache();
  });

  afterEach(() => {
    // Clean up test directory
    rmSync(TEST_DIR, { recursive: true, force: true });
  });

  describe('initialization', () => {
    it('initializes successfully with valid catalog', () => {
      writeFileSync(TEST_CATALOG_PATH, JSON.stringify(validCatalog));

      const service = new MarketplacesService(TEST_CATALOG_PATH);
      const result = service.initialize();

      expect(result.ready).toBe(true);
      expect(result.error).toBeUndefined();
      expect(service.isReady()).toBe(true);
    });

    it('fails gracefully when catalog file does not exist', () => {
      const service = new MarketplacesService('/nonexistent/catalog.json');
      const result = service.initialize();

      expect(result.ready).toBe(false);
      expect(result.error).toContain('not found');
      expect(service.isReady()).toBe(false);
    });

    it('fails gracefully when catalog has invalid JSON', () => {
      writeFileSync(TEST_CATALOG_PATH, '{ invalid json }');

      const service = new MarketplacesService(TEST_CATALOG_PATH);
      const result = service.initialize();

      expect(result.ready).toBe(false);
      expect(result.error).toContain('parse error');
      expect(service.isReady()).toBe(false);
    });

    it('fails gracefully when catalog schema is invalid', () => {
      const invalidCatalog = {
        version: 'not-a-number', // invalid
        countries: [],
      };
      writeFileSync(TEST_CATALOG_PATH, JSON.stringify(invalidCatalog));

      const service = new MarketplacesService(TEST_CATALOG_PATH);
      const result = service.initialize();

      expect(result.ready).toBe(false);
      expect(result.error).toContain('validation failed');
      expect(service.isReady()).toBe(false);
    });
  });

  describe('listCountries', () => {
    it('returns list of country codes', () => {
      writeFileSync(TEST_CATALOG_PATH, JSON.stringify(validCatalog));

      const service = new MarketplacesService(TEST_CATALOG_PATH);
      service.initialize();

      const result = service.listCountries();

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data).toEqual(['NL', 'DE']);
      }
    });

    it('returns error when service not initialized', () => {
      const service = new MarketplacesService('/nonexistent/catalog.json');
      service.initialize();

      const result = service.listCountries();

      expect(result.success).toBe(false);
      expect(result.errorCode).toBe('CATALOG_ERROR');
    });
  });

  describe('getMarketplaces', () => {
    it('returns marketplaces for known country', () => {
      writeFileSync(TEST_CATALOG_PATH, JSON.stringify(validCatalog));

      const service = new MarketplacesService(TEST_CATALOG_PATH);
      service.initialize();

      const result = service.getMarketplaces('NL');

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data).toHaveLength(2);
        expect(result.data[0].id).toBe('bol');
        expect(result.data[1].id).toBe('marktplaats');
      }
    });

    it('normalizes country code to uppercase', () => {
      writeFileSync(TEST_CATALOG_PATH, JSON.stringify(validCatalog));

      const service = new MarketplacesService(TEST_CATALOG_PATH);
      service.initialize();

      const result = service.getMarketplaces('nl'); // lowercase

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data).toHaveLength(2);
      }
    });

    it('returns NOT_FOUND for unknown country', () => {
      writeFileSync(TEST_CATALOG_PATH, JSON.stringify(validCatalog));

      const service = new MarketplacesService(TEST_CATALOG_PATH);
      service.initialize();

      const result = service.getMarketplaces('US');

      expect(result.success).toBe(false);
      expect(result.errorCode).toBe('NOT_FOUND');
      expect(result.error).toContain('US');
    });

    it('returns error when service not initialized', () => {
      const service = new MarketplacesService('/nonexistent/catalog.json');
      service.initialize();

      const result = service.getMarketplaces('NL');

      expect(result.success).toBe(false);
      expect(result.errorCode).toBe('CATALOG_ERROR');
    });
  });

  describe('getCountryConfig', () => {
    it('returns full country configuration', () => {
      writeFileSync(TEST_CATALOG_PATH, JSON.stringify(validCatalog));

      const service = new MarketplacesService(TEST_CATALOG_PATH);
      service.initialize();

      const result = service.getCountryConfig('NL');

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.code).toBe('NL');
        expect(result.data.defaultCurrency).toBe('EUR');
        expect(result.data.marketplaces).toHaveLength(2);
      }
    });

    it('returns NOT_FOUND for unknown country', () => {
      writeFileSync(TEST_CATALOG_PATH, JSON.stringify(validCatalog));

      const service = new MarketplacesService(TEST_CATALOG_PATH);
      service.initialize();

      const result = service.getCountryConfig('FR');

      expect(result.success).toBe(false);
      expect(result.errorCode).toBe('NOT_FOUND');
    });
  });

  describe('getCatalogVersion', () => {
    it('returns catalog version', () => {
      writeFileSync(TEST_CATALOG_PATH, JSON.stringify(validCatalog));

      const service = new MarketplacesService(TEST_CATALOG_PATH);
      service.initialize();

      const result = service.getCatalogVersion();

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data).toBe(1);
      }
    });

    it('returns error when service not initialized', () => {
      const service = new MarketplacesService('/nonexistent/catalog.json');
      service.initialize();

      const result = service.getCatalogVersion();

      expect(result.success).toBe(false);
      expect(result.errorCode).toBe('CATALOG_ERROR');
    });
  });
});
