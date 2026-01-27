import { ListingQuery, FetchedListing, MarketplaceAdapter } from '../../pricing/types-v4.js';

type MarktplaatsAdapterOptions = {
  fetcher?: typeof fetch;
};

export class MarktplaatsAdapter implements MarketplaceAdapter {
  readonly id = 'marktplaats';
  readonly name = 'Marktplaats';
  private readonly fetcher: typeof fetch;

  constructor(options: MarktplaatsAdapterOptions = {}) {
    this.fetcher = options.fetcher ?? fetch;
  }

  async fetchListings(query: ListingQuery): Promise<FetchedListing[]> {
    const endpoint = this.buildRssUrl(query);
    const response = await this.fetcher(endpoint, {
      method: 'GET',
      headers: {
        Accept: 'application/rss+xml, application/xml;q=0.9, */*;q=0.8',
        'User-Agent': 'scanium-backend/1.0',
      },
    });

    if (!response.ok) {
      const body = await response.text();
      throw new Error(`Marktplaats RSS error: ${response.status} ${body}`);
    }

    const xml = await response.text();
    const items = this.parseRssItems(xml);

    return items.slice(0, query.maxResults);
  }

  buildSearchUrl(query: ListingQuery): string {
    const q = encodeURIComponent(this.buildSearchTerms(query));
    return `https://www.marktplaats.nl/q/${q}/`;
  }

  async isHealthy(): Promise<boolean> {
    return true;
  }

  private buildSearchTerms(query: ListingQuery): string {
    return [query.brand, query.model, query.productType].filter(Boolean).join(' ').trim();
  }

  private buildRssUrl(query: ListingQuery): string {
    const q = encodeURIComponent(this.buildSearchTerms(query));
    return `https://www.marktplaats.nl/rss?q=${q}`;
  }

  private parseRssItems(xml: string): FetchedListing[] {
    const items = xml.match(/<item[\s\S]*?<\/item>/gi) ?? [];

    return items
      .map((block) => {
        const title = this.extractTag(block, 'title');
        const url = this.extractTag(block, 'link');
        const priceText =
          this.extractTag(block, 'g:price') ??
          this.extractTag(block, 'price') ??
          this.extractTag(block, 'pr:price');
        const pubDate = this.extractTag(block, 'pubDate') ?? this.extractTag(block, 'dc:date');

        const parsedPrice = this.parsePrice(priceText ?? '');
        if (!title || !url || !parsedPrice) {
          return null;
        }

        return {
          title,
          url,
          price: parsedPrice.amount,
          currency: parsedPrice.currency,
          postedDate: pubDate ? new Date(pubDate) : undefined,
          marketplace: this.id,
        } as FetchedListing;
      })
      .filter((item): item is FetchedListing => Boolean(item && item.price > 0 && item.url));
  }

  private extractTag(block: string, tag: string): string | null {
    const regex = new RegExp(`<${tag}>([\\s\\S]*?)<\\/${tag}>`, 'i');
    const match = block.match(regex);
    if (!match) {
      return null;
    }
    return this.decodeXml(match[1].trim());
  }

  private decodeXml(value: string): string {
    return value
      .replace(/&amp;/g, '&')
      .replace(/&lt;/g, '<')
      .replace(/&gt;/g, '>')
      .replace(/&quot;/g, '"')
      .replace(/&#39;/g, "'");
  }

  private parsePrice(value: string): { amount: number; currency: string } | null {
    if (!value) {
      return null;
    }

    const currencyMatch = value.match(/\b(EUR|USD|GBP)\b/i);
    const currency = currencyMatch ? currencyMatch[1].toUpperCase() : 'EUR';
    const numberMatch = value.match(/([0-9]+(?:[.,][0-9]+)?)/);
    if (!numberMatch) {
      return null;
    }
    const normalized = numberMatch[1].replace(',', '.');
    const amount = Number.parseFloat(normalized);
    if (!Number.isFinite(amount)) {
      return null;
    }
    return { amount, currency };
  }
}
