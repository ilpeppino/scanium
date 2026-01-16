import {
  AssistantChatRequest,
  AssistantChatRequestWithVision,
  AssistantResponse,
  AssistantAction,
  ConfidenceTier,
  EvidenceBullet,
  SuggestedAttribute,
  ItemContextSnapshot,
  AssistantPrefs,
  StructuredSellingHelp,
} from './types.js';
import { resolveAttributes, ResolvedAttributes } from '../vision/attribute-resolver.js';
import {
  selectTemplatePack,
  generateTitleSuggestion,
  getMissingInfoQuestions,
  getBuyerFaqSuggestions,
  TemplatePack,
} from './template-packs.js';

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

function buildPriceGuidance(priceEstimate?: number | null): { content: string; priceRange?: { low: number; high: number; currency: string } } {
  if (!priceEstimate || priceEstimate <= 0) {
    return {
      content: 'I do not have enough data for a reliable price range yet. If you can share condition, brand, and recent comparable prices, I can refine an estimate.',
    };
  }
  const low = Math.max(1, Math.round(priceEstimate * 0.85));
  const high = Math.max(low + 1, Math.round(priceEstimate * 1.15));
  return {
    content: '',
    priceRange: { low, high, currency: 'EUR' },
  };
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
      const guidance = buildPriceGuidance(primaryItem.priceEstimate);
      const response: AssistantResponse = {
        content: guidance.content,
      };
      if (guidance.priceRange) {
        response.marketPrice = {
          status: 'OK',
          countryCode: 'NL',
          marketplacesUsed: [],
          range: guidance.priceRange,
          confidence: 'MED',
          errorCode: undefined,
        };
      }
      return response;
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
  _questionType?: string
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
  _item: ItemContextSnapshot
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
    content: parts.map((p) => `• ${p}`).join('\n'),
    confidence: overallConfidence,
  };
}

/**
 * Check if a message is asking for listing help (title, description, etc.)
 */
function isListingHelpQuestion(message: string): boolean {
  const keywords = [
    'title', 'description', 'listing', 'improve', 'suggest', 'write',
    'help me', 'better', 'draft', 'create', 'make', 'generate',
  ];
  const lower = message.toLowerCase();
  return keywords.some((kw) => lower.includes(kw));
}

/**
 * Apply tone preference to response content.
 */
function applyTone(content: string, tone?: string): string {
  if (!tone || tone === 'NEUTRAL') return content;

  if (tone === 'FRIENDLY') {
    // Add friendly touches
    if (!content.startsWith('Great') && !content.startsWith('Sure') && !content.startsWith('Happy')) {
      content = 'Happy to help! ' + content;
    }
  } else if (tone === 'PROFESSIONAL') {
    // Make more formal
    content = content.replace(/!/g, '.');
    content = content.replace(/Great!/g, 'Understood.');
    content = content.replace(/Happy to help!/g, '');
  } else if (tone === 'MARKETPLACE') {
    // Strip marketing language and make concise
    content = content.replace(/!/g, '.');
    content = content.replace(/\b(perfect for|don't miss out|amazing|incredible|fantastic|wonderful)\b/gi, '');
    content = content.replace(/Happy to help!\s*/g, '');
    content = content.replace(/Great!\s*/g, '');
    content = content.replace(/\s+/g, ' '); // Clean up extra spaces
  }

  return content.trim();
}

/**
 * Build structured selling help from template pack and resolved attributes.
 */
function buildStructuredHelp(
  pack: TemplatePack,
  item: ItemContextSnapshot,
  resolved?: ResolvedAttributes,
  _prefs?: AssistantPrefs
): StructuredSellingHelp {
  const existingAttrs: Record<string, string | undefined> = {};

  // Collect existing attributes
  if (item.attributes) {
    for (const attr of item.attributes) {
      existingAttrs[attr.key.toLowerCase()] = attr.value;
    }
  }

  // Add resolved attributes
  if (resolved?.brand) existingAttrs.brand = resolved.brand.value;
  if (resolved?.model) existingAttrs.model = resolved.model.value;
  if (resolved?.color) existingAttrs.color = resolved.color.value;
  if (resolved?.material) existingAttrs.material = resolved.material.value;

  // Generate title suggestion
  const titleSuggestion = generateTitleSuggestion(pack, {
    brand: existingAttrs.brand,
    model: existingAttrs.model,
    color: existingAttrs.color,
    condition: existingAttrs.condition,
    size: existingAttrs.size,
    material: existingAttrs.material,
    keyFeature: item.title ?? undefined,
  });

  // Determine confidence
  let titleConfidence: ConfidenceTier = 'MED';
  if (existingAttrs.brand && existingAttrs.model) {
    titleConfidence = 'HIGH';
  } else if (!existingAttrs.brand && !existingAttrs.model) {
    titleConfidence = 'LOW';
  }

  // Get missing info and FAQ suggestions
  const missingInfoChecklist = getMissingInfoQuestions(pack, existingAttrs, 5);
  const buyerFaqSuggestions = getBuyerFaqSuggestions(pack, 5);

  // Build description sections
  const descriptionSections: Array<{ id: string; label: string; content: string }> = [];
  for (const section of pack.descriptionSections) {
    let content = '';
    if (section.id === 'condition' && existingAttrs.condition) {
      content = existingAttrs.condition;
    } else if (section.id === 'brand' && existingAttrs.brand) {
      content = existingAttrs.brand;
    } else if (section.id === 'material' && existingAttrs.material) {
      content = existingAttrs.material;
    } else if (section.id === 'color' && existingAttrs.color) {
      content = existingAttrs.color;
    }
    descriptionSections.push({
      id: section.id,
      label: section.label,
      content: content || `[Add ${section.label.toLowerCase()}]`,
    });
  }

  // Build description text
  const descriptionText = descriptionSections
    .filter((s) => !s.content.startsWith('['))
    .map((s) => `**${s.label}:** ${s.content}`)
    .join('\n');

  return {
    suggestedTitle: {
      value: titleSuggestion,
      confidence: titleConfidence,
    },
    suggestedDescription: descriptionText ? {
      value: descriptionText,
      sections: descriptionSections,
      confidence: 'MED',
    } : undefined,
    missingInfoChecklist,
    buyerFaqSuggestions,
    templatePackId: pack.packId,
  };
}

/**
 * Grounded Assistant Provider that uses VisualFacts and AttributeResolver
 * for evidence-based responses.
 *
 * PR5 additions:
 * - Template pack integration for category-aware responses
 * - Personalization preferences (tone, language, region, units)
 * - Structured selling help output
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
    const prefs = request.assistantPrefs;

    // Select template pack based on category
    const category = primaryItem?.category;
    const templatePack = selectTemplatePack(category);

    // Detect if this is a visual question
    const { requiresVision, questionType } = detectVisualQuestion(message);

    // Check if this is a listing help question
    const isListingHelp = isListingHelpQuestion(message);

    // Resolve attributes from VisualFacts if available
    let resolved: ResolvedAttributes | undefined;
    if (visualFacts) {
      resolved = resolveAttributes(primaryItem?.itemId ?? '', visualFacts);
    }

    // Build structured help if this is a listing-related question
    let structuredHelp: StructuredSellingHelp | undefined;
    if (isListingHelp && primaryItem) {
      structuredHelp = buildStructuredHelp(templatePack, primaryItem, resolved, prefs);
    }

    // If no visual evidence needed or available, handle with template-aware fallback
    if (!requiresVision || !visualFacts) {
      // Build response content based on question type
      let content: string;
      let actions: AssistantAction[] = [];

      if (isListingHelp && structuredHelp?.suggestedTitle) {
        content = `Here's a suggested title based on the ${templatePack.displayName} template:\n\n`;
        content += `**"${structuredHelp.suggestedTitle.value}"**\n\n`;

        if (structuredHelp.missingInfoChecklist && structuredHelp.missingInfoChecklist.length > 0) {
          content += `To improve your listing, consider adding:\n`;
          for (const q of structuredHelp.missingInfoChecklist.slice(0, 3)) {
            content += `• ${q}\n`;
          }
        }

        // Add apply action
        if (primaryItem) {
          actions.push({
            type: 'APPLY_DRAFT_UPDATE',
            payload: {
              itemId: primaryItem.itemId,
              title: structuredHelp.suggestedTitle.value,
            },
            label: 'Apply title',
            requiresConfirmation: structuredHelp.suggestedTitle.confidence === 'LOW',
          });
        }
      } else {
        // Fallback to default response
        const fallbackResponse = await this.fallbackProvider.respond(request);
        content = fallbackResponse.content;
        actions = fallbackResponse.actions ?? [];
      }

      // Apply tone preference
      content = applyTone(content, prefs?.tone);

      return {
        content,
        actions: actions.length > 0 ? actions : undefined,
        structuredHelp,
      };
    }

    // Build response components with visual evidence
    const responseData = buildGroundedResponseContent(resolved!, questionType, primaryItem!);
    const evidence = buildEvidenceFromResolved(resolved!, questionType);
    const suggestedAttributes = buildSuggestedAttributesFromResolved(resolved!);
    const actions = buildActionsFromResolved(resolved!, primaryItem!, questionType);

    // Apply tone preference
    const content = applyTone(responseData.content, prefs?.tone);

    return {
      content,
      actions: actions.length > 0 ? actions : undefined,
      confidenceTier: responseData.confidence,
      evidence: evidence.length > 0 ? evidence : undefined,
      suggestedAttributes: suggestedAttributes.length > 0 ? suggestedAttributes : undefined,
      suggestedNextPhoto: resolved!.suggestedNextPhoto,
      structuredHelp,
    };
  }
}
