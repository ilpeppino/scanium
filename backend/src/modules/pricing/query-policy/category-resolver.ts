import { CatalogQueryContext, CategoryResolution } from './types.js';

export interface CategoryResolver {
  resolve(context: CatalogQueryContext, marketplace: string): Promise<CategoryResolution>;
}

export class NullCategoryResolver implements CategoryResolver {
  async resolve(): Promise<CategoryResolution> {
    return {
      confidence: 'low',
      source: 'none',
    };
  }
}
