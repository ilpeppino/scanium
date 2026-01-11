import { z } from 'zod';

/**
 * Marketplace type classification
 */
export const marketplaceTypeSchema = z.enum(['global', 'marketplace', 'classifieds']);

/**
 * Individual marketplace entry
 */
export const marketplaceSchema = z.object({
  id: z.string().min(1),
  name: z.string().min(1),
  domains: z.array(z.string().min(1)),
  type: marketplaceTypeSchema,
});

/**
 * Country-specific marketplace configuration
 */
export const countryConfigSchema = z.object({
  code: z.string().length(2).toUpperCase(),
  defaultCurrency: z.string().length(3).toUpperCase(),
  marketplaces: z.array(marketplaceSchema).min(1),
});

/**
 * Root marketplaces catalog schema
 */
export const marketplacesCatalogSchema = z.object({
  version: z.number().int().positive(),
  countries: z.array(countryConfigSchema).min(1),
});

/**
 * Inferred TypeScript types
 */
export type MarketplaceType = z.infer<typeof marketplaceTypeSchema>;
export type Marketplace = z.infer<typeof marketplaceSchema>;
export type CountryConfig = z.infer<typeof countryConfigSchema>;
export type MarketplacesCatalog = z.infer<typeof marketplacesCatalogSchema>;
