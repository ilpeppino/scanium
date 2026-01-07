/**
 * Pricing Estimator Tests
 *
 * Acceptance tests for the baseline price estimation algorithm.
 * Tests the deterministic, explainable pricing logic.
 */

import { describe, it, expect } from 'vitest';
import { estimatePrice, formatPriceRange } from './estimator.js';
import { PriceEstimateInput } from './types.js';

describe('Pricing Estimator', () => {
  describe('Basic Functionality', () => {
    it('produces a price range (not a single value)', () => {
      const input: PriceEstimateInput = {
        itemId: 'test-1',
        category: 'consumer_electronics_portable',
      };

      const result = estimatePrice(input);

      expect(result.priceEstimateMinCents).toBeLessThanOrEqual(result.priceEstimateMaxCents);
      expect(result.priceEstimateMinCents).toBeGreaterThan(0);
      expect(result.priceEstimateMaxCents).toBeGreaterThan(0);
    });

    it('produces a confidence level (LOW|MEDIUM|HIGH)', () => {
      const input: PriceEstimateInput = {
        itemId: 'test-2',
        category: 'furniture',
      };

      const result = estimatePrice(input);

      expect(['LOW', 'MEDIUM', 'HIGH']).toContain(result.confidence);
    });

    it('produces human-readable explanation', () => {
      const input: PriceEstimateInput = {
        itemId: 'test-3',
        category: 'drinkware',
        brand: 'LE CREUSET',
      };

      const result = estimatePrice(input);

      expect(result.explanation).toBeInstanceOf(Array);
      expect(result.explanation.length).toBeGreaterThan(0);
      expect(result.explanation.some((line) => line.includes('$'))).toBe(true);
    });

    it('includes input summary in result', () => {
      const input: PriceEstimateInput = {
        itemId: 'test-4',
        category: 'kitchen_appliance',
        brand: 'KITCHENAID',
        condition: 'GOOD',
      };

      const result = estimatePrice(input);

      expect(result.inputSummary).toBeDefined();
      expect(result.inputSummary.category).toBe('kitchen_appliance');
      expect(result.inputSummary.brand).toBe('KITCHENAID');
      expect(result.inputSummary.brandTier).toBe('PREMIUM');
      expect(result.inputSummary.condition).toBe('GOOD');
    });
  });

  describe('Category-Based Pricing', () => {
    it('electronics have higher base prices than household items', () => {
      const electronics: PriceEstimateInput = {
        itemId: 'elec-1',
        category: 'consumer_electronics_portable',
      };

      const household: PriceEstimateInput = {
        itemId: 'house-1',
        category: 'drinkware',
      };

      const elecResult = estimatePrice(electronics);
      const houseResult = estimatePrice(household);

      expect(elecResult.priceEstimateMaxCents).toBeGreaterThan(houseResult.priceEstimateMaxCents);
    });

    it('uses conservative defaults for unknown categories', () => {
      const known: PriceEstimateInput = {
        itemId: 'known-1',
        category: 'furniture',
      };

      const unknown: PriceEstimateInput = {
        itemId: 'unknown-1',
        category: 'unknown_category_xyz',
      };

      const knownResult = estimatePrice(known);
      const unknownResult = estimatePrice(unknown);

      // Unknown categories should have caveats
      expect(unknownResult.caveats).toBeDefined();
      expect(unknownResult.caveats?.some((c) => c.includes('not recognized'))).toBe(true);

      // Unknown categories should use conservative defaults
      expect(unknownResult.inputSummary.categoryLabel).toBe('General Item');
    });

    it('respects category max caps', () => {
      const input: PriceEstimateInput = {
        itemId: 'cap-1',
        category: 'electronics_small',
        brand: 'LOUIS VUITTON', // Luxury brand to push price up
        condition: 'NEW_SEALED',
        completeness: {
          hasOriginalBox: true,
          isSealed: true,
          hasTags: true,
        },
      };

      const result = estimatePrice(input);

      // electronics_small has a $100 cap (10000 cents)
      expect(result.priceEstimateMaxCents).toBeLessThanOrEqual(10000);
    });

    it('respects category min floors', () => {
      const input: PriceEstimateInput = {
        itemId: 'floor-1',
        category: 'cutlery',
        condition: 'POOR',
      };

      const result = estimatePrice(input);

      // cutlery has a $0.25 floor (25 cents)
      expect(result.priceEstimateMinCents).toBeGreaterThanOrEqual(25);
    });
  });

  describe('Brand Tier Adjustments', () => {
    it('luxury brands increase price significantly', () => {
      const nobrandom: PriceEstimateInput = {
        itemId: 'nobrand-1',
        category: 'textile',
      };

      const luxury: PriceEstimateInput = {
        itemId: 'luxury-1',
        category: 'textile',
        brand: 'LOUIS VUITTON',
      };

      const noBrandResult = estimatePrice(nobrandom);
      const luxuryResult = estimatePrice(luxury);

      // Luxury should be at least 2x the price
      expect(luxuryResult.priceEstimateMaxCents).toBeGreaterThan(
        noBrandResult.priceEstimateMaxCents * 2
      );
    });

    it('premium brands hold value better than budget', () => {
      const premium: PriceEstimateInput = {
        itemId: 'premium-1',
        category: 'consumer_electronics_portable',
        brand: 'APPLE',
      };

      const budget: PriceEstimateInput = {
        itemId: 'budget-1',
        category: 'consumer_electronics_portable',
        brand: 'TCL',
      };

      const premiumResult = estimatePrice(premium);
      const budgetResult = estimatePrice(budget);

      expect(premiumResult.priceEstimateMaxCents).toBeGreaterThan(budgetResult.priceEstimateMaxCents);
    });

    it('unknown brands use conservative multipliers', () => {
      const known: PriceEstimateInput = {
        itemId: 'known-brand-1',
        category: 'kitchen_appliance',
        brand: 'KITCHENAID',
      };

      const unknown: PriceEstimateInput = {
        itemId: 'unknown-brand-1',
        category: 'kitchen_appliance',
        brand: 'RandomBrandXYZ',
      };

      const knownResult = estimatePrice(known);
      const unknownResult = estimatePrice(unknown);

      // Unknown brand should have lower max price than known premium brand
      expect(unknownResult.priceEstimateMaxCents).toBeLessThan(knownResult.priceEstimateMaxCents);

      // Unknown brand should have caveat
      expect(unknownResult.caveats?.some((c) => c.includes('not in our database'))).toBe(true);
    });
  });

  describe('Condition Modifiers', () => {
    it('NEW_SEALED has highest value', () => {
      const newSealed: PriceEstimateInput = {
        itemId: 'new-sealed-1',
        category: 'consumer_electronics_portable',
        condition: 'NEW_SEALED',
      };

      const good: PriceEstimateInput = {
        itemId: 'good-1',
        category: 'consumer_electronics_portable',
        condition: 'GOOD',
      };

      const newResult = estimatePrice(newSealed);
      const goodResult = estimatePrice(good);

      expect(newResult.priceEstimateMinCents).toBeGreaterThan(goodResult.priceEstimateMinCents);
    });

    it('POOR condition significantly reduces value', () => {
      const good: PriceEstimateInput = {
        itemId: 'good-2',
        category: 'furniture',
        condition: 'GOOD',
      };

      const poor: PriceEstimateInput = {
        itemId: 'poor-1',
        category: 'furniture',
        condition: 'POOR',
      };

      const goodResult = estimatePrice(good);
      const poorResult = estimatePrice(poor);

      // Poor should be less than half of good
      expect(poorResult.priceEstimateMaxCents).toBeLessThan(goodResult.priceEstimateMaxCents * 0.5);
    });

    it('defaults to GOOD when condition not specified', () => {
      const noCondition: PriceEstimateInput = {
        itemId: 'no-cond-1',
        category: 'decor',
      };

      const goodCondition: PriceEstimateInput = {
        itemId: 'good-cond-1',
        category: 'decor',
        condition: 'GOOD',
      };

      const noCondResult = estimatePrice(noCondition);
      const goodCondResult = estimatePrice(goodCondition);

      // Should be the same
      expect(noCondResult.priceEstimateMinCents).toBe(goodCondResult.priceEstimateMinCents);
      expect(noCondResult.priceEstimateMaxCents).toBe(goodCondResult.priceEstimateMaxCents);
    });
  });

  describe('Completeness Bonuses', () => {
    it('original box increases max price', () => {
      const noBox: PriceEstimateInput = {
        itemId: 'no-box-1',
        category: 'consumer_electronics_portable',
      };

      const withBox: PriceEstimateInput = {
        itemId: 'with-box-1',
        category: 'consumer_electronics_portable',
        completeness: {
          hasOriginalBox: true,
        },
      };

      const noBoxResult = estimatePrice(noBox);
      const withBoxResult = estimatePrice(withBox);

      expect(withBoxResult.priceEstimateMaxCents).toBeGreaterThan(noBoxResult.priceEstimateMaxCents);
    });

    it('sealed items get premium', () => {
      const notSealed: PriceEstimateInput = {
        itemId: 'not-sealed-1',
        category: 'kitchen_appliance',
      };

      const sealed: PriceEstimateInput = {
        itemId: 'sealed-1',
        category: 'kitchen_appliance',
        completeness: {
          isSealed: true,
        },
      };

      const notSealedResult = estimatePrice(notSealed);
      const sealedResult = estimatePrice(sealed);

      expect(sealedResult.priceEstimateMaxCents).toBeGreaterThan(notSealedResult.priceEstimateMaxCents);
    });

    it('calculates completeness score', () => {
      const input: PriceEstimateInput = {
        itemId: 'complete-1',
        category: 'consumer_electronics_portable',
        completeness: {
          hasOriginalBox: true,
          hasTags: true,
          isSealed: true,
          hasAccessories: true,
          hasDocumentation: true,
        },
      };

      const result = estimatePrice(input);

      expect(result.inputSummary.completenessScore).toBe(100);
    });
  });

  describe('Confidence Calculation', () => {
    it('HIGH confidence with full attribute coverage', () => {
      const input: PriceEstimateInput = {
        itemId: 'high-conf-1',
        category: 'consumer_electronics_portable',
        brand: 'APPLE',
        brandConfidence: 'HIGH',
        condition: 'GOOD',
        productType: 'smartphone',
        completeness: {
          hasOriginalBox: true,
        },
      };

      const result = estimatePrice(input);

      expect(result.confidence).toBe('HIGH');
    });

    it('LOW confidence with minimal info', () => {
      const input: PriceEstimateInput = {
        itemId: 'low-conf-1',
      };

      const result = estimatePrice(input);

      expect(result.confidence).toBe('LOW');
    });

    it('MEDIUM confidence with partial info', () => {
      const input: PriceEstimateInput = {
        itemId: 'med-conf-1',
        category: 'furniture',
        brand: 'IKEA',
      };

      const result = estimatePrice(input);

      expect(result.confidence).toBe('MEDIUM');
    });
  });

  describe('Explanation Generation', () => {
    it('includes price range in explanation', () => {
      const input: PriceEstimateInput = {
        itemId: 'expl-1',
        category: 'drinkware',
      };

      const result = estimatePrice(input);

      expect(result.explanation.some((line) => line.includes('Estimated resale value'))).toBe(true);
    });

    it('includes category in explanation', () => {
      const input: PriceEstimateInput = {
        itemId: 'expl-2',
        category: 'kitchen_appliance',
      };

      const result = estimatePrice(input);

      expect(result.explanation.some((line) => line.includes('Kitchen Appliance'))).toBe(true);
    });

    it('includes disclaimer about estimate nature', () => {
      const input: PriceEstimateInput = {
        itemId: 'expl-3',
        category: 'textile',
      };

      const result = estimatePrice(input);

      expect(result.explanation.some((line) => line.includes('estimate'))).toBe(true);
    });
  });

  describe('Edge Cases', () => {
    it('handles empty input gracefully', () => {
      const input: PriceEstimateInput = {
        itemId: 'empty-1',
      };

      const result = estimatePrice(input);

      expect(result.priceEstimateMinCents).toBeGreaterThan(0);
      expect(result.priceEstimateMaxCents).toBeGreaterThan(0);
      expect(result.confidence).toBe('LOW');
    });

    it('handles all categories', () => {
      const categories = [
        'drinkware',
        'tableware',
        'cutlery',
        'kitchen_appliance',
        'food_container',
        'cleaning_item',
        'textile',
        'consumer_electronics_portable',
        'consumer_electronics_stationary',
        'home_electronics',
        'electronics_small',
        'decor',
        'plant',
        'storage',
        'furniture',
      ];

      for (const category of categories) {
        const input: PriceEstimateInput = {
          itemId: `cat-${category}`,
          category,
        };

        const result = estimatePrice(input);

        expect(result.priceEstimateMinCents).toBeGreaterThan(0);
        expect(result.priceEstimateMaxCents).toBeGreaterThan(0);
        expect(result.inputSummary.category).toBe(category);
      }
    });

    it('handles all condition levels', () => {
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
        const input: PriceEstimateInput = {
          itemId: `cond-${condition}`,
          category: 'furniture',
          condition: condition as PriceEstimateInput['condition'],
        };

        const result = estimatePrice(input);

        expect(result.priceEstimateMinCents).toBeGreaterThan(0);
        expect(result.inputSummary.condition).toBe(condition);
      }
    });
  });

  describe('Price Formatting', () => {
    it('formats price range correctly', () => {
      expect(formatPriceRange(1000, 5000)).toBe('$10 - $50');
      expect(formatPriceRange(500, 500)).toBe('$5');
      expect(formatPriceRange(99, 199)).toBe('$1 - $2');
    });
  });

  describe('Determinism', () => {
    it('produces same result for same input', () => {
      const input: PriceEstimateInput = {
        itemId: 'determinism-1',
        category: 'consumer_electronics_portable',
        brand: 'APPLE',
        condition: 'LIKE_NEW',
        completeness: {
          hasOriginalBox: true,
        },
      };

      const result1 = estimatePrice(input);
      const result2 = estimatePrice(input);

      expect(result1.priceEstimateMinCents).toBe(result2.priceEstimateMinCents);
      expect(result1.priceEstimateMaxCents).toBe(result2.priceEstimateMaxCents);
      expect(result1.confidence).toBe(result2.confidence);
    });
  });
});
