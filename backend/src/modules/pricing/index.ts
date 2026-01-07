/**
 * Pricing Module
 *
 * Provides baseline price estimation based on visual attributes.
 * No marketplace integrations - deterministic and explainable.
 */

export { pricingRoutes } from './routes.js';
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
