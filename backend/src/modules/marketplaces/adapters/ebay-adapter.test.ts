import { describe, expect, it, vi } from 'vitest';
import { EbayBrowseAdapter } from './ebay-adapter.js';
import { Config } from '../../../config/index.js';
import { ListingQuery } from '../../pricing/types-v4.js';

const baseConfig = {
  ebay: {
    env: 'sandbox',
    clientId: 'client-id',
    clientSecret: 'client-secret',
    scopes: 'https://api.ebay.com/oauth/api_scope',
    redirectPath: '/auth/ebay/callback',
    tokenEncryptionKey: 'x'.repeat(32),
  },
} as unknown as Config;

const query: ListingQuery = {
  brand: 'Apple',
  model: 'iPhone 13',
  productType: 'smartphone',
  condition: 'GOOD',
  countryCode: 'NL',
  maxResults: 3,
};

describe('EbayBrowseAdapter', () => {
  it('builds a search URL with encoded query', () => {
    const adapter = new EbayBrowseAdapter(baseConfig, { fetcher: vi.fn() });
    const url = adapter.buildSearchUrl(query);
    expect(url).toContain('https://www.ebay.nl/sch/i.html?_nkw=');
    expect(url).toContain('Apple%20iPhone%2013%20smartphone');
  });

  it('returns empty list when credentials are missing', async () => {
    const fetcher = vi.fn();
    const adapter = new EbayBrowseAdapter(
      {
        ebay: {
          ...baseConfig.ebay,
          clientId: '',
        },
      } as unknown as Config,
      { fetcher }
    );

    const results = await adapter.fetchListings(query);
    expect(results).toEqual([]);
    expect(fetcher).not.toHaveBeenCalled();
  });

  it('fetches and parses listings from Browse API', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ access_token: 'token', expires_in: 3600 }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          itemSummaries: [
            {
              title: 'Apple iPhone 13',
              itemWebUrl: 'https://example.com/item/1',
              condition: 'Used',
              price: { value: '499.99', currency: 'EUR' },
            },
            {
              title: 'Apple iPhone 13 (broken)',
              itemWebUrl: 'https://example.com/item/2',
              condition: 'For parts',
              price: { value: '0', currency: 'EUR' },
            },
          ],
        }),
      });

    const adapter = new EbayBrowseAdapter(baseConfig, { fetcher });
    const results = await adapter.fetchListings(query);

    expect(results).toHaveLength(1);
    expect(results[0]).toEqual({
      title: 'Apple iPhone 13',
      price: 499.99,
      currency: 'EUR',
      condition: 'Used',
      url: 'https://example.com/item/1',
      marketplace: 'ebay',
    });
  });

  it('caches access token between calls', async () => {
    let now = Date.now();
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ access_token: 'token', expires_in: 3600 }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ itemSummaries: [] }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ itemSummaries: [] }),
      });

    const adapter = new EbayBrowseAdapter(baseConfig, {
      fetcher,
      now: () => now,
    });

    await adapter.fetchListings(query);
    now += 5_000;
    await adapter.fetchListings(query);

    expect(fetcher).toHaveBeenCalledTimes(3);
  });
});
