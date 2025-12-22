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
import { CircuitBreaker } from '../../infra/resilience/circuit-breaker.js';

type ClassifierDeps = {
  config: Config;
  logger?: {
    info: (payload: Record<string, unknown>, message: string) => void;
    warn: (payload: Record<string, unknown>, message: string) => void;
    debug: (payload: Record<string, unknown>, message: string) => void;
  };
};

const SIGNAL_LOG_LIMIT = 5;

export class ClassifierService {
  private readonly mock: MockClassifier;
  private readonly vision: GoogleVisionClassifier | null;
  private readonly domainPack: DomainPack;
  private readonly visionBreaker: CircuitBreaker;
  private readonly logger?: ClassifierDeps['logger'];

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
    this.logger = deps.logger;
    this.visionBreaker = new CircuitBreaker({
      failureThreshold: deps.config.classifier.circuitBreakerFailureThreshold,
      cooldownMs: deps.config.classifier.circuitBreakerCooldownSeconds * 1000,
      minimumRequests: deps.config.classifier.circuitBreakerMinimumRequests,
    });

    if (this.domainPack.id !== deps.config.classifier.domainPackId) {
      console.warn(
        `[ClassifierService] domain pack id mismatch: config=${deps.config.classifier.domainPackId}, pack=${this.domainPack.id}`
      );
    }
  }

  async classify(request: ClassificationRequest): Promise<ClassificationResult> {
    const started = performance.now();
    const { providerResponse, providerUnavailable } = await this.runProvider(request);
    const mapping = mapSignalsToDomainCategory(this.domainPack, providerResponse.signals);
    this.logSignals(request.requestId, request.correlationId, providerResponse, mapping);

    return {
      requestId: request.requestId,
      correlationId: request.correlationId,
      domainPackId: request.domainPackId,
      domainCategoryId: mapping.domainCategoryId,
      confidence: mapping.confidence,
      label: mapping.label,
      attributes: mapping.attributes,
      provider: providerResponse.provider,
      providerUnavailable,
      timingsMs: {
        total: Math.round(performance.now() - started),
        vision: providerResponse.visionMs,
        mapping: mapping.timingMs,
      },
    };
  }

  private async runProvider(
    request: ClassificationRequest
  ): Promise<{ providerResponse: ProviderResponse; providerUnavailable: boolean }> {
    const breakerState = this.visionBreaker.getState();
    if (this.vision && !this.visionBreaker.canRequest()) {
      this.logger?.warn(
        { requestId: request.requestId, correlationId: request.correlationId, breakerState },
        'Vision provider circuit open; using mock'
      );
      return { providerResponse: await this.mock.classify(request), providerUnavailable: true };
    }

    if (this.vision) {
      try {
        const response = await this.vision.classify(request);
        this.visionBreaker.recordSuccess();
        return { providerResponse: response, providerUnavailable: false };
      } catch (error) {
        this.visionBreaker.recordFailure();
        // Fallback to mock if Vision is unavailable to keep mobile builds/tests unblocked
        this.logger?.warn(
          { requestId: request.requestId, correlationId: request.correlationId, error },
          'Vision provider failed; falling back to mock'
        );
      }
    }

    return { providerResponse: await this.mock.classify(request), providerUnavailable: true };
  }

  private logSignals(
    requestId: string,
    correlationId: string,
    providerResponse: ProviderResponse,
    mapping: ReturnType<typeof mapSignalsToDomainCategory>
  ) {
    const topSignals = [...(providerResponse.signals.labels ?? [])]
      .sort((a, b) => (b.score ?? 0) - (a.score ?? 0))
      .slice(0, SIGNAL_LOG_LIMIT)
      .map((signal) => ({
        description: signal.description,
        score: Number((signal.score ?? 0).toFixed(3)),
      }));

    this.logger?.debug(
      {
        requestId,
        correlationId,
        provider: providerResponse.provider,
        topSignals,
      },
      'Classifier signals'
    );

    this.logger?.debug(
      {
        requestId,
        correlationId,
        domainCategoryId: mapping.domainCategoryId ?? null,
        label: mapping.label ?? null,
        confidence: mapping.confidence ?? null,
        reason: mapping.debug.reason,
      },
      'Classifier mapping'
    );
  }
}
