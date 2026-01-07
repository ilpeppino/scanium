/**
 * Category Base Pricing Configuration
 *
 * Defines conservative base price ranges for each item category.
 * These ranges represent typical resale values for items in "GOOD"
 * condition with no recognized brand.
 *
 * All prices are in CENTS to avoid floating point issues.
 *
 * Philosophy:
 * - Conservative: Better to underestimate than overestimate
 * - Wide ranges: Acknowledges uncertainty without marketplace data
 * - Category-appropriate: Electronics are worth more than cleaning items
 */

import { CategoryPricing } from './types.js';

/**
 * Category pricing configuration.
 * Base ranges assume: GOOD condition, unknown brand, no packaging.
 */
export const CATEGORY_PRICING: Record<string, CategoryPricing> = {
  // Electronics - Higher value, faster depreciation
  consumer_electronics_portable: {
    id: 'consumer_electronics_portable',
    label: 'Portable Electronics',
    baseRangeCents: [2000, 15000],      // $20-150 for phones, tablets, laptops
    maxCapCents: 100000,                 // $1000 cap
    minFloorCents: 500,                  // $5 minimum
    notes: 'High variance - brand and model matter significantly',
  },
  consumer_electronics_stationary: {
    id: 'consumer_electronics_stationary',
    label: 'Stationary Electronics',
    baseRangeCents: [3000, 20000],      // $30-200 for TVs, monitors
    maxCapCents: 150000,                 // $1500 cap
    minFloorCents: 1000,                 // $10 minimum
    notes: 'Size and resolution affect value significantly',
  },
  home_electronics: {
    id: 'home_electronics',
    label: 'Home Electronics',
    baseRangeCents: [1500, 8000],       // $15-80 for routers, consoles
    maxCapCents: 50000,                  // $500 cap
    minFloorCents: 500,                  // $5 minimum
  },
  electronics_small: {
    id: 'electronics_small',
    label: 'Small Electronics',
    baseRangeCents: [300, 2000],        // $3-20 for cables, chargers
    maxCapCents: 10000,                  // $100 cap
    minFloorCents: 100,                  // $1 minimum
    notes: 'Most items in this category have low resale value',
  },

  // Kitchen & Dining
  kitchen_appliance: {
    id: 'kitchen_appliance',
    label: 'Kitchen Appliance',
    baseRangeCents: [1000, 5000],       // $10-50 for small appliances
    maxCapCents: 30000,                  // $300 cap
    minFloorCents: 300,                  // $3 minimum
  },
  drinkware: {
    id: 'drinkware',
    label: 'Drinkware',
    baseRangeCents: [100, 800],         // $1-8 per piece
    maxCapCents: 5000,                   // $50 cap (for premium items)
    minFloorCents: 50,                   // $0.50 minimum
    notes: 'Value depends on material and brand',
  },
  tableware: {
    id: 'tableware',
    label: 'Tableware',
    baseRangeCents: [100, 600],         // $1-6 per piece
    maxCapCents: 5000,                   // $50 cap
    minFloorCents: 50,                   // $0.50 minimum
  },
  cutlery: {
    id: 'cutlery',
    label: 'Cutlery',
    baseRangeCents: [50, 300],          // $0.50-3 per piece
    maxCapCents: 3000,                   // $30 cap (for sets)
    minFloorCents: 25,                   // $0.25 minimum
  },
  food_container: {
    id: 'food_container',
    label: 'Food Container',
    baseRangeCents: [100, 500],         // $1-5 per container
    maxCapCents: 3000,                   // $30 cap
    minFloorCents: 50,                   // $0.50 minimum
  },

  // Home & Living
  textile: {
    id: 'textile',
    label: 'Household Textile',
    baseRangeCents: [200, 1500],        // $2-15 for towels, blankets
    maxCapCents: 10000,                  // $100 cap
    minFloorCents: 100,                  // $1 minimum
    notes: 'Condition matters significantly for textiles',
  },
  cleaning_item: {
    id: 'cleaning_item',
    label: 'Cleaning Item',
    baseRangeCents: [100, 500],         // $1-5 for basic items
    maxCapCents: 5000,                   // $50 cap (for vacuums)
    minFloorCents: 50,                   // $0.50 minimum
    notes: 'Most cleaning items have low resale value',
  },
  decor: {
    id: 'decor',
    label: 'Decor',
    baseRangeCents: [200, 2000],        // $2-20 for decorative items
    maxCapCents: 15000,                  // $150 cap
    minFloorCents: 100,                  // $1 minimum
    notes: 'High variance - style and condition matter',
  },
  storage: {
    id: 'storage',
    label: 'Storage & Organization',
    baseRangeCents: [200, 1000],        // $2-10 for storage items
    maxCapCents: 5000,                   // $50 cap
    minFloorCents: 100,                  // $1 minimum
  },
  plant: {
    id: 'plant',
    label: 'Plant',
    baseRangeCents: [300, 2000],        // $3-20 for houseplants
    maxCapCents: 10000,                  // $100 cap
    minFloorCents: 200,                  // $2 minimum
    notes: 'Size and rarity affect value',
  },
  furniture: {
    id: 'furniture',
    label: 'Furniture',
    baseRangeCents: [2000, 10000],      // $20-100 for basic furniture
    maxCapCents: 100000,                 // $1000 cap
    minFloorCents: 500,                  // $5 minimum
    notes: 'Size, material, and brand significantly affect value',
  },
};

/**
 * Default pricing for unknown categories.
 */
export const DEFAULT_CATEGORY_PRICING: CategoryPricing = {
  id: 'unknown',
  label: 'General Item',
  baseRangeCents: [300, 2000],          // $3-20 default range
  maxCapCents: 20000,                    // $200 cap
  minFloorCents: 100,                    // $1 minimum
  notes: 'Category not recognized - using conservative defaults',
};

/**
 * Get pricing configuration for a category.
 */
export function getCategoryPricing(categoryId: string | undefined): CategoryPricing {
  if (!categoryId) {
    return DEFAULT_CATEGORY_PRICING;
  }
  return CATEGORY_PRICING[categoryId] || DEFAULT_CATEGORY_PRICING;
}

/**
 * Get all category IDs for validation.
 */
export function getAllCategoryIds(): string[] {
  return Object.keys(CATEGORY_PRICING);
}
