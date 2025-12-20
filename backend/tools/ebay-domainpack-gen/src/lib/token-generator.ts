/**
 * Token Generator for Domain Pack Categories
 * Generates searchable keyword tokens from eBay category names
 */

// Generic words that should not be used as tokens (too broad)
const TOKEN_DENYLIST = new Set([
  'other',
  'misc',
  'miscellaneous',
  'parts',
  'accessories',
  'home',
  'general',
  'unknown',
  'more',
  'various',
  'all',
  'everything',
  'stuff',
  'items',
  'things',
  'products',
  'goods',
  'and',
  'or',
  'the',
  'a',
  'an',
  'for',
  'with',
  'without',
  'in',
  'on',
  'at',
]);

export class TokenGenerator {
  private maxTokensPerCategory: number;
  private minTokenLength: number;

  constructor(options?: { maxTokensPerCategory?: number; minTokenLength?: number }) {
    this.maxTokensPerCategory = options?.maxTokensPerCategory || 15;
    this.minTokenLength = options?.minTokenLength || 2;
  }

  /**
   * Generate tokens from category name and path
   * @param categoryName The leaf category name
   * @param categoryPath Full path from root to leaf
   * @returns Array of normalized, deduplicated tokens
   */
  generateTokens(categoryName: string, categoryPath: string[]): string[] {
    const tokens = new Set<string>();

    // Add tokens from category name
    this.extractTokensFromText(categoryName, tokens);

    // Add tokens from parent categories (with lower priority)
    // Skip the first (root) and last (same as categoryName) elements
    const parentCategories = categoryPath.slice(1, -1);
    for (const parent of parentCategories) {
      this.extractTokensFromText(parent, tokens);
    }

    // Convert to array, sort for determinism, and limit count
    const tokenArray = Array.from(tokens).sort();
    return tokenArray.slice(0, this.maxTokensPerCategory);
  }

  /**
   * Extract and normalize tokens from a text string
   */
  private extractTokensFromText(text: string, tokens: Set<string>): void {
    // Normalize the text
    let normalized = text.toLowerCase().trim();

    // Replace & with "and"
    normalized = normalized.replace(/&/g, 'and');

    // Replace common separators with spaces
    normalized = normalized.replace(/[/\-_,;:|]/g, ' ');

    // Remove other punctuation
    normalized = normalized.replace(/[^\w\s]/g, '');

    // Split into words
    const words = normalized.split(/\s+/).filter((w) => w.length >= this.minTokenLength);

    // Add individual words (if not in denylist)
    for (const word of words) {
      if (!TOKEN_DENYLIST.has(word)) {
        tokens.add(word);
      }
    }

    // Add bigrams (2-word phrases) for better specificity
    for (let i = 0; i < words.length - 1; i++) {
      const bigram = `${words[i]} ${words[i + 1]}`;
      // Only add bigrams where both words are meaningful
      if (!TOKEN_DENYLIST.has(words[i]) && !TOKEN_DENYLIST.has(words[i + 1])) {
        tokens.add(bigram);
      }
    }

    // Add trigrams for very specific categories
    if (words.length >= 3) {
      for (let i = 0; i < words.length - 2; i++) {
        const trigram = `${words[i]} ${words[i + 1]} ${words[i + 2]}`;
        if (
          !TOKEN_DENYLIST.has(words[i]) &&
          !TOKEN_DENYLIST.has(words[i + 1]) &&
          !TOKEN_DENYLIST.has(words[i + 2])
        ) {
          tokens.add(trigram);
        }
      }
    }
  }

  /**
   * Get the current denylist (for testing/debugging)
   */
  static getDenylist(): string[] {
    return Array.from(TOKEN_DENYLIST).sort();
  }
}
