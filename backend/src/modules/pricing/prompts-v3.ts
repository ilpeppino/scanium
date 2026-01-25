/**
 * Pricing V3 Prompts
 *
 * Token-optimized prompts for OpenAI pricing estimation.
 * Target: <250 tokens input, <150 tokens output = ~400 tokens total per request.
 */

import { ItemCondition } from './types-v3.js';

/**
 * System prompt for pricing estimation (v1.0.0)
 *
 * Token budget: ~120 tokens
 */
export const PRICING_V3_SYSTEM_PROMPT = `You estimate secondhand prices. Given item details, estimate resale price range.

Output ONLY JSON:
{"low":number,"high":number,"cur":"EUR","conf":"HIGH|MED|LOW","why":"<50 chars"}

Rules:
- Prices in seller's currency
- HIGH: exact model matches
- MED: similar items
- LOW: category estimates`;

/**
 * Build user prompt for pricing request
 *
 * Token budget: ~80 tokens
 *
 * Example output:
 * ```
 * Philips Coffee Machine 3200 Series
 * Cond: USED
 * Cat: appliance_coffee_machine
 * Region: NL
 * Sites: marktplaats,bol,amazon
 * ```
 */
export function buildPricingV3UserPrompt(params: {
  brand: string;
  productType: string;
  model: string;
  condition: ItemCondition;
  countryCode: string;
  marketplaceIds: string[];
}): string {
  const { brand, productType, model, condition, countryCode, marketplaceIds } = params;

  // Map condition enum to short display text
  const conditionShort = mapConditionToShort(condition);

  // Limit marketplaces to 3 for token efficiency
  const sitesStr = marketplaceIds.slice(0, 3).join(',');

  return `${brand} ${productType.replace(/_/g, ' ')} ${model}
Cond: ${conditionShort}
Cat: ${productType}
Region: ${countryCode}
Sites: ${sitesStr}`;
}

/**
 * Map ItemCondition enum to short text for prompt
 */
function mapConditionToShort(condition: ItemCondition): string {
  switch (condition) {
    case 'NEW_SEALED':
      return 'NEW_SEALED';
    case 'NEW_WITH_TAGS':
      return 'NEW_W_TAGS';
    case 'NEW_WITHOUT_TAGS':
      return 'NEW_NO_TAGS';
    case 'LIKE_NEW':
      return 'LIKE_NEW';
    case 'GOOD':
      return 'GOOD';
    case 'FAIR':
      return 'FAIR';
    case 'POOR':
      return 'POOR';
  }
}

/**
 * Prompt version for A/B testing and monitoring
 */
export const PRICING_V3_PROMPT_VERSION = '1.0.0';
