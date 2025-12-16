/**
 * Marketplace adapter interface
 * Future marketplace integrations should implement this interface
 */

export interface MarketplaceAdapter {
  /**
   * Marketplace identifier
   */
  readonly name: string;

  /**
   * Initiate OAuth flow
   */
  initiateAuth(): Promise<{ authorizeUrl: string }>;

  /**
   * Handle OAuth callback
   */
  handleCallback(code: string, state: string): Promise<void>;

  /**
   * Check connection status
   */
  getConnectionStatus(): Promise<{
    connected: boolean;
    environment?: string;
    expiresAt?: Date;
  }>;

  /**
   * Create a listing
   * TODO: Define listing interface
   */
  createListing(listing: unknown): Promise<{ listingId: string; url: string }>;

  /**
   * Update a listing
   */
  updateListing(
    listingId: string,
    updates: unknown
  ): Promise<{ listingId: string }>;

  /**
   * Delete a listing
   */
  deleteListing(listingId: string): Promise<void>;

  /**
   * Get listing details
   */
  getListing(listingId: string): Promise<unknown>;
}

/**
 * Future adapters:
 * - EbayMarketplaceAdapter (extends this interface)
 * - MercariMarketplaceAdapter
 * - FacebookMarketplaceAdapter
 * - etc.
 */
