import OpenAI from 'openai';
import { ClassificationHypothesis } from '../types.js';
import { PerceptionContext, buildReasoningUserPrompt } from './perception-context.js';
import { DomainPack } from '../domain/domain-pack.js';
import { ReasoningProvider } from './reasoning-service.js';

export interface OpenAIReasoningConfig {
  apiKey: string;
  model: string;
  maxTokens: number;
  timeoutMs: number;
}

/**
 * System prompt with sellability bias for classification.
 */
const CLASSIFICATION_SYSTEM_PROMPT = `You are an expert product classifier for a resale marketplace app.

Your goal: Help users sell items faster by providing smart, useful category suggestions.

CRITICAL INSTRUCTIONS:

1. SELLABILITY BIAS (highest priority):
   - Strongly favor common, sellable household categories
   - Avoid obscure technical terms on first classification
   - Examples:
     • "Kitchen appliance" NOT "Sous-vide thermal immersion circulator"
     • "Gaming console" NOT "AMD RDNA 3 graphics architecture"
     • "Robot vacuum" NOT "LiDAR SLAM autonomous navigation system"

2. HYPOTHESIS GENERATION:
   - Provide 3-5 ranked hypotheses (most likely first)
   - Each hypothesis must be actionable for listing creation
   - Use category IDs from the provided domain pack

3. CONFIDENCE SCORING (0-1 scale):
   - HIGH (≥0.70): Clear, unambiguous visual evidence
   - MED (0.40-0.69): Plausible but needs user confirmation
   - LOW (<0.40): Multiple equally likely interpretations

4. EXPLANATIONS:
   - Keep to 1-2 sentences per hypothesis
   - Cite specific visual evidence (shape, brand, text, colors)
   - Use plain language, not technical jargon
   - Examples:
     • "Round shape with floor sensors visible"
     • "Nike swoosh logo detected on shoe"
     • "Rectangular appliance with control panel"

5. NEVER RETURN:
   - Joke categories or sarcasm
   - Overly generic categories like "product" or "object"
   - Multiple hypotheses that mean the same thing
   - Hypotheses that aren't in the provided category list

OUTPUT FORMAT (JSON only):
{
  "hypotheses": [
    {
      "categoryId": "robot-vacuum",
      "categoryName": "Robot Vacuum",
      "confidence": 0.85,
      "explanation": "Round shape with floor sensors visible"
    }
  ],
  "globalConfidence": 85
}`;

/**
 * OpenAI-based reasoning provider for classification hypothesis generation.
 */
export class OpenAIReasoningProvider implements ReasoningProvider {
  private readonly client: OpenAI;
  private readonly model: string;
  private readonly maxTokens: number;

  constructor(config: OpenAIReasoningConfig) {
    this.client = new OpenAI({
      apiKey: config.apiKey,
      timeout: config.timeoutMs,
    });
    this.model = config.model;
    this.maxTokens = config.maxTokens;
  }

  async generateHypotheses(
    perception: PerceptionContext,
    domainPack: DomainPack,
    _maxHypotheses: number = 5,
    recentCorrections?: import('../types.js').RecentCorrection[]
  ): Promise<ClassificationHypothesis[]> {
    const userPrompt = buildReasoningUserPrompt(perception, domainPack);

    const messages: Array<{ role: 'system' | 'user' | 'assistant'; content: string }> = [
      { role: 'system', content: CLASSIFICATION_SYSTEM_PROMPT },
      { role: 'user', content: userPrompt },
    ];

    // Add correction history for local learning overlay
    if (recentCorrections && recentCorrections.length > 0) {
      const correctionContext = this.buildCorrectionContext(recentCorrections, domainPack);
      messages.splice(1, 0, {
        role: 'system',
        content: correctionContext,
      });
    }

    try {
      const response = await this.client.chat.completions.create({
        model: this.model,
        messages,
        response_format: { type: 'json_object' },
        max_tokens: this.maxTokens,
        temperature: 0.3, // Lower temperature for more consistent classification
      });

      const content = response.choices[0]?.message?.content;
      if (!content) {
        throw new Error('No content in OpenAI response');
      }

      const parsed = JSON.parse(content);
      return this.parseHypotheses(parsed, domainPack);
    } catch (error) {
      // Log error and return fallback
      console.error('OpenAI reasoning error:', error);
      return this.getFallbackHypotheses(perception, domainPack);
    }
  }

  private parseHypotheses(
    llmResponse: any,
    domainPack: DomainPack
  ): ClassificationHypothesis[] {
    const hypotheses = llmResponse.hypotheses ?? [];

    return hypotheses.slice(0, 5).map((h: any) => {
      const categoryId = h.categoryId ?? h.category ?? '';
      const categoryName =
        h.categoryName ?? h.label ?? this.lookupCategoryName(categoryId, domainPack);

      return {
        domainCategoryId: categoryId,
        label: categoryName,
        confidence: h.confidence ?? 0.5,
        confidenceBand: this.getConfidenceBand(h.confidence ?? 0.5),
        explanation: h.explanation ?? 'Visual evidence suggests this category',
        attributes: h.attributes ?? {},
        visualEvidence: h.visualEvidence,
      };
    });
  }

  private getConfidenceBand(confidence: number): 'HIGH' | 'MED' | 'LOW' {
    if (confidence >= 0.7) return 'HIGH';
    if (confidence >= 0.4) return 'MED';
    return 'LOW';
  }

  private lookupCategoryName(categoryId: string, domainPack: DomainPack): string {
    const category = domainPack.categories.find((c) => c.id === categoryId);
    return category?.label ?? categoryId;
  }

  /**
   * Build correction context from recent user corrections.
   * This helps the model learn from past mistakes locally.
   */
  private buildCorrectionContext(
    corrections: import('../types.js').RecentCorrection[],
    domainPack: DomainPack
  ): string {
    const recent = corrections.slice(0, 10); // Use only last 10 corrections
    const lines: string[] = [
      'LEARNING FROM RECENT USER CORRECTIONS:',
      'The user has recently corrected these classifications. Use this to avoid repeating mistakes:',
      '',
    ];

    for (const correction of recent) {
      const originalLabel = domainPack.categories.find(
        (c) => c.id === correction.originalCategoryId
      )?.label;
      lines.push(
        `- Misclassified as "${originalLabel ?? correction.originalCategoryId}" → Corrected to "${correction.correctedCategoryName}"`
      );
    }

    lines.push('');
    lines.push(
      'When you see similar items, strongly favor the corrected categories over the original misclassifications.'
    );

    return lines.join('\n');
  }

  /**
   * Fallback hypotheses when OpenAI fails or returns invalid data.
   * Uses top perception labels as fallback.
   */
  private getFallbackHypotheses(
    perception: PerceptionContext,
    _domainPack: DomainPack
  ): ClassificationHypothesis[] {
    // Use domain pack hints as fallback
    return perception.domainPackHints.topCandidates.slice(0, 3).map((candidate) => ({
      domainCategoryId: candidate.category,
      label: candidate.label,
      confidence: candidate.score,
      confidenceBand: this.getConfidenceBand(candidate.score),
      explanation: `Based on visual analysis`,
      attributes: {},
    }));
  }
}
