import { Config } from '../../config/index.js';
import {
  PricingV4Request,
  PricingV4Insights,
  PricingV4CacheKeyComponents,
  MarketplaceAdapter,
  FetchedListing,
  NormalizedListing,
  ListingQuery,
} from './types-v4.js';
import { sha256Hex } from '../../infra/observability/hash.js';
import { EbayBrowseAdapter } from '../marketplaces/adapters/ebay-adapter.js';
import { MarktplaatsAdapter } from '../marketplaces/adapters/marktplaats-adapter.js';
import { filterListings, ListingFilterOptions } from './normalization/filters.js';
import { AiClusterer } from './normalization/ai-clusterer.js';
import { aggregatePrices } from './normalization/aggregator.js';
import {
  recordPricingV4AdapterResult,
  recordPricingV4CacheHit,
  recordPricingV4ListingsFetched,
} from '../../infra/observability/metrics.js';
import { PricingV3Service } from './service-v3.js';
import { PricingV3Insights } from './types-v3.js';
import { SubtypeClassifier } from './query-policy/subtype-classifier.js';
import { buildCatalogQueryContext } from './query-policy/catalog-query-context.js';
import { buildQueryPlan } from './query-policy/query-plan.js';
import { applyPostFilterRules } from './query-policy/post-filter-rules.js';
import { CategoryResolver, NullCategoryResolver } from './query-policy/category-resolver.js';
import { EbayCategoryResolver } from './query-policy/category-resolver-ebay.js';
import { QueryPlan } from './query-policy/types.js';

/**
 * Pricing V4 Service (skeleton)
 *
 * Verifiable price range based on marketplace listings.
 * Placeholder implementation until adapters and normalization pipeline land.
 */
export class PricingV4Service {
  private cache: Map<string, { insights: PricingV4Insights; expiresAt: number }>;
  private cleanupInterval: NodeJS.Timeout | null = null;
  private readonly adapters: MarketplaceAdapter[];
  private readonly aiClusterer: AiClusterer;
  private readonly timeWindowDays = 30;
  private readonly v3Service: PricingV3Service | null;
  private readonly subtypeClassifier: SubtypeClassifier;
  private readonly categoryResolvers: Map<string, CategoryResolver>;
  private readonly nullCategoryResolver = new NullCategoryResolver();

  constructor(
    private readonly config: Config,
    options: {
      adapters?: MarketplaceAdapter[];
      aiClusterer?: AiClusterer;
      v3Service?: PricingV3Service;
      subtypeClassifier?: SubtypeClassifier;
      categoryResolvers?: Map<string, CategoryResolver>;
    } = {}
  ) {
    this.cache = new Map();
    this.adapters = options.adapters ?? [new MarktplaatsAdapter(), new EbayBrowseAdapter(config)];
    this.aiClusterer =
      options.aiClusterer ??
      new AiClusterer({
        enabled: config.pricing.v4AiNormEnabled ?? true,
        openaiApiKey: config.pricing.openaiApiKey,
        openaiModel: config.pricing.openaiModel,
        timeoutMs: config.pricing.v4TimeoutMs ?? 20000,
      });
    this.v3Service =
      options.v3Service ??
      (config.pricing.v4FallbackToV3 && config.pricing.v3Enabled
        ? new PricingV3Service({
            enabled: config.pricing.v3Enabled ?? false,
            timeoutMs: config.pricing.v3TimeoutMs ?? 15000,
            cacheTtlSeconds: config.pricing.v3CacheTtlSeconds ?? 86400,
            dailyQuota: config.pricing.v3DailyQuota ?? 1000,
            promptVersion: config.pricing.v3PromptVersion ?? '1.0.0',
            openaiApiKey: config.pricing.openaiApiKey,
            openaiModel: config.pricing.openaiModel,
            catalogPath: config.pricing.catalogPath,
          })
        : null);
    this.subtypeClassifier = options.subtypeClassifier ?? new SubtypeClassifier();
    this.categoryResolvers =
      options.categoryResolvers ??
      new Map<string, CategoryResolver>([['ebay', new EbayCategoryResolver()]]);

    this.cleanupInterval = setInterval(() => {
      this.cleanupExpiredCache();
    }, 5 * 60 * 1000);
  }

  stop(): void {
    if (this.cleanupInterval) {
      clearInterval(this.cleanupInterval);
      this.cleanupInterval = null;
    }
    this.cache.clear();
    if (this.v3Service) {
      this.v3Service.stop();
    }
  }

  getCacheStats(): { size: number; maxTtlSeconds: number } {
    return {
      size: this.cache.size,
      maxTtlSeconds: this.config.pricing.v4CacheTtlSeconds ?? 86400,
    };
  }

  async estimateVerifiableRange(request: PricingV4Request): Promise<PricingV4Insights> {
    if (!this.config.pricing.v4Enabled) {
      return {
        status: 'ERROR',
        countryCode: request.countryCode,
        sources: [],
        totalListingsAnalyzed: 0,
        timeWindowDays: this.timeWindowDays,
        confidence: 'LOW',
        fallbackReason: 'Pricing V4 disabled',
      };
    }

    const cacheKey = this.buildCacheKey({
      brand: request.brand,
      productType: request.productType,
      model: request.model,
      condition: request.condition,
      countryCode: request.countryCode,
      variantAttributes: request.variantAttributes,
      completeness: request.completeness,
      identifier: request.identifier,
    });

    const cached = this.cache.get(cacheKey);
    if (cached && cached.expiresAt > Date.now()) {
      recordPricingV4CacheHit(true);
      return cached.insights;
    }
    recordPricingV4CacheHit(false);

    const preferred = request.preferredMarketplaces?.map((id) => id.toLowerCase());
    const activeAdapters = preferred?.length
      ? this.adapters.filter((adapter) => preferred.includes(adapter.id))
      : this.adapters;

    const adapterResults = await Promise.all(
      activeAdapters.map(async (adapter) => {
        const queryPlan = await this.buildPlanForAdapter(request, adapter.id);
        this.logQueryPlan(adapter.id, queryPlan);
        const listingQueries = this.buildListingQueries(request, queryPlan);
        const queryResults = await Promise.all(
          listingQueries.map((query) => this.fetchWithTimeout(adapter, query))
        );
        const listings = queryResults.flatMap((result) => result.listings);
        const error = queryResults.find((result) => result.error)?.error;
        const timedOut = queryResults.some((result) => result.timedOut);
        return { adapter, listings, error, timedOut, queryPlan, listingQueries };
      })
    );

    const sources = adapterResults.map((result) => ({
      id: result.adapter.id,
      name: result.adapter.name,
      baseUrl: this.getMarketplaceBaseUrl(result.adapter.id),
      listingCount: result.listings.length,
      searchUrl: result.adapter.buildSearchUrl(result.listingQueries[0]),
    }));

    const combinedListings = adapterResults.flatMap((result) => result.listings);
    recordPricingV4ListingsFetched(combinedListings.length);
    const hadErrors = adapterResults.some((result) => result.error);
    const hadTimeouts = adapterResults.some((result) => result.timedOut);
    const basePlan = adapterResults[0]?.queryPlan;

    if (combinedListings.length === 0) {
      if ((hadErrors || hadTimeouts) && this.config.pricing.v4FallbackToV3 && this.v3Service) {
        const fallback = await this.estimateFallbackFromV3(request, sources);
        this.storeCache(cacheKey, fallback);
        return fallback;
      }

      const status = hadTimeouts ? 'TIMEOUT' : hadErrors ? 'FALLBACK' : 'NO_RESULTS';
      const insights: PricingV4Insights = {
        status,
        countryCode: request.countryCode,
        sources,
        totalListingsAnalyzed: 0,
        timeWindowDays: this.timeWindowDays,
        confidence: 'LOW',
        fallbackReason: status === 'FALLBACK' ? 'Marketplace adapters failed' : undefined,
      };
      this.storeCache(cacheKey, insights);
      return insights;
    }

    const filtered = filterListings(combinedListings, this.buildListingFilterOptions(basePlan));
    const postFiltered = basePlan
      ? applyPostFilterRules(filtered, basePlan.postFilterRules)
      : { kept: filtered, excluded: [] };
    const policyFiltered = postFiltered.kept;
    const noiseRatio =
      combinedListings.length > 0
        ? (combinedListings.length - policyFiltered.length) / combinedListings.length
        : 0;

    const shouldNormalize =
      (this.config.pricing.v4AiNormEnabled ?? true) && noiseRatio > 0.3;

    const normalized = shouldNormalize
      ? await this.aiClusterer.normalize({
          listings: policyFiltered,
          targetBrand: request.brand,
          targetModel: this.buildModelWithVariants(request),
          targetProductType: request.productType,
          variantAttributes: request.variantAttributes,
          completeness: request.completeness,
          identifier: request.identifier,
        })
      : {
          relevantListings: policyFiltered,
          excludedListings: [],
          clusterSummary: 'AI normalization skipped',
        };

    const relevantListings = normalized.relevantListings;
    const aggregation = aggregatePrices(relevantListings);

    if (!aggregation) {
      const insights: PricingV4Insights = {
        status: 'NO_RESULTS',
        countryCode: request.countryCode,
        sources,
        totalListingsAnalyzed: relevantListings.length,
        timeWindowDays: this.timeWindowDays,
        confidence: 'LOW',
      };
      this.storeCache(cacheKey, insights);
      return insights;
    }

    const currency = this.pickCurrency(relevantListings);
    const sampleListings = this.selectSampleListings(relevantListings, 3);
    const confidence = this.deriveConfidence(aggregation.sampleSize, noiseRatio);

    const insights: PricingV4Insights = {
      status: 'OK',
      countryCode: request.countryCode,
      sources,
      totalListingsAnalyzed: aggregation.sampleSize,
      timeWindowDays: this.timeWindowDays,
      range: {
        low: aggregation.low,
        median: aggregation.median,
        high: aggregation.high,
        currency,
      },
      sampleListings,
      confidence,
    };

    this.storeCache(cacheKey, insights);

    return insights;
  }

  private buildCacheKey(components: PricingV4CacheKeyComponents): string {
    const normalizedVariantAttributes = this.normalizeVariantAttributes(components.variantAttributes);
    const normalizedCompleteness = this.normalizeCompleteness(components.completeness);
    const normalized = JSON.stringify({
      brand: components.brand.trim().toLowerCase(),
      productType: components.productType.trim().toLowerCase(),
      model: components.model?.trim().toLowerCase() ?? '',
      condition: components.condition,
      countryCode: components.countryCode.trim().toUpperCase(),
      variantAttributes: normalizedVariantAttributes,
      completeness: normalizedCompleteness,
      identifier: components.identifier?.trim().toLowerCase() ?? undefined,
    });

    return `pricing:v4:${sha256Hex(normalized)}`;
  }

  private buildListingQueryFromPlan(
    request: PricingV4Request,
    queryPlan: QueryPlan | undefined
  ): ListingQuery {
    const modelWithVariants = this.buildModelWithVariants(request);
    return {
      brand: request.brand,
      model: modelWithVariants,
      productType: request.productType,
      condition: request.condition,
      countryCode: request.countryCode,
      maxResults: 30,
      q: queryPlan?.q,
      categoryId: queryPlan?.categoryId,
      filters: queryPlan?.filters ?? [],
      postFilterRules: queryPlan?.postFilterRules ?? [],
    };
  }

  private buildListingQueries(request: PricingV4Request, queryPlan: QueryPlan): ListingQuery[] {
    const baseQuery = this.buildListingQueryFromPlan(request, queryPlan);
    const queries = [baseQuery];
    const identifier = request.identifier?.trim();
    if (identifier) {
      queries.push({
        ...baseQuery,
        brand: '',
        productType: '',
        model: identifier,
        q: identifier,
      });
    }
    return queries;
  }

  private buildModelWithVariants(request: PricingV4Request): string {
    const variantText = this.buildVariantText(request.variantAttributes);
    return [request.model, variantText].filter(Boolean).join(' ').trim();
  }

  private buildVariantText(variantAttributes?: Record<string, string>): string {
    if (!variantAttributes) {
      return '';
    }
    const entries = Object.entries(variantAttributes)
      .map(([key, value]) => [key.trim(), value.trim()] as const)
      .filter(([, value]) => Boolean(value))
      .sort(([a], [b]) => a.localeCompare(b));
    const values = entries.map(([, value]) => value);
    const unique = Array.from(new Set(values));
    return unique.join(' ');
  }

  private normalizeVariantAttributes(
    variantAttributes?: Record<string, string>
  ): Record<string, string> | undefined {
    if (!variantAttributes) {
      return undefined;
    }
    const entries = Object.entries(variantAttributes)
      .map(([key, value]) => [key.trim().toLowerCase(), value.trim().toLowerCase()] as const)
      .filter(([key, value]) => Boolean(key && value))
      .sort(([a], [b]) => a.localeCompare(b));
    return entries.length ? Object.fromEntries(entries) : undefined;
  }

  private normalizeCompleteness(completeness?: string[]): string[] | undefined {
    if (!completeness) {
      return undefined;
    }
    const normalized = completeness
      .map((value) => value.trim().toLowerCase())
      .filter(Boolean)
      .sort((a, b) => a.localeCompare(b));
    return normalized.length ? normalized : undefined;
  }

  private async fetchWithTimeout(
    adapter: MarketplaceAdapter,
    query: ListingQuery
  ): Promise<{
    listings: FetchedListing[];
    error?: string;
    timedOut?: boolean;
  }> {
    const timeoutMs = 5000;

    const startTime = Date.now();
    const timeoutPromise = new Promise<never>((_, reject) => {
      const timer = setTimeout(() => {
        clearTimeout(timer);
        reject(new Error('Adapter timeout'));
      }, timeoutMs);
    });

    try {
      const listings = await Promise.race([adapter.fetchListings(query), timeoutPromise]);
      const latencyMs = Date.now() - startTime;
      recordPricingV4AdapterResult(adapter.id, 'success', latencyMs);
      return { listings };
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unknown error';
      const latencyMs = Date.now() - startTime;
      const status = message.toLowerCase().includes('timeout') ? 'timeout' : 'error';
      recordPricingV4AdapterResult(adapter.id, status, latencyMs);
      return {
        listings: [],
        error: message,
        timedOut: message.toLowerCase().includes('timeout'),
      };
    }
  }

  private getMarketplaceBaseUrl(id: string): string {
    switch (id) {
      case 'marktplaats':
        return 'https://www.marktplaats.nl';
      case 'ebay':
        return 'https://www.ebay.nl';
      default:
        return '';
    }
  }

  private selectSampleListings(listings: NormalizedListing[], max: number) {
    const picked: NormalizedListing[] = [];
    const seenConditions = new Set<string>();

    for (const listing of listings) {
      const condition = listing.normalizedCondition ?? listing.condition ?? 'UNKNOWN';
      if (!seenConditions.has(condition) || picked.length === 0) {
        picked.push(listing);
        seenConditions.add(condition);
      }
      if (picked.length >= max) break;
    }

    if (picked.length < max) {
      for (const listing of listings) {
        if (picked.includes(listing)) continue;
        picked.push(listing);
        if (picked.length >= max) break;
      }
    }

    return picked.slice(0, max).map((listing) => ({
      title: listing.title,
      price: listing.price,
      currency: listing.currency,
      condition: listing.normalizedCondition ?? listing.condition,
      url: listing.url,
      marketplace: listing.marketplace,
    }));
  }

  private pickCurrency(listings: NormalizedListing[]): string {
    return listings[0]?.currency ?? 'EUR';
  }

  private deriveConfidence(sampleSize: number, noiseRatio: number): 'HIGH' | 'MED' | 'LOW' {
    if (sampleSize >= 10 && noiseRatio < 0.3) return 'HIGH';
    if (sampleSize >= 5) return 'MED';
    return 'LOW';
  }

  private storeCache(key: string, insights: PricingV4Insights): void {
    this.cache.set(key, {
      insights,
      expiresAt: Date.now() + (this.config.pricing.v4CacheTtlSeconds ?? 86400) * 1000,
    });
  }

  private async estimateFallbackFromV3(
    request: PricingV4Request,
    sources: PricingV4Insights['sources']
  ): Promise<PricingV4Insights> {
    const v3Request = {
      itemId: request.itemId,
      brand: request.brand,
      productType: request.productType,
      model: request.model,
      condition: request.condition,
      countryCode: request.countryCode,
    };

    const v3 = await this.v3Service!.estimateResalePrice(v3Request);
    return this.mapV3ToV4(v3, request.countryCode, sources);
  }

  private mapV3ToV4(
    v3: PricingV3Insights,
    countryCode: string,
    sources: PricingV4Insights['sources']
  ): PricingV4Insights {
    if (v3.status === 'NO_RESULTS') {
      return {
        status: 'NO_RESULTS',
        countryCode,
        sources,
        totalListingsAnalyzed: v3.resultCount ?? 0,
        timeWindowDays: this.timeWindowDays,
        confidence: 'LOW',
        fallbackReason: 'Fallback to V3 yielded no results',
      };
    }

    if (v3.status !== 'OK' || !v3.range) {
      return {
        status: v3.status === 'TIMEOUT' ? 'TIMEOUT' : 'ERROR',
        countryCode,
        sources,
        totalListingsAnalyzed: v3.resultCount ?? 0,
        timeWindowDays: this.timeWindowDays,
        confidence: 'LOW',
        fallbackReason: 'Fallback to V3 failed',
      };
    }

    const median = (v3.range.low + v3.range.high) / 2;
    return {
      status: 'FALLBACK',
      countryCode,
      sources,
      totalListingsAnalyzed: v3.resultCount ?? 0,
      timeWindowDays: this.timeWindowDays,
      range: {
        low: v3.range.low,
        median,
        high: v3.range.high,
        currency: v3.range.currency,
      },
      confidence: v3.confidence ?? 'LOW',
      fallbackReason: 'Marketplace adapters failed; V3 estimate used',
    };
  }

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
      console.log(`[PricingV4Service] Cleaned up ${removed} expired cache entries`);
    }
  }

  private async buildPlanForAdapter(
    request: PricingV4Request,
    marketplaceId: string
  ): Promise<QueryPlan> {
    const modelWithVariants = this.buildModelWithVariants(request);
    const variantText = this.buildVariantText(request.variantAttributes);
    const freeText = request.model ? undefined : variantText || undefined;
    const context = buildCatalogQueryContext(
      {
        subtype: request.productType,
        brand: request.brand,
        model: modelWithVariants,
        freeText,
        condition: request.condition,
        identifier: request.identifier,
      },
      this.subtypeClassifier
    );
    const resolver = this.getCategoryResolver(marketplaceId);
    return buildQueryPlan(context, marketplaceId, resolver);
  }

  private getCategoryResolver(marketplaceId: string): CategoryResolver {
    return this.categoryResolvers.get(marketplaceId) ?? this.nullCategoryResolver;
  }

  private buildListingFilterOptions(queryPlan: QueryPlan | undefined): ListingFilterOptions {
    if (!queryPlan) return {};
    const options: ListingFilterOptions = {};
    for (const filter of queryPlan.filters) {
      if (filter.type === 'excludeParts') {
        options.excludeParts = filter.value;
      }
      if (filter.type === 'excludeBundles') {
        options.excludeBundles = filter.value;
      }
    }
    return options;
  }

  private logQueryPlan(marketplaceId: string, queryPlan: QueryPlan): void {
    console.log('[PricingV4Service] Query plan', {
      marketplaceId,
      q: queryPlan.q,
      categoryId: queryPlan.categoryId,
      filters: queryPlan.filters,
      postFilterRules: queryPlan.postFilterRules,
      subtypeClass: queryPlan.telemetry.subtypeClass,
      categorySource: queryPlan.telemetry.categoryResolution.source,
      categoryConfidence: queryPlan.telemetry.categoryResolution.confidence,
      warnings: queryPlan.telemetry.warnings,
    });
  }
}
