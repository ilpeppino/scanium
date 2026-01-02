import { createHash } from 'crypto';

export interface CacheOptions {
  ttlMs: number;
  maxEntries: number;
  name: string;
  enableDedup?: boolean;
}

export interface CacheStats {
  size: number;
  hits: number;
  misses: number;
  coalescedRequests: number;
  evictions: number;
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
    hits: 0,
    misses: 0,
    coalescedRequests: 0,
    evictions: 0,
  };

  private cleanupInterval?: ReturnType<typeof setInterval>;

  constructor(options: CacheOptions) {
    this.ttlMs = options.ttlMs;
    this.maxEntries = options.maxEntries;
    this.name = options.name;
    this.enableDedup = options.enableDedup ?? false;

    // Periodic cleanup of expired entries (1 min)
    this.cleanupInterval = setInterval(() => this.cleanup(), 60000);
  }

  setUsageCallback(callback: UsageCallback): void {
    this.usageCallback = callback;
  }

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
  }

  private cleanup(): void {
    const now = Date.now();
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
    // Simple eviction: evict the first (oldest-in-map) entry
    const firstKey = this.cache.keys().next().value as string | undefined;
    if (firstKey) {
      this.cache.delete(firstKey);
      this.stats.evictions++;
      this.usageCallback?.({
        type: 'eviction',
        cacheName: this.name,
        cacheKey: firstKey,
      });
    }
  }
}

/**
 * Generic stable cache key builder (sha256 hex, optionally shortened).
 */
export function buildCacheKey(
  parts: Array<string | number | undefined | null>,
  opts?: { prefix?: string; length?: number }
): string {
  const normalized = parts
    .filter((p) => p !== undefined && p !== null)
    .map((p) => String(p))
    .join('|');

  const base = (opts?.prefix ? `${opts.prefix}|` : '') + normalized;
  const hex = createHash('sha256').update(base).digest('hex');
  return typeof opts?.length === 'number' ? hex.slice(0, opts.length) : hex;
}

/**
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
  return createHash('sha256').update(parts.join('|')).digest('hex').slice(0, 16);
}

/**
 * Normalize a question for cache key generation.
 */
export function normalizeQuestion(question: string): string {
  return question
    .toLowerCase()
    .trim()
    .replace(/\s+/g, ' ')
    .replace(/[^\w\s]/g, '');
}

/**
 * Build a vision facts cache key.
 * (kept from your branch: used for OCR / color / logo / vision pipelines)
 */
export function buildVisionCacheKey(params: {
  featureVersion: string;
  imageHashes: string[];
  mode?: string;
}): string {
  const sortedImageHashes = [...params.imageHashes].sort().join('|');
  return buildCacheKey(
    ['vision', params.featureVersion, sortedImageHashes, params.mode ?? 'default'],
    { length: 32 }
  );
}