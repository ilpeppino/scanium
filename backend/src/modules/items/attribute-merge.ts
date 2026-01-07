/**
 * Attribute Merge Engine
 *
 * Pure functions for merging structured attributes with correct precedence rules:
 * - USER always wins for the same key
 * - DETECTED can fill missing keys
 * - DETECTED replaces older DETECTED only if confidence is higher or evidence is stronger
 */

import {
  StructuredAttribute,
  SuggestedAddition,
  ItemEnrichmentState,
  confidenceTierToScore,
  sourcePriority,
} from './attribute-types.js';

/**
 * Merge result containing merged attributes and any suggested additions.
 */
export type MergeResult = {
  /** Merged attributes */
  merged: StructuredAttribute[];
  /** Suggestions for user review (when existing has user edits) */
  suggestedAdditions: SuggestedAddition[];
  /** Whether any changes were made */
  hasChanges: boolean;
};

/**
 * Compare two attributes to determine which should win.
 * Returns:
 *   positive if `a` wins
 *   negative if `b` wins
 *   0 if equal (prefer existing)
 */
function compareAttributes(a: StructuredAttribute, b: StructuredAttribute): number {
  // Rule 1: Source priority (USER > DETECTED > DEFAULT > UNKNOWN)
  const sourceDiff = sourcePriority(a.source) - sourcePriority(b.source);
  if (sourceDiff !== 0) {
    return sourceDiff;
  }

  // Rule 2: Same source - compare confidence
  const confDiff = confidenceTierToScore(a.confidence) - confidenceTierToScore(b.confidence);
  if (Math.abs(confDiff) > 0.1) {
    return confDiff;
  }

  // Rule 3: Same confidence - prefer richer evidence
  const evidenceA = a.evidence?.length ?? 0;
  const evidenceB = b.evidence?.length ?? 0;
  if (evidenceA !== evidenceB) {
    return evidenceA - evidenceB;
  }

  // Rule 4: Tie - prefer newer (higher timestamp)
  return (a.updatedAt ?? 0) - (b.updatedAt ?? 0);
}

/**
 * Determine if an incoming attribute should replace an existing one.
 */
function shouldReplace(existing: StructuredAttribute, incoming: StructuredAttribute): boolean {
  // USER values are never replaced by non-USER
  if (existing.source === 'USER' && incoming.source !== 'USER') {
    return false;
  }

  // Compare using standard rules
  return compareAttributes(incoming, existing) > 0;
}

/**
 * Merge incoming attributes into existing attributes.
 *
 * Rules:
 * 1. USER values are never overwritten by DETECTED
 * 2. DETECTED fills missing keys
 * 3. DETECTED replaces older DETECTED only if confidence improves
 *
 * @param existing Current attributes
 * @param incoming New attributes to merge
 * @returns Merge result with merged attributes and any suggestions
 */
export function mergeAttributes(
  existing: StructuredAttribute[],
  incoming: StructuredAttribute[]
): MergeResult {
  const result: StructuredAttribute[] = [...existing];
  const keyToIndex = new Map<string, number>();
  const suggestedAdditions: SuggestedAddition[] = [];
  let hasChanges = false;

  // Build index of existing attributes
  existing.forEach((attr, idx) => {
    keyToIndex.set(attr.key, idx);
  });

  // Process incoming attributes
  for (const incomingAttr of incoming) {
    const existingIndex = keyToIndex.get(incomingAttr.key);

    if (existingIndex === undefined) {
      // New key - add it
      result.push(incomingAttr);
      keyToIndex.set(incomingAttr.key, result.length - 1);
      hasChanges = true;
    } else {
      // Existing key - check if we should replace
      const existingAttr = result[existingIndex];

      if (shouldReplace(existingAttr, incomingAttr)) {
        // Replace the existing value
        result[existingIndex] = incomingAttr;
        hasChanges = true;
      }
      // Otherwise keep existing (no change)
    }
  }

  return { merged: result, suggestedAdditions, hasChanges };
}

/**
 * Merge attributes when user has edited summary text.
 * Instead of auto-merging, generates suggestions for user review.
 *
 * @param existing Current attributes
 * @param incoming New detected attributes
 * @returns Suggested additions for user review
 */
export function computeSuggestedAdditions(
  existing: StructuredAttribute[],
  incoming: StructuredAttribute[]
): SuggestedAddition[] {
  const suggestions: SuggestedAddition[] = [];
  const existingByKey = new Map(existing.map((a) => [a.key, a]));

  for (const incomingAttr of incoming) {
    const existingAttr = existingByKey.get(incomingAttr.key);

    if (!existingAttr) {
      // New key - suggest adding
      suggestions.push({
        attribute: incomingAttr,
        reason: `Detected ${incomingAttr.key}: "${incomingAttr.value}" (${incomingAttr.confidence} confidence)`,
        action: 'add',
      });
    } else if (existingAttr.source !== 'USER') {
      // Existing non-USER value - check if incoming is better
      if (shouldReplace(existingAttr, incomingAttr)) {
        suggestions.push({
          attribute: incomingAttr,
          reason: `Updated ${incomingAttr.key} from "${existingAttr.value}" to "${incomingAttr.value}" (${incomingAttr.confidence} confidence)`,
          action: 'replace',
          existingValue: existingAttr.value,
        });
      }
    }
    // Don't suggest replacing USER values
  }

  return suggestions;
}

/**
 * Stable attribute key ordering for summary text generation.
 * Keys are ordered by importance/convention.
 */
const ATTRIBUTE_KEY_ORDER: readonly string[] = [
  'category',
  'brand',
  'product_type',
  'model',
  'color',
  'secondary_color',
  'size',
  'material',
  'condition',
] as const;

/**
 * Get the sort index for an attribute key.
 * Known keys come first in defined order, unknown keys come last alphabetically.
 */
function getKeyOrder(key: string): number {
  const idx = ATTRIBUTE_KEY_ORDER.indexOf(key);
  return idx >= 0 ? idx : ATTRIBUTE_KEY_ORDER.length + 1;
}

/**
 * Format attributes into summary text with stable ordering.
 *
 * Format:
 * ```
 * Category: Electronics
 * Brand: Sony
 * Color: Black
 * ```
 *
 * @param attributes Structured attributes to format
 * @returns Formatted summary text
 */
export function formatSummaryText(attributes: StructuredAttribute[]): string {
  if (attributes.length === 0) {
    return '';
  }

  // Sort attributes by key order
  const sorted = [...attributes].sort((a, b) => {
    const orderA = getKeyOrder(a.key);
    const orderB = getKeyOrder(b.key);
    if (orderA !== orderB) {
      return orderA - orderB;
    }
    // Same order - sort alphabetically
    return a.key.localeCompare(b.key);
  });

  // Format each attribute
  const lines = sorted.map((attr) => {
    const label = formatKeyLabel(attr.key);
    return `${label}: ${attr.value}`;
  });

  return lines.join('\n');
}

/**
 * Format an attribute key as a human-readable label.
 * e.g., "product_type" -> "Product Type"
 */
function formatKeyLabel(key: string): string {
  return key
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(' ');
}

/**
 * Parse summary text back into attribute key-value pairs.
 * This is the inverse of formatSummaryText, used when user edits summary.
 *
 * @param summaryText The summary text to parse
 * @returns Parsed key-value pairs
 */
export function parseSummaryText(summaryText: string): Array<{ key: string; value: string }> {
  const lines = summaryText.split('\n').filter((line) => line.trim());
  const result: Array<{ key: string; value: string }> = [];

  for (const line of lines) {
    const colonIndex = line.indexOf(':');
    if (colonIndex === -1) continue;

    const label = line.substring(0, colonIndex).trim();
    const value = line.substring(colonIndex + 1).trim();

    if (label && value) {
      // Convert label back to key format
      const key = label.toLowerCase().replace(/\s+/g, '_');
      result.push({ key, value });
    }
  }

  return result;
}

/**
 * Apply enrichment to an item's state.
 * Handles merge logic based on whether user has edited summary text.
 *
 * @param currentState Current enrichment state
 * @param incomingAttributes New detected attributes
 * @returns Updated enrichment state
 */
export function applyEnrichment(
  currentState: ItemEnrichmentState,
  incomingAttributes: StructuredAttribute[]
): ItemEnrichmentState {
  const now = Date.now();

  if (currentState.summaryTextUserEdited) {
    // User has edited - compute suggestions instead of auto-merging
    const newSuggestions = computeSuggestedAdditions(
      currentState.attributesStructured,
      incomingAttributes
    );

    // Merge with existing suggestions (avoid duplicates)
    const existingSuggestionKeys = new Set(
      currentState.suggestedAdditions.map((s) => s.attribute.key)
    );
    const combinedSuggestions = [
      ...currentState.suggestedAdditions,
      ...newSuggestions.filter((s) => !existingSuggestionKeys.has(s.attribute.key)),
    ];

    return {
      ...currentState,
      suggestedAdditions: combinedSuggestions,
      lastEnrichmentAt: now,
    };
  } else {
    // Auto-merge allowed
    const mergeResult = mergeAttributes(currentState.attributesStructured, incomingAttributes);

    return {
      ...currentState,
      attributesStructured: mergeResult.merged,
      summaryText: formatSummaryText(mergeResult.merged),
      suggestedAdditions: [],
      lastEnrichmentAt: now,
    };
  }
}

/**
 * Apply user edits from summary text.
 * Parses the text and updates attributes with USER source.
 *
 * @param currentState Current enrichment state
 * @param editedSummaryText The user-edited summary text
 * @returns Updated enrichment state
 */
export function applyUserSummaryEdit(
  currentState: ItemEnrichmentState,
  editedSummaryText: string
): ItemEnrichmentState {
  const now = Date.now();
  const parsed = parseSummaryText(editedSummaryText);

  // Build map of current attributes
  const currentByKey = new Map(currentState.attributesStructured.map((a) => [a.key, a]));

  // Update or add attributes from parsed text
  for (const { key, value } of parsed) {
    const existing = currentByKey.get(key);

    if (!existing || existing.value !== value) {
      // Value changed or new key - mark as USER
      currentByKey.set(key, {
        key,
        value,
        source: 'USER',
        confidence: 'HIGH', // User values are authoritative
        updatedAt: now,
      });
    }
  }

  // Remove keys that were deleted from summary
  const parsedKeys = new Set(parsed.map((p) => p.key));
  for (const key of currentByKey.keys()) {
    if (!parsedKeys.has(key)) {
      // Key was removed by user - keep it but could mark as deleted
      // For now, we keep all keys (user can explicitly clear values)
    }
  }

  const updatedAttributes = Array.from(currentByKey.values());

  return {
    ...currentState,
    attributesStructured: updatedAttributes,
    summaryText: editedSummaryText,
    summaryTextUserEdited: true,
    suggestedAdditions: [], // Clear suggestions after user edit
    lastEnrichmentAt: now,
  };
}

/**
 * Accept a suggested addition, merging it into the attributes.
 *
 * @param currentState Current enrichment state
 * @param suggestionIndex Index of the suggestion to accept
 * @returns Updated enrichment state
 */
export function acceptSuggestion(
  currentState: ItemEnrichmentState,
  suggestionIndex: number
): ItemEnrichmentState {
  if (suggestionIndex < 0 || suggestionIndex >= currentState.suggestedAdditions.length) {
    return currentState;
  }

  const suggestion = currentState.suggestedAdditions[suggestionIndex];
  const mergeResult = mergeAttributes(currentState.attributesStructured, [suggestion.attribute]);

  // Remove the accepted suggestion
  const remainingSuggestions = currentState.suggestedAdditions.filter((_, i) => i !== suggestionIndex);

  // Regenerate summary text to include the new attribute
  const newSummaryText = formatSummaryText(mergeResult.merged);

  return {
    ...currentState,
    attributesStructured: mergeResult.merged,
    summaryText: newSummaryText,
    suggestedAdditions: remainingSuggestions,
    lastEnrichmentAt: Date.now(),
  };
}

/**
 * Dismiss a suggested addition without accepting it.
 *
 * @param currentState Current enrichment state
 * @param suggestionIndex Index of the suggestion to dismiss
 * @returns Updated enrichment state
 */
export function dismissSuggestion(
  currentState: ItemEnrichmentState,
  suggestionIndex: number
): ItemEnrichmentState {
  if (suggestionIndex < 0 || suggestionIndex >= currentState.suggestedAdditions.length) {
    return currentState;
  }

  return {
    ...currentState,
    suggestedAdditions: currentState.suggestedAdditions.filter((_, i) => i !== suggestionIndex),
  };
}

/**
 * Create initial enrichment state from detected attributes.
 *
 * @param detectedAttributes Initial detected attributes
 * @returns Initial enrichment state
 */
export function createInitialEnrichmentState(
  detectedAttributes: StructuredAttribute[]
): ItemEnrichmentState {
  return {
    attributesStructured: detectedAttributes,
    summaryText: formatSummaryText(detectedAttributes),
    summaryTextUserEdited: false,
    suggestedAdditions: [],
    lastEnrichmentAt: Date.now(),
  };
}
