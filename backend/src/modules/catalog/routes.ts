import { FastifyInstance } from "fastify";
import { z } from "zod";
import { prisma } from "../../infra/db/prisma";

const ModelsQuery = z.object({
  subtype: z.string().min(1),
  brand: z.string().min(1),
  q: z.string().optional(),
  limit: z.coerce.number().int().min(1).max(100).optional()
});

export async function catalogRoutes(app: FastifyInstance) {
  app.get("/v1/catalog/models", async (req, reply) => {
    const parsed = ModelsQuery.safeParse(req.query);
    if (!parsed.success) return reply.code(400).send({ error: "Invalid query", details: parsed.error.flatten() });

    const { subtype, brand, q, limit } = parsed.data;
    const take = limit ?? 50;

    const map = await prisma.catalogBrandWikidataMap.findUnique({
      where: { subtype_brandString: { subtype, brandString: brand } }
    });

    if (!map) return reply.code(404).send({ error: "Brand not mapped yet", subtype, brand });

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
      items: items.map(i => ({
        label: i.modelLabel,
        modelQid: i.modelQid,
        aliases: i.aliases
      }))
    };
  });
}
