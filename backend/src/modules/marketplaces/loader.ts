import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { marketplacesCatalogSchema, MarketplacesCatalog } from './schema.js';
import { ZodError } from 'zod';

/**
 * Loader result containing validated catalog or error details
 */
export type LoadResult =
  | { success: true; catalog: MarketplacesCatalog }
  | { success: false; error: string; zodError?: ZodError };

/**
 * Load and validate marketplaces catalog from JSON file
 * @param filePath - Path to marketplaces JSON file (relative to project root or absolute)
 * @returns LoadResult with either validated catalog or error
 */
export function loadMarketplacesCatalog(filePath: string): LoadResult {
  try {
    // Resolve path (support both absolute and relative from backend root)
    const resolvedPath = filePath.startsWith('/')
      ? filePath
      : join(process.cwd(), filePath);

    // Read file synchronously (ok for startup/config loading)
    const rawJson = readFileSync(resolvedPath, 'utf-8');

    // Parse JSON
    const parsed = JSON.parse(rawJson);

    // Validate with Zod schema
    const validated = marketplacesCatalogSchema.parse(parsed);

    return {
      success: true,
      catalog: validated,
    };
  } catch (error) {
    if (error instanceof ZodError) {
      // Format Zod validation errors for logging
      const errorMessages = error.errors.map((err) => {
        const path = err.path.join('.');
        return `${path}: ${err.message}`;
      });
      return {
        success: false,
        error: `Marketplaces catalog validation failed:\n  ${errorMessages.join('\n  ')}`,
        zodError: error,
      };
    }

    if (error instanceof SyntaxError) {
      return {
        success: false,
        error: `Marketplaces catalog JSON parse error: ${error.message}`,
      };
    }

    if (error instanceof Error && 'code' in error && error.code === 'ENOENT') {
      return {
        success: false,
        error: `Marketplaces catalog file not found: ${filePath}`,
      };
    }

    return {
      success: false,
      error: `Failed to load marketplaces catalog: ${error instanceof Error ? error.message : String(error)}`,
    };
  }
}

/**
 * In-memory cache for loaded catalog
 * Prevents re-reading/re-parsing on every service call
 */
let cachedCatalog: MarketplacesCatalog | null = null;
let cacheError: string | null = null;

/**
 * Load catalog with memoization
 * Safe to call multiple times - only loads once
 */
export function getCachedCatalog(filePath: string): LoadResult {
  if (cachedCatalog) {
    return { success: true, catalog: cachedCatalog };
  }

  if (cacheError) {
    return { success: false, error: cacheError };
  }

  const result = loadMarketplacesCatalog(filePath);

  if (result.success) {
    cachedCatalog = result.catalog;
  } else {
    cacheError = result.error;
  }

  return result;
}

/**
 * Clear cache (useful for testing or hot-reload in development)
 */
export function clearCatalogCache(): void {
  cachedCatalog = null;
  cacheError = null;
}
