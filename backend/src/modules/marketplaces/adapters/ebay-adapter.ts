import { Config, getEbayTokenEndpoint } from '../../../config/index.js';
import { ListingQuery, FetchedListing, MarketplaceAdapter } from '../../pricing/types-v4.js';

type EbayBrowseAdapterOptions = {
  fetcher?: typeof fetch;
  now?: () => number;
};

type EbayTokenResponse = {
  access_token?: string;
  expires_in?: number;
  token_type?: string;
};

type EbaySearchResponse = {
  itemSummaries?: Array<{
    title?: string;
    itemWebUrl?: string;
    condition?: string;
    price?: { value?: string | number; currency?: string };
  }>;
};

export class EbayBrowseAdapter implements MarketplaceAdapter {
  readonly id = 'ebay';
  readonly name = 'eBay';
  private readonly fetcher: typeof fetch;
  private readonly now: () => number;
  private tokenCache: { token: string; expiresAt: number } | null = null;

  constructor(
    private readonly config: Config,
    options: EbayBrowseAdapterOptions = {}
  ) {
    this.fetcher = options.fetcher ?? fetch;
    this.now = options.now ?? (() => Date.now());
  }

  async fetchListings(query: ListingQuery): Promise<FetchedListing[]> {
    console.log('[eBay] fetchListings called', {
      brand: query.brand,
      model: query.model,
      productType: query.productType,
      countryCode: query.countryCode,
      maxResults: query.maxResults,
    });

    if (!this.config.ebay.clientId || !this.config.ebay.clientSecret) {
      console.log('[eBay] Missing credentials, returning empty array');
      return [];
    }

    console.log('[eBay] Credentials present, acquiring token...');
    const token = await this.getAccessToken();
    if (!token) {
      console.log('[eBay] Failed to acquire token, returning empty array');
      return [];
    }
    console.log('[eBay] Token acquired successfully');

    const endpoint = this.getBrowseEndpoint();
    const searchTerms = this.buildSearchTerms(query);
    const params = new URLSearchParams({
      q: searchTerms,
      limit: String(Math.max(1, Math.min(query.maxResults, 50))),
    });
    const marketplaceId = this.getMarketplaceId(query.countryCode);

    console.log('[eBay] Making Browse API call', {
      endpoint,
      searchTerms,
      limit: params.get('limit'),
      marketplaceId,
    });

    const response = await this.fetcher(`${endpoint}?${params.toString()}`, {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: 'application/json',
        'X-EBAY-C-MARKETPLACE-ID': marketplaceId,
      },
    });

    console.log('[eBay] Browse API response', {
      status: response.status,
      statusText: response.statusText,
      ok: response.ok,
    });

    if (!response.ok) {
      const body = await response.text();
      console.error('[eBay] Browse API error response body:', body);
      throw new Error(`eBay Browse API error: ${response.status} ${body}`);
    }

    const data = (await response.json()) as EbaySearchResponse;
    const items = data.itemSummaries ?? [];
    console.log('[eBay] Received items from API', { count: items.length });

    const listings = items
      .map((item) => ({
        title: item.title ?? 'Unknown',
        price: Number(item.price?.value ?? 0),
        currency: item.price?.currency ?? 'EUR',
        condition: item.condition,
        url: item.itemWebUrl ?? '',
        marketplace: this.id,
      }))
      .filter((item) => item.price > 0 && item.url)
      .slice(0, query.maxResults);

    console.log('[eBay] Returning filtered listings', { count: listings.length });
    return listings;
  }

  buildSearchUrl(query: ListingQuery): string {
    const q = encodeURIComponent(this.buildSearchTerms(query));
    return `https://www.ebay.nl/sch/i.html?_nkw=${q}`;
  }

  async isHealthy(): Promise<boolean> {
    if (!this.config.ebay.clientId || !this.config.ebay.clientSecret) {
      return false;
    }
    try {
      const token = await this.getAccessToken();
      return Boolean(token);
    } catch {
      return false;
    }
  }

  private buildSearchTerms(query: ListingQuery): string {
    // Only use brand and model for eBay searches
    // productType (e.g., "electronics_smartphone") is from internal taxonomy and doesn't match eBay search patterns
    return [query.brand, query.model].filter(Boolean).join(' ').trim();
  }

  private getBrowseEndpoint(): string {
    return this.config.ebay.env === 'production'
      ? 'https://api.ebay.com/buy/browse/v1/item_summary/search'
      : 'https://api.sandbox.ebay.com/buy/browse/v1/item_summary/search';
  }

  private getMarketplaceId(countryCode: string): string {
    const code = countryCode.trim().toUpperCase();
    switch (code) {
      case 'NL':
        return 'EBAY_NL';
      case 'BE':
        return 'EBAY_BE';
      case 'DE':
        return 'EBAY_DE';
      case 'FR':
        return 'EBAY_FR';
      case 'UK':
      case 'GB':
        return 'EBAY_GB';
      case 'US':
        return 'EBAY_US';
      default:
        return 'EBAY_NL';
    }
  }

  private async getAccessToken(): Promise<string | null> {
    const cached = this.tokenCache;
    const now = this.now();
    if (cached && cached.expiresAt > now + 60_000) {
      console.log('[eBay] Using cached token', {
        expiresInSeconds: Math.floor((cached.expiresAt - now) / 1000),
      });
      return cached.token;
    }

    console.log('[eBay] Fetching new OAuth2 token', {
      hasCachedToken: Boolean(cached),
      cacheExpired: cached ? cached.expiresAt <= now + 60_000 : null,
    });

    const endpoint = getEbayTokenEndpoint(this.config);
    const credentials = Buffer.from(
      `${this.config.ebay.clientId}:${this.config.ebay.clientSecret}`
    ).toString('base64');
    const body = new URLSearchParams({
      grant_type: 'client_credentials',
      scope: this.config.ebay.scopes,
    });

    console.log('[eBay] OAuth2 request details', {
      endpoint,
      clientId: this.config.ebay.clientId,
      env: this.config.ebay.env,
      scopes: this.config.ebay.scopes,
      grantType: 'client_credentials',
    });

    const response = await this.fetcher(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${credentials}`,
      },
      body: body.toString(),
    });

    console.log('[eBay] OAuth2 response', {
      status: response.status,
      statusText: response.statusText,
      ok: response.ok,
    });

    if (!response.ok) {
      const text = await response.text();
      console.error('[eBay] OAuth2 error response:', text);
      throw new Error(`eBay token error: ${response.status} ${text}`);
    }

    const data = (await response.json()) as EbayTokenResponse;
    console.log('[eBay] OAuth2 token response', {
      hasAccessToken: Boolean(data.access_token),
      tokenType: data.token_type,
      expiresIn: data.expires_in,
    });

    if (!data.access_token) {
      console.error('[eBay] No access_token in response');
      return null;
    }

    const expiresIn = data.expires_in ?? 3600;
    this.tokenCache = {
      token: data.access_token,
      expiresAt: now + expiresIn * 1000,
    };

    console.log('[eBay] Token cached successfully', {
      expiresInSeconds: expiresIn,
    });

    return data.access_token;
  }
}
