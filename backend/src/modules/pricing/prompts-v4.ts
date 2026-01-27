import { FetchedListing } from './types-v4.js';

export const PRICING_V4_NORMALIZATION_SYSTEM_PROMPT =
  'You analyze marketplace listings to identify which match a target product. ' +
  'Do not estimate prices. Output only JSON.';

export const PRICING_V4_PROMPT_VERSION = '1.0.0';

export function buildPricingV4NormalizationPrompt(params: {
  brand: string;
  model: string;
  productType: string;
  listings: Array<{ id: number; listing: FetchedListing }>;
}): string {
  const listingPayload = params.listings.map(({ id, listing }) => ({
    listingId: id,
    title: listing.title,
    price: listing.price,
    currency: listing.currency,
    condition: listing.condition,
    url: listing.url,
  }));

  return `Target: ${params.brand} ${params.model} (${params.productType})\n\n` +
    `Listings to analyze:\n${JSON.stringify(listingPayload)}\n\n` +
    'Output JSON object: { "results": [ ... ] }\n' +
    'Each result:\n' +
    '{ "listingId": number, "isMatch": boolean, "matchConfidence": "HIGH" | "MED" | "LOW", "reason": "<20 chars>", "normalizedCondition": "NEW" | "LIKE_NEW" | "GOOD" | "FAIR" | "POOR" | null }\n' +
    'Rules:\n' +
    '- HIGH: exact brand+model match\n' +
    '- MED: same product line, minor variation\n' +
    '- LOW: similar category, uncertain match\n' +
    '- Do NOT estimate prices, only classify relevance';
}
