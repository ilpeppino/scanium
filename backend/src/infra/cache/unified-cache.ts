/**
 * Unified Cache Layer
 *
 * Provides:
 * - In-memory caching with TTL
 * - Request deduplication (in-flight coalescing)
 * - Usage accounting (cache hits/misses)
 * - Pluggable for production (Redis, etc.)
 */

import { createHash } from 'crypto';

/**
 * Cache statistics for monitoring.
 */
export type CacheStats = {
  hits: number;
  misses: number;
  coalescedRequests: number;
  evictions: number;
  size: number;
};

/**
 * Cache entry with metadata.
 */
type CacheEntry<T> = {
  value: T;
  expiresAt: number;
  insertedAt: number;
  accessCount: number;
};

/**
 * In-flight request for deduplication.
 */
type InFlightRequest<T> = {
  promise: Promise<T>;
  startedAt: number;
  waiterCount: number;
};

/**
 * Cache options.
 */
export type UnifiedCacheOptions = {
  /** Time-to-live in milliseconds */
  ttlMs: number;
  /** Maximum number of entries */
  maxEntries: number;
  /** Cache name for logging */
  name: string;
  /** Enable request deduplication */
  enableDedup: boolean;
};

/**
 * Cache usage event for accounting callbacks.
 */
export type CacheUsageEvent = {
  type: 'hit' | 'miss' | 'coalesced' | 'eviction' | 'error';
  cacheKey: string;
  cacheName: string;
  metadata?: Record<string, string | number>;
};

/**
 * Usage accounting callback.
 */
export type UsageCallback = (event: CacheUsageEvent) => void;

/**
 * Unified cache with deduplication and accounting.
 */
export class UnifiedCache<T> {
  private readonly store = new Map<string, CacheEntry<T>>();
  private readonly inFlight = new Map<string, InFlightRequest<T>>();
  private readonly stats: CacheStats = {
    hits: 0,
    misses: 0,
    coalescedRequests: 0,
    evictions: 0,
    size: 0,
  };
  private cleanupTimer?: NodeJS.Timeout;
  private usageCallback?: UsageCallback;

  constructor(private readonly options: UnifiedCacheOptions) {
    // Periodic cleanup
    this.cleanupTimer = setInterval(() => {
      this.cleanup();
    }, Math.min(this.options.ttlMs / 2, 300000));
  }

  /**
   * Set usage accounting callback.
   */
  setUsageCallback(callback: UsageCallback): void {
    this.usageCallback = callback;
  }

  /**
   * Get a cached value.
   */
  get(key: string): T | null {
    const entry = this.store.get(key);
    if (!entry) {
      this.stats.misses++;
      this.emitUsage('miss', key);
      return null;
    }

    if (entry.expiresAt <= Date.now()) {
      this.store.delete(key);
      this.stats.misses++;
      this.stats.size = this.store.size;
      this.emitUsage('miss', key);
      return null;
    }

    entry.accessCount++;
    this.stats.hits++;
    this.emitUsage('hit', key);
    return entry.value;
  }

  /**
   * Store a value.
   */
  set(key: string, value: T): void {
    const now = Date.now();
    this.store.set(key, {
      value,
      insertedAt: now,
      expiresAt: now + this.options.ttlMs,
      accessCount: 0,
    });
    this.stats.size = this.store.size;
    this.prune();
  }

  /**
   * Check if key exists (without counting as access).
   */
  has(key: string): boolean {
    const entry = this.store.get(key);
    if (!entry) return false;
    if (entry.expiresAt <= Date.now()) {
      this.store.delete(key);
      this.stats.size = this.store.size;
      return false;
    }
    return true;
  }

  /**
   * Delete a specific key.
   */
  delete(key: string): boolean {
    const deleted = this.store.delete(key);
    this.stats.size = this.store.size;
    return deleted;
  }

  /**
   * Clear all entries.
   */
  clear(): void {
    this.store.clear();
    this.stats.size = 0;
  }

  /**
   * Get or compute with deduplication.
   *
   * If the same key is being computed, waits for the in-flight request
   * instead of launching a duplicate computation.
   */
  async getOrCompute(key: string, compute: () => Promise<T>): Promise<T> {
    // Check cache first
    const cached = this.get(key);
    if (cached !== null) {
      return cached;
    }

    // Check for in-flight request (deduplication)
    if (this.options.enableDedup) {
      const inFlight = this.inFlight.get(key);
      if (inFlight) {
        inFlight.waiterCount++;
        this.stats.coalescedRequests++;
        this.emitUsage('coalesced', key, { waiterCount: inFlight.waiterCount });
        return inFlight.promise;
      }
    }

    // Start new computation
    const promise = this.computeAndCache(key, compute);

    if (this.options.enableDedup) {
      this.inFlight.set(key, {
        promise,
        startedAt: Date.now(),
        waiterCount: 1,
      });
    }

    try {
      return await promise;
    } finally {
      this.inFlight.delete(key);
    }
  }

  /**
   * Get cache statistics.
   */
  getStats(): CacheStats {
    return { ...this.stats, size: this.store.size };
  }

  /**
   * Stop cleanup timer.
   */
  stop(): void {
    if (this.cleanupTimer) {
      clearInterval(this.cleanupTimer);
      this.cleanupTimer = undefined;
    }
  }

  private async computeAndCache(key: string, compute: () => Promise<T>): Promise<T> {
    try {
      const value = await compute();
      this.set(key, value);
      return value;
    } catch (error) {
      this.emitUsage('error', key);
      throw error;
    }
  }

  private cleanup(): void {
    const now = Date.now();
    for (const [key, entry] of this.store.entries()) {
      if (entry.expiresAt <= now) {
        this.store.delete(key);
        this.stats.evictions++;
        this.emitUsage('eviction', key);
      }
    }
    this.stats.size = this.store.size;
  }

  private prune(): void {
    if (this.store.size <= this.options.maxEntries) return;

    // LRU-style eviction based on access count and age
    const entries = [...this.store.entries()].sort((a, b) => {
      // Prefer evicting entries with fewer accesses
      if (a[1].accessCount !== b[1].accessCount) {
        return a[1].accessCount - b[1].accessCount;
      }
      // Then by age (older first)
      return a[1].insertedAt - b[1].insertedAt;
    });

    const toRemove = entries.slice(0, this.store.size - this.options.maxEntries);
    for (const [key] of toRemove) {
      this.store.delete(key);
      this.stats.evictions++;
      this.emitUsage('eviction', key);
    }
    this.stats.size = this.store.size;
  }

  private emitUsage(
    type: 'hit' | 'miss' | 'coalesced' | 'eviction' | 'error',
    cacheKey: string,
    metadata?: Record<string, string | number>
  ): void {
    if (this.usageCallback) {
      this.usageCallback({
        type,
        cacheKey,
        cacheName: this.options.name,
        metadata,
      });
    }
  }
}

/**
 * Build a cache key from components.
 *
 * @param components - Key components to hash
 * @returns Cache key string
 */
export function buildCacheKey(...components: (string | number | undefined)[]): string {
  const normalized = components
    .filter((c) => c !== undefined)
    .map((c) => String(c))
    .join(':');
  return createHash('sha256').update(normalized).digest('hex').slice(0, 32);
}

/**
 * Build an item snapshot hash for cache key.
 *
 * @param item - Item context snapshot
 * @returns Hash string
 */
export function buildItemSnapshotHash(item: {
  itemId: string;
  title?: string | null;
  category?: string | null;
  attributes?: Array<{ key: string; value: string }>;
}): string {
  const parts = [
    item.itemId,
    item.title ?? '',
    item.category ?? '',
    ...(item.attributes ?? []).map((a) => `${a.key}:${a.value}`).sort(),
  ];
  return buildCacheKey(...parts);
}

/**
 * Normalize a question for cache key.
 *
 * @param question - User question
 * @returns Normalized question
 */
export function normalizeQuestion(question: string): string {
  return question
    .toLowerCase()
    .trim()
    .replace(/\s+/g, ' ')
    .replace(/[^\w\s]/g, '');
}

/**
 * Build an assistant response cache key.
 *
 * @param params - Cache key parameters
 * @returns Cache key string
 */
export function buildAssistantCacheKey(params: {
  promptVersion: string;
  question: string;
  itemSnapshotHash: string;
  imageHashes: string[];
  providerId: string;
}): string {
  const normalizedQ = normalizeQuestion(params.question);
  const sortedImageHashes = [...params.imageHashes].sort().join(':');
  return buildCacheKey(
    'assist',
    params.promptVersion,
    normalizedQ,
    params.itemSnapshotHash,
    sortedImageHashes,
    params.providerId
  );
}

/**
 * Build a vision facts cache key.
 *
 * @param params - Cache key parameters
 * @returns Cache key string
 */
export function buildVisionCacheKey(params: {
  featureVersion: string;
  imageHashes: string[];
  mode?: string;
}): string {
  const sortedImageHashes = [...params.imageHashes].sort().join(':');
  return buildCacheKey('vision', params.featureVersion, sortedImageHashes, params.mode ?? 'default');
}
