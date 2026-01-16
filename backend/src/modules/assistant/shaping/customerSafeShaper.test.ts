/**
 * Tests for customer-safe response shaper.
 */

import { describe, it, expect } from 'vitest';
import {
  shapeCustomerSafeText,
  containsBannedTokens,
} from './customerSafeShaper.js';

describe('shapeCustomerSafeText', () => {
  it('should remove banned tokens (case-insensitive)', () => {
    const input = 'This is an Unknown brand with Generic features.';
    const result = shapeCustomerSafeText(input);
    expect(result).toBe('This is an brand with features.');
  });

  it('should remove confidence indicators', () => {
    const input = 'The confidence is 85% for this item.';
    const result = shapeCustomerSafeText(input);
    expect(result).toBe('The is for this item.');
  });

  it('should remove percentage patterns', () => {
    const input = 'Score: 58% accuracy.';
    const result = shapeCustomerSafeText(input);
    // "score" is a banned token, so it will be removed too
    expect(result).toBe(': accuracy.');
  });

  it('should remove decimal confidence scores', () => {
    const input = 'Confidence: 0.85 for this attribute.';
    const result = shapeCustomerSafeText(input);
    expect(result).toBe('for this attribute.');
  });

  it('should remove "might be" and "possibly"', () => {
    const input = 'This might be a Sony camera, possibly a DSLR.';
    const result = shapeCustomerSafeText(input);
    expect(result).toBe('This a Sony camera, a DSLR.');
  });

  it('should remove "cannot determine"', () => {
    const input = 'Cannot determine the exact model.';
    const result = shapeCustomerSafeText(input);
    expect(result).toBe('the exact model.');
  });

  it('should clean up extra whitespace', () => {
    const input = 'This  is   a    test.';
    const result = shapeCustomerSafeText(input);
    expect(result).toBe('This is a test.');
  });

  it('should remove empty parentheses after cleaning', () => {
    const input = 'Item (confidence: 0.5) is valid.';
    const result = shapeCustomerSafeText(input);
    expect(result).toBe('Item is valid.');
  });

  it('should handle multiple banned tokens in one string', () => {
    const input = 'Unknown brand, generic features, confidence 50%, possibly unbranded.';
    const result = shapeCustomerSafeText(input);
    // After removing all banned tokens and cleaning up
    expect(result).toBe('brand, features,.');
  });

  it('should preserve clean text unchanged', () => {
    const input = 'Sony Alpha 7 III, excellent condition, 24MP sensor.';
    const result = shapeCustomerSafeText(input);
    expect(result).toBe('Sony Alpha 7 III, excellent condition, 24MP sensor.');
  });

  it('should handle empty strings', () => {
    const result = shapeCustomerSafeText('');
    expect(result).toBe('');
  });

  it('should handle null/undefined gracefully', () => {
    // @ts-expect-error Testing null handling
    const resultNull = shapeCustomerSafeText(null);
    expect(resultNull).toBe(null);

    // @ts-expect-error Testing undefined handling
    const resultUndefined = shapeCustomerSafeText(undefined);
    expect(resultUndefined).toBe(undefined);
  });

  it('should remove "score" as standalone word', () => {
    const input = 'The score for this item is high.';
    const result = shapeCustomerSafeText(input);
    expect(result).toBe('The for this item is high.');
  });

  it('should not remove "score" when part of another word', () => {
    const input = 'The scorecard shows good results.';
    const result = shapeCustomerSafeText(input);
    // "scorecard" should remain intact
    expect(result).toContain('scorecard');
  });
});

describe('containsBannedTokens', () => {
  it('should detect banned tokens', () => {
    expect(containsBannedTokens('This is an unknown brand')).toBe(true);
    expect(containsBannedTokens('Generic product')).toBe(true);
    expect(containsBannedTokens('Confidence: 85%')).toBe(true);
  });

  it('should not detect clean text', () => {
    expect(containsBannedTokens('Sony Alpha 7 III camera')).toBe(false);
    expect(containsBannedTokens('Excellent condition item')).toBe(false);
  });

  it('should handle empty strings', () => {
    expect(containsBannedTokens('')).toBe(false);
  });
});
