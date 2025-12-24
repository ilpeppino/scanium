/**
 * Daily quota store for tracking assistant API usage per session/user.
 * Uses in-memory storage with automatic daily reset.
 *
 * For production with multiple instances, consider using Redis or a database.
 */

type QuotaEntry = {
  count: number;
  resetAt: number; // Unix timestamp for next reset
};

type QuotaResult = {
  allowed: boolean;
  remaining: number;
  resetAt: number;
};

function getNextResetTimestamp(): number {
  const now = new Date();
  // Reset at midnight UTC
  const resetDate = new Date(now);
  resetDate.setUTCHours(24, 0, 0, 0);
  return resetDate.getTime();
}

export class DailyQuotaStore {
  private readonly store = new Map<string, QuotaEntry>();
  private readonly dailyLimit: number;
  private readonly cleanupIntervalMs: number;
  private cleanupTimer?: ReturnType<typeof setInterval>;

  constructor(options: { dailyLimit: number; cleanupIntervalMs?: number }) {
    this.dailyLimit = options.dailyLimit;
    this.cleanupIntervalMs = options.cleanupIntervalMs ?? 60 * 60 * 1000; // Default: 1 hour
    this.startCleanupTimer();
  }

  /**
   * Check if a key is within quota and consume one unit if allowed.
   * @param key - The session ID, user ID, or device hash
   * @returns Whether the request is allowed and remaining quota
   */
  consume(key: string): QuotaResult {
    const now = Date.now();
    const entry = this.store.get(key);

    // Check if entry exists and hasn't expired
    if (entry && entry.resetAt > now) {
      if (entry.count >= this.dailyLimit) {
        return {
          allowed: false,
          remaining: 0,
          resetAt: entry.resetAt,
        };
      }
      entry.count++;
      return {
        allowed: true,
        remaining: this.dailyLimit - entry.count,
        resetAt: entry.resetAt,
      };
    }

    // Create new entry or reset expired entry
    const resetAt = getNextResetTimestamp();
    this.store.set(key, { count: 1, resetAt });
    return {
      allowed: true,
      remaining: this.dailyLimit - 1,
      resetAt,
    };
  }

  /**
   * Check remaining quota without consuming.
   */
  check(key: string): QuotaResult {
    const now = Date.now();
    const entry = this.store.get(key);

    if (!entry || entry.resetAt <= now) {
      return {
        allowed: true,
        remaining: this.dailyLimit,
        resetAt: getNextResetTimestamp(),
      };
    }

    return {
      allowed: entry.count < this.dailyLimit,
      remaining: Math.max(0, this.dailyLimit - entry.count),
      resetAt: entry.resetAt,
    };
  }

  /**
   * Get current usage for a key (for logging/monitoring).
   */
  getUsage(key: string): { used: number; limit: number; resetAt: number } {
    const now = Date.now();
    const entry = this.store.get(key);

    if (!entry || entry.resetAt <= now) {
      return {
        used: 0,
        limit: this.dailyLimit,
        resetAt: getNextResetTimestamp(),
      };
    }

    return {
      used: entry.count,
      limit: this.dailyLimit,
      resetAt: entry.resetAt,
    };
  }

  /**
   * Clean up expired entries to prevent memory growth.
   */
  cleanup(): number {
    const now = Date.now();
    let removed = 0;

    for (const [key, entry] of this.store.entries()) {
      if (entry.resetAt <= now) {
        this.store.delete(key);
        removed++;
      }
    }

    return removed;
  }

  /**
   * Start periodic cleanup of expired entries.
   */
  private startCleanupTimer(): void {
    if (this.cleanupTimer) return;
    this.cleanupTimer = setInterval(() => {
      this.cleanup();
    }, this.cleanupIntervalMs);
    // Don't prevent process exit
    if (this.cleanupTimer.unref) {
      this.cleanupTimer.unref();
    }
  }

  /**
   * Stop the cleanup timer (for graceful shutdown).
   */
  stop(): void {
    if (this.cleanupTimer) {
      clearInterval(this.cleanupTimer);
      this.cleanupTimer = undefined;
    }
  }

  /**
   * Get the current size of the store (for monitoring).
   */
  size(): number {
    return this.store.size;
  }

  /**
   * Clear all entries (for testing).
   */
  clear(): void {
    this.store.clear();
  }
}
