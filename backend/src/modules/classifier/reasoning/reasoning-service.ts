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
    maxHypotheses: number,
    recentCorrections?: import('../types.js').RecentCorrection[]
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

    // 2. Call reasoning provider (OpenAI/Claude) with recent corrections for local learning
    const reasoningStart = Date.now();
    const hypotheses = await this.provider.generateHypotheses(
      context,
      this.domainPack,
      5,
      request.recentCorrections
    );
    const reasoningMs = Date.now() - reasoningStart;

    // 3. Enforce allowlist & ensure minimum hypotheses
    const { validHypotheses, hadBackfill } = this.enforceAllowlistAndBackfill(
      hypotheses,
      context
    );

    // 4. Sanitize explanations
    const sanitizedHypotheses = validHypotheses.map((h) => ({
      ...h,
      explanation: this.sanitizeExplanation(h.explanation),
    }));

    // 5. Rank & deduplicate
    const rankedHypotheses = this.rankHypotheses(sanitizedHypotheses);

    // 6. Calculate global confidence (from top hypothesis)
    const globalConfidence = rankedHypotheses[0]?.confidence ?? 0;

    // 7. Calculate top-2 confidence delta
    const top2Delta =
      rankedHypotheses.length >= 2
        ? rankedHypotheses[0].confidence - rankedHypotheses[1].confidence
        : 1.0; // If only 1 hypothesis, no ambiguity

    // 8. Determine refinement need
    const lowConfidence = globalConfidence < this.config.confidenceThreshold / 100;
    const ambiguousTop2 = top2Delta < 0.1;
    const needsRefinement = lowConfidence || hadBackfill || ambiguousTop2;

    let refinementReason: string | undefined;
    if (needsRefinement) {
      if (hadBackfill) {
        refinementReason = 'Insufficient valid hypotheses';
      } else if (ambiguousTop2) {
        refinementReason = 'Multiple equally likely options';
      } else {
        refinementReason = 'Low confidence';
      }
    }

    return {
      requestId: request.requestId,
      correlationId: request.correlationId,
      domainPackId: request.domainPackId,
      hypotheses: rankedHypotheses.slice(0, 5), // Return top 5
      globalConfidence: Math.round(globalConfidence * 100),
      needsRefinement,
      refinementReason,
      provider: this.config.provider,
      timingsMs: {
        total: Date.now() - startMs,
        perception: perceptionResponse.visionMs,
        reasoning: reasoningMs,
      },
    };
  }

  /**
   * Sanitize explanation text:
   * - Remove LLM hedging phrases
   * - Trim to max 120 characters
   * - Force observational tone
   */
  private sanitizeExplanation(explanation: string): string {
    const hedgingPhrases = [
      /appears to be\s*/gi,
      /seems like\s*/gi,
      /seems to be\s*/gi,
      /possibly\s*/gi,
      /might be\s*/gi,
      /could be\s*/gi,
      /likely\s*/gi,
      /probably\s*/gi,
      /may be\s*/gi,
      /I think\s*/gi,
      /I believe\s*/gi,
      /suggests that\s*/gi,
      /indicates that\s*/gi,
      /it looks like\s*/gi,
      /this looks like\s*/gi,
      /this appears to be\s*/gi,
      /this seems to be\s*/gi,
    ];

    let sanitized = explanation.trim();

    // Remove hedging phrases
    for (const phrase of hedgingPhrases) {
      sanitized = sanitized.replace(phrase, '');
    }

    // Clean up extra spaces and capitalize first letter
    sanitized = sanitized
      .replace(/\s+/g, ' ')
      .trim();

    if (sanitized.length > 0) {
      sanitized = sanitized.charAt(0).toUpperCase() + sanitized.slice(1);
    }

    // Truncate to 120 characters at sentence boundary if possible
    if (sanitized.length > 120) {
      const truncated = sanitized.substring(0, 120);
      const lastPeriod = truncated.lastIndexOf('.');
      const lastQuestion = truncated.lastIndexOf('?');
      const lastExclamation = truncated.lastIndexOf('!');
      const lastSentenceEnd = Math.max(lastPeriod, lastQuestion, lastExclamation);

      if (lastSentenceEnd > 60) {
        // If there's a sentence end in the latter half, use it
        sanitized = truncated.substring(0, lastSentenceEnd + 1);
      } else {
        // Otherwise, just truncate and add ellipsis
        sanitized = truncated.trim() + '...';
      }
    }

    return sanitized || 'Visual evidence suggests this category';
  }

  /**
   * Enforce allowlist of valid domain category IDs and backfill if needed.
   * Drops any hypotheses with category IDs not in the domain pack.
   * If fewer than 3 valid hypotheses remain, backfills from perception context.
   */
  private enforceAllowlistAndBackfill(
    hypotheses: ClassificationHypothesis[],
    context: PerceptionContext
  ): { validHypotheses: ClassificationHypothesis[]; hadBackfill: boolean } {
    const allowedIds = new Set(this.domainPack.categories.map((c) => c.id));

    // Filter to only allowed category IDs
    const validHypotheses = hypotheses.filter((h) =>
      allowedIds.has(h.domainCategoryId)
    );

    // If we have at least 3 valid hypotheses, we're done
    if (validHypotheses.length >= 3) {
      return { validHypotheses, hadBackfill: false };
    }

    // Otherwise, backfill from domain pack hints
    const existingIds = new Set(validHypotheses.map((h) => h.domainCategoryId));
    const backfillCandidates = context.domainPackHints.topCandidates.filter(
      (candidate) => !existingIds.has(candidate.category)
    );

    // Add backfill hypotheses until we have at least 3
    const needed = 3 - validHypotheses.length;
    for (let i = 0; i < needed && i < backfillCandidates.length; i++) {
      const candidate = backfillCandidates[i];
      const category = this.domainPack.categories.find((c) => c.id === candidate.category);

      validHypotheses.push({
        domainCategoryId: candidate.category,
        label: candidate.label,
        confidence: candidate.score * 0.8, // Reduce confidence for backfill
        confidenceBand: this.getConfidenceBand(candidate.score * 0.8),
        explanation: 'Based on visual analysis',
        attributes: category?.attributes ?? {},
      });
    }

    return {
      validHypotheses,
      hadBackfill: validHypotheses.length < 3 || needed > 0,
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

  /**
   * Get confidence band from numeric confidence.
   */
  private getConfidenceBand(confidence: number): 'HIGH' | 'MED' | 'LOW' {
    if (confidence >= 0.7) return 'HIGH';
    if (confidence >= 0.4) return 'MED';
    return 'LOW';
  }
}
