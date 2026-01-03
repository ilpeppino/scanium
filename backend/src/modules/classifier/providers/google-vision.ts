import { ImageAnnotatorClient, protos } from '@google-cloud/vision';
import { ClassificationRequest, ProviderResponse, VisionFeature } from '../types.js';
import {
  extractVisualFactsFromResponses,
  VisionResponseMappingConfig,
  VisionResponseMappingOptions,
} from '../../vision/response-mapper.js';

export type GoogleVisionOptions = {
  features: VisionFeature[];
  timeoutMs: number;
  maxRetries: number;
  enableVisualFacts: boolean;
  visualFactsConfig: VisionResponseMappingConfig;
  visualFactsLimits: Pick<
    VisionResponseMappingOptions,
    'maxOcrSnippets' | 'maxLabelHints' | 'maxLogoHints' | 'maxColors'
  >;
};

export class GoogleVisionClassifier {
  private readonly client: ImageAnnotatorClient;

  constructor(private readonly options: GoogleVisionOptions) {
    this.client = new ImageAnnotatorClient();
  }

  async classify(request: ClassificationRequest): Promise<ProviderResponse> {
    const started = performance.now();
    const featureSet = new Set(this.options.features);

    const annotateRequest: protos.google.cloud.vision.v1.IAnnotateImageRequest = {
      image: { content: request.buffer },
      features: this.buildFeatureRequests(featureSet),
    };

    const response = await this.callWithRetry(annotateRequest);

    const signals = this.parseSignals(response, featureSet);
    const visionMs = Math.round(performance.now() - started);
    const visualFacts = this.buildVisualFacts(request, response, featureSet, visionMs);

    return {
      provider: 'google-vision',
      signals,
      visionMs,
      visualFacts,
    };
  }

  private buildFeatureRequests(
    featureSet: Set<VisionFeature>
  ): protos.google.cloud.vision.v1.IFeature[] {
    const features: protos.google.cloud.vision.v1.IFeature[] = [];
    const ordered = [
      'OBJECT_LOCALIZATION',
      'LABEL_DETECTION',
      'TEXT_DETECTION',
      'DOCUMENT_TEXT_DETECTION',
      'LOGO_DETECTION',
      'IMAGE_PROPERTIES',
    ] as const;

    for (const feature of ordered) {
      if (!featureSet.has(feature)) continue;
      features.push(this.buildFeatureRequest(feature));
    }

    return features;
  }

  private buildFeatureRequest(
    feature: VisionFeature
  ): protos.google.cloud.vision.v1.IFeature {
    switch (feature) {
      case 'OBJECT_LOCALIZATION':
        return {
          type: protos.google.cloud.vision.v1.Feature.Type.OBJECT_LOCALIZATION,
          maxResults: 10,
        };
      case 'LABEL_DETECTION':
        return {
          type: protos.google.cloud.vision.v1.Feature.Type.LABEL_DETECTION,
          maxResults: 10,
        };
      case 'TEXT_DETECTION':
        return {
          type: protos.google.cloud.vision.v1.Feature.Type.TEXT_DETECTION,
          maxResults: 50,
        };
      case 'DOCUMENT_TEXT_DETECTION':
        return {
          type: protos.google.cloud.vision.v1.Feature.Type.DOCUMENT_TEXT_DETECTION,
          maxResults: 50,
        };
      case 'LOGO_DETECTION':
        return {
          type: protos.google.cloud.vision.v1.Feature.Type.LOGO_DETECTION,
          maxResults: 10,
        };
      case 'IMAGE_PROPERTIES':
        return {
          type: protos.google.cloud.vision.v1.Feature.Type.IMAGE_PROPERTIES,
        };
    }
  }

  private async callWithRetry(
    request: protos.google.cloud.vision.v1.IAnnotateImageRequest
  ): Promise<protos.google.cloud.vision.v1.IAnnotateImageResponse> {
    let lastError: unknown;
    for (let attempt = 0; attempt <= this.options.maxRetries; attempt++) {
      let timeoutRef: NodeJS.Timeout | undefined;
      try {
        const batchRequest: protos.google.cloud.vision.v1.IBatchAnnotateImagesRequest =
          { requests: [request] };
        const visionPromise = this.client.batchAnnotateImages(batchRequest);
        const [response] = (await Promise.race([
          visionPromise,
          new Promise((_, reject) => {
            timeoutRef = setTimeout(
              () => reject(new Error('Vision timeout')),
              this.options.timeoutMs
            );
          }),
        ])) as [protos.google.cloud.vision.v1.IBatchAnnotateImagesResponse];
        if (timeoutRef) {
          clearTimeout(timeoutRef);
        }
        return response.responses?.[0] ?? {};
      } catch (error) {
        lastError = error;
        if (attempt === this.options.maxRetries) {
          throw error;
        }
        const backoff = 200 * Math.pow(2, attempt);
        const jitter = 1 + Math.random() * 0.3;
        await new Promise((resolve) => setTimeout(resolve, backoff * jitter));
      } finally {
        if (timeoutRef) {
          clearTimeout(timeoutRef);
        }
      }
    }
    throw lastError ?? new Error('Unknown Vision error');
  }

  private parseSignals(
    response: protos.google.cloud.vision.v1.IAnnotateImageResponse,
    featureSet: Set<VisionFeature>
  ) {
    const labels: Array<{ description: string; score: number }> = [];

    if (featureSet.has('LABEL_DETECTION')) {
      labels.push(
        ...(response.labelAnnotations?.map((label) => ({
          description: label.description ?? 'unknown',
          score: label.score ?? 0,
        })) ?? [])
      );
    }

    if (featureSet.has('OBJECT_LOCALIZATION')) {
      labels.push(
        ...(response.localizedObjectAnnotations?.map((obj) => ({
          description: obj.name ?? 'unknown',
          score: obj.score ?? 0,
        })) ?? [])
      );
    }

    if (labels.length === 0) {
      return { labels: [] };
    }

    const byDescription = new Map<string, { description: string; score: number }>();
    for (const label of labels) {
      const key = label.description.toLowerCase();
      const existing = byDescription.get(key);
      if (!existing || label.score > existing.score) {
        byDescription.set(key, label);
      }
    }

    return { labels: Array.from(byDescription.values()) };
  }

  private buildVisualFacts(
    request: ClassificationRequest,
    response: protos.google.cloud.vision.v1.IAnnotateImageResponse,
    featureSet: Set<VisionFeature>,
    visionMs: number
  ): ProviderResponse['visualFacts'] {
    if (!this.options.enableVisualFacts) return undefined;

    const enableOcr =
      featureSet.has('TEXT_DETECTION') || featureSet.has('DOCUMENT_TEXT_DETECTION');
    const enableLabels = featureSet.has('LABEL_DETECTION');
    const enableLogos = featureSet.has('LOGO_DETECTION');
    const enableColors = featureSet.has('IMAGE_PROPERTIES');

    if (!enableOcr && !enableLabels && !enableLogos && !enableColors) {
      return undefined;
    }

    const ocrMode = featureSet.has('DOCUMENT_TEXT_DETECTION')
      ? 'DOCUMENT_TEXT_DETECTION'
      : 'TEXT_DETECTION';

    const extracted = extractVisualFactsFromResponses(
      [response],
      {
        enableOcr,
        enableLabels,
        enableLogos,
        enableColors,
        maxOcrSnippets: this.options.visualFactsLimits.maxOcrSnippets,
        maxLabelHints: this.options.visualFactsLimits.maxLabelHints,
        maxLogoHints: this.options.visualFactsLimits.maxLogoHints,
        maxColors: this.options.visualFactsLimits.maxColors,
        ocrMode,
      },
      this.options.visualFactsConfig
    );

    return {
      itemId: request.requestId,
      dominantColors: extracted.dominantColors,
      ocrSnippets: extracted.ocrSnippets,
      labelHints: extracted.labelHints,
      logoHints: extracted.logoHints.length > 0 ? extracted.logoHints : undefined,
      extractionMeta: {
        provider: 'google-vision',
        timingsMs: {
          total: visionMs,
          ocr: enableOcr ? visionMs : undefined,
          labels: enableLabels ? visionMs : undefined,
          logos: enableLogos ? visionMs : undefined,
          colors: enableColors ? visionMs : undefined,
        },
        imageCount: 1,
        imageHashes: [request.imageHash],
      },
    };
  }
}
