import {
  AssistantPrefs,
  ItemContextSnapshot,
  ConfidenceTier,
  AttributeSource,
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
  MARKETPLACE: 'Use concise, matter-of-fact marketplace copy. No marketing hype, exclamation marks, or emojis. Avoid phrases like "perfect for", "don\'t miss out", "amazing". Title: short, includes key identifiers (type + brand + model + size/color if available). Description: 3-6 bullet lines max covering condition, key specs, what\'s included, defects (if known). Only use detected attributes; do not invent details. If info is missing, mark as "Unknown" or omit.',
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

  return `You are a marketplace listing assistant helping sellers create effective, marketplace-ready listings for second-hand items.

${langInstruction}
${toneInstruction}
${verbosityGuide}

CONTEXT:
- Target marketplaces: ${regionCtx.marketplaces}
- Currency: ${regionCtx.currency}

ATTRIBUTE HANDLING - CRITICAL:
1. USER-PROVIDED attributes (marked [USER]) are AUTHORITATIVE - use them exactly as given without questioning.
2. DETECTED attributes (marked [DETECTED]) with HIGH confidence - use with confidence, cite source.
3. DETECTED attributes with MED confidence - use with "Please verify" warning.
4. DETECTED attributes with LOW confidence - mention as "Possibly [value]" or omit.
5. NEVER invent specifications not provided (storage, RAM, screen size, dimensions, etc.).

TITLE RULES:
- Maximum 80 characters
- Include brand (if known) + model/type + key differentiator
- Front-load important keywords for search visibility
- Format: "[Brand] [Model/Type] - [Key Feature/Condition]"
- Examples: "Dell XPS 13 Laptop - 16GB RAM, Excellent Condition" (55 chars)

DESCRIPTION RULES:
- Start with 1-2 sentence overview
- Use bullet points (•) for features and specifications
- Include condition details
- End with shipping/pickup info if relevant
- Structure: Overview → Key Features (bullets) → Condition → Notes

OUTPUT FORMAT (JSON):
{
  "title": "Keyword-rich title (max 80 chars)",
  "description": "Full description with bullet points",
  "suggestedDraftUpdates": [
    { "field": "title", "value": "...", "confidence": "HIGH|MED|LOW", "requiresConfirmation": false },
    { "field": "description", "value": "...", "confidence": "HIGH|MED|LOW", "requiresConfirmation": false }
  ],
  "warnings": ["Items needing verification (only for DETECTED non-HIGH)"],
  "missingInfo": ["Information that would improve the listing"],
  "suggestedNextPhoto": "Photo suggestion if evidence is insufficient (or null)"
}

CONFIDENCE ASSIGNMENT:
- HIGH: User-provided values OR detected with strong visual evidence
- MED: Detected with moderate evidence, needs verification
- LOW: Speculative, insufficient evidence`;
}

/**
 * Format confidence tier for prompt.
 */
function formatConfidence(confidence: ConfidenceTier | 'HIGH' | 'MED' | 'LOW'): string {
  switch (confidence) {
    case 'HIGH':
      return 'HIGH';
    case 'MED':
      return 'MED';
    case 'LOW':
      return 'LOW';
    default:
      return 'UNKNOWN';
  }
}

/**
 * Format attribute source tag for prompt.
 * USER source is marked as authoritative.
 */
function formatSourceTag(source?: AttributeSource): string {
  switch (source) {
    case 'USER':
      return '[USER]'; // Authoritative - use as-is
    case 'DETECTED':
      return '[DETECTED]';
    case 'DEFAULT':
      return '[DEFAULT]';
    default:
      return '[DETECTED]'; // Treat unknown as detected for safety
  }
}

/**
 * Format attribute for prompt inclusion.
 * User-provided attributes are marked as authoritative.
 */
function formatAttribute(
  key: string,
  value: string,
  confidence: ConfidenceTier | 'HIGH' | 'MED' | 'LOW',
  source?: AttributeSource | string,
  evidenceSource?: string
): string {
  // Determine if this is a user-provided value
  const isUserProvided = source === 'USER';
  const sourceTag = typeof source === 'string' && ['USER', 'DETECTED', 'DEFAULT', 'UNKNOWN'].includes(source)
    ? formatSourceTag(source as AttributeSource)
    : formatSourceTag(undefined);

  // For user-provided values, always HIGH confidence
  const effectiveConfidence = isUserProvided ? 'HIGH' : confidence;
  const confidenceStr = formatConfidence(effectiveConfidence);

  // Evidence note for detected values
  const evidenceNote = evidenceSource && !isUserProvided ? ` (from: ${evidenceSource})` : '';

  return `- ${key}: "${value}" ${sourceTag} [${confidenceStr}]${evidenceNote}`;
}

/**
 * Build the user prompt for listing generation with item context and visual evidence.
 * Prioritizes user-provided attributes over detected ones.
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
  parts.push('Generate a marketplace-ready listing for the following item(s):\n');

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

    // Separate user-provided from detected attributes
    const userAttributes = item.attributes?.filter(a => a.source === 'USER') ?? [];
    const detectedAttributes = item.attributes?.filter(a => a.source !== 'USER') ?? [];

    // User-provided attributes (authoritative - show first)
    if (userAttributes.length > 0) {
      parts.push('\n**User-provided attributes (use as-is):**');
      for (const attr of userAttributes) {
        parts.push(formatAttribute(attr.key, attr.value, 'HIGH', 'USER'));
      }
    }

    // Detected attributes from item context
    if (detectedAttributes.length > 0) {
      parts.push('\nDetected attributes:');
      for (const attr of detectedAttributes) {
        const confidence = attr.confidence
          ? attr.confidence >= 0.8 ? 'HIGH' : attr.confidence >= 0.5 ? 'MED' : 'LOW'
          : 'MED';
        parts.push(formatAttribute(attr.key, attr.value, confidence as ConfidenceTier, attr.source ?? 'DETECTED'));
      }
    }

    // Resolved attributes from vision analysis (only if not already user-provided)
    const resolved = resolvedAttributes?.get(item.itemId);
    if (resolved) {
      const userAttrKeys = new Set(userAttributes.map(a => a.key.toLowerCase()));

      // Only show vision-detected attributes that weren't user-provided
      const visionAttrs: string[] = [];

      if (resolved.brand && !userAttrKeys.has('brand')) {
        visionAttrs.push(formatAttribute(
          'brand',
          resolved.brand.value,
          resolved.brand.confidence,
          'DETECTED',
          resolved.brand.evidenceRefs[0]?.type
        ));
      }
      if (resolved.model && !userAttrKeys.has('model')) {
        visionAttrs.push(formatAttribute(
          'model',
          resolved.model.value,
          resolved.model.confidence,
          'DETECTED',
          resolved.model.evidenceRefs[0]?.type
        ));
      }
      if (resolved.color && !userAttrKeys.has('color')) {
        visionAttrs.push(formatAttribute(
          'color',
          resolved.color.value,
          resolved.color.confidence,
          'DETECTED',
          'color extraction'
        ));
      }
      if (resolved.secondaryColor && !userAttrKeys.has('secondary_color')) {
        visionAttrs.push(formatAttribute(
          'secondary color',
          resolved.secondaryColor.value,
          resolved.secondaryColor.confidence,
          'DETECTED',
          'color extraction'
        ));
      }
      if (resolved.material && !userAttrKeys.has('material')) {
        visionAttrs.push(formatAttribute(
          'material',
          resolved.material.value,
          resolved.material.confidence,
          'DETECTED',
          'label detection'
        ));
      }

      if (visionAttrs.length > 0) {
        parts.push('\nVision-extracted attributes:');
        parts.push(...visionAttrs);
      }

      if (resolved.suggestedNextPhoto) {
        parts.push(`\nNote: ${resolved.suggestedNextPhoto}`);
      }
    }

    // Visual facts summary (OCR snippets useful for context)
    const facts = visualFacts?.get(item.itemId);
    if (facts) {
      const factParts: string[] = [];

      if (facts.dominantColors.length > 0) {
        const colors = facts.dominantColors.slice(0, 3).map(c => `${c.name} (${c.pct}%)`).join(', ');
        factParts.push(`- Dominant colors: ${colors}`);
      }

      if (facts.ocrSnippets.length > 0) {
        const texts = facts.ocrSnippets.slice(0, 5).map(s => `"${s.text}"`).join(', ');
        factParts.push(`- OCR text detected: ${texts}`);
      }

      if (facts.logoHints && facts.logoHints.length > 0) {
        const logos = facts.logoHints.slice(0, 3).map(l => `${l.brand} (${Math.round(l.score * 100)}%)`).join(', ');
        factParts.push(`- Detected logos: ${logos}`);
      }

      if (facts.labelHints.length > 0) {
        const labels = facts.labelHints.slice(0, 5).map(l => l.label).join(', ');
        factParts.push(`- Image labels: ${labels}`);
      }

      if (factParts.length > 0) {
        parts.push('\nVisual evidence (for reference):');
        parts.push(...factParts);
      }
    }

    parts.push(''); // Empty line between items
  }

  parts.push('\nGenerate the listing following the output format. Remember: [USER] attributes are authoritative.');

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
