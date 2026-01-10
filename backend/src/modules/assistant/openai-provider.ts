import OpenAI from 'openai';
import { randomBytes } from 'node:crypto';
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
import {
  recordOpenAIRequest,
  recordOpenAITokens,
  updateRateLimitState,
} from '../../infra/telemetry/openai-metrics.js';

export interface OpenAIProviderConfig {
  apiKey: string;
  model: string;
  maxTokens: number;
  timeoutMs: number;
}

/**
 * Generates a new span ID for W3C trace context.
 * Format: 8 bytes (16 hex chars)
 */
function generateSpanId(): string {
  return randomBytes(8).toString('hex');
}

/**
 * OpenAI-based assistant provider using the OpenAI API.
 *
 * Implements the AssistantProvider interface for real LLM-powered
 * listing generation with grounded responses based on visual evidence.
 */
export class OpenAIAssistantProvider implements AssistantProvider {
  private readonly client: OpenAI;
  private readonly model: string;
  private readonly maxTokens: number;

  constructor(config: OpenAIProviderConfig) {
    this.client = new OpenAI({
      apiKey: config.apiKey,
      timeout: config.timeoutMs,
    });
    this.model = config.model;
    this.maxTokens = config.maxTokens;
  }

  /**
   * Create a custom fetch function that injects W3C trace context headers.
   */
  private createTracingFetch(
    traceContext?: AssistantChatRequest['traceContext']
  ): typeof fetch | undefined {
    if (!traceContext) {
      return undefined; // Use default fetch
    }

    // Generate new span ID for OpenAI API call
    const openaiSpanId = generateSpanId();
    const traceparent = `00-${traceContext.traceId}-${openaiSpanId}-${traceContext.flags}`;

    // Return custom fetch that adds traceparent header
    return async (input: string | URL | Request, init?: RequestInit) => {
      const headers = new Headers(init?.headers);
      headers.set('traceparent', traceparent);

      return fetch(input, {
        ...init,
        headers,
      });
    };
  }

  async respond(request: AssistantChatRequest | AssistantChatRequestWithVision): Promise<AssistantResponse> {
    const visualRequest = request as AssistantChatRequestWithVision;
    const primaryItem = request.items[0];

    // Get the client to use (with trace context if available)
    const clientToUse = request.traceContext
      ? new OpenAI({
          apiKey: this.client.apiKey,
          timeout: this.client.timeout,
          fetch: this.createTracingFetch(request.traceContext),
        })
      : this.client;

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

    // Start timing for metrics
    const startTime = Date.now();

    // DEBUG: Log request start
    console.error('[OpenAI Provider] ===== STARTING OPENAI REQUEST =====');
    console.error('[OpenAI Provider] Model:', this.model);
    console.error('[OpenAI Provider] CorrelationId:', request.correlationId);

    try {
      console.error('[OpenAI Provider] Calling OpenAI API...');
      const response = await clientToUse.chat.completions.create({
        model: this.model,
        max_tokens: this.maxTokens,
        messages: [
          { role: 'system', content: systemPrompt },
          { role: 'user', content: fullUserMessage },
        ],
        // Enforce strict JSON-only output using OpenAI Structured Outputs
        // This ensures no prose appears before/after JSON, preventing UI leakage
        response_format: {
          type: 'json_object',
        },
      });

      console.error('[OpenAI Provider] API call completed successfully');
      console.error('[OpenAI Provider] Response status:', response.id ? 'OK' : 'UNKNOWN');

      // Calculate duration
      const durationSeconds = (Date.now() - startTime) / 1000;

      // Record successful request metrics
      recordOpenAIRequest(
        {
          model: this.model,
          status: 'success',
        },
        durationSeconds
      );

      // Record token usage if available
      if (response.usage) {
        recordOpenAITokens({
          model: this.model,
          inputTokens: response.usage.prompt_tokens || 0,
          outputTokens: response.usage.completion_tokens || 0,
          totalTokens: response.usage.total_tokens || 0,
        });
      }

      // Update rate limit state from headers if available
      // Note: OpenAI SDK may expose these via response object or headers
      // This is a placeholder - adjust based on actual SDK implementation
      const rateLimitHeaders = (response as any).headers;
      if (rateLimitHeaders) {
        const remainingRequests = rateLimitHeaders['x-ratelimit-remaining-requests'];
        const remainingTokens = rateLimitHeaders['x-ratelimit-remaining-tokens'];

        if (remainingRequests !== undefined || remainingTokens !== undefined) {
          updateRateLimitState({
            model: this.model,
            remainingRequests: remainingRequests ? parseInt(remainingRequests, 10) : undefined,
            remainingTokens: remainingTokens ? parseInt(remainingTokens, 10) : undefined,
          });
        }
      }

      // Extract text content from response
      const textContent = response.choices[0]?.message?.content ?? '';

      // DEBUG: Log the raw AI response
      console.error('[OpenAI Provider] Raw AI response (FULL):', textContent);
      console.error('[OpenAI Provider] Response length:', textContent.length);

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

      // DEBUG: Log parsed results
      console.error('[OpenAI Provider] Parsed title:', parsed.title);
      console.error('[OpenAI Provider] Parsed description length:', parsed.description?.length ?? 0);
      console.error('[OpenAI Provider] Parsed suggestedDraftUpdates:', parsed.suggestedDraftUpdates?.length ?? 0);

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
      // Calculate duration for error case
      const durationSeconds = (Date.now() - startTime) / 1000;

      // DEBUG: Log the actual error
      console.error('[OpenAI Provider] Error generating listing:', error);
      console.error('[OpenAI Provider] Error type:', typeof error);
      console.error('[OpenAI Provider] Error constructor:', error?.constructor?.name);

      // Map OpenAI errors to domain error structure
      const openaiError = error as { status?: number; code?: string; message?: string };
      const errorMessage = openaiError.message ?? String(error);
      const statusCode = openaiError.status;

      // Determine error type and category
      let errorType: 'provider_unavailable' | 'rate_limited' | 'unauthorized' = 'provider_unavailable';
      let category: 'temporary' | 'auth' | 'policy' = 'temporary';
      let retryable = true;
      let reasonCode: 'PROVIDER_UNAVAILABLE' | 'RATE_LIMITED' | 'UNAUTHORIZED' | 'PROVIDER_ERROR' = 'PROVIDER_ERROR';
      let retryAfterSeconds: number | undefined;

      if (statusCode === 401 || openaiError.code === 'invalid_api_key') {
        errorType = 'unauthorized';
        category = 'auth';
        retryable = false;
        reasonCode = 'UNAUTHORIZED';
      } else if (statusCode === 429) {
        errorType = 'rate_limited';
        category = 'policy';
        retryable = true;
        reasonCode = 'RATE_LIMITED';
        retryAfterSeconds = 60; // Default retry after for rate limits
      } else if (statusCode === 503 || statusCode === 502 || statusCode === 500) {
        errorType = 'provider_unavailable';
        category = 'temporary';
        retryable = true;
        reasonCode = 'PROVIDER_UNAVAILABLE';
      }

      // Record error metrics
      recordOpenAIRequest(
        {
          model: this.model,
          status: 'error',
          errorType: reasonCode,
        },
        durationSeconds
      );

      // Return graceful error response
      return {
        content: 'I encountered an issue generating the listing. Please try again or provide more details about the item.',
        assistantError: {
          type: errorType,
          category,
          retryable,
          retryAfterSeconds,
          message: errorMessage,
          reasonCode,
        },
      };
    }
  }
}
