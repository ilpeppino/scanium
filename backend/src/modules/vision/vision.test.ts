import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import {
  VisualFactsCache,
  buildCacheKey,
  computeImageHash,
  MockVisionExtractor,
  resolveAttributes,
  getBrandDictionary,
} from './index.js';
import { VisualFacts } from './types.js';
import { ResolvedAttributes } from './attribute-resolver.js';

describe('computeImageHash', () => {
  it('returns consistent hash for same input', () => {
    const base64 = 'SGVsbG8gV29ybGQ='; // "Hello World"
    const hash1 = computeImageHash(base64);
    const hash2 = computeImageHash(base64);
    expect(hash1).toBe(hash2);
    expect(hash1.length).toBe(16);
  });

  it('returns different hashes for different inputs', () => {
    const hash1 = computeImageHash('SGVsbG8gV29ybGQ=');
    const hash2 = computeImageHash('R29vZGJ5ZQ==');
    expect(hash1).not.toBe(hash2);
  });
});

describe('buildCacheKey', () => {
  it('builds consistent cache key from image hashes', () => {
    const hashes = ['abc123', 'def456'];
    const key1 = buildCacheKey(hashes);
    const key2 = buildCacheKey(hashes);
    expect(key1).toBe(key2);
  });

  it('builds same key regardless of hash order', () => {
    const key1 = buildCacheKey(['abc', 'def']);
    const key2 = buildCacheKey(['def', 'abc']);
    expect(key1).toBe(key2);
  });

  it('includes context in cache key', () => {
    const hashes = ['abc123'];
    const key1 = buildCacheKey(hashes, { featureVersion: 'v1' });
    const key2 = buildCacheKey(hashes, { featureVersion: 'v2' });
    expect(key1).not.toBe(key2);
    expect(key1).toContain('v1');
    expect(key2).toContain('v2');
  });
});

describe('VisualFactsCache', () => {
  let cache: VisualFactsCache;

  const mockFacts: VisualFacts = {
    itemId: 'test-item',
    dominantColors: [{ name: 'blue', rgbHex: '***REMOVED***0000FF', pct: 50 }],
    ocrSnippets: [{ text: 'IKEA', confidence: 0.9 }],
    labelHints: [{ label: 'Furniture', score: 0.85 }],
    logoHints: [{ brand: 'IKEA', score: 0.8 }],
    extractionMeta: {
      provider: 'mock',
      timingsMs: { total: 100 },
      imageCount: 1,
      imageHashes: ['abc123'],
    },
  };

  beforeEach(() => {
    cache = new VisualFactsCache({ ttlMs: 1000, maxEntries: 10 });
  });

  afterEach(() => {
    cache.stop();
  });

  it('stores and retrieves visual facts', () => {
    cache.set('key1', mockFacts);
    const retrieved = cache.get('key1');
    expect(retrieved).toBeDefined();
    expect(retrieved?.itemId).toBe('test-item');
    expect(retrieved?.extractionMeta.cacheHit).toBe(true);
  });

  it('returns null for missing keys', () => {
    const result = cache.get('nonexistent');
    expect(result).toBeNull();
  });

  it('expires entries after TTL', async () => {
    const shortCache = new VisualFactsCache({ ttlMs: 50, maxEntries: 10 });
    shortCache.set('key1', mockFacts);

    expect(shortCache.get('key1')).toBeDefined();

    await new Promise((resolve) => setTimeout(resolve, 100));

    expect(shortCache.get('key1')).toBeNull();
    shortCache.stop();
  });

  it('prunes old entries when over capacity', () => {
    const smallCache = new VisualFactsCache({ ttlMs: 10000, maxEntries: 3 });

    smallCache.set('key1', { ...mockFacts, itemId: 'item1' });
    smallCache.set('key2', { ...mockFacts, itemId: 'item2' });
    smallCache.set('key3', { ...mockFacts, itemId: 'item3' });
    smallCache.set('key4', { ...mockFacts, itemId: 'item4' });

    const stats = smallCache.stats();
    expect(stats.size).toBeLessThanOrEqual(3);

    smallCache.stop();
  });

  it('has() checks existence without returning value', () => {
    cache.set('key1', mockFacts);
    expect(cache.has('key1')).toBe(true);
    expect(cache.has('key2')).toBe(false);
  });

  it('delete() removes specific key', () => {
    cache.set('key1', mockFacts);
    cache.set('key2', mockFacts);

    expect(cache.delete('key1')).toBe(true);
    expect(cache.has('key1')).toBe(false);
    expect(cache.has('key2')).toBe(true);
  });

  it('clear() removes all entries', () => {
    cache.set('key1', mockFacts);
    cache.set('key2', mockFacts);
    cache.clear();

    expect(cache.stats().size).toBe(0);
  });
});

describe('MockVisionExtractor', () => {
  const extractor = new MockVisionExtractor();

  it('returns mock visual facts for valid input', async () => {
    const result = await extractor.extractVisualFacts(
      'item-1',
      [{ base64Data: 'SGVsbG8=', mimeType: 'image/jpeg' }],
      {}
    );

    expect(result.success).toBe(true);
    expect(result.facts).toBeDefined();
    expect(result.facts?.itemId).toBe('item-1');
    expect(result.facts?.dominantColors.length).toBeGreaterThan(0);
    expect(result.facts?.ocrSnippets.length).toBeGreaterThan(0);
    expect(result.facts?.labelHints.length).toBeGreaterThan(0);
    expect(result.facts?.logoHints?.length).toBeGreaterThan(0);
  });

  it('returns consistent mock data for IKEA-style detection', async () => {
    const result = await extractor.extractVisualFacts('item-1', [
      { base64Data: 'SGVsbG8=', mimeType: 'image/jpeg' },
    ]);

    // Mock extractor returns IKEA as a mock brand
    const ikea = result.facts?.ocrSnippets.find((s) => s.text === 'IKEA');
    expect(ikea).toBeDefined();

    const ikeaLogo = result.facts?.logoHints?.find((l) => l.brand === 'IKEA');
    expect(ikeaLogo).toBeDefined();
  });

  it('includes image hashes in extraction meta', async () => {
    const base64 = 'SGVsbG8gV29ybGQ=';
    const result = await extractor.extractVisualFacts('item-1', [
      { base64Data: base64, mimeType: 'image/jpeg' },
    ]);

    expect(result.facts?.extractionMeta.imageHashes.length).toBe(1);
    expect(result.facts?.extractionMeta.imageHashes[0]).toBe(
      computeImageHash(base64)
    );
  });
});

// ============================================================================
// AttributeResolver Tests
// ============================================================================

describe('resolveAttributes', () => {
  const baseFacts: VisualFacts = {
    itemId: 'test-item',
    dominantColors: [],
    ocrSnippets: [],
    labelHints: [],
    logoHints: [],
    extractionMeta: {
      provider: 'mock',
      timingsMs: { total: 100 },
      imageCount: 1,
      imageHashes: ['abc123'],
    },
  };

  describe('brand extraction', () => {
    it('extracts brand from logoHint with HIGH confidence when score >= 0.8', () => {
      const facts: VisualFacts = {
        ...baseFacts,
        logoHints: [{ brand: 'Nike', score: 0.9 }],
      };

      const result = resolveAttributes('item-1', facts);

      expect(result.brand).toBeDefined();
      expect(result.brand?.value).toBe('Nike');
      expect(result.brand?.confidence).toBe('HIGH');
      expect(result.brand?.evidenceRefs[0].type).toBe('logo');
      expect(result.brand?.evidenceRefs[0].score).toBe(0.9);
    });

    it('extracts brand from logoHint with MED confidence when 0.5 <= score < 0.8', () => {
      const facts: VisualFacts = {
        ...baseFacts,
        logoHints: [{ brand: 'Adidas', score: 0.65 }],
      };

      const result = resolveAttributes('item-1', facts);

      expect(result.brand).toBeDefined();
      expect(result.brand?.value).toBe('Adidas');
      expect(result.brand?.confidence).toBe('MED');
    });

    it('ignores logoHint with score < 0.5', () => {
      const facts: VisualFacts = {
        ...baseFacts,
        logoHints: [{ brand: 'Unknown', score: 0.3 }],
      };

      const result = resolveAttributes('item-1', facts);
      expect(result.brand).toBeUndefined();
    });

    it('extracts brand from OCR matching brand dictionary with HIGH confidence', () => {
      const facts: VisualFacts = {
        ...baseFacts,
        ocrSnippets: [{ text: 'IKEA', confidence: 0.9 }],
      };

      const result = resolveAttributes('item-1', facts);

      expect(result.brand).toBeDefined();
      expect(result.brand?.value).toBe('IKEA');
      expect(result.brand?.confidence).toBe('HIGH');
      expect(result.brand?.evidenceRefs[0].type).toBe('ocr');
    });

    it('extracts brand from OCR matching brand dictionary with MED confidence when OCR confidence < 0.8', () => {
      const facts: VisualFacts = {
        ...baseFacts,
        ocrSnippets: [{ text: 'Samsung', confidence: 0.7 }],
      };

      const result = resolveAttributes('item-1', facts);

      expect(result.brand).toBeDefined();
      expect(result.brand?.value).toBe('SAMSUNG');
      expect(result.brand?.confidence).toBe('MED');
    });

    it('extracts brand-like OCR text not in dictionary with LOW confidence', () => {
      const facts: VisualFacts = {
        ...baseFacts,
        ocrSnippets: [{ text: 'Acmecorp', confidence: 0.85 }],
      };

      const result = resolveAttributes('item-1', facts);

      expect(result.brand).toBeDefined();
      expect(result.brand?.value).toBe('Acmecorp');
      expect(result.brand?.confidence).toBe('LOW');
    });

    it('rejects generic words as brands', () => {
      const facts: VisualFacts = {
        ...baseFacts,
        ocrSnippets: [
          { text: 'MADE', confidence: 0.95 },
          { text: 'MODEL', confidence: 0.9 },
          { text: 'SERIAL', confidence: 0.88 },
        ],
      };

      const result = resolveAttributes('item-1', facts);
      expect(result.brand).toBeUndefined();
    });

    it('prefers logo over OCR when both present', () => {
      const facts: VisualFacts = {
        ...baseFacts,
        logoHints: [{ brand: 'Nike', score: 0.7 }],
        ocrSnippets: [{ text: 'IKEA', confidence: 0.9 }],
      };

      const result = resolveAttributes('item-1', facts);

      expect(result.brand?.value).toBe('Nike');
      expect(result.brand?.evidenceRefs[0].type).toBe('logo');
    });
  });

  describe('model extraction', () => {
    it('extracts model with strong pattern and keyword nearby -> HIGH', () => {
      const facts: VisualFacts = {
        ...baseFacts,
        ocrSnippets: [
          { text: 'Model', confidence: 0.9 },
          { text: 'AB1234', confidence: 0.85 },
        ],
      };

      const result = resolveAttributes('item-1', facts);

      expect(result.model).toBeDefined();
      expect(result.model?.value).toBe('AB1234');
      expect(result.model?.confidence).toBe('HIGH');
    });

    it('extracts model with strong pattern but no keyword -> MED', () => {
      const facts: VisualFacts = {
        ...baseFacts,
        ocrSnippets: [{ text: 'XK9800-PRO', confidence: 0.9 }],
      };

      const result = resolveAttributes('item-1', facts);

      expect(result.model).toBeDefined();
      expect(result.model?.value).toBe('XK9800-PRO');
      expect(result.model?.confidence).toBe('MED');
    });

    it('extracts model with keyword but weaker pattern -> MED', () => {
      const facts: VisualFacts = {
        ...baseFacts,
        ocrSnippets: [
          { text: 'SKU', confidence: 0.9 },
          { text: '12345', confidence: 0.85 },
        ],
      };

      const result = resolveAttributes('item-1', facts);

      expect(result.model).toBeDefined();
      expect(result.model?.value).toBe('12345');
      expect(result.model?.confidence).toBe('MED');
    });

    it('returns undefined for text without model pattern', () => {
      const facts: VisualFacts = {
        ...baseFacts,
        ocrSnippets: [
          { text: 'Hello', confidence: 0.9 },
          { text: 'World', confidence: 0.85 },
        ],
      };

      const result = resolveAttributes('item-1', facts);
      expect(result.model).toBeUndefined();
    });

    it('returns undefined for all-letter text even with keyword', () => {
      const facts: VisualFacts = {
        ...baseFacts,
        ocrSnippets: [
          { text: 'Model', confidence: 0.9 },
          { text: 'DELUXE', confidence: 0.85 },
        ],
      };

      const result = resolveAttributes('item-1', facts);
      expect(result.model).toBeUndefined();
    });
  });

  describe('color extraction', () => {
    it('extracts single dominant color with HIGH confidence when >= 50%', () => {
      const facts: VisualFacts = {
        ...baseFacts,
        dominantColors: [
          { name: 'blue', rgbHex: '***REMOVED***0000FF', pct: 60 },
          { name: 'white', rgbHex: '***REMOVED***FFFFFF', pct: 20 },
        ],
      };

      const result = resolveAttributes('item-1', facts);

      expect(result.color).toBeDefined();
      expect(result.color?.value).toBe('blue');
      expect(result.color?.confidence).toBe('HIGH');
      expect(result.secondaryColor).toBeUndefined();
    });

    it('extracts single dominant color with MED confidence when 40-50%', () => {
      const facts: VisualFacts = {
        ...baseFacts,
        dominantColors: [
          { name: 'red', rgbHex: '***REMOVED***FF0000', pct: 45 },
          { name: 'black', rgbHex: '***REMOVED***000000', pct: 15 },
        ],
      };

      const result = resolveAttributes('item-1', facts);

      expect(result.color?.value).toBe('red');
      expect(result.color?.confidence).toBe('MED');
      expect(result.secondaryColor).toBeUndefined();
    });

    it('returns primary with MED when clearly dominant but < 40%', () => {
      const facts: VisualFacts = {
        ...baseFacts,
        dominantColors: [
          { name: 'green', rgbHex: '***REMOVED***00FF00', pct: 30 },
          { name: 'gray', rgbHex: '***REMOVED***808080', pct: 10 },
        ],
      };

      const result = resolveAttributes('item-1', facts);

      expect(result.color?.value).toBe('green');
      expect(result.color?.confidence).toBe('MED');
      expect(result.secondaryColor).toBeUndefined();
    });

    it('returns ambiguous colors with LOW confidence and secondaryColor', () => {
      const facts: VisualFacts = {
        ...baseFacts,
        dominantColors: [
          { name: 'blue', rgbHex: '***REMOVED***0000FF', pct: 28 },
          { name: 'green', rgbHex: '***REMOVED***00FF00', pct: 25 },
        ],
      };

      const result = resolveAttributes('item-1', facts);

      expect(result.color).toBeDefined();
      expect(result.color?.value).toBe('blue');
      expect(result.color?.confidence).toBe('LOW');
      expect(result.secondaryColor).toBeDefined();
      expect(result.secondaryColor?.value).toBe('green');
      expect(result.secondaryColor?.confidence).toBe('LOW');
    });

    it('returns empty when no dominant colors', () => {
      const facts: VisualFacts = {
        ...baseFacts,
        dominantColors: [],
      };

      const result = resolveAttributes('item-1', facts);
      expect(result.color).toBeUndefined();
    });
  });

  describe('material extraction', () => {
    it('extracts material from label hints with MED confidence when score >= 0.8', () => {
      const facts: VisualFacts = {
        ...baseFacts,
        labelHints: [{ label: 'Wooden furniture', score: 0.85 }],
      };

      const result = resolveAttributes('item-1', facts);

      expect(result.material).toBeDefined();
      expect(result.material?.value).toBe('wood');
      expect(result.material?.confidence).toBe('MED');
    });

    it('extracts material from label hints with LOW confidence when 0.6 <= score < 0.8', () => {
      const facts: VisualFacts = {
        ...baseFacts,
        labelHints: [{ label: 'Metal frame', score: 0.7 }],
      };

      const result = resolveAttributes('item-1', facts);

      expect(result.material).toBeDefined();
      expect(result.material?.value).toBe('metal');
      expect(result.material?.confidence).toBe('LOW');
    });

    it('ignores material with score < 0.6', () => {
      const facts: VisualFacts = {
        ...baseFacts,
        labelHints: [{ label: 'Plastic container', score: 0.5 }],
      };

      const result = resolveAttributes('item-1', facts);
      expect(result.material).toBeUndefined();
    });
  });

  describe('suggestedNextPhoto', () => {
    it('suggests brand photo when brand is missing', () => {
      const facts: VisualFacts = {
        ...baseFacts,
        dominantColors: [{ name: 'blue', rgbHex: '***REMOVED***0000FF', pct: 60 }],
      };

      const result = resolveAttributes('item-1', facts);
      expect(result.suggestedNextPhoto).toContain('brand');
    });

    it('suggests brand photo when brand has LOW confidence', () => {
      const facts: VisualFacts = {
        ...baseFacts,
        ocrSnippets: [{ text: 'Acmecorp', confidence: 0.8 }],
        dominantColors: [{ name: 'blue', rgbHex: '***REMOVED***0000FF', pct: 60 }],
      };

      const result = resolveAttributes('item-1', facts);
      expect(result.brand?.confidence).toBe('LOW');
      expect(result.suggestedNextPhoto).toContain('brand');
    });

    it('suggests model photo when model is missing but brand is present', () => {
      const facts: VisualFacts = {
        ...baseFacts,
        logoHints: [{ brand: 'IKEA', score: 0.9 }],
        dominantColors: [{ name: 'blue', rgbHex: '***REMOVED***0000FF', pct: 60 }],
      };

      const result = resolveAttributes('item-1', facts);
      expect(result.suggestedNextPhoto).toContain('model');
    });

    it('suggests color photo when colors are ambiguous', () => {
      const facts: VisualFacts = {
        ...baseFacts,
        logoHints: [{ brand: 'IKEA', score: 0.9 }],
        ocrSnippets: [
          { text: 'Model', confidence: 0.9 },
          { text: 'AB1234', confidence: 0.9 },
        ],
        dominantColors: [
          { name: 'blue', rgbHex: '***REMOVED***0000FF', pct: 30 },
          { name: 'green', rgbHex: '***REMOVED***00FF00', pct: 28 },
        ],
      };

      const result = resolveAttributes('item-1', facts);
      expect(result.secondaryColor).toBeDefined();
      expect(result.suggestedNextPhoto).toContain('color');
    });

    it('returns undefined when all attributes are strong', () => {
      const facts: VisualFacts = {
        ...baseFacts,
        logoHints: [{ brand: 'IKEA', score: 0.9 }],
        ocrSnippets: [
          { text: 'Model', confidence: 0.9 },
          { text: 'AB1234', confidence: 0.9 },
        ],
        dominantColors: [{ name: 'blue', rgbHex: '***REMOVED***0000FF', pct: 60 }],
      };

      const result = resolveAttributes('item-1', facts);
      expect(result.suggestedNextPhoto).toBeUndefined();
    });
  });
});

describe('getBrandDictionary', () => {
  it('returns a readonly set of known brands', () => {
    const dictionary = getBrandDictionary();
    expect(dictionary.has('IKEA')).toBe(true);
    expect(dictionary.has('APPLE')).toBe(true);
    expect(dictionary.has('SAMSUNG')).toBe(true);
    expect(dictionary.has('NIKE')).toBe(true);
  });
});
