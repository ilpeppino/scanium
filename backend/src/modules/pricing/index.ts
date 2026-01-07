/**
 * Pricing Module
 *
 * Phase 1: Baseline price estimation based on visual attributes.
 * Phase 2: Market-aware pricing with regional, urban, and seasonal adjustments.
 *
 * No marketplace integrations - deterministic and explainable.
 */

// Routes
export { pricingRoutes } from './routes.js';

// Phase 1: Baseline estimator
export { estimatePrice, estimatePriceBatch, formatPriceRange } from './estimator.js';
export { getCategoryPricing, getAllCategoryIds, CATEGORY_PRICING } from './category-pricing.js';
export { getBrandTier, getBrandMultiplier, getBrandTierLabel, isKnownBrand } from './brand-tiers.js';
export { getConditionModifier, parseCondition, getConditionsOrdered } from './condition-modifiers.js';
export {
  getCompletenessMultiplier,
  getCompletenessScore,
  describeCompleteness,
  inferCompleteness,
} from './completeness-modifiers.js';

// Phase 2: Market-aware estimator
export { estimatePriceV2, estimatePriceV2Batch } from './estimator-v2.js';
export {
  initializeMarketData,
  getRegionalIndex,
  getCurrencyForRegion,
  getSeasonalityFactor,
  isPostalCodeUrban,
  getSupportedRegions,
  getMarketDataLoadedAt,
} from './market-data.js';
export {
  calculatePhotoQualityScore,
  narrowRangeByQuality,
  refineConditionByWear,
  describePhotoQuality,
  describeWearIndicators,
} from './vision-quality.js';

// Phase 1 types
export type {
  PriceEstimateInput,
  PriceEstimateResult,
  PriceConfidence,
  ItemCondition,
  BrandTier,
  CompletenessIndicators,
  PricingStep,
  CategoryPricing,
} from './types.js';

// Phase 2 types
export type {
  PriceEstimateInputV2,
  PriceEstimateResultV2,
  RegionCode,
  CategorySegment,
  MarketContext,
  VisionQualityInput,
  WearIndicators,
  RegionalPriceIndex,
  CurrencyConfig,
  SeasonalityConfig,
  PricingStepV2,
  MarketAdjustment,
} from './types-v2.js';
