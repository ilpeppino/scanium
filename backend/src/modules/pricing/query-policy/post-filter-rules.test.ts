import { describe, expect, it } from 'vitest';
import { applyPostFilterRules } from './post-filter-rules.js';
import { FetchedListing } from '../types-v4.js';

const listings: FetchedListing[] = [
  {
    title: 'iPhone 13 case',
    price: 10,
    currency: 'EUR',
    url: 'https://example.com/1',
    marketplace: 'ebay',
  },
  {
    title: 'iPhone 13',
    price: 300,
    currency: 'EUR',
    url: 'https://example.com/2',
    marketplace: 'ebay',
  },
];

describe('applyPostFilterRules', () => {
  it('filters accessory-like listings when rule is enabled', () => {
    const result = applyPostFilterRules(listings, ['exclude_accessory_like']);
    expect(result.kept).toHaveLength(1);
    expect(result.kept[0].title).toBe('iPhone 13');
  });

  it('does not filter accessory-like listings when rule is disabled', () => {
    const result = applyPostFilterRules(listings, []);
    expect(result.kept).toHaveLength(2);
  });
});
