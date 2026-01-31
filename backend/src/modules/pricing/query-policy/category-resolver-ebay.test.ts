import { describe, expect, it } from 'vitest';
import { join } from 'node:path';
import { mkdirSync, rmSync } from 'node:fs';
import { EbayCategoryResolver } from './category-resolver-ebay.js';
import { CategoryResolutionCache } from './category-cache.js';
import { CatalogQueryContext } from './types.js';

const baseContext: CatalogQueryContext = {
  subtype: 'electronics_smartphone',
  subtypeClass: 'device',
  brand: 'Apple',
  model: 'iPhone 13',
};

describe('EbayCategoryResolver', () => {
  it('resolves with precedence override > cache > suggestion > none', async () => {
    const cacheDir = join(process.cwd(), 'tmp', 'test-category-cache');
    mkdirSync(cacheDir, { recursive: true });
    const cachePath = join(cacheDir, 'cache.json');
    const cache = new CategoryResolutionCache({ filePath: cachePath, ttlMs: 60_000 });

    const overrides = {
      ebay: { electronics_smartphone: 'override-id' },
    };

    const resolver = new EbayCategoryResolver({
      overrides,
      cache,
      suggestionProvider: async () => 'suggested-id',
    });

    const overrideResult = await resolver.resolve(baseContext, 'ebay');
    expect(overrideResult.categoryId).toBe('override-id');
    expect(overrideResult.source).toBe('override');

    cache.set('ebay:electronics_camera', 'cached-id');
    const cacheResult = await resolver.resolve(
      { ...baseContext, subtype: 'electronics_camera' },
      'ebay'
    );
    expect(cacheResult.categoryId).toBe('cached-id');
    expect(cacheResult.source).toBe('cache');

    const suggestionResult = await resolver.resolve(
      { ...baseContext, subtype: 'furniture_table' },
      'ebay'
    );
    expect(suggestionResult.categoryId).toBe('suggested-id');
    expect(suggestionResult.source).toBe('suggestion');

    const noneResolver = new EbayCategoryResolver({ overrides: {}, cache });
    const noneResult = await noneResolver.resolve(
      { ...baseContext, subtype: 'unknown_subtype' },
      'ebay'
    );
    expect(noneResult.categoryId).toBeUndefined();
    expect(noneResult.source).toBe('none');

    rmSync(cacheDir, { recursive: true, force: true });
  });
});
