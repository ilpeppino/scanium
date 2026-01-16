/**
 * Banned tokens list for customer-safe response shaping.
 *
 * These tokens are removed from all customer-visible text to ensure
 * responses are definitive and professional.
 *
 * Phase 4 Implementation.
 */

/**
 * List of banned tokens (case-insensitive).
 * These indicate uncertainty or lack of confidence.
 */
export const BANNED_TOKENS = [
  'unknown',
  'generic',
  'unbranded',
  'confidence',
  '%',
  'score',
  'might be',
  'possibly',
  'cannot determine',
] as const;

/**
 * Regex patterns for banned confidence-like expressions.
 * Matches patterns like "58%", "confidence: 0.58", etc.
 */
export const BANNED_PATTERNS = [
  // Percentage patterns: "58%", "confidence 58%", etc.
  /\b\d{1,3}%/gi,

  // Decimal confidence scores: "confidence: 0.58", "score: 0.8", etc.
  /\b(?:confidence|score)\s*:?\s*0?\.\d+\b/gi,

  // Confidence ranges: "confidence between 50-80%"
  /\bconfidence\s+between\s+\d+\s*-\s*\d+%?/gi,

  // Pattern: "(confidence: ...)" or "[score: ...]"
  /[(\[]\s*(?:confidence|score)\s*:?\s*[^\])]+[)\]]/gi,
] as const;
