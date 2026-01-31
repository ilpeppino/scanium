import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { z } from 'zod';
import { FetchedListing } from '../types-v4.js';

type FilterRule = {
  fallback_min_results?: number;
  strong_exclude_keywords?: string[];
  exclude_keywords?: string[];
  negative_keywords?: string[];
  heuristic_keywords?: string[];
  price_floor_ratio?: number;
  price_floor_min_listings?: number;
};

type GroupRule = FilterRule & { subtypes: string[] };

type PricingFilterConfig = {
  version: string;
  defaults: FilterRule;
  groups?: Record<string, GroupRule>;
  subtypes?: Record<string, FilterRule>;
};

export type AccessoryFilterDiagnostics = {
  totalInput: number;
  kept: number;
  removed: number;
  removedByKeyword: Record<string, number>;
  removedByReason: Record<string, number>;
  fallbackUsed: boolean;
  fallbackReason?: string;
  fallbackMinResults: number;
};

export type AccessoryFilterResult = {
  listings: FetchedListing[];
  diagnostics: AccessoryFilterDiagnostics;
};

const pricingFilterConfigSchema = z
  .object({
    version: z.string().min(1),
    defaults: z
      .object({
        fallback_min_results: z.number().int().min(1).optional(),
        strong_exclude_keywords: z.array(z.string().min(1)).optional(),
        exclude_keywords: z.array(z.string().min(1)).optional(),
        negative_keywords: z.array(z.string().min(1)).optional(),
        heuristic_keywords: z.array(z.string().min(1)).optional(),
        price_floor_ratio: z.number().positive().max(1).optional(),
        price_floor_min_listings: z.number().int().min(1).optional(),
      })
      .passthrough(),
    groups: z
      .record(
        z.string().min(1),
        z
          .object({
            subtypes: z.array(z.string().min(1)),
            fallback_min_results: z.number().int().min(1).optional(),
            strong_exclude_keywords: z.array(z.string().min(1)).optional(),
            exclude_keywords: z.array(z.string().min(1)).optional(),
            negative_keywords: z.array(z.string().min(1)).optional(),
            heuristic_keywords: z.array(z.string().min(1)).optional(),
            price_floor_ratio: z.number().positive().max(1).optional(),
            price_floor_min_listings: z.number().int().min(1).optional(),
          })
          .passthrough()
      )
      .optional(),
    subtypes: z
      .record(
        z.string().min(1),
        z
          .object({
            fallback_min_results: z.number().int().min(1).optional(),
            strong_exclude_keywords: z.array(z.string().min(1)).optional(),
            exclude_keywords: z.array(z.string().min(1)).optional(),
            negative_keywords: z.array(z.string().min(1)).optional(),
            heuristic_keywords: z.array(z.string().min(1)).optional(),
            price_floor_ratio: z.number().positive().max(1).optional(),
            price_floor_min_listings: z.number().int().min(1).optional(),
          })
          .passthrough()
      )
      .optional(),
  })
  .passthrough();

let cachedConfig: PricingFilterConfig | null = null;

function loadPricingFilterConfig(): PricingFilterConfig {
  if (cachedConfig) {
    return cachedConfig;
  }

  const primaryPath = resolve(process.cwd(), 'config', 'pricing_filters_v1.json');
  const fallbackPath = resolve(process.cwd(), 'backend', 'config', 'pricing_filters_v1.json');
  const filePath = existsSync(primaryPath) ? primaryPath : fallbackPath;

  try {
    const raw = readFileSync(filePath, 'utf-8');
    const parsed = JSON.parse(raw);
    const result = pricingFilterConfigSchema.safeParse(parsed);

    if (!result.success) {
      console.warn('[PricingFilter] Invalid config, using defaults only', {
        filePath,
        issues: result.error.issues.map((issue) => ({
          path: issue.path.join('.'),
          message: issue.message,
        })),
      });
      cachedConfig = { version: 'invalid', defaults: {} };
      return cachedConfig;
    }

    cachedConfig = result.data;
    return cachedConfig;
  } catch (error) {
    console.warn('[PricingFilter] Failed to load config, using defaults only', {
      filePath,
      error: error instanceof Error ? error.message : String(error),
    });
    cachedConfig = { version: 'missing', defaults: {} };
    return cachedConfig;
  }
}

function normalizeSubtype(subtype: string): string {
  return subtype.trim().toLowerCase();
}

function normalizeKeywords(values?: string[]): string[] {
  if (!values) return [];
  return values
    .map((value) => value.trim().toLowerCase())
    .filter(Boolean);
}

function mergeRule(defaults: FilterRule, override?: FilterRule): Required<FilterRule> {
  return {
    fallback_min_results: override?.fallback_min_results ?? defaults.fallback_min_results ?? 10,
    strong_exclude_keywords: [
      ...normalizeKeywords(defaults.strong_exclude_keywords),
      ...normalizeKeywords(override?.strong_exclude_keywords),
    ],
    exclude_keywords: [
      ...normalizeKeywords(defaults.exclude_keywords),
      ...normalizeKeywords(override?.exclude_keywords),
    ],
    negative_keywords: [
      ...normalizeKeywords(defaults.negative_keywords),
      ...normalizeKeywords(override?.negative_keywords),
    ],
    heuristic_keywords: [
      ...normalizeKeywords(defaults.heuristic_keywords),
      ...normalizeKeywords(override?.heuristic_keywords),
    ],
    price_floor_ratio: override?.price_floor_ratio ?? defaults.price_floor_ratio ?? 0,
    price_floor_min_listings:
      override?.price_floor_min_listings ?? defaults.price_floor_min_listings ?? 0,
  };
}

function resolveFilterRule(subtype: string, config: PricingFilterConfig): Required<FilterRule> {
  const normalized = normalizeSubtype(subtype);
  const defaults = config.defaults ?? {};

  const subtypeRule = config.subtypes?.[normalized];
  if (subtypeRule) {
    return mergeRule(defaults, subtypeRule);
  }

  const groups = config.groups ?? {};
  for (const group of Object.values(groups)) {
    if (group.subtypes.map(normalizeSubtype).includes(normalized)) {
      return mergeRule(defaults, group);
    }
  }

  return mergeRule(defaults, undefined);
}

function calculateMedian(values: number[]): number | null {
  if (!values.length) return null;
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  if (sorted.length % 2 === 0) {
    return (sorted[mid - 1] + sorted[mid]) / 2;
  }
  return sorted[mid];
}

export function getNegativeKeywordsForSubtype(subtype: string): string[] {
  const config = loadPricingFilterConfig();
  const rule = resolveFilterRule(subtype, config);
  return Array.from(new Set(rule.negative_keywords));
}

export function applyAccessoryFilter(
  listings: FetchedListing[],
  subtype: string,
  configOverride?: PricingFilterConfig
): AccessoryFilterResult {
  const config = configOverride ?? loadPricingFilterConfig();
  const rule = resolveFilterRule(subtype, config);

  const strongKeywords = Array.from(new Set(rule.strong_exclude_keywords));
  const excludeKeywords = Array.from(new Set(rule.exclude_keywords));
  const heuristicKeywords = Array.from(new Set(rule.heuristic_keywords));
  const fallbackMinResults = rule.fallback_min_results;

  const prices = listings.map((listing) => listing.price).filter((price) => price > 0);
  const medianPrice =
    rule.price_floor_ratio > 0 &&
    rule.price_floor_min_listings > 0 &&
    prices.length >= rule.price_floor_min_listings
      ? calculateMedian(prices)
      : null;
  const priceFloor = medianPrice ? medianPrice * rule.price_floor_ratio : null;

  const removedByKeyword: Record<string, number> = {};
  const removedByReason: Record<string, number> = {};

  function trackRemoval(reason: string, keyword?: string) {
    removedByReason[reason] = (removedByReason[reason] ?? 0) + 1;
    if (keyword) {
      removedByKeyword[keyword] = (removedByKeyword[keyword] ?? 0) + 1;
    }
  }

  function filterWithRules(
    rules: { strong: string[]; exclude: string[] },
    useHeuristic: boolean
  ): FetchedListing[] {
    return listings.filter((listing) => {
      const title = (listing.title ?? '').toLowerCase();

      const strongMatch = rules.strong.find((keyword) => title.includes(keyword));
      if (strongMatch) {
        trackRemoval('strong_keyword', strongMatch);
        return false;
      }

      const excludeMatch = rules.exclude.find((keyword) => title.includes(keyword));
      if (excludeMatch) {
        trackRemoval('keyword', excludeMatch);
        return false;
      }

      if (useHeuristic && priceFloor && listing.price > 0 && listing.price < priceFloor) {
        const heuristicMatch = heuristicKeywords.find((keyword) => title.includes(keyword));
        if (heuristicMatch) {
          trackRemoval('heuristic_low_price', heuristicMatch);
          return false;
        }
      }

      return true;
    });
  }

  const filtered = filterWithRules(
    { strong: strongKeywords, exclude: excludeKeywords },
    true
  );

  if (filtered.length >= fallbackMinResults || listings.length === 0) {
    return {
      listings: filtered,
      diagnostics: {
        totalInput: listings.length,
        kept: filtered.length,
        removed: listings.length - filtered.length,
        removedByKeyword,
        removedByReason,
        fallbackUsed: false,
        fallbackMinResults,
      },
    };
  }

  if (strongKeywords.length > 0) {
    const relaxedRemovedByKeyword: Record<string, number> = {};
    const relaxedRemovedByReason: Record<string, number> = {};
    const originalRemovedByKeyword = { ...removedByKeyword };
    const originalRemovedByReason = { ...removedByReason };

    const relaxedListings = listings.filter((listing) => {
      const title = (listing.title ?? '').toLowerCase();
      const strongMatch = strongKeywords.find((keyword) => title.includes(keyword));
      if (strongMatch) {
        relaxedRemovedByReason['strong_keyword'] =
          (relaxedRemovedByReason['strong_keyword'] ?? 0) + 1;
        relaxedRemovedByKeyword[strongMatch] =
          (relaxedRemovedByKeyword[strongMatch] ?? 0) + 1;
        return false;
      }
      return true;
    });

    if (relaxedListings.length >= fallbackMinResults) {
      return {
        listings: relaxedListings,
        diagnostics: {
          totalInput: listings.length,
          kept: relaxedListings.length,
          removed: listings.length - relaxedListings.length,
          removedByKeyword: relaxedRemovedByKeyword,
          removedByReason: relaxedRemovedByReason,
          fallbackUsed: true,
          fallbackReason: 'relaxed_to_strong_keywords',
          fallbackMinResults,
        },
      };
    }

    return {
      listings,
      diagnostics: {
        totalInput: listings.length,
        kept: listings.length,
        removed: 0,
        removedByKeyword: originalRemovedByKeyword,
        removedByReason: originalRemovedByReason,
        fallbackUsed: true,
        fallbackReason: 'disabled_filtering',
        fallbackMinResults,
      },
    };
  }

  return {
    listings,
    diagnostics: {
      totalInput: listings.length,
      kept: listings.length,
      removed: 0,
      removedByKeyword,
      removedByReason,
      fallbackUsed: true,
      fallbackReason: 'disabled_filtering',
      fallbackMinResults,
    },
  };
}
