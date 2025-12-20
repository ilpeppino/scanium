/**
 * Unit tests for TokenGenerator
 */

import { describe, it, expect } from 'vitest';
import { TokenGenerator } from './token-generator.js';

describe('TokenGenerator', () => {
  const generator = new TokenGenerator();

  describe('generateTokens', () => {
    it('should generate tokens from category name', () => {
      const tokens = generator.generateTokens('Coffee Maker', ['Home & Garden', 'Coffee Maker']);

      expect(tokens).toContain('coffee');
      expect(tokens).toContain('maker');
      expect(tokens).toContain('coffee maker');
    });

    it('should normalize text (lowercase, & to and)', () => {
      const tokens = generator.generateTokens('Bread & Butter', ['Food', 'Bread & Butter']);

      // "&" is replaced with "and", which creates "bread and butter"
      // But "and" is denylisted, so bigrams containing "and" are excluded
      // We only get "bread" and "butter" as individual tokens
      expect(tokens).toContain('bread');
      expect(tokens).toContain('butter');
      expect(tokens).not.toContain('&');
      expect(tokens).not.toContain('and'); // denylisted
      expect(tokens).not.toContain('bread and'); // bigram excluded due to "and"
      expect(tokens).not.toContain('and butter'); // bigram excluded due to "and"
    });

    it('should exclude denylisted tokens', () => {
      const tokens = generator.generateTokens('Other Misc Parts', ['Other Misc Parts']);

      expect(tokens).not.toContain('other');
      expect(tokens).not.toContain('misc');
      expect(tokens).not.toContain('parts');
    });

    it('should generate bigrams for specificity', () => {
      const tokens = generator.generateTokens('Laptop Computer', ['Electronics', 'Laptop Computer']);

      expect(tokens).toContain('laptop computer');
    });

    it('should limit token count', () => {
      const generatorWithLimit = new TokenGenerator({ maxTokensPerCategory: 5 });
      const tokens = generatorWithLimit.generateTokens(
        'Very Long Category Name With Many Words',
        ['Root', 'Very Long Category Name With Many Words']
      );

      expect(tokens.length).toBeLessThanOrEqual(5);
    });

    it('should produce deterministic output', () => {
      const tokens1 = generator.generateTokens('Coffee Maker', ['Home', 'Coffee Maker']);
      const tokens2 = generator.generateTokens('Coffee Maker', ['Home', 'Coffee Maker']);

      expect(tokens1).toEqual(tokens2);
    });

    it('should handle special characters', () => {
      const tokens = generator.generateTokens('Phone/Tablet Cases', ['Accessories', 'Phone/Tablet Cases']);

      expect(tokens).toContain('phone');
      expect(tokens).toContain('tablet');
      expect(tokens).toContain('cases');
    });

    it('should include tokens from parent categories', () => {
      const tokens = generator.generateTokens('Coffee Maker', ['Home & Garden', 'Kitchen', 'Coffee Maker']);

      // Should include tokens from "Kitchen" parent
      expect(tokens).toContain('kitchen');
    });
  });

  describe('getDenylist', () => {
    it('should return the denylist', () => {
      const denylist = TokenGenerator.getDenylist();

      expect(denylist).toContain('other');
      expect(denylist).toContain('misc');
      expect(denylist).toContain('parts');
      expect(Array.isArray(denylist)).toBe(true);
    });
  });
});
