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
  type: 'hit' | 'miss' | 'eviction' | 'set' | 'coalesced' | 'error';
  cacheName: string;
  cacheKey: string;
  metadata?: Record<string, unknown>;
}

// Backward-compat alias for older imports
export type CacheUsageEvent = CacheEvent;


export type UsageCallback = (event: CacheEvent) => void;

interface CacheEntry<T> {
  value: T;
  expiresAt: number;
}

/**
 * Simple in-memory cache with TTL and optional request deduplication.
 *
 * Notes:
 * - `get()` returns `undefined` when missing/expired (origin/main behavior).
 * - `getOrCompute()` can deduplicate in-flight computations when enableDedup=true.
 * - Periodic cleanup removes expired entries.
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

    // Periodic cleanup of expired entries (every 60s)
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

    // If present but expired, delete it (counts as miss)
    if (cached && cached.expiresAt <= Date.now()) {
      this.cache.delete(key);
      this.stats.size = this.cache.size;
    }

    this.stats.misses++;
    this.usageCallback?.({
      type: 'miss',
      cacheName: this.name,
      cacheKey: key,
    });

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
    // Check cache first (hit path accounted in get() already)
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
      this.usageCallback?.({
        type: 'coalesced',
        cacheName: this.name,
        cacheKey: key,
      });
      return this.pending.get(key)!;
    }

    this.stats.misses++;
    this.usageCallback?.({
      type: 'miss',
      cacheName: this.name,
      cacheKey: key,
    });

    const computePromise = (async () => {
      try {
        const value = await compute();

        // Evict if at capacity (again, in case cache filled while computing)
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

        return value;
      } catch (error) {
        this.usageCallback?.({
          type: 'error',
          cacheName: this.name,
          cacheKey: key,
          metadata: { error: compilerSafeToString(error) },
        });
        throw error;
      } finally {
        if (this.enableDedup) {
          this.pending.delete(key);
        }
      }
    })();

    if (this.enableDedup) {
      this.pending.set(key, computePromise);
    }

    return computePromise;
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
    this.stats.size = 0;
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
    const firstKey = this.cache.keys().next().value as string | undefined;
    if (!firstKey) return;

    this.cache.delete(firstKey);
    this.stats.evictions++;
    this.stats.size = this.cache.size;
    this.usageCallback?.({
      type: 'eviction',
      cacheName: this.name,
      cacheKey: firstKey,
    });
  }
}

/**
 * Internal helper to build stable cache keys from components.
 * (Used by buildVisionCacheKey; safe to keep private.)
 */
function buildCacheKey(...components: Array<string | number | undefined | null>): string {
  const normalized = components
    .filter((c): c is string | number => c !== undefined && c !== null)
    .map((c) => String(c))
    .join('|');
  // 32 hex chars is usually enough and keeps keys shorter
  return createHash('sha256').update(normalized).digest('hex').slice(0, 32);
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
    ...params.imageHashes.slice().sort(),
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
 * Keep this exported because parts of the assistant/vision pipeline may rely on it.
 */
export function buildVisionCacheKey(params: {
  featureVersion: string;
  imageHashes: string[];
  mode?: string;
}): string {
  const sortedImageHashes = params.imageHashes.slice().sort().join('|');
  return buildCacheKey('vision', params.featureVersion, sortedImageHashes, params.mode ?? 'default');
}

/**
 * Avoid leaking complex objects into logs; keep it compiler-safe.
 */
function compilerSafeToString(value: unknown): string {
  try {
    if (value instanceof Error) return value.message;
    return typeof value === 'string' ? value : JSON.stringify(value);
  } catch {
    return String(value);
  }
}
