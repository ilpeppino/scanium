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
} from './types.js';
import { extractDominantColors } from './color-extractor.js';
import {
  extractVisualFactsFromResponses,
  mergeColors,
} from './response-mapper.js';

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
  ocrMode: 'TEXT_DETECTION',
};

/**
 * Compute SHA-256 hash of image data for caching.
 */
export function computeImageHash(base64Data: string): string {
  return createHash('sha256').update(base64Data).digest('hex').slice(0, 16);
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
          type:
            opts.ocrMode === 'DOCUMENT_TEXT_DETECTION'
              ? protos.google.cloud.vision.v1.Feature.Type.DOCUMENT_TEXT_DETECTION
              : protos.google.cloud.vision.v1.Feature.Type.TEXT_DETECTION,
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

      if (opts.enableColors) {
        features.push({
          type: protos.google.cloud.vision.v1.Feature.Type.IMAGE_PROPERTIES,
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

      const extracted = extractVisualFactsFromResponses(
        visionResponses,
        {
          enableOcr: opts.enableOcr,
          enableLabels: opts.enableLabels,
          enableLogos: opts.enableLogos,
          enableColors: opts.enableColors,
          maxOcrSnippets: opts.maxOcrSnippets,
          maxLabelHints: opts.maxLabelHints,
          maxLogoHints: opts.maxLogoHints,
          maxColors: opts.maxColors,
          ocrMode: opts.ocrMode,
        },
        {
          maxOcrSnippetLength: this.config.maxOcrSnippetLength,
          minOcrConfidence: this.config.minOcrConfidence,
          minLabelConfidence: this.config.minLabelConfidence,
          minLogoConfidence: this.config.minLogoConfidence,
        }
      );

      timings.ocr = opts.enableOcr ? visionTime : undefined;
      timings.labels = opts.enableLabels ? visionTime : undefined;
      timings.logos = opts.enableLogos ? visionTime : undefined;
      timings.colors = opts.enableColors ? visionTime : undefined;

      let dominantColors = extracted.dominantColors;

      if (opts.enableColors && dominantColors.length === 0) {
        const colorStart = performance.now();
        const colorResults = await Promise.all(
          images.map((img) => {
            const buffer = Buffer.from(img.base64Data, 'base64');
            return extractDominantColors(buffer, { numColors: opts.maxColors });
          })
        );

        const allColors = colorResults.flatMap((result) => result.colors);
        dominantColors = mergeColors(allColors, opts.maxColors);
        timings.colors = Math.round(performance.now() - colorStart);
      }

      timings.total = Math.round(performance.now() - startTime);

      const facts: VisualFacts = {
        itemId,
        dominantColors,
        ocrSnippets: extracted.ocrSnippets,
        labelHints: extracted.labelHints,
        logoHints: extracted.logoHints.length > 0 ? extracted.logoHints : undefined,
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
