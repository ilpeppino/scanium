import { z } from 'zod';

/**
 * Pricing V3 Types
 *
 * Dedicated types for the V3 pricing endpoint with manual-trigger pricing requests.
 */

/**
 * Item condition enum matching Android ItemCondition (7 values)
 */
export const itemConditionSchema = z.enum([
  'NEW_SEALED',
  'NEW_WITH_TAGS',
  'NEW_WITHOUT_TAGS',
  'LIKE_NEW',
  'GOOD',
  'FAIR',
  'POOR',
]);

export type ItemCondition = z.infer<typeof itemConditionSchema>;

/**
 * Pricing V3 Request Schema (Android -> Backend)
 */
export const pricingV3RequestSchema = z.object({
  /** Item ID for request tracking */
  itemId: z.string().min(1).max(100),
  /** Brand name */
  brand: z.string().min(1).max(100),
  /** Product type from domainCategoryId (e.g., "electronics_laptop") */
  productType: z.string().min(1).max(100),
  /** Model name/number (optional - can be omitted if unknown) */
  model: z.string().min(1).max(200).optional(),
  /** Item condition (7 values) */
  condition: itemConditionSchema,
  /** ISO 2-letter country code */
  countryCode: z.string().length(2).toUpperCase(),
  /** Optional: year of manufacture */
  year: z.number().int().min(1990).max(2030).optional(),
  /** Optional: has original accessories */
  hasAccessories: z.boolean().optional(),
});

export type PricingV3Request = z.infer<typeof pricingV3RequestSchema>;

/**
 * Pricing V3 Response Status
 */
export const pricingV3StatusSchema = z.enum([
  'OK',            // Successfully retrieved pricing data
  'NO_RESULTS',    // No comparable listings found
  'ERROR',         // Error occurred during pricing lookup
  'TIMEOUT',       // Pricing lookup timed out
  'RATE_LIMITED',  // Rate limit exceeded
  'DISABLED',      // Pricing feature disabled
]);

export type PricingV3Status = z.infer<typeof pricingV3StatusSchema>;

/**
 * Confidence level for pricing results (HIGH, MED, LOW)
 */
export const pricingV3ConfidenceSchema = z.enum(['HIGH', 'MED', 'LOW']);

export type PricingV3Confidence = z.infer<typeof pricingV3ConfidenceSchema>;

/**
 * Marketplace used in pricing lookup
 */
export const marketplaceUsedV3Schema = z.object({
  id: z.string(),
  name: z.string(),
});

export type MarketplaceUsedV3 = z.infer<typeof marketplaceUsedV3Schema>;

/**
 * Price range response
 */
export const priceRangeV3Schema = z.object({
  low: z.number().positive(),
  high: z.number().positive(),
  currency: z.string().length(3),
});

export type PriceRangeV3 = z.infer<typeof priceRangeV3Schema>;

/**
 * Pricing insights from V3 endpoint
 */
export const pricingV3InsightsSchema = z.object({
  status: pricingV3StatusSchema,
  countryCode: z.string().length(2),
  marketplacesUsed: z.array(marketplaceUsedV3Schema),
  range: priceRangeV3Schema.optional(),
  confidence: pricingV3ConfidenceSchema.optional(),
  reason: z.string().max(200).optional(),
  resultCount: z.number().int().min(0).optional(),
});

export type PricingV3Insights = z.infer<typeof pricingV3InsightsSchema>;

/**
 * Pricing V3 Response Schema (Backend -> Android)
 */
export const pricingV3ResponseSchema = z.object({
  success: z.boolean(),
  pricing: pricingV3InsightsSchema,
  cached: z.boolean(),
  processingTimeMs: z.number(),
  promptVersion: z.string(),
});

export type PricingV3Response = z.infer<typeof pricingV3ResponseSchema>;

/**
 * OpenAI prompt response structure (internal)
 */
export const openAIPricingResponseSchema = z.object({
  low: z.number(),
  high: z.number(),
  cur: z.string().length(3),
  conf: z.enum(['HIGH', 'MED', 'LOW']),
  why: z.string().max(100),
});

export type OpenAIPricingResponse = z.infer<typeof openAIPricingResponseSchema>;

/**
 * Cache key components
 */
export interface CacheKeyComponents {
  brand: string;
  productType: string;
  model?: string;
  condition: ItemCondition;
  countryCode: string;
}

/**
 * Pricing V3 service configuration
 */
export interface PricingV3Config {
  enabled: boolean;
  timeoutMs: number;
  cacheTtlSeconds: number;
  dailyQuota: number;
  promptVersion: string;
  openaiApiKey?: string;
  openaiModel: string;
  catalogPath: string;
}
