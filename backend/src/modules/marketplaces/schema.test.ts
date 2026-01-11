import { describe, expect, it } from 'vitest';
import {
  marketplaceSchema,
  countryConfigSchema,
  marketplacesCatalogSchema,
} from './schema.js';

describe('Marketplaces Schema Validation', () => {
  describe('marketplaceSchema', () => {
    it('accepts valid marketplace', () => {
      const valid = {
        id: 'amazon',
        name: 'Amazon',
        domains: ['amazon.de'],
        type: 'global',
      };

      const result = marketplaceSchema.safeParse(valid);
      expect(result.success).toBe(true);
    });

    it('rejects marketplace with missing required fields', () => {
      const invalid = {
        id: 'amazon',
        name: 'Amazon',
        // missing domains
        type: 'global',
      };

      const result = marketplaceSchema.safeParse(invalid);
      expect(result.success).toBe(false);
    });

    it('rejects invalid marketplace type', () => {
      const invalid = {
        id: 'amazon',
        name: 'Amazon',
        domains: ['amazon.de'],
        type: 'invalid_type',
      };

      const result = marketplaceSchema.safeParse(invalid);
      expect(result.success).toBe(false);
    });

    it('accepts all valid marketplace types', () => {
      const types = ['global', 'marketplace', 'classifieds'];

      types.forEach((type) => {
        const valid = {
          id: 'test',
          name: 'Test',
          domains: ['test.com'],
          type,
        };

        const result = marketplaceSchema.safeParse(valid);
        expect(result.success).toBe(true);
      });
    });
  });

  describe('countryConfigSchema', () => {
    it('accepts valid country config', () => {
      const valid = {
        code: 'NL',
        defaultCurrency: 'EUR',
        marketplaces: [
          {
            id: 'bol',
            name: 'Bol.com',
            domains: ['bol.com'],
            type: 'marketplace',
          },
        ],
      };

      const result = countryConfigSchema.safeParse(valid);
      expect(result.success).toBe(true);
    });

    it('uppercases country code', () => {
      const input = {
        code: 'nl',
        defaultCurrency: 'EUR',
        marketplaces: [
          {
            id: 'bol',
            name: 'Bol.com',
            domains: ['bol.com'],
            type: 'marketplace',
          },
        ],
      };

      const result = countryConfigSchema.safeParse(input);
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.code).toBe('NL');
      }
    });

    it('rejects country code not 2 characters', () => {
      const invalid = {
        code: 'USA',
        defaultCurrency: 'USD',
        marketplaces: [
          {
            id: 'amazon',
            name: 'Amazon',
            domains: ['amazon.com'],
            type: 'global',
          },
        ],
      };

      const result = countryConfigSchema.safeParse(invalid);
      expect(result.success).toBe(false);
    });

    it('rejects empty marketplaces array', () => {
      const invalid = {
        code: 'NL',
        defaultCurrency: 'EUR',
        marketplaces: [],
      };

      const result = countryConfigSchema.safeParse(invalid);
      expect(result.success).toBe(false);
    });
  });

  describe('marketplacesCatalogSchema', () => {
    it('accepts valid catalog', () => {
      const valid = {
        version: 1,
        countries: [
          {
            code: 'NL',
            defaultCurrency: 'EUR',
            marketplaces: [
              {
                id: 'bol',
                name: 'Bol.com',
                domains: ['bol.com'],
                type: 'marketplace',
              },
            ],
          },
        ],
      };

      const result = marketplacesCatalogSchema.safeParse(valid);
      expect(result.success).toBe(true);
    });

    it('rejects catalog with invalid version', () => {
      const invalid = {
        version: 'v1', // should be number
        countries: [
          {
            code: 'NL',
            defaultCurrency: 'EUR',
            marketplaces: [
              {
                id: 'bol',
                name: 'Bol.com',
                domains: ['bol.com'],
                type: 'marketplace',
              },
            ],
          },
        ],
      };

      const result = marketplacesCatalogSchema.safeParse(invalid);
      expect(result.success).toBe(false);
    });

    it('rejects catalog with empty countries array', () => {
      const invalid = {
        version: 1,
        countries: [],
      };

      const result = marketplacesCatalogSchema.safeParse(invalid);
      expect(result.success).toBe(false);
    });

    it('validates real marketplaces.eu.json structure', () => {
      const realCatalog = {
        version: 1,
        countries: [
          {
            code: 'NL',
            defaultCurrency: 'EUR',
            marketplaces: [
              {
                id: 'bol',
                name: 'Bol.com',
                domains: ['bol.com'],
                type: 'marketplace',
              },
              {
                id: 'marktplaats',
                name: 'Marktplaats',
                domains: ['marktplaats.nl'],
                type: 'classifieds',
              },
              {
                id: 'amazon',
                name: 'Amazon',
                domains: ['amazon.nl'],
                type: 'global',
              },
            ],
          },
          {
            code: 'DE',
            defaultCurrency: 'EUR',
            marketplaces: [
              {
                id: 'amazon',
                name: 'Amazon',
                domains: ['amazon.de'],
                type: 'global',
              },
              {
                id: 'ebay',
                name: 'eBay',
                domains: ['ebay.de'],
                type: 'global',
              },
            ],
          },
        ],
      };

      const result = marketplacesCatalogSchema.safeParse(realCatalog);
      expect(result.success).toBe(true);
    });
  });
});
