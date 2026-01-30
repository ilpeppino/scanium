import path from "node:path";
import { fileURLToPath } from "node:url";
import { syncWikidataModelsForSubtype } from "./sync-job";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

async function main() {
  const userAgent = process.env.WIKIDATA_USER_AGENT;
  if (!userAgent) {
    throw new Error("Missing WIKIDATA_USER_AGENT (e.g. 'ScaniumBot/1.0 (contact: you@domain)')");
  }

  const catalogPath = process.env.BRAND_CATALOG_PATH;
  if (!catalogPath) {
    throw new Error("Missing BRAND_CATALOG_PATH (path to your brand catalog JSON)");
  }

  // default to module-local config if env not provided
  const subtypesConfigPath =
    process.env.WIKIDATA_SUBTYPES_PATH ??
    path.resolve(__dirname, "../data/wikidata-subtypes.json");

  const overridesPath =
    process.env.WIKIDATA_OVERRIDES_PATH ??
    path.resolve(__dirname, "../data/brand-wikidata-overrides.json");

  // For now: sync only phones (you can expand later)
  const subtype = process.env.SUBTYPE ?? "electronics_phone";

  const result = await syncWikidataModelsForSubtype({
    subtype,
    catalogPath,
    subtypesConfigPath,
    overridesPath,
    userAgent
  });

  console.log(JSON.stringify({ ok: true, result }, null, 2));
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
