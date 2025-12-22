import {
  AssistantChatRequest,
  AssistantResponse,
  AssistantAction,
} from './types.js';

export interface AssistantProvider {
  respond(request: AssistantChatRequest): Promise<AssistantResponse>;
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
