/**
 * Pricing Estimator Service
 *
 * Produces deterministic, explainable price range estimates
 * based solely on visual attributes.
 *
 * Algorithm:
 * 1. Start with category base price range
 * 2. Apply brand tier multiplier
 * 3. Apply condition modifier
 * 4. Apply completeness bonus
 * 5. Clamp to category min/max bounds
 * 6. Calculate confidence based on input quality
 * 7. Generate human-readable explanation
 */

import {
  PriceEstimateInput,
  PriceEstimateResult,
  PriceConfidence,
  PricingStep,
  BrandTier,
  ItemCondition,
} from './types.js';
import { getCategoryPricing, DEFAULT_CATEGORY_PRICING } from './category-pricing.js';
import { getBrandTier, getBrandMultiplier, getBrandTierLabel } from './brand-tiers.js';
import { getConditionModifier, parseCondition } from './condition-modifiers.js';
import {
  getCompletenessMultiplier,
  getCompletenessScore,
  describeCompleteness,
  inferCompleteness,
} from './completeness-modifiers.js';

/**
 * Main pricing estimation function.
 *
 * Produces a conservative price range with explanation.
 */
export function estimatePrice(input: PriceEstimateInput): PriceEstimateResult {
  const steps: PricingStep[] = [];
  const caveats: string[] = [];

  // ============ STEP 1: Category Base Price ============
  const categoryPricing = getCategoryPricing(input.category);
  const isUnknownCategory = categoryPricing === DEFAULT_CATEGORY_PRICING;

  let [minPrice, maxPrice] = categoryPricing.baseRangeCents;

  steps.push({
    step: 'base_price',
    description: `Base price for ${categoryPricing.label}`,
    resultRange: [minPrice, maxPrice],
  });

  if (isUnknownCategory) {
    caveats.push('Category not recognized - using conservative defaults');
  }

  // ============ STEP 2: Brand Tier Adjustment ============
  const brandTier = getBrandTier(input.brand);
  const [brandMinMult, brandMaxMult] = getBrandMultiplier(brandTier);

  // Apply brand multiplier
  minPrice = Math.round(minPrice * brandMinMult);
  maxPrice = Math.round(maxPrice * brandMaxMult);

  steps.push({
    step: 'brand_adjustment',
    description: `${getBrandTierLabel(brandTier)}${input.brand ? ` (${input.brand})` : ''}`,
    factor: (brandMinMult + brandMaxMult) / 2,
    resultRange: [minPrice, maxPrice],
  });

  if (brandTier === 'UNKNOWN' && input.brand) {
    caveats.push(`Brand "${input.brand}" not in our database - using conservative estimate`);
  }

  // ============ STEP 3: Condition Adjustment ============
  const condition = input.condition || parseCondition(undefined);
  const conditionMod = getConditionModifier(condition);

  minPrice = Math.round(minPrice * conditionMod.multiplier);
  maxPrice = Math.round(maxPrice * conditionMod.multiplier);

  steps.push({
    step: 'condition_adjustment',
    description: conditionMod.label,
    factor: conditionMod.multiplier,
    resultRange: [minPrice, maxPrice],
  });

  if (!input.condition) {
    caveats.push('Condition not specified - assuming "Good" condition');
  }

  // ============ STEP 4: Completeness Bonus ============
  // Try to infer completeness from vision hints if not provided
  let completeness = input.completeness;
  if (!completeness && input.visionHints) {
    completeness = inferCompleteness(
      input.visionHints.ocrSnippets,
      input.visionHints.labels
    );
  }

  const completenessMultiplier = getCompletenessMultiplier(completeness, input.category);
  const completenessScore = getCompletenessScore(completeness);

  if (completenessMultiplier > 1.0) {
    // Completeness bonus only affects the max price (optimistic case)
    maxPrice = Math.round(maxPrice * completenessMultiplier);

    steps.push({
      step: 'completeness_bonus',
      description: describeCompleteness(completeness).join(', '),
      factor: completenessMultiplier,
      resultRange: [minPrice, maxPrice],
    });
  }

  // ============ STEP 5: Clamp to Category Bounds ============
  const originalMin = minPrice;
  const originalMax = maxPrice;

  minPrice = Math.max(minPrice, categoryPricing.minFloorCents);
  maxPrice = Math.min(maxPrice, categoryPricing.maxCapCents);

  // Ensure min <= max
  if (minPrice > maxPrice) {
    minPrice = maxPrice;
  }

  if (originalMin !== minPrice || originalMax !== maxPrice) {
    steps.push({
      step: 'bounds_clamp',
      description: `Clamped to category limits ($${(categoryPricing.minFloorCents / 100).toFixed(2)} - $${(categoryPricing.maxCapCents / 100).toFixed(2)})`,
      resultRange: [minPrice, maxPrice],
    });
  }

  // ============ STEP 6: Calculate Confidence ============
  const confidence = calculateConfidence(input, brandTier, condition);

  // ============ STEP 7: Generate Explanation ============
  const explanation = generateExplanation(
    input,
    categoryPricing.label,
    brandTier,
    conditionMod.label,
    completenessScore,
    confidence,
    minPrice,
    maxPrice
  );

  return {
    priceEstimateMinCents: minPrice,
    priceEstimateMaxCents: maxPrice,
    confidence,
    explanation,
    calculationSteps: steps,
    caveats: caveats.length > 0 ? caveats : undefined,
    inputSummary: {
      category: input.category || 'unknown',
      categoryLabel: categoryPricing.label,
      brand: input.brand || null,
      brandTier,
      condition,
      completenessScore,
    },
  };
}

/**
 * Calculate confidence level based on input quality.
 */
function calculateConfidence(
  input: PriceEstimateInput,
  brandTier: BrandTier,
  _condition: ItemCondition
): PriceConfidence {
  let score = 0;

  // Category known: +2 points
  if (input.category) {
    score += 2;
  }

  // Brand known and in database: +2 points
  if (input.brand && brandTier !== 'UNKNOWN') {
    score += 2;
  } else if (input.brand) {
    // Brand provided but not in database: +1 point
    score += 1;
  }

  // Condition explicitly provided: +1 point
  if (input.condition) {
    score += 1;
  }

  // Brand confidence is HIGH: +1 point
  if (input.brandConfidence === 'HIGH') {
    score += 1;
  }

  // Completeness info available: +1 point
  if (input.completeness && Object.values(input.completeness).some(Boolean)) {
    score += 1;
  }

  // Product type known: +1 point
  if (input.productType) {
    score += 1;
  }

  // Scoring thresholds:
  // HIGH: 6+ points (category + brand in DB + condition + something else)
  // MEDIUM: 3-5 points (category + partial info)
  // LOW: 0-2 points (minimal info)

  if (score >= 6) {
    return 'HIGH';
  } else if (score >= 3) {
    return 'MEDIUM';
  } else {
    return 'LOW';
  }
}

/**
 * Generate human-readable explanation lines.
 */
function generateExplanation(
  input: PriceEstimateInput,
  categoryLabel: string,
  brandTier: BrandTier,
  conditionLabel: string,
  completenessScore: number,
  confidence: PriceConfidence,
  minCents: number,
  maxCents: number
): string[] {
  const lines: string[] = [];

  // Price range statement
  const minDollars = (minCents / 100).toFixed(2);
  const maxDollars = (maxCents / 100).toFixed(2);

  lines.push(
    `Estimated resale value: $${minDollars} - $${maxDollars}`
  );

  // Category context
  lines.push(`Category: ${categoryLabel}`);

  // Brand context
  if (input.brand) {
    lines.push(`Brand: ${input.brand} (${getBrandTierLabel(brandTier).toLowerCase()})`);
  } else {
    lines.push('Brand: Not identified');
  }

  // Condition context
  lines.push(`Condition: ${conditionLabel}`);

  // Completeness context
  if (completenessScore > 0) {
    lines.push(`Completeness: ${completenessScore}% of original packaging/accessories`);
  }

  // Confidence context
  const confidenceDescriptions: Record<PriceConfidence, string> = {
    HIGH: 'High confidence - good attribute coverage',
    MEDIUM: 'Medium confidence - partial attribute coverage',
    LOW: 'Low confidence - limited information available',
  };
  lines.push(confidenceDescriptions[confidence]);

  // Disclaimer
  lines.push(
    'Note: This is an estimate based on item attributes, not actual market data.'
  );

  return lines;
}

/**
 * Batch estimate prices for multiple items.
 */
export function estimatePriceBatch(
  inputs: PriceEstimateInput[]
): PriceEstimateResult[] {
  return inputs.map(estimatePrice);
}

/**
 * Get price estimate as formatted string (for display).
 */
export function formatPriceRange(
  minCents: number,
  maxCents: number,
  currency: string = 'USD'
): string {
  const formatter = new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  });

  const min = formatter.format(minCents / 100);
  const max = formatter.format(maxCents / 100);

  if (minCents === maxCents) {
    return min;
  }

  return `${min} - ${max}`;
}
