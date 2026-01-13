import {
  AssistantPrefs,
  ItemContextSnapshot,
  ConfidenceTier,
  AttributeSource,
} from '../types.js';
import { ResolvedAttributes } from '../../vision/attribute-resolver.js';
import { VisualFacts } from '../../vision/types.js';
import {
  getLocalizedContent,
  getLocalizedAttributeLabel,
  LocalizedPromptContent,
} from './prompt-localization.js';

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
 * Get localized tone instruction.
 */
function getLocalizedTone(tone: string, content: LocalizedPromptContent): string {
  switch (tone) {
    case 'FRIENDLY':
      return content.tones.friendly;
    case 'PROFESSIONAL':
      return content.tones.professional;
    case 'MARKETPLACE':
      return content.tones.marketplace;
    default:
      return content.tones.neutral;
  }
}

/**
 * Get localized verbosity instruction.
 */
function getLocalizedVerbosity(verbosity: string, content: LocalizedPromptContent): string {
  switch (verbosity) {
    case 'CONCISE':
      return content.verbosity.concise;
    case 'DETAILED':
      return content.verbosity.detailed;
    default:
      return content.verbosity.normal;
  }
}

/**
 * Build the system prompt for marketplace listing generation.
 * All prompt text is fully localized based on the user's language preference.
 */
export function buildListingSystemPrompt(prefs?: AssistantPrefs): string {
  const language = prefs?.language ?? 'EN';
  const tone = prefs?.tone ?? 'NEUTRAL';
  const region = prefs?.region ?? 'EU';
  const verbosity = prefs?.verbosity ?? 'NORMAL';

  // Get fully localized content
  const content = getLocalizedContent(language);
  const toneInstruction = getLocalizedTone(tone, content);
  const verbosityGuide = getLocalizedVerbosity(verbosity, content);
  const regionCtx = REGION_CONTEXT[region] ?? REGION_CONTEXT.EU;

  // Build fully localized system prompt
  return `${content.roleDescription}

${content.languageEnforcement}
${toneInstruction}
${verbosityGuide}

${content.contextSection}:
- Target marketplaces: ${regionCtx.marketplaces}
- Currency: ${regionCtx.currency}

${content.attributeHandling.title}:
1. ${content.attributeHandling.userProvided}
2. ${content.attributeHandling.detectedHigh}
3. ${content.attributeHandling.detectedMed}
4. ${content.attributeHandling.detectedLow}
5. ${content.attributeHandling.neverInvent}

${content.titleRules.title}:
- ${content.titleRules.maxChars}
- ${content.titleRules.include}
- ${content.titleRules.frontLoad}
- ${content.titleRules.format}
- ${content.titleRules.example}

${content.descriptionRules.title}:
- ${content.descriptionRules.startWith}
- ${content.descriptionRules.useBullets}
- ${content.descriptionRules.includeCondition}
- ${content.descriptionRules.structure}

${content.outputFormat.title}:
{
  "title": "${content.outputFormat.fields.title}",
  "description": "${content.outputFormat.fields.description}",
  "suggestedDraftUpdates": [
    { "field": "title", "value": "...", "confidence": "HIGH|MED|LOW", "requiresConfirmation": false },
    { "field": "description", "value": "...", "confidence": "HIGH|MED|LOW", "requiresConfirmation": false }
  ],
  "warnings": ["${content.outputFormat.fields.warnings}"],
  "missingInfo": ["${content.outputFormat.fields.missingInfo}"],
  "suggestedNextPhoto": "${content.outputFormat.fields.suggestedNextPhoto}"
}

${content.confidenceAssignment.title}:
- ${content.confidenceAssignment.high}
- ${content.confidenceAssignment.med}
- ${content.confidenceAssignment.low}`;
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
 * Attribute labels are localized based on language preference.
 */
function formatAttribute(
  key: string,
  value: string,
  confidence: ConfidenceTier | 'HIGH' | 'MED' | 'LOW',
  source?: AttributeSource | string,
  evidenceSource?: string,
  language?: string
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

  // Get localized attribute label
  const localizedKey = language ? getLocalizedAttributeLabel(key, language) : key;

  return `- ${localizedKey}: "${value}" ${sourceTag} [${confidenceStr}]${evidenceNote}`;
}

/**
 * Build the user prompt for listing generation with item context and visual evidence.
 * Prioritizes user-provided attributes over detected ones.
 * All section headers and attribute labels are localized based on language preference.
 */
export function buildListingUserPrompt(
  items: ItemContextSnapshot[],
  resolvedAttributes?: Map<string, ResolvedAttributes>,
  visualFacts?: Map<string, VisualFacts>,
  language?: string
): string {
  const lang = language ?? 'EN';
  const content = getLocalizedContent(lang);

  if (items.length === 0) {
    return 'No items provided. Please describe the item you want to list.';
  }

  const parts: string[] = [];
  parts.push(`${content.userPrompt.generateListing}\n`);

  for (const item of items) {
    parts.push(`***REMOVED******REMOVED*** ${content.userPrompt.itemHeader}: ${item.itemId}`);

    // Basic info with localized labels
    if (item.title) {
      parts.push(`${content.userPrompt.currentTitle}: "${item.title}"`);
    }
    if (item.category) {
      parts.push(`${content.userPrompt.category}: ${item.category}`);
    }
    if (item.description) {
      parts.push(`${content.userPrompt.currentDescription}: "${item.description}"`);
    }
    if (item.priceEstimate) {
      parts.push(`${content.userPrompt.priceEstimate}: €${item.priceEstimate.toFixed(0)}`);
    }
    if (item.photosCount) {
      parts.push(`${content.userPrompt.photosAttached}: ${item.photosCount}`);
    }

    // Separate user-provided from detected attributes
    const userAttributes = item.attributes?.filter(a => a.source === 'USER') ?? [];
    const detectedAttributes = item.attributes?.filter(a => a.source !== 'USER') ?? [];

    // User-provided attributes (authoritative - show first)
    if (userAttributes.length > 0) {
      parts.push(`\n**${content.userPrompt.userProvidedAttributes}:**`);
      for (const attr of userAttributes) {
        parts.push(formatAttribute(attr.key, attr.value, 'HIGH', 'USER', undefined, lang));
      }
    }

    // Detected attributes from item context
    if (detectedAttributes.length > 0) {
      parts.push(`\n${content.userPrompt.detectedAttributes}:`);
      for (const attr of detectedAttributes) {
        const confidence = attr.confidence
          ? attr.confidence >= 0.8 ? 'HIGH' : attr.confidence >= 0.5 ? 'MED' : 'LOW'
          : 'MED';
        parts.push(formatAttribute(attr.key, attr.value, confidence as ConfidenceTier, attr.source ?? 'DETECTED', undefined, lang));
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
          resolved.brand.evidenceRefs[0]?.type,
          lang
        ));
      }
      if (resolved.model && !userAttrKeys.has('model')) {
        visionAttrs.push(formatAttribute(
          'model',
          resolved.model.value,
          resolved.model.confidence,
          'DETECTED',
          resolved.model.evidenceRefs[0]?.type,
          lang
        ));
      }
      if (resolved.color && !userAttrKeys.has('color')) {
        visionAttrs.push(formatAttribute(
          'color',
          resolved.color.value,
          resolved.color.confidence,
          'DETECTED',
          'color extraction',
          lang
        ));
      }
      if (resolved.secondaryColor && !userAttrKeys.has('secondary_color')) {
        visionAttrs.push(formatAttribute(
          'secondary_color',
          resolved.secondaryColor.value,
          resolved.secondaryColor.confidence,
          'DETECTED',
          'color extraction',
          lang
        ));
      }
      if (resolved.material && !userAttrKeys.has('material')) {
        visionAttrs.push(formatAttribute(
          'material',
          resolved.material.value,
          resolved.material.confidence,
          'DETECTED',
          'label detection',
          lang
        ));
      }

      if (visionAttrs.length > 0) {
        parts.push(`\n${content.userPrompt.visionExtractedAttributes}:`);
        parts.push(...visionAttrs);
      }

      if (resolved.suggestedNextPhoto) {
        parts.push(`\n${content.userPrompt.note}: ${resolved.suggestedNextPhoto}`);
      }
    }

    // Visual facts summary (OCR snippets useful for context)
    const facts = visualFacts?.get(item.itemId);
    if (facts) {
      const factParts: string[] = [];

      if (facts.dominantColors.length > 0) {
        const colors = facts.dominantColors.slice(0, 3).map(c => `${c.name} (${c.pct}%)`).join(', ');
        factParts.push(`- ${content.userPrompt.dominantColors}: ${colors}`);
      }

      if (facts.ocrSnippets.length > 0) {
        const texts = facts.ocrSnippets.slice(0, 5).map(s => `"${s.text}"`).join(', ');
        factParts.push(`- ${content.userPrompt.ocrTextDetected}: ${texts}`);
      }

      if (facts.logoHints && facts.logoHints.length > 0) {
        const logos = facts.logoHints.slice(0, 3).map(l => `${l.brand} (${Math.round(l.score * 100)}%)`).join(', ');
        factParts.push(`- ${content.userPrompt.detectedLogos}: ${logos}`);
      }

      if (facts.labelHints.length > 0) {
        const labels = facts.labelHints.slice(0, 5).map(l => l.label).join(', ');
        factParts.push(`- ${content.userPrompt.imageLabels}: ${labels}`);
      }

      if (factParts.length > 0) {
        parts.push(`\n${content.userPrompt.visualEvidence}:`);
        parts.push(...factParts);
      }
    }

    parts.push(''); // Empty line between items
  }

  parts.push(`\n${content.userPrompt.generateReminder}`);

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
        ? parsed.suggestedDraftUpdates
            .map((update: Record<string, unknown>) => ({
              field: update.field === 'title' || update.field === 'description' ? update.field : 'title',
              value: String(update.value ?? ''),
              confidence: validateConfidence(update.confidence),
              requiresConfirmation: Boolean(update.requiresConfirmation),
            }))
            .filter((update: { value: string }) => update.value.trim().length > 0) // Filter out empty/null values
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
