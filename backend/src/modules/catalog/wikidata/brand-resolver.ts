const WIKIDATA_API = "https://www.wikidata.org/w/api.php";

export async function resolveBrandQid(brandString: string, userAgent: string): Promise<string | null> {
  const url = new URL(WIKIDATA_API);
  url.searchParams.set("action", "wbsearchentities");
  url.searchParams.set("search", brandString);
  url.searchParams.set("language", "en");
  url.searchParams.set("format", "json");
  url.searchParams.set("limit", "1");

  const res = await fetch(url, { headers: { "user-agent": userAgent } });
  if (!res.ok) return null;

  const json = await res.json() as any;
  const id = json?.search?.[0]?.id;
  return typeof id === "string" && /^Q\d+$/.test(id) ? id : null;
}
