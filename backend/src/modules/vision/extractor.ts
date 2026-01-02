/**
 * Vision Extractor Service
 *
 * Extracts structured VisualFacts from item images using:
 * - Google Cloud Vision API (OCR, labels, logos)
 * - Server-side color extraction (no ML)
 */

import { ImageAnnotatorClient, protos } from '@google-cloud/vision';
import { createHash } from 'node:crypto';
import {
  VisualFacts,
  VisionImageInput,
  VisionExtractorOptions,
  VisionExtractionResult,
  OcrSnippet,
  LabelHint,
  LogoHint,
  DominantColor,
} from './types.js';
import { extractDominantColors } from './color-extractor.js';

export type VisionExtractorConfig = {
  /** Timeout for Vision API calls in ms */
  timeoutMs: number;
  /** Maximum retries for Vision API calls */
  maxRetries: number;
  /** Enable logo detection (additional cost) */
  enableLogoDetection: boolean;
  /** Maximum characters per OCR snippet */
  maxOcrSnippetLength: number;
  /** Minimum confidence for OCR text */
  minOcrConfidence: number;
  /** Minimum confidence for labels */
  minLabelConfidence: number;
  /** Minimum confidence for logos */
  minLogoConfidence: number;
};

const DEFAULT_CONFIG: VisionExtractorConfig = {
  timeoutMs: 10000,
  maxRetries: 2,
  enableLogoDetection: true,
  maxOcrSnippetLength: 100,
  minOcrConfidence: 0.5,
  minLabelConfidence: 0.5,
  minLogoConfidence: 0.5,
};

const DEFAULT_OPTIONS: Required<VisionExtractorOptions> = {
  enableOcr: true,
  enableLabels: true,
  enableLogos: true,
  enableColors: true,
  maxOcrSnippets: 10,
  maxLabelHints: 10,
  maxLogoHints: 5,
  maxColors: 5,
};

/**
 * Compute SHA-256 hash of image data for caching.
 */
export function computeImageHash(base64Data: string): string {
  return createHash('sha256').update(base64Data).digest('hex').slice(0, 16);
}

/**
 * Truncate and normalize OCR text.
 * Removes excessive whitespace and limits length.
 */
function normalizeOcrText(text: string, maxLength: number): string {
  return text
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, maxLength);
}

/**
 * Extract high-signal text snippets from OCR response.
 * Filters out noise and keeps only meaningful text.
 */
function extractOcrSnippets(
  textAnnotations: protos.google.cloud.vision.v1.IEntityAnnotation[] | null | undefined,
  options: { maxSnippets: number; maxLength: number; minConfidence: number }
): OcrSnippet[] {
  if (!textAnnotations || textAnnotations.length === 0) {
    return [];
  }

  // First annotation is full text, rest are individual words/phrases
  const snippets: OcrSnippet[] = [];

  // Skip the first annotation (full text) and process individual detections
  for (let i = 1; i < textAnnotations.length && snippets.length < options.maxSnippets; i++) {
    const annotation = textAnnotations[i];
    const text = annotation.description?.trim();

    if (!text || text.length < 2) continue;

    // Skip very short or numeric-only text (likely noise)
    if (text.length < 3 && /^\d+$/.test(text)) continue;

    const confidence = annotation.score ?? 0.8; // Default high confidence for text detection
    if (confidence < options.minConfidence) continue;

    const normalizedText = normalizeOcrText(text, options.maxLength);
    if (normalizedText.length >= 2) {
      snippets.push({
        text: normalizedText,
        confidence: Math.round(confidence * 100) / 100,
      });
    }
  }

  // Also extract from full text if individual snippets are sparse
  if (snippets.length < 3 && textAnnotations[0]?.description) {
    const fullText = textAnnotations[0].description;
    // Split by lines and extract meaningful lines
    const lines = fullText.split('\n')
      .map((line) => normalizeOcrText(line, options.maxLength))
      .filter((line) => line.length >= 3);

    for (const line of lines) {
      if (snippets.length >= options.maxSnippets) break;
      // Avoid duplicates
      if (!snippets.some((s) => s.text.includes(line) || line.includes(s.text))) {
        snippets.push({ text: line, confidence: 0.8 });
      }
    }
  }

  return snippets.slice(0, options.maxSnippets);
}

/**
 * Extract label hints from Vision API response.
 */
function extractLabelHints(
  labelAnnotations: protos.google.cloud.vision.v1.IEntityAnnotation[] | null | undefined,
  options: { maxLabels: number; minConfidence: number }
): LabelHint[] {
  if (!labelAnnotations) return [];

  return labelAnnotations
    .filter((label) => (label.score ?? 0) >= options.minConfidence)
    .slice(0, options.maxLabels)
    .map((label) => ({
      label: label.description ?? 'unknown',
      score: Math.round((label.score ?? 0) * 100) / 100,
    }));
}

/**
 * Extract logo hints from Vision API response.
 */
function extractLogoHints(
  logoAnnotations: protos.google.cloud.vision.v1.IEntityAnnotation[] | null | undefined,
  options: { maxLogos: number; minConfidence: number }
): LogoHint[] {
  if (!logoAnnotations) return [];

  return logoAnnotations
    .filter((logo) => (logo.score ?? 0) >= options.minConfidence)
    .slice(0, options.maxLogos)
    .map((logo) => ({
      brand: logo.description ?? 'unknown',
      score: Math.round((logo.score ?? 0) * 100) / 100,
    }));
}

export class VisionExtractor {
  private readonly client: ImageAnnotatorClient;
  private readonly config: VisionExtractorConfig;

  constructor(config: Partial<VisionExtractorConfig> = {}) {
    this.config = { ...DEFAULT_CONFIG, ...config };
    this.client = new ImageAnnotatorClient();
  }

  /**
   * Extract visual facts from a set of images for an item.
   */
  async extractVisualFacts(
    itemId: string,
    images: VisionImageInput[],
    options: VisionExtractorOptions = {}
  ): Promise<VisionExtractionResult> {
    const opts = { ...DEFAULT_OPTIONS, ...options };
    const startTime = performance.now();

    if (images.length === 0) {
      return {
        success: false,
        error: 'No images provided',
        errorCode: 'INVALID_IMAGE',
      };
    }

    const imageHashes = images.map((img) => computeImageHash(img.base64Data));
    const timings: {
      total: number;
      ocr?: number;
      labels?: number;
      logos?: number;
      colors?: number;
    } = { total: 0 };

    try {
      // Build Vision API request with all features
      const features: protos.google.cloud.vision.v1.IFeature[] = [];

      if (opts.enableOcr) {
        features.push({
          type: protos.google.cloud.vision.v1.Feature.Type.TEXT_DETECTION,
          maxResults: 50,
        });
      }

      if (opts.enableLabels) {
        features.push({
          type: protos.google.cloud.vision.v1.Feature.Type.LABEL_DETECTION,
          maxResults: opts.maxLabelHints,
        });
      }

      if (opts.enableLogos && this.config.enableLogoDetection) {
        features.push({
          type: protos.google.cloud.vision.v1.Feature.Type.LOGO_DETECTION,
          maxResults: opts.maxLogoHints,
        });
      }

      // Process all images in parallel
      const visionRequests: protos.google.cloud.vision.v1.IAnnotateImageRequest[] =
        images.map((img) => ({
          image: { content: img.base64Data },
          features,
        }));

      // Call Vision API with timeout and retry
      const visionStart = performance.now();
      const visionResponses = await this.callWithRetry(visionRequests);
      const visionTime = Math.round(performance.now() - visionStart);

      // Aggregate results from all images
      const allOcrSnippets: OcrSnippet[] = [];
      const allLabelHints: LabelHint[] = [];
      const allLogoHints: LogoHint[] = [];
      const allColors: DominantColor[] = [];

      // Process Vision API responses
      for (const response of visionResponses) {
        if (opts.enableOcr) {
          const snippets = extractOcrSnippets(response.textAnnotations, {
            maxSnippets: opts.maxOcrSnippets,
            maxLength: this.config.maxOcrSnippetLength,
            minConfidence: this.config.minOcrConfidence,
          });
          allOcrSnippets.push(...snippets);
        }

        if (opts.enableLabels) {
          const labels = extractLabelHints(response.labelAnnotations, {
            maxLabels: opts.maxLabelHints,
            minConfidence: this.config.minLabelConfidence,
          });
          allLabelHints.push(...labels);
        }

        if (opts.enableLogos) {
          const logos = extractLogoHints(response.logoAnnotations, {
            maxLogos: opts.maxLogoHints,
            minConfidence: this.config.minLogoConfidence,
          });
          allLogoHints.push(...logos);
        }
      }

      timings.ocr = opts.enableOcr ? visionTime : undefined;
      timings.labels = opts.enableLabels ? visionTime : undefined;
      timings.logos = opts.enableLogos ? visionTime : undefined;

      // Extract colors in parallel (server-side)
      if (opts.enableColors) {
        const colorStart = performance.now();
        const colorResults = await Promise.all(
          images.map((img) => {
            const buffer = Buffer.from(img.base64Data, 'base64');
            return extractDominantColors(buffer, { numColors: opts.maxColors });
          })
        );

        // Merge color results from all images
        for (const result of colorResults) {
          allColors.push(...result.colors);
        }
        timings.colors = Math.round(performance.now() - colorStart);
      }

      // Deduplicate and sort results
      const uniqueOcrSnippets = deduplicateSnippets(allOcrSnippets, opts.maxOcrSnippets);
      const uniqueLabels = deduplicateLabels(allLabelHints, opts.maxLabelHints);
      const uniqueLogos = deduplicateLogos(allLogoHints, opts.maxLogoHints);
      const mergedColors = mergeColors(allColors, opts.maxColors);

      timings.total = Math.round(performance.now() - startTime);

      const facts: VisualFacts = {
        itemId,
        dominantColors: mergedColors,
        ocrSnippets: uniqueOcrSnippets,
        labelHints: uniqueLabels,
        logoHints: uniqueLogos.length > 0 ? uniqueLogos : undefined,
        extractionMeta: {
          provider: 'google-vision',
          timingsMs: timings,
          imageCount: images.length,
          imageHashes,
        },
      };

      return { success: true, facts };
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Unknown error';

      if (errorMessage.includes('timeout')) {
        return {
          success: false,
          error: 'Vision extraction timed out',
          errorCode: 'TIMEOUT',
        };
      }

      if (errorMessage.includes('quota') || errorMessage.includes('RESOURCE_EXHAUSTED')) {
        return {
          success: false,
          error: 'Vision API quota exceeded',
          errorCode: 'QUOTA_EXCEEDED',
        };
      }

      return {
        success: false,
        error: 'Vision extraction failed',
        errorCode: 'VISION_UNAVAILABLE',
      };
    }
  }

  /**
   * Call Vision API with timeout and retry logic.
   */
  private async callWithRetry(
    requests: protos.google.cloud.vision.v1.IAnnotateImageRequest[]
  ): Promise<protos.google.cloud.vision.v1.IAnnotateImageResponse[]> {
    let lastError: unknown;

    for (let attempt = 0; attempt <= this.config.maxRetries; attempt++) {
      let timeoutRef: NodeJS.Timeout | undefined;

      try {
        const batchRequest: protos.google.cloud.vision.v1.IBatchAnnotateImagesRequest = {
          requests,
        };

        const visionPromise = this.client.batchAnnotateImages(batchRequest);
        const [response] = (await Promise.race([
          visionPromise,
          new Promise<never>((_, reject) => {
            timeoutRef = setTimeout(
              () => reject(new Error('Vision timeout')),
              this.config.timeoutMs
            );
          }),
        ])) as [protos.google.cloud.vision.v1.IBatchAnnotateImagesResponse, ...unknown[]];

        if (timeoutRef) clearTimeout(timeoutRef);

        return response.responses ?? [];
      } catch (error) {
        lastError = error;
        if (timeoutRef) clearTimeout(timeoutRef);

        if (attempt === this.config.maxRetries) {
          throw error;
        }

        const backoff = 200 * Math.pow(2, attempt);
        const jitter = 1 + Math.random() * 0.3;
        await new Promise((resolve) => setTimeout(resolve, backoff * jitter));
      }
    }

    throw lastError ?? new Error('Unknown Vision error');
  }
}

/**
 * Mock Vision Extractor for testing.
 */
export class MockVisionExtractor {
  async extractVisualFacts(
    itemId: string,
    images: VisionImageInput[],
    _options: VisionExtractorOptions = {}
  ): Promise<VisionExtractionResult> {
    const imageHashes = images.map((img) => computeImageHash(img.base64Data));

    // Return mock visual facts
    const facts: VisualFacts = {
      itemId,
      dominantColors: [
        { name: 'blue', rgbHex: '#1E40AF', pct: 45 },
        { name: 'white', rgbHex: '#FFFFFF', pct: 30 },
        { name: 'gray', rgbHex: '#6B7280', pct: 25 },
      ],
      ocrSnippets: [
        { text: 'IKEA', confidence: 0.95 },
        { text: 'KALLAX', confidence: 0.88 },
        { text: 'Made in Sweden', confidence: 0.75 },
      ],
      labelHints: [
        { label: 'Furniture', score: 0.92 },
        { label: 'Shelf', score: 0.88 },
        { label: 'Storage', score: 0.75 },
      ],
      logoHints: [{ brand: 'IKEA', score: 0.85 }],
      extractionMeta: {
        provider: 'mock',
        timingsMs: { total: 50, ocr: 20, labels: 15, logos: 10, colors: 5 },
        imageCount: images.length,
        imageHashes,
      },
    };

    return { success: true, facts };
  }
}

/**
 * Deduplicate OCR snippets by text similarity.
 */
function deduplicateSnippets(snippets: OcrSnippet[], maxCount: number): OcrSnippet[] {
  const unique: OcrSnippet[] = [];

  for (const snippet of snippets) {
    const isDuplicate = unique.some(
      (u) =>
        u.text.toLowerCase() === snippet.text.toLowerCase() ||
        u.text.toLowerCase().includes(snippet.text.toLowerCase()) ||
        snippet.text.toLowerCase().includes(u.text.toLowerCase())
    );

    if (!isDuplicate) {
      unique.push(snippet);
    }
  }

  // Sort by confidence (descending)
  return unique
    .sort((a, b) => (b.confidence ?? 0) - (a.confidence ?? 0))
    .slice(0, maxCount);
}

/**
 * Deduplicate labels by name.
 */
function deduplicateLabels(labels: LabelHint[], maxCount: number): LabelHint[] {
  const seen = new Map<string, LabelHint>();

  for (const label of labels) {
    const key = label.label.toLowerCase();
    const existing = seen.get(key);
    if (!existing || label.score > existing.score) {
      seen.set(key, label);
    }
  }

  return Array.from(seen.values())
    .sort((a, b) => b.score - a.score)
    .slice(0, maxCount);
}

/**
 * Deduplicate logos by brand name.
 */
function deduplicateLogos(logos: LogoHint[], maxCount: number): LogoHint[] {
  const seen = new Map<string, LogoHint>();

  for (const logo of logos) {
    const key = logo.brand.toLowerCase();
    const existing = seen.get(key);
    if (!existing || logo.score > existing.score) {
      seen.set(key, logo);
    }
  }

  return Array.from(seen.values())
    .sort((a, b) => b.score - a.score)
    .slice(0, maxCount);
}

/**
 * Merge colors from multiple images by color name.
 */
function mergeColors(colors: DominantColor[], maxCount: number): DominantColor[] {
  const byName = new Map<string, DominantColor & { totalPct: number; count: number }>();

  for (const color of colors) {
    const existing = byName.get(color.name);
    if (existing) {
      existing.totalPct += color.pct;
      existing.count++;
      // Keep the most representative hex value (from highest pct image)
      if (color.pct > existing.pct) {
        existing.rgbHex = color.rgbHex;
      }
    } else {
      byName.set(color.name, { ...color, totalPct: color.pct, count: 1 });
    }
  }

  return Array.from(byName.values())
    .map((c) => ({
      name: c.name,
      rgbHex: c.rgbHex,
      pct: Math.round(c.totalPct / c.count),
    }))
    .sort((a, b) => b.pct - a.pct)
    .slice(0, maxCount);
}
