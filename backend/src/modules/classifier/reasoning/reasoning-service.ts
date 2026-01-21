import {
  ClassificationRequest,
  ClassificationHypothesis,
  MultiHypothesisResult,
  ProviderResponse,
} from '../types.js';
import { DomainPack } from '../domain/domain-pack.js';
import { buildPerceptionContext, PerceptionContext } from './perception-context.js';

/**
 * Interface for reasoning providers (OpenAI, Claude, Mock).
 */
export interface ReasoningProvider {
  generateHypotheses(
    perceptionSignals: PerceptionContext,
    domainPack: DomainPack,
    maxHypotheses: number
  ): Promise<ClassificationHypothesis[]>;
}

export interface ReasoningConfig {
  provider: 'openai' | 'claude' | 'mock';
  confidenceThreshold: number; // 70 = 70%
}

/**
 * Classification reasoning service.
 * Orchestrates multi-hypothesis generation from perception data.
 */
export class ClassificationReasoningService {
  constructor(
    private readonly provider: ReasoningProvider,
    private readonly domainPack: DomainPack,
    private readonly config: ReasoningConfig
  ) {}

  /**
   * Generate multi-hypothesis classification result.
   */
  async generateMultiHypothesis(
    request: ClassificationRequest,
    perceptionResponse: ProviderResponse
  ): Promise<MultiHypothesisResult> {
    const startMs = Date.now();

    // 1. Build perception context from Google Vision results
    const context = buildPerceptionContext(
      perceptionResponse,
      this.domainPack,
      perceptionResponse.visualFacts
    );

    // 2. Call reasoning provider (OpenAI/Claude)
    const reasoningStart = Date.now();
    const hypotheses = await this.provider.generateHypotheses(context, this.domainPack, 5);
    const reasoningMs = Date.now() - reasoningStart;

    // 3. Rank & deduplicate
    const rankedHypotheses = this.rankHypotheses(hypotheses);

    // 4. Calculate global confidence (from top hypothesis)
    const globalConfidence = rankedHypotheses[0]?.confidence ?? 0;

    // 5. Determine refinement need
    const needsRefinement = globalConfidence < this.config.confidenceThreshold / 100;

    return {
      requestId: request.requestId,
      correlationId: request.correlationId,
      domainPackId: request.domainPackId,
      hypotheses: rankedHypotheses.slice(0, 5), // Return top 5
      globalConfidence: Math.round(globalConfidence * 100),
      needsRefinement,
      refinementReason: needsRefinement ? 'Low confidence' : undefined,
      provider: this.config.provider,
      timingsMs: {
        total: Date.now() - startMs,
        perception: perceptionResponse.visionMs,
        reasoning: reasoningMs,
      },
    };
  }

  /**
   * Rank hypotheses by confidence and deduplicate same categories.
   */
  private rankHypotheses(
    hypotheses: ClassificationHypothesis[]
  ): ClassificationHypothesis[] {
    // Sort by confidence descending, deduplicate same categories
    const seen = new Set<string>();
    return hypotheses
      .sort((a, b) => b.confidence - a.confidence)
      .filter((h) => {
        if (seen.has(h.domainCategoryId)) return false;
        seen.add(h.domainCategoryId);
        return true;
      });
  }
}
