import { describe, expect, it } from 'vitest';
import { filterListings } from './filters.js';
import { FetchedListing } from '../types-v4.js';

describe('filterListings', () => {
  it('filters out bad listings', () => {
    const listings: FetchedListing[] = [
      {
        title: 'Philips 3200 defect',
        price: 50,
        currency: 'EUR',
        url: 'https://example.com/1',
        marketplace: 'marktplaats',
      },
      {
        title: '2x Philips 3200 bundle',
        price: 200,
        currency: 'EUR',
        url: 'https://example.com/2',
        marketplace: 'marktplaats',
      },
      {
        title: 'Philips 3200',
        price: 0,
        currency: 'EUR',
        url: 'https://example.com/3',
        marketplace: 'marktplaats',
      },
      {
        title: 'Philips 3200',
        price: 12000,
        currency: 'EUR',
        url: 'https://example.com/4',
        marketplace: 'marktplaats',
      },
      {
        title: 'Philips 3200 koffie',
        price: 180,
        currency: 'EUR',
        url: 'https://example.com/5',
        marketplace: 'marktplaats',
      },
    ];

    const result = filterListings(listings);
    expect(result).toHaveLength(1);
    expect(result[0].url).toBe('https://example.com/5');
  });
});
