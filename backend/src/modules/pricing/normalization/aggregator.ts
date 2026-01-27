import { NormalizedListing } from '../types-v4.js';

export type PriceAggregation = {
  low: number;
  median: number;
  high: number;
  sampleSize: number;
  outliersRemoved: number;
};

function percentile(sortedValues: number[], p: number): number {
  if (sortedValues.length === 0) {
    return 0;
  }

  const clamped = Math.min(100, Math.max(0, p));
  const index = (clamped / 100) * (sortedValues.length - 1);
  const lower = Math.floor(index);
  const upper = Math.ceil(index);

  if (lower === upper) {
    return sortedValues[lower];
  }

  const weight = index - lower;
  return sortedValues[lower] * (1 - weight) + sortedValues[upper] * weight;
}

export function aggregatePrices(listings: NormalizedListing[]): PriceAggregation | null {
  const prices = listings.map((listing) => listing.price).filter((price) => Number.isFinite(price));
  if (prices.length === 0) {
    return null;
  }

  const sorted = prices.slice().sort((a, b) => a - b);
  const q1 = percentile(sorted, 25);
  const q3 = percentile(sorted, 75);
  const iqr = q3 - q1;
  const lowerBound = q1 - 1.5 * iqr;
  const upperBound = q3 + 1.5 * iqr;

  const filtered = sorted.filter((price) => price >= lowerBound && price <= upperBound);
  if (filtered.length === 0) {
    return null;
  }

  return {
    low: percentile(filtered, 25),
    median: percentile(filtered, 50),
    high: percentile(filtered, 75),
    sampleSize: filtered.length,
    outliersRemoved: sorted.length - filtered.length,
  };
}
