import { describe, expect, it } from 'vitest';
import { protos } from '@google-cloud/vision';
import { extractVisualFactsFromResponses } from './response-mapper.js';

const baseConfig = {
  maxOcrSnippetLength: 100,
  minOcrConfidence: 0.5,
  minLabelConfidence: 0.5,
  minLogoConfidence: 0.5,
};

/**
 * Fixture: Multi-feature Google Vision API response
 * This fixture represents a response when multiple features are requested:
 * - TEXT_DETECTION (OCR)
 * - IMAGE_PROPERTIES (colors)
 * - LOGO_DETECTION (brand logos)
 * - LABEL_DETECTION (category/material hints)
 */
const multiFeatureVisionResponse: protos.google.cloud.vision.v1.IAnnotateImageResponse = {
  // TEXT_DETECTION / DOCUMENT_TEXT_DETECTION results
  textAnnotations: [
    { description: 'IKEA\nKALLAX\n77x77 cm\nMade in Poland\nArt. 802.758.87' },
    { description: 'IKEA', score: 0.95, confidence: 0.95 },
    { description: 'KALLAX', score: 0.92, confidence: 0.92 },
    { description: '77x77 cm', score: 0.88, confidence: 0.88 },
    { description: 'Made in Poland', score: 0.85, confidence: 0.85 },
    { description: 'Art. 802.758.87', score: 0.90, confidence: 0.90 },
  ],
  fullTextAnnotation: {
    text: 'IKEA\nKALLAX\n77x77 cm\nMade in Poland\nArt. 802.758.87',
    pages: [],
  },

  // LABEL_DETECTION results
  labelAnnotations: [
    { description: 'Furniture', score: 0.95, topicality: 0.95 },
    { description: 'Shelf', score: 0.92, topicality: 0.92 },
    { description: 'Shelving', score: 0.88, topicality: 0.88 },
    { description: 'Wood', score: 0.82, topicality: 0.82 },
    { description: 'Rectangle', score: 0.78, topicality: 0.78 },
    { description: 'Storage', score: 0.75, topicality: 0.75 },
    { description: 'Bookcase', score: 0.72, topicality: 0.72 },
  ],

  // LOGO_DETECTION results
  logoAnnotations: [
    { description: 'IKEA', score: 0.91 },
  ],

  // IMAGE_PROPERTIES results (dominant colors)
  imagePropertiesAnnotation: {
    dominantColors: {
      colors: [
        {
          color: { red: 255, green: 255, blue: 255 },
          pixelFraction: 0.35,
          score: 0.35,
        },
        {
          color: { red: 139, green: 90, blue: 43 },
          pixelFraction: 0.28,
          score: 0.28,
        },
        {
          color: { red: 45, green: 45, blue: 45 },
          pixelFraction: 0.15,
          score: 0.15,
        },
        {
          color: { red: 200, green: 180, blue: 160 },
          pixelFraction: 0.12,
          score: 0.12,
        },
      ],
    },
  },
};

/**
 * Fixture: Vision response with only labels (single feature)
 */
const labelOnlyVisionResponse: protos.google.cloud.vision.v1.IAnnotateImageResponse = {
  labelAnnotations: [
    { description: 'Electronics', score: 0.94 },
    { description: 'Laptop', score: 0.92 },
    { description: 'Computer', score: 0.88 },
  ],
};

/**
 * Fixture: Vision response for an item with weak brand detection
 */
const weakBrandVisionResponse: protos.google.cloud.vision.v1.IAnnotateImageResponse = {
  textAnnotations: [
    { description: 'Generic Product\nModel X100' },
    { description: 'Generic Product', score: 0.75 },
    { description: 'Model X100', score: 0.82 },
  ],
  labelAnnotations: [
    { description: 'Table', score: 0.85 },
  ],
  imagePropertiesAnnotation: {
    dominantColors: {
      colors: [
        { color: { red: 0, green: 0, blue: 0 }, pixelFraction: 0.45 },
      ],
    },
  },
};

describe('extractVisualFactsFromResponses', () => {
  it('extracts OCR, labels, logos, and colors from Vision responses', () => {
    const response: protos.google.cloud.vision.v1.IAnnotateImageResponse = {
      textAnnotations: [
        { description: 'FULL\nTEXT' },
        { description: 'IKEA', score: 0.92 },
        { description: 'KALLAX', score: 0.86 },
      ],
      labelAnnotations: [{ description: 'Furniture', score: 0.9 }],
      logoAnnotations: [{ description: 'IKEA', score: 0.88 }],
      imagePropertiesAnnotation: {
        dominantColors: {
          colors: [
            { color: { red: 255, green: 0, blue: 0 }, pixelFraction: 0.6 },
            { color: { red: 0, green: 0, blue: 0 }, pixelFraction: 0.2 },
          ],
        },
      },
    };

    const facts = extractVisualFactsFromResponses(
      [response],
      {
        enableOcr: true,
        enableLabels: true,
        enableLogos: true,
        enableColors: true,
        maxOcrSnippets: 10,
        maxLabelHints: 5,
        maxLogoHints: 5,
        maxColors: 5,
        ocrMode: 'TEXT_DETECTION',
      },
      baseConfig
    );

    expect(facts.ocrSnippets.some((s) => s.text === 'IKEA')).toBe(true);
    expect(facts.labelHints[0]?.label).toBe('Furniture');
    expect(facts.logoHints[0]?.brand).toBe('IKEA');
    expect(facts.dominantColors[0]?.rgbHex).toBe('***REMOVED***FF0000');
    expect(facts.dominantColors[0]?.name).toBe('red');
  });

  it('uses document OCR text when configured', () => {
    const response: protos.google.cloud.vision.v1.IAnnotateImageResponse = {
      fullTextAnnotation: {
        text: 'Model ABC123\nMade in USA',
      },
    };

    const facts = extractVisualFactsFromResponses(
      [response],
      {
        enableOcr: true,
        enableLabels: false,
        enableLogos: false,
        enableColors: false,
        maxOcrSnippets: 5,
        maxLabelHints: 0,
        maxLogoHints: 0,
        maxColors: 0,
        ocrMode: 'DOCUMENT_TEXT_DETECTION',
      },
      baseConfig
    );

    expect(facts.ocrSnippets[0]?.text).toBe('Model ABC123');
  });
});

/**
 * Test suite: Multi-feature Vision extraction with fixtures
 *
 * Tests that all Vision features work together and produce the expected
 * response shape for attribute extraction.
 */
describe('Multi-feature Vision Extraction', () => {
  const allFeaturesOptions = {
    enableOcr: true,
    enableLabels: true,
    enableLogos: true,
    enableColors: true,
    maxOcrSnippets: 10,
    maxLabelHints: 10,
    maxLogoHints: 5,
    maxColors: 5,
    ocrMode: 'TEXT_DETECTION' as const,
  };

  it('extracts all features from multi-feature Vision response', () => {
    const facts = extractVisualFactsFromResponses(
      [multiFeatureVisionResponse],
      allFeaturesOptions,
      baseConfig
    );

    // OCR extraction
    expect(facts.ocrSnippets.length).toBeGreaterThan(0);
    expect(facts.ocrSnippets.some(s => s.text === 'IKEA')).toBe(true);
    expect(facts.ocrSnippets.some(s => s.text === 'KALLAX')).toBe(true);

    // Labels extraction
    expect(facts.labelHints.length).toBeGreaterThan(0);
    expect(facts.labelHints[0].label).toBe('Furniture');
    expect(facts.labelHints[0].score).toBe(0.95);
    expect(facts.labelHints.some(l => l.label === 'Wood')).toBe(true);

    // Logo extraction
    expect(facts.logoHints.length).toBe(1);
    expect(facts.logoHints[0].brand).toBe('IKEA');
    expect(facts.logoHints[0].score).toBe(0.91);

    // Color extraction
    expect(facts.dominantColors.length).toBeGreaterThan(0);
    expect(facts.dominantColors[0].rgbHex).toBe('***REMOVED***FFFFFF'); // white
    expect(facts.dominantColors[0].pct).toBe(35);
  });

  it('produces brand candidates from logo detection', () => {
    const facts = extractVisualFactsFromResponses(
      [multiFeatureVisionResponse],
      allFeaturesOptions,
      baseConfig
    );

    // Logo should provide brand candidate
    expect(facts.logoHints.some(l => l.brand === 'IKEA')).toBe(true);
  });

  it('produces model candidates from OCR with pattern matching', () => {
    const facts = extractVisualFactsFromResponses(
      [multiFeatureVisionResponse],
      allFeaturesOptions,
      baseConfig
    );

    // OCR should contain potential model number patterns
    const modelPatternSnippets = facts.ocrSnippets.filter(s =>
      /\d/.test(s.text) && s.text.length >= 3 && s.text.length <= 30
    );
    expect(modelPatternSnippets.length).toBeGreaterThan(0);
  });

  it('handles single-feature response (labels only)', () => {
    const facts = extractVisualFactsFromResponses(
      [labelOnlyVisionResponse],
      allFeaturesOptions,
      baseConfig
    );

    // Only labels should be populated
    expect(facts.labelHints.length).toBe(3);
    expect(facts.labelHints[0].label).toBe('Electronics');

    // Other features should be empty
    expect(facts.ocrSnippets.length).toBe(0);
    expect(facts.logoHints.length).toBe(0);
    expect(facts.dominantColors.length).toBe(0);
  });

  it('handles response with weak brand detection', () => {
    const facts = extractVisualFactsFromResponses(
      [weakBrandVisionResponse],
      allFeaturesOptions,
      baseConfig
    );

    // OCR should be extracted
    expect(facts.ocrSnippets.length).toBeGreaterThan(0);
    expect(facts.ocrSnippets.some(s => s.text.includes('Model X100'))).toBe(true);

    // No logo detection
    expect(facts.logoHints.length).toBe(0);

    // Labels should include Table
    expect(facts.labelHints.some(l => l.label === 'Table')).toBe(true);

    // Color should be black
    expect(facts.dominantColors[0].name).toBe('black');
    expect(facts.dominantColors[0].pct).toBe(45);
  });

  it('respects confidence thresholds for filtering', () => {
    const strictConfig = {
      ...baseConfig,
      minOcrConfidence: 0.9, // Very strict
      minLabelConfidence: 0.9,
      minLogoConfidence: 0.95,
    };

    const facts = extractVisualFactsFromResponses(
      [multiFeatureVisionResponse],
      allFeaturesOptions,
      strictConfig
    );

    // Only high-confidence OCR should remain
    expect(facts.ocrSnippets.every(s => s.confidence >= 0.9)).toBe(true);

    // Only high-confidence labels
    expect(facts.labelHints.every(l => l.score >= 0.9)).toBe(true);

    // Logo below 0.95 should be filtered
    expect(facts.logoHints.length).toBe(0); // IKEA logo was 0.91
  });

  it('respects max limits for each feature', () => {
    const limitedOptions = {
      enableOcr: true,
      enableLabels: true,
      enableLogos: true,
      enableColors: true,
      maxOcrSnippets: 2,
      maxLabelHints: 2,
      maxLogoHints: 1,
      maxColors: 2,
      ocrMode: 'TEXT_DETECTION' as const,
    };

    const facts = extractVisualFactsFromResponses(
      [multiFeatureVisionResponse],
      limitedOptions,
      baseConfig
    );

    expect(facts.ocrSnippets.length).toBeLessThanOrEqual(2);
    expect(facts.labelHints.length).toBeLessThanOrEqual(2);
    expect(facts.logoHints.length).toBeLessThanOrEqual(1);
    expect(facts.dominantColors.length).toBeLessThanOrEqual(2);
  });

  it('can selectively disable features', () => {
    const ocrOnlyOptions = {
      enableOcr: true,
      enableLabels: false,
      enableLogos: false,
      enableColors: false,
      maxOcrSnippets: 10,
      maxLabelHints: 0,
      maxLogoHints: 0,
      maxColors: 0,
      ocrMode: 'TEXT_DETECTION' as const,
    };

    const facts = extractVisualFactsFromResponses(
      [multiFeatureVisionResponse],
      ocrOnlyOptions,
      baseConfig
    );

    // Only OCR should be extracted
    expect(facts.ocrSnippets.length).toBeGreaterThan(0);
    expect(facts.labelHints.length).toBe(0);
    expect(facts.logoHints.length).toBe(0);
    expect(facts.dominantColors.length).toBe(0);
  });
});

/**
 * Test suite: Response shape validation
 *
 * Ensures the extracted facts match the expected structure for downstream
 * attribute resolution.
 */
describe('Vision Response Shape', () => {
  it('produces ocrSnippets with text and confidence', () => {
    const facts = extractVisualFactsFromResponses(
      [multiFeatureVisionResponse],
      {
        enableOcr: true,
        enableLabels: false,
        enableLogos: false,
        enableColors: false,
        maxOcrSnippets: 10,
        maxLabelHints: 0,
        maxLogoHints: 0,
        maxColors: 0,
        ocrMode: 'TEXT_DETECTION',
      },
      baseConfig
    );

    for (const snippet of facts.ocrSnippets) {
      expect(typeof snippet.text).toBe('string');
      expect(snippet.text.length).toBeGreaterThan(0);
      expect(typeof snippet.confidence).toBe('number');
      expect(snippet.confidence).toBeGreaterThanOrEqual(0);
      expect(snippet.confidence).toBeLessThanOrEqual(1);
    }
  });

  it('produces labelHints with label and score', () => {
    const facts = extractVisualFactsFromResponses(
      [multiFeatureVisionResponse],
      {
        enableOcr: false,
        enableLabels: true,
        enableLogos: false,
        enableColors: false,
        maxOcrSnippets: 0,
        maxLabelHints: 10,
        maxLogoHints: 0,
        maxColors: 0,
        ocrMode: 'TEXT_DETECTION',
      },
      baseConfig
    );

    for (const label of facts.labelHints) {
      expect(typeof label.label).toBe('string');
      expect(label.label.length).toBeGreaterThan(0);
      expect(typeof label.score).toBe('number');
      expect(label.score).toBeGreaterThanOrEqual(0);
      expect(label.score).toBeLessThanOrEqual(1);
    }
  });

  it('produces logoHints with brand and score', () => {
    const facts = extractVisualFactsFromResponses(
      [multiFeatureVisionResponse],
      {
        enableOcr: false,
        enableLabels: false,
        enableLogos: true,
        enableColors: false,
        maxOcrSnippets: 0,
        maxLabelHints: 0,
        maxLogoHints: 5,
        maxColors: 0,
        ocrMode: 'TEXT_DETECTION',
      },
      baseConfig
    );

    for (const logo of facts.logoHints) {
      expect(typeof logo.brand).toBe('string');
      expect(logo.brand.length).toBeGreaterThan(0);
      expect(typeof logo.score).toBe('number');
      expect(logo.score).toBeGreaterThanOrEqual(0);
      expect(logo.score).toBeLessThanOrEqual(1);
    }
  });

  it('produces dominantColors with name, hex, and percentage', () => {
    const facts = extractVisualFactsFromResponses(
      [multiFeatureVisionResponse],
      {
        enableOcr: false,
        enableLabels: false,
        enableLogos: false,
        enableColors: true,
        maxOcrSnippets: 0,
        maxLabelHints: 0,
        maxLogoHints: 0,
        maxColors: 5,
        ocrMode: 'TEXT_DETECTION',
      },
      baseConfig
    );

    for (const color of facts.dominantColors) {
      expect(typeof color.name).toBe('string');
      expect(color.name.length).toBeGreaterThan(0);
      expect(typeof color.rgbHex).toBe('string');
      expect(color.rgbHex).toMatch(/^***REMOVED***[0-9A-F]{6}$/i);
      expect(typeof color.pct).toBe('number');
      expect(color.pct).toBeGreaterThanOrEqual(0);
      expect(color.pct).toBeLessThanOrEqual(100);
    }
  });
});
