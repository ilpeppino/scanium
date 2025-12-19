import { DomainPack } from './domain-pack.js';
import { ClassificationSignals } from '../types.js';

export type MappingResult = {
  domainCategoryId: string | null;
  confidence: number | null;
  label: string | null;
  attributes: Record<string, string>;
  timingMs: number;
  debug: {
    threshold: number;
    bestScore: number;
    matchedLabel?: string;
    matchedToken?: string;
    reason: string;
    priority?: number;
    contextPenaltyApplied?: boolean;
  };
};

const DEFAULT_THRESHOLD = 0.28;
const DEFAULT_CONTEXT_PENALTY = 0.5;
const DEFAULT_PRIORITY = 5;

type Candidate = {
  categoryId: string;
  priority: number;
  score: number;
  roundedScore: number;
  attributes: Record<string, string>;
  matchedLabel?: string;
  matchedToken?: string;
  contextPenaltyApplied: boolean;
};

export function mapSignalsToDomainCategory(
  pack: DomainPack,
  signals: ClassificationSignals
): MappingResult {
  const started = performance.now();
  const threshold = pack.threshold ?? DEFAULT_THRESHOLD;
  const contextPenalty = clampPenalty(pack.contextPenalty ?? DEFAULT_CONTEXT_PENALTY);
  const stoplistTokens = new Set(
    (pack.contextStoplist ?? []).map((token) => token.toLowerCase().trim()).filter(Boolean)
  );

  const candidates: Candidate[] = [];
  let fallbackCandidate: Candidate | null = null;

  for (const category of pack.categories) {
    let categoryScore = 0;
    let categoryMatchLabel: string | undefined;
    let categoryMatchToken: string | undefined;
    let contextPenaltyApplied = false;

    for (const label of signals.labels) {
      const normalizedLabel = label.description.toLowerCase();
      const matchesToken = category.tokens.some((token) =>
        normalizedLabel.includes(token.toLowerCase())
      );
      if (!matchesToken) continue;

      const baseScore = label.score ?? 0;
      const isContextLabel = hasStoplistMatch(normalizedLabel, stoplistTokens);
      const effectiveScore = isContextLabel ? baseScore * contextPenalty : baseScore;

      if (effectiveScore > categoryScore) {
        categoryScore = effectiveScore;
        categoryMatchLabel = label.description;
        categoryMatchToken = category.tokens.find((token) =>
          normalizedLabel.includes(token.toLowerCase())
        );
        contextPenaltyApplied = isContextLabel;
      }
    }

    if (categoryScore <= 0) {
      continue;
    }

    const priority = category.priority ?? category.priorityTier ?? DEFAULT_PRIORITY;
    const candidate: Candidate = {
      categoryId: category.id,
      priority,
      score: categoryScore,
      roundedScore: Number(categoryScore.toFixed(3)),
      attributes: category.attributes ?? {},
      matchedLabel: categoryMatchLabel,
      matchedToken: categoryMatchToken,
      contextPenaltyApplied,
    };

    candidates.push(candidate);

    if (!fallbackCandidate || candidate.score > fallbackCandidate.score) {
      fallbackCandidate = candidate;
    }
  }

  const selected =
    candidates
      .filter((candidate) => candidate.score >= threshold)
      .sort((a, b) => {
        if (b.priority !== a.priority) {
          return b.priority - a.priority;
        }
        return b.score - a.score;
      })[0] ?? null;

  const reason = buildReason(selected, fallbackCandidate, threshold);

  return {
    domainCategoryId: selected ? selected.categoryId : null,
    confidence: selected ? selected.roundedScore : null,
    label: selected?.matchedToken ?? selected?.matchedLabel ?? null,
    attributes: selected ? selected.attributes : {},
    timingMs: Math.round(performance.now() - started),
    debug: {
      threshold,
      bestScore: selected?.roundedScore ?? fallbackCandidate?.roundedScore ?? 0,
      matchedLabel: selected?.matchedLabel ?? fallbackCandidate?.matchedLabel,
      matchedToken: selected?.matchedToken ?? fallbackCandidate?.matchedToken,
      reason,
      priority: selected?.priority,
      contextPenaltyApplied: selected?.contextPenaltyApplied,
    },
  };
}

function hasStoplistMatch(label: string, stoplist: Set<string>): boolean {
  if (stoplist.size === 0) return false;
  for (const token of stoplist) {
    if (token && label.includes(token)) {
      return true;
    }
  }
  return false;
}

function clampPenalty(value: number): number {
  if (Number.isNaN(value) || value <= 0) return DEFAULT_CONTEXT_PENALTY;
  return Math.min(1, Math.max(0.1, value));
}

function buildReason(
  selected: Candidate | null,
  fallback: Candidate | null,
  threshold: number
): string {
  if (selected) {
    const priorityText = `priority ${selected.priority}`;
    const labelText = selected.matchedLabel
      ? `label "${selected.matchedLabel}"`
      : 'matching signal';
    const contextNote = selected.contextPenaltyApplied ? ' (context penalty applied)' : '';
    return `Selected ${priorityText} category "${selected.categoryId}" from ${labelText} (score=${selected.roundedScore} >= threshold=${threshold})${contextNote}`;
  }

  if (fallback) {
    const labelText = fallback.matchedLabel
      ? `label "${fallback.matchedLabel}"`
      : 'available signals';
    return `No category met threshold ${threshold}; best candidate was priority ${fallback.priority} "${fallback.categoryId}" from ${labelText} (score=${fallback.roundedScore})`;
  }

  return 'No signals matched any domain category tokens';
}
