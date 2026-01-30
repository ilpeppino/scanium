import OpenAI from 'openai';
import {
  PricingV3Request,
  PricingV3Insights,
  PricingV3Config,
  CacheKeyComponents,
  OpenAIPricingResponse,
  openAIPricingResponseSchema,
  MarketplaceUsedV3,
  PriceRangeV3,
} from './types-v3.js';
import {
  PRICING_V3_SYSTEM_PROMPT,
  buildPricingV3UserPrompt,
  PRICING_V3_PROMPT_VERSION,
} from './prompts-v3.js';
import { MarketplacesService } from '../marketplaces/service.js';
import { sha256Hex } from '../../infra/observability/hash.js';

/**
 * Pricing V3 Service
 *
 * Dedicated service for manual-trigger pricing requests using OpenAI.
 * Features:
 * - Token-optimized prompts (~400 tokens per request)
 * - 24h cache TTL
 * - Rate limiting via routes
 * - Region-based marketplace selection
 */
export class PricingV3Service {
  private marketplacesService: MarketplacesService;
  private cache: Map<string, { insights: PricingV3Insights; expiresAt: number }>;
  private cleanupInterval: NodeJS.Timeout | null = null;
  private openaiClient: OpenAI | null = null;

  constructor(private readonly config: PricingV3Config) {
    this.marketplacesService = new MarketplacesService(config.catalogPath);
    this.cache = new Map();

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
      console.error('[PricingV3Service] Failed to load marketplaces catalog:', initResult.error);
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
   * Estimate resale price for an item
   */
  async estimateResalePrice(request: PricingV3Request): Promise<PricingV3Insights> {
    const startTime = Date.now();

    // Check if feature is enabled
    if (!this.config.enabled) {
      return this.buildDisabledResponse(request.countryCode);
    }

    // Check if marketplaces catalog is ready
    if (!this.marketplacesService.isReady()) {
      return this.buildErrorResponse(request.countryCode, [], 'Marketplaces catalog not loaded');
    }

    // Build cache key
    const cacheKey = this.buildCacheKey({
      brand: request.brand,
      productType: request.productType,
      model: request.model,
      condition: request.condition,
      countryCode: request.countryCode,
    });

    // Check cache
    const cached = this.cache.get(cacheKey);
    if (cached && cached.expiresAt > Date.now()) {
      console.log(
        `[PricingV3Service] Cache hit for ${request.brand} ${request.model ?? '(no model)'} (${Date.now() - startTime}ms)`
      );
      return cached.insights;
    }

    // Get marketplaces for country
    const marketplacesResult = this.marketplacesService.getMarketplaces(request.countryCode);
    if (!marketplacesResult.success) {
      return this.buildErrorResponse(
        request.countryCode,
        [],
        `Country '${request.countryCode}' not supported`
      );
    }

    // Build marketplaces metadata (limit to 5 domains)
    const selectedMarketplaces = marketplacesResult.data.slice(0, 5);
    const marketplacesUsed: MarketplaceUsedV3[] = selectedMarketplaces.map((m) => ({
      id: m.id,
      name: m.name,
    }));

    // Check if OpenAI client is available
    if (!this.openaiClient) {
      return this.buildErrorResponse(
        request.countryCode,
        marketplacesUsed,
        'OpenAI API not configured'
      );
    }

    // Get country config for currency
    const countryConfig = this.marketplacesService.getCountryConfig(request.countryCode);
    const defaultCurrency = countryConfig.success ? countryConfig.data.defaultCurrency : 'EUR';

    // Call OpenAI for pricing estimate
    try {
      const insights = await this.getPricingFromOpenAI(
        request,
        selectedMarketplaces.map((m) => m.id),
        defaultCurrency,
        marketplacesUsed
      );

      // Cache successful results (OK or NO_RESULTS)
      if (insights.status === 'OK' || insights.status === 'NO_RESULTS') {
        this.cache.set(cacheKey, {
          insights,
          expiresAt: Date.now() + this.config.cacheTtlSeconds * 1000,
        });
      }

      console.log(
        `[PricingV3Service] Pricing complete for ${request.brand} ${request.model ?? '(no model)'}: ${insights.status} (${Date.now() - startTime}ms)`
      );

      return insights;
    } catch (error) {
      console.error('[PricingV3Service] Error during pricing:', error);

      // Handle timeout
      if (error instanceof Error && error.message.includes('timeout')) {
        return {
          status: 'TIMEOUT',
          countryCode: request.countryCode,
          marketplacesUsed,
        };
      }

      // Handle rate limiting
      if (error instanceof Error && error.message.includes('rate_limit')) {
        return {
          status: 'RATE_LIMITED',
          countryCode: request.countryCode,
          marketplacesUsed,
        };
      }

      return this.buildErrorResponse(
        request.countryCode,
        marketplacesUsed,
        error instanceof Error ? error.message : 'Unknown error'
      );
    }
  }

  /**
   * Get pricing from OpenAI using token-optimized prompts
   */
  private async getPricingFromOpenAI(
    request: PricingV3Request,
    marketplaceIds: string[],
    defaultCurrency: string,
    marketplacesUsed: MarketplaceUsedV3[]
  ): Promise<PricingV3Insights> {
    // Build user prompt
    const userPrompt = buildPricingV3UserPrompt({
      brand: request.brand,
      productType: request.productType,
      model: request.model,
      condition: request.condition,
      countryCode: request.countryCode,
      marketplaceIds,
    });

    // Call OpenAI with JSON mode
    const completion = await this.openaiClient!.chat.completions.create({
      model: this.config.openaiModel,
      messages: [
        { role: 'system', content: PRICING_V3_SYSTEM_PROMPT },
        { role: 'user', content: userPrompt },
      ],
      temperature: 0.2, // Low temperature for factual estimates
      max_tokens: 150, // Limit output tokens
      response_format: { type: 'json_object' },
    });

    const responseText = completion.choices[0]?.message?.content;
    if (!responseText) {
      throw new Error('No response from OpenAI');
    }

    // Parse and validate JSON response
    let parsed: unknown;
    try {
      parsed = JSON.parse(responseText);
    } catch (parseError) {
      console.error('[PricingV3Service] Failed to parse OpenAI response:', responseText.slice(0, 200));
      throw new Error('Invalid JSON response from OpenAI');
    }

    // Validate schema
    const validationResult = openAIPricingResponseSchema.safeParse(parsed);
    if (!validationResult.success) {
      console.error('[PricingV3Service] Schema validation failed:', validationResult.error);
      throw new Error('Invalid response schema from OpenAI');
    }

    const pricingData: OpenAIPricingResponse = validationResult.data;

    // Check if prices are reasonable (sanity check)
    if (pricingData.low <= 0 || pricingData.high <= 0 || pricingData.high < pricingData.low) {
      return {
        status: 'NO_RESULTS',
        countryCode: request.countryCode,
        marketplacesUsed,
      };
    }

    // Build price range
    const range: PriceRangeV3 = {
      low: Math.round(pricingData.low * 100) / 100, // Round to 2 decimals
      high: Math.round(pricingData.high * 100) / 100,
      currency: pricingData.cur.toUpperCase() || defaultCurrency,
    };

    return {
      status: 'OK',
      countryCode: request.countryCode,
      marketplacesUsed,
      range,
      confidence: pricingData.conf,
      reason: pricingData.why.slice(0, 200), // Limit reason length
      resultCount: 0, // V3 doesn't return individual results
    };
  }

  /**
   * Build cache key from request components
   *
   * Cache key format: pricing:v3:<sha256(normalized_input)>
   */
  buildCacheKey(components: CacheKeyComponents): string {
    const normalizedInput = {
      brand: components.brand.toLowerCase().trim(),
      productType: components.productType.toLowerCase().trim(),
      model: components.model?.toLowerCase().trim() ?? '',
      condition: components.condition,
      countryCode: components.countryCode,
    };

    const inputHash = sha256Hex(JSON.stringify(normalizedInput));
    return `pricing:v3:${inputHash}`;
  }

  /**
   * Build disabled response
   */
  private buildDisabledResponse(countryCode: string): PricingV3Insights {
    return {
      status: 'DISABLED',
      countryCode,
      marketplacesUsed: [],
    };
  }

  /**
   * Build error response
   */
  private buildErrorResponse(
    countryCode: string,
    marketplacesUsed: MarketplaceUsedV3[],
    reason: string
  ): PricingV3Insights {
    console.error(`[PricingV3Service] Error: ${reason}`);

    return {
      status: 'ERROR',
      countryCode,
      marketplacesUsed,
      reason: reason.slice(0, 200),
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
      console.log(`[PricingV3Service] Cleaned up ${removed} expired cache entries`);
    }
  }

  /**
   * Get cache statistics
   */
  getCacheStats(): { size: number; maxTtlSeconds: number } {
    return {
      size: this.cache.size,
      maxTtlSeconds: this.config.cacheTtlSeconds,
    };
  }

  /**
   * Get prompt version for monitoring
   */
  getPromptVersion(): string {
    return PRICING_V3_PROMPT_VERSION;
  }
}
