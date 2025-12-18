import {
  ClassificationRequest,
  ClassificationResult,
  ProviderResponse,
} from './types.js';
import { GoogleVisionClassifier, GoogleVisionOptions } from './providers/google-vision.js';
import { MockClassifier, MockClassifierOptions } from './providers/mock-classifier.js';
import { DomainPack, loadDomainPack } from './domain/domain-pack.js';
import { mapSignalsToDomainCategory } from './domain/mapper.js';
import { Config } from '../../config/index.js';

type ClassifierDeps = {
  config: Config;
};

export class ClassifierService {
  private readonly mock: MockClassifier;
  private readonly vision: GoogleVisionClassifier | null;
  private readonly domainPack: DomainPack;

  constructor(deps: ClassifierDeps) {
    this.mock = new MockClassifier({
      seed: deps.config.classifier.mockSeed,
    } satisfies MockClassifierOptions);

    this.vision =
      deps.config.classifier.provider === 'google'
        ? new GoogleVisionClassifier({
            feature: deps.config.classifier.visionFeature,
            timeoutMs: deps.config.classifier.visionTimeoutMs,
            maxRetries: deps.config.classifier.visionMaxRetries,
          } satisfies GoogleVisionOptions)
        : null;

    this.domainPack = loadDomainPack(deps.config.classifier.domainPackPath);

    if (this.domainPack.id !== deps.config.classifier.domainPackId) {
      console.warn(
        `[ClassifierService] domain pack id mismatch: config=${deps.config.classifier.domainPackId}, pack=${this.domainPack.id}`
      );
    }
  }

  async classify(request: ClassificationRequest): Promise<ClassificationResult> {
    const started = performance.now();
    const providerResponse = await this.runProvider(request);
    const mapping = mapSignalsToDomainCategory(this.domainPack, providerResponse.signals);

    return {
      requestId: request.requestId,
      domainPackId: request.domainPackId,
      domainCategoryId: mapping.domainCategoryId,
      confidence: mapping.confidence,
      attributes: mapping.attributes,
      provider: providerResponse.provider,
      timingsMs: {
        total: Math.round(performance.now() - started),
        vision: providerResponse.visionMs,
        mapping: mapping.timingMs,
      },
    };
  }

  private async runProvider(request: ClassificationRequest): Promise<ProviderResponse> {
    if (this.vision) {
      try {
        return await this.vision.classify(request);
      } catch (error) {
        // Fallback to mock if Vision is unavailable to keep mobile builds/tests unblocked
        console.warn(
          `[ClassifierService] Vision provider failed, falling back to mock:`,
          error
        );
      }
    }

    return this.mock.classify(request);
  }
}
