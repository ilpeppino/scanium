/**
 * Brand Tier Classification
 *
 * Classifies brands into pricing tiers for value estimation.
 * This is a curated list based on general market perception.
 *
 * Tiers:
 * - LUXURY: High-end designer/luxury brands (2.5-5x base price)
 * - PREMIUM: Quality brands with strong resale (1.5-2.5x base price)
 * - MID_RANGE: Solid mainstream brands (1.0-1.5x base price)
 * - BUDGET: Budget/store brands (0.5-1.0x base price)
 * - UNKNOWN: Brand not in dictionary (0.8-1.0x base price)
 */

import { BrandTier, BrandClassification } from './types.js';

/**
 * Brand classifications organized by tier.
 */
const BRAND_CLASSIFICATIONS: BrandClassification[] = [
  // ============ LUXURY TIER ============
  // Fashion/Accessories
  { name: 'LOUIS VUITTON', tier: 'LUXURY' },
  { name: 'GUCCI', tier: 'LUXURY' },
  { name: 'PRADA', tier: 'LUXURY' },
  { name: 'CHANEL', tier: 'LUXURY' },
  { name: 'HERMES', tier: 'LUXURY' },
  { name: 'DIOR', tier: 'LUXURY' },
  { name: 'FENDI', tier: 'LUXURY' },
  { name: 'BOTTEGA VENETA', tier: 'LUXURY' },
  { name: 'BURBERRY', tier: 'LUXURY' },
  { name: 'ROLEX', tier: 'LUXURY' },
  { name: 'OMEGA', tier: 'LUXURY' },
  { name: 'TAG HEUER', tier: 'LUXURY' },
  // Electronics
  { name: 'BANG OLUFSEN', tier: 'LUXURY' },
  { name: 'MCINTOSH', tier: 'LUXURY' },
  // Home
  { name: 'MIELE', tier: 'LUXURY' },
  { name: 'GAGGENAU', tier: 'LUXURY' },
  { name: 'SUB ZERO', tier: 'LUXURY' },
  { name: 'THERMADOR', tier: 'LUXURY' },

  // ============ PREMIUM TIER ============
  // Electronics
  { name: 'APPLE', tier: 'PREMIUM' },
  { name: 'SONY', tier: 'PREMIUM' },
  { name: 'BOSE', tier: 'PREMIUM' },
  { name: 'BANG AND OLUFSEN', tier: 'PREMIUM' },
  { name: 'SENNHEISER', tier: 'PREMIUM' },
  { name: 'MARSHALL', tier: 'PREMIUM' },
  { name: 'DYSON', tier: 'PREMIUM' },
  { name: 'DJI', tier: 'PREMIUM' },
  { name: 'GOPRO', tier: 'PREMIUM' },
  { name: 'NINTENDO', tier: 'PREMIUM' },
  { name: 'PLAYSTATION', tier: 'PREMIUM' },
  { name: 'XBOX', tier: 'PREMIUM' },
  { name: 'CANON', tier: 'PREMIUM' },
  { name: 'NIKON', tier: 'PREMIUM' },
  { name: 'FUJIFILM', tier: 'PREMIUM' },
  // Kitchen
  { name: 'LE CREUSET', tier: 'PREMIUM' },
  { name: 'STAUB', tier: 'PREMIUM' },
  { name: 'ALL CLAD', tier: 'PREMIUM' },
  { name: 'VITAMIX', tier: 'PREMIUM' },
  { name: 'KITCHENAID', tier: 'PREMIUM' },
  { name: 'BREVILLE', tier: 'PREMIUM' },
  { name: 'WILLIAMS SONOMA', tier: 'PREMIUM' },
  // Furniture
  { name: 'HERMAN MILLER', tier: 'PREMIUM' },
  { name: 'STEELCASE', tier: 'PREMIUM' },
  { name: 'POTTERY BARN', tier: 'PREMIUM' },
  { name: 'WEST ELM', tier: 'PREMIUM' },
  { name: 'CB2', tier: 'PREMIUM' },
  { name: 'ARTICLE', tier: 'PREMIUM' },
  // Fashion
  { name: 'COACH', tier: 'PREMIUM' },
  { name: 'KATE SPADE', tier: 'PREMIUM' },
  { name: 'MICHAEL KORS', tier: 'PREMIUM' },
  { name: 'TORY BURCH', tier: 'PREMIUM' },
  { name: 'NORTH FACE', tier: 'PREMIUM' },
  { name: 'PATAGONIA', tier: 'PREMIUM' },
  { name: 'ARCTERYX', tier: 'PREMIUM' },
  { name: 'CANADA GOOSE', tier: 'PREMIUM' },
  // Fitness
  { name: 'PELOTON', tier: 'PREMIUM' },
  { name: 'BOWFLEX', tier: 'PREMIUM' },
  // Tools
  { name: 'SNAP ON', tier: 'PREMIUM' },
  { name: 'FESTOOL', tier: 'PREMIUM' },
  // Outdoor
  { name: 'YETI', tier: 'PREMIUM' },
  { name: 'TRAEGER', tier: 'PREMIUM' },
  // Baby
  { name: 'UPPABABY', tier: 'PREMIUM' },
  // Sleep
  { name: 'CASPER', tier: 'PREMIUM' },
  { name: 'PURPLE', tier: 'PREMIUM' },
  { name: 'TEMPUR', tier: 'PREMIUM' },

  // ============ MID_RANGE TIER ============
  // Electronics
  { name: 'SAMSUNG', tier: 'MID_RANGE' },
  { name: 'LG', tier: 'MID_RANGE' },
  { name: 'PANASONIC', tier: 'MID_RANGE' },
  { name: 'PHILIPS', tier: 'MID_RANGE' },
  { name: 'DELL', tier: 'MID_RANGE' },
  { name: 'HP', tier: 'MID_RANGE' },
  { name: 'LENOVO', tier: 'MID_RANGE' },
  { name: 'ASUS', tier: 'MID_RANGE' },
  { name: 'ACER', tier: 'MID_RANGE' },
  { name: 'MSI', tier: 'MID_RANGE' },
  { name: 'RAZER', tier: 'MID_RANGE' },
  { name: 'LOGITECH', tier: 'MID_RANGE' },
  { name: 'CORSAIR', tier: 'MID_RANGE' },
  { name: 'JBL', tier: 'MID_RANGE' },
  { name: 'HARMAN KARDON', tier: 'MID_RANGE' },
  { name: 'SONOS', tier: 'MID_RANGE' },
  { name: 'ROOMBA', tier: 'MID_RANGE' },
  { name: 'IROBOT', tier: 'MID_RANGE' },
  { name: 'SHARK', tier: 'MID_RANGE' },
  // Kitchen
  { name: 'CUISINART', tier: 'MID_RANGE' },
  { name: 'NINJA', tier: 'MID_RANGE' },
  { name: 'INSTANT POT', tier: 'MID_RANGE' },
  { name: 'KEURIG', tier: 'MID_RANGE' },
  { name: 'NESPRESSO', tier: 'MID_RANGE' },
  { name: 'LODGE', tier: 'MID_RANGE' },
  { name: 'OXON', tier: 'MID_RANGE' },
  { name: 'RUBBERMAID', tier: 'MID_RANGE' },
  { name: 'SIMPLEHUMAN', tier: 'MID_RANGE' },
  // Furniture
  { name: 'IKEA', tier: 'MID_RANGE' },
  { name: 'WAYFAIR', tier: 'MID_RANGE' },
  { name: 'ASHLEY', tier: 'MID_RANGE' },
  { name: 'CRATE', tier: 'MID_RANGE' },
  { name: 'BARREL', tier: 'MID_RANGE' },
  // Fashion
  { name: 'NIKE', tier: 'MID_RANGE' },
  { name: 'ADIDAS', tier: 'MID_RANGE' },
  { name: 'PUMA', tier: 'MID_RANGE' },
  { name: 'NEW BALANCE', tier: 'MID_RANGE' },
  { name: 'COLUMBIA', tier: 'MID_RANGE' },
  { name: 'LEVIS', tier: 'MID_RANGE' },
  // Tools
  { name: 'DEWALT', tier: 'MID_RANGE' },
  { name: 'MAKITA', tier: 'MID_RANGE' },
  { name: 'MILWAUKEE', tier: 'MID_RANGE' },
  { name: 'BOSCH', tier: 'MID_RANGE' },
  { name: 'CRAFTSMAN', tier: 'MID_RANGE' },
  { name: 'STANLEY', tier: 'MID_RANGE' },
  // Outdoor
  { name: 'WEBER', tier: 'MID_RANGE' },
  { name: 'COLEMAN', tier: 'MID_RANGE' },
  { name: 'REI', tier: 'MID_RANGE' },
  // Baby
  { name: 'GRACO', tier: 'MID_RANGE' },
  { name: 'CHICCO', tier: 'MID_RANGE' },
  { name: 'BABY JOGGER', tier: 'MID_RANGE' },
  { name: 'BRITAX', tier: 'MID_RANGE' },
  // Sports
  { name: 'SCHWINN', tier: 'MID_RANGE' },
  { name: 'TREK', tier: 'MID_RANGE' },
  { name: 'GIANT', tier: 'MID_RANGE' },
  { name: 'SPECIALIZED', tier: 'MID_RANGE' },
  // Sleep
  { name: 'SEALY', tier: 'MID_RANGE' },
  { name: 'SERTA', tier: 'MID_RANGE' },

  // ============ BUDGET TIER ============
  // Electronics
  { name: 'TCL', tier: 'BUDGET' },
  { name: 'HISENSE', tier: 'BUDGET' },
  { name: 'VIZIO', tier: 'BUDGET' },
  { name: 'ANKER', tier: 'BUDGET' },
  { name: 'ONN', tier: 'BUDGET' },
  // Fashion
  { name: 'GAP', tier: 'BUDGET' },
  { name: 'ZARA', tier: 'BUDGET' },
  { name: 'HM', tier: 'BUDGET' },
  { name: 'UNIQLO', tier: 'BUDGET' },
  { name: 'OLD NAVY', tier: 'BUDGET' },
  { name: 'TARGET', tier: 'BUDGET' },
  { name: 'WALMART', tier: 'BUDGET' },
  // Tools
  { name: 'RYOBI', tier: 'BUDGET' },
  { name: 'BLACK DECKER', tier: 'BUDGET' },
  { name: 'HUSKY', tier: 'BUDGET' },
  // Home
  { name: 'BISSELL', tier: 'BUDGET' },
  { name: 'HOOVER', tier: 'BUDGET' },
  // Baby/Kids
  { name: 'FISHER PRICE', tier: 'BUDGET' },
  { name: 'LITTLE TIKES', tier: 'BUDGET' },
  { name: 'STEP2', tier: 'BUDGET' },
];

/**
 * Normalized brand lookup map.
 */
const brandLookup = new Map<string, BrandClassification>();

// Build lookup map with normalized keys
for (const classification of BRAND_CLASSIFICATIONS) {
  const normalizedName = normalizeBrandName(classification.name);
  brandLookup.set(normalizedName, classification);
}

/**
 * Normalize brand name for comparison.
 */
function normalizeBrandName(name: string): string {
  return name
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '')
    .trim();
}

/**
 * Get brand tier for a given brand name.
 */
export function getBrandTier(brandName: string | undefined): BrandTier {
  if (!brandName) {
    return 'UNKNOWN';
  }

  const normalized = normalizeBrandName(brandName);
  const classification = brandLookup.get(normalized);

  return classification?.tier ?? 'UNKNOWN';
}

/**
 * Get brand tier multiplier for pricing.
 *
 * Returns [minMultiplier, maxMultiplier] to apply to base price.
 */
export function getBrandMultiplier(tier: BrandTier): [number, number] {
  switch (tier) {
    case 'LUXURY':
      // Luxury items command significant premium
      return [2.5, 5.0];
    case 'PREMIUM':
      // Premium brands hold value well
      return [1.5, 2.5];
    case 'MID_RANGE':
      // Solid mainstream brands
      return [1.0, 1.5];
    case 'BUDGET':
      // Budget brands depreciate more
      return [0.5, 0.9];
    case 'UNKNOWN':
    default:
      // Conservative estimate for unknown brands
      return [0.7, 1.0];
  }
}

/**
 * Get human-readable label for brand tier.
 */
export function getBrandTierLabel(tier: BrandTier): string {
  switch (tier) {
    case 'LUXURY':
      return 'Luxury brand';
    case 'PREMIUM':
      return 'Premium brand';
    case 'MID_RANGE':
      return 'Popular brand';
    case 'BUDGET':
      return 'Budget brand';
    case 'UNKNOWN':
    default:
      return 'Brand not recognized';
  }
}

/**
 * Check if a brand is in the dictionary.
 */
export function isKnownBrand(brandName: string): boolean {
  const normalized = normalizeBrandName(brandName);
  return brandLookup.has(normalized);
}

/**
 * Get all brands in a specific tier.
 */
export function getBrandsInTier(tier: BrandTier): string[] {
  return BRAND_CLASSIFICATIONS
    .filter((b) => b.tier === tier)
    .map((b) => b.name);
}
