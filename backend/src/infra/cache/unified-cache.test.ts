import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import {
  UnifiedCache,
  buildCacheKey,
  buildAssistantCacheKey,
  buildVisionCacheKey,
  buildItemSnapshotHash,
  normalizeQuestion,
} from './unified-cache.js';

describe('UnifiedCache', () => {
  let cache: UnifiedCache<string>;

  beforeEach(() => {
    cache = new UnifiedCache<string>({
      ttlMs: 10000,
      maxEntries: 10,
      name: 'test',
      enableDedup: true,
    });
  });

  afterEach(() => {
    cache.stop();
  });

  describe('basic operations', () => {
    it('stores and retrieves values', () => {
      cache.set('key1', 'value1');
      expect(cache.get('key1')).toBe('value1');
    });

    it('returns null for missing keys', () => {
      expect(cache.get('nonexistent')).toBeNull();
    });

    it('tracks cache hits and misses', () => {
      cache.set('key1', 'value1');

      cache.get('key1'); // hit
      cache.get('key1'); // hit
      cache.get('nonexistent'); // miss

      const stats = cache.getStats();
      expect(stats.hits).toBe(2);
      expect(stats.misses).toBe(1);
    });

    it('respects maxEntries limit', () => {
      for (let i = 0; i < 15; i++) {
        cache.set(`key${i}`, `value${i}`);
      }

      const stats = cache.getStats();
      expect(stats.size).toBeLessThanOrEqual(10);
      expect(stats.evictions).toBeGreaterThan(0);
    });
  });

  describe('getOrCompute with deduplication', () => {
    it('computes value on cache miss', async () => {
      let computeCount = 0;
      const result = await cache.getOrCompute('key1', async () => {
        computeCount++;
        return 'computed';
      });

      expect(result).toBe('computed');
      expect(computeCount).toBe(1);
    });

    it('returns cached value without computing', async () => {
      cache.set('key1', 'cached');

      let computeCount = 0;
      const result = await cache.getOrCompute('key1', async () => {
        computeCount++;
        return 'computed';
      });

      expect(result).toBe('cached');
      expect(computeCount).toBe(0);
    });

    it('deduplicates concurrent requests', async () => {
      let computeCount = 0;

      const compute = async () => {
        computeCount++;
        await new Promise((resolve) => setTimeout(resolve, 50));
        return 'computed';
      };

      // Launch concurrent requests
      const promises = [
        cache.getOrCompute('key1', compute),
        cache.getOrCompute('key1', compute),
        cache.getOrCompute('key1', compute),
      ];

      const results = await Promise.all(promises);

      expect(results).toEqual(['computed', 'computed', 'computed']);
      expect(computeCount).toBe(1); // Only one computation
    });

    it('tracks coalesced requests', async () => {
      const compute = async () => {
        await new Promise((resolve) => setTimeout(resolve, 50));
        return 'computed';
      };

      await Promise.all([
        cache.getOrCompute('key1', compute),
        cache.getOrCompute('key1', compute),
      ]);

      const stats = cache.getStats();
      expect(stats.coalescedRequests).toBeGreaterThan(0);
    });
  });

  describe('TTL expiration', () => {
    it('expires entries after TTL', async () => {
      const shortCache = new UnifiedCache<string>({
        ttlMs: 50,
        maxEntries: 10,
        name: 'short-ttl',
        enableDedup: false,
      });

      shortCache.set('key1', 'value1');
      expect(shortCache.get('key1')).toBe('value1');

      // Wait for expiration
      await new Promise((resolve) => setTimeout(resolve, 100));

      expect(shortCache.get('key1')).toBeNull();
      shortCache.stop();
    });
  });

  describe('usage callback', () => {
    it('emits usage events', async () => {
      const events: Array<{ type: string; cacheKey: string }> = [];

      cache.setUsageCallback((event) => {
        events.push({ type: event.type, cacheKey: event.cacheKey });
      });

      cache.set('key1', 'value1');
      cache.get('key1'); // hit
      cache.get('nonexistent'); // miss

      expect(events).toContainEqual(expect.objectContaining({ type: 'hit' }));
      expect(events).toContainEqual(expect.objectContaining({ type: 'miss' }));
    });
  });
});

describe('buildCacheKey', () => {
  it('creates consistent keys from components', () => {
    const key1 = buildCacheKey('a', 'b', 'c');
    const key2 = buildCacheKey('a', 'b', 'c');
    expect(key1).toBe(key2);
  });

  it('creates different keys for different inputs', () => {
    const key1 = buildCacheKey('a', 'b');
    const key2 = buildCacheKey('a', 'c');
    expect(key1).not.toBe(key2);
  });

  it('handles undefined components', () => {
    const key1 = buildCacheKey('a', undefined, 'b');
    const key2 = buildCacheKey('a', 'b');
    expect(key1).toBe(key2);
  });
});

describe('buildAssistantCacheKey', () => {
  it('creates consistent keys', () => {
    const key1 = buildAssistantCacheKey({
      promptVersion: 'v1',
      question: 'What color is this?',
      itemSnapshotHash: 'abc123',
      imageHashes: ['hash1', 'hash2'],
      providerId: 'mock',
    });

    const key2 = buildAssistantCacheKey({
      promptVersion: 'v1',
      question: 'What color is this?',
      itemSnapshotHash: 'abc123',
      imageHashes: ['hash1', 'hash2'],
      providerId: 'mock',
    });

    expect(key1).toBe(key2);
  });

  it('normalizes questions', () => {
    const key1 = buildAssistantCacheKey({
      promptVersion: 'v1',
      question: 'What color is this?',
      itemSnapshotHash: 'abc',
      imageHashes: [],
      providerId: 'mock',
    });

    const key2 = buildAssistantCacheKey({
      promptVersion: 'v1',
      question: '  WHAT COLOR IS THIS?  ',
      itemSnapshotHash: 'abc',
      imageHashes: [],
      providerId: 'mock',
    });

    expect(key1).toBe(key2);
  });

  it('sorts image hashes for consistent keys', () => {
    const key1 = buildAssistantCacheKey({
      promptVersion: 'v1',
      question: 'test',
      itemSnapshotHash: 'abc',
      imageHashes: ['a', 'b', 'c'],
      providerId: 'mock',
    });

    const key2 = buildAssistantCacheKey({
      promptVersion: 'v1',
      question: 'test',
      itemSnapshotHash: 'abc',
      imageHashes: ['c', 'a', 'b'],
      providerId: 'mock',
    });

    expect(key1).toBe(key2);
  });
});

describe('buildVisionCacheKey', () => {
  it('creates consistent keys', () => {
    const key1 = buildVisionCacheKey({
      featureVersion: 'v1',
      imageHashes: ['hash1', 'hash2'],
    });

    const key2 = buildVisionCacheKey({
      featureVersion: 'v1',
      imageHashes: ['hash2', 'hash1'], // Different order
    });

    expect(key1).toBe(key2);
  });
});

describe('buildItemSnapshotHash', () => {
  it('creates consistent hashes', () => {
    const hash1 = buildItemSnapshotHash({
      itemId: 'item1',
      title: 'Test Item',
      category: 'Test',
      attributes: [{ key: 'color', value: 'blue' }],
    });

    const hash2 = buildItemSnapshotHash({
      itemId: 'item1',
      title: 'Test Item',
      category: 'Test',
      attributes: [{ key: 'color', value: 'blue' }],
    });

    expect(hash1).toBe(hash2);
  });

  it('handles null/undefined fields', () => {
    const hash = buildItemSnapshotHash({
      itemId: 'item1',
      title: null,
      category: undefined,
    });

    expect(hash).toBeDefined();
    expect(hash.length).toBe(32);
  });
});

describe('normalizeQuestion', () => {
  it('lowercases text', () => {
    expect(normalizeQuestion('HELLO WORLD')).toBe('hello world');
  });

  it('trims whitespace', () => {
    expect(normalizeQuestion('  hello  ')).toBe('hello');
  });

  it('collapses multiple spaces', () => {
    expect(normalizeQuestion('hello   world')).toBe('hello world');
  });

  it('removes punctuation', () => {
    expect(normalizeQuestion('hello, world!')).toBe('hello world');
  });
});
