/**
 * eBay Taxonomy API Client
 * Implements category tree and aspect retrieval
 */

import type {
  CategoryTreeIdResponse,
  CategoryTree,
  CategorySubtreeResponse,
  AspectMetadata,
  MarketplaceId,
} from '../types/ebay.js';
import type { EBayAuthClient } from './ebay-auth.js';

const API_ENDPOINTS = {
  sandbox: 'https://api.sandbox.ebay.com/commerce/taxonomy/v1',
  production: 'https://api.ebay.com/commerce/taxonomy/v1',
} as const;

export class EBayTaxonomyClient {
  private authClient: EBayAuthClient;
  private baseUrl: string;

  constructor(authClient: EBayAuthClient, environment: 'sandbox' | 'production') {
    this.authClient = authClient;
    this.baseUrl = API_ENDPOINTS[environment];
  }

  /**
   * Get the default category tree ID for a marketplace
   * https://developer.ebay.com/api-docs/commerce/taxonomy/resources/category_tree/methods/getDefaultCategoryTreeId
   */
  async getDefaultCategoryTreeId(marketplaceId: MarketplaceId): Promise<string> {
    const token = await this.authClient.getAccessToken();
    const url = `${this.baseUrl}/get_default_category_tree_id?marketplace_id=${marketplaceId}`;

    const response = await fetch(url, {
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(
        `Failed to get default category tree ID for ${marketplaceId} (${response.status}): ${errorText}`
      );
    }

    const data = (await response.json()) as CategoryTreeIdResponse;
    return data.categoryTreeId;
  }

  /**
   * Get the complete category tree
   * https://developer.ebay.com/api-docs/commerce/taxonomy/resources/category_tree/methods/getCategoryTree
   */
  async getCategoryTree(categoryTreeId: string): Promise<CategoryTree> {
    const token = await this.authClient.getAccessToken();
    const url = `${this.baseUrl}/category_tree/${categoryTreeId}`;

    const response = await fetch(url, {
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(
        `Failed to get category tree ${categoryTreeId} (${response.status}): ${errorText}`
      );
    }

    const data = (await response.json()) as CategoryTree;
    return data;
  }

  /**
   * Get a category subtree starting from a specific category
   * https://developer.ebay.com/api-docs/commerce/taxonomy/resources/category_tree/methods/getCategorySubtree
   */
  async getCategorySubtree(
    categoryTreeId: string,
    categoryId: string
  ): Promise<CategorySubtreeResponse> {
    const token = await this.authClient.getAccessToken();
    const url = `${this.baseUrl}/category_tree/${categoryTreeId}/get_category_subtree?category_id=${categoryId}`;

    const response = await fetch(url, {
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(
        `Failed to get category subtree for ${categoryId} (${response.status}): ${errorText}`
      );
    }

    const data = (await response.json()) as CategorySubtreeResponse;
    return data;
  }

  /**
   * Get item aspects for a specific category
   * https://developer.ebay.com/api-docs/commerce/taxonomy/resources/category_tree/methods/getItemAspectsForCategory
   */
  async getItemAspectsForCategory(
    categoryTreeId: string,
    categoryId: string
  ): Promise<AspectMetadata> {
    const token = await this.authClient.getAccessToken();
    const url = `${this.baseUrl}/category_tree/${categoryTreeId}/get_item_aspects_for_category?category_id=${categoryId}`;

    const response = await fetch(url, {
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(
        `Failed to get item aspects for category ${categoryId} (${response.status}): ${errorText}`
      );
    }

    const data = (await response.json()) as AspectMetadata;
    return data;
  }

  /**
   * Fetch item aspects for multiple categories in batch
   * (sequential calls with basic rate limiting)
   */
  async getItemAspectsForCategories(
    categoryTreeId: string,
    categoryIds: string[]
  ): Promise<Map<string, AspectMetadata>> {
    const results = new Map<string, AspectMetadata>();

    for (const categoryId of categoryIds) {
      try {
        const aspects = await this.getItemAspectsForCategory(categoryTreeId, categoryId);
        results.set(categoryId, aspects);
        // Basic rate limiting: wait 100ms between requests
        await new Promise((resolve) => setTimeout(resolve, 100));
      } catch (error) {
        console.warn(
          `Warning: Failed to fetch aspects for category ${categoryId}:`,
          error instanceof Error ? error.message : String(error)
        );
        // Continue with other categories
      }
    }

    return results;
  }
}
