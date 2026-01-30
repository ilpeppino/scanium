import { setTimeout as delay } from "node:timers/promises";

const WDQS_ENDPOINT = "https://query.wikidata.org/sparql";

function qidFromEntityUri(uri: string): string | null {
  const m = uri.match(/\/entity\/(Q\d+)$/);
  return m?.[1] ?? null;
}

export async function runWdqs(query: string, userAgent: string): Promise<Array<{
  brandQid: string;
  modelQid: string;
  modelLabel: string;
}>> {
  const res = await fetch(WDQS_ENDPOINT, {
    method: "POST",
    headers: {
      "content-type": "application/sparql-query",
      "accept": "application/sparql-results+json",
      "user-agent": userAgent
    },
    body: query
  });

  if (res.status === 429) {
    const retryAfter = res.headers.get("retry-after");
    const waitMs = retryAfter ? Number(retryAfter) * 1000 : 5000;
    await delay(Number.isFinite(waitMs) ? waitMs : 5000);
    throw new Error("WDQS rate-limited (429). Retry later.");
  }

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(`WDQS error ${res.status}: ${text.slice(0, 500)}`);
  }

  const json = await res.json() as any;

  const out: Array<{ brandQid: string; modelQid: string; modelLabel: string }> = [];
  for (const b of (json?.results?.bindings ?? [])) {
    const brandQid = qidFromEntityUri(b.brand?.value ?? "");
    const modelQid = qidFromEntityUri(b.model?.value ?? "");
    const modelLabel = b.modelLabel?.value ?? null;
    if (!brandQid || !modelQid || !modelLabel) continue;
    out.push({ brandQid, modelQid, modelLabel });
  }
  return out;
}
