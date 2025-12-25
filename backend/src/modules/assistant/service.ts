import { AssistantProvider } from './provider.js';
import { AssistantChatRequest, AssistantChatRequestWithVision, AssistantResponse } from './types.js';
import { refusalResponse, sanitizeActions, shouldRefuse } from './safety.js';
import { CircuitBreaker } from '../../infra/resilience/circuit-breaker.js';

type AssistantServiceOptions = {
  breaker: CircuitBreaker;
  retries: number;
};

export class AssistantService {
  constructor(
    private readonly provider: AssistantProvider,
    private readonly options: AssistantServiceOptions
  ) {}

  async respond(request: AssistantChatRequest | AssistantChatRequestWithVision): Promise<AssistantResponse> {
    if (shouldRefuse(request.message || '')) {
      return refusalResponse();
    }

    const response = await this.callWithRetry(request);
    return {
      content: response.content,
      actions: sanitizeActions(response.actions),
      citationsMetadata: response.citationsMetadata,
      confidenceTier: response.confidenceTier,
      evidence: response.evidence,
      suggestedAttributes: response.suggestedAttributes,
      suggestedDraftUpdates: response.suggestedDraftUpdates,
      suggestedNextPhoto: response.suggestedNextPhoto,
    };
  }

  private async callWithRetry(request: AssistantChatRequest | AssistantChatRequestWithVision): Promise<AssistantResponse> {
    if (!this.options.breaker.canRequest()) {
      return {
        content:
          'Assistant provider is temporarily unavailable. I can still help with general listing guidance.',
        actions: [],
        citationsMetadata: { providerUnavailable: 'true' },
      };
    }

    let lastError: unknown;
    for (let attempt = 0; attempt <= this.options.retries; attempt++) {
      try {
        const response = await this.provider.respond(request);
        this.options.breaker.recordSuccess();
        return response;
      } catch (error) {
        lastError = error;
        this.options.breaker.recordFailure();
        if (attempt >= this.options.retries) {
          break;
        }
        const jitter = 1 + Math.random() * 0.3;
        const delayMs = Math.min(800, 200 * Math.pow(2, attempt)) * jitter;
        await new Promise((resolve) => setTimeout(resolve, delayMs));
      }
    }

    return {
      content:
        'Assistant provider is temporarily unavailable. I can still help with general listing guidance.',
      actions: [],
      citationsMetadata: {
        providerUnavailable: 'true',
        error: lastError instanceof Error ? lastError.message : 'unknown',
      },
    };
  }
}
