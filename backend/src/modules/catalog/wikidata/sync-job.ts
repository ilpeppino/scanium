import fs from "node:fs/promises";
import { prisma } from "../../../infra/db/prisma";
import { buildBroadQuery, buildStrictQuery } from "./queries";
import { runWdqs } from "./wdqs-client";
import { resolveBrandQid } from "./brand-resolver";

type SubtypesConfig = {
  version: number;
  subtypes: Record<string, {
    modelConcept: { modelClassQids: string[] };
    batching?: { brandBatchSize?: number };
  }>;
};

type Overrides = {
  version: number;
  overrides: Array<{
    subtype: string;
    brandString: string;
    wikidataQid: string;
    qidType: "manufacturer" | "brand" | "unknown";
    confidence: "manual_override";
  }>;
};

function chunk<T>(arr: T[], size: number): T[][] {
  const out: T[][] = [];
  for (let i = 0; i < arr.length; i += size) out.push(arr.slice(i, i + size));
  return out;
}

function getOverride(overrides: Overrides, subtype: string, brandString: string) {
  return overrides.overrides.find(
    o => o.subtype === subtype && o.brandString.toLowerCase() === brandString.toLowerCase()
  );
}

export async function syncWikidataModelsForSubtype(params: {
  subtype: string;
  catalogPath: string;
  subtypesConfigPath: string;
  overridesPath: string;
  userAgent: string;
}) {
  const { subtype, catalogPath, subtypesConfigPath, overridesPath, userAgent } = params;

  // ✅ FIXED: support bundle format
  const catalogRaw = JSON.parse(await fs.readFile(catalogPath, "utf-8")) as any;

  const subtypeMap =
    catalogRaw?.brandsBySubtype && typeof catalogRaw.brandsBySubtype === "object"
      ? catalogRaw.brandsBySubtype
      : catalogRaw;

  const brands: string[] = Array.isArray(subtypeMap?.[subtype]) ? subtypeMap[subtype] : [];

  if (!brands.length) {
    return { subtype, brands: 0, modelsUpserted: 0 };
  }

  const cfg = JSON.parse(await fs.readFile(subtypesConfigPath, "utf-8")) as SubtypesConfig;
  const subtypeCfg = cfg.subtypes[subtype];
  if (!subtypeCfg) throw new Error(`Subtype not enabled in wikidata config: ${subtype}`);

  const overrides = JSON.parse(await fs.readFile(overridesPath, "utf-8")) as Overrides;
  const batchSize = subtypeCfg.batching?.brandBatchSize ?? 25;

  const resolved: Array<{ brandString: string; qid: string; qidType: string; confidence: string }> = [];

  for (const brandString of brands) {
    const ovr = getOverride(overrides, subtype, brandString);
    if (ovr) {
      resolved.push({
        brandString,
        qid: ovr.wikidataQid,
        qidType: ovr.qidType,
        confidence: ovr.confidence
      });
      continue;
    }

    const qid = await resolveBrandQid(brandString, userAgent);
    if (!qid) continue;

    resolved.push({
      brandString,
      qid,
      qidType: "unknown",
      confidence: "auto"
    });
  }

  for (const r of resolved) {
    await prisma.catalogBrandWikidataMap.upsert({
      where: { subtype_brandString: { subtype, brandString: r.brandString } },
      update: { wikidataQid: r.qid, qidType: r.qidType, confidence: r.confidence },
      create: {
        subtype,
        brandString: r.brandString,
        wikidataQid: r.qid,
        qidType: r.qidType,
        confidence: r.confidence
      }
    });
  }

  const modelClassQids = subtypeCfg.modelConcept.modelClassQids;
  const brandQids = [...new Set(resolved.map(r => r.qid))];
  const batches = chunk(brandQids, batchSize);

  let upserts = 0;

  for (const batch of batches) {
    const strictRows = await runWdqs(buildStrictQuery(batch, modelClassQids), userAgent);
    const broadRows = await runWdqs(buildBroadQuery(batch, modelClassQids), userAgent);

    const merged = new Map<string, { brandQid: string; modelQid: string; modelLabel: string; source: string }>();

    for (const r of strictRows) merged.set(`${r.brandQid}:${r.modelQid}`, { ...r, source: "wdqs_strict" });
    for (const r of broadRows) {
      const key = `${r.brandQid}:${r.modelQid}`;
      if (!merged.has(key)) merged.set(key, { ...r, source: "wdqs_broad" });
    }

    for (const m of merged.values()) {
      await prisma.catalogModel.upsert({
        where: { subtype_brandQid_modelQid: { subtype, brandQid: m.brandQid, modelQid: m.modelQid } },
        update: { modelLabel: m.modelLabel, source: m.source },
        create: {
          subtype,
          brandQid: m.brandQid,
          modelQid: m.modelQid,
          modelLabel: m.modelLabel,
          source: m.source
        }
      });
      upserts++;
    }
  }

  return { subtype, brands: brands.length, modelsUpserted: upserts };
}
