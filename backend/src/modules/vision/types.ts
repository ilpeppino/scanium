/**
 * Vision Extractor Types
 *
 * Defines the VisualFacts structure extracted from item images.
 * Used to ground assistant responses with visual evidence.
 */

/**
 * A dominant color extracted from an image.
 */
export type DominantColor = {
  /** Basic color name (black, white, gray, red, orange, yellow, green, blue, purple, pink, brown) */
  name: string;
  /** RGB hex value (e.g., "***REMOVED***FF5733") */
  rgbHex: string;
  /** Approximate percentage of the image covered by this color */
  pct: number;
};

/**
 * An OCR text snippet extracted from an image.
 */
export type OcrSnippet = {
  /** Extracted text (truncated/normalized) */
  text: string;
  /** Confidence score from Vision API (0-1) */
  confidence?: number;
};

/**
 * A label hint from image classification.
 */
export type LabelHint = {
  /** Label description (e.g., "Furniture", "Chair") */
  label: string;
  /** Confidence score (0-1) */
  score: number;
};

/**
 * A logo/brand hint detected in the image.
 */
export type LogoHint = {
  /** Brand name detected */
  brand: string;
  /** Confidence score (0-1) */
  score: number;
};

/**
 * Metadata about the extraction process.
 */
export type ExtractionMeta = {
  /** Provider used for extraction (e.g., "google-vision") */
  provider: string;
  /** Timing breakdown in milliseconds */
  timingsMs: {
    total: number;
    ocr?: number;
    labels?: number;
    logos?: number;
    colors?: number;
  };
  /** Number of images processed */
  imageCount: number;
  /** SHA-256 hashes of processed images (for cache keys) */
  imageHashes: string[];
  /** Whether result was served from cache */
  cacheHit?: boolean;
};

/**
 * Visual facts extracted from item images.
 * This is the primary output of the Vision Extractor.
 */
export type VisualFacts = {
  /** Item ID these facts belong to */
  itemId: string;
  /** Top dominant colors (up to 5) */
  dominantColors: DominantColor[];
  /** OCR text snippets (up to 10, truncated) */
  ocrSnippets: OcrSnippet[];
  /** Label hints from image classification (up to 10) */
  labelHints: LabelHint[];
  /** Logo/brand hints if detected (optional) */
  logoHints?: LogoHint[];
  /** Extraction metadata */
  extractionMeta: ExtractionMeta;
};

/**
 * Input image for vision extraction.
 */
export type VisionImageInput = {
  /** Base64-encoded image data */
  base64Data: string;
  /** MIME type */
  mimeType: 'image/jpeg' | 'image/png';
  /** Original filename (for logging) */
  filename?: string;
};

/**
 * Options for vision extraction.
 */
export type VisionExtractorOptions = {
  /** Enable OCR text detection */
  enableOcr?: boolean;
  /** Enable label detection */
  enableLabels?: boolean;
  /** Enable logo detection */
  enableLogos?: boolean;
  /** Enable dominant color extraction */
  enableColors?: boolean;
  /** Maximum OCR snippets to return */
  maxOcrSnippets?: number;
  /** Maximum label hints to return */
  maxLabelHints?: number;
  /** Maximum logo hints to return */
  maxLogoHints?: number;
  /** Maximum dominant colors to return */
  maxColors?: number;
};

/**
 * Result of a vision extraction request.
 */
export type VisionExtractionResult = {
  /** Whether extraction succeeded */
  success: boolean;
  /** Visual facts if successful */
  facts?: VisualFacts;
  /** Error message if failed */
  error?: string;
  /** Error code for programmatic handling */
  errorCode?: 'VISION_UNAVAILABLE' | 'INVALID_IMAGE' | 'QUOTA_EXCEEDED' | 'TIMEOUT';
};

/**
 * Google Vision API feature types we use.
 */
export type VisionFeatureType =
  | 'TEXT_DETECTION'
  | 'LABEL_DETECTION'
  | 'LOGO_DETECTION';
