import type { AssistantReadiness, ProviderState } from './types.js';

/**
 * Registry for sharing assistant readiness status across modules.
 * This allows the health endpoint to report assistant readiness
 * without tight coupling between modules.
 */
class AssistantReadinessRegistry {
  private readinessProvider: (() => AssistantReadiness) | null = null;

  /**
   * Register the readiness provider (called by assistant routes on startup).
   */
  register(provider: () => AssistantReadiness): void {
    this.readinessProvider = provider;
  }

  /**
   * Unregister the readiness provider (called on shutdown).
   */
  unregister(): void {
    this.readinessProvider = null;
  }

  /**
   * Get current assistant readiness status.
   * Returns a default "not registered" status if provider not yet registered.
   */
  getReadiness(): AssistantReadiness {
    if (!this.readinessProvider) {
      return {
        providerConfigured: false,
        providerReachable: false,
        state: 'DISABLED' as ProviderState,
        providerType: 'unknown',
        lastSuccessAt: null,
        lastErrorAt: null,
      };
    }
    return this.readinessProvider();
  }

  /**
   * Check if the registry has a provider registered.
   */
  isRegistered(): boolean {
    return this.readinessProvider !== null;
  }
}

/**
 * Singleton instance of the readiness registry.
 */
export const assistantReadinessRegistry = new AssistantReadinessRegistry();
