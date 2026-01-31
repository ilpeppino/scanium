import { join } from 'node:path';
import { CatalogQueryContext, CategoryResolution } from './types.js';
import { CategoryResolutionCache } from './category-cache.js';
import { CategoryResolver } from './category-resolver.js';
import { CATEGORY_OVERRIDES } from './category-overrides.js';

export type CategorySuggestionProvider = (context: CatalogQueryContext) => Promise<string | undefined>;

type EbayCategoryResolverOptions = {
  overrides?: Record<string, Record<string, string>>;
  cache?: CategoryResolutionCache;
  suggestionProvider?: CategorySuggestionProvider;
};

export class EbayCategoryResolver implements CategoryResolver {
  private readonly overrides: Record<string, Record<string, string>>;
  private readonly cache: CategoryResolutionCache;
  private readonly suggestionProvider?: CategorySuggestionProvider;

  constructor(options: EbayCategoryResolverOptions = {}) {
    this.overrides = options.overrides ?? CATEGORY_OVERRIDES;
    this.cache =
      options.cache ??
      new CategoryResolutionCache({
        filePath: join(process.cwd(), 'tmp', 'pricing-category-cache.json'),
      });
    this.suggestionProvider = options.suggestionProvider;
  }

  async resolve(context: CatalogQueryContext, marketplace: string): Promise<CategoryResolution> {
    try {
      const normalizedSubtype = context.subtype.trim().toLowerCase();
      if (!normalizedSubtype) {
        return { confidence: 'low', source: 'none' };
      }

      const override = this.overrides[marketplace]?.[normalizedSubtype];
      if (override) {
        return { categoryId: override, confidence: 'high', source: 'override' };
      }

      const cacheKey = `${marketplace}:${normalizedSubtype}`;
      const cached = this.cache.get(cacheKey);
      if (cached) {
        return { categoryId: cached, confidence: 'medium', source: 'cache' };
      }

      if (this.suggestionProvider) {
        const suggestion = await this.suggestionProvider(context);
        if (suggestion) {
          this.cache.set(cacheKey, suggestion);
          return { categoryId: suggestion, confidence: 'medium', source: 'suggestion' };
        }
      }

      return { confidence: 'low', source: 'none' };
    } catch {
      return { confidence: 'low', source: 'none' };
    }
  }
}
