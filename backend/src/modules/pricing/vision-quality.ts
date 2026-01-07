/**
 * Vision Quality Scoring
 *
 * Calculates photo quality score and applies range narrowing.
 * Better photos = narrower price range = higher confidence.
 */

import { VisionQualityInput, WearIndicators, ItemCondition } from './types-v2.js';

/**
 * Calculate photo quality score (0-100).
 */
export function calculatePhotoQualityScore(
  input: Partial<VisionQualityInput> | undefined
): number {
  if (!input) {
    return 0;
  }

  let score = 0;

  // Photo count: 1-5+ photos (max 50 points)
  const photoCount = input.photoCount ?? 0;
  score += Math.min(photoCount, 5) * 10;

  // Resolution: 0.5MP - 12MP+ (max 20 points)
  const resolution = input.avgResolutionMp ?? 0;
  const resScore = Math.min(resolution / 12, 1) * 20;
  score += resScore;

  // Sharpness: inverse of blur (max 15 points)
  const blurScore = input.blurScore ?? 0.5; // Default to medium blur
  score += (1 - blurScore) * 15;

  // Lighting quality (max 15 points)
  const lightingScores: Record<string, number> = {
    GOOD: 15,
    FAIR: 8,
    POOR: 0,
  };
  const lighting = input.lightingQuality ?? 'FAIR';
  score += lightingScores[lighting] ?? 8;

  return Math.round(score);
}

/**
 * Narrow price range based on photo quality.
 *
 * Higher quality → narrower range (more precise estimate).
 * Quality 100 → 70% narrowing (30% of original spread)
 * Quality 0 → 0% narrowing (full spread)
 */
export function narrowRangeByQuality(
  minCents: number,
  maxCents: number,
  qualityScore: number
): [number, number] {
  // Only narrow if quality score is above threshold
  if (qualityScore < 40) {
    return [minCents, maxCents];
  }

  // Calculate narrowing factor (0 to 0.7)
  // Quality 40 → 0% narrowing
  // Quality 100 → 70% narrowing
  const normalizedQuality = (qualityScore - 40) / 60; // 0 to 1
  const narrowingFactor = normalizedQuality * 0.7;

  const spread = maxCents - minCents;
  const midpoint = (minCents + maxCents) / 2;

  const newSpread = spread * (1 - narrowingFactor);
  const newMin = Math.round(midpoint - newSpread / 2);
  const newMax = Math.round(midpoint + newSpread / 2);

  // Ensure min <= max
  return [Math.min(newMin, newMax), Math.max(newMin, newMax)];
}

/**
 * Refine condition based on detected wear indicators.
 *
 * If vision detects scratches, stains, etc., adjust condition downward.
 */
export function refineConditionByWear(
  declaredCondition: ItemCondition,
  wearIndicators: WearIndicators | undefined
): ItemCondition {
  if (!wearIndicators) {
    return declaredCondition;
  }

  // Count detected wear indicators
  const indicators = [
    wearIndicators.scratchesDetected,
    wearIndicators.stainDetected,
    wearIndicators.tearDetected,
    wearIndicators.fadeDetected,
  ];
  const wearCount = indicators.filter(Boolean).length;

  if (wearCount === 0) {
    return declaredCondition;
  }

  // Condition order from best to worst
  const conditionOrder: ItemCondition[] = [
    'NEW_SEALED',
    'NEW_WITH_TAGS',
    'NEW_WITHOUT_TAGS',
    'LIKE_NEW',
    'GOOD',
    'FAIR',
    'POOR',
  ];

  const currentIdx = conditionOrder.indexOf(declaredCondition);
  if (currentIdx === -1) {
    return declaredCondition; // Unknown condition - don't modify
  }

  // Each wear indicator drops condition by 1 level (max 2 drops)
  const drops = Math.min(wearCount, 2);
  const newIdx = Math.min(currentIdx + drops, conditionOrder.length - 1);

  return conditionOrder[newIdx];
}

/**
 * Get quality assessment description.
 */
export function describePhotoQuality(score: number): string {
  if (score >= 80) {
    return 'Excellent photo quality';
  } else if (score >= 60) {
    return 'Good photo quality';
  } else if (score >= 40) {
    return 'Fair photo quality';
  } else if (score >= 20) {
    return 'Limited photo quality';
  } else {
    return 'Minimal photo information';
  }
}

/**
 * Describe detected wear indicators.
 */
export function describeWearIndicators(
  indicators: WearIndicators | undefined
): string[] {
  if (!indicators) {
    return [];
  }

  const descriptions: string[] = [];

  if (indicators.scratchesDetected) {
    descriptions.push('Scratches detected');
  }
  if (indicators.stainDetected) {
    descriptions.push('Stains detected');
  }
  if (indicators.tearDetected) {
    descriptions.push('Tears detected');
  }
  if (indicators.fadeDetected) {
    descriptions.push('Color fading detected');
  }

  return descriptions;
}
