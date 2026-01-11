import OpenAI from 'openai';
import {
  PricingInsights,
  PricingPrefs,
  MarketplaceUsed,
  PricingResult,
  PriceRange,
} from './schema.js';
import { MarketplacesService } from '../marketplaces/service.js';
import { ItemContextSnapshot } from '../assistant/types.js';
import { sha256Hex } from '../../infra/observability/hash.js';

/**
 * Pricing service configuration
 */
export interface PricingServiceConfig {
  /** Whether pricing feature is enabled */
  enabled: boolean;
  /** Timeout for pricing lookup in milliseconds */
  timeoutMs: number;
  /** Cache TTL in milliseconds (default 24h) */
  cacheTtlMs: number;
  /** Path to marketplaces catalog */
  catalogPath: string;
  /** OpenAI API key */
  openaiApiKey?: string;
  /** OpenAI model for pricing */
  openaiModel?: string;
}

/**
 * Pricing service for market price lookups
 *
 * Uses OpenAI API with web search to find comparable listings across
 * European marketplaces and compute price ranges.
 */
export class PricingService {
  private marketplacesService: MarketplacesService;
  private cache: Map<string, { insights: PricingInsights; expiresAt: number }>;
  private cleanupInterval: NodeJS.Timeout | null = null;
  private openaiClient: OpenAI | null = null;
  private openaiModel: string;

  constructor(private readonly config: PricingServiceConfig) {
    this.marketplacesService = new MarketplacesService(config.catalogPath);
    this.cache = new Map();
    this.openaiModel = config.openaiModel || 'gpt-4o-mini';

    // Initialize OpenAI client if API key provided
    if (config.openaiApiKey) {
      this.openaiClient = new OpenAI({
        apiKey: config.openaiApiKey,
        timeout: config.timeoutMs,
      });
    }

    // Initialize marketplaces catalog
    const initResult = this.marketplacesService.initialize();
    if (!initResult.ready) {
      console.error('[PricingService] Failed to load marketplaces catalog:', initResult.error);
    }

    // Start cache cleanup interval (every 5 minutes)
    this.cleanupInterval = setInterval(() => {
      this.cleanupExpiredCache();
    }, 5 * 60 * 1000);
  }

  /**
   * Stop the service and clean up resources
   */
  stop(): void {
    if (this.cleanupInterval) {
      clearInterval(this.cleanupInterval);
      this.cleanupInterval = null;
    }
    this.cache.clear();
  }

  /**
   * Compute pricing insights for an item
   *
   * @param item - Item snapshot with attributes
   * @param prefs - Pricing preferences (country, max results, etc.)
   * @returns PricingInsights with status and results
   */
  async computePricingInsights(
    item: ItemContextSnapshot,
    prefs?: PricingPrefs
  ): Promise<PricingInsights> {
    // Validate prefs
    if (!prefs) {
      return {
        status: 'ERROR',
        countryCode: 'XX',
        marketplacesUsed: [],
        errorCode: 'VALIDATION',
      };
    }

    // Check if feature is enabled
    if (!this.config.enabled) {
      return this.buildDisabledResponse(prefs);
    }

    // Check if marketplaces catalog is ready
    if (!this.marketplacesService.isReady()) {
      return this.buildErrorResponse(prefs, 'PROVIDER_ERROR', 'Marketplaces catalog not loaded');
    }

    // Build cache key from item snapshot and preferences
    const cacheKey = this.buildCacheKey(item, prefs);

    // Check cache
    const cached = this.cache.get(cacheKey);
    if (cached && cached.expiresAt > Date.now()) {
      return cached.insights;
    }

    // Get marketplaces for country
    const marketplacesResult = this.marketplacesService.getMarketplaces(prefs.countryCode);
    if (!marketplacesResult.success) {
      return this.buildErrorResponse(
        prefs,
        'VALIDATION',
        `Country '${prefs.countryCode}' not supported`
      );
    }

    // Filter marketplaces if specific ones requested
    let marketplaces = marketplacesResult.data;
    if (prefs.marketplaces && prefs.marketplaces.length > 0) {
      marketplaces = marketplaces.filter((m) => prefs.marketplaces!.includes(m.id));
    }

    // Build marketplaces metadata (limit to 5 domains)
    const selectedMarketplaces = marketplaces.slice(0, 5);
    const marketplacesUsed: MarketplaceUsed[] = selectedMarketplaces.map((m) => ({
      id: m.id,
      name: m.name,
      baseUrl: m.domains[0], // Use first domain as base URL
    }));

    // Build query summary
    const querySummary = this.buildQuerySummary(item, prefs);

    // Check if OpenAI client is available
    if (!this.openaiClient) {
      return {
        status: 'ERROR',
        countryCode: prefs.countryCode,
        marketplacesUsed,
        errorCode: 'NO_TOOLING',
      };
    }

    // Perform web search using OpenAI
    try {
      const insights = await this.searchPricesWithOpenAI(
        item,
        prefs,
        selectedMarketplaces,
        marketplacesUsed,
        querySummary
      );

      // Cache successful results
      if (insights.status === 'OK') {
        this.cache.set(cacheKey, {
          insights,
          expiresAt: Date.now() + this.config.cacheTtlMs,
        });
      }

      return insights;
    } catch (error) {
      console.error('[PricingService] Error during web search:', error);
      return this.buildErrorResponse(
        prefs,
        'PROVIDER_ERROR',
        error instanceof Error ? error.message : 'Unknown error'
      );
    }
  }

  /**
   * Search for prices using OpenAI with web search
   */
  private async searchPricesWithOpenAI(
    item: ItemContextSnapshot,
    prefs: PricingPrefs,
    selectedMarketplaces: any[],
    marketplacesUsed: MarketplaceUsed[],
    querySummary: string
  ): Promise<PricingInsights> {
    // Build search query targeting specific marketplaces
    const domains = selectedMarketplaces.flatMap((m) => m.domains);
    const searchQuery = this.buildSearchQuery(item, domains);

    // Get country config for currency
    const countryConfig = this.marketplacesService.getCountryConfig(prefs.countryCode);
    const defaultCurrency = countryConfig.success ? countryConfig.data.defaultCurrency : 'EUR';

    // Call OpenAI with web search (using chat completion with browsing)
    const systemPrompt = `You are a price research assistant. Search the web for ${item.title || 'the item'} listings on these marketplaces: ${domains.join(', ')}.

Find up to 5 comparable listings and extract:
- Listing title
- Price (numeric value and currency)
- URL to the listing
- Marketplace name

Return ONLY valid JSON matching this schema:
{
  "results": [
    {
      "title": "string",
      "price": { "amount": number, "currency": "EUR|USD|GBP|..." },
      "url": "https://...",
      "marketplaceId": "string (marktplaats|amazon|ebay|etc)"
    }
  ]
}

Requirements:
- Return 0-5 results (max 5)
- Price amounts must be positive numbers
- URLs must be valid https:// links
- Currency must be 3-letter code
- marketplaceId should match one of: ${selectedMarketplaces.map(m => m.id).join(', ')}`;

    const userPrompt = `Search for: ${searchQuery}

Focus on marketplaces in ${prefs.countryCode}.`;

    try {
      const completion = await this.openaiClient!.chat.completions.create({
        model: this.openaiModel,
        messages: [
          { role: 'system', content: systemPrompt },
          { role: 'user', content: userPrompt },
        ],
        temperature: 0.3,
        max_tokens: 2000,
        response_format: { type: 'json_object' },
      });

      const responseText = completion.choices[0]?.message?.content;
      if (!responseText) {
        throw new Error('No response from OpenAI');
      }

      // Parse JSON response
      const parsed = JSON.parse(responseText);

      // Validate and normalize results
      const results: PricingResult[] = [];
      if (Array.isArray(parsed.results)) {
        for (const result of parsed.results.slice(0, 5)) {
          // Validate required fields
          if (
            typeof result.title === 'string' &&
            typeof result.price?.amount === 'number' &&
            result.price.amount > 0 &&
            typeof result.price?.currency === 'string' &&
            typeof result.url === 'string' &&
            result.url.startsWith('https://') &&
            typeof result.marketplaceId === 'string'
          ) {
            results.push({
              title: result.title.slice(0, 200), // Limit length
              price: {
                amount: result.price.amount,
                currency: result.price.currency.toUpperCase().slice(0, 3),
              },
              url: result.url,
              sourceMarketplaceId: result.marketplaceId,
            });
          }
        }
      }

      // Check if we have any results
      if (results.length === 0) {
        return {
          status: 'NO_RESULTS',
          countryCode: prefs.countryCode,
          marketplacesUsed,
          querySummary,
          errorCode: 'NO_RESULTS',
        };
      }

      // Compute price range
      const prices = results.map((r) => r.price.amount);
      const range: PriceRange = {
        low: Math.min(...prices),
        high: Math.max(...prices),
        currency: defaultCurrency,
      };

      // Determine confidence based on result count and price variance
      const priceVariance = range.high - range.low;
      const avgPrice = prices.reduce((a, b) => a + b, 0) / prices.length;
      const varianceRatio = priceVariance / avgPrice;

      let confidence: 'LOW' | 'MED' | 'HIGH' = 'MED';
      if (results.length >= 4 && varianceRatio < 0.5) {
        confidence = 'HIGH';
      } else if (results.length < 2 || varianceRatio > 1.0) {
        confidence = 'LOW';
      }

      return {
        status: 'OK',
        countryCode: prefs.countryCode,
        marketplacesUsed,
        querySummary,
        results,
        range,
        confidence,
      };
    } catch (error) {
      // Handle rate limiting
      if (error instanceof Error && error.message.includes('rate_limit')) {
        throw new Error('OpenAI rate limited');
      }

      // Re-throw for caller to handle
      throw error;
    }
  }

  /**
   * Build search query from item attributes
   */
  private buildSearchQuery(item: ItemContextSnapshot, domains: string[]): string {
    const parts: string[] = [];

    // Add title if present
    if (item.title) {
      parts.push(item.title);
    }

    // Add key attributes
    const keyAttrs = item.attributes?.filter((a) =>
      ['brand', 'model', 'color', 'size'].includes(a.key.toLowerCase())
    );
    if (keyAttrs && keyAttrs.length > 0) {
      parts.push(...keyAttrs.map((a) => a.value));
    }

    // Add category hint
    if (item.category) {
      parts.push(`(${item.category})`);
    }

    // Add site: operators for specific domains (helps search engines focus)
    const siteOperators = domains.slice(0, 3).map((d) => `site:${d}`).join(' OR ');

    return `${parts.join(' ')} ${siteOperators}`.trim();
  }

  /**
   * Build cache key from item snapshot and pricing preferences
   */
  private buildCacheKey(item: ItemContextSnapshot, prefs: PricingPrefs): string {
    const itemHash = sha256Hex(
      JSON.stringify({
        title: item.title,
        category: item.category,
        attributes: item.attributes?.map((a) => `${a.key}:${a.value}`).sort(),
      })
    );

    const prefsHash = sha256Hex(
      JSON.stringify({
        country: prefs.countryCode,
        maxResults: prefs.maxResults,
        marketplaces: prefs.marketplaces?.sort(),
      })
    );

    return `pricing:v1:${itemHash}:${prefsHash}`;
  }

  /**
   * Build query summary for logging (no secrets)
   */
  private buildQuerySummary(item: ItemContextSnapshot, prefs: PricingPrefs): string {
    const parts: string[] = [];

    if (item.title) parts.push(item.title);
    if (item.category) parts.push(`(${item.category})`);

    // Add key attributes (brand, model, etc.)
    const keyAttrs = item.attributes?.filter((a) =>
      ['brand', 'model', 'color', 'size', 'condition'].includes(a.key.toLowerCase())
    );
    if (keyAttrs && keyAttrs.length > 0) {
      const attrStr = keyAttrs.map((a) => `${a.key}=${a.value}`).join(', ');
      parts.push(`[${attrStr}]`);
    }

    parts.push(`in ${prefs.countryCode}`);

    return parts.join(' ');
  }

  /**
   * Build disabled response
   */
  private buildDisabledResponse(prefs: PricingPrefs): PricingInsights {
    return {
      status: 'DISABLED',
      countryCode: prefs.countryCode,
      marketplacesUsed: [],
    };
  }

  /**
   * Build error response
   */
  private buildErrorResponse(
    prefs: PricingPrefs,
    errorCode: string,
    reason: string
  ): PricingInsights {
    console.error(`[PricingService] Error: ${errorCode} - ${reason}`);

    return {
      status: 'ERROR',
      countryCode: prefs.countryCode,
      marketplacesUsed: [],
      errorCode: errorCode as any,
    };
  }

  /**
   * Clean up expired cache entries
   */
  private cleanupExpiredCache(): void {
    const now = Date.now();
    let removed = 0;

    for (const [key, entry] of this.cache.entries()) {
      if (entry.expiresAt <= now) {
        this.cache.delete(key);
        removed++;
      }
    }

    if (removed > 0) {
      console.log(`[PricingService] Cleaned up ${removed} expired cache entries`);
    }
  }

  /**
   * Get cache statistics
   */
  getCacheStats(): { size: number; maxTtlMs: number } {
    return {
      size: this.cache.size,
      maxTtlMs: this.config.cacheTtlMs,
    };
  }
}
