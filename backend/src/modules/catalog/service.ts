/**
 * Catalog service - Business logic for catalog lookups
 * Provides read-only access to pre-synced catalog data
 */

import { prisma } from '../../infra/db/prisma.js';
import { BrandsResponse, ModelsResponse } from './schema.js';

/**
 * Get all available brands for a given subtype
 * Returns brands sorted alphabetically
 *
 * @param subtype - The item subtype (e.g., "electronics_phone")
 * @returns Object with subtype and sorted brand list
 */
export async function getBrandsBySubtype(subtype: string): Promise<BrandsResponse> {
  // Fetch all distinct brands for this subtype from the brand-Wikidata mapping table
  const brandMaps = await prisma.catalogBrandWikidataMap.findMany({
    where: { subtype },
    select: { brandString: true },
    orderBy: { brandString: 'asc' },
  });

  // Extract brand strings from the results
  const brands = brandMaps.map((b: { brandString: string }) => b.brandString);

  return {
    subtype,
    brands,
  };
}

/**
 * Get all available models for a given subtype + brand
 * Returns models sorted alphabetically by label
 *
 * @param subtype - The item subtype (e.g., "electronics_phone")
 * @param brandString - The brand name (e.g., "Samsung")
 * @returns Object with subtype, brand, and sorted model list
 * @throws Error if brand not found in Wikidata map
 */
export async function getModelsBySubtypeAndBrand(
  subtype: string,
  brandString: string
): Promise<ModelsResponse> {
  // Step 1: Resolve brandString -> wikidataQid using the brand-Wikidata mapping
  const brandMap = await prisma.catalogBrandWikidataMap.findUnique({
    where: {
      subtype_brandString: {
        subtype,
        brandString,
      },
    },
    select: { wikidataQid: true },
  });

  // If brand not found, return empty models list (graceful degradation)
  if (!brandMap) {
    return {
      subtype,
      brand: brandString,
      models: [],
    };
  }

  // Step 2: Fetch models from CatalogModel by (subtype, brandQid)
  const catalogModels = await prisma.catalogModel.findMany({
    where: {
      subtype,
      brandQid: brandMap.wikidataQid,
    },
    select: {
      modelLabel: true,
      modelQid: true,
    },
    orderBy: { modelLabel: 'asc' },
  });

  // Transform to response format
  const models = catalogModels.map((m: { modelLabel: string; modelQid: string }) => ({
    label: m.modelLabel,
    id: m.modelQid,
  }));

  return {
    subtype,
    brand: brandString,
    models,
  };
}
