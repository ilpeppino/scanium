import { ProviderResponse } from '../types.js';
import { VisualFacts } from '../../vision/types.js';
import { DomainPack } from '../domain/domain-pack.js';
import { mapSignalsToDomainCategory } from '../domain/mapper.js';

/**
 * Perception context aggregates visual signals for the reasoning layer.
 * This bridges raw Google Vision output to the LLM reasoning prompt.
 */
export type PerceptionContext = {
  labels: Array<{ text: string; score: number }>;
  detectedText: string[];
  brands: string[];
  dominantColors: Array<{ name: string; percentage: number }>;
  domainPackHints: {
    topCandidates: Array<{ category: string; label: string; score: number }>;
    availableCategories: string[];
  };
};

/**
 * Build perception context from Google Vision response and visual facts.
 * Aggregates all perception signals into structured context for reasoning.
 */
export function buildPerceptionContext(
  visionResponse: ProviderResponse,
  domainPack: DomainPack,
  visualFacts?: VisualFacts
): PerceptionContext {
  // Extract labels from Vision API response
  const labels = visionResponse.signals.labels.map((label) => ({
    text: label.description,
    score: label.score,
  }));

  // Extract OCR text from visual facts
  const detectedText = visualFacts?.ocrSnippets?.map((s) => s.text) ?? [];

  // Extract brands from logo detection
  const brands = visualFacts?.logoHints?.map((l) => l.brand) ?? [];

  // Extract dominant colors
  const dominantColors =
    visualFacts?.dominantColors?.map((c) => ({
      name: c.name,
      percentage: c.pct,
    })) ?? [];

  // Get domain pack hints using existing mapper
  const mappingResult = mapSignalsToDomainCategory(domainPack, visionResponse.signals);

  // Build top candidates list by running mapper on top labels
  const topCandidates: Array<{ category: string; label: string; score: number }> = [];

  if (mappingResult.domainCategoryId) {
    const matchedCategory = domainPack.categories.find(
      (c) => c.id === mappingResult.domainCategoryId
    );
    if (matchedCategory) {
      topCandidates.push({
        category: mappingResult.domainCategoryId,
        label: matchedCategory.label,
        score: mappingResult.confidence ?? 0,
      });
    }
  }

  // Add other high-scoring categories as hints
  const otherCategories = domainPack.categories
    .filter((c) => c.id !== mappingResult.domainCategoryId)
    .slice(0, 2);

  for (const category of otherCategories) {
    topCandidates.push({
      category: category.id,
      label: category.label,
      score: 0.5, // Lower confidence for alternative suggestions
    });
  }

  return {
    labels,
    detectedText: detectedText.slice(0, 10), // Limit to 10 OCR snippets
    brands,
    dominantColors: dominantColors.slice(0, 5), // Limit to 5 colors
    domainPackHints: {
      topCandidates: topCandidates.slice(0, 3),
      availableCategories: domainPack.categories.map((c) => c.id),
    },
  };
}

/**
 * Build user prompt for reasoning LLM from perception context.
 */
export function buildReasoningUserPrompt(
  context: PerceptionContext,
  domainPack: DomainPack
): string {
  const labelsList = context.labels
    .map((l) => `"${l.text}" (${(l.score * 100).toFixed(0)}%)`)
    .join(', ');

  const textSection =
    context.detectedText.length > 0
      ? `\nText found: ${context.detectedText.join(', ')}`
      : '';

  const brandsSection =
    context.brands.length > 0 ? `\nBrands detected: ${context.brands.join(', ')}` : '';

  const colorsSection =
    context.dominantColors.length > 0
      ? `\nDominant colors: ${context.dominantColors.map((c) => `${c.name} (${c.percentage}%)`).join(', ')}`
      : '';

  const categoriesList = domainPack.categories
    .slice(0, 30)
    .map((c) => `- ${c.id}: ${c.label}`)
    .join('\n');

  const hintsSection = context.domainPackHints.topCandidates
    .map((c) => `- ${c.label} (score: ${c.score.toFixed(2)})`)
    .join('\n');

  return `VISUAL PERCEPTION DATA:

Labels detected: ${labelsList}${textSection}${brandsSection}${colorsSection}

AVAILABLE CATEGORIES (domain pack):
${categoriesList}

TOP CATEGORY HINTS:
${hintsSection}

TASK:
Generate 3-5 ranked hypotheses for what this item is. Prioritize categories useful for resale listings.`;
}
