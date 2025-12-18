import { DomainPack } from './domain-pack.js';
import { ClassificationSignals } from '../types.js';

export type MappingResult = {
  domainCategoryId: string | null;
  confidence: number | null;
  attributes: Record<string, string>;
  timingMs: number;
};

const DEFAULT_THRESHOLD = 0.28;

export function mapSignalsToDomainCategory(
  pack: DomainPack,
  signals: ClassificationSignals
): MappingResult {
  const started = performance.now();
  const threshold = pack.threshold ?? DEFAULT_THRESHOLD;

  let bestCategory: string | null = null;
  let bestScore = 0;
  let bestAttributes: Record<string, string> = {};

  for (const category of pack.categories) {
    let categoryScore = 0;
    for (const label of signals.labels) {
      const normalizedLabel = label.description.toLowerCase();
      for (const token of category.tokens) {
        if (normalizedLabel.includes(token.toLowerCase())) {
          categoryScore = Math.max(categoryScore, label.score);
          break;
        }
      }
    }

    if (categoryScore > bestScore) {
      bestScore = categoryScore;
      bestCategory = category.id;
      bestAttributes = category.attributes ?? {};
    }
  }

  const confident = bestScore >= threshold;

  return {
    domainCategoryId: confident ? bestCategory : null,
    confidence: confident ? Number(bestScore.toFixed(3)) : null,
    attributes: confident ? bestAttributes : {},
    timingMs: Math.round(performance.now() - started),
  };
}
