/**
 * Market Data Loader
 *
 * Loads and caches market data from JSON files at startup.
 * All data is static - no runtime refresh (restart required for updates).
 */

import { readFileSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';
import {
  RegionCode,
  CategorySegment,
  RegionalPriceIndex,
  CurrencyConfig,
} from './types-v2.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const DATA_DIR = join(__dirname, 'data');

// ============================================================================
// Data Types (internal)
// ============================================================================

type RegionalIndicesFile = {
  version: string;
  validFrom: string;
  validUntil: string;
  source: string;
  baseline: string;
  regions: Array<{
    region: RegionCode;
    categoryIndices: Partial<Record<CategorySegment, number>>;
    defaultIndex: number;
  }>;
};

type CurrenciesFile = {
  version: string;
  validFrom: string;
  validUntil: string;
  source: string;
  baseCurrency: string;
  currencies: Record<RegionCode, CurrencyConfig>;
};

type SeasonalityFile = {
  version: string;
  validFrom: string;
  source: string;
  patterns: Record<CategorySegment, Record<string, number>>;
};

type UrbanClassificationFile = {
  version: string;
  validFrom: string;
  source: string;
  classifications: Record<
    RegionCode,
    {
      urbanPrefixes: string[];
      notes?: string;
    }
  >;
};

// ============================================================================
// Cached Data
// ============================================================================

let regionalIndices: Map<RegionCode, RegionalPriceIndex> | null = null;
let currencies: Map<RegionCode, CurrencyConfig> | null = null;
let seasonality: Map<CategorySegment, Record<number, number>> | null = null;
let urbanPrefixes: Map<RegionCode, Set<string>> | null = null;
let loadedAt: Date | null = null;

// ============================================================================
// Loaders
// ============================================================================

function loadJSON<T>(relativePath: string): T {
  const fullPath = join(DATA_DIR, relativePath);
  const content = readFileSync(fullPath, 'utf-8');
  return JSON.parse(content) as T;
}

function loadRegionalIndices(): Map<RegionCode, RegionalPriceIndex> {
  const data = loadJSON<RegionalIndicesFile>('regional-indices/2025-v1.json');
  const map = new Map<RegionCode, RegionalPriceIndex>();

  for (const region of data.regions) {
    map.set(region.region, {
      region: region.region,
      categoryIndices: region.categoryIndices,
      defaultIndex: region.defaultIndex,
      validFrom: data.validFrom,
      validUntil: data.validUntil,
      source: data.source,
    });
  }

  return map;
}

function loadCurrencies(): Map<RegionCode, CurrencyConfig> {
  const data = loadJSON<CurrenciesFile>('currencies/2025-01.json');
  const map = new Map<RegionCode, CurrencyConfig>();

  for (const [region, config] of Object.entries(data.currencies)) {
    map.set(region as RegionCode, config);
  }

  return map;
}

function loadSeasonality(): Map<CategorySegment, Record<number, number>> {
  const data = loadJSON<SeasonalityFile>('seasonality/patterns-v1.json');
  const map = new Map<CategorySegment, Record<number, number>>();

  for (const [segment, monthlyFactors] of Object.entries(data.patterns)) {
    // Convert string keys to numbers
    const factors: Record<number, number> = {};
    for (const [month, factor] of Object.entries(monthlyFactors)) {
      factors[parseInt(month, 10)] = factor;
    }
    map.set(segment as CategorySegment, factors);
  }

  return map;
}

function loadUrbanPrefixes(): Map<RegionCode, Set<string>> {
  const data = loadJSON<UrbanClassificationFile>('urban-classification/eu-us-uk-2025.json');
  const map = new Map<RegionCode, Set<string>>();

  for (const [region, classification] of Object.entries(data.classifications)) {
    map.set(region as RegionCode, new Set(classification.urbanPrefixes));
  }

  return map;
}

// ============================================================================
// Public API
// ============================================================================

/**
 * Initialize market data (call at startup).
 */
export function initializeMarketData(): void {
  regionalIndices = loadRegionalIndices();
  currencies = loadCurrencies();
  seasonality = loadSeasonality();
  urbanPrefixes = loadUrbanPrefixes();
  loadedAt = new Date();
}

/**
 * Ensure data is loaded (lazy initialization).
 */
function ensureLoaded(): void {
  if (!loadedAt) {
    initializeMarketData();
  }
}

/**
 * Get regional price index for a region and category.
 */
export function getRegionalIndex(
  region: RegionCode,
  segment: CategorySegment
): number {
  ensureLoaded();

  const regionData = regionalIndices!.get(region);
  if (!regionData) {
    // Unknown region - use US baseline
    return 1.0;
  }

  return regionData.categoryIndices[segment] ?? regionData.defaultIndex;
}

/**
 * Get currency configuration for a region.
 */
export function getCurrencyForRegion(region: RegionCode): CurrencyConfig {
  ensureLoaded();

  const currency = currencies!.get(region);
  if (!currency) {
    // Default to USD
    return { code: 'USD', symbol: '$', rate: 1.0, decimals: 2 };
  }

  return currency;
}

/**
 * Get seasonality factor for a category and month.
 */
export function getSeasonalityFactor(
  segment: CategorySegment,
  month: number
): number {
  ensureLoaded();

  const segmentFactors = seasonality!.get(segment);
  if (!segmentFactors) {
    return 1.0; // No seasonality data - neutral
  }

  return segmentFactors[month] ?? 1.0;
}

/**
 * Check if a postal code is classified as urban.
 */
export function isPostalCodeUrban(
  postalCode: string,
  region: RegionCode
): boolean {
  ensureLoaded();

  const prefixes = urbanPrefixes!.get(region);
  if (!prefixes) {
    return false; // Unknown region - assume non-urban (conservative)
  }

  // Normalize postal code (uppercase, no spaces)
  const normalized = postalCode.toUpperCase().replace(/\s/g, '');

  // Check if any prefix matches
  for (const prefix of prefixes) {
    if (normalized.startsWith(prefix)) {
      return true;
    }
  }

  return false;
}

/**
 * Get all supported regions.
 */
export function getSupportedRegions(): RegionCode[] {
  ensureLoaded();
  return Array.from(regionalIndices!.keys());
}

/**
 * Get market data load timestamp.
 */
export function getMarketDataLoadedAt(): Date | null {
  return loadedAt;
}

/**
 * Force reload market data (for testing).
 */
export function reloadMarketData(): void {
  loadedAt = null;
  initializeMarketData();
}
