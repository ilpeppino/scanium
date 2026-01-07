/**
 * Completeness Modifiers
 *
 * Defines how item completeness (packaging, tags, accessories)
 * affects pricing.
 *
 * Philosophy:
 * - Original packaging adds value
 * - Tags indicate item hasn't been used
 * - Sealed items are most valuable
 * - Accessories can significantly affect electronics value
 * - Documentation matters less for most items
 */

import { CompletenessIndicators } from './types.js';

/**
 * Individual bonus multipliers for completeness factors.
 * These are additive bonuses on top of base price.
 */
export const COMPLETENESS_BONUSES = {
  /** Original box/packaging - significant value add */
  hasOriginalBox: 0.15,
  /** Original tags attached - indicates unused */
  hasTags: 0.10,
  /** Factory sealed - highest premium */
  isSealed: 0.20,
  /** All original accessories - important for electronics */
  hasAccessories: 0.10,
  /** Original documentation - minor value add */
  hasDocumentation: 0.05,
};

/**
 * Maximum combined completeness bonus.
 * Caps the total bonus to avoid unrealistic prices.
 */
export const MAX_COMPLETENESS_BONUS = 0.40;

/**
 * Category-specific completeness weights.
 * Some categories value certain completeness factors more.
 */
export const CATEGORY_COMPLETENESS_WEIGHTS: Record<string, Partial<Record<keyof CompletenessIndicators, number>>> = {
  // Electronics: Accessories matter a lot (chargers, cables)
  consumer_electronics_portable: {
    hasAccessories: 0.20,
    hasOriginalBox: 0.15,
    hasDocumentation: 0.05,
  },
  consumer_electronics_stationary: {
    hasAccessories: 0.15,
    hasOriginalBox: 0.10,
  },
  home_electronics: {
    hasAccessories: 0.15,
    hasOriginalBox: 0.10,
  },

  // Fashion: Tags matter more, boxes less
  textile: {
    hasTags: 0.15,
    hasOriginalBox: 0.05,
  },

  // Kitchen: Box matters for resale
  kitchen_appliance: {
    hasOriginalBox: 0.20,
    hasAccessories: 0.15,
  },
  drinkware: {
    hasOriginalBox: 0.10,
  },

  // Furniture: Original packaging rarely available
  furniture: {
    hasOriginalBox: 0.05,
    hasDocumentation: 0.02,
  },
};

/**
 * Calculate completeness multiplier for pricing.
 *
 * Returns a multiplier >= 1.0 to apply to base price.
 *
 * @param completeness - Completeness indicators
 * @param categoryId - Optional category for weighted bonuses
 * @returns Multiplier to apply (e.g., 1.25 for 25% bonus)
 */
export function getCompletenessMultiplier(
  completeness: Partial<CompletenessIndicators> | undefined,
  categoryId?: string
): number {
  if (!completeness) {
    return 1.0;
  }

  let totalBonus = 0;

  // Get category-specific weights if available
  const categoryWeights = categoryId
    ? CATEGORY_COMPLETENESS_WEIGHTS[categoryId]
    : undefined;

  // Calculate bonus for each completeness factor
  for (const [key, value] of Object.entries(completeness)) {
    if (!value) continue;

    const typedKey = key as keyof CompletenessIndicators;

    // Use category-specific weight if available, otherwise default
    const bonus =
      categoryWeights?.[typedKey] ?? COMPLETENESS_BONUSES[typedKey] ?? 0;

    totalBonus += bonus;
  }

  // Cap the total bonus
  const cappedBonus = Math.min(totalBonus, MAX_COMPLETENESS_BONUS);

  return 1.0 + cappedBonus;
}

/**
 * Calculate completeness score as percentage (0-100).
 * Useful for display and confidence calculations.
 */
export function getCompletenessScore(
  completeness: Partial<CompletenessIndicators> | undefined
): number {
  if (!completeness) {
    return 0;
  }

  const factors: (keyof CompletenessIndicators)[] = [
    'hasOriginalBox',
    'hasTags',
    'isSealed',
    'hasAccessories',
    'hasDocumentation',
  ];

  let trueCount = 0;
  for (const factor of factors) {
    if (completeness[factor]) {
      trueCount++;
    }
  }

  return Math.round((trueCount / factors.length) * 100);
}

/**
 * Get human-readable description of completeness.
 */
export function describeCompleteness(
  completeness: Partial<CompletenessIndicators> | undefined
): string[] {
  if (!completeness) {
    return ['No packaging information available'];
  }

  const descriptions: string[] = [];

  if (completeness.isSealed) {
    descriptions.push('Factory sealed');
  }
  if (completeness.hasTags) {
    descriptions.push('Original tags attached');
  }
  if (completeness.hasOriginalBox) {
    descriptions.push('Includes original packaging');
  }
  if (completeness.hasAccessories) {
    descriptions.push('All original accessories included');
  }
  if (completeness.hasDocumentation) {
    descriptions.push('Original documentation/manual included');
  }

  if (descriptions.length === 0) {
    return ['No original packaging or accessories'];
  }

  return descriptions;
}

/**
 * Infer completeness from OCR and labels.
 * Used when user hasn't explicitly provided completeness info.
 */
export function inferCompleteness(
  ocrSnippets: string[] | undefined,
  labels: string[] | undefined
): Partial<CompletenessIndicators> {
  const result: Partial<CompletenessIndicators> = {};

  if (!ocrSnippets && !labels) {
    return result;
  }

  const allText = [
    ...(ocrSnippets || []),
    ...(labels || []),
  ]
    .map((s) => s.toLowerCase())
    .join(' ');

  // Check for sealed indicators
  if (
    allText.includes('sealed') ||
    allText.includes('factory sealed') ||
    allText.includes('unopened')
  ) {
    result.isSealed = true;
  }

  // Check for box indicators
  if (
    allText.includes('box') ||
    allText.includes('original packaging') ||
    allText.includes('in box') ||
    allText.includes('nib')
  ) {
    result.hasOriginalBox = true;
  }

  // Check for tags
  if (
    allText.includes('tags') ||
    allText.includes('nwt') ||
    allText.includes('with tags')
  ) {
    result.hasTags = true;
  }

  return result;
}
