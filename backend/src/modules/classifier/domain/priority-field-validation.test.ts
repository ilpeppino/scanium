import { describe, it, expect } from 'vitest';
import homeResalePack from './home-resale.json' assert { type: 'json' };
import { mapSignalsToDomainCategory } from './mapper.js';
import type { DomainPack } from './domain-pack.js';

/**
 * Tests to validate that the priority field from home-resale.json
 * is correctly used for tier-based selection.
 *
 * CRITICAL: The JSON uses "priority" but the TypeScript type defines "priorityTier".
 * This test suite will expose if there's a field name mismatch causing
 * all categories to default to the same tier.
 */

describe('Priority Field Validation', () => {
  it('JSON categories have priority field defined', () => {
    homeResalePack.categories.forEach((category) => {
      expect(category).toHaveProperty('priority');
      expect(category.priority).toBeDefined();
      expect(typeof category.priority).toBe('number');
    });
  });

  it('priority values vary across categories (not all default)', () => {
    const priorities = homeResalePack.categories.map((cat) => cat.priority);
    const uniquePriorities = new Set(priorities);

    // Should have multiple different priority values
    expect(uniquePriorities.size).toBeGreaterThan(1);
  });

  it('drinkware has higher priority than furniture in JSON', () => {
    const drinkware = homeResalePack.categories.find((cat) => cat.id === 'drinkware');
    const furniture = homeResalePack.categories.find((cat) => cat.id === 'furniture');

    expect(drinkware).toBeDefined();
    expect(furniture).toBeDefined();
    expect(drinkware!.priority).toBeGreaterThan(furniture!.priority);
  });

  /**
   * CRITICAL TEST: This validates that priority-based selection actually works.
   *
   * When "mug" (drinkware, priority 10) and "table" (furniture, priority 2)
   * both match with similar scores, drinkware MUST win due to higher priority.
   *
   * If this test fails, it likely means:
   * 1. The "priority" field from JSON is not being read as "priorityTier"
   * 2. All categories default to the same tier
   * 3. Selection becomes score-based only (highest score wins regardless of priority)
   */
  it('priority-based selection works correctly (drinkware beats furniture)', () => {
    const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
      labels: [
        { description: 'wooden table', score: 0.95 }, // High score, low priority
        { description: 'coffee mug', score: 0.65 },   // Lower score, HIGH priority
      ],
    });

    // Drinkware MUST win due to priority 10 vs furniture priority 2
    expect(result.domainCategoryId).toBe('drinkware');
    expect(result.confidence).toBeCloseTo(0.65, 2);

    // If this fails and returns 'furniture', the priority system is broken
    if (result.domainCategoryId === 'furniture') {
      console.error(
        '❌ PRIORITY SYSTEM FAILURE: Furniture (low priority, high score) ' +
        'won over drinkware (high priority, lower score). ' +
        'Check if JSON "priority" field is being read as "priorityTier".'
      );
    }
  });

  it('priority-based selection with threshold enforced', () => {
    const result = mapSignalsToDomainCategory(homeResalePack as DomainPack, {
      labels: [
        { description: 'dining table', score: 0.9 },   // furniture
        { description: 'ceramic plate', score: 0.45 }, // tableware (priority 9)
      ],
    });

    // Tableware (priority 9) should beat furniture (priority 2) even with lower score
    expect(result.domainCategoryId).toBe('tableware');
    expect(result.confidence).toBeCloseTo(0.45, 2);
  });

  it('priority ordering matches expected hierarchy', () => {
    const drinkware = homeResalePack.categories.find((cat) => cat.id === 'drinkware');
    const tableware = homeResalePack.categories.find((cat) => cat.id === 'tableware');
    const kitchen = homeResalePack.categories.find((cat) => cat.id === 'kitchen_appliance');
    const decor = homeResalePack.categories.find((cat) => cat.id === 'decor');
    const furniture = homeResalePack.categories.find((cat) => cat.id === 'furniture');

    // Specific items should have higher priority than generic furniture
    expect(drinkware!.priority).toBeGreaterThan(furniture!.priority);
    expect(tableware!.priority).toBeGreaterThan(furniture!.priority);
    expect(kitchen!.priority).toBeGreaterThan(furniture!.priority);
    expect(decor!.priority).toBeGreaterThan(furniture!.priority);

    // Drinkware should be highest priority
    expect(drinkware!.priority).toBe(10);

    // Furniture should be lowest priority
    expect(furniture!.priority).toBe(2);
  });

  it('field name in JSON matches TypeScript type expectations', () => {
    // This test documents the expected field name
    // TypeScript type defines: priorityTier?: number
    // JSON uses: priority: number
    //
    // If there's a mismatch, the mapper will use DEFAULT_PRIORITY_TIER (2) for all categories

    const category = homeResalePack.categories[0];

    // Check what field exists
    const hasPriority = 'priority' in category;
    const hasPriorityTier = 'priorityTier' in category;

    console.log(`JSON field check: priority=${hasPriority}, priorityTier=${hasPriorityTier}`);

    // Document the actual field name used
    expect(hasPriority).toBe(true);

    // If priorityTier doesn't exist, this is the mismatch
    if (!hasPriorityTier) {
      console.warn(
        '⚠️  Field name mismatch detected: JSON uses "priority" but ' +
        'TypeScript type expects "priorityTier". All categories may default to tier 2.'
      );
    }
  });
});
