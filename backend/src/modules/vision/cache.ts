/**
 * VisualFacts Cache
 *
 * In-memory cache for vision extraction results.
 * Keyed by image hashes to avoid redundant Vision API calls.
 */

import { VisualFacts } from './types.js';

type CacheEntry = {
  value: VisualFacts;
  expiresAt: number;
  insertedAt: number;
};

export type VisualFactsCacheOptions = {
  /** Time-to-live in milliseconds */
  ttlMs: number;
  /** Maximum number of entries */
  maxEntries: number;
};

const DEFAULT_OPTIONS: VisualFactsCacheOptions = {
  ttlMs: 3600000, // 1 hour
  maxEntries: 500,
};

/**
 * Build a cache key from image hashes and optional context.
 *
 * @param imageHashes - SHA-256 hashes of images
 * @param context - Optional context (e.g., feature set version)
 * @returns Cache key string
 */
export function buildCacheKey(
  imageHashes: string[],
  context?: { featureVersion?: string; mode?: string }
): string {
  const sortedHashes = [...imageHashes].sort().join(':');
  const contextPart = context
    ? `:${context.featureVersion ?? 'v1'}:${context.mode ?? 'default'}`
    : ':v1:default';
  return `vf:${sortedHashes}${contextPart}`;
}

export class VisualFactsCache {
  private readonly store = new Map<string, CacheEntry>();
  private readonly options: VisualFactsCacheOptions;
  private cleanupTimer?: NodeJS.Timeout;

  constructor(options: Partial<VisualFactsCacheOptions> = {}) {
    this.options = { ...DEFAULT_OPTIONS, ...options };

    // Periodic cleanup to remove expired entries
    // Guard against NaN/undefined by using default
    const ttlMs = Number.isFinite(this.options.ttlMs) ? this.options.ttlMs : DEFAULT_OPTIONS.ttlMs;
    this.cleanupTimer = setInterval(() => {
      this.cleanup();
    }, Math.min(ttlMs / 2, 300000)); // Every half TTL or 5 min max
  }

  /**
   * Get a cached VisualFacts entry.
   *
   * @param key - Cache key
   * @returns VisualFacts if found and not expired, null otherwise
   */
  get(key: string): VisualFacts | null {
    const entry = this.store.get(key);
    if (!entry) return null;

    if (entry.expiresAt <= Date.now()) {
      this.store.delete(key);
      return null;
    }

    // Mark as cache hit in the returned facts
    return {
      ...entry.value,
      extractionMeta: {
        ...entry.value.extractionMeta,
        cacheHit: true,
      },
    };
  }

  /**
   * Store a VisualFacts entry.
   *
   * @param key - Cache key
   * @param value - VisualFacts to cache
   */
  set(key: string, value: VisualFacts): void {
    const now = Date.now();
    this.store.set(key, {
      value,
      insertedAt: now,
      expiresAt: now + this.options.ttlMs,
    });
    this.prune();
  }

  /**
   * Check if a key exists in cache (without returning value).
   *
   * @param key - Cache key
   * @returns true if key exists and is not expired
   */
  has(key: string): boolean {
    const entry = this.store.get(key);
    if (!entry) return false;
    if (entry.expiresAt <= Date.now()) {
      this.store.delete(key);
      return false;
    }
    return true;
  }

  /**
   * Delete a specific key from cache.
   *
   * @param key - Cache key
   * @returns true if key was deleted
   */
  delete(key: string): boolean {
    return this.store.delete(key);
  }

  /**
   * Clear all cache entries.
   */
  clear(): void {
    this.store.clear();
  }

  /**
   * Get cache statistics.
   */
  stats(): { size: number; maxEntries: number; ttlMs: number } {
    return {
      size: this.store.size,
      maxEntries: this.options.maxEntries,
      ttlMs: this.options.ttlMs,
    };
  }

  /**
   * Stop the cleanup timer.
   * Call this when shutting down.
   */
  stop(): void {
    if (this.cleanupTimer) {
      clearInterval(this.cleanupTimer);
      this.cleanupTimer = undefined;
    }
  }

  /**
   * Remove expired entries.
   */
  private cleanup(): void {
    const now = Date.now();
    for (const [key, entry] of this.store.entries()) {
      if (entry.expiresAt <= now) {
        this.store.delete(key);
      }
    }
  }

  /**
   * Prune oldest entries if over capacity.
   * Uses LRU-style eviction.
   */
  private prune(): void {
    if (this.store.size <= this.options.maxEntries) return;

    const entries = [...this.store.entries()].sort(
      (a, b) => a[1].insertedAt - b[1].insertedAt
    );

    const toRemove = entries.slice(0, this.store.size - this.options.maxEntries);
    toRemove.forEach(([key]) => this.store.delete(key));
  }
}
