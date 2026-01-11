import { getCachedCatalog, LoadResult } from './loader.js';
import { Marketplace, CountryConfig } from './schema.js';

/**
 * Service result types
 */
export type ServiceResult<T> =
  | { success: true; data: T }
  | { success: false; error: string; errorCode: 'NOT_FOUND' | 'CATALOG_ERROR' };

/**
 * Marketplaces service - provides read-only access to catalog
 */
export class MarketplacesService {
  private readonly catalogPath: string;
  private catalogLoadResult: LoadResult | null = null;

  constructor(catalogPath: string) {
    this.catalogPath = catalogPath;
  }

  /**
   * Initialize service - loads and validates catalog
   * Call this at server startup to fail fast if catalog is invalid
   */
  initialize(): { ready: boolean; error?: string } {
    this.catalogLoadResult = getCachedCatalog(this.catalogPath);

    if (!this.catalogLoadResult.success) {
      return {
        ready: false,
        error: this.catalogLoadResult.error,
      };
    }

    return { ready: true };
  }

  /**
   * Check if service is ready (catalog loaded successfully)
   */
  isReady(): boolean {
    return this.catalogLoadResult?.success === true;
  }

  /**
   * Get list of supported country codes
   */
  listCountries(): ServiceResult<string[]> {
    if (!this.catalogLoadResult?.success) {
      return {
        success: false,
        error: 'Marketplaces catalog not loaded',
        errorCode: 'CATALOG_ERROR',
      };
    }

    const codes = this.catalogLoadResult.catalog.countries.map((c) => c.code);
    return {
      success: true,
      data: codes,
    };
  }

  /**
   * Get marketplaces for a specific country
   * @param countryCode - ISO 3166-1 alpha-2 country code (e.g., "NL", "DE")
   */
  getMarketplaces(countryCode: string): ServiceResult<Marketplace[]> {
    if (!this.catalogLoadResult?.success) {
      return {
        success: false,
        error: 'Marketplaces catalog not loaded',
        errorCode: 'CATALOG_ERROR',
      };
    }

    const normalizedCode = countryCode.toUpperCase();
    const countryConfig = this.catalogLoadResult.catalog.countries.find(
      (c) => c.code === normalizedCode
    );

    if (!countryConfig) {
      return {
        success: false,
        error: `Country '${normalizedCode}' not found in catalog`,
        errorCode: 'NOT_FOUND',
      };
    }

    return {
      success: true,
      data: countryConfig.marketplaces,
    };
  }

  /**
   * Get full country configuration including currency
   * @param countryCode - ISO 3166-1 alpha-2 country code
   */
  getCountryConfig(countryCode: string): ServiceResult<CountryConfig> {
    if (!this.catalogLoadResult?.success) {
      return {
        success: false,
        error: 'Marketplaces catalog not loaded',
        errorCode: 'CATALOG_ERROR',
      };
    }

    const normalizedCode = countryCode.toUpperCase();
    const countryConfig = this.catalogLoadResult.catalog.countries.find(
      (c) => c.code === normalizedCode
    );

    if (!countryConfig) {
      return {
        success: false,
        error: `Country '${normalizedCode}' not found in catalog`,
        errorCode: 'NOT_FOUND',
      };
    }

    return {
      success: true,
      data: countryConfig,
    };
  }

  /**
   * Get catalog version
   */
  getCatalogVersion(): ServiceResult<number> {
    if (!this.catalogLoadResult?.success) {
      return {
        success: false,
        error: 'Marketplaces catalog not loaded',
        errorCode: 'CATALOG_ERROR',
      };
    }

    return {
      success: true,
      data: this.catalogLoadResult.catalog.version,
    };
  }
}
