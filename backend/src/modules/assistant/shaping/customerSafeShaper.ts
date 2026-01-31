/**
 * Customer-safe response shaper for assistant responses.
 *
 * Removes banned tokens and confidence indicators to ensure
 * all customer-visible text is professional and definitive.
 *
 * Phase 4 Implementation.
 */

import { BANNED_TOKENS, BANNED_PATTERNS } from './bannedTokens.js';

/**
 * Assistant mode enum - determines the type of response formatting.
 */
export type AssistantMode =
  | 'FIRST_SCAN'
  | 'ITEM_LIST'
  | 'ITEM_CARD'
  | 'ASSISTANT'
  | 'EDIT_SUGGESTIONS';

/**
 * Shape customer-visible text by removing banned tokens and patterns.
 *
 * @param text - The text to shape
 * @param mode - The assistant mode (for future mode-specific shaping)
 * @returns Shaped text with banned tokens removed
 */
export function shapeCustomerSafeText(text: string, mode?: AssistantMode): string {
  if (!text) return text;

  let shaped = text;

  // Step 1: Remove confidence patterns (must be done before token removal to avoid partial matches)
  for (const pattern of BANNED_PATTERNS) {
    shaped = shaped.replace(pattern, '');
  }

  // Step 2: Remove banned tokens (case-insensitive, whole word matching)
  for (const token of BANNED_TOKENS) {
    // Escape special regex characters in the token
    const escapedToken = token.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

    // Create regex for whole word matching (case-insensitive)
    // Use word boundaries for single words, or direct match for phrases
    const regex = token.includes(' ')
      ? new RegExp(escapedToken, 'gi')
      : new RegExp(`\\b${escapedToken}\\b`, 'gi');

    shaped = shaped.replace(regex, '');
  }

  // Step 3: Clean up whitespace and punctuation artifacts
  shaped = shaped
    .replace(/\s+/g, ' ') // Collapse multiple spaces
    .replace(/\s+([.,;:!?)])/g, '$1') // Remove space before punctuation
    .replace(/([([])\s+/g, '$1') // Remove space after opening brackets
    .replace(/\s*\(\s*\)/g, '') // Remove empty parentheses
    .replace(/\s*\[\s*\]/g, '') // Remove empty brackets
    .replace(/([.,;:])\s*\1+/g, '$1') // Remove consecutive duplicate punctuation (e.g., ",," -> ",")
    .trim();

  // Step 4: Mode-specific shaping (future extension point)
  switch (mode) {
    case 'ASSISTANT':
      // Sectioned listing output - future enhancement
      break;
    case 'ITEM_LIST':
    case 'ITEM_CARD':
      // Concise title + pricing lines - future enhancement
      break;
    case 'FIRST_SCAN':
    case 'EDIT_SUGGESTIONS':
      // Mode-specific formatting - future enhancement
      break;
  }

  return shaped;
}

/**
 * Shape all text fields in an object recursively.
 *
 * @param obj - Object to shape (must be JSON-serializable)
 * @param mode - The assistant mode
 * @returns Shaped object with all text fields cleaned
 */
export function shapeObjectFields<T>(obj: T, mode?: AssistantMode): T {
  if (obj === null || obj === undefined) {
    return obj;
  }

  if (typeof obj === 'string') {
    return shapeCustomerSafeText(obj, mode) as T;
  }

  if (Array.isArray(obj)) {
    return obj.map((item) => shapeObjectFields(item, mode)) as T;
  }

  if (typeof obj === 'object') {
    const shaped: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(obj)) {
      shaped[key] = shapeObjectFields(value, mode);
    }
    return shaped as T;
  }

  return obj;
}

/**
 * Check if text contains banned tokens or patterns.
 * Useful for testing and validation.
 *
 * @param text - Text to check
 * @returns True if text contains banned tokens
 */
export function containsBannedTokens(text: string): boolean {
  if (!text) return false;

  const lower = text.toLowerCase();

  // Check banned tokens
  for (const token of BANNED_TOKENS) {
    if (lower.includes(token.toLowerCase())) {
      return true;
    }
  }

  // Check banned patterns
  for (const pattern of BANNED_PATTERNS) {
    if (pattern.test(text)) {
      return true;
    }
  }

  return false;
}
