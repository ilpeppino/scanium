export type CircuitBreakerState = 'closed' | 'open' | 'half-open';

export type CircuitBreakerOptions = {
  failureThreshold: number;
  cooldownMs: number;
  minimumRequests: number;
};

export class CircuitBreaker {
  private failures = 0;
  private successes = 0;
  private openedAt: number | null = null;

  constructor(private readonly options: CircuitBreakerOptions) {}

  getState(now: number = Date.now()): CircuitBreakerState {
    if (this.openedAt === null) {
      return 'closed';
    }
    if (now - this.openedAt >= this.options.cooldownMs) {
      return 'half-open';
    }
    return 'open';
  }

  canRequest(now: number = Date.now()): boolean {
    const state = this.getState(now);
    return state === 'closed' || state === 'half-open';
  }

  recordSuccess(): void {
    this.successes += 1;
    if (this.getState() === 'half-open') {
      this.reset();
    }
  }

  recordFailure(): void {
    this.failures += 1;
    const total = this.failures + this.successes;
    if (total < this.options.minimumRequests) {
      return;
    }
    if (this.failures >= this.options.failureThreshold) {
      this.openedAt = Date.now();
    }
  }

  reset(): void {
    this.failures = 0;
    this.successes = 0;
    this.openedAt = null;
  }
}
