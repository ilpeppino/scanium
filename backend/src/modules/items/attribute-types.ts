/**
 * Structured Attribute Types
 *
 * Canonical attribute model with full provenance tracking for the enrichment pipeline.
 * These types are used to:
 * 1. Store detected attributes with confidence and evidence
 * 2. Track user edits vs system detections
 * 3. Support merge operations that respect user authority
 */

/**
 * Well-known attribute keys for type safety.
 * Extensible - unknown keys are allowed but these are commonly used.
 */
export type AttributeKey =
  | 'brand'
  | 'model'
  | 'color'
  | 'secondary_color'
  | 'material'
  | 'product_type'
  | 'size'
  | 'condition'
  | 'category'
  | string; // Allow arbitrary keys

/**
 * Source/provenance of an attribute value.
 * Determines merge priority: USER > DETECTED > DEFAULT > UNKNOWN
 */
export type AttributeSource = 'USER' | 'DETECTED' | 'DEFAULT' | 'UNKNOWN';

/**
 * Confidence tier for attribute values.
 */
export type AttributeConfidence = 'HIGH' | 'MED' | 'LOW';

/**
 * Evidence type indicating how the value was detected.
 */
export type EvidenceType = 'OCR' | 'LOGO' | 'COLOR' | 'LABEL' | 'LLM' | 'RULE';

/**
 * Reference to evidence supporting an attribute value.
 */
export type EvidenceRef = {
  /** Type of evidence */
  type: EvidenceType;
  /** Raw value from detection (e.g., OCR text, logo name) */
  rawValue: string;
  /** Optional score from detection (0-1) */
  score?: number;
  /** Optional reference to image (hash or cropId) */
  imageRef?: string;
};

/**
 * A structured attribute with full provenance.
 * This is the canonical format for storing and transmitting item attributes.
 */
export type StructuredAttribute = {
  /** Attribute key (e.g., "brand", "color") */
  key: AttributeKey;
  /** Attribute value */
  value: string;
  /** Source/provenance of the value */
  source: AttributeSource;
  /** Confidence level (required for DETECTED, optional for USER) */
  confidence: AttributeConfidence;
  /** Evidence references (for DETECTED values) */
  evidence?: EvidenceRef[];
  /** Timestamp when this value was set/updated (epoch ms) */
  updatedAt: number;
};

/**
 * A suggested addition for user review.
 * Generated when summaryTextUserEdited=true and new attributes are detected.
 */
export type SuggestedAddition = {
  /** The attribute being suggested */
  attribute: StructuredAttribute;
  /** Human-readable reason for the suggestion */
  reason: string;
  /** Whether this replaces an existing value or adds a new key */
  action: 'add' | 'replace';
  /** Existing value being replaced (if action='replace') */
  existingValue?: string;
};

/**
 * Complete enrichment state for an item.
 * This is the persisted state that combines structured attributes with summary text.
 */
export type ItemEnrichmentState = {
  /** Structured attributes with provenance */
  attributesStructured: StructuredAttribute[];
  /** Generated summary text from attributes */
  summaryText: string;
  /** Whether user has manually edited the summary text */
  summaryTextUserEdited: boolean;
  /** Pending suggestions for user review (when summaryTextUserEdited=true) */
  suggestedAdditions: SuggestedAddition[];
  /** Timestamp of last enrichment update */
  lastEnrichmentAt?: number;
};

/**
 * Default empty enrichment state.
 */
export const EMPTY_ENRICHMENT_STATE: ItemEnrichmentState = {
  attributesStructured: [],
  summaryText: '',
  summaryTextUserEdited: false,
  suggestedAdditions: [],
};

/**
 * Convert a numeric confidence score (0-1) to a confidence tier.
 */
export function confidenceScoreToTier(score: number): AttributeConfidence {
  if (score >= 0.8) return 'HIGH';
  if (score >= 0.5) return 'MED';
  return 'LOW';
}

/**
 * Convert a confidence tier to a numeric score for comparison.
 * HIGH=1.0, MED=0.65, LOW=0.3
 */
export function confidenceTierToScore(tier: AttributeConfidence): number {
  switch (tier) {
    case 'HIGH':
      return 1.0;
    case 'MED':
      return 0.65;
    case 'LOW':
      return 0.3;
  }
}

/**
 * Source priority for merge decisions (higher = wins).
 */
export function sourcePriority(source: AttributeSource): number {
  switch (source) {
    case 'USER':
      return 100;
    case 'DETECTED':
      return 50;
    case 'DEFAULT':
      return 10;
    case 'UNKNOWN':
      return 0;
  }
}
