import { z } from 'zod';

/**
 * Pricing Insights Schema
 *
 * Response schema for market pricing information
 */

/**
 * Status codes for pricing insights
 */
export const pricingStatusSchema = z.enum([
  'OK',              // Successfully retrieved pricing data
  'NOT_SUPPORTED',   // Web search/tooling not available
  'DISABLED',        // Pricing feature disabled via config
  'ERROR',           // Error occurred during pricing lookup
  'TIMEOUT',         // Pricing lookup timed out
  'NO_RESULTS',      // No pricing results found
]);

export type PricingStatus = z.infer<typeof pricingStatusSchema>;

/**
 * Error codes for pricing failures
 */
export const pricingErrorCodeSchema = z.enum([
  'NO_TOOLING',       // OpenAI web search tool not available
  'NO_RESULTS',       // No listings found
  'PROVIDER_ERROR',   // OpenAI API error
  'VALIDATION',       // Response validation failed
  'TIMEOUT',          // Request timeout
  'RATE_LIMITED',     // OpenAI rate limited
]).optional();

export type PricingErrorCode = z.infer<typeof pricingErrorCodeSchema>;

/**
 * Confidence level for pricing results
 */
export const confidenceLevelSchema = z.enum(['LOW', 'MED', 'HIGH']);

export type ConfidenceLevel = z.infer<typeof confidenceLevelSchema>;

/**
 * Price information with amount and currency
 */
export const priceInfoSchema = z.object({
  amount: z.number().positive(),
  currency: z.string().length(3),
});

export type PriceInfo = z.infer<typeof priceInfoSchema>;

/**
 * Single pricing result from a marketplace
 */
export const pricingResultSchema = z.object({
  /** Listing title */
  title: z.string(),
  /** Price information */
  price: priceInfoSchema,
  /** Full URL to the listing */
  url: z.string().url(),
  /** Marketplace ID from catalog */
  sourceMarketplaceId: z.string(),
});

export type PricingResult = z.infer<typeof pricingResultSchema>;

/**
 * Price range (low/high)
 */
export const priceRangeSchema = z.object({
  low: z.number().positive(),
  high: z.number().positive(),
  currency: z.string().length(3),
}).refine(
  data => data.high >= data.low,
  { message: 'High price must be >= low price' }
);

export type PriceRange = z.infer<typeof priceRangeSchema>;

/**
 * Marketplace used for pricing lookup
 */
export const marketplaceUsedSchema = z.object({
  id: z.string(),
  name: z.string(),
  baseUrl: z.string(),
});

export type MarketplaceUsed = z.infer<typeof marketplaceUsedSchema>;

/**
 * Complete pricing insights response
 */
export const pricingInsightsSchema = z.object({
  /** Status of the pricing lookup */
  status: pricingStatusSchema,
  /** Country code used for lookup */
  countryCode: z.string().length(2),
  /** Marketplaces queried */
  marketplacesUsed: z.array(marketplaceUsedSchema),
  /** Summary of the search query (no secrets) */
  querySummary: z.string().optional(),
  /** Pricing results (only if status OK) */
  results: z.array(pricingResultSchema).max(5).optional(),
  /** Price range derived from results */
  range: priceRangeSchema.optional(),
  /** Confidence in the pricing data */
  confidence: confidenceLevelSchema.optional(),
  /** Error code if status is ERROR/TIMEOUT/NO_RESULTS */
  errorCode: pricingErrorCodeSchema,
});

export type PricingInsights = z.infer<typeof pricingInsightsSchema>;

/**
 * Pricing preferences from client
 */
export const pricingPrefsSchema = z.object({
  /** Country code (ISO 3166-1 alpha-2) */
  countryCode: z.string().length(2).toUpperCase(),
  /** Max results to return (capped at 5) */
  maxResults: z.number().int().min(1).max(5).default(5),
  /** Optional marketplace ID filter */
  marketplaces: z.array(z.string()).optional(),
});

export type PricingPrefs = z.infer<typeof pricingPrefsSchema>;
