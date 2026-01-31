/**
 * Catalog API routes
 * Provides read-only access to pre-synced catalog data (brands, models)
 *
 * Endpoints:
 * - GET /v1/catalog/:subtype/brands - List available brands for a subtype
 * - GET /v1/catalog/:subtype/models?brand=X - List models for a subtype + brand
 * - GET /v1/catalog/models - Legacy search endpoint (query params)
 */

import { FastifyInstance } from "fastify";
import { z } from "zod";
import { prisma } from "../../infra/db/prisma.js";
import { ValidationError } from "../../shared/errors/index.js";
import {
  subtypeParamsSchema,
  modelsQuerySchema,
} from "./schema.js";
import {
  getBrandsBySubtype,
  getModelsBySubtypeAndBrand,
} from "./service.js";

// Legacy query schema for backward compatibility
const LegacyModelsQuery = z.object({
  subtype: z.string().min(1),
  brand: z.string().min(1),
  q: z.string().optional(),
  limit: z.coerce.number().int().min(1).max(100).optional()
});

export async function catalogRoutes(app: FastifyInstance) {
  /**
   * GET /v1/catalog/:subtype/brands
   * List all available brands for a given subtype
   *
   * Response: { subtype: "electronics_phone", brands: ["Apple", "Samsung", ...] }
   */
  app.get("/v1/catalog/:subtype/brands", async (req, reply) => {
    // Validate path parameters
    const paramsResult = subtypeParamsSchema.safeParse(req.params);
    if (!paramsResult.success) {
      throw new ValidationError("Invalid subtype parameter", {
        errors: paramsResult.error.errors,
      });
    }

    const { subtype } = paramsResult.data;

    try {
      const result = await getBrandsBySubtype(subtype);
      reply.header("Cache-Control", "public, max-age=3600");
      return reply.status(200).send(result);
    } catch (error) {

      req.log.error(
        {
          event: "get_brands_failed",
          subtype,
          error: error instanceof Error ? error.message : "Unknown error",
        },
        "Failed to get brands"
      );

      return reply.status(500).send({
        error: {
          code: "INTERNAL_ERROR",
          message: "Failed to retrieve brands",
        },
      });
    }
  });

  /**
   * GET /v1/catalog/:subtype/models?brand=Samsung
   * List all available models for a given subtype + brand
   *
   * Flow:
   * 1. Resolve brandString -> wikidataQid using CatalogBrandWikidataMap
   * 2. Fetch models from CatalogModel by (subtype, brandQid)
   *
   * Response: {
   *   subtype: "electronics_phone",
   *   brand: "Samsung",
   *   models: [
   *     { label: "Galaxy S24", id: "Q12345" },
   *     { label: "Galaxy S23 Ultra", id: "Q67890" }
   *   ]
   * }
   */
  app.get("/v1/catalog/:subtype/models", async (req, reply) => {
    // Validate path parameters
    const paramsResult = subtypeParamsSchema.safeParse(req.params);
    if (!paramsResult.success) {
      throw new ValidationError("Invalid subtype parameter", {
        errors: paramsResult.error.errors,
      });
    }

    // Validate query parameters
    const queryResult = modelsQuerySchema.safeParse(req.query);
    if (!queryResult.success) {
      throw new ValidationError("Invalid query parameters", {
        errors: queryResult.error.errors,
      });
    }

    const { subtype } = paramsResult.data;
    const { brand } = queryResult.data;

    try {
      const result = await getModelsBySubtypeAndBrand(subtype, brand);
      reply.header("Cache-Control", "public, max-age=3600");
      return reply.status(200).send(result);
    } catch (error) {
      req.log.error(
        {
          event: "get_models_failed",
          subtype,
          brand,
          error: error instanceof Error ? error.message : "Unknown error",
        },
        "Failed to get models"
      );

      return reply.status(500).send({
        error: {
          code: "INTERNAL_ERROR",
          message: "Failed to retrieve models",
        },
      });
    }
  });

  /**
   * Legacy endpoint: GET /v1/catalog/models
   * Search models using query parameters (backward compatibility)
   *
   * This endpoint uses query params for both subtype and brand,
   * unlike the newer path-based endpoint above.
   */
  app.get("/v1/catalog/models", async (req, reply) => {
    const parsed = LegacyModelsQuery.safeParse(req.query);
    if (!parsed.success) return reply.code(400).send({ error: "Invalid query", details: parsed.error.flatten() });

    const { subtype, brand, q, limit } = parsed.data;
    const take = limit ?? 50;

    // Resolve brand -> Wikidata QID
    const map = await prisma.catalogBrandWikidataMap.findUnique({
      where: { subtype_brandString: { subtype, brandString: brand } }
    });

    if (!map) return reply.code(404).send({ error: "Brand not mapped yet", subtype, brand });

    // Build query with optional search filter
    const where: any = { subtype, brandQid: map.wikidataQid };
    if (q?.trim()) where.modelLabel = { contains: q.trim(), mode: "insensitive" };

    const items = await prisma.catalogModel.findMany({
      where,
      orderBy: { modelLabel: "asc" },
      take
    });

    return {
      subtype,
      brand,
      brandQid: map.wikidataQid,
      query: q ?? null,
      items: items.map((i: { modelLabel: string; modelQid: string; aliases: any }) => ({
        label: i.modelLabel,
        modelQid: i.modelQid,
        aliases: i.aliases
      }))
    };
  });
}
