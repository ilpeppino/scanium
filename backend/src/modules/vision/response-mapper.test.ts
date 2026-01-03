import { describe, expect, it } from 'vitest';
import { protos } from '@google-cloud/vision';
import { extractVisualFactsFromResponses } from './response-mapper.js';

const baseConfig = {
  maxOcrSnippetLength: 100,
  minOcrConfidence: 0.5,
  minLabelConfidence: 0.5,
  minLogoConfidence: 0.5,
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
