/**
 * Zod schemas for Catalog API
 * Validates requests and responses for catalog lookup operations
 */

import { z } from 'zod';

/**
 * Path parameter schema for subtype
 */
export const subtypeParamsSchema = z.object({
  subtype: z.string().min(1, 'Subtype is required'),
});

/**
 * Query parameters for models endpoint
 */
export const modelsQuerySchema = z.object({
  brand: z.string().min(1, 'Brand is required'),
});

/**
 * Response schema for brands list
 */
export const brandsResponseSchema = z.object({
  subtype: z.string(),
  brands: z.array(z.string()),
});

/**
 * Model item shape
 */
export const modelItemSchema = z.object({
  label: z.string(),
  id: z.string(),
});

/**
 * Response schema for models list
 */
export const modelsResponseSchema = z.object({
  subtype: z.string(),
  brand: z.string(),
  models: z.array(modelItemSchema),
});

// Type exports
export type SubtypeParams = z.infer<typeof subtypeParamsSchema>;
export type ModelsQuery = z.infer<typeof modelsQuerySchema>;
export type BrandsResponse = z.infer<typeof brandsResponseSchema>;
export type ModelItem = z.infer<typeof modelItemSchema>;
export type ModelsResponse = z.infer<typeof modelsResponseSchema>;
