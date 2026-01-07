/**
 * Enrichment Module Types
 *
 * Defines types for the scan-to-enrichment pipeline that automatically
 * extracts vision facts, normalizes attributes, and generates listing drafts.
 */

/**
 * Enrichment processing stages.
 */
export type EnrichmentStage =
  | 'QUEUED'
  | 'VISION_STARTED'
  | 'VISION_DONE'
  | 'ATTRIBUTES_STARTED'
  | 'ATTRIBUTES_DONE'
  | 'DRAFT_STARTED'
  | 'DRAFT_DONE'
  | 'FAILED';

/**
 * Confidence tier for normalized attributes.
 */
export type AttributeConfidence = 'HIGH' | 'MED' | 'LOW';

/**
 * Source of attribute detection.
 */
export type AttributeSource =
  | 'VISION_OCR'
  | 'VISION_LOGO'
  | 'VISION_LABEL'
  | 'VISION_COLOR'
  | 'LLM_INFERRED'
  | 'USER';

/**
 * A normalized attribute with provenance.
 */
export type NormalizedAttribute = {
  /** Attribute key (e.g., "brand", "product_type", "color") */
  key: string;
  /** Attribute value */
  value: string;
  /** Confidence level */
  confidence: AttributeConfidence;
  /** Source of detection */
  source: AttributeSource;
  /** Raw evidence that led to this attribute */
  evidence?: string;
};

/**
 * Vision facts summary (capped and safe for API response).
 */
export type VisionFactsSummary = {
  /** Top OCR snippets (capped at 5) */
  ocrSnippets: string[];
  /** Detected logos/brands */
  logoHints: Array<{ name: string; confidence: number }>;
  /** Dominant colors */
  dominantColors: Array<{ name: string; hex: string; pct: number }>;
  /** Label hints from image classification */
  labelHints: string[];
};

/**
 * Generated listing draft.
 */
export type ListingDraft = {
  /** Suggested listing title */
  title: string;
  /** Suggested listing description */
  description: string;
  /** Fields the LLM marked as uncertain or missing */
  missingFields?: string[];
  /** Confidence in the draft quality */
  confidence: AttributeConfidence;
};

/**
 * Error info for failed enrichment.
 */
export type EnrichmentError = {
  /** Error code for programmatic handling */
  code: string;
  /** Human-readable error message */
  message: string;
  /** Stage where error occurred */
  stage: EnrichmentStage;
  /** Whether retry might succeed */
  retryable: boolean;
};

/**
 * Current status of an enrichment request.
 */
export type EnrichmentStatus = {
  /** Unique request ID */
  requestId: string;
  /** Correlation ID for tracing */
  correlationId: string;
  /** Item ID being enriched */
  itemId: string;
  /** Current processing stage */
  stage: EnrichmentStage;
  /** Vision facts summary (available after VISION_DONE) */
  visionFacts?: VisionFactsSummary;
  /** Normalized attributes (available after ATTRIBUTES_DONE) */
  normalizedAttributes?: NormalizedAttribute[];
  /** Generated draft (available after DRAFT_DONE) */
  draft?: ListingDraft;
  /** Error info if failed */
  error?: EnrichmentError;
  /** Timestamp when request was created */
  createdAt: number;
  /** Timestamp of last update */
  updatedAt: number;
  /** Processing timings in milliseconds */
  timings?: {
    vision?: number;
    attributes?: number;
    draft?: number;
    total?: number;
  };
};

/**
 * Request to enrich an item.
 */
export type EnrichRequest = {
  /** Item ID to associate results with */
  itemId: string;
  /** Base64-encoded image data */
  imageBase64: string;
  /** MIME type of the image */
  imageMimeType: 'image/jpeg' | 'image/png';
  /** Optional item context for better draft generation */
  itemContext?: {
    /** Current item title (if any) */
    title?: string;
    /** Current category */
    category?: string;
    /** Current condition */
    condition?: string;
    /** User's asking price (cents) */
    priceCents?: number;
  };
};

/**
 * Response to enrichment request (immediate).
 */
export type EnrichResponse = {
  /** Request ID for polling status */
  requestId: string;
  /** Correlation ID for tracing */
  correlationId: string;
};

/**
 * Configuration for the enrichment pipeline.
 */
export type EnrichmentConfig = {
  /** Timeout for vision extraction (ms) */
  visionTimeoutMs: number;
  /** Timeout for LLM draft generation (ms) */
  draftTimeoutMs: number;
  /** Maximum concurrent enrichments */
  maxConcurrent: number;
  /** Request retention time (ms) */
  resultRetentionMs: number;
  /** Enable draft generation (can be disabled to only do vision+attributes) */
  enableDraftGeneration: boolean;
  /** LLM model for draft generation */
  llmModel: string;
};
