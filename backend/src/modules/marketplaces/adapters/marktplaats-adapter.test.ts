import { describe, expect, it, vi } from 'vitest';
import { MarktplaatsAdapter } from './marktplaats-adapter.js';
import { ListingQuery } from '../../pricing/types-v4.js';

const query: ListingQuery = {
  brand: 'Philips',
  model: '3200',
  productType: 'koffiemachine',
  condition: 'GOOD',
  countryCode: 'NL',
  maxResults: 2,
};

const sampleRss = `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0">
  <channel>
    <item>
      <title>Philips 3200 koffiemachine</title>
      <link>https://www.marktplaats.nl/a/123</link>
      <pubDate>Mon, 15 Jan 2026 10:00:00 GMT</pubDate>
      <g:price>EUR 199,00</g:price>
    </item>
    <item>
      <title>Philips 3200 defect</title>
      <link>https://www.marktplaats.nl/a/456</link>
      <pubDate>Mon, 15 Jan 2026 12:00:00 GMT</pubDate>
      <price>EUR 0</price>
    </item>
  </channel>
</rss>`;

describe('MarktplaatsAdapter', () => {
  it('builds a search URL with encoded query', () => {
    const adapter = new MarktplaatsAdapter({ fetcher: vi.fn() });
    const url = adapter.buildSearchUrl(query);
    expect(url).toContain('https://www.marktplaats.nl/q/');
    expect(url).toContain('Philips%203200%20koffiemachine');
  });

  it('parses RSS items into listings', async () => {
    const fetcher = vi.fn().mockResolvedValue({
      ok: true,
      text: async () => sampleRss,
    });
    const adapter = new MarktplaatsAdapter({ fetcher });

    const results = await adapter.fetchListings(query);
    expect(results).toHaveLength(1);
    expect(results[0]).toEqual({
      title: 'Philips 3200 koffiemachine',
      url: 'https://www.marktplaats.nl/a/123',
      price: 199,
      currency: 'EUR',
      postedDate: new Date('Mon, 15 Jan 2026 10:00:00 GMT'),
      marketplace: 'marktplaats',
    });
  });
});
