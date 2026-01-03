export type VisionFeature = 'LABEL_DETECTION' | 'OBJECT_LOCALIZATION';

export type ClassificationHints = Record<string, unknown>;

export type ClassificationRequest = {
  requestId: string;
  correlationId: string;
  imageHash: string;
  buffer: Buffer;
  contentType: string;
  fileName: string;
  domainPackId: string;
  hints?: ClassificationHints;
  /** Request attribute enrichment via VisionExtractor */
  enrichAttributes?: boolean;
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
