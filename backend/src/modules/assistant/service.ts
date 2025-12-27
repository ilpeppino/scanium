import { AssistantProvider } from './provider.js';
import {
  AssistantChatRequest,
  AssistantChatRequestWithVision,
  AssistantResponse,
  ProviderState,
  AssistantReadiness,
} from './types.js';
import { refusalResponse, sanitizeActions, shouldRefuse } from './safety.js';
import { CircuitBreaker } from '../../infra/resilience/circuit-breaker.js';

type AssistantServiceOptions = {
  breaker: CircuitBreaker;
  retries: number;
  /** Provider type from config (mock, openai, disabled) */
  providerType: string;
};

export class AssistantService {
  /** Timestamp of last successful provider call */
  private lastSuccessAt: Date | null = null;
  /** Timestamp of last provider error */
  private lastErrorAt: Date | null = null;

  constructor(
    private readonly provider: AssistantProvider,
    private readonly options: AssistantServiceOptions
  ) {}

  /**
   * Get the current provider state based on configuration and circuit breaker.
   */
  getProviderState(): ProviderState {
    // Check if provider is disabled by configuration
    if (this.options.providerType === 'disabled') {
      return 'DISABLED';
    }

    // Check if circuit breaker is open (provider unreachable)
    if (!this.options.breaker.canRequest()) {
      return 'ERROR';
    }

    return 'ENABLED';
  }

  /**
   * Get full readiness status for health check responses.
   */
  getReadiness(): AssistantReadiness {
    const state = this.getProviderState();
    const providerConfigured = this.options.providerType !== 'disabled';
    const providerReachable = state === 'ENABLED';

    return {
      providerConfigured,
      providerReachable,
      state,
      providerType: this.options.providerType,
      lastSuccessAt: this.lastSuccessAt?.toISOString() ?? null,
      lastErrorAt: this.lastErrorAt?.toISOString() ?? null,
    };
  }

  /**
   * Check if the provider is currently available for requests.
   */
  isAvailable(): boolean {
    return this.getProviderState() === 'ENABLED';
  }

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
        assistantError: {
          type: 'provider_unavailable',
          category: 'temporary',
          retryable: true,
          reasonCode: 'PROVIDER_UNAVAILABLE',
          message: 'Circuit breaker is open',
        },
      };
    }

    let lastError: unknown;
    for (let attempt = 0; attempt <= this.options.retries; attempt++) {
      try {
        const response = await this.provider.respond(request);
        this.options.breaker.recordSuccess();
        this.lastSuccessAt = new Date();
        return response;
      } catch (error) {
        lastError = error;
        this.lastErrorAt = new Date();
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
      assistantError: {
        type: 'provider_unavailable',
        category: 'temporary',
        retryable: true,
        reasonCode: 'PROVIDER_ERROR',
        message: lastError instanceof Error ? lastError.message : 'Provider call failed',
      },
    };
  }
}
