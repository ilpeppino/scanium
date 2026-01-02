/**
 * Attribute Resolver
 *
 * Converts VisualFacts into structured, reusable item attributes
 * with per-field confidence. Runs deterministic, evidence-based rules.
 */

import { VisualFacts } from './types.js';

/**
 * Confidence tier for resolved attributes.
 */
export type AttributeConfidenceTier = 'HIGH' | 'MED' | 'LOW';

/**
 * Reference to the evidence supporting an attribute.
 */
export type EvidenceRef = {
  type: 'logo' | 'ocr' | 'color' | 'label';
  value: string;
  score?: number;
};

/**
 * A resolved attribute with value and confidence.
 */
export type ResolvedAttribute = {
  value: string;
  confidence: AttributeConfidenceTier;
  evidenceRefs: EvidenceRef[];
};

/**
 * All resolved attributes for an item.
 */
export type ResolvedAttributes = {
  itemId: string;
  brand?: ResolvedAttribute;
  model?: ResolvedAttribute;
  color?: ResolvedAttribute;
  secondaryColor?: ResolvedAttribute;
  material?: ResolvedAttribute;
  /** Suggested next photo when evidence is insufficient */
  suggestedNextPhoto?: string;
};

/**
 * Curated brand dictionary.
 * Start small with common household/electronics/furniture brands.
 * Extensible via configuration.
 */
const BRAND_DICTIONARY = new Set([
  // Furniture
  'IKEA', 'KALLAX', 'MALM', 'BILLY', 'HEMNES', 'LACK', 'POANG',
  'WEST ELM', 'CB2', 'CRATE', 'BARREL', 'POTTERY BARN',
  'ASHLEY', 'WAYFAIR', 'ARTICLE', 'FLOYD', 'BURROW',
  // Electronics
  'APPLE', 'SAMSUNG', 'SONY', 'LG', 'PANASONIC', 'PHILIPS', 'BOSE',
  'DELL', 'HP', 'LENOVO', 'ASUS', 'ACER', 'MSI', 'RAZER',
  'NINTENDO', 'PLAYSTATION', 'XBOX', 'LOGITECH', 'CORSAIR',
  'CANON', 'NIKON', 'FUJIFILM', 'GOPRO', 'DJI',
  'DYSON', 'ROOMBA', 'IROBOT', 'SHARK', 'BISSELL',
  'KITCHENAID', 'CUISINART', 'NINJA', 'VITAMIX', 'INSTANT POT',
  'KEURIG', 'NESPRESSO', 'BREVILLE',
  // Home goods
  'WILLIAMS SONOMA', 'LE CREUSET', 'STAUB', 'ALL CLAD', 'LODGE',
  'RUBBERMAID', 'OXON', 'SIMPLEHUMAN', 'BRABANTIA',
  'CASPER', 'PURPLE', 'TEMPUR', 'SEALY', 'SERTA',
  // Fashion/Accessories (common resale)
  'NIKE', 'ADIDAS', 'PUMA', 'REEBOK', 'NEW BALANCE',
  'NORTH FACE', 'PATAGONIA', 'COLUMBIA', 'ARCTERYX',
  'COACH', 'KATE SPADE', 'MICHAEL KORS', 'TORY BURCH',
  'LOUIS VUITTON', 'GUCCI', 'PRADA', 'CHANEL', 'HERMES',
  'LEVIS', 'GAP', 'ZARA', 'HM', 'UNIQLO',
  // Tools
  'DEWALT', 'MAKITA', 'MILWAUKEE', 'BOSCH', 'RYOBI', 'BLACK DECKER',
  'CRAFTSMAN', 'STANLEY', 'SNAP ON', 'HUSKY',
  // Outdoor
  'WEBER', 'TRAEGER', 'YETI', 'COLEMAN', 'REI',
  // Baby/Kids
  'GRACO', 'CHICCO', 'UPPABABY', 'BABY JOGGER', 'BRITAX',
  'FISHER PRICE', 'LITTLE TIKES', 'STEP2',
  // Sports
  'PELOTON', 'BOWFLEX', 'SCHWINN', 'TREK', 'GIANT', 'SPECIALIZED',
]);

/**
 * Generic words to reject as brand names.
 */
const GENERIC_WORDS = new Set([
  'MADE', 'MODEL', 'SERIAL', 'THE', 'AND', 'FOR', 'WITH', 'FROM',
  'TYPE', 'SIZE', 'COLOR', 'COLOUR', 'REF', 'NO', 'NUMBER', 'LOT',
  'BATCH', 'DATE', 'MFG', 'MFD', 'EXP', 'USE', 'BY', 'BEFORE',
  'WARRANTY', 'PATENT', 'PAT', 'REG', 'TM', 'TRADEMARK',
  'ASSEMBLED', 'PRINTED', 'DESIGNED', 'IMPORTED', 'PRODUCT',
  'ITEM', 'STYLE', 'ART', 'SKU', 'UPC', 'EAN', 'ISBN',
  'NEW', 'USED', 'CONDITION', 'GOOD', 'FAIR', 'EXCELLENT',
  'CM', 'MM', 'INCH', 'INCHES', 'FT', 'FEET', 'LB', 'LBS', 'KG', 'OZ',
  'VOLT', 'WATT', 'AMP', 'HZ', 'RPM', 'PSI',
]);

/**
 * Pattern for SKU-like model numbers.
 */
const MODEL_PATTERN = /^[A-Z]{0,3}[0-9]+[A-Z0-9\-\.]*$/i;
const MODEL_KEYWORD_PATTERN = /\b(model|type|ref|no|number|art|sku|serial)\b/i;

/**
 * Normalize text for comparison.
 */
function normalizeForComparison(text: string): string {
  return text.toUpperCase().replace(/[^A-Z0-9]/g, '');
}

/**
 * Check if a text matches a known brand.
 */
function matchBrandDictionary(text: string): string | null {
  const normalized = normalizeForComparison(text);

  // Direct match
  for (const brand of BRAND_DICTIONARY) {
    const normalizedBrand = normalizeForComparison(brand);
    if (normalized === normalizedBrand) {
      return brand;
    }
  }

  // Check if text starts with a known brand
  for (const brand of BRAND_DICTIONARY) {
    const normalizedBrand = normalizeForComparison(brand);
    if (normalized.startsWith(normalizedBrand) && normalized.length <= normalizedBrand.length + 5) {
      return brand;
    }
  }

  return null;
}

/**
 * Check if text looks like a brand name (capitalized, not generic).
 */
function looksLikeBrand(text: string): boolean {
  // Must start with capital letter
  if (!/^[A-Z]/.test(text)) return false;

  // Must be reasonable length
  if (text.length < 2 || text.length > 25) return false;

  // Must not be a generic word
  if (GENERIC_WORDS.has(text.toUpperCase())) return false;

  // Must not be all numbers
  if (/^\d+$/.test(text)) return false;

  // Must not look like a model number (letters + digits mixed)
  if (MODEL_PATTERN.test(text) && /\d/.test(text)) return false;

  return true;
}

/**
 * Check if text looks like a model number.
 */
function looksLikeModel(text: string, allOcrText: string[]): boolean {
  // Must have at least one digit
  if (!/\d/.test(text)) return false;

  // Must match SKU-like pattern
  if (!MODEL_PATTERN.test(text)) return false;

  // Reasonable length
  if (text.length < 3 || text.length > 30) return false;

  // Check for nearby model keywords in OCR
  const hasKeyword = allOcrText.some((t) => MODEL_KEYWORD_PATTERN.test(t));

  // Strong pattern (letters followed by numbers) or has keyword nearby
  const strongPattern = /^[A-Z]{1,4}[0-9]{2,}[A-Z0-9\-\.]*$/i.test(text);

  return strongPattern || hasKeyword;
}

/**
 * Resolve brand attribute from VisualFacts.
 */
function resolveBrand(facts: VisualFacts): ResolvedAttribute | undefined {
  const evidenceRefs: EvidenceRef[] = [];

  // Priority 1: Logo hints (strongest evidence)
  if (facts.logoHints && facts.logoHints.length > 0) {
    const topLogo = facts.logoHints[0];
    if (topLogo.score >= 0.5) {
      evidenceRefs.push({
        type: 'logo',
        value: topLogo.brand,
        score: topLogo.score,
      });

      return {
        value: topLogo.brand,
        confidence: topLogo.score >= 0.8 ? 'HIGH' : 'MED',
        evidenceRefs,
      };
    }
  }

  // Priority 2: OCR tokens matching brand dictionary
  for (const snippet of facts.ocrSnippets) {
    const matchedBrand = matchBrandDictionary(snippet.text);
    if (matchedBrand) {
      evidenceRefs.push({
        type: 'ocr',
        value: snippet.text,
        score: snippet.confidence,
      });

      // Dictionary match = higher confidence
      const confidence: AttributeConfidenceTier =
        (snippet.confidence ?? 0.7) >= 0.8 ? 'HIGH' : 'MED';

      return {
        value: matchedBrand,
        confidence,
        evidenceRefs,
      };
    }
  }

  // Priority 3: Brand-like OCR text (lower confidence)
  for (const snippet of facts.ocrSnippets) {
    if (looksLikeBrand(snippet.text)) {
      evidenceRefs.push({
        type: 'ocr',
        value: snippet.text,
        score: snippet.confidence,
      });

      return {
        value: snippet.text,
        confidence: 'LOW',
        evidenceRefs,
      };
    }
  }

  return undefined;
}

/**
 * Resolve model attribute from VisualFacts.
 */
function resolveModel(facts: VisualFacts): ResolvedAttribute | undefined {
  const allOcrText = facts.ocrSnippets.map((s) => s.text);
  const evidenceRefs: EvidenceRef[] = [];

  for (const snippet of facts.ocrSnippets) {
    if (looksLikeModel(snippet.text, allOcrText)) {
      evidenceRefs.push({
        type: 'ocr',
        value: snippet.text,
        score: snippet.confidence,
      });

      // Check for strong evidence (keyword nearby)
      const hasKeyword = allOcrText.some((t) => MODEL_KEYWORD_PATTERN.test(t));
      const strongPattern = /^[A-Z]{1,4}[0-9]{2,}[A-Z0-9\-\.]*$/i.test(snippet.text);

      const confidence: AttributeConfidenceTier =
        (hasKeyword && strongPattern) ? 'HIGH' :
        (hasKeyword || strongPattern) ? 'MED' : 'LOW';

      return {
        value: snippet.text,
        confidence,
        evidenceRefs,
      };
    }
  }

  return undefined;
}

/**
 * Resolve color attribute from VisualFacts.
 */
function resolveColor(facts: VisualFacts): {
  primary?: ResolvedAttribute;
  secondary?: ResolvedAttribute;
} {
  if (facts.dominantColors.length === 0) {
    return {};
  }

  const [first, second] = facts.dominantColors;

  // Clear winner if top color is significantly more prominent
  if (first.pct >= 40 || (first.pct >= 25 && (!second || first.pct >= second.pct * 1.5))) {
    return {
      primary: {
        value: first.name,
        confidence: first.pct >= 50 ? 'HIGH' : 'MED',
        evidenceRefs: [{
          type: 'color',
          value: `${first.name} (${first.pct}%)`,
          score: first.pct / 100,
        }],
      },
    };
  }

  // Ambiguous - return both with lower confidence
  if (second && second.pct >= 15) {
    return {
      primary: {
        value: first.name,
        confidence: 'LOW',
        evidenceRefs: [{
          type: 'color',
          value: `${first.name} (${first.pct}%)`,
          score: first.pct / 100,
        }],
      },
      secondary: {
        value: second.name,
        confidence: 'LOW',
        evidenceRefs: [{
          type: 'color',
          value: `${second.name} (${second.pct}%)`,
          score: second.pct / 100,
        }],
      },
    };
  }

  return {
    primary: {
      value: first.name,
      confidence: 'MED',
      evidenceRefs: [{
        type: 'color',
        value: `${first.name} (${first.pct}%)`,
        score: first.pct / 100,
      }],
    },
  };
}

/**
 * Resolve material hint from label hints.
 */
function resolveMaterial(facts: VisualFacts): ResolvedAttribute | undefined {
  const materialLabels = ['Wood', 'Metal', 'Plastic', 'Glass', 'Fabric', 'Leather',
    'Cotton', 'Wool', 'Silk', 'Ceramic', 'Stone', 'Marble', 'Concrete', 'Paper',
    'Cardboard', 'Rubber', 'Vinyl', 'Foam', 'Velvet', 'Suede', 'Denim', 'Linen'];

  for (const label of facts.labelHints) {
    const matchedMaterial = materialLabels.find(
      (m) => label.label.toLowerCase().includes(m.toLowerCase())
    );

    if (matchedMaterial && label.score >= 0.6) {
      return {
        value: matchedMaterial.toLowerCase(),
        confidence: label.score >= 0.8 ? 'MED' : 'LOW',
        evidenceRefs: [{
          type: 'label',
          value: label.label,
          score: label.score,
        }],
      };
    }
  }

  return undefined;
}

/**
 * Determine suggested next photo based on missing/weak evidence.
 */
function suggestNextPhoto(resolved: Partial<ResolvedAttributes>): string | undefined {
  // Check what's missing or weak
  const brandMissing = !resolved.brand;
  const brandWeak = resolved.brand?.confidence === 'LOW';
  const modelMissing = !resolved.model;
  const modelWeak = resolved.model?.confidence === 'LOW';
  const colorAmbiguous = resolved.secondaryColor !== undefined;

  if (brandMissing || brandWeak) {
    return 'Take a close-up photo of any brand labels, logos, or tags on the item.';
  }

  if (modelMissing || modelWeak) {
    return 'Take a close-up photo of any product labels, serial numbers, or model plates.';
  }

  if (colorAmbiguous) {
    return 'Take a well-lit photo showing the main color of the item more clearly.';
  }

  return undefined;
}

/**
 * Resolve all attributes from VisualFacts.
 */
export function resolveAttributes(
  itemId: string,
  facts: VisualFacts
): ResolvedAttributes {
  const brand = resolveBrand(facts);
  const model = resolveModel(facts);
  const { primary: color, secondary: secondaryColor } = resolveColor(facts);
  const material = resolveMaterial(facts);

  const partial = { brand, model, color, secondaryColor, material };
  const suggestedNextPhoto = suggestNextPhoto(partial);

  return {
    itemId,
    brand,
    model,
    color,
    secondaryColor,
    material,
    suggestedNextPhoto,
  };
}

/**
 * Get the brand dictionary for extension.
 */
export function getBrandDictionary(): ReadonlySet<string> {
  return BRAND_DICTIONARY;
}

/**
 * Add brands to the dictionary (for runtime extension).
 */
export function extendBrandDictionary(brands: string[]): void {
  for (const brand of brands) {
    BRAND_DICTIONARY.add(brand.toUpperCase());
  }
}
