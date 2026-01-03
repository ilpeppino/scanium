import {
  AssistantPrefs,
  ItemContextSnapshot,
  ConfidenceTier,
} from '../types.js';
import { ResolvedAttributes } from '../../vision/attribute-resolver.js';
import { VisualFacts } from '../../vision/types.js';

/**
 * Language-specific instructions for response generation.
 */
const LANGUAGE_INSTRUCTIONS: Record<string, string> = {
  EN: 'Respond in English.',
  NL: 'Respond in Dutch (Nederlands).',
  DE: 'Respond in German (Deutsch).',
  FR: 'Respond in French (Français).',
  IT: 'Respond in Italian (Italiano).',
  ES: 'Respond in Spanish (Español).',
};

/**
 * Tone-specific instructions for response style.
 */
const TONE_INSTRUCTIONS: Record<string, string> = {
  NEUTRAL: 'Use a neutral, informative tone.',
  FRIENDLY: 'Use a friendly, approachable tone with helpful suggestions.',
  PROFESSIONAL: 'Use a formal, professional business tone.',
};

/**
 * Region-specific marketplace context.
 */
const REGION_CONTEXT: Record<string, { currency: string; marketplaces: string }> = {
  NL: { currency: 'EUR', marketplaces: 'Marktplaats, eBay.nl, Facebook Marketplace' },
  DE: { currency: 'EUR', marketplaces: 'eBay.de, eBay Kleinanzeigen, Facebook Marketplace' },
  BE: { currency: 'EUR', marketplaces: '2dehands, eBay.be, Facebook Marketplace' },
  FR: { currency: 'EUR', marketplaces: 'Leboncoin, eBay.fr, Facebook Marketplace' },
  UK: { currency: 'GBP', marketplaces: 'eBay.co.uk, Facebook Marketplace, Gumtree' },
  US: { currency: 'USD', marketplaces: 'eBay.com, Facebook Marketplace, Craigslist' },
  EU: { currency: 'EUR', marketplaces: 'eBay, Facebook Marketplace' },
};

/**
 * Build the system prompt for marketplace listing generation.
 */
export function buildListingSystemPrompt(prefs?: AssistantPrefs): string {
  const language = prefs?.language ?? 'EN';
  const tone = prefs?.tone ?? 'NEUTRAL';
  const region = prefs?.region ?? 'EU';
  const verbosity = prefs?.verbosity ?? 'NORMAL';

  const langInstruction = LANGUAGE_INSTRUCTIONS[language] ?? LANGUAGE_INSTRUCTIONS.EN;
  const toneInstruction = TONE_INSTRUCTIONS[tone] ?? TONE_INSTRUCTIONS.NEUTRAL;
  const regionCtx = REGION_CONTEXT[region] ?? REGION_CONTEXT.EU;

  const verbosityGuide = verbosity === 'CONCISE'
    ? 'Keep responses brief and to the point.'
    : verbosity === 'DETAILED'
    ? 'Provide comprehensive details and explanations.'
    : 'Balance detail with clarity.';

  return `You are a marketplace listing assistant helping sellers create effective listings for second-hand items.

${langInstruction}
${toneInstruction}
${verbosityGuide}

CONTEXT:
- Target marketplaces: ${regionCtx.marketplaces}
- Currency: ${regionCtx.currency}

CRITICAL RULES - YOU MUST FOLLOW THESE:
1. ONLY use brand/model/attributes if confidence >= MED. If confidence is LOW, say "Unknown brand" or "Possibly [value]" and flag for user verification.
2. NEVER invent or hallucinate specifications not provided in the input (storage, RAM, screen size, dimensions, weight, etc.).
3. If no attributes are provided, generate a generic description and list what information is missing.
4. Always include the evidence source when stating facts: "Brand: Dell (detected from logo)" or "Color: Silver (detected from image analysis)".
5. Add "Please verify" warnings for any MED confidence attributes used.
6. Be honest about uncertainty - it's better to say "I cannot determine..." than to guess.

OUTPUT FORMAT (JSON):
{
  "title": "Short, keyword-rich title (max 80 characters)",
  "description": "Full listing description with bullet points for key features",
  "suggestedDraftUpdates": [
    { "field": "title", "value": "...", "confidence": "HIGH|MED|LOW", "requiresConfirmation": true|false },
    { "field": "description", "value": "...", "confidence": "HIGH|MED|LOW", "requiresConfirmation": true|false }
  ],
  "warnings": ["List of items needing user verification"],
  "missingInfo": ["List of information that would improve the listing"],
  "suggestedNextPhoto": "Instruction for additional photo if evidence is insufficient (or null)"
}

Ensure the title is compelling for buyers and includes key searchable terms.
Ensure the description is scannable with bullet points for key features.`;
}

/**
 * Format confidence tier for prompt.
 */
function formatConfidence(confidence: ConfidenceTier | 'HIGH' | 'MED' | 'LOW'): string {
  switch (confidence) {
    case 'HIGH':
      return 'HIGH (verified)';
    case 'MED':
      return 'MED (likely correct, please verify)';
    case 'LOW':
      return 'LOW (uncertain, needs confirmation)';
    default:
      return 'UNKNOWN';
  }
}

/**
 * Format attribute for prompt inclusion.
 */
function formatAttribute(
  key: string,
  value: string,
  confidence: ConfidenceTier | 'HIGH' | 'MED' | 'LOW',
  source?: string
): string {
  const sourceNote = source ? ` (source: ${source})` : '';
  return `- ${key}: "${value}" [${formatConfidence(confidence)}]${sourceNote}`;
}

/**
 * Build the user prompt for listing generation with item context and visual evidence.
 */
export function buildListingUserPrompt(
  items: ItemContextSnapshot[],
  resolvedAttributes?: Map<string, ResolvedAttributes>,
  visualFacts?: Map<string, VisualFacts>
): string {
  if (items.length === 0) {
    return 'No items provided. Please describe the item you want to list.';
  }

  const parts: string[] = [];
  parts.push('Generate a marketplace listing for the following item(s):\n');

  for (const item of items) {
    parts.push(`## Item: ${item.itemId}`);

    // Basic info
    if (item.title) {
      parts.push(`Current title: "${item.title}"`);
    }
    if (item.category) {
      parts.push(`Category: ${item.category}`);
    }
    if (item.description) {
      parts.push(`Current description: "${item.description}"`);
    }
    if (item.priceEstimate) {
      parts.push(`Price estimate: €${item.priceEstimate.toFixed(0)}`);
    }
    if (item.photosCount) {
      parts.push(`Photos attached: ${item.photosCount}`);
    }

    // Existing attributes from item context
    if (item.attributes && item.attributes.length > 0) {
      parts.push('\nExisting attributes:');
      for (const attr of item.attributes) {
        const confidence = attr.confidence
          ? attr.confidence >= 0.8 ? 'HIGH' : attr.confidence >= 0.5 ? 'MED' : 'LOW'
          : 'MED';
        parts.push(formatAttribute(attr.key, attr.value, confidence as ConfidenceTier));
      }
    }

    // Resolved attributes from vision analysis
    const resolved = resolvedAttributes?.get(item.itemId);
    if (resolved) {
      parts.push('\nExtracted attributes from image analysis:');

      if (resolved.brand) {
        parts.push(formatAttribute(
          'brand',
          resolved.brand.value,
          resolved.brand.confidence,
          resolved.brand.evidenceRefs[0]?.type
        ));
      }
      if (resolved.model) {
        parts.push(formatAttribute(
          'model',
          resolved.model.value,
          resolved.model.confidence,
          resolved.model.evidenceRefs[0]?.type
        ));
      }
      if (resolved.color) {
        parts.push(formatAttribute(
          'color',
          resolved.color.value,
          resolved.color.confidence,
          'color extraction'
        ));
      }
      if (resolved.secondaryColor) {
        parts.push(formatAttribute(
          'secondary color',
          resolved.secondaryColor.value,
          resolved.secondaryColor.confidence,
          'color extraction'
        ));
      }
      if (resolved.material) {
        parts.push(formatAttribute(
          'material',
          resolved.material.value,
          resolved.material.confidence,
          'label detection'
        ));
      }
      if (resolved.suggestedNextPhoto) {
        parts.push(`\nNote: ${resolved.suggestedNextPhoto}`);
      }
    }

    // Visual facts summary
    const facts = visualFacts?.get(item.itemId);
    if (facts) {
      parts.push('\nVisual evidence summary:');

      if (facts.dominantColors.length > 0) {
        const colors = facts.dominantColors.slice(0, 3).map(c => `${c.name} (${c.pct}%)`).join(', ');
        parts.push(`- Dominant colors: ${colors}`);
      }

      if (facts.ocrSnippets.length > 0) {
        const texts = facts.ocrSnippets.slice(0, 5).map(s => `"${s.text}"`).join(', ');
        parts.push(`- Detected text: ${texts}`);
      }

      if (facts.logoHints && facts.logoHints.length > 0) {
        const logos = facts.logoHints.slice(0, 3).map(l => `${l.brand} (${Math.round(l.score * 100)}%)`).join(', ');
        parts.push(`- Detected logos: ${logos}`);
      }

      if (facts.labelHints.length > 0) {
        const labels = facts.labelHints.slice(0, 5).map(l => l.label).join(', ');
        parts.push(`- Image labels: ${labels}`);
      }
    }

    parts.push(''); // Empty line between items
  }

  parts.push('\nPlease generate the listing following the output format specified in the system prompt.');

  return parts.join('\n');
}

/**
 * Parse the LLM response into structured format.
 */
export interface ParsedListingResponse {
  title?: string;
  description?: string;
  suggestedDraftUpdates?: Array<{
    field: 'title' | 'description';
    value: string;
    confidence: ConfidenceTier;
    requiresConfirmation?: boolean;
  }>;
  warnings?: string[];
  missingInfo?: string[];
  suggestedNextPhoto?: string | null;
}

/**
 * Parse the LLM response content into structured data.
 */
export function parseListingResponse(content: string): ParsedListingResponse {
  // Try to extract JSON from the response
  const jsonMatch = content.match(/\{[\s\S]*\}/);
  if (!jsonMatch) {
    // If no JSON found, try to parse as plain text
    return parseTextResponse(content);
  }

  try {
    const parsed = JSON.parse(jsonMatch[0]);
    return {
      title: typeof parsed.title === 'string' ? parsed.title : undefined,
      description: typeof parsed.description === 'string' ? parsed.description : undefined,
      suggestedDraftUpdates: Array.isArray(parsed.suggestedDraftUpdates)
        ? parsed.suggestedDraftUpdates.map((update: Record<string, unknown>) => ({
            field: update.field === 'title' || update.field === 'description' ? update.field : 'title',
            value: String(update.value ?? ''),
            confidence: validateConfidence(update.confidence),
            requiresConfirmation: Boolean(update.requiresConfirmation),
          }))
        : undefined,
      warnings: Array.isArray(parsed.warnings)
        ? parsed.warnings.filter((w: unknown) => typeof w === 'string')
        : undefined,
      missingInfo: Array.isArray(parsed.missingInfo)
        ? parsed.missingInfo.filter((m: unknown) => typeof m === 'string')
        : undefined,
      suggestedNextPhoto: typeof parsed.suggestedNextPhoto === 'string'
        ? parsed.suggestedNextPhoto
        : null,
    };
  } catch {
    // JSON parsing failed, try text parsing
    return parseTextResponse(content);
  }
}

/**
 * Parse plain text response (fallback when JSON parsing fails).
 */
function parseTextResponse(content: string): ParsedListingResponse {
  const result: ParsedListingResponse = {};

  // Try to extract title
  const titleMatch = content.match(/(?:title|Title|TITLE)[:\s]*["']?([^"'\n]+)["']?/);
  if (titleMatch) {
    result.title = titleMatch[1].trim();
  }

  // Try to extract description
  const descMatch = content.match(/(?:description|Description|DESCRIPTION)[:\s]*["']?([\s\S]+?)(?=(?:warnings|Warnings|WARNINGS|$))/);
  if (descMatch) {
    result.description = descMatch[1].trim().replace(/["']$/, '');
  }

  // Extract warnings from bullet points
  const warningsMatch = content.match(/(?:warnings?|please verify)[:\s]*([\s\S]+?)(?=(?:missing|$))/i);
  if (warningsMatch) {
    result.warnings = warningsMatch[1]
      .split('\n')
      .map(line => line.replace(/^[-•*]\s*/, '').trim())
      .filter(line => line.length > 0);
  }

  return result;
}

/**
 * Validate and normalize confidence tier.
 */
function validateConfidence(value: unknown): ConfidenceTier {
  if (typeof value === 'string') {
    const upper = value.toUpperCase();
    if (upper === 'HIGH' || upper === 'MED' || upper === 'LOW') {
      return upper as ConfidenceTier;
    }
  }
  return 'MED';
}
