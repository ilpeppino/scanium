import {
  PricingInsights,
  PricingPrefs,
  MarketplaceUsed,
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
}

/**
 * Pricing service for market price lookups
 *
 * Phase 2 implementation: Returns NOT_SUPPORTED status since web search
 * tooling is not yet available. Architecture is ready for future enhancement.
 */
export class PricingService {
  private marketplacesService: MarketplacesService;
  private cache: Map<string, { insights: PricingInsights; expiresAt: number }>;
  private cleanupInterval: NodeJS.Timeout | null = null;

  constructor(private readonly config: PricingServiceConfig) {
    this.marketplacesService = new MarketplacesService(config.catalogPath);
    this.cache = new Map();

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

    // Build marketplaces metadata
    const marketplacesUsed: MarketplaceUsed[] = marketplaces.map((m) => ({
      id: m.id,
      name: m.name,
      baseUrl: m.domains[0], // Use first domain as base URL
    }));

    // Phase 2: Web search tooling not available yet
    // Return NOT_SUPPORTED status with clear explanation
    const insights: PricingInsights = {
      status: 'NOT_SUPPORTED',
      countryCode: prefs.countryCode,
      marketplacesUsed,
      querySummary: this.buildQuerySummary(item, prefs),
      errorCode: 'NO_TOOLING',
    };

    // Cache the result
    this.cache.set(cacheKey, {
      insights,
      expiresAt: Date.now() + this.config.cacheTtlMs,
    });

    return insights;
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
