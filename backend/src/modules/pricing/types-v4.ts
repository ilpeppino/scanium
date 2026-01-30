import { z } from 'zod';
import { itemConditionSchema } from './types-v3.js';

/**
 * Pricing V4 Types
 *
 * Verifiable price range based on marketplace listings (NL scope).
 */

/**
 * Pricing V4 Request Schema (Android -> Backend)
 */
export const pricingV4RequestSchema = z.object({
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
  /** Optional: preferred marketplaces to query */
  preferredMarketplaces: z.array(z.string().min(1).max(50)).optional(),
  /** Optional: variant attributes (e.g., storage, color) */
  variantAttributes: z.record(z.string().min(1).max(100), z.string().min(1).max(100)).optional(),
  /** Optional: completeness hints (charger, box, etc.) */
  completeness: z.array(z.string().min(1).max(100)).optional(),
  /** Optional: identifier (EAN/UPC) */
  identifier: z.string().min(1).max(100).optional(),
});

export type PricingV4Request = z.infer<typeof pricingV4RequestSchema>;

/**
 * Pricing V4 Response Status
 */
export const pricingV4StatusSchema = z.enum(['OK', 'NO_RESULTS', 'FALLBACK', 'ERROR', 'TIMEOUT']);

export type PricingV4Status = z.infer<typeof pricingV4StatusSchema>;

/**
 * Confidence level for pricing results (HIGH, MED, LOW)
 */
export const pricingV4ConfidenceSchema = z.enum(['HIGH', 'MED', 'LOW']);

export type PricingV4Confidence = z.infer<typeof pricingV4ConfidenceSchema>;

/**
 * Marketplace source metadata
 */
export const marketplaceSourceSchema = z.object({
  id: z.string().min(1).max(50),
  name: z.string().min(1).max(100),
  baseUrl: z.string().url(),
  listingCount: z.number().int().min(0),
  searchUrl: z.string().url().optional(),
});

export type MarketplaceSource = z.infer<typeof marketplaceSourceSchema>;

/**
 * Sample listing for verification
 */
export const sampleListingSchema = z.object({
  title: z.string().min(1).max(200),
  price: z.number().positive(),
  currency: z.string().length(3),
  condition: z.string().max(50).optional(),
  url: z.string().url().optional(),
  marketplace: z.string().min(1).max(50),
});

export type SampleListing = z.infer<typeof sampleListingSchema>;

/**
 * Price range response (p25/median/p75)
 */
export const priceRangeV4Schema = z.object({
  low: z.number().positive(),
  median: z.number().positive(),
  high: z.number().positive(),
  currency: z.string().length(3),
});

export type PriceRangeV4 = z.infer<typeof priceRangeV4Schema>;

/**
 * Pricing insights from V4 endpoint
 */
export const pricingV4InsightsSchema = z.object({
  status: pricingV4StatusSchema,
  countryCode: z.string().length(2),
  sources: z.array(marketplaceSourceSchema),
  totalListingsAnalyzed: z.number().int().min(0),
  timeWindowDays: z.number().int().min(1),
  range: priceRangeV4Schema.optional(),
  sampleListings: z.array(sampleListingSchema).max(3).optional(),
  confidence: pricingV4ConfidenceSchema,
  fallbackReason: z.string().max(200).optional(),
});

export type PricingV4Insights = z.infer<typeof pricingV4InsightsSchema>;

/**
 * Pricing V4 Response Schema (Backend -> Android)
 */
export const pricingV4ResponseSchema = z.object({
  success: z.boolean(),
  pricing: pricingV4InsightsSchema,
  cached: z.boolean(),
  processingTimeMs: z.number(),
});

export type PricingV4Response = z.infer<typeof pricingV4ResponseSchema>;

/**
 * Cache key components
 */
export interface PricingV4CacheKeyComponents {
  brand: string;
  productType: string;
  model?: string;
  condition: z.infer<typeof itemConditionSchema>;
  countryCode: string;
  variantAttributes?: Record<string, string>;
  completeness?: string[];
  identifier?: string;
}

/**
 * Marketplace adapter types (internal)
 */
export interface ListingQuery {
  brand: string;
  model: string;
  productType: string;
  condition?: z.infer<typeof itemConditionSchema>;
  countryCode: string;
  maxResults: number;
}

export interface FetchedListing {
  title: string;
  price: number;
  currency: string;
  condition?: string;
  url: string;
  postedDate?: Date;
  marketplace: string;
  raw?: unknown;
}

export interface NormalizedListing extends FetchedListing {
  matchConfidence?: 'HIGH' | 'MED' | 'LOW';
  normalizedCondition?: 'NEW' | 'LIKE_NEW' | 'GOOD' | 'FAIR' | 'POOR' | null;
}

export interface NormalizationInput {
  listings: FetchedListing[];
  targetBrand: string;
  targetModel: string;
  targetProductType: string;
  variantAttributes?: Record<string, string>;
  completeness?: string[];
  identifier?: string;
}

export interface NormalizationOutput {
  relevantListings: NormalizedListing[];
  excludedListings: { listing: FetchedListing; reason: string }[];
  clusterSummary: string;
}

export interface MarketplaceAdapter {
  readonly id: string;
  readonly name: string;
  fetchListings(query: ListingQuery): Promise<FetchedListing[]>;
  buildSearchUrl(query: ListingQuery): string;
  isHealthy(): Promise<boolean>;
}

/**
 * Pricing V4 service configuration
 */
export interface PricingV4Config {
  enabled: boolean;
  timeoutMs: number;
  cacheTtlSeconds: number;
  aiNormalizationEnabled: boolean;
  fallbackToV3: boolean;
  openaiApiKey?: string;
  openaiModel: string;
}
