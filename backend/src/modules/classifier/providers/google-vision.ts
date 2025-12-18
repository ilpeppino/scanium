import { ImageAnnotatorClient, protos } from '@google-cloud/vision';
import { ClassificationRequest, ProviderResponse, VisionFeature } from '../types.js';

export type GoogleVisionOptions = {
  feature: VisionFeature;
  timeoutMs: number;
  maxRetries: number;
};

export class GoogleVisionClassifier {
  private readonly client: ImageAnnotatorClient;

  constructor(private readonly options: GoogleVisionOptions) {
    this.client = new ImageAnnotatorClient();
  }

  async classify(request: ClassificationRequest): Promise<ProviderResponse> {
    const started = performance.now();
    const feature = this.options.feature;

    const annotateRequest: protos.google.cloud.vision.v1.IAnnotateImageRequest = {
      image: { content: request.buffer },
      features: [
        {
          type:
            feature === 'OBJECT_LOCALIZATION'
              ? protos.google.cloud.vision.v1.Feature.Type.OBJECT_LOCALIZATION
              : protos.google.cloud.vision.v1.Feature.Type.LABEL_DETECTION,
          maxResults: 10,
        },
      ],
    };

    const response = await this.callWithRetry(annotateRequest);

    const signals =
      feature === 'OBJECT_LOCALIZATION'
        ? this.parseObjects(response)
        : this.parseLabels(response);

    return {
      provider: 'google-vision',
      signals,
      visionMs: Math.round(performance.now() - started),
    };
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
        await new Promise((resolve) => setTimeout(resolve, backoff));
      } finally {
        if (timeoutRef) {
          clearTimeout(timeoutRef);
        }
      }
    }
    throw lastError ?? new Error('Unknown Vision error');
  }

  private parseLabels(
    response: protos.google.cloud.vision.v1.IAnnotateImageResponse
  ) {
    const labels =
      response.labelAnnotations?.map((label) => ({
        description: label.description ?? 'unknown',
        score: label.score ?? 0,
      })) ?? [];

    return { labels };
  }

  private parseObjects(
    response: protos.google.cloud.vision.v1.IAnnotateImageResponse
  ) {
    const labels =
      response.localizedObjectAnnotations?.map((obj) => ({
        description: obj.name ?? 'unknown',
        score: obj.score ?? 0,
      })) ?? [];

    return { labels };
  }
}
