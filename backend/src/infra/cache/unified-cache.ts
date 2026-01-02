import { createHash } from 'crypto';

export interface CacheOptions {
  ttlMs: number;
  maxEntries: number;
  name: string;
  enableDedup?: boolean;
}

export interface CacheStats {
  size: number;
>>>>>>> origin/main
  hits: number;
  misses: number;
  coalescedRequests: number;
  evictions: number;
<<<<<<< HEAD
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
=======
}

export interface CacheEvent {
  type: 'hit' | 'miss' | 'eviction' | 'set';
  cacheName: string;
  cacheKey: string;
  metadata?: Record<string, unknown>;
}

export type UsageCallback = (event: CacheEvent) => void;

interface CacheEntry<T> {
  value: T;
  expiresAt: number;
}

/**
 * Simple in-memory cache with TTL and deduplication support.
 */
export class UnifiedCache<T> {
  private readonly cache = new Map<string, CacheEntry<T>>();
  private readonly pending = new Map<string, Promise<T>>();
  private readonly ttlMs: number;
  private readonly maxEntries: number;
  private readonly name: string;
  private readonly enableDedup: boolean;
  private usageCallback?: UsageCallback;
  private stats: CacheStats = {
    size: 0,
>>>>>>> origin/main
    hits: 0,
    misses: 0,
    coalescedRequests: 0,
    evictions: 0,
<<<<<<< HEAD
    size: 0,
  };
  private cleanupTimer?: NodeJS.Timeout;
  private usageCallback?: UsageCallback;

  constructor(private readonly options: UnifiedCacheOptions) {
    // Periodic cleanup - ensure valid interval (default 5 min if NaN)
    const ttlMs = Number.isFinite(this.options.ttlMs) ? this.options.ttlMs : 600000;
    this.cleanupTimer = setInterval(() => {
      this.cleanup();
    }, Math.min(ttlMs / 2, 300000));
  }

  /**
   * Set usage accounting callback.
   */
=======
  };
  private cleanupInterval?: ReturnType<typeof setInterval>;

  constructor(options: CacheOptions) {
    this.ttlMs = options.ttlMs;
    this.maxEntries = options.maxEntries;
    this.name = options.name;
    this.enableDedup = options.enableDedup ?? false;

    // Periodic cleanup of expired entries
    this.cleanupInterval = setInterval(() => this.cleanup(), 60000);
  }

>>>>>>> origin/main
  setUsageCallback(callback: UsageCallback): void {
    this.usageCallback = callback;
  }

<<<<<<< HEAD
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
=======
  get(key: string): T | undefined {
    const cached = this.cache.get(key);
    if (cached && cached.expiresAt > Date.now()) {
      this.stats.hits++;
      this.usageCallback?.({
        type: 'hit',
        cacheName: this.name,
        cacheKey: key,
      });
      return cached.value;
    }
    return undefined;
  }

  set(key: string, value: T): void {
    // Evict if at capacity
    if (this.cache.size >= this.maxEntries) {
      this.evictOldest();
    }

    this.cache.set(key, {
      value,
      expiresAt: Date.now() + this.ttlMs,
    });
    this.stats.size = this.cache.size;
    this.usageCallback?.({
      type: 'set',
      cacheName: this.name,
      cacheKey: key,
    });
  }

  async getOrCompute(key: string, compute: () => Promise<T>): Promise<T> {
    // Check cache first
    const cached = this.cache.get(key);
    if (cached && cached.expiresAt > Date.now()) {
      this.stats.hits++;
      this.usageCallback?.({
        type: 'hit',
        cacheName: this.name,
        cacheKey: key,
      });
      return cached.value;
    }

    // Deduplication: if a request for this key is in flight, wait for it
    if (this.enableDedup && this.pending.has(key)) {
      this.stats.coalescedRequests++;
      return this.pending.get(key)!;
    }

    this.stats.misses++;
    this.usageCallback?.({
      type: 'miss',
      cacheName: this.name,
      cacheKey: key,
    });

    // Compute the value
    const computePromise = compute();

    if (this.enableDedup) {
      this.pending.set(key, computePromise);
    }

    try {
      const value = await computePromise;

      // Evict if at capacity
      if (this.cache.size >= this.maxEntries) {
        this.evictOldest();
      }

      // Store in cache
      this.cache.set(key, {
        value,
        expiresAt: Date.now() + this.ttlMs,
      });
      this.stats.size = this.cache.size;
      this.usageCallback?.({
        type: 'set',
        cacheName: this.name,
        cacheKey: key,
      });

      return value;
    } finally {
      if (this.enableDedup) {
        this.pending.delete(key);
      }
    }
  }

  getStats(): CacheStats {
    return { ...this.stats, size: this.cache.size };
  }

  stop(): void {
    if (this.cleanupInterval) {
      clearInterval(this.cleanupInterval);
      this.cleanupInterval = undefined;
    }
    this.cache.clear();
    this.pending.clear();
>>>>>>> origin/main
  }

  private cleanup(): void {
    const now = Date.now();
<<<<<<< HEAD
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
=======
    for (const [key, entry] of this.cache) {
      if (entry.expiresAt <= now) {
        this.cache.delete(key);
        this.stats.evictions++;
        this.usageCallback?.({
          type: 'eviction',
          cacheName: this.name,
          cacheKey: key,
        });
      }
    }
    this.stats.size = this.cache.size;
  }

  private evictOldest(): void {
    // Simple LRU-like: evict the first (oldest) entry
    const firstKey = this.cache.keys().next().value;
    if (firstKey) {
      this.cache.delete(firstKey);
      this.stats.evictions++;
      this.usageCallback?.({
        type: 'eviction',
        cacheName: this.name,
        cacheKey: firstKey,
>>>>>>> origin/main
      });
    }
  }
}

/**
<<<<<<< HEAD
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
=======
 * Build a cache key for assistant responses.
 */
export function buildAssistantCacheKey(params: {
  promptVersion: string;
  question: string;
  itemSnapshotHash: string;
  imageHashes: string[];
  providerId: string;
}): string {
  const normalized = normalizeQuestion(params.question);
  const parts = [
    params.promptVersion,
    params.providerId,
    normalized,
    params.itemSnapshotHash,
    ...params.imageHashes.sort(),
  ];
  return createHash('sha256').update(parts.join('|')).digest('hex');
}

/**
 * Build a hash for an item snapshot (for cache key generation).
>>>>>>> origin/main
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
<<<<<<< HEAD
  return buildCacheKey(...parts);
}

/**
 * Normalize a question for cache key.
 *
 * @param question - User question
 * @returns Normalized question
=======
  return createHash('sha256').update(parts.join('|')).digest('hex').slice(0, 16);
}

/**
 * Normalize a question for cache key generation.
>>>>>>> origin/main
 */
export function normalizeQuestion(question: string): string {
  return question
    .toLowerCase()
    .trim()
    .replace(/\s+/g, ' ')
    .replace(/[^\w\s]/g, '');
}
<<<<<<< HEAD

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
=======
>>>>>>> origin/main
