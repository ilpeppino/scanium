import {
  AssistantChatRequest,
  AssistantChatRequestWithVision,
  AssistantResponse,
  AssistantAction,
  ConfidenceTier,
  EvidenceBullet,
  SuggestedAttribute,
  SuggestedDraftUpdate,
  ItemContextSnapshot,
} from './types.js';
import { VisualFacts } from '../vision/types.js';
import { resolveAttributes, ResolvedAttributes, ResolvedAttribute } from '../vision/attribute-resolver.js';

export interface AssistantProvider {
  respond(request: AssistantChatRequest | AssistantChatRequestWithVision): Promise<AssistantResponse>;
}

const DEFAULT_RESPONSE =
  'I can help with listing improvements, missing details, and safe pricing guidance. What would you like to adjust?';

function normalizeText(text: string): string {
  return text.toLowerCase();
}

function buildTitleSuggestion(title?: string | null, category?: string | null): string {
  const base = title?.trim() || category?.trim() || 'Item';
  return base.length > 0 ? base : 'Item';
}

function buildPriceGuidance(priceEstimate?: number | null): string {
  if (!priceEstimate || priceEstimate <= 0) {
    return 'I do not have enough data for a reliable price range yet. If you can share condition, brand, and recent comparable prices, I can refine an estimate.';
  }
  const low = Math.max(1, Math.round(priceEstimate * 0.85));
  const high = Math.max(low + 1, Math.round(priceEstimate * 1.15));
  return `Based on the current draft estimate, a reasonable range is EUR ${low}-${high}.`;
}

function buildChecklist(): string {
  return [
    'Confirm brand, model, and any serial numbers.',
    'Note condition issues (scratches, stains, missing parts).',
    'Capture measurements and key dimensions.',
    'Take clear photos: front, back, labels, defects, and scale reference.',
  ].join(' ');
}

function createDraftUpdateAction(itemId: string, title?: string, description?: string): AssistantAction {
  const payload: Record<string, string> = { itemId };
  if (title) {
    payload.title = title;
  }
  if (description) {
    payload.description = description;
  }
  return { type: 'APPLY_DRAFT_UPDATE', payload };
}

export class MockAssistantProvider implements AssistantProvider {
  async respond(request: AssistantChatRequest): Promise<AssistantResponse> {
    const message = normalizeText(request.message || '');
    const primaryItem = request.items[0];

    if (message.includes('marktplaats') && message.includes('price')) {
      return {
        content:
          'I cannot query Marktplaats directly because there is no official API available. If you can share a few comparable listings or prices, I can help interpret them and suggest a range.',
      };
    }

    if (!primaryItem) {
      return {
        content: 'Attach at least one item so I can tailor the listing advice.',
      };
    }

    if (message.includes('title')) {
      const suggestion = buildTitleSuggestion(primaryItem.title, primaryItem.category);
      const improvedTitle = `Used ${suggestion}`.trim();
      return {
        content: `Suggested title: "${improvedTitle}".`,
        actions: [
          createDraftUpdateAction(primaryItem.itemId, improvedTitle),
          {
            type: 'COPY_TEXT',
            payload: { label: 'Title', text: improvedTitle },
          },
        ],
      };
    }

    if (message.includes('description')) {
      const description = `Clean, concise description: ${primaryItem.title || 'Item'} in ${primaryItem.category || 'good'} condition.`;
      return {
        content: 'Here is an improved description you can use.',
        actions: [createDraftUpdateAction(primaryItem.itemId, undefined, description)],
      };
    }

    if (message.includes('detail') || message.includes('missing')) {
      return {
        content: buildChecklist(),
      };
    }

    if (message.includes('price') || message.includes('estimate')) {
      return {
        content: buildPriceGuidance(primaryItem.priceEstimate),
      };
    }

    if (message.includes('posting assist')) {
      return {
        content: 'Opening Posting Assist for this item.',
        actions: [{ type: 'OPEN_POSTING_ASSIST', payload: { itemId: primaryItem.itemId } }],
      };
    }

    if (message.includes('share')) {
      return {
        content: 'Ready to share the draft for this item.',
        actions: [{ type: 'OPEN_SHARE', payload: { itemId: primaryItem.itemId } }],
      };
    }

    return { content: DEFAULT_RESPONSE };
  }
}

/**
 * Question types that require visual evidence.
 */
const VISUAL_QUESTION_PATTERNS = {
  color: /\b(color|colour|colors|colours|shade|hue)\b/i,
  brand: /\b(brand|make|manufacturer|maker|logo)\b/i,
  model: /\b(model|type|variant|version|series)\b/i,
  text: /\b(text|label|writing|inscription|says|written|reads)\b/i,
  material: /\b(material|fabric|made of|construction)\b/i,
};

/**
 * Detect if a question requires visual evidence.
 */
function detectVisualQuestion(message: string): {
  requiresVision: boolean;
  questionType?: 'color' | 'brand' | 'model' | 'text' | 'material';
} {
  const normalized = message.toLowerCase();

  for (const [type, pattern] of Object.entries(VISUAL_QUESTION_PATTERNS)) {
    if (pattern.test(normalized)) {
      return { requiresVision: true, questionType: type as keyof typeof VISUAL_QUESTION_PATTERNS };
    }
  }

  return { requiresVision: false };
}

/**
 * Build evidence bullets from VisualFacts.
 */
function buildEvidence(facts: VisualFacts, questionType?: string): EvidenceBullet[] {
  const evidence: EvidenceBullet[] = [];

  // Color evidence
  if (questionType === 'color' || !questionType) {
    for (const color of facts.dominantColors.slice(0, 3)) {
      evidence.push({
        type: 'color',
        text: `Dominant color: ${color.name} (${color.pct}%)`,
      });
    }
  }

  // OCR/text evidence
  if (questionType === 'brand' || questionType === 'text' || questionType === 'model' || !questionType) {
    for (const snippet of facts.ocrSnippets.slice(0, 5)) {
      evidence.push({
        type: 'ocr',
        text: `Detected text: "${snippet.text}"`,
      });
    }
  }

  // Logo evidence
  if (questionType === 'brand' && facts.logoHints) {
    for (const logo of facts.logoHints.slice(0, 3)) {
      evidence.push({
        type: 'logo',
        text: `Detected logo: ${logo.brand} (${Math.round(logo.score * 100)}% confidence)`,
      });
    }
  }

  // Label evidence for material/type questions
  if (questionType === 'material' || !questionType) {
    for (const label of facts.labelHints.slice(0, 3)) {
      evidence.push({
        type: 'label',
        text: `Image label: ${label.label} (${Math.round(label.score * 100)}% confidence)`,
      });
    }
  }

  return evidence;
}

/**
 * Determine confidence tier based on evidence strength.
 */
function determineConfidence(
  facts: VisualFacts,
  questionType?: string
): ConfidenceTier {
  // Check for strong evidence
  if (questionType === 'color') {
    const topColor = facts.dominantColors[0];
    if (topColor && topColor.pct >= 40) return 'HIGH';
    if (topColor && topColor.pct >= 20) return 'MED';
    return 'LOW';
  }

  if (questionType === 'brand') {
    // Logo detection is strongest evidence
    if (facts.logoHints && facts.logoHints.length > 0) {
      const topLogo = facts.logoHints[0];
      if (topLogo.score >= 0.8) return 'HIGH';
      if (topLogo.score >= 0.5) return 'MED';
    }
    // OCR text as secondary evidence
    if (facts.ocrSnippets.length > 0) {
      const hasConfidentOcr = facts.ocrSnippets.some((s) => (s.confidence ?? 0) >= 0.8);
      if (hasConfidentOcr) return 'MED';
    }
    return 'LOW';
  }

  if (questionType === 'model' || questionType === 'text') {
    if (facts.ocrSnippets.length >= 3) return 'HIGH';
    if (facts.ocrSnippets.length >= 1) return 'MED';
    return 'LOW';
  }

  // Default: base on overall evidence availability
  const hasColors = facts.dominantColors.length > 0;
  const hasOcr = facts.ocrSnippets.length > 0;
  const hasLogos = facts.logoHints && facts.logoHints.length > 0;
  const hasLabels = facts.labelHints.length > 0;

  const evidenceCount = [hasColors, hasOcr, hasLogos, hasLabels].filter(Boolean).length;
  if (evidenceCount >= 3) return 'HIGH';
  if (evidenceCount >= 2) return 'MED';
  return 'LOW';
}

/**
 * Build suggested attributes from VisualFacts.
 */
function buildSuggestedAttributes(
  facts: VisualFacts,
  questionType?: string
): SuggestedAttribute[] {
  const suggestions: SuggestedAttribute[] = [];

  // Suggest brand from logo or OCR
  if (!questionType || questionType === 'brand') {
    if (facts.logoHints && facts.logoHints.length > 0) {
      const topLogo = facts.logoHints[0];
      suggestions.push({
        key: 'brand',
        value: topLogo.brand,
        confidence: topLogo.score >= 0.8 ? 'HIGH' : topLogo.score >= 0.5 ? 'MED' : 'LOW',
        source: 'logo',
      });
    } else {
      // Look for brand-like text in OCR
      const brandPatterns = /^[A-Z][A-Za-z0-9]+$/;
      const potentialBrand = facts.ocrSnippets.find(
        (s) => brandPatterns.test(s.text) && s.text.length >= 3 && s.text.length <= 20
      );
      if (potentialBrand) {
        suggestions.push({
          key: 'brand',
          value: potentialBrand.text,
          confidence: 'LOW',
          source: 'ocr',
        });
      }
    }
  }

  // Suggest color
  if (!questionType || questionType === 'color') {
    const topColor = facts.dominantColors[0];
    if (topColor) {
      suggestions.push({
        key: 'color',
        value: topColor.name,
        confidence: topColor.pct >= 40 ? 'HIGH' : topColor.pct >= 20 ? 'MED' : 'LOW',
        source: 'color',
      });
    }
  }

  return suggestions;
}

/**
 * Generate a grounded response for color questions.
 */
function buildColorResponse(
  facts: VisualFacts,
  itemTitle?: string | null
): { content: string; confidence: ConfidenceTier } {
  if (facts.dominantColors.length === 0) {
    return {
      content: 'I cannot determine the color from the available images. Could you take a well-lit photo showing the full item?',
      confidence: 'LOW',
    };
  }

  const colors = facts.dominantColors.slice(0, 3);
  const primary = colors[0];

  if (colors.length === 1 || primary.pct >= 60) {
    return {
      content: `Based on the image analysis, this ${itemTitle || 'item'} is primarily **${primary.name}** (${primary.pct}% of the visible area).`,
      confidence: primary.pct >= 40 ? 'HIGH' : 'MED',
    };
  }

  const colorList = colors.map((c) => `${c.name} (${c.pct}%)`).join(', ');
  return {
    content: `Based on the image analysis, the dominant colors are: ${colorList}.`,
    confidence: 'MED',
  };
}

/**
 * Generate a grounded response for brand questions.
 */
function buildBrandResponse(
  facts: VisualFacts,
  itemTitle?: string | null
): { content: string; confidence: ConfidenceTier } {
  // Check for logo detection first
  if (facts.logoHints && facts.logoHints.length > 0) {
    const topLogo = facts.logoHints[0];
    if (topLogo.score >= 0.7) {
      return {
        content: `I detected a **${topLogo.brand}** logo in the image with ${Math.round(topLogo.score * 100)}% confidence.`,
        confidence: topLogo.score >= 0.8 ? 'HIGH' : 'MED',
      };
    }
  }

  // Check OCR for brand-like text
  const brandPatterns = /^[A-Z][A-Za-z0-9\-]+$/;
  const potentialBrands = facts.ocrSnippets.filter(
    (s) => brandPatterns.test(s.text) && s.text.length >= 3 && s.text.length <= 25
  );

  if (potentialBrands.length > 0) {
    const topBrand = potentialBrands[0];
    return {
      content: `I found text that might be a brand name: "**${topBrand.text}**". This was detected via OCR with ${Math.round((topBrand.confidence ?? 0.8) * 100)}% confidence. Please verify this matches the actual brand.`,
      confidence: 'MED',
    };
  }

  // No brand evidence found
  return {
    content: 'I cannot confirm the brand from the available images. Could you take a close-up photo of any brand labels, logos, or tags on the item?',
    confidence: 'LOW',
  };
}

/**
 * Generate a grounded response for model questions.
 */
function buildModelResponse(
  facts: VisualFacts,
  itemTitle?: string | null
): { content: string; confidence: ConfidenceTier } {
  // Look for model number patterns in OCR
  const modelPatterns = /^[A-Z0-9\-\.]+$/i;
  const potentialModels = facts.ocrSnippets.filter(
    (s) => modelPatterns.test(s.text) && s.text.length >= 3 && /\d/.test(s.text)
  );

  if (potentialModels.length > 0) {
    const topModel = potentialModels[0];
    return {
      content: `I found text that might be a model number: "**${topModel.text}**". Please verify this against any documentation or the manufacturer's website.`,
      confidence: 'MED',
    };
  }

  // Check for any relevant text
  if (facts.ocrSnippets.length > 0) {
    const textList = facts.ocrSnippets.slice(0, 3).map((s) => `"${s.text}"`).join(', ');
    return {
      content: `I found the following text on the item: ${textList}. None of these clearly appear to be a model number. Could you take a close-up of any product labels or serial number plates?`,
      confidence: 'LOW',
    };
  }

  return {
    content: 'I cannot identify the model from the available images. Could you take a close-up photo of any product labels, serial numbers, or identification plates?',
    confidence: 'LOW',
  };
}

/**
 * Convert ResolvedAttribute confidence to ConfidenceTier.
 */
function toConfidenceTier(confidence: 'HIGH' | 'MED' | 'LOW'): ConfidenceTier {
  return confidence;
}

/**
 * Build suggested attributes from ResolvedAttributes.
 */
function buildSuggestedAttributesFromResolved(
  resolved: ResolvedAttributes
): SuggestedAttribute[] {
  const suggestions: SuggestedAttribute[] = [];

  if (resolved.brand) {
    suggestions.push({
      key: 'brand',
      value: resolved.brand.value,
      confidence: toConfidenceTier(resolved.brand.confidence),
      source: resolved.brand.evidenceRefs[0]?.type,
    });
  }

  if (resolved.model) {
    suggestions.push({
      key: 'model',
      value: resolved.model.value,
      confidence: toConfidenceTier(resolved.model.confidence),
      source: resolved.model.evidenceRefs[0]?.type,
    });
  }

  if (resolved.color) {
    suggestions.push({
      key: 'color',
      value: resolved.color.value,
      confidence: toConfidenceTier(resolved.color.confidence),
      source: 'color',
    });
  }

  if (resolved.material) {
    suggestions.push({
      key: 'material',
      value: resolved.material.value,
      confidence: toConfidenceTier(resolved.material.confidence),
      source: 'label',
    });
  }

  return suggestions;
}

/**
 * Build evidence from ResolvedAttributes.
 */
function buildEvidenceFromResolved(
  resolved: ResolvedAttributes,
  questionType?: string
): EvidenceBullet[] {
  const evidence: EvidenceBullet[] = [];

  // Add brand evidence
  if (resolved.brand && (!questionType || questionType === 'brand')) {
    for (const ref of resolved.brand.evidenceRefs.slice(0, 2)) {
      evidence.push({
        type: ref.type as 'ocr' | 'color' | 'label' | 'logo',
        text: ref.type === 'logo'
          ? `Detected logo: ${ref.value}`
          : `Detected text: "${ref.value}"`,
      });
    }
  }

  // Add model evidence
  if (resolved.model && (!questionType || questionType === 'model')) {
    for (const ref of resolved.model.evidenceRefs.slice(0, 2)) {
      evidence.push({
        type: 'ocr',
        text: `Model/SKU: "${ref.value}"`,
      });
    }
  }

  // Add color evidence
  if (resolved.color && (!questionType || questionType === 'color')) {
    evidence.push({
      type: 'color',
      text: `Dominant color: ${resolved.color.evidenceRefs[0]?.value || resolved.color.value}`,
    });

    if (resolved.secondaryColor) {
      evidence.push({
        type: 'color',
        text: `Secondary color: ${resolved.secondaryColor.evidenceRefs[0]?.value || resolved.secondaryColor.value}`,
      });
    }
  }

  // Add material evidence
  if (resolved.material && (!questionType || questionType === 'material')) {
    evidence.push({
      type: 'label',
      text: `Material hint: ${resolved.material.value}`,
    });
  }

  return evidence.slice(0, 5); // Max 5 evidence bullets
}

/**
 * Build actions from ResolvedAttributes.
 */
function buildActionsFromResolved(
  resolved: ResolvedAttributes,
  item: ItemContextSnapshot,
  questionType?: string
): AssistantAction[] {
  const actions: AssistantAction[] = [];
  const hasAnyAttribute = resolved.brand || resolved.model || resolved.color;

  // ADD_ATTRIBUTES action (adds to item attributes)
  if (hasAnyAttribute) {
    const payload: Record<string, string> = { itemId: item.itemId };
    const labels: string[] = [];

    if (resolved.brand) {
      payload.brand = resolved.brand.value;
      payload.brandConfidence = resolved.brand.confidence;
      labels.push('brand');
    }
    if (resolved.model) {
      payload.model = resolved.model.value;
      payload.modelConfidence = resolved.model.confidence;
      labels.push('model');
    }
    if (resolved.color) {
      payload.color = resolved.color.value;
      payload.colorConfidence = resolved.color.confidence;
      labels.push('color');
    }

    // Require confirmation if any attribute has LOW confidence
    const hasLowConfidence =
      resolved.brand?.confidence === 'LOW' ||
      resolved.model?.confidence === 'LOW' ||
      resolved.color?.confidence === 'LOW';

    actions.push({
      type: 'ADD_ATTRIBUTES',
      payload,
      label: `Add ${labels.join(', ')}`,
      requiresConfirmation: hasLowConfidence,
    });
  }

  // APPLY_DRAFT_UPDATE for title with brand (only if HIGH confidence)
  if (resolved.brand?.confidence === 'HIGH' && item.title) {
    const currentTitle = item.title;
    const brand = resolved.brand.value;

    // Only suggest if brand is not already in title
    if (!currentTitle.toLowerCase().includes(brand.toLowerCase())) {
      const improvedTitle = `${brand} ${currentTitle}`;
      actions.push({
        type: 'APPLY_DRAFT_UPDATE',
        payload: {
          itemId: item.itemId,
          title: improvedTitle,
        },
        label: 'Update title',
        requiresConfirmation: false,
      });
    }
  }

  // COPY_TEXT for brand/model
  if (resolved.brand) {
    actions.push({
      type: 'COPY_TEXT',
      payload: { label: 'Brand', text: resolved.brand.value },
      label: `Copy "${resolved.brand.value}"`,
    });
  }

  if (resolved.model) {
    actions.push({
      type: 'COPY_TEXT',
      payload: { label: 'Model', text: resolved.model.value },
      label: `Copy "${resolved.model.value}"`,
    });
  }

  // SUGGEST_NEXT_PHOTO if we have a suggestion
  if (resolved.suggestedNextPhoto) {
    actions.push({
      type: 'SUGGEST_NEXT_PHOTO',
      payload: { instruction: resolved.suggestedNextPhoto },
      label: 'Next photo tip',
    });
  }

  return actions;
}

/**
 * Build response content based on resolved attributes.
 */
function buildGroundedResponseContent(
  resolved: ResolvedAttributes,
  questionType: string | undefined,
  item: ItemContextSnapshot
): { content: string; confidence: ConfidenceTier } {
  if (questionType === 'color') {
    if (!resolved.color) {
      return {
        content: 'I cannot determine the color from the available images. Could you take a well-lit photo showing the full item?',
        confidence: 'LOW',
      };
    }

    if (resolved.secondaryColor) {
      return {
        content: `The item appears to be **${resolved.color.value}** with **${resolved.secondaryColor.value}** accents. The colors are close in proportion, so please confirm which is primary.`,
        confidence: 'LOW',
      };
    }

    const confidenceLabel =
      resolved.color.confidence === 'HIGH' ? 'High confidence' :
      resolved.color.confidence === 'MED' ? 'Likely' : 'Uncertain';

    return {
      content: `The item is **${resolved.color.value}**. (${confidenceLabel})`,
      confidence: toConfidenceTier(resolved.color.confidence),
    };
  }

  if (questionType === 'brand') {
    if (!resolved.brand) {
      return {
        content: 'I cannot confirm the brand from the available images. Could you take a close-up photo of any brand labels, logos, or tags?',
        confidence: 'LOW',
      };
    }

    const confidenceLabel =
      resolved.brand.confidence === 'HIGH' ? 'High confidence' :
      resolved.brand.confidence === 'MED' ? 'Likely' : 'Uncertain';

    const sourceNote =
      resolved.brand.evidenceRefs[0]?.type === 'logo'
        ? 'detected via logo recognition'
        : 'detected via text recognition';

    return {
      content: `The brand is **${resolved.brand.value}** (${confidenceLabel}, ${sourceNote}).`,
      confidence: toConfidenceTier(resolved.brand.confidence),
    };
  }

  if (questionType === 'model') {
    if (!resolved.model) {
      return {
        content: 'I cannot identify the model from the available images. Could you take a close-up photo of any product labels, serial numbers, or model plates?',
        confidence: 'LOW',
      };
    }

    const confidenceLabel =
      resolved.model.confidence === 'HIGH' ? 'High confidence' :
      resolved.model.confidence === 'MED' ? 'Likely' : 'Uncertain';

    return {
      content: `The model appears to be **${resolved.model.value}**. (${confidenceLabel}) Please verify against product documentation.`,
      confidence: toConfidenceTier(resolved.model.confidence),
    };
  }

  // General visual analysis
  const parts: string[] = [];

  if (resolved.brand) {
    parts.push(`Brand: ${resolved.brand.value}`);
  }
  if (resolved.model) {
    parts.push(`Model: ${resolved.model.value}`);
  }
  if (resolved.color) {
    parts.push(`Color: ${resolved.color.value}`);
  }
  if (resolved.material) {
    parts.push(`Material: ${resolved.material.value}`);
  }

  if (parts.length === 0) {
    return {
      content: 'I could not extract specific attributes from the images. Try taking clearer photos of labels, logos, or the full item.',
      confidence: 'LOW',
    };
  }

  // Determine overall confidence
  const confidences = [resolved.brand, resolved.model, resolved.color, resolved.material]
    .filter(Boolean)
    .map((a) => a!.confidence);

  const overallConfidence: ConfidenceTier =
    confidences.every((c) => c === 'HIGH') ? 'HIGH' :
    confidences.some((c) => c === 'LOW') ? 'LOW' : 'MED';

  return {
    content: `Based on image analysis:\n${parts.map((p) => `• ${p}`).join('\n')}`,
    confidence: overallConfidence,
  };
}

/**
 * Grounded Assistant Provider that uses VisualFacts and AttributeResolver
 * for evidence-based responses.
 */
export class GroundedMockAssistantProvider implements AssistantProvider {
  private readonly fallbackProvider: MockAssistantProvider;

  constructor() {
    this.fallbackProvider = new MockAssistantProvider();
  }

  async respond(request: AssistantChatRequest | AssistantChatRequestWithVision): Promise<AssistantResponse> {
    const message = normalizeText(request.message || '');
    const primaryItem = request.items[0];
    const visualRequest = request as AssistantChatRequestWithVision;
    const visualFacts = visualRequest.visualFacts?.get(primaryItem?.itemId ?? '');

    // Detect if this is a visual question
    const { requiresVision, questionType } = detectVisualQuestion(message);

    // If no visual evidence needed or available, use fallback
    if (!requiresVision || !visualFacts) {
      return this.fallbackProvider.respond(request);
    }

    // Resolve attributes from VisualFacts
    const resolved = resolveAttributes(primaryItem?.itemId ?? '', visualFacts);

    // Build response components
    const responseData = buildGroundedResponseContent(resolved, questionType, primaryItem!);
    const evidence = buildEvidenceFromResolved(resolved, questionType);
    const suggestedAttributes = buildSuggestedAttributesFromResolved(resolved);
    const actions = buildActionsFromResolved(resolved, primaryItem!, questionType);

    return {
      content: responseData.content,
      actions: actions.length > 0 ? actions : undefined,
      confidenceTier: responseData.confidence,
      evidence: evidence.length > 0 ? evidence : undefined,
      suggestedAttributes: suggestedAttributes.length > 0 ? suggestedAttributes : undefined,
      suggestedNextPhoto: resolved.suggestedNextPhoto,
    };
  }
}
