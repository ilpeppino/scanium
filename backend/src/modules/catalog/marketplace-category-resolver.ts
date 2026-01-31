import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { z } from 'zod';

type EbayCategoryMapping = {
  sacat?: string;
};

type MarketplaceCategoryMap = {
  version: string;
  ebay?: Record<string, EbayCategoryMapping>;
};

const marketplaceCategoryMapSchema = z
  .object({
    version: z.string().min(1),
    ebay: z
      .record(
        z.string().min(1),
        z
          .object({
            sacat: z.string().min(1).optional(),
          })
          .passthrough()
      )
      .optional(),
  })
  .passthrough();

let cachedMap: MarketplaceCategoryMap | null = null;

function loadMarketplaceCategoryMap(): MarketplaceCategoryMap {
  if (cachedMap) {
    return cachedMap;
  }

  const primaryPath = resolve(process.cwd(), 'config', 'marketplace_category_map_v1.json');
  const fallbackPath = resolve(process.cwd(), 'backend', 'config', 'marketplace_category_map_v1.json');
  const filePath = existsSync(primaryPath) ? primaryPath : fallbackPath;

  try {
    const raw = readFileSync(filePath, 'utf-8');
    const parsed = JSON.parse(raw);
    const result = marketplaceCategoryMapSchema.safeParse(parsed);

    if (!result.success) {
      console.warn('[MarketplaceCategoryMap] Invalid mapping file, using empty map', {
        filePath,
        issues: result.error.issues.map((issue) => ({
          path: issue.path.join('.'),
          message: issue.message,
        })),
      });
      cachedMap = { version: 'invalid', ebay: {} };
      return cachedMap;
    }

    cachedMap = result.data;
    return cachedMap;
  } catch (error) {
    console.warn('[MarketplaceCategoryMap] Failed to load mapping file, using empty map', {
      filePath,
      error: error instanceof Error ? error.message : String(error),
    });
    cachedMap = { version: 'missing', ebay: {} };
    return cachedMap;
  }
}

function normalizeSubtype(subtype: string): string {
  return subtype.trim().toLowerCase();
}

export function resolveEbayCategory(subtype: string): EbayCategoryMapping {
  const normalized = normalizeSubtype(subtype);
  if (!normalized) {
    return {};
  }

  const map = loadMarketplaceCategoryMap();
  const entry = map.ebay?.[normalized];

  if (!entry?.sacat) {
    return {};
  }

  return { sacat: entry.sacat };
}
