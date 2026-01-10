import Anthropic from '@anthropic-ai/sdk';
import {
  AssistantChatRequest,
  AssistantChatRequestWithVision,
  AssistantResponse,
  AssistantAction,
  ConfidenceTier,
  EvidenceBullet,
  SuggestedAttribute,
  SuggestedDraftUpdate,
} from './types.js';
import { AssistantProvider } from './provider.js';
import { resolveAttributes, ResolvedAttributes } from '../vision/attribute-resolver.js';
import {
  buildListingSystemPrompt,
  buildListingUserPrompt,
  parseListingResponse,
} from './prompts/listing-generation.js';
import {
  checkResponseLanguage,
  recordLanguageCheck,
} from './language-consistency.js';
import { recordAssistantTokens } from '../../infra/observability/metrics.js';

export interface ClaudeProviderConfig {
  apiKey: string;
  model: string;
  maxTokens: number;
  timeoutMs: number;
}

/**
 * Claude-based assistant provider using the Anthropic API.
 *
 * Implements the AssistantProvider interface for real LLM-powered
 * listing generation with grounded responses based on visual evidence.
 */
export class ClaudeAssistantProvider implements AssistantProvider {
  private readonly client: Anthropic;
  private readonly model: string;
  private readonly maxTokens: number;

  constructor(config: ClaudeProviderConfig) {
    this.client = new Anthropic({
      apiKey: config.apiKey,
      timeout: config.timeoutMs,
    });
    this.model = config.model;
    this.maxTokens = config.maxTokens;
  }

  async respond(request: AssistantChatRequest | AssistantChatRequestWithVision): Promise<AssistantResponse> {
    const visualRequest = request as AssistantChatRequestWithVision;
    const primaryItem = request.items[0];

    // Resolve attributes from visual facts
    const resolvedAttributesMap = new Map<string, ResolvedAttributes>();
    if (visualRequest.visualFacts) {
      for (const [itemId, facts] of visualRequest.visualFacts) {
        const resolved = resolveAttributes(itemId, facts);
        resolvedAttributesMap.set(itemId, resolved);
      }
    }

    // Build prompts with language localization
    const language = request.assistantPrefs?.language ?? 'EN';
    const systemPrompt = buildListingSystemPrompt(request.assistantPrefs);
    const userPrompt = buildListingUserPrompt(
      request.items,
      resolvedAttributesMap,
      visualRequest.visualFacts,
      language
    );

    // Combine user message with generated prompt context
    const fullUserMessage = request.message
      ? `${request.message}\n\n${userPrompt}`
      : userPrompt;

    try {
      const response = await this.client.messages.create({
        model: this.model,
        max_tokens: this.maxTokens,
        system: systemPrompt,
        messages: [{ role: 'user', content: fullUserMessage }],
      });

      // Record token usage if available
      if (response.usage) {
        const inputTokens = response.usage.input_tokens || 0;
        const outputTokens = response.usage.output_tokens || 0;
        const totalTokens = inputTokens + outputTokens;

        recordAssistantTokens('claude', inputTokens, outputTokens, totalTokens);
      }

      // Extract text content from response
      const textContent = response.content
        .filter((block): block is Anthropic.TextBlock => block.type === 'text')
        .map((block) => block.text)
        .join('\n');

      // Check response language consistency
      if (language && language !== 'EN') {
        const langCheck = checkResponseLanguage(
          textContent,
          language,
          request.correlationId
        );
        recordLanguageCheck('response', langCheck.matchesExpected);

        // Log mismatch in DEV mode (handled by checkResponseLanguage)
        // Future: Could implement retry logic here if needed
      }

      // Parse the structured response
      const parsed = parseListingResponse(textContent);

      // Build response components
      const suggestedDraftUpdates: SuggestedDraftUpdate[] = [];
      const warnings: string[] = parsed.warnings ?? [];
      let overallConfidence: ConfidenceTier = 'MED';

      // Add title update if present
      if (parsed.title || parsed.suggestedDraftUpdates?.find(u => u.field === 'title')) {
        const titleUpdate = parsed.suggestedDraftUpdates?.find(u => u.field === 'title');
        const title = titleUpdate?.value ?? parsed.title ?? '';
        const confidence = titleUpdate?.confidence ?? 'MED';
        const requiresConfirmation = titleUpdate?.requiresConfirmation ?? confidence !== 'HIGH';

        suggestedDraftUpdates.push({
          field: 'title',
          value: title,
          confidence,
          requiresConfirmation,
        });

        if (confidence === 'LOW') {
          overallConfidence = 'LOW';
        }
      }

      // Add description update if present
      if (parsed.description || parsed.suggestedDraftUpdates?.find(u => u.field === 'description')) {
        const descUpdate = parsed.suggestedDraftUpdates?.find(u => u.field === 'description');
        const description = descUpdate?.value ?? parsed.description ?? '';
        const confidence = descUpdate?.confidence ?? 'MED';
        const requiresConfirmation = descUpdate?.requiresConfirmation ?? confidence !== 'HIGH';

        suggestedDraftUpdates.push({
          field: 'description',
          value: description,
          confidence,
          requiresConfirmation,
        });

        if (confidence === 'LOW') {
          overallConfidence = 'LOW';
        }
      }

      // Build evidence from resolved attributes
      const evidence: EvidenceBullet[] = [];
      const suggestedAttributes: SuggestedAttribute[] = [];

      if (primaryItem) {
        const resolved = resolvedAttributesMap.get(primaryItem.itemId);
        if (resolved) {
          // Add evidence bullets
          if (resolved.brand) {
            evidence.push({
              type: resolved.brand.evidenceRefs[0]?.type === 'logo' ? 'logo' : 'ocr',
              text: `Brand: ${resolved.brand.value} (${resolved.brand.evidenceRefs[0]?.type ?? 'detected'})`,
            });
            suggestedAttributes.push({
              key: 'brand',
              value: resolved.brand.value,
              confidence: resolved.brand.confidence,
              source: resolved.brand.evidenceRefs[0]?.type,
            });
          }

          if (resolved.model) {
            evidence.push({
              type: 'ocr',
              text: `Model: ${resolved.model.value}`,
            });
            suggestedAttributes.push({
              key: 'model',
              value: resolved.model.value,
              confidence: resolved.model.confidence,
              source: resolved.model.evidenceRefs[0]?.type,
            });
          }

          if (resolved.color) {
            evidence.push({
              type: 'color',
              text: `Color: ${resolved.color.value}`,
            });
            suggestedAttributes.push({
              key: 'color',
              value: resolved.color.value,
              confidence: resolved.color.confidence,
              source: 'color',
            });
          }

          if (resolved.material) {
            evidence.push({
              type: 'label',
              text: `Material: ${resolved.material.value}`,
            });
            suggestedAttributes.push({
              key: 'material',
              value: resolved.material.value,
              confidence: resolved.material.confidence,
              source: 'label',
            });
          }
        }
      }

      // Add missing info to warnings
      if (parsed.missingInfo && parsed.missingInfo.length > 0) {
        for (const missing of parsed.missingInfo.slice(0, 5)) {
          warnings.push(`Missing: ${missing}`);
        }
      }

      // Build actions
      const actions: AssistantAction[] = [];

      // Add apply draft update action if we have title/description
      if (primaryItem && suggestedDraftUpdates.length > 0) {
        const payload: Record<string, string> = { itemId: primaryItem.itemId };
        const titleUpdate = suggestedDraftUpdates.find(u => u.field === 'title');
        const descUpdate = suggestedDraftUpdates.find(u => u.field === 'description');

        if (titleUpdate) {
          payload.title = titleUpdate.value;
        }
        if (descUpdate) {
          payload.description = descUpdate.value;
        }

        const hasLowConfidence = suggestedDraftUpdates.some(u => u.confidence === 'LOW');

        actions.push({
          type: 'APPLY_DRAFT_UPDATE',
          payload,
          label: 'Apply suggested listing',
          requiresConfirmation: hasLowConfidence,
        });

        // Add copy actions
        if (titleUpdate) {
          actions.push({
            type: 'COPY_TEXT',
            payload: { label: 'Title', text: titleUpdate.value },
            label: 'Copy title',
          });
        }
        if (descUpdate) {
          actions.push({
            type: 'COPY_TEXT',
            payload: { label: 'Description', text: descUpdate.value },
            label: 'Copy description',
          });
        }
      }

      // Add suggested next photo action if needed
      if (parsed.suggestedNextPhoto) {
        actions.push({
          type: 'SUGGEST_NEXT_PHOTO',
          payload: { instruction: parsed.suggestedNextPhoto },
          label: 'Photo suggestion',
        });
      }

      // Build response content
      // IMPORTANT: Never return raw JSON in content field when structured data exists
      // to prevent JSON leakage in the UI
      let content = '';

      // If we have structured draft updates, create a clean summary for display
      if (suggestedDraftUpdates.length > 0 || parsed.title || parsed.description) {
        const parts: string[] = [];

        const titleValue = parsed.suggestedDraftUpdates?.find(u => u.field === 'title')?.value ?? parsed.title;
        const descValue = parsed.suggestedDraftUpdates?.find(u => u.field === 'description')?.value ?? parsed.description;

        if (titleValue) {
          parts.push(`**Suggested Title:**\n${titleValue}`);
        }

        if (descValue) {
          parts.push(`**Suggested Description:**\n${descValue}`);
        }

        if (warnings.length > 0) {
          parts.push(`**Please Verify:**\n${warnings.map(w => `- ${w}`).join('\n')}`);
        }

        content = parts.length > 0 ? parts.join('\n\n') : '';
      } else {
        // Fallback: use cleaned text content only if no structured data
        content = textContent;
      }

      return {
        content,
        actions: actions.length > 0 ? actions : undefined,
        confidenceTier: overallConfidence,
        evidence: evidence.length > 0 ? evidence : undefined,
        suggestedAttributes: suggestedAttributes.length > 0 ? suggestedAttributes : undefined,
        suggestedDraftUpdates: suggestedDraftUpdates.length > 0 ? suggestedDraftUpdates : undefined,
        suggestedNextPhoto: parsed.suggestedNextPhoto ?? undefined,
      };
    } catch (error) {
      // Log error details for debugging
      const errorMessage = error instanceof Error ? error.message : String(error);

      // Return graceful error response
      return {
        content: 'I encountered an issue generating the listing. Please try again or provide more details about the item.',
        assistantError: {
          type: 'provider_unavailable',
          category: 'temporary',
          retryable: true,
          message: errorMessage,
          reasonCode: 'PROVIDER_ERROR',
        },
      };
    }
  }
}
