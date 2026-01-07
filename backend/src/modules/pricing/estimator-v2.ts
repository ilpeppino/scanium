/**
 * Pricing Estimator V2
 *
 * Produces market-aware price range estimates using a 3-layer approach:
 * - Layer 1: Phase 1 baseline (category, brand, condition, completeness)
 * - Layer 2: Market adjustments (regional, urban, seasonality)
 * - Layer 3: Confidence weighting (photo quality, wear detection)
 */

import {
  PriceEstimateInputV2,
  PriceEstimateResultV2,
  PricingStepV2,
  RegionCode,
  CategorySegment,
  MarketAdjustment,
  CurrencyConfig,
} from './types-v2.js';
import { PriceConfidence } from './types.js';
import { estimatePrice } from './estimator.js';
import { getConditionModifier, parseCondition } from './condition-modifiers.js';
import {
  getRegionalIndex,
  getCurrencyForRegion,
  getSeasonalityFactor,
  isPostalCodeUrban,
} from './market-data.js';
import {
  calculatePhotoQualityScore,
  narrowRangeByQuality,
  refineConditionByWear,
  describePhotoQuality,
  describeWearIndicators,
} from './vision-quality.js';

/**
 * Urban premium multipliers by category segment.
 */
const URBAN_PREMIUM: Record<CategorySegment, number> = {
  electronics: 1.05,
  fashion: 1.12,
  furniture: 1.15,
  home_goods: 1.05,
  sports: 1.08,
  toys: 1.03,
  other: 1.05,
};

/**
 * Map category ID to segment.
 */
function mapCategoryToSegment(category: string | undefined): CategorySegment {
  if (!category) return 'other';

  const lowerCategory = category.toLowerCase();

  // Electronics
  if (
    lowerCategory.includes('electronics') ||
    lowerCategory.includes('phone') ||
    lowerCategory.includes('laptop') ||
    lowerCategory.includes('tablet')
  ) {
    return 'electronics';
  }

  // Fashion/Textiles
  if (
    lowerCategory.includes('textile') ||
    lowerCategory.includes('fashion') ||
    lowerCategory.includes('clothing') ||
    lowerCategory.includes('apparel')
  ) {
    return 'fashion';
  }

  // Furniture
  if (lowerCategory.includes('furniture')) {
    return 'furniture';
  }

  // Home goods
  if (
    lowerCategory.includes('kitchen') ||
    lowerCategory.includes('drinkware') ||
    lowerCategory.includes('tableware') ||
    lowerCategory.includes('cutlery') ||
    lowerCategory.includes('container') ||
    lowerCategory.includes('cleaning') ||
    lowerCategory.includes('decor') ||
    lowerCategory.includes('storage') ||
    lowerCategory.includes('plant')
  ) {
    return 'home_goods';
  }

  // Sports
  if (lowerCategory.includes('sport') || lowerCategory.includes('fitness')) {
    return 'sports';
  }

  // Toys
  if (lowerCategory.includes('toy') || lowerCategory.includes('game')) {
    return 'toys';
  }

  return 'other';
}

/**
 * Resolve urban status from market context.
 */
function resolveIsUrban(
  marketContext: PriceEstimateInputV2['marketContext'],
  region: RegionCode
): boolean {
  if (!marketContext) return false;

  // Explicit isUrban takes precedence
  if (marketContext.isUrban !== undefined) {
    return marketContext.isUrban;
  }

  // Try postal code lookup
  if (marketContext.postalCode) {
    return isPostalCodeUrban(marketContext.postalCode, region);
  }

  return false; // Default to non-urban (conservative)
}

/**
 * Calculate confidence V2 (includes market context and vision quality).
 */
function calculateConfidenceV2(
  phase1Confidence: PriceConfidence,
  hasMarketContext: boolean,
  hasUrbanSignal: boolean,
  photoQualityScore: number
): PriceConfidence {
  let score = 0;

  // Phase 1 base score
  const phase1Scores: Record<PriceConfidence, number> = {
    HIGH: 6,
    MEDIUM: 4,
    LOW: 2,
  };
  score += phase1Scores[phase1Confidence];

  // Region known: +1
  if (hasMarketContext) score += 1;

  // Urban/suburban known: +1
  if (hasUrbanSignal) score += 1;

  // Photo quality
  if (photoQualityScore >= 70) score += 2;
  else if (photoQualityScore >= 40) score += 1;

  // Thresholds
  if (score >= 10) return 'HIGH';
  if (score >= 6) return 'MEDIUM';
  return 'LOW';
}

/**
 * Format price range for display.
 */
function formatPriceRangeV2(
  minCents: number,
  maxCents: number,
  currency: CurrencyConfig
): string {
  const formatValue = (cents: number): string => {
    const value = cents / 100;
    if (currency.decimals === 0) {
      return Math.round(value).toString();
    }
    return value.toFixed(currency.decimals);
  };

  const min = formatValue(minCents);
  const max = formatValue(maxCents);

  if (minCents === maxCents) {
    return `${currency.symbol}${min}`;
  }

  return `${currency.symbol}${min} - ${currency.symbol}${max}`;
}

/**
 * Generate V2 explanation lines.
 */
function generateExplanationV2(
  phase1Explanation: string[],
  region: RegionCode,
  _isUrban: boolean,
  _segment: CategorySegment,
  regionalIndex: number,
  urbanPremium: number | null,
  seasonalFactor: number,
  photoQualityScore: number,
  currency: CurrencyConfig,
  minCents: number,
  maxCents: number,
  confidence: PriceConfidence,
  wearDescriptions: string[]
): string[] {
  const lines: string[] = [];

  // Price range in local currency
  lines.push(
    `Estimated resale value: ${formatPriceRangeV2(minCents, maxCents, currency)}`
  );

  // Include key Phase 1 lines (skip the first price line and last disclaimer)
  for (const line of phase1Explanation.slice(1, -1)) {
    lines.push(line);
  }

  // Market context
  if (regionalIndex !== 1.0) {
    const direction = regionalIndex > 1 ? 'higher' : 'lower';
    const pct = Math.round(Math.abs(regionalIndex - 1) * 100);
    lines.push(`${region} market: ${pct}% ${direction} than baseline`);
  }

  if (urbanPremium && urbanPremium > 1.0) {
    const pct = Math.round((urbanPremium - 1) * 100);
    lines.push(`Urban location: +${pct}% premium`);
  }

  if (seasonalFactor !== 1.0) {
    const direction = seasonalFactor > 1 ? 'High' : 'Low';
    const pct = Math.round(Math.abs(seasonalFactor - 1) * 100);
    lines.push(`${direction} demand season: ${seasonalFactor > 1 ? '+' : '-'}${pct}%`);
  }

  // Vision quality
  if (photoQualityScore > 0) {
    lines.push(describePhotoQuality(photoQualityScore));
  }

  // Wear indicators
  for (const wear of wearDescriptions) {
    lines.push(wear);
  }

  // Confidence
  const confidenceDescriptions: Record<PriceConfidence, string> = {
    HIGH: 'High confidence - comprehensive attribute and market coverage',
    MEDIUM: 'Medium confidence - partial attribute coverage',
    LOW: 'Low confidence - limited information available',
  };
  lines.push(confidenceDescriptions[confidence]);

  // Disclaimer
  lines.push(
    'Note: This is an estimate based on item attributes and regional market data.'
  );

  return lines;
}

/**
 * Main Phase 2 pricing estimation function.
 */
export function estimatePriceV2(input: PriceEstimateInputV2): PriceEstimateResultV2 {
  const steps: PricingStepV2[] = [];
  const caveats: string[] = [];
  const marketAdjustments: MarketAdjustment[] = [];

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 1: Phase 1 Baseline
  // ═══════════════════════════════════════════════════════════════════════════

  const phase1Result = estimatePrice({
    itemId: input.itemId,
    category: input.category,
    segment: input.segment,
    brand: input.brand,
    brandConfidence: input.brandConfidence,
    productType: input.productType,
    condition: input.condition,
    completeness: input.completeness,
    material: input.material,
    visionHints: input.visionHints,
  });

  let minCents = phase1Result.priceEstimateMinCents;
  let maxCents = phase1Result.priceEstimateMaxCents;

  steps.push({
    step: 'phase1_baseline',
    description: 'Phase 1 baseline estimate',
    resultRange: [minCents, maxCents],
  });

  // Inherit Phase 1 caveats
  if (phase1Result.caveats) {
    caveats.push(...phase1Result.caveats);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 2: Market Adjustments
  // ═══════════════════════════════════════════════════════════════════════════

  const marketContext = input.marketContext || {};
  const region: RegionCode = (marketContext.region as RegionCode) || 'US';
  const segment = mapCategoryToSegment(input.category);

  // --- 2a. Regional Price Index ---
  const regionalIndex = getRegionalIndex(region, segment);

  if (regionalIndex !== 1.0) {
    minCents = Math.round(minCents * regionalIndex);
    maxCents = Math.round(maxCents * regionalIndex);

    steps.push({
      step: 'regional_adjustment',
      description: `${region} market index (${segment})`,
      factor: regionalIndex,
      resultRange: [minCents, maxCents],
    });

    marketAdjustments.push({
      type: 'regional',
      factor: regionalIndex,
      description: `${region} regional index`,
    });
  }

  // --- 2b. Urban Premium ---
  const isUrban = resolveIsUrban(marketContext, region);
  let appliedUrbanPremium: number | null = null;

  if (isUrban) {
    const urbanPremium = URBAN_PREMIUM[segment] || URBAN_PREMIUM.other;
    // Urban premium only affects max (allows higher ceiling)
    maxCents = Math.round(maxCents * urbanPremium);
    appliedUrbanPremium = urbanPremium;

    steps.push({
      step: 'urban_premium',
      description: 'Urban market premium',
      factor: urbanPremium,
      resultRange: [minCents, maxCents],
    });

    marketAdjustments.push({
      type: 'urban',
      factor: urbanPremium,
      description: 'Urban location premium',
    });
  }

  // --- 2c. Seasonality ---
  const month = new Date().getMonth() + 1; // 1-12
  const seasonalFactor = getSeasonalityFactor(segment, month);

  if (seasonalFactor !== 1.0) {
    minCents = Math.round(minCents * seasonalFactor);
    maxCents = Math.round(maxCents * seasonalFactor);

    const seasonLabel = seasonalFactor > 1 ? 'High demand season' : 'Low demand season';
    steps.push({
      step: 'seasonality',
      description: seasonLabel,
      factor: seasonalFactor,
      resultRange: [minCents, maxCents],
    });

    marketAdjustments.push({
      type: 'seasonality',
      factor: seasonalFactor,
      description: seasonLabel,
    });
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // LAYER 3: Confidence Weighting
  // ═══════════════════════════════════════════════════════════════════════════

  const visionQuality = input.visionQuality || {};
  let wearDescriptions: string[] = [];

  // --- 3a. Wear Indicator Condition Refinement ---
  if (visionQuality.wearIndicators) {
    const originalCondition = parseCondition(input.condition);
    const refinedCondition = refineConditionByWear(
      originalCondition,
      visionQuality.wearIndicators
    );

    if (refinedCondition !== originalCondition) {
      // Re-apply condition modifier
      const originalMod = getConditionModifier(originalCondition);
      const refinedMod = getConditionModifier(refinedCondition);
      const adjustment = refinedMod.multiplier / originalMod.multiplier;

      minCents = Math.round(minCents * adjustment);
      maxCents = Math.round(maxCents * adjustment);

      steps.push({
        step: 'wear_refinement',
        description: `Wear detected → ${refinedMod.label}`,
        factor: adjustment,
        resultRange: [minCents, maxCents],
      });

      caveats.push('Condition adjusted based on detected wear');
    }

    wearDescriptions = describeWearIndicators(visionQuality.wearIndicators);
  }

  // --- 3b. Range Narrowing by Photo Quality ---
  const photoQualityScore = calculatePhotoQualityScore(visionQuality);

  if (photoQualityScore >= 40) {
    const [narrowedMin, narrowedMax] = narrowRangeByQuality(
      minCents,
      maxCents,
      photoQualityScore
    );
    minCents = narrowedMin;
    maxCents = narrowedMax;

    steps.push({
      step: 'range_narrowing',
      description: `Photo quality score: ${photoQualityScore}/100`,
      resultRange: [minCents, maxCents],
    });
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // CURRENCY CONVERSION
  // ═══════════════════════════════════════════════════════════════════════════

  const currency = getCurrencyForRegion(region);

  if (currency.code !== 'USD') {
    minCents = Math.round(minCents * currency.rate);
    maxCents = Math.round(maxCents * currency.rate);

    steps.push({
      step: 'currency_conversion',
      description: `Converted to ${currency.code}`,
      factor: currency.rate,
      resultRange: [minCents, maxCents],
    });

    marketAdjustments.push({
      type: 'currency',
      factor: currency.rate,
      description: `USD → ${currency.code}`,
    });
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // FINAL CONFIDENCE
  // ═══════════════════════════════════════════════════════════════════════════

  const hasMarketContext = !!marketContext.region;
  const hasUrbanSignal =
    marketContext.isUrban !== undefined || !!marketContext.postalCode;

  const confidence = calculateConfidenceV2(
    phase1Result.confidence,
    hasMarketContext,
    hasUrbanSignal,
    photoQualityScore
  );

  // ═══════════════════════════════════════════════════════════════════════════
  // GENERATE EXPLANATION
  // ═══════════════════════════════════════════════════════════════════════════

  const explanation = generateExplanationV2(
    phase1Result.explanation,
    region,
    isUrban,
    segment,
    regionalIndex,
    appliedUrbanPremium,
    seasonalFactor,
    photoQualityScore,
    currency,
    minCents,
    maxCents,
    confidence,
    wearDescriptions
  );

  // ═══════════════════════════════════════════════════════════════════════════
  // BUILD RESPONSE
  // ═══════════════════════════════════════════════════════════════════════════

  return {
    priceEstimateMinCents: minCents,
    priceEstimateMaxCents: maxCents,
    priceEstimateMin: minCents / 100,
    priceEstimateMax: maxCents / 100,
    priceRangeFormatted: formatPriceRangeV2(minCents, maxCents, currency),
    currency: currency.code,
    currencySymbol: currency.symbol,
    confidence,
    marketContext: `${region} / ${isUrban ? 'urban' : 'suburban'} / ${segment}`,
    explanation,
    caveats: caveats.length > 0 ? caveats : undefined,
    inputSummary: {
      category: phase1Result.inputSummary.category,
      categoryLabel: phase1Result.inputSummary.categoryLabel,
      brand: phase1Result.inputSummary.brand,
      brandTier: phase1Result.inputSummary.brandTier,
      condition: phase1Result.inputSummary.condition,
      completenessScore: phase1Result.inputSummary.completenessScore,
      region,
      isUrban,
      photoQualityScore,
    },
    calculationSteps: steps,
  };
}

/**
 * Batch estimate prices for multiple items.
 */
export function estimatePriceV2Batch(
  inputs: PriceEstimateInputV2[]
): PriceEstimateResultV2[] {
  return inputs.map(estimatePriceV2);
}
