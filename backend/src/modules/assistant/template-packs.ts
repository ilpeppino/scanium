/**
 * Template Packs for category-aware assistant responses.
 *
 * Each pack provides structured guidance for:
 * - Title patterns with placeholders
 * - Description sections
 * - Missing info questions (top 5)
 * - Buyer FAQ suggestions (top 5)
 */

/**
 * Template pack structure.
 */
export type TemplatePack = {
  packId: string;
  displayName: string;
  /** Categories this pack applies to (domain category IDs) */
  categories: string[];
  /** Title patterns with placeholders: {brand} {model} {color} {condition} {size} {material} {keyFeature} */
  titlePatterns: string[];
  /** Description sections to include */
  descriptionSections: DescriptionSection[];
  /** Top questions to ask about missing info */
  missingInfoQuestions: string[];
  /** Common buyer FAQ suggestions */
  buyerFaqSuggestions: string[];
  /** Language code (EN for now) */
  language: string;
};

export type DescriptionSection = {
  id: string;
  label: string;
  prompt: string;
  required: boolean;
};

/**
 * Home & Furniture template pack.
 */
const HOME_PACK: TemplatePack = {
  packId: 'home',
  displayName: 'Home & Furniture',
  categories: ['furniture', 'home_decor', 'storage', 'lighting', 'kitchen', 'garden'],
  titlePatterns: [
    '{brand} {model} {color} {material} - {condition}',
    '{color} {material} {keyFeature} - {brand}',
    'Vintage {brand} {keyFeature} - {condition}',
  ],
  descriptionSections: [
    { id: 'condition', label: 'Condition', prompt: 'Describe the condition (new, like new, used, vintage)', required: true },
    { id: 'dimensions', label: 'Dimensions', prompt: 'Include height, width, depth in cm', required: true },
    { id: 'material', label: 'Material', prompt: 'What is it made of?', required: false },
    { id: 'defects', label: 'Defects', prompt: 'Note any scratches, stains, or damage', required: true },
    { id: 'assembly', label: 'Assembly', prompt: 'Is assembly required? Include instructions if available', required: false },
    { id: 'pickup', label: 'Pickup/Shipping', prompt: 'Available for pickup or shipping?', required: true },
  ],
  missingInfoQuestions: [
    'What are the exact dimensions (H x W x D)?',
    'Are there any scratches, stains, or defects?',
    'What material is it made of?',
    'Is assembly required?',
    'Is it available for pickup or shipping only?',
  ],
  buyerFaqSuggestions: [
    'Can you deliver or is pickup only?',
    'Is the price negotiable?',
    'How old is this item?',
    'Why are you selling it?',
    'Do you have the original packaging/instructions?',
  ],
  language: 'EN',
};

/**
 * Electronics template pack.
 */
const ELECTRONICS_PACK: TemplatePack = {
  packId: 'electronics',
  displayName: 'Electronics',
  categories: ['electronics', 'home_electronics', 'computers', 'phones', 'audio', 'gaming', 'cameras'],
  titlePatterns: [
    '{brand} {model} - {condition} - {keyFeature}',
    '{brand} {model} {color} - Works Perfectly',
    '{brand} {keyFeature} - Tested & Working',
  ],
  descriptionSections: [
    { id: 'condition', label: 'Condition', prompt: 'Describe cosmetic and functional condition', required: true },
    { id: 'functionality', label: 'Functionality', prompt: 'Does it power on and work correctly?', required: true },
    { id: 'specs', label: 'Specifications', prompt: 'List key specs (storage, RAM, screen size, etc.)', required: false },
    { id: 'accessories', label: 'Included Accessories', prompt: 'What comes with it? (cables, charger, remote, etc.)', required: true },
    { id: 'defects', label: 'Defects', prompt: 'Note any cosmetic damage or functional issues', required: true },
    { id: 'warranty', label: 'Warranty', prompt: 'Is there remaining warranty?', required: false },
  ],
  missingInfoQuestions: [
    'Does it power on and work correctly?',
    'What is the model number?',
    'What accessories are included?',
    'Are there any cosmetic defects?',
    'Is there any remaining warranty?',
  ],
  buyerFaqSuggestions: [
    'Can I test it before buying?',
    'How long have you used it?',
    'Why are you selling?',
    'Does it come with original packaging?',
    'Is the battery still good?',
  ],
  language: 'EN',
};

/**
 * Fashion & Clothing template pack.
 */
const FASHION_PACK: TemplatePack = {
  packId: 'fashion',
  displayName: 'Fashion & Clothing',
  categories: ['clothing', 'fashion', 'shoes', 'accessories', 'bags', 'jewelry', 'watches'],
  titlePatterns: [
    '{brand} {keyFeature} - Size {size} - {color}',
    '{brand} {model} {color} - {condition}',
    'Designer {brand} {keyFeature} - Authentic',
  ],
  descriptionSections: [
    { id: 'size', label: 'Size & Fit', prompt: 'Include size, fit (slim, regular, loose), and measurements', required: true },
    { id: 'condition', label: 'Condition', prompt: 'New with tags, like new, gently used, worn', required: true },
    { id: 'material', label: 'Material', prompt: 'Fabric composition from label', required: true },
    { id: 'color', label: 'Color', prompt: 'Accurate color description', required: true },
    { id: 'defects', label: 'Defects', prompt: 'Note any stains, tears, pilling, or wear', required: true },
    { id: 'care', label: 'Care Instructions', prompt: 'Washing/care instructions if relevant', required: false },
  ],
  missingInfoQuestions: [
    'What size is it and how does it fit?',
    'What is the fabric/material composition?',
    'Are there any stains, tears, or defects?',
    'Is the brand label visible in photos?',
    'Has it been worn or is it new with tags?',
  ],
  buyerFaqSuggestions: [
    'Can you share exact measurements?',
    'Does it run true to size?',
    'Is it machine washable?',
    'Is this authentic/genuine?',
    'Are there any flaws not shown in photos?',
  ],
  language: 'EN',
};

/**
 * Toys & Games template pack.
 */
const TOYS_PACK: TemplatePack = {
  packId: 'toys',
  displayName: 'Toys & Games',
  categories: ['toys', 'games', 'puzzles', 'collectibles', 'hobbies'],
  titlePatterns: [
    '{brand} {model} - {condition} - Complete',
    'Vintage {brand} {keyFeature} - Rare',
    '{brand} {keyFeature} - Age {size}+',
  ],
  descriptionSections: [
    { id: 'condition', label: 'Condition', prompt: 'New in box, opened, played with, vintage', required: true },
    { id: 'completeness', label: 'Completeness', prompt: 'All pieces included? Missing parts?', required: true },
    { id: 'age', label: 'Age Range', prompt: 'Recommended age range', required: false },
    { id: 'functionality', label: 'Functionality', prompt: 'Does it work? Batteries needed?', required: true },
    { id: 'packaging', label: 'Packaging', prompt: 'Original box/packaging included?', required: false },
  ],
  missingInfoQuestions: [
    'Are all pieces/parts included?',
    'Does it require batteries?',
    'Is the original packaging included?',
    'What age range is it suitable for?',
    'Are there any broken or missing parts?',
  ],
  buyerFaqSuggestions: [
    'Is this from a smoke-free home?',
    'Is it a genuine/authentic product?',
    'Have all pieces been counted?',
    'Is the box in good condition?',
    'Was it played with or display only?',
  ],
  language: 'EN',
};

/**
 * Books & Media template pack.
 */
const BOOKS_PACK: TemplatePack = {
  packId: 'books',
  displayName: 'Books & Media',
  categories: ['books', 'media', 'vinyl', 'cds', 'dvds', 'magazines'],
  titlePatterns: [
    '{keyFeature} by {brand} - {condition}',
    '{brand} {model} - First Edition',
    '{keyFeature} - {condition} - {brand}',
  ],
  descriptionSections: [
    { id: 'condition', label: 'Condition', prompt: 'Like new, good, acceptable, worn', required: true },
    { id: 'edition', label: 'Edition/Format', prompt: 'First edition, hardcover, paperback, etc.', required: true },
    { id: 'defects', label: 'Defects', prompt: 'Highlighting, writing, torn pages, water damage', required: true },
    { id: 'contents', label: 'Contents', prompt: 'Includes dust jacket, inserts, etc.', required: false },
  ],
  missingInfoQuestions: [
    'Is it hardcover or paperback?',
    'Are there any highlights or writing inside?',
    'Is the dust jacket included?',
    'Is this a first edition?',
    'Any water damage or musty smell?',
  ],
  buyerFaqSuggestions: [
    'Is there any writing or highlighting?',
    'Does it have a musty smell?',
    'Is the spine cracked?',
    'Is the dust jacket in good condition?',
    'Is this a library copy?',
  ],
  language: 'EN',
};

/**
 * Sports & Outdoors template pack.
 */
const SPORTS_PACK: TemplatePack = {
  packId: 'sports',
  displayName: 'Sports & Outdoors',
  categories: ['sports', 'outdoors', 'fitness', 'cycling', 'camping', 'golf'],
  titlePatterns: [
    '{brand} {model} - {size} - {condition}',
    '{brand} {keyFeature} - Like New',
    'Professional {brand} {model} - {color}',
  ],
  descriptionSections: [
    { id: 'condition', label: 'Condition', prompt: 'New, lightly used, well used', required: true },
    { id: 'size', label: 'Size/Specifications', prompt: 'Size, weight, dimensions as relevant', required: true },
    { id: 'functionality', label: 'Functionality', prompt: 'Does everything work correctly?', required: true },
    { id: 'defects', label: 'Wear & Defects', prompt: 'Note any wear, scratches, or damage', required: true },
    { id: 'accessories', label: 'Included Items', prompt: 'What accessories come with it?', required: false },
  ],
  missingInfoQuestions: [
    'What size is it?',
    'How much has it been used?',
    'Are there any scratches or damage?',
    'What accessories are included?',
    'Is this suitable for beginners?',
  ],
  buyerFaqSuggestions: [
    'How many times has this been used?',
    'Is it suitable for a beginner?',
    'Does it come with a carrying case?',
    'Are there any performance issues?',
    'Would you recommend this brand?',
  ],
  language: 'EN',
};

/**
 * General/Fallback template pack.
 */
const GENERAL_PACK: TemplatePack = {
  packId: 'general',
  displayName: 'General',
  categories: ['other', 'general', 'unknown'],
  titlePatterns: [
    '{brand} {model} - {condition}',
    '{keyFeature} - {color} - {condition}',
    '{brand} {keyFeature} - Good Condition',
  ],
  descriptionSections: [
    { id: 'condition', label: 'Condition', prompt: 'Describe the overall condition', required: true },
    { id: 'description', label: 'Description', prompt: 'What is this item and its key features?', required: true },
    { id: 'defects', label: 'Defects', prompt: 'Note any damage or wear', required: true },
    { id: 'dimensions', label: 'Size/Dimensions', prompt: 'Include relevant measurements', required: false },
    { id: 'included', label: 'What\'s Included', prompt: 'List everything that comes with it', required: false },
  ],
  missingInfoQuestions: [
    'What is the brand and model?',
    'What condition is it in?',
    'Are there any defects or damage?',
    'What are the dimensions?',
    'What is included with this item?',
  ],
  buyerFaqSuggestions: [
    'Why are you selling this?',
    'Is the price negotiable?',
    'Can I see more photos?',
    'Is pickup or shipping available?',
    'How old is this item?',
  ],
  language: 'EN',
};

/**
 * All available template packs.
 */
export const TEMPLATE_PACKS: TemplatePack[] = [
  HOME_PACK,
  ELECTRONICS_PACK,
  FASHION_PACK,
  TOYS_PACK,
  BOOKS_PACK,
  SPORTS_PACK,
  GENERAL_PACK,
];

/**
 * Map of pack ID to pack.
 */
const PACK_BY_ID = new Map<string, TemplatePack>(
  TEMPLATE_PACKS.map((pack) => [pack.packId, pack])
);

/**
 * Map of category to pack (for quick lookup).
 */
const CATEGORY_TO_PACK = new Map<string, TemplatePack>();
for (const pack of TEMPLATE_PACKS) {
  for (const category of pack.categories) {
    CATEGORY_TO_PACK.set(category.toLowerCase(), pack);
  }
}

/**
 * Get template pack by ID.
 */
export function getTemplatePackById(packId: string): TemplatePack | null {
  return PACK_BY_ID.get(packId) ?? null;
}

/**
 * Select the best template pack for a category.
 * Falls back to 'general' if no match found.
 */
export function selectTemplatePack(category: string | null | undefined): TemplatePack {
  if (!category) {
    return GENERAL_PACK;
  }

  const normalized = category.toLowerCase().trim();

  // Direct match
  const directMatch = CATEGORY_TO_PACK.get(normalized);
  if (directMatch) {
    return directMatch;
  }

  // Partial match (category contains or is contained by a known category)
  for (const [cat, pack] of CATEGORY_TO_PACK.entries()) {
    if (normalized.includes(cat) || cat.includes(normalized)) {
      return pack;
    }
  }

  // Keyword matching
  if (normalized.includes('furniture') || normalized.includes('home') || normalized.includes('decor') || normalized.includes('kitchen')) {
    return HOME_PACK;
  }
  if (normalized.includes('electronic') || normalized.includes('phone') || normalized.includes('computer') || normalized.includes('audio') || normalized.includes('camera')) {
    return ELECTRONICS_PACK;
  }
  if (normalized.includes('cloth') || normalized.includes('fashion') || normalized.includes('shoe') || normalized.includes('bag') || normalized.includes('watch')) {
    return FASHION_PACK;
  }
  if (normalized.includes('toy') || normalized.includes('game') || normalized.includes('puzzle')) {
    return TOYS_PACK;
  }
  if (normalized.includes('book') || normalized.includes('media') || normalized.includes('vinyl') || normalized.includes('dvd')) {
    return BOOKS_PACK;
  }
  if (normalized.includes('sport') || normalized.includes('outdoor') || normalized.includes('fitness') || normalized.includes('bike') || normalized.includes('cycling')) {
    return SPORTS_PACK;
  }

  return GENERAL_PACK;
}

/**
 * Generate a title suggestion from a template pack.
 */
export function generateTitleSuggestion(
  pack: TemplatePack,
  placeholders: {
    brand?: string;
    model?: string;
    color?: string;
    condition?: string;
    size?: string;
    material?: string;
    keyFeature?: string;
  }
): string {
  // Find the best matching pattern (one with most filled placeholders)
  let bestPattern = pack.titlePatterns[0];
  let bestScore = 0;

  for (const pattern of pack.titlePatterns) {
    let score = 0;
    if (pattern.includes('{brand}') && placeholders.brand) score++;
    if (pattern.includes('{model}') && placeholders.model) score++;
    if (pattern.includes('{color}') && placeholders.color) score++;
    if (pattern.includes('{condition}') && placeholders.condition) score++;
    if (pattern.includes('{size}') && placeholders.size) score++;
    if (pattern.includes('{material}') && placeholders.material) score++;
    if (pattern.includes('{keyFeature}') && placeholders.keyFeature) score++;

    if (score > bestScore) {
      bestScore = score;
      bestPattern = pattern;
    }
  }

  // Fill placeholders
  let title = bestPattern;
  title = title.replace('{brand}', placeholders.brand ?? '');
  title = title.replace('{model}', placeholders.model ?? '');
  title = title.replace('{color}', placeholders.color ?? '');
  title = title.replace('{condition}', placeholders.condition ?? 'Good Condition');
  title = title.replace('{size}', placeholders.size ?? '');
  title = title.replace('{material}', placeholders.material ?? '');
  title = title.replace('{keyFeature}', placeholders.keyFeature ?? '');

  // Clean up double spaces and trim
  title = title.replace(/\s+/g, ' ').replace(/\s*-\s*-\s*/g, ' - ').trim();
  title = title.replace(/^\s*-\s*/, '').replace(/\s*-\s*$/, '');

  return title;
}

/**
 * Get relevant missing info questions for an item.
 * Filters out questions that already have answers based on provided attributes.
 */
export function getMissingInfoQuestions(
  pack: TemplatePack,
  existingAttributes: Record<string, string | undefined>,
  maxQuestions: number = 3
): string[] {
  const questions: string[] = [];

  for (const question of pack.missingInfoQuestions) {
    // Skip if we likely already have this info
    const lowerQ = question.toLowerCase();

    // Check if the question relates to info we already have
    if (lowerQ.includes('dimension') && existingAttributes.dimensions) continue;
    if (lowerQ.includes('size') && (existingAttributes.size || existingAttributes.dimensions)) continue;
    if (lowerQ.includes('brand') && existingAttributes.brand) continue;
    if (lowerQ.includes('model') && existingAttributes.model) continue;
    if (lowerQ.includes('color') && existingAttributes.color) continue;
    if (lowerQ.includes('material') && existingAttributes.material) continue;
    if (lowerQ.includes('condition') && existingAttributes.condition) continue;

    questions.push(question);

    if (questions.length >= maxQuestions) break;
  }

  // If we have fewer than max, add more from the list
  if (questions.length < maxQuestions) {
    for (const question of pack.missingInfoQuestions) {
      if (!questions.includes(question)) {
        questions.push(question);
        if (questions.length >= maxQuestions) break;
      }
    }
  }

  return questions;
}

/**
 * Get buyer FAQ suggestions for a category.
 */
export function getBuyerFaqSuggestions(
  pack: TemplatePack,
  maxSuggestions: number = 3
): string[] {
  return pack.buyerFaqSuggestions.slice(0, maxSuggestions);
}
