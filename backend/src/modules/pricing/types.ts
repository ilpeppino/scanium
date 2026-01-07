/**
 * Pricing Module Types
 *
 * Defines types for the baseline price estimation system.
 * This module produces conservative, explainable price ranges
 * based solely on visual attributes - no marketplace data.
 */

/**
 * Confidence level for price estimates.
 * - HIGH: Strong attribute coverage (category + brand + condition known)
 * - MEDIUM: Partial coverage (category + some attributes)
 * - LOW: Minimal coverage (category only or unknown category)
 */
export type PriceConfidence = 'HIGH' | 'MEDIUM' | 'LOW';

/**
 * Item condition levels (ordered from best to worst).
 */
export type ItemCondition =
  | 'NEW_SEALED'      // Factory sealed, never opened
  | 'NEW_WITH_TAGS'   // New with original tags attached
  | 'NEW_WITHOUT_TAGS'// New, no tags, never used
  | 'LIKE_NEW'        // Used briefly, no visible wear
  | 'GOOD'            // Normal use, minor wear
  | 'FAIR'            // Noticeable wear, fully functional
  | 'POOR';           // Heavy wear, may have defects

/**
 * Brand tier classification for pricing.
 */
export type BrandTier =
  | 'LUXURY'          // High-end luxury brands (Louis Vuitton, Chanel, Hermes)
  | 'PREMIUM'         // Premium quality brands (Apple, Sony, Le Creuset)
  | 'MID_RANGE'       // Solid mid-market brands (Samsung, KitchenAid)
  | 'BUDGET'          // Budget-friendly brands (generic, store brands)
  | 'UNKNOWN';        // Brand not recognized

/**
 * Completeness indicators for items.
 */
export type CompletenessIndicators = {
  /** Item has original packaging/box */
  hasOriginalBox: boolean;
  /** Item has original tags attached */
  hasTags: boolean;
  /** Item is factory sealed */
  isSealed: boolean;
  /** Item includes all original accessories */
  hasAccessories: boolean;
  /** Item has original documentation/manual */
  hasDocumentation: boolean;
};

/**
 * Input for price estimation.
 */
export type PriceEstimateInput = {
  /** Item ID for correlation */
  itemId: string;
  /** Category ID from domain pack (e.g., "consumer_electronics_portable") */
  category?: string;
  /** Category segment (e.g., "electronics", "furniture") */
  segment?: string;
  /** Detected or user-provided brand */
  brand?: string;
  /** Brand confidence from detection */
  brandConfidence?: 'HIGH' | 'MED' | 'LOW';
  /** Detected product type (e.g., "smartphone", "coffee maker") */
  productType?: string;
  /** Item condition */
  condition?: ItemCondition;
  /** Completeness indicators */
  completeness?: Partial<CompletenessIndicators>;
  /** Detected material (for furniture/textiles) */
  material?: string;
  /** Additional context from vision */
  visionHints?: {
    /** OCR-detected text snippets */
    ocrSnippets?: string[];
    /** Vision labels */
    labels?: string[];
  };
};

/**
 * A single step in the pricing calculation for explainability.
 */
export type PricingStep = {
  /** Step identifier */
  step: string;
  /** Human-readable description */
  description: string;
  /** Multiplier or adjustment applied */
  factor?: number;
  /** Resulting range after this step [min, max] */
  resultRange: [number, number];
};

/**
 * Result of price estimation.
 */
export type PriceEstimateResult = {
  /** Minimum estimated price in cents (conservative) */
  priceEstimateMinCents: number;
  /** Maximum estimated price in cents (optimistic) */
  priceEstimateMaxCents: number;
  /** Confidence in the estimate */
  confidence: PriceConfidence;
  /** Human-readable explanation lines */
  explanation: string[];
  /** Detailed calculation steps (for debugging/transparency) */
  calculationSteps?: PricingStep[];
  /** Warnings or caveats about the estimate */
  caveats?: string[];
  /** Input attributes used for calculation */
  inputSummary: {
    category: string;
    categoryLabel: string;
    brand: string | null;
    brandTier: BrandTier;
    condition: ItemCondition;
    completenessScore: number;
  };
};

/**
 * Category pricing configuration.
 */
export type CategoryPricing = {
  /** Category ID */
  id: string;
  /** Display label */
  label: string;
  /** Base price range for "good" condition, no brand [min, max] in cents */
  baseRangeCents: [number, number];
  /** Maximum price cap for this category in cents */
  maxCapCents: number;
  /** Minimum floor price in cents (below this, don't list) */
  minFloorCents: number;
  /** Category-specific notes */
  notes?: string;
};

/**
 * Brand classification for pricing.
 */
export type BrandClassification = {
  /** Brand name (normalized uppercase) */
  name: string;
  /** Price tier */
  tier: BrandTier;
  /** Category-specific overrides */
  categoryOverrides?: Record<string, BrandTier>;
};

/**
 * Condition modifier configuration.
 */
export type ConditionModifier = {
  /** Condition level */
  condition: ItemCondition;
  /** Multiplier to apply to base price (e.g., 1.0 for GOOD, 0.5 for POOR) */
  multiplier: number;
  /** Human-readable label */
  label: string;
};
