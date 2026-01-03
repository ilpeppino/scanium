import {
  ClassificationRequest,
  ClassificationResult,
  ProviderResponse,
  EnrichedAttributes,
  EnrichedAttribute,
} from './types.js';
import { GoogleVisionClassifier, GoogleVisionOptions } from './providers/google-vision.js';
import { MockClassifier, MockClassifierOptions } from './providers/mock-classifier.js';
import { DomainPack, loadDomainPack } from './domain/domain-pack.js';
import { mapSignalsToDomainCategory } from './domain/mapper.js';
import { Config } from '../../config/index.js';
import { CircuitBreaker } from '../../infra/resilience/circuit-breaker.js';
import { VisionExtractor, MockVisionExtractor } from '../vision/extractor.js';
import { resolveAttributes, ResolvedAttribute } from '../vision/attribute-resolver.js';
import { VisualFacts } from '../vision/types.js';

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
  private readonly visionExtractor: VisionExtractor | MockVisionExtractor;
  private readonly enableAttributeEnrichment: boolean;
  private readonly config: Config;

  constructor(deps: ClassifierDeps) {
    this.config = deps.config;
    this.mock = new MockClassifier({
      seed: deps.config.classifier.mockSeed,
    } satisfies MockClassifierOptions);

    this.enableAttributeEnrichment = deps.config.classifier.enableAttributeEnrichment;
    this.vision =
      deps.config.classifier.provider === 'google'
        ? new GoogleVisionClassifier({
            features: deps.config.classifier.visionFeature,
            timeoutMs: deps.config.classifier.visionTimeoutMs,
            maxRetries: deps.config.classifier.visionMaxRetries,
            enableVisualFacts: this.enableAttributeEnrichment,
            visualFactsConfig: {
              maxOcrSnippetLength: deps.config.vision.maxOcrSnippetLength,
              minOcrConfidence: deps.config.vision.minOcrConfidence,
              minLabelConfidence: deps.config.vision.minLabelConfidence,
              minLogoConfidence: deps.config.vision.minLogoConfidence,
            },
            visualFactsLimits: {
              maxOcrSnippets: deps.config.vision.maxOcrSnippets,
              maxLabelHints: deps.config.vision.maxLabelHints,
              maxLogoHints: deps.config.vision.maxLogoHints,
              maxColors: deps.config.vision.maxColors,
            },
          } satisfies GoogleVisionOptions)
        : null;

    this.domainPack = loadDomainPack(deps.config.classifier.domainPackPath);
    this.logger = deps.logger;
    this.visionBreaker = new CircuitBreaker({
      failureThreshold: deps.config.classifier.circuitBreakerFailureThreshold,
      cooldownMs: deps.config.classifier.circuitBreakerCooldownSeconds * 1000,
      minimumRequests: deps.config.classifier.circuitBreakerMinimumRequests,
    });

    // Initialize VisionExtractor for attribute enrichment
    this.visionExtractor =
      deps.config.vision.provider === 'google'
        ? new VisionExtractor({
            timeoutMs: deps.config.vision.timeoutMs,
            maxRetries: deps.config.vision.maxRetries,
            enableLogoDetection: deps.config.vision.enableLogos,
            maxOcrSnippetLength: deps.config.vision.maxOcrSnippetLength,
            minOcrConfidence: deps.config.vision.minOcrConfidence,
            minLabelConfidence: deps.config.vision.minLabelConfidence,
            minLogoConfidence: deps.config.vision.minLogoConfidence,
          })
        : new MockVisionExtractor();

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

    // Attribute enrichment via VisionExtractor
    let enrichedAttributes: EnrichedAttributes | undefined;
    let visualFacts: VisualFacts | undefined;
    let enrichmentMs: number | undefined;

    if (request.enrichAttributes && this.enableAttributeEnrichment) {
      const enrichmentStart = performance.now();
      try {
        const enrichmentResult = await this.extractEnrichedAttributes(
          request,
          providerResponse
        );
        enrichedAttributes = enrichmentResult?.attributes;
        visualFacts = enrichmentResult?.visualFacts;
        enrichmentMs = Math.round(performance.now() - enrichmentStart);

        // Structured logging for attribute extraction (matches observability requirements)
        // IMPORTANT: Never log OCR text content (may contain PII)
        const attributesExtracted = enrichedAttributes
          ? Object.keys(enrichedAttributes).filter(k => k !== 'suggestedNextPhoto')
          : [];
        const confidenceByAttribute: Record<string, string | null> = {};
        if (enrichedAttributes) {
          const attrMap: Record<string, typeof enrichedAttributes.brand> = {
            brand: enrichedAttributes.brand,
            model: enrichedAttributes.model,
            color: enrichedAttributes.color,
            secondaryColor: enrichedAttributes.secondaryColor,
            material: enrichedAttributes.material,
          };
          for (const [key, attr] of Object.entries(attrMap)) {
            confidenceByAttribute[`${key}Confidence`] = attr
              ? attr.confidenceScore >= 0.8 ? 'HIGH' : attr.confidenceScore >= 0.5 ? 'MED' : 'LOW'
              : null;
          }
        }

        this.logger?.info(
          {
            requestId: request.requestId,
            correlationId: request.correlationId,
            itemId: request.requestId, // requestId serves as item identifier
            attributesExtracted,
            ...confidenceByAttribute,
            visionLatencyMs: enrichmentMs,
            cacheHit: false,
            // Values logged only in debug mode to prevent PII exposure
            // attributeValues: enrichedAttributes (NOT LOGGED IN PRODUCTION)
          },
          'Attribute extraction completed'
        );
      } catch (error) {
        this.logger?.warn(
          {
            requestId: request.requestId,
            correlationId: request.correlationId,
            itemId: request.requestId,
            error: error instanceof Error ? error.message : 'Unknown error',
            visionLatencyMs: Math.round(performance.now() - enrichmentStart),
          },
          'Attribute extraction failed'
        );
        enrichmentMs = Math.round(performance.now() - enrichmentStart);
      }
    }

    return {
      requestId: request.requestId,
      correlationId: request.correlationId,
      domainPackId: request.domainPackId,
      domainCategoryId: mapping.domainCategoryId,
      confidence: mapping.confidence,
      label: mapping.label,
      attributes: mapping.attributes,
      enrichedAttributes,
      visualFacts,
      provider: providerResponse.provider,
      providerUnavailable,
      timingsMs: {
        total: Math.round(performance.now() - started),
        vision: providerResponse.visionMs,
        mapping: mapping.timingMs,
        enrichment: enrichmentMs,
      },
    };
  }

  /**
   * Extract enriched attributes using VisionExtractor and AttributeResolver.
   */
  private async extractEnrichedAttributes(
    request: ClassificationRequest,
    providerResponse: ProviderResponse
  ): Promise<{ attributes: EnrichedAttributes; visualFacts: VisualFacts } | undefined> {
    let visualFacts = providerResponse.visualFacts;

    if (!visualFacts) {
      const base64Data = request.buffer.toString('base64');
      const imageInput = {
        base64Data,
        mimeType: request.contentType as 'image/jpeg' | 'image/png',
        filename: request.fileName,
      };

      const extractionResult = await this.visionExtractor.extractVisualFacts(
        request.requestId,
        [imageInput],
        {
          enableOcr: this.config.vision.enableOcr,
          enableLabels: this.config.vision.enableLabels,
          enableLogos: this.config.vision.enableLogos,
          enableColors: this.config.vision.enableColors,
          maxOcrSnippets: this.config.vision.maxOcrSnippets,
          maxLabelHints: this.config.vision.maxLabelHints,
          maxLogoHints: this.config.vision.maxLogoHints,
          maxColors: this.config.vision.maxColors,
          ocrMode: this.config.vision.ocrMode,
        }
      );

      if (!extractionResult.success || !extractionResult.facts) {
        this.logger?.debug(
          {
            requestId: request.requestId,
            error: extractionResult.error,
            errorCode: extractionResult.errorCode,
          },
          'Vision extraction failed for attribute enrichment'
        );
        return undefined;
      }

      visualFacts = extractionResult.facts;
    }

    const resolved = resolveAttributes(request.requestId, visualFacts);

    return {
      attributes: this.mapResolvedToEnriched(resolved),
      visualFacts,
    };
  }

  /**
   * Map ResolvedAttributes to EnrichedAttributes type.
   */
  private mapResolvedToEnriched(
    resolved: ReturnType<typeof resolveAttributes>
  ): EnrichedAttributes {
    const result: EnrichedAttributes = {};

    if (resolved.brand) {
      result.brand = this.mapResolvedAttribute(resolved.brand);
    }
    if (resolved.model) {
      result.model = this.mapResolvedAttribute(resolved.model);
    }
    if (resolved.color) {
      result.color = this.mapResolvedAttribute(resolved.color);
    }
    if (resolved.secondaryColor) {
      result.secondaryColor = this.mapResolvedAttribute(resolved.secondaryColor);
    }
    if (resolved.material) {
      result.material = this.mapResolvedAttribute(resolved.material);
    }
    if (resolved.suggestedNextPhoto) {
      result.suggestedNextPhoto = resolved.suggestedNextPhoto;
    }

    return result;
  }

  /**
   * Map a single ResolvedAttribute to EnrichedAttribute.
   */
  private mapResolvedAttribute(attr: ResolvedAttribute): EnrichedAttribute {
    // Convert confidence tier to numeric score
    const confidenceScores: Record<string, number> = {
      HIGH: 0.9,
      MED: 0.65,
      LOW: 0.35,
    };

    return {
      value: attr.value,
      confidence: attr.confidence,
      confidenceScore: confidenceScores[attr.confidence] ?? 0.5,
      evidenceRefs: attr.evidenceRefs.map((ref) => ({
        type: ref.type,
        value: ref.value,
        score: ref.score,
      })),
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
