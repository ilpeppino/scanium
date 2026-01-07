/**
 * Enrichment Pipeline
 *
 * 3-stage pipeline for scan-to-enrichment:
 * Stage A: Vision facts extraction (Google Vision) + caching by image hash
 * Stage B: Attribute normalization (rules first, optional LLM for product_type)
 * Stage C: Draft generation (OpenAI) with template fallback
 */

import { createHash } from 'crypto';
import OpenAI from 'openai';
import {
  EnrichRequest,
  EnrichmentConfig,
  EnrichmentStage,
  VisionFactsSummary,
  NormalizedAttribute,
  ListingDraft,
  EnrichmentError,
  AttributeConfidence,
} from './types.js';
import { VisionExtractor, MockVisionExtractor } from '../vision/extractor.js';
import { VisualFacts, VisionImageInput } from '../vision/types.js';
import { VisualFactsCache, buildCacheKey } from '../vision/cache.js';
import { resolveAttributes, collectAttributeCandidates } from '../vision/attribute-resolver.js';
import { FastifyBaseLogger } from 'fastify';
import { loadConfig } from '../../config/index.js';

/**
 * Result from the enrichment pipeline.
 */
export type PipelineResult = {
  success: boolean;
  error?: EnrichmentError;
};

/**
 * Callback for stage progress updates.
 */
export type StageUpdateCallback = (
  stage: EnrichmentStage,
  data: Partial<{
    visionFacts: VisionFactsSummary;
    normalizedAttributes: NormalizedAttribute[];
    draft: ListingDraft;
    timingMs: number;
    timingKey: 'vision' | 'attributes' | 'draft';
  }>
) => void;

// Product type dictionary for rule-based normalization
const PRODUCT_TYPE_MAPPINGS: Record<string, string[]> = {
  't-shirt': ['shirt', 'tee', 'tshirt', 't shirt', 'top', 'jersey'],
  'tissue box': ['tissue', 'tissues', 'kleenex', 'facial tissue'],
  'lip balm': ['lip', 'chapstick', 'labello', 'lip care', 'lip stick'],
  'sneakers': ['shoe', 'sneaker', 'footwear', 'trainer', 'kicks'],
  'jeans': ['denim', 'pants', 'jeans', 'jean'],
  'jacket': ['coat', 'jacket', 'blazer', 'hoodie', 'outerwear'],
  'watch': ['watch', 'timepiece', 'wristwatch'],
  'bag': ['bag', 'purse', 'handbag', 'backpack', 'tote'],
  'phone': ['phone', 'smartphone', 'mobile', 'iphone', 'android'],
  'laptop': ['laptop', 'notebook', 'computer', 'macbook'],
  'headphones': ['headphone', 'earphone', 'earbud', 'airpod'],
  'speaker': ['speaker', 'bluetooth speaker', 'soundbar'],
  'book': ['book', 'novel', 'textbook', 'paperback'],
  'toy': ['toy', 'action figure', 'doll', 'lego'],
  'mug': ['mug', 'cup', 'coffee cup', 'tumbler'],
  'bottle': ['bottle', 'water bottle', 'flask'],
  'candle': ['candle', 'candle holder', 'wax'],
};

// Generic labels to reject
const GENERIC_LABELS = new Set([
  'product', 'item', 'object', 'thing', 'goods', 'merchandise',
  'material', 'plastic', 'metal', 'wood', 'fabric', 'textile',
  'fashion', 'accessory', 'electronics', 'home', 'decoration',
  'indoor', 'outdoor', 'white', 'black', 'blue', 'red', 'green',
]);

// Singleton cache instance
let visualFactsCache: VisualFactsCache | null = null;

function getVisualFactsCache(): VisualFactsCache {
  if (!visualFactsCache) {
    visualFactsCache = new VisualFactsCache({
      ttlMs: 6 * 60 * 60 * 1000, // 6 hours
      maxEntries: 1000,
    });
  }
  return visualFactsCache;
}

/**
 * Compute SHA-256 hash of image data for caching.
 */
function computeImageHash(base64Data: string): string {
  return createHash('sha256').update(base64Data).digest('hex').slice(0, 16);
}

/**
 * Create vision extractor based on config.
 */
function createVisionExtractor(): VisionExtractor | MockVisionExtractor {
  try {
    const config = loadConfig();
    if (config.vision.provider === 'mock') {
      return new MockVisionExtractor();
    }
    return new VisionExtractor({
      timeoutMs: config.vision.timeoutMs,
      maxRetries: config.vision.maxRetries,
      enableLogoDetection: config.vision.enableLogos,
    });
  } catch {
    // Fallback to mock if config loading fails
    return new MockVisionExtractor();
  }
}

/**
 * Run Stage A: Vision facts extraction.
 */
async function stageVision(
  request: EnrichRequest,
  _config: EnrichmentConfig,
  logger: FastifyBaseLogger,
  onUpdate: StageUpdateCallback
): Promise<{ facts: VisualFacts | null; cacheHit: boolean }> {
  const startTime = Date.now();
  onUpdate('VISION_STARTED', {});

  const imageHash = computeImageHash(request.imageBase64);
  const cacheKey = buildCacheKey([imageHash]);
  const cache = getVisualFactsCache();

  // Check cache first
  const cached = cache.get(cacheKey);
  if (cached) {
    const timingMs = Date.now() - startTime;
    logger.info({
      msg: 'Vision cache HIT',
      itemId: request.itemId,
      imageHash,
      timingMs,
    });

    const summary = factsToSummary(cached);
    onUpdate('VISION_DONE', {
      visionFacts: summary,
      timingMs,
      timingKey: 'vision',
    });

    return { facts: cached, cacheHit: true };
  }

  logger.info({
    msg: 'Vision cache MISS - extracting',
    itemId: request.itemId,
    imageHash,
  });

  try {
    const extractor = createVisionExtractor();
    const imageInput: VisionImageInput = {
      base64Data: request.imageBase64,
      mimeType: request.imageMimeType,
    };

    const result = await extractor.extractVisualFacts(request.itemId, [imageInput], {
      enableOcr: true,
      enableLabels: true,
      enableLogos: true,
      enableColors: true,
    });

    const timingMs = Date.now() - startTime;

    if (!result.success || !result.facts) {
      logger.error({
        msg: 'Vision extraction failed',
        itemId: request.itemId,
        error: result.error,
      });

      onUpdate('VISION_DONE', { timingMs, timingKey: 'vision' });
      return { facts: null, cacheHit: false };
    }

    // Cache the result
    cache.set(cacheKey, result.facts);

    const summary = factsToSummary(result.facts);
    onUpdate('VISION_DONE', {
      visionFacts: summary,
      timingMs,
      timingKey: 'vision',
    });

    logger.info({
      msg: 'Vision extraction complete',
      itemId: request.itemId,
      timingMs,
      ocrSnippets: summary.ocrSnippets.length,
      logoHints: summary.logoHints.length,
      colors: summary.dominantColors.length,
    });

    return { facts: result.facts, cacheHit: false };
  } catch (err) {
    const timingMs = Date.now() - startTime;
    logger.error({
      msg: 'Vision extraction error',
      itemId: request.itemId,
      error: err instanceof Error ? err.message : String(err),
      timingMs,
    });

    onUpdate('VISION_DONE', { timingMs, timingKey: 'vision' });
    return { facts: null, cacheHit: false };
  }
}

/**
 * Convert VisualFacts to VisionFactsSummary for API response.
 */
function factsToSummary(facts: VisualFacts): VisionFactsSummary {
  return {
    ocrSnippets: facts.ocrSnippets.slice(0, 5).map((s) => s.text),
    logoHints: (facts.logoHints ?? []).slice(0, 3).map((l) => ({
      name: l.brand,
      confidence: l.score,
    })),
    dominantColors: facts.dominantColors.slice(0, 5).map((c) => ({
      name: c.name,
      hex: c.rgbHex,
      pct: c.pct,
    })),
    labelHints: facts.labelHints.slice(0, 5).map((l) => l.label),
  };
}

/**
 * Run Stage B: Attribute normalization.
 */
async function stageAttributes(
  request: EnrichRequest,
  facts: VisualFacts | null,
  _config: EnrichmentConfig,
  logger: FastifyBaseLogger,
  onUpdate: StageUpdateCallback
): Promise<NormalizedAttribute[]> {
  const startTime = Date.now();
  onUpdate('ATTRIBUTES_STARTED', {});

  const attributes: NormalizedAttribute[] = [];

  if (!facts) {
    const timingMs = Date.now() - startTime;
    onUpdate('ATTRIBUTES_DONE', {
      normalizedAttributes: attributes,
      timingMs,
      timingKey: 'attributes',
    });
    return attributes;
  }

  // Use existing attribute resolver for brand/model/color
  const resolved = resolveAttributes(request.itemId, facts);
  // Collect candidates for potential future use
  collectAttributeCandidates(facts);

  // Add brand
  if (resolved.brand) {
    attributes.push({
      key: 'brand',
      value: resolved.brand.value,
      confidence: resolved.brand.confidence,
      source: resolved.brand.evidenceRefs[0]?.type === 'logo' ? 'VISION_LOGO' : 'VISION_OCR',
      evidence: resolved.brand.evidenceRefs[0]?.value,
    });
  }

  // Add model
  if (resolved.model) {
    attributes.push({
      key: 'model',
      value: resolved.model.value,
      confidence: resolved.model.confidence,
      source: 'VISION_OCR',
      evidence: resolved.model.evidenceRefs[0]?.value,
    });
  }

  // Add primary color
  if (resolved.color) {
    attributes.push({
      key: 'color',
      value: resolved.color.value,
      confidence: resolved.color.confidence,
      source: 'VISION_COLOR',
      evidence: resolved.color.evidenceRefs[0]?.value,
    });
  }

  // Add secondary color
  if (resolved.secondaryColor) {
    attributes.push({
      key: 'secondary_color',
      value: resolved.secondaryColor.value,
      confidence: resolved.secondaryColor.confidence,
      source: 'VISION_COLOR',
      evidence: resolved.secondaryColor.evidenceRefs[0]?.value,
    });
  }

  // Add material
  if (resolved.material) {
    attributes.push({
      key: 'material',
      value: resolved.material.value,
      confidence: resolved.material.confidence,
      source: 'VISION_LABEL',
      evidence: resolved.material.evidenceRefs[0]?.value,
    });
  }

  // Resolve product_type from labels
  const productType = resolveProductType(facts);
  if (productType) {
    attributes.push(productType);
  }

  const timingMs = Date.now() - startTime;
  onUpdate('ATTRIBUTES_DONE', {
    normalizedAttributes: attributes,
    timingMs,
    timingKey: 'attributes',
  });

  logger.info({
    msg: 'Attributes normalized',
    itemId: request.itemId,
    timingMs,
    attributeCount: attributes.length,
    attributes: attributes.map((a) => `${a.key}=${a.value}`),
  });

  return attributes;
}

/**
 * Resolve product_type from labels using rule-based mapping.
 */
function resolveProductType(facts: VisualFacts): NormalizedAttribute | null {
  // Check labels against product type mappings
  for (const label of facts.labelHints) {
    const labelLower = label.label.toLowerCase();

    // Skip generic labels
    if (GENERIC_LABELS.has(labelLower)) {
      continue;
    }

    // Check against mappings
    for (const [productType, keywords] of Object.entries(PRODUCT_TYPE_MAPPINGS)) {
      if (keywords.some((kw) => labelLower.includes(kw))) {
        return {
          key: 'product_type',
          value: productType,
          confidence: label.score >= 0.8 ? 'HIGH' : label.score >= 0.6 ? 'MED' : 'LOW',
          source: 'VISION_LABEL',
          evidence: label.label,
        };
      }
    }
  }

  // Check OCR text for product type hints
  for (const snippet of facts.ocrSnippets) {
    const textLower = snippet.text.toLowerCase();

    for (const [productType, keywords] of Object.entries(PRODUCT_TYPE_MAPPINGS)) {
      if (keywords.some((kw) => textLower.includes(kw))) {
        return {
          key: 'product_type',
          value: productType,
          confidence: 'MED',
          source: 'VISION_OCR',
          evidence: snippet.text,
        };
      }
    }
  }

  // Return top label if not too generic
  const topLabel = facts.labelHints[0];
  if (topLabel && !GENERIC_LABELS.has(topLabel.label.toLowerCase()) && topLabel.score >= 0.7) {
    return {
      key: 'product_type',
      value: topLabel.label.toLowerCase(),
      confidence: 'LOW',
      source: 'VISION_LABEL',
      evidence: topLabel.label,
    };
  }

  return null;
}

/**
 * Run Stage C: Draft generation.
 */
async function stageDraft(
  request: EnrichRequest,
  _facts: VisualFacts | null,
  attributes: NormalizedAttribute[],
  config: EnrichmentConfig,
  logger: FastifyBaseLogger,
  onUpdate: StageUpdateCallback
): Promise<ListingDraft | null> {
  const startTime = Date.now();
  onUpdate('DRAFT_STARTED', {});

  // Check if draft generation is enabled
  if (!config.enableDraftGeneration) {
    const timingMs = Date.now() - startTime;
    onUpdate('DRAFT_DONE', { timingMs, timingKey: 'draft' });
    return null;
  }

  // Check for OpenAI API key from config
  let openaiKey: string | undefined;
  try {
    const appConfig = loadConfig();
    openaiKey = appConfig.assistant.openaiApiKey;
  } catch {
    // Ignore config errors
  }

  if (!openaiKey) {
    logger.warn({
      msg: 'OpenAI API key not configured, using template fallback',
      itemId: request.itemId,
    });

    const draft = generateTemplateDraft(attributes, request.itemContext);
    const timingMs = Date.now() - startTime;
    onUpdate('DRAFT_DONE', { draft, timingMs, timingKey: 'draft' });
    return draft;
  }

  try {
    const client = new OpenAI({
      apiKey: openaiKey,
      timeout: config.draftTimeoutMs,
    });

    // Build context from attributes
    const attrContext = attributes
      .map((a) => `${a.key}: ${a.value} (${a.confidence} confidence)`)
      .join('\n');

    const userContext = request.itemContext
      ? `User context: category=${request.itemContext.category ?? 'unknown'}, condition=${request.itemContext.condition ?? 'unknown'}`
      : '';

    const prompt = `You are an expert at writing concise, compelling product listings for resale marketplaces like eBay.

Based on the following detected attributes and context, generate a listing title and description.

DETECTED ATTRIBUTES:
${attrContext || 'No attributes detected'}

${userContext}

RULES:
1. Title: Max 80 characters, include brand + product type + key feature/color if available
2. Description: 2-3 short sentences, highlight condition-relevant details
3. Only include information you can infer from the attributes
4. If critical info is missing, note it in missingFields
5. Be honest about uncertainty

Respond with ONLY valid JSON in this format:
{
  "title": "listing title here",
  "description": "listing description here",
  "missingFields": ["field1", "field2"],
  "confidence": "HIGH" | "MED" | "LOW"
}`;

    const response = await client.chat.completions.create({
      model: config.llmModel,
      max_tokens: 500,
      messages: [
        { role: 'system', content: 'You are a helpful assistant that generates product listings. Always respond with valid JSON only.' },
        { role: 'user', content: prompt },
      ],
    });

    const content = response.choices[0]?.message?.content ?? '';
    const timingMs = Date.now() - startTime;

    // Parse JSON response
    try {
      // Extract JSON from response (handle markdown code blocks)
      let jsonStr = content;
      const jsonMatch = content.match(/```(?:json)?\s*([\s\S]*?)```/);
      if (jsonMatch) {
        jsonStr = jsonMatch[1].trim();
      }

      const parsed = JSON.parse(jsonStr) as {
        title?: string;
        description?: string;
        missingFields?: string[];
        confidence?: string;
      };

      const draft: ListingDraft = {
        title: parsed.title ?? generateTemplateTitle(attributes),
        description: parsed.description ?? generateTemplateDescription(attributes),
        missingFields: parsed.missingFields,
        confidence: (parsed.confidence as AttributeConfidence) ?? 'MED',
      };

      onUpdate('DRAFT_DONE', { draft, timingMs, timingKey: 'draft' });

      logger.info({
        msg: 'Draft generated via LLM',
        itemId: request.itemId,
        timingMs,
        titleLength: draft.title.length,
        confidence: draft.confidence,
      });

      return draft;
    } catch {
      logger.warn({
        msg: 'Failed to parse LLM response, using template',
        itemId: request.itemId,
        response: content.substring(0, 200),
      });

      const draft = generateTemplateDraft(attributes, request.itemContext);
      onUpdate('DRAFT_DONE', { draft, timingMs, timingKey: 'draft' });
      return draft;
    }
  } catch (err) {
    const timingMs = Date.now() - startTime;
    logger.error({
      msg: 'LLM draft generation failed, using template',
      itemId: request.itemId,
      error: err instanceof Error ? err.message : String(err),
      timingMs,
    });

    const draft = generateTemplateDraft(attributes, request.itemContext);
    onUpdate('DRAFT_DONE', { draft, timingMs, timingKey: 'draft' });
    return draft;
  }
}

/**
 * Generate a template-based draft (fallback when LLM unavailable).
 */
function generateTemplateDraft(
  attributes: NormalizedAttribute[],
  context?: EnrichRequest['itemContext']
): ListingDraft {
  return {
    title: generateTemplateTitle(attributes),
    description: generateTemplateDescription(attributes, context),
    missingFields: detectMissingFields(attributes),
    confidence: 'LOW',
  };
}

/**
 * Generate template title from attributes.
 */
function generateTemplateTitle(attributes: NormalizedAttribute[]): string {
  const parts: string[] = [];

  const brand = attributes.find((a) => a.key === 'brand');
  const productType = attributes.find((a) => a.key === 'product_type');
  const color = attributes.find((a) => a.key === 'color');
  const model = attributes.find((a) => a.key === 'model');

  if (brand) parts.push(brand.value);
  if (productType) parts.push(capitalize(productType.value));
  if (model) parts.push(model.value);
  if (color && parts.length < 4) parts.push(capitalize(color.value));

  if (parts.length === 0) {
    return 'Item for Sale';
  }

  return parts.join(' ').substring(0, 80);
}

/**
 * Generate template description from attributes.
 */
function generateTemplateDescription(
  attributes: NormalizedAttribute[],
  context?: EnrichRequest['itemContext']
): string {
  const lines: string[] = [];

  const brand = attributes.find((a) => a.key === 'brand');
  const productType = attributes.find((a) => a.key === 'product_type');
  const color = attributes.find((a) => a.key === 'color');
  const material = attributes.find((a) => a.key === 'material');

  if (brand && productType) {
    lines.push(`${brand.value} ${productType.value} in excellent condition.`);
  } else if (productType) {
    lines.push(`${capitalize(productType.value)} in great condition.`);
  } else {
    lines.push('Quality item in good condition.');
  }

  const details: string[] = [];
  if (color) details.push(`Color: ${color.value}`);
  if (material) details.push(`Material: ${material.value}`);

  if (details.length > 0) {
    lines.push(details.join('. ') + '.');
  }

  if (context?.condition) {
    lines.push(`Condition: ${context.condition}.`);
  }

  return lines.join(' ');
}

/**
 * Detect missing critical fields.
 */
function detectMissingFields(attributes: NormalizedAttribute[]): string[] {
  const missing: string[] = [];
  const keys = new Set(attributes.map((a) => a.key));

  if (!keys.has('brand')) missing.push('brand');
  if (!keys.has('product_type')) missing.push('product_type');
  if (!keys.has('color')) missing.push('color');

  return missing;
}

/**
 * Capitalize first letter of each word.
 */
function capitalize(str: string): string {
  return str
    .split(' ')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(' ');
}

/**
 * Run the full 3-stage enrichment pipeline.
 */
export async function runEnrichmentPipeline(
  request: EnrichRequest,
  config: EnrichmentConfig,
  logger: FastifyBaseLogger,
  onUpdate: StageUpdateCallback
): Promise<PipelineResult> {
  try {
    // Stage A: Vision facts
    const { facts } = await stageVision(request, config, logger, onUpdate);

    // Stage B: Attribute normalization
    const attributes = await stageAttributes(request, facts, config, logger, onUpdate);

    // Stage C: Draft generation
    await stageDraft(request, facts, attributes, config, logger, onUpdate);

    return { success: true };
  } catch (err) {
    logger.error({
      msg: 'Pipeline error',
      itemId: request.itemId,
      error: err instanceof Error ? err.message : String(err),
    });

    return {
      success: false,
      error: {
        code: 'PIPELINE_ERROR',
        message: err instanceof Error ? err.message : 'Unknown error',
        stage: 'FAILED',
        retryable: true,
      },
    };
  }
}
