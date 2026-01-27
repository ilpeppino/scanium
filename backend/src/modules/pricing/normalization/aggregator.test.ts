import { describe, expect, it } from 'vitest';
import { aggregatePrices } from './aggregator.js';
import { NormalizedListing } from '../types-v4.js';

describe('aggregatePrices', () => {
  it('removes outliers using IQR', () => {
    const listings: NormalizedListing[] = [
      { title: 'a', price: 10, currency: 'EUR', url: 'u1', marketplace: 'ebay' },
      { title: 'b', price: 10, currency: 'EUR', url: 'u2', marketplace: 'ebay' },
      { title: 'c', price: 10, currency: 'EUR', url: 'u3', marketplace: 'ebay' },
      { title: 'd', price: 10, currency: 'EUR', url: 'u4', marketplace: 'ebay' },
      { title: 'e', price: 1000, currency: 'EUR', url: 'u5', marketplace: 'ebay' },
    ];

    const result = aggregatePrices(listings);
    expect(result).not.toBeNull();
    expect(result?.outliersRemoved).toBe(1);
    expect(result?.sampleSize).toBe(4);
    expect(result?.low).toBe(10);
    expect(result?.median).toBe(10);
    expect(result?.high).toBe(10);
  });

  it('returns null for empty input', () => {
    const result = aggregatePrices([]);
    expect(result).toBeNull();
  });
});
