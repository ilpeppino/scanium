/**
 * Condition Modifiers
 *
 * Defines how item condition affects pricing.
 * Uses multipliers relative to "GOOD" condition as baseline.
 *
 * Philosophy:
 * - NEW items command premium
 * - GOOD is the baseline (1.0x multiplier)
 * - Poor condition items depreciate significantly
 */

import { ItemCondition, ConditionModifier } from './types.js';

/**
 * Condition modifiers ordered from best to worst.
 *
 * Multipliers are applied to base price ranges.
 */
export const CONDITION_MODIFIERS: Record<ItemCondition, ConditionModifier> = {
  NEW_SEALED: {
    condition: 'NEW_SEALED',
    multiplier: 1.6,
    label: 'New, factory sealed',
  },
  NEW_WITH_TAGS: {
    condition: 'NEW_WITH_TAGS',
    multiplier: 1.4,
    label: 'New with tags',
  },
  NEW_WITHOUT_TAGS: {
    condition: 'NEW_WITHOUT_TAGS',
    multiplier: 1.2,
    label: 'New without tags',
  },
  LIKE_NEW: {
    condition: 'LIKE_NEW',
    multiplier: 1.1,
    label: 'Like new',
  },
  GOOD: {
    condition: 'GOOD',
    multiplier: 1.0,
    label: 'Good condition',
  },
  FAIR: {
    condition: 'FAIR',
    multiplier: 0.7,
    label: 'Fair condition',
  },
  POOR: {
    condition: 'POOR',
    multiplier: 0.4,
    label: 'Poor condition',
  },
};

/**
 * Default condition when not specified.
 * Uses GOOD as a conservative middle-ground.
 */
export const DEFAULT_CONDITION: ItemCondition = 'GOOD';

/**
 * Get condition modifier for a given condition level.
 */
export function getConditionModifier(
  condition: ItemCondition | undefined
): ConditionModifier {
  const normalizedCondition = condition || DEFAULT_CONDITION;
  return CONDITION_MODIFIERS[normalizedCondition];
}

/**
 * Parse condition string to normalized ItemCondition.
 * Handles various user input formats.
 */
export function parseCondition(input: string | undefined): ItemCondition {
  if (!input) {
    return DEFAULT_CONDITION;
  }

  const normalized = input.toUpperCase().replace(/[\s_-]+/g, '_');

  // Direct match
  if (normalized in CONDITION_MODIFIERS) {
    return normalized as ItemCondition;
  }

  // Fuzzy matching for common variations
  const mappings: Record<string, ItemCondition> = {
    // New variants
    'NEW': 'NEW_WITHOUT_TAGS',
    'BRAND_NEW': 'NEW_WITHOUT_TAGS',
    'SEALED': 'NEW_SEALED',
    'BNIB': 'NEW_SEALED',  // Brand New In Box
    'BNWT': 'NEW_WITH_TAGS',
    'NWOT': 'NEW_WITHOUT_TAGS',
    'NWT': 'NEW_WITH_TAGS',
    'NIB': 'NEW_SEALED',
    'NOS': 'NEW_WITHOUT_TAGS',  // New Old Stock
    'MINT': 'LIKE_NEW',

    // Like new variants
    'LIKENEW': 'LIKE_NEW',
    'EXCELLENT': 'LIKE_NEW',
    'PRISTINE': 'LIKE_NEW',
    'PERFECT': 'LIKE_NEW',

    // Good variants
    'GREAT': 'GOOD',
    'VERY_GOOD': 'GOOD',
    'VERYGOOD': 'GOOD',
    'VG': 'GOOD',
    'GENTLY_USED': 'GOOD',
    'GENTLYUSED': 'GOOD',
    'LIGHTLY_USED': 'GOOD',

    // Fair variants
    'ACCEPTABLE': 'FAIR',
    'OK': 'FAIR',
    'OKAY': 'FAIR',
    'AVERAGE': 'FAIR',
    'USED': 'FAIR',
    'MODERATE': 'FAIR',

    // Poor variants
    'BAD': 'POOR',
    'WORN': 'POOR',
    'DAMAGED': 'POOR',
    'BROKEN': 'POOR',
    'FOR_PARTS': 'POOR',
    'FORPARTS': 'POOR',
    'AS_IS': 'POOR',
    'ASIS': 'POOR',
  };

  return mappings[normalized] || DEFAULT_CONDITION;
}

/**
 * Get all conditions in order from best to worst.
 */
export function getConditionsOrdered(): ItemCondition[] {
  return [
    'NEW_SEALED',
    'NEW_WITH_TAGS',
    'NEW_WITHOUT_TAGS',
    'LIKE_NEW',
    'GOOD',
    'FAIR',
    'POOR',
  ];
}

/**
 * Check if condition A is better than condition B.
 */
export function isConditionBetter(a: ItemCondition, b: ItemCondition): boolean {
  const order = getConditionsOrdered();
  return order.indexOf(a) < order.indexOf(b);
}
