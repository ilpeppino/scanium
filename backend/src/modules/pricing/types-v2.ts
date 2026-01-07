/**
 * Pricing Module Types - Phase 2
 *
 * Extends Phase 1 types with market awareness signals.
 */

import { PriceConfidence, ItemCondition, BrandTier, CompletenessIndicators } from './types.js';

// Re-export Phase 1 types
export * from './types.js';

/**
 * Supported regions for market adjustments.
 */
export type RegionCode =
  | 'NL' | 'DE' | 'BE' | 'FR' | 'ES' | 'IT' | 'PT' | 'AT' | 'PL' | 'SE' | 'DK' | 'FI' | 'IE'  // EU
  | 'US'  // United States
  | 'UK'; // United Kingdom

/**
 * Category segments for market adjustments.
 */
export type CategorySegment =
  | 'electronics'
  | 'fashion'
  | 'furniture'
  | 'home_goods'
  | 'sports'
  | 'toys'
  | 'other';

/**
 * Market context for price adjustments.
 */
export type MarketContext = {
  /** Region code (ISO 3166-1 alpha-2) */
  region?: RegionCode;
  /** Whether location is urban (vs suburban/rural) */
  isUrban?: boolean;
  /** Postal code for urban classification lookup */
  postalCode?: string;
};

/**
 * Wear indicators detected from vision.
 */
export type WearIndicators = {
  /** Scratches visible on item */
  scratchesDetected?: boolean;
  /** Stains or discoloration detected */
  stainDetected?: boolean;
  /** Tears or rips detected */
  tearDetected?: boolean;
  /** Fading or color loss detected */
  fadeDetected?: boolean;
};

/**
 * Vision quality signals for confidence weighting.
 */
export type VisionQualityInput = {
  /** Number of photos provided */
  photoCount?: number;
  /** Average resolution in megapixels */
  avgResolutionMp?: number;
  /** Blur score 0-1 (0 = sharp, 1 = blurry) */
  blurScore?: number;
  /** Lighting quality assessment */
  lightingQuality?: 'GOOD' | 'FAIR' | 'POOR';
  /** Wear indicators from vision analysis */
  wearIndicators?: WearIndicators;
};

/**
 * Extended input for Phase 2 price estimation.
 */
export type PriceEstimateInputV2 = {
  /** Item ID for correlation */
  itemId: string;
  /** Category ID from domain pack */
  category?: string;
  /** Category segment */
  segment?: string;
  /** Detected or user-provided brand */
  brand?: string;
  /** Brand confidence from detection */
  brandConfidence?: 'HIGH' | 'MED' | 'LOW';
  /** Detected product type */
  productType?: string;
  /** Item condition */
  condition?: ItemCondition;
  /** Completeness indicators */
  completeness?: Partial<CompletenessIndicators>;
  /** Detected material */
  material?: string;
  /** Vision hints from extraction */
  visionHints?: {
    ocrSnippets?: string[];
    labels?: string[];
  };
  /** Market context for regional adjustments */
  marketContext?: MarketContext;
  /** Vision quality signals */
  visionQuality?: VisionQualityInput;
};

/**
 * Regional price index configuration.
 */
export type RegionalPriceIndex = {
  /** Region code */
  region: RegionCode;
  /** Category-specific indices (relative to USD baseline) */
  categoryIndices: Partial<Record<CategorySegment, number>>;
  /** Default index for unlisted categories */
  defaultIndex: number;
  /** Valid from date (ISO 8601) */
  validFrom: string;
  /** Valid until date (ISO 8601) */
  validUntil: string;
  /** Data source identifier */
  source: string;
};

/**
 * Currency configuration.
 */
export type CurrencyConfig = {
  /** ISO 4217 currency code */
  code: string;
  /** Currency symbol */
  symbol: string;
  /** Exchange rate relative to USD */
  rate: number;
  /** Decimal places for display */
  decimals: number;
};

/**
 * Seasonality factor configuration.
 */
export type SeasonalityConfig = {
  /** Category segment */
  segment: CategorySegment;
  /** Monthly factors (1-12 → multiplier) */
  monthlyFactors: Record<number, number>;
};

/**
 * Urban classification entry.
 */
export type UrbanClassification = {
  /** Postal code prefix */
  postalPrefix: string;
  /** Whether this prefix is urban */
  isUrban: boolean;
  /** Country code */
  country: RegionCode;
};

/**
 * Extended pricing step for Phase 2.
 */
export type PricingStepV2 = {
  /** Step identifier */
  step: string;
  /** Human-readable description */
  description: string;
  /** Multiplier or adjustment applied */
  factor?: number;
  /** Resulting range after this step [min, max] */
  resultRange: [number, number];
  /** Nested steps (for Phase 1 details) */
  details?: PricingStepV2[];
};

/**
 * Market adjustment record.
 */
export type MarketAdjustment = {
  /** Type of adjustment */
  type: 'regional' | 'urban' | 'seasonality' | 'currency';
  /** Factor applied */
  factor: number;
  /** Description */
  description: string;
};

/**
 * Extended result for Phase 2 price estimation.
 */
export type PriceEstimateResultV2 = {
  /** Minimum estimated price in cents (local currency) */
  priceEstimateMinCents: number;
  /** Maximum estimated price in cents (local currency) */
  priceEstimateMaxCents: number;
  /** Minimum as decimal */
  priceEstimateMin: number;
  /** Maximum as decimal */
  priceEstimateMax: number;
  /** Formatted price range for display */
  priceRangeFormatted: string;
  /** Currency code */
  currency: string;
  /** Currency symbol */
  currencySymbol: string;
  /** Confidence in the estimate */
  confidence: PriceConfidence;
  /** Market context summary */
  marketContext: string;
  /** Human-readable explanation lines */
  explanation: string[];
  /** Warnings or caveats */
  caveats?: string[];
  /** Input summary */
  inputSummary: {
    category: string;
    categoryLabel: string;
    brand: string | null;
    brandTier: BrandTier;
    condition: ItemCondition;
    completenessScore: number;
    region: RegionCode;
    isUrban: boolean;
    photoQualityScore: number;
  };
  /** Calculation steps (for debugging) */
  calculationSteps?: PricingStepV2[];
};
