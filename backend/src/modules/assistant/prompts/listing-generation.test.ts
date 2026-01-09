import { describe, it, expect } from 'vitest';
import {
  buildListingSystemPrompt,
  buildListingUserPrompt,
  parseListingResponse,
  ParsedListingResponse,
} from './listing-generation.js';
import { ResolvedAttributes } from '../../vision/attribute-resolver.js';
import { VisualFacts } from '../../vision/types.js';

describe('Listing Generation Prompts', () => {
  describe('buildListingSystemPrompt', () => {
    it('returns system prompt with default preferences', () => {
      const prompt = buildListingSystemPrompt();

      expect(prompt).toContain('marketplace listing assistant');
      expect(prompt).toContain('Reply in English');
      expect(prompt).toContain('neutral');
      expect(prompt).toContain('EUR');
    });

    it('includes Dutch language instruction when specified', () => {
      const prompt = buildListingSystemPrompt({ language: 'NL' });

      expect(prompt).toContain('Dutch');
      expect(prompt).toContain('Nederlands');
    });

    it('includes German language instruction when specified', () => {
      const prompt = buildListingSystemPrompt({ language: 'DE' });

      expect(prompt).toContain('German');
      expect(prompt).toContain('Deutsch');
    });

    it('includes French language instruction when specified', () => {
      const prompt = buildListingSystemPrompt({ language: 'FR' });

      expect(prompt).toContain('French');
      expect(prompt).toContain('Français');
    });

    it('includes Italian language instruction when specified', () => {
      const prompt = buildListingSystemPrompt({ language: 'IT' });

      expect(prompt).toContain('Italian');
      expect(prompt).toContain('Italiano');
    });

    it('includes Spanish language instruction when specified', () => {
      const prompt = buildListingSystemPrompt({ language: 'ES' });

      expect(prompt).toContain('Spanish');
      expect(prompt).toContain('Español');
    });

    it('includes Portuguese language instruction when specified', () => {
      const prompt = buildListingSystemPrompt({ language: 'PT_BR' });

      expect(prompt).toContain('Portuguese');
      expect(prompt).toContain('Português');
    });

    it('enforces strict language compliance with "Do not mix languages"', () => {
      const prompt = buildListingSystemPrompt({ language: 'EN' });

      expect(prompt).toContain('Do not mix languages');
      expect(prompt).toContain('All output');
      expect(prompt).toContain('must be in English');
    });

    it('enforces language compliance for all supported languages', () => {
      const languages = ['EN', 'NL', 'DE', 'FR', 'IT', 'ES', 'PT_BR'];

      for (const lang of languages) {
        const prompt = buildListingSystemPrompt({ language: lang });
        expect(prompt).toContain('Do not mix languages');
      }
    });

    it('falls back to English for unsupported language codes', () => {
      const prompt = buildListingSystemPrompt({ language: 'ZZ' });

      expect(prompt).toContain('Reply in English');
      expect(prompt).toContain('Do not mix languages');
    });

    it('includes friendly tone instruction when specified', () => {
      const prompt = buildListingSystemPrompt({ tone: 'FRIENDLY' });

      expect(prompt).toContain('friendly');
      expect(prompt).toContain('approachable');
    });

    it('includes professional tone instruction when specified', () => {
      const prompt = buildListingSystemPrompt({ tone: 'PROFESSIONAL' });

      expect(prompt).toContain('formal');
      expect(prompt).toContain('professional');
    });

    it('includes marketplace tone instruction when specified', () => {
      const prompt = buildListingSystemPrompt({ tone: 'MARKETPLACE' });

      expect(prompt).toContain('concise');
      expect(prompt).toContain('matter-of-fact');
      expect(prompt).toContain('No marketing hype');
      expect(prompt).toContain('emojis');
      expect(prompt).toContain('3-6 bullet lines max');
    });

    it('includes region-specific marketplaces for NL', () => {
      const prompt = buildListingSystemPrompt({ region: 'NL' });

      expect(prompt).toContain('Marktplaats');
      expect(prompt).toContain('EUR');
    });

    it('includes region-specific marketplaces for UK', () => {
      const prompt = buildListingSystemPrompt({ region: 'UK' });

      expect(prompt).toContain('eBay.co.uk');
      expect(prompt).toContain('Gumtree');
      expect(prompt).toContain('GBP');
    });

    it('includes concise verbosity instruction', () => {
      const prompt = buildListingSystemPrompt({ verbosity: 'CONCISE' });

      expect(prompt).toContain('brief');
    });

    it('includes detailed verbosity instruction', () => {
      const prompt = buildListingSystemPrompt({ verbosity: 'DETAILED' });

      expect(prompt).toContain('comprehensive');
    });

    it('includes critical rules about hallucination prevention', () => {
      const prompt = buildListingSystemPrompt();

      expect(prompt).toContain('NEVER invent');
      expect(prompt).toContain('confidence');
    });

    // PR4 User attribute handling rules
    it('includes user attribute handling rules (PR4)', () => {
      const prompt = buildListingSystemPrompt();

      expect(prompt).toContain('USER-PROVIDED');
      expect(prompt).toContain('AUTHORITATIVE');
      expect(prompt).toContain('[USER]');
      expect(prompt).toContain('[DETECTED]');
    });

    it('includes title rules with 80 char limit', () => {
      const prompt = buildListingSystemPrompt();

      expect(prompt).toContain('TITLE RULES');
      expect(prompt).toContain('80 characters');
      expect(prompt).toContain('keyword');
    });

    it('includes description rules with bullet points', () => {
      const prompt = buildListingSystemPrompt();

      expect(prompt).toContain('DESCRIPTION RULES');
      expect(prompt).toContain('bullet');
    });

    it('includes confidence assignment rules', () => {
      const prompt = buildListingSystemPrompt();

      expect(prompt).toContain('CONFIDENCE ASSIGNMENT');
      expect(prompt).toContain('User-provided');
      expect(prompt).toContain('HIGH');
    });

    it('includes output format specification', () => {
      const prompt = buildListingSystemPrompt();

      expect(prompt).toContain('OUTPUT FORMAT');
      expect(prompt).toContain('JSON');
      expect(prompt).toContain('title');
      expect(prompt).toContain('description');
      expect(prompt).toContain('warnings');
      expect(prompt).toContain('missingInfo');
    });
  });

  describe('buildListingUserPrompt', () => {
    it('returns message for empty items array', () => {
      const prompt = buildListingUserPrompt([]);

      expect(prompt).toContain('No items provided');
    });

    it('includes item ID in prompt', () => {
      const prompt = buildListingUserPrompt([{ itemId: 'item-123' }]);

      expect(prompt).toContain('item-123');
    });

    it('includes existing item attributes', () => {
      const prompt = buildListingUserPrompt([
        {
          itemId: 'item-1',
          title: 'Test Item',
          category: 'Electronics',
          attributes: [{ key: 'brand', value: 'Sony' }],
        },
      ]);

      expect(prompt).toContain('Test Item');
      expect(prompt).toContain('Electronics');
      expect(prompt).toContain('brand');
      expect(prompt).toContain('Sony');
    });

    it('includes price estimate when provided', () => {
      const prompt = buildListingUserPrompt([
        {
          itemId: 'item-1',
          priceEstimate: 150,
        },
      ]);

      expect(prompt).toContain('€150');
    });

    it('includes photos count when provided', () => {
      const prompt = buildListingUserPrompt([
        {
          itemId: 'item-1',
          photosCount: 3,
        },
      ]);

      expect(prompt).toContain('3');
    });

    it('includes resolved attributes from vision analysis', () => {
      const resolvedAttrs = new Map<string, ResolvedAttributes>();
      resolvedAttrs.set('item-1', {
        itemId: 'item-1',
        brand: {
          value: 'IKEA',
          confidence: 'HIGH',
          evidenceRefs: [{ type: 'logo', value: 'IKEA', score: 0.9 }],
        },
        color: {
          value: 'white',
          confidence: 'MED',
          evidenceRefs: [{ type: 'color', value: 'white', score: 0.7 }],
        },
      });

      const prompt = buildListingUserPrompt([{ itemId: 'item-1' }], resolvedAttrs);

      expect(prompt).toContain('Vision-extracted attributes');
      expect(prompt).toContain('brand');
      expect(prompt).toContain('IKEA');
      expect(prompt).toContain('HIGH');
      expect(prompt).toContain('color');
      expect(prompt).toContain('white');
    });

    it('includes suggested next photo when present', () => {
      const resolvedAttrs = new Map<string, ResolvedAttributes>();
      resolvedAttrs.set('item-1', {
        itemId: 'item-1',
        suggestedNextPhoto: 'Take a photo of the brand label',
      });

      const prompt = buildListingUserPrompt([{ itemId: 'item-1' }], resolvedAttrs);

      expect(prompt).toContain('Take a photo of the brand label');
    });

    it('includes visual facts summary', () => {
      const visualFacts = new Map<string, VisualFacts>();
      visualFacts.set('item-1', {
        itemId: 'item-1',
        dominantColors: [
          { name: 'blue', rgbHex: '***REMOVED***0000FF', pct: 50 },
          { name: 'white', rgbHex: '***REMOVED***FFFFFF', pct: 30 },
        ],
        ocrSnippets: [{ text: 'MODEL X100', confidence: 0.9 }],
        labelHints: [{ label: 'Furniture', score: 0.85 }],
        logoHints: [{ brand: 'IKEA', score: 0.9 }],
        extractionMeta: {
          provider: 'google',
          timingsMs: { total: 200 },
          imageCount: 1,
          imageHashes: ['abc'],
        },
      });

      const prompt = buildListingUserPrompt([{ itemId: 'item-1' }], undefined, visualFacts);

      expect(prompt).toContain('Visual evidence');
      expect(prompt).toContain('blue (50%)');
      expect(prompt).toContain('MODEL X100');
      expect(prompt).toContain('IKEA');
      expect(prompt).toContain('Furniture');
    });

    it('formats attribute confidence correctly', () => {
      const prompt = buildListingUserPrompt([
        {
          itemId: 'item-1',
          attributes: [{ key: 'brand', value: 'Test', confidence: 0.95 }],
        },
      ]);

      expect(prompt).toContain('HIGH');
    });

    it('formats medium confidence attribute correctly', () => {
      const prompt = buildListingUserPrompt([
        {
          itemId: 'item-1',
          attributes: [{ key: 'brand', value: 'Test', confidence: 0.6 }],
        },
      ]);

      expect(prompt).toContain('MED');
    });

    it('formats low confidence attribute correctly', () => {
      const prompt = buildListingUserPrompt([
        {
          itemId: 'item-1',
          attributes: [{ key: 'brand', value: 'Test', confidence: 0.3 }],
        },
      ]);

      expect(prompt).toContain('LOW');
    });

    // PR4 User Override Tests
    describe('user-provided attributes (PR4)', () => {
      it('marks user-provided attributes with [USER] tag', () => {
        const prompt = buildListingUserPrompt([
          {
            itemId: 'item-1',
            attributes: [{ key: 'brand', value: 'Apple', source: 'USER' }],
          },
        ]);

        expect(prompt).toContain('[USER]');
        expect(prompt).toContain('Apple');
      });

      it('treats user-provided attributes as HIGH confidence', () => {
        const prompt = buildListingUserPrompt([
          {
            itemId: 'item-1',
            attributes: [{ key: 'brand', value: 'Apple', source: 'USER', confidence: 0.5 }],
          },
        ]);

        // Even with 0.5 confidence, USER source should be HIGH
        expect(prompt).toContain('[USER]');
        expect(prompt).toContain('[HIGH]');
      });

      it('shows user-provided attributes before detected attributes', () => {
        const prompt = buildListingUserPrompt([
          {
            itemId: 'item-1',
            attributes: [
              { key: 'color', value: 'Red', source: 'DETECTED', confidence: 0.9 },
              { key: 'brand', value: 'Apple', source: 'USER' },
            ],
          },
        ]);

        // User-provided should come first in the prompt
        const userIndex = prompt.indexOf('User-provided attributes');
        const detectedIndex = prompt.indexOf('Detected attributes');
        expect(userIndex).toBeLessThan(detectedIndex);
      });

      it('does not override user-provided attributes with vision-detected ones', () => {
        const resolvedAttrs = new Map<string, ResolvedAttributes>();
        resolvedAttrs.set('item-1', {
          itemId: 'item-1',
          brand: {
            value: 'Samsung', // Vision detected Samsung
            confidence: 'HIGH',
            evidenceRefs: [{ type: 'logo', value: 'Samsung', score: 0.95 }],
          },
        });

        const prompt = buildListingUserPrompt(
          [
            {
              itemId: 'item-1',
              attributes: [{ key: 'brand', value: 'Apple', source: 'USER' }], // User said Apple
            },
          ],
          resolvedAttrs
        );

        // User's Apple should be included
        expect(prompt).toContain('Apple');
        expect(prompt).toContain('[USER]');
        // Vision's Samsung should NOT be in the vision section (since user override exists)
        const visionSection = prompt.indexOf('Vision-extracted attributes');
        if (visionSection >= 0) {
          const afterVision = prompt.slice(visionSection);
          expect(afterVision).not.toContain('Samsung');
        }
      });

      it('includes detected attributes that user has not overridden', () => {
        const resolvedAttrs = new Map<string, ResolvedAttributes>();
        resolvedAttrs.set('item-1', {
          itemId: 'item-1',
          brand: {
            value: 'Samsung',
            confidence: 'HIGH',
            evidenceRefs: [{ type: 'logo', value: 'Samsung', score: 0.95 }],
          },
          color: {
            value: 'Black',
            confidence: 'MED',
            evidenceRefs: [{ type: 'color', value: 'Black', score: 0.7 }],
          },
        });

        const prompt = buildListingUserPrompt(
          [
            {
              itemId: 'item-1',
              attributes: [{ key: 'brand', value: 'Apple', source: 'USER' }], // Only brand overridden
            },
          ],
          resolvedAttrs
        );

        // User's Apple brand should be in user section
        expect(prompt).toContain('Apple');
        expect(prompt).toContain('[USER]');
        // Vision's Black color should be included (not overridden)
        expect(prompt).toContain('Black');
      });

      it('marks detected attributes with [DETECTED] tag', () => {
        const prompt = buildListingUserPrompt([
          {
            itemId: 'item-1',
            attributes: [{ key: 'brand', value: 'Sony', source: 'DETECTED', confidence: 0.8 }],
          },
        ]);

        expect(prompt).toContain('[DETECTED]');
        expect(prompt).toContain('Sony');
      });

      it('includes condition in user attributes when user-edited', () => {
        const prompt = buildListingUserPrompt([
          {
            itemId: 'item-1',
            attributes: [
              { key: 'brand', value: 'Dell', source: 'USER' },
              { key: 'model', value: 'XPS 15', source: 'USER' },
              { key: 'color', value: 'Silver', source: 'USER' },
              { key: 'condition', value: 'Like New', source: 'USER' },
            ],
          },
        ]);

        // All user attributes should be marked as [USER] [HIGH]
        expect(prompt).toContain('Dell');
        expect(prompt).toContain('XPS 15');
        expect(prompt).toContain('Silver');
        expect(prompt).toContain('Like New');
        const userSection = prompt.slice(0, prompt.indexOf('Detected attributes') || prompt.length);
        expect(userSection).toContain('[USER]');
      });
    });
  });

  describe('parseListingResponse', () => {
    it('parses valid JSON response', () => {
      const content = JSON.stringify({
        title: 'Dell XPS 15 Laptop',
        description: 'High-performance laptop in excellent condition',
        warnings: ['Verify model number'],
        missingInfo: ['Storage capacity'],
      });

      const result = parseListingResponse(content);

      expect(result.title).toBe('Dell XPS 15 Laptop');
      expect(result.description).toBe('High-performance laptop in excellent condition');
      expect(result.warnings).toContain('Verify model number');
      expect(result.missingInfo).toContain('Storage capacity');
    });

    it('parses JSON with suggested draft updates', () => {
      const content = JSON.stringify({
        title: 'Test',
        suggestedDraftUpdates: [
          { field: 'title', value: 'New Title', confidence: 'HIGH', requiresConfirmation: false },
          { field: 'description', value: 'Desc', confidence: 'MED', requiresConfirmation: true },
        ],
      });

      const result = parseListingResponse(content);

      expect(result.suggestedDraftUpdates).toHaveLength(2);
      expect(result.suggestedDraftUpdates?.[0].field).toBe('title');
      expect(result.suggestedDraftUpdates?.[0].confidence).toBe('HIGH');
      expect(result.suggestedDraftUpdates?.[1].requiresConfirmation).toBe(true);
    });

    it('parses suggested next photo', () => {
      const content = JSON.stringify({
        title: 'Test',
        suggestedNextPhoto: 'Take a close-up of the brand label',
      });

      const result = parseListingResponse(content);

      expect(result.suggestedNextPhoto).toBe('Take a close-up of the brand label');
    });

    it('extracts JSON from mixed content', () => {
      const content = `Here is the listing:

{
  "title": "IKEA Bookshelf",
  "description": "White bookshelf in good condition"
}

Let me know if you need changes.`;

      const result = parseListingResponse(content);

      expect(result.title).toBe('IKEA Bookshelf');
      expect(result.description).toBe('White bookshelf in good condition');
    });

    it('falls back to text parsing when JSON is invalid', () => {
      const content = `Title: My Awesome Product
Description: This is a great product with many features.
Warnings: Check the condition`;

      const result = parseListingResponse(content);

      expect(result.title).toBe('My Awesome Product');
    });

    it('normalizes confidence values', () => {
      const content = JSON.stringify({
        suggestedDraftUpdates: [
          { field: 'title', value: 'Test', confidence: 'high' }, // lowercase
          { field: 'description', value: 'Test', confidence: 'MEDIUM' }, // invalid
        ],
      });

      const result = parseListingResponse(content);

      expect(result.suggestedDraftUpdates?.[0].confidence).toBe('HIGH');
      expect(result.suggestedDraftUpdates?.[1].confidence).toBe('MED'); // defaults to MED
    });

    it('handles null suggestedNextPhoto', () => {
      const content = JSON.stringify({
        title: 'Test',
        suggestedNextPhoto: null,
      });

      const result = parseListingResponse(content);

      expect(result.suggestedNextPhoto).toBeNull();
    });

    it('filters non-string warnings', () => {
      const content = JSON.stringify({
        title: 'Test',
        warnings: ['Valid warning', 123, null, 'Another warning'],
      });

      const result = parseListingResponse(content);

      expect(result.warnings).toEqual(['Valid warning', 'Another warning']);
    });

    it('filters non-string missingInfo', () => {
      const content = JSON.stringify({
        title: 'Test',
        missingInfo: ['Storage', null, 'RAM', 456],
      });

      const result = parseListingResponse(content);

      expect(result.missingInfo).toEqual(['Storage', 'RAM']);
    });

    it('returns empty result for completely unparseable content', () => {
      const content = 'Random text without any structure';

      const result = parseListingResponse(content);

      // Should not throw, may have partial or empty results
      expect(result).toBeDefined();
    });

    it('extracts title from text with various formats', () => {
      const testCases = [
        { input: 'Title: My Product', expected: 'My Product' },
        { input: 'TITLE: "Quoted Title"', expected: 'Quoted Title' },
      ];

      for (const { input, expected } of testCases) {
        const result = parseListingResponse(input);
        expect(result.title?.trim()).toBe(expected);
      }
    });
  });
});
