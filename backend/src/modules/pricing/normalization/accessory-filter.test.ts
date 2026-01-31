import { describe, expect, it } from 'vitest';
import { applyAccessoryFilter } from './accessory-filter.js';
import { FetchedListing } from '../types-v4.js';

const listings: FetchedListing[] = [
  {
    title: 'iPhone 13 Case - Black',
    price: 15,
    currency: 'EUR',
    url: 'https://example.com/case',
    marketplace: 'ebay',
  },
  {
    title: 'Samsung Galaxy Screen Protector 2-pack',
    price: 9,
    currency: 'EUR',
    url: 'https://example.com/protector',
    marketplace: 'ebay',
  },
  {
    title: 'Apple iPhone 13 128GB',
    price: 450,
    currency: 'EUR',
    url: 'https://example.com/phone',
    marketplace: 'ebay',
  },
  {
    title: 'Apple iPhone 13 - with charger',
    price: 480,
    currency: 'EUR',
    url: 'https://example.com/phone2',
    marketplace: 'ebay',
  },
];

describe('applyAccessoryFilter', () => {
  it('removes accessory listings based on keyword rules', () => {
    const config = {
      version: '1.0.0',
      defaults: {
        fallback_min_results: 1,
        strong_exclude_keywords: ['case', 'screen protector'],
        exclude_keywords: ['charger'],
        negative_keywords: [],
        heuristic_keywords: [],
      },
      groups: {},
      subtypes: {},
    };

    const result = applyAccessoryFilter(listings, 'electronics_phone', config);

    expect(result.listings).toHaveLength(2);
    expect(result.listings.some((listing) => listing.title.includes('Case'))).toBe(false);
    expect(result.listings.some((listing) => listing.title.includes('Screen Protector'))).toBe(false);
    expect(result.diagnostics.removed).toBe(2);
    expect(result.diagnostics.fallbackUsed).toBe(false);
  });

  it('falls back when filtering is too aggressive', () => {
    const config = {
      version: '1.0.0',
      defaults: {
        fallback_min_results: 5,
        strong_exclude_keywords: ['case', 'screen protector'],
        exclude_keywords: ['charger'],
        negative_keywords: [],
        heuristic_keywords: [],
      },
      groups: {},
      subtypes: {},
    };

    const result = applyAccessoryFilter(listings, 'electronics_phone', config);

    expect(result.diagnostics.fallbackUsed).toBe(true);
    expect(result.listings).toHaveLength(listings.length);
    expect(result.diagnostics.fallbackMinResults).toBe(5);
  });
});
