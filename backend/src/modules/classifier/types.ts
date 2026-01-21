import { VisualFacts } from '../vision/types.js';

export type VisionFeature =
  | 'LABEL_DETECTION'
  | 'OBJECT_LOCALIZATION'
  | 'TEXT_DETECTION'
  | 'DOCUMENT_TEXT_DETECTION'
  | 'IMAGE_PROPERTIES'
  | 'LOGO_DETECTION';

export type ClassificationHints = Record<string, unknown>;

/** Recent correction for local learning overlay */
export type RecentCorrection = {
  originalCategoryId: string;
  correctedCategoryId: string;
  correctedCategoryName: string;
  correctedAt: number; // Timestamp in ms
  visualFingerprint?: string; // Simplified visual context (e.g., dominant colors, detected brands)
};

export type ClassificationRequest = {
  requestId: string;
  correlationId: string;
  imageHash: string;
  buffer: Buffer;
  contentType: string;
  fileName: string;
  domainPackId: string;
  hints?: ClassificationHints;
  /** Recent corrections for local learning overlay */
  recentCorrections?: RecentCorrection[];
  /** Request attribute enrichment via VisionExtractor */
  enrichAttributes?: boolean;
  /** W3C Trace Context for distributed tracing */
  traceContext?: {
    traceId: string;      // 32 hex chars - identifies the entire trace
    spanId: string;       // 16 hex chars - identifies this backend operation
    parentSpanId: string; // 16 hex chars - identifies the parent span (mobile)
    flags: string;        // 2 hex chars - trace flags
  };
};

export type VisionLabel = {
  description: string;
  score: number;
};

export type ClassificationSignals = {
  labels: VisionLabel[];
};

export type ProviderResponse = {
  provider: 'google-vision' | 'mock';
  signals: ClassificationSignals;
  visionMs?: number;
  visualFacts?: VisualFacts;
};

/** Confidence tier for extracted attributes */
export type AttributeConfidenceTier = 'HIGH' | 'MED' | 'LOW';

/** Evidence reference for an extracted attribute */
export type AttributeEvidence = {
  type: 'logo' | 'ocr' | 'color' | 'label';
  value: string;
  score?: number;
};

/** An enriched attribute with value, confidence, and evidence */
export type EnrichedAttribute = {
  value: string;
  confidence: AttributeConfidenceTier;
  /** Numeric confidence score (0-1) for sorting/filtering */
  confidenceScore: number;
  evidenceRefs: AttributeEvidence[];
};

/** Enriched attributes extracted via VisionExtractor */
export type EnrichedAttributes = {
  brand?: EnrichedAttribute;
  model?: EnrichedAttribute;
  color?: EnrichedAttribute;
  secondaryColor?: EnrichedAttribute;
  material?: EnrichedAttribute;
  /** Suggested next photo when evidence is insufficient */
  suggestedNextPhoto?: string;
};

export type VisionAttributeSummary = {
  colors: Array<{ name: string; hex: string; score: number }>;
  ocrText: string;
  logos: Array<{ name: string; score: number }>;
  labels: Array<{ name: string; score: number }>;
  brandCandidates: string[];
  modelCandidates: string[];
};

export type VisionStats = {
  attempted: boolean;
  /** The Vision provider used for enrichment ('google-vision' | 'mock') */
  visionProvider: 'google-vision' | 'mock';
  visionExtractions: number;
  visionCacheHits: number;
  visionErrors: number;
};

export type ClassificationResult = {
  requestId: string;
  correlationId: string;
  domainPackId: string;
  domainCategoryId: string | null;
  confidence: number | null;
  label?: string | null;
  /** Static attributes from domain pack category */
  attributes: Record<string, string>;
  /** Enriched attributes extracted via VisionExtractor (when enrichAttributes=true) */
  enrichedAttributes?: EnrichedAttributes;
  /** Raw visual facts for evidence (OCR, logos, colors, labels) */
  visualFacts?: VisualFacts;
  /** Summary of visual attributes for clients */
  visionAttributes?: VisionAttributeSummary;
  /** Vision execution stats for observability */
  visionStats?: VisionStats;
  provider: ProviderResponse['provider'];
  providerUnavailable?: boolean;
  cacheHit?: boolean;
  timingsMs: {
    total: number;
    vision?: number;
    mapping?: number;
    /** Time spent on attribute enrichment (ms) */
    enrichment?: number;
  };
};

/** Classification mode for hypothesis generation */
export type ClassificationMode = 'single' | 'multi-hypothesis';

/** A single classification hypothesis from reasoning layer */
export type ClassificationHypothesis = {
  domainCategoryId: string;
  label: string;
  confidence: number; // 0-1
  confidenceBand: 'HIGH' | 'MED' | 'LOW';
  explanation: string; // 1-2 sentences
  attributes: Record<string, string>;
  visualEvidence?: {
    matchedLabels?: string[];
    detectedBrands?: string[];
    dominantColors?: string[];
    ocrHints?: string[];
  };
};

/** Multi-hypothesis classification result */
export type MultiHypothesisResult = {
  requestId: string;
  correlationId: string;
  domainPackId: string;
  hypotheses: ClassificationHypothesis[]; // 3-5 ranked
  globalConfidence: number; // 0-100
  needsRefinement: boolean; // true if < 70%
  refinementReason?: string;
  provider: 'openai' | 'claude' | 'mock';
  timingsMs: {
    total: number;
    perception?: number;
    reasoning?: number;
  };
};
