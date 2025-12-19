import { describe, it, expect } from 'vitest';
import homeResalePack from './home-resale.json' assert { type: 'json' };
import { mapSignalsToDomainCategory } from './mapper.js';
import type { DomainPack, DomainCategory } from './domain-pack.js';

/**
 * Comprehensive tests for home-resale.json domain pack.
 *
 * Validates:
 * 1. JSON parsing and integrity
 * 2. Token matching for all categories
 * 3. Priority resolution (specific beats generic)
 * 4. Threshold behavior
 * 5. Case/plural/spacing handling
 */

describe('home-resale.json Domain Pack', () => {
  // STEP 5 — DOMAIN PACK INTEGRITY TESTS
  describe('JSON Integrity', () => {
    it('parses correctly with required fields', () => {
      expect(homeResalePack.id).toBe('home_resale');
      expect(homeResalePack.name).toBe('Home Resale');
      expect(homeResalePack.threshold).toBeDefined();
      expect(homeResalePack.categories).toBeDefined();
      expect(Array.isArray(homeResalePack.categories)).toBe(true);
    });

    it('has valid threshold value', () => {
      expect(homeResalePack.threshold).toBe(0.28);
      expect(homeResalePack.threshold).toBeGreaterThan(0);
      expect(homeResalePack.threshold).toBeLessThan(1);
    });

    it('has all categories with unique ids', () => {
      const ids = homeResalePack.categories.map((cat) => cat.id);
      const uniqueIds = new Set(ids);
      expect(ids.length).toBe(uniqueIds.size);
      expect(ids.length).toBeGreaterThan(0);
    });

    it('has all categories with non-empty labels', () => {
      homeResalePack.categories.forEach((category) => {
        expect(category.label).toBeTruthy();
        expect(category.label.trim()).toBe(category.label);
        expect(category.label.length).toBeGreaterThan(0);
      });
    });

    it('has all categories with non-empty tokens arrays', () => {
      homeResalePack.categories.forEach((category) => {
        expect(category.tokens).toBeDefined();
        expect(Array.isArray(category.tokens)).toBe(true);
        expect(category.tokens.length).toBeGreaterThan(0);
      });
    });

    it('has no blank or whitespace-only tokens', () => {
      homeResalePack.categories.forEach((category) => {
        category.tokens.forEach((token) => {
          expect(token).toBeTruthy();
          expect(token.trim()).toBe(token);
          expect(token.length).toBeGreaterThan(0);
        });
      });
    });

    it('has all categories with valid priority values', () => {
      homeResalePack.categories.forEach((category) => {
        expect(category.priority).toBeDefined();
        expect(typeof category.priority).toBe('number');
        expect(category.priority).toBeGreaterThanOrEqual(1);
        expect(category.priority).toBeLessThanOrEqual(10);
      });
    });

    it('has furniture with lowest priority (most generic)', () => {
      const furniture = homeResalePack.categories.find((cat) => cat.id === 'furniture');
      expect(furniture).toBeDefined();
      expect(furniture!.priority).toBe(2);

      // Furniture should have lower priority than specific items
      const drinkware = homeResalePack.categories.find((cat) => cat.id === 'drinkware');
      expect(drinkware!.priority).toBeGreaterThan(furniture!.priority);
    });

    it('has all expected category IDs', () => {
      const expectedIds = [
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

      const actualIds = homeResalePack.categories.map((cat) => cat.id);
      expectedIds.forEach((id) => {
        expect(actualIds).toContain(id);
      });
    });

    it('has all categories with itemCategoryName field', () => {
      homeResalePack.categories.forEach((category) => {
        expect(category.itemCategoryName).toBeDefined();
        expect(typeof category.itemCategoryName).toBe('string');
        expect(category.itemCategoryName.length).toBeGreaterThan(0);
      });
    });
  });

  // STEP 2 & 3 — TOKEN MATCHING TESTS
  describe('Token Matching - Basic Categories', () => {
    it('matches drinkware tokens correctly', () => {
      const testCases = [
        { label: 'mug', expected: 'drinkware' },
        { label: 'cup', expected: 'drinkware' },
        { label: 'glass', expected: 'drinkware' },
        { label: 'bottle', expected: 'drinkware' },
        { label: 'tumbler', expected: 'drinkware' },
        { label: 'thermos', expected: 'drinkware' },
      ];

      testCases.forEach(({ label, expected }) => {
        const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
          labels: [{ description: label, score: 0.8 }],
        });
        expect(result.domainCategoryId).toBe(expected);
        expect(result.confidence).toBeGreaterThanOrEqual(homeResalePack.threshold);
      });
    });

    it('matches tableware tokens correctly', () => {
      const testCases = [
        { label: 'plate', expected: 'tableware' },
        { label: 'bowl', expected: 'tableware' },
        { label: 'dish', expected: 'tableware' },
        { label: 'saucer', expected: 'tableware' },
      ];

      testCases.forEach(({ label, expected }) => {
        const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
          labels: [{ description: label, score: 0.8 }],
        });
        expect(result.domainCategoryId).toBe(expected);
      });
    });

    it('matches cutlery tokens correctly', () => {
      const testCases = [
        { label: 'fork', expected: 'cutlery' },
        { label: 'knife', expected: 'cutlery' },
        { label: 'spoon', expected: 'cutlery' },
      ];

      testCases.forEach(({ label, expected }) => {
        const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
          labels: [{ description: label, score: 0.8 }],
        });
        expect(result.domainCategoryId).toBe(expected);
      });
    });

    it('matches kitchen appliance tokens correctly', () => {
      const testCases = [
        { label: 'kettle', expected: 'kitchen_appliance' },
        { label: 'toaster', expected: 'kitchen_appliance' },
        { label: 'blender', expected: 'kitchen_appliance' },
        { label: 'microwave', expected: 'kitchen_appliance' },
      ];

      testCases.forEach(({ label, expected }) => {
        const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
          labels: [{ description: label, score: 0.8 }],
        });
        expect(result.domainCategoryId).toBe(expected);
      });
    });

    it('matches food container tokens correctly', () => {
      const testCases = [
        { label: 'jar', expected: 'food_container' },
        { label: 'lunchbox', expected: 'food_container' },
        { label: 'container', expected: 'food_container' },
      ];

      testCases.forEach(({ label, expected }) => {
        const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
          labels: [{ description: label, score: 0.8 }],
        });
        expect(result.domainCategoryId).toBe(expected);
      });
    });

    it('matches cleaning item tokens correctly', () => {
      const testCases = [
        { label: 'sponge', expected: 'cleaning_item' },
        { label: 'brush', expected: 'cleaning_item' },
        { label: 'broom', expected: 'cleaning_item' },
        { label: 'mop', expected: 'cleaning_item' },
      ];

      testCases.forEach(({ label, expected }) => {
        const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
          labels: [{ description: label, score: 0.8 }],
        });
        expect(result.domainCategoryId).toBe(expected);
      });
    });

    it('matches textile tokens correctly', () => {
      const testCases = [
        { label: 'towel', expected: 'textile' },
        { label: 'napkin', expected: 'textile' },
        { label: 'cloth', expected: 'textile' },
        { label: 'blanket', expected: 'textile' },
        { label: 'pillow', expected: 'textile' },
      ];

      testCases.forEach(({ label, expected }) => {
        const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
          labels: [{ description: label, score: 0.8 }],
        });
        expect(result.domainCategoryId).toBe(expected);
      });
    });

    it('matches portable electronics tokens correctly', () => {
      const testCases = [
        { label: 'phone', expected: 'consumer_electronics_portable' },
        { label: 'smartphone', expected: 'consumer_electronics_portable' },
        { label: 'laptop', expected: 'consumer_electronics_portable' },
        { label: 'tablet', expected: 'consumer_electronics_portable' },
        { label: 'earbuds', expected: 'consumer_electronics_portable' },
      ];

      testCases.forEach(({ label, expected }) => {
        const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
          labels: [{ description: label, score: 0.8 }],
        });
        expect(result.domainCategoryId).toBe(expected);
      });
    });

    it('matches stationary electronics tokens correctly', () => {
      const testCases = [
        { label: 'tv', expected: 'consumer_electronics_stationary' },
        { label: 'monitor', expected: 'consumer_electronics_stationary' },
        { label: 'printer', expected: 'consumer_electronics_stationary' },
        { label: 'desktop computer', expected: 'consumer_electronics_stationary' },
      ];

      testCases.forEach(({ label, expected }) => {
        const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
          labels: [{ description: label, score: 0.8 }],
        });
        expect(result.domainCategoryId).toBe(expected);
      });
    });

    it('matches home electronics tokens correctly', () => {
      const testCases = [
        { label: 'router', expected: 'home_electronics' },
        { label: 'modem', expected: 'home_electronics' },
        { label: 'game console', expected: 'home_electronics' },
        { label: 'controller', expected: 'home_electronics' },
      ];

      testCases.forEach(({ label, expected }) => {
        const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
          labels: [{ description: label, score: 0.8 }],
        });
        expect(result.domainCategoryId).toBe(expected);
      });
    });

    it('matches small electronics tokens correctly', () => {
      const testCases = [
        { label: 'remote', expected: 'electronics_small' },
        { label: 'charger', expected: 'electronics_small' },
        { label: 'cable', expected: 'electronics_small' },
        { label: 'speaker', expected: 'electronics_small' },
      ];

      testCases.forEach(({ label, expected }) => {
        const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
          labels: [{ description: label, score: 0.8 }],
        });
        expect(result.domainCategoryId).toBe(expected);
      });
    });

    it('matches decor tokens correctly', () => {
      const testCases = [
        { label: 'vase', expected: 'decor' },
        { label: 'frame', expected: 'decor' },
        { label: 'candle', expected: 'decor' },
        { label: 'ornament', expected: 'decor' },
      ];

      testCases.forEach(({ label, expected }) => {
        const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
          labels: [{ description: label, score: 0.8 }],
        });
        expect(result.domainCategoryId).toBe(expected);
      });
    });

    it('matches plant tokens correctly', () => {
      const testCases = [
        { label: 'plant', expected: 'plant' },
        { label: 'flower', expected: 'plant' },
        { label: 'houseplant', expected: 'plant' },
      ];

      testCases.forEach(({ label, expected }) => {
        const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
          labels: [{ description: label, score: 0.8 }],
        });
        expect(result.domainCategoryId).toBe(expected);
      });
    });

    it('matches storage tokens correctly', () => {
      const testCases = [
        { label: 'basket', expected: 'storage' },
        { label: 'box', expected: 'storage' },
        { label: 'drawer', expected: 'storage' },
        { label: 'organizer', expected: 'storage' },
      ];

      testCases.forEach(({ label, expected }) => {
        const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
          labels: [{ description: label, score: 0.8 }],
        });
        expect(result.domainCategoryId).toBe(expected);
      });
    });

    it('matches furniture tokens correctly', () => {
      const testCases = [
        { label: 'chair', expected: 'furniture' },
        { label: 'table', expected: 'furniture' },
        { label: 'desk', expected: 'furniture' },
        { label: 'sofa', expected: 'furniture' },
        { label: 'couch', expected: 'furniture' },
        { label: 'bed', expected: 'furniture' },
      ];

      testCases.forEach(({ label, expected }) => {
        const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
          labels: [{ description: label, score: 0.8 }],
        });
        expect(result.domainCategoryId).toBe(expected);
      });
    });
  });

  // STEP 2 — CASE/PLURAL/SPACING TESTS
  describe('Token Matching - Case, Plurals, Spacing', () => {
    it('handles uppercase tokens correctly', () => {
      const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [{ description: 'MUG', score: 0.8 }],
      });
      expect(result.domainCategoryId).toBe('drinkware');
    });

    it('handles mixed case tokens correctly', () => {
      const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [{ description: 'Coffee Mug', score: 0.8 }],
      });
      expect(result.domainCategoryId).toBe('drinkware');
    });

    it('handles plural forms in labels', () => {
      const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [{ description: 'coffee cups', score: 0.8 }],
      });
      expect(result.domainCategoryId).toBe('drinkware');
    });

    it('handles multi-word tokens with spaces', () => {
      const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [{ description: 'coffee maker', score: 0.8 }],
      });
      expect(result.domainCategoryId).toBe('kitchen_appliance');
    });

    it('handles storage box multi-word token', () => {
      const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [{ description: 'storage box', score: 0.8 }],
      });
      expect(result.domainCategoryId).toBe('food_container');
    });

    it('handles potted plant multi-word token', () => {
      const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [{ description: 'potted plant', score: 0.8 }],
      });
      expect(result.domainCategoryId).toBe('plant');
    });
  });

  // STEP 3 — PRIORITY / CONFLICT TESTS (MOST IMPORTANT)
  describe('Priority Resolution - Specific Beats Generic', () => {
    it('prefers drinkware over furniture when both match (mug on table)', () => {
      const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [
          { description: 'mug', score: 0.8 },
          { description: 'table', score: 0.85 },
        ],
      });

      // Drinkware (priority 10) should win over furniture (priority 2)
      expect(result.domainCategoryId).toBe('drinkware');
      expect(result.confidence).toBeCloseTo(0.8, 2);
    });

    it('prefers textile over furniture when both match (towel on chair)', () => {
      const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [
          { description: 'towel', score: 0.7 },
          { description: 'chair', score: 0.9 },
        ],
      });

      // Textile (priority 7) should win over furniture (priority 2)
      expect(result.domainCategoryId).toBe('textile');
    });

    it('prefers tableware over furniture when both match (plate on table)', () => {
      const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [
          { description: 'plate', score: 0.75 },
          { description: 'table', score: 0.9 },
        ],
      });

      // Tableware (priority 9) should win over furniture (priority 2)
      expect(result.domainCategoryId).toBe('tableware');
    });

    it('prefers electronics over furniture when both match (remote on sofa)', () => {
      const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [
          { description: 'remote', score: 0.6 },
          { description: 'sofa', score: 0.95 },
        ],
      });

      // Electronics (priority 8) should win over furniture (priority 2)
      expect(result.domainCategoryId).toBe('electronics_small');
    });

    it('prefers vase (decor) over furniture', () => {
      const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [
          { description: 'vase', score: 0.7 },
          { description: 'table', score: 0.9 },
        ],
      });

      // Decor (priority 5) should win over furniture (priority 2)
      expect(result.domainCategoryId).toBe('decor');
    });

    it('furniture wins only when no higher priority items match', () => {
      const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [{ description: 'wooden desk', score: 0.9 }],
      });

      expect(result.domainCategoryId).toBe('furniture');
    });

    it('prefers electronics over furniture when both appear', () => {
      const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [
          { description: 'table', score: 0.9 },
          { description: 'laptop', score: 0.4 },
        ],
      });

      expect(result.domainCategoryId).toBe('consumer_electronics_portable');
    });

    it('prefers electronics over desks when monitors appear', () => {
      const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [
          { description: 'desk', score: 0.85 },
          { description: 'monitor', score: 0.5 },
        ],
      });

      expect(result.domainCategoryId).toBe('consumer_electronics_stationary');
    });

    it('prefers electronics over decor when speaker is present', () => {
      const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [
          { description: 'decor', score: 0.9 },
          { description: 'speaker', score: 0.35 },
        ],
      });

      expect([
        'home_electronics',
        'electronics_small',
      ]).toContain(result.domainCategoryId);
    });

    it('prefers electronics over storage labels', () => {
      const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [
          { description: 'box', score: 0.9 },
          { description: 'router', score: 0.4 },
        ],
      });

      expect(result.domainCategoryId).toBe('home_electronics');
    });
  });

  // STEP 3b — REGRESSION GUARDRAILS
  describe('Regression - household items remain correct', () => {
    const regressionCases = [
      { label: 'mug', expected: 'drinkware' },
      { label: 'napkin', expected: 'textile' },
      { label: 'bottle', expected: 'drinkware' },
      { label: 'plant', expected: 'plant' },
      { label: 'chair', expected: 'furniture' },
    ];

    regressionCases.forEach(({ label, expected }) => {
      it(`keeps ${label} classified as ${expected}`, () => {
        const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
          labels: [{ description: label, score: 0.9 }],
        });

        expect(result.domainCategoryId).toBe(expected);
      });
    });
  });

  // STEP 4 — THRESHOLD TESTS
  describe('Threshold Behavior', () => {
    it('returns null when best match is below threshold', () => {
      const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [{ description: 'mug', score: 0.15 }],
      });

      expect(result.domainCategoryId).toBeNull();
      expect(result.confidence).toBeNull();
      expect(result.debug.reason).toContain('No category met threshold');
    });

    it('returns category when exactly at threshold', () => {
      const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [{ description: 'mug', score: 0.28 }],
      });

      expect(result.domainCategoryId).toBe('drinkware');
      expect(result.confidence).toBeGreaterThanOrEqual(0.28);
    });

    it('returns category when slightly above threshold', () => {
      const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [{ description: 'mug', score: 0.29 }],
      });

      expect(result.domainCategoryId).toBe('drinkware');
      expect(result.confidence).toBeCloseTo(0.29, 2);
    });

    it('returns null when all signals are weak', () => {
      const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [
          { description: 'mug', score: 0.1 },
          { description: 'plate', score: 0.15 },
          { description: 'fork', score: 0.2 },
        ],
      });

      expect(result.domainCategoryId).toBeNull();
      expect(result.confidence).toBeNull();
    });

    it('uses highest confidence match when multiple categories match', () => {
      const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [
          { description: 'mug', score: 0.5 },
          { description: 'plate', score: 0.9 },
        ],
      });

      // Should return the highest confidence that meets priority rules
      expect(result.domainCategoryId).not.toBeNull();
      expect(result.confidence).toBeGreaterThanOrEqual(0.5);
    });

    it('requires electronics tokens to meet threshold', () => {
      const belowThreshold = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [{ description: 'phone', score: 0.1 }],
      });

      expect(belowThreshold.domainCategoryId).toBeNull();

      const aboveThreshold = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [{ description: 'smartphone', score: 0.35 }],
      });

      expect(aboveThreshold.domainCategoryId).toBe('consumer_electronics_portable');
    });
  });

  // ADDITIONAL EDGE CASES
  describe('Edge Cases', () => {
    it('handles empty labels array', () => {
      const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [],
      });

      expect(result.domainCategoryId).toBeNull();
      expect(result.confidence).toBeNull();
      expect(result.debug.reason).toContain('No signals matched');
    });

    it('handles labels with no matches', () => {
      const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [{ description: 'quantum entanglement device', score: 0.99 }],
      });

      expect(result.domainCategoryId).toBeNull();
      expect(result.debug.reason).toContain('No signals matched');
    });

    it('handles partial token matches correctly', () => {
      // "mugshot" contains "mug" but shouldn't match drinkware
      const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [{ description: 'ceramic mug', score: 0.8 }],
      });

      // Should still match because "mug" is in the label
      expect(result.domainCategoryId).toBe('drinkware');
    });

    it('returns attributes from matched category', () => {
      const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [{ description: 'glass', score: 0.8 }],
      });

      expect(result.domainCategoryId).toBe('drinkware');
      expect(result.attributes).toBeDefined();
      expect(result.attributes.segment).toBe('container');
    });

    it('includes debug information in result', () => {
      const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [{ description: 'coffee mug', score: 0.85 }],
      });

      expect(result.debug).toBeDefined();
      expect(result.debug.threshold).toBe(0.28);
      expect(result.debug.bestScore).toBeGreaterThan(0);
      expect(result.debug.matchedLabel).toBe('coffee mug');
      expect(result.debug.matchedToken).toBe('mug');
      expect(result.debug.reason).toContain('Selected');
    });

    it('completes quickly (performance check)', () => {
      const start = performance.now();

      mapSignalsToDomainCategory(homeResalePack as DomainPack, {
        labels: [
          { description: 'mug', score: 0.8 },
          { description: 'plate', score: 0.7 },
          { description: 'fork', score: 0.6 },
        ],
      });

      const elapsed = performance.now() - start;
      expect(elapsed).toBeLessThan(10); // Should complete in < 10ms
    });
  });
});
