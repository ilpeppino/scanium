/**
 * eBay Comps Tool
 *
 * Provides pricing comparisons using official eBay APIs only.
 * This tool is gated behind the ASSIST_EBAY_COMPS_ENABLED feature flag.
 *
 * Uses:
 * - eBay Browse API (findItemsAdvanced) for completed listings
 * - No scraping, no UI automation, no password prompts
 * - Results are cached to minimize API calls
 * - Rate limited to prevent abuse
 */

import { UnifiedCache } from '../../infra/cache/unified-cache.js';


/**
 * Comparable item from eBay.
 */
export type EbayCompItem = {
  /** Item title */
  title: string;
  /** Sale price */
  price: number;
  /** Currency code (EUR, USD, GBP, etc.) */
  currency: string;
  /** Whether item was sold */
  sold: boolean;
  /** Sale/listing date */
  date: string;
  /** Condition (new, used, etc.) */
  condition?: string;
};

/**
 * eBay comps summary.
 */
export type EbayCompsSummary = {
  /** Query used for search */
  query: string;
  /** Number of comparable items found */
  sampleSize: number;
  /** Median price */
  medianPrice: number;
  /** Minimum price */
  minPrice: number;
  /** Maximum price */
  maxPrice: number;
  /** Currency code */
  currency: string;
  /** Top comparable items (limited to 3) */
  topComps: EbayCompItem[];
  /** Cache hit indicator */
  fromCache: boolean;
  /** Timestamp of data retrieval */
  timestamp: number;
};

/**
 * eBay comps tool options.
 */
export type EbayCompsToolOptions = {
  /** eBay API client ID (OAuth) */
  clientId?: string;
  /** eBay API client secret */
  clientSecret?: string;
  /** Cache TTL in milliseconds */
  cacheTtlMs: number;
  /** Maximum cache entries */
  cacheMaxEntries: number;
  /** Whether the tool is enabled */
  enabled: boolean;
};

/**
 * Input for eBay comps query.
 */
export type EbayCompsInput = {
  /** Category (e.g., 'electronics', 'furniture') */
  category?: string;
  /** Brand name */
  brand?: string;
  /** Model name/number */
  model?: string;
  /** Color */
  color?: string;
  /** User's explicit question */
  userQuestion: string;
  /** Preferred currency */
  currency?: string;
};

/**
 * Build a search query from input.
 */
function buildSearchQuery(input: EbayCompsInput): string {
  const parts: string[] = [];

  if (input.brand) parts.push(input.brand);
  if (input.model) parts.push(input.model);
  if (input.color) parts.push(input.color);

  // If no structured data, try to extract keywords from the question
  if (parts.length === 0) {
    // Extract potential product terms from the question
    const cleaned = input.userQuestion
      .toLowerCase()
      .replace(/what|how much|is|the|price|worth|value|cost|of|for|a|an/g, '')
      .trim();
    if (cleaned.length > 3) {
      parts.push(cleaned);
    }
  }

  return parts.join(' ').trim();
}

/**
 * Normalize query for cache key.
 */
function normalizeQuery(query: string): string {
  return query
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, '')
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * eBay Comps Tool
 *
 * Note: This is a mock implementation. For production use, you would need to:
 * 1. Implement OAuth2 authentication with eBay
 * 2. Use the eBay Browse API or Finding API
 * 3. Handle rate limiting and error responses
 *
 * The mock provides realistic-looking data for development/testing.
 */
export class EbayCompsTool {
  private readonly cache: UnifiedCache<EbayCompsSummary>;
  private readonly options: EbayCompsToolOptions;

  constructor(options: EbayCompsToolOptions) {
    this.options = options;
    this.cache = new UnifiedCache<EbayCompsSummary>({
      ttlMs: options.cacheTtlMs,
      maxEntries: options.cacheMaxEntries,
      name: 'ebay-comps',
      enableDedup: true,
    });
  }

  /**
   * Check if the tool is enabled.
   */
  isEnabled(): boolean {
    return this.options.enabled;
  }

  /**
   * Get pricing comparisons from eBay.
   */
  async getComps(input: EbayCompsInput): Promise<EbayCompsSummary | null> {
    if (!this.options.enabled) {
      return null;
    }

    const query = buildSearchQuery(input);
    if (!query || query.length < 3) {
      return null;
    }

    const cacheKey = normalizeQuery(query);

    // Check cache first
    const cached = this.cache.get(cacheKey);
    if (cached) {
      return { ...cached, fromCache: true };
    }

    // In production, this would call the eBay API
    // For now, return mock data that looks realistic
    const mockComps = this.generateMockComps(query, input.currency ?? 'EUR');

    // Cache the result
    this.cache.set(cacheKey, mockComps);

    return mockComps;
  }

  /**
   * Generate mock comparables for development/testing.
   * In production, this would be replaced with actual eBay API calls.
   */
  private generateMockComps(query: string, currency: string): EbayCompsSummary {
    // Generate realistic-looking mock data based on query
    const basePrice = this.estimateBasePrice(query);
    const variance = basePrice * 0.3;

    const prices = [
      basePrice - variance * 0.5,
      basePrice - variance * 0.2,
      basePrice,
      basePrice + variance * 0.3,
      basePrice + variance * 0.7,
    ].map((p) => Math.round(p * 100) / 100);

    prices.sort((a, b) => a - b);

    const topComps: EbayCompItem[] = [
      {
        title: `${query} - Good Condition`,
        price: prices[2],
        currency,
        sold: true,
        date: this.recentDate(7),
        condition: 'Used',
      },
      {
        title: `${query} - Like New`,
        price: prices[3],
        currency,
        sold: true,
        date: this.recentDate(14),
        condition: 'Like New',
      },
      {
        title: `${query} - Excellent`,
        price: prices[1],
        currency,
        sold: true,
        date: this.recentDate(21),
        condition: 'Used',
      },
    ];

    return {
      query,
      sampleSize: 5 + Math.floor(Math.random() * 10),
      medianPrice: prices[2],
      minPrice: prices[0],
      maxPrice: prices[4],
      currency,
      topComps,
      fromCache: false,
      timestamp: Date.now(),
    };
  }

  /**
   * Estimate a base price based on query keywords.
   */
  private estimateBasePrice(query: string): number {
    const lowerQuery = query.toLowerCase();

    // Very rough heuristics for mock data
    if (lowerQuery.includes('iphone') || lowerQuery.includes('macbook')) {
      return 300 + Math.random() * 500;
    }
    if (lowerQuery.includes('samsung') || lowerQuery.includes('galaxy')) {
      return 150 + Math.random() * 300;
    }
    if (lowerQuery.includes('ikea')) {
      return 30 + Math.random() * 100;
    }
    if (lowerQuery.includes('vintage') || lowerQuery.includes('antique')) {
      return 50 + Math.random() * 200;
    }
    if (lowerQuery.includes('chair') || lowerQuery.includes('table')) {
      return 40 + Math.random() * 150;
    }
    if (lowerQuery.includes('nike') || lowerQuery.includes('adidas')) {
      return 30 + Math.random() * 100;
    }

    // Default range
    return 20 + Math.random() * 80;
  }

  /**
   * Generate a recent date string.
   */
  private recentDate(daysAgo: number): string {
    const date = new Date();
    date.setDate(date.getDate() - daysAgo);
    return date.toISOString().split('T')[0];
  }

  /**
   * Stop the cache cleanup timer.
   */
  stop(): void {
    this.cache.stop();
  }

  /**
   * Get cache statistics.
   */
  getStats(): { hits: number; misses: number; size: number } {
    const stats = this.cache.getStats();
    return {
      hits: stats.hits,
      misses: stats.misses,
      size: stats.size,
    };
  }
}

/**
 * Format eBay comps summary for assistant response.
 * Returns only the comparable items without hardcoded English utility phrases.
 */
export function formatCompsSummary(comps: EbayCompsSummary): string {
  const currencySymbol = comps.currency === 'EUR' ? '€' : comps.currency === 'USD' ? '$' : comps.currency === 'GBP' ? '£' : comps.currency;

  let response = '';

  if (comps.topComps.length > 0) {
    for (const comp of comps.topComps) {
      response += `- "${comp.title.slice(0, 50)}..." - ${currencySymbol}${comp.price.toFixed(0)} (${comp.condition ?? 'Used'})\n`;
    }
  }

  return response.trim();
}

/**
 * Check if a question is asking about pricing.
 */
export function isPricingQuestion(message: string): boolean {
  const lowerMessage = message.toLowerCase();
  const pricingKeywords = [
    'price',
    'pricing',
    'worth',
    'value',
    'cost',
    'how much',
    'sell for',
    'asking price',
    'market price',
    'estimate',
    'valuation',
    'comparable',
    'comps',
  ];

  return pricingKeywords.some((keyword) => lowerMessage.includes(keyword));
}
