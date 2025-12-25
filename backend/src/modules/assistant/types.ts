export type AssistantRole = 'USER' | 'ASSISTANT' | 'SYSTEM';

// ============================================================================
// Personalization Preferences
// ============================================================================

/**
 * Tone preference for assistant responses.
 */
export type AssistantTone = 'NEUTRAL' | 'FRIENDLY' | 'PROFESSIONAL';

/**
 * Region preference (affects currency, marketplace mentions).
 */
export type AssistantRegion = 'NL' | 'DE' | 'BE' | 'FR' | 'UK' | 'US' | 'EU';

/**
 * Unit system preference.
 */
export type AssistantUnits = 'METRIC' | 'IMPERIAL';

/**
 * Verbosity preference.
 */
export type AssistantVerbosity = 'CONCISE' | 'NORMAL' | 'DETAILED';

/**
 * User's assistant personalization preferences.
 * Sent with each request, not stored server-side.
 */
export type AssistantPrefs = {
  /** Language code (e.g., 'EN', 'NL', 'DE') */
  language?: string;
  /** Response tone */
  tone?: AssistantTone;
  /** Region for currency/marketplace context */
  region?: AssistantRegion;
  /** Unit system */
  units?: AssistantUnits;
  /** Response verbosity */
  verbosity?: AssistantVerbosity;
};

// ============================================================================
// Structured Response Types (for PR5 template-driven responses)
// ============================================================================

/**
 * Structured selling help response sections.
 */
export type StructuredSellingHelp = {
  /** Suggested title with placeholders filled */
  suggestedTitle?: {
    value: string;
    confidence: ConfidenceTier;
  };
  /** Suggested description with sections */
  suggestedDescription?: {
    value: string;
    sections: Array<{ id: string; label: string; content: string }>;
    confidence: ConfidenceTier;
  };
  /** Missing info checklist */
  missingInfoChecklist?: string[];
  /** Buyer FAQ suggestions */
  buyerFaqSuggestions?: string[];
  /** Template pack used */
  templatePackId?: string;
};

export type AssistantMessage = {
  role: AssistantRole;
  content: string;
  timestamp: number;
  itemContextIds?: string[];
};

export type AssistantActionType =
  | 'APPLY_DRAFT_UPDATE'
  | 'ADD_ATTRIBUTES'
  | 'COPY_TEXT'
  | 'OPEN_POSTING_ASSIST'
  | 'OPEN_SHARE'
  | 'OPEN_URL'
  | 'SUGGEST_NEXT_PHOTO';

export type AssistantAction = {
  type: AssistantActionType;
  payload?: Record<string, string>;
  /** Label to display on the action button/chip */
  label?: string;
  /** Whether this action requires user confirmation (e.g., for LOW confidence) */
  requiresConfirmation?: boolean;
};

/**
 * Confidence tier for assistant responses.
 * HIGH: Strong visual evidence supports the answer.
 * MED: Some evidence, but not conclusive.
 * LOW: Insufficient evidence; response is speculative.
 */
export type ConfidenceTier = 'HIGH' | 'MED' | 'LOW';

/**
 * Evidence bullet derived from VisualFacts.
 */
export type EvidenceBullet = {
  /** Type of evidence */
  type: 'ocr' | 'color' | 'label' | 'logo';
  /** Human-readable evidence statement */
  text: string;
};

/**
 * Suggested attribute with confidence.
 */
export type SuggestedAttribute = {
  /** Attribute key (brand, model, color, etc.) */
  key: string;
  /** Suggested value */
  value: string;
  /** Confidence tier for this suggestion */
  confidence: ConfidenceTier;
  /** Source of suggestion (ocr, logo, color, etc.) */
  source?: string;
};

/**
 * Suggested draft updates (title, description).
 */
export type SuggestedDraftUpdate = {
  /** Field to update */
  field: 'title' | 'description';
  /** Suggested value */
  value: string;
  /** Confidence tier for this suggestion */
  confidence: ConfidenceTier;
  /** Whether this requires user confirmation before applying */
  requiresConfirmation?: boolean;
};

export type AssistantResponse = {
  content: string;
  actions?: AssistantAction[];
  citationsMetadata?: Record<string, string>;
  /** Confidence tier for the response (when visual evidence is used) */
  confidenceTier?: ConfidenceTier;
  /** Evidence bullets referencing VisualFacts */
  evidence?: EvidenceBullet[];
  /** Suggested attributes derived from visual evidence */
  suggestedAttributes?: SuggestedAttribute[];
  /** Suggested draft updates (title, description) */
  suggestedDraftUpdates?: SuggestedDraftUpdate[];
  /** Suggested next photo instruction when evidence is insufficient */
  suggestedNextPhoto?: string;
  /** Structured selling help (template-driven) */
  structuredHelp?: StructuredSellingHelp;
};

export type ItemAttributeSnapshot = {
  key: string;
  value: string;
  confidence?: number;
};

/**
 * Metadata for an image attached to an item in the assistant request.
 * Images are passed in-memory only; not stored on disk.
 */
export type ItemImageMetadata = {
  /** Original filename from the upload */
  filename: string;
  /** MIME type (image/jpeg or image/png) */
  mimeType: 'image/jpeg' | 'image/png';
  /** File size in bytes */
  sizeBytes: number;
  /** Base64-encoded image data */
  base64Data: string;
};

export type ItemContextSnapshot = {
  itemId: string;
  title?: string | null;
  description?: string | null;
  category?: string | null;
  confidence?: number | null;
  attributes?: ItemAttributeSnapshot[];
  priceEstimate?: number | null;
  photosCount?: number;
  exportProfileId?: string;
  /** Optional images attached for visual context (max 3 per item) */
  itemImages?: ItemImageMetadata[];
};

export type ExportProfileSnapshot = {
  id: string;
  displayName: string;
};

/**
 * Re-export VisualFacts and ResolvedAttributes for convenience
 */
export type { VisualFacts } from '../vision/types.js';
export type { ResolvedAttributes, ResolvedAttribute, EvidenceRef, AttributeConfidenceTier } from '../vision/attribute-resolver.js';

export type AssistantChatRequest = {
  items: ItemContextSnapshot[];
  history?: AssistantMessage[];
  message: string;
  exportProfile?: ExportProfileSnapshot;
  /** User's personalization preferences */
  assistantPrefs?: AssistantPrefs;
};

/**
 * Extended request with visual facts for internal processing.
 */
export type AssistantChatRequestWithVision = AssistantChatRequest & {
  /** Visual facts extracted from item images */
  visualFacts?: Map<string, import('../vision/types.js').VisualFacts>;
};
