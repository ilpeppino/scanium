/**
 * Lightweight language consistency check for assistant requests.
 *
 * Uses stopword-based detection to identify language mismatches
 * between requested language and actual content language.
 */

/**
 * Language-specific stopwords for detection.
 * These are common words that strongly indicate a specific language.
 */
const STOPWORDS: Record<string, Set<string>> = {
  EN: new Set([
    'the',
    'is',
    'are',
    'was',
    'were',
    'be',
    'been',
    'being',
    'have',
    'has',
    'had',
    'do',
    'does',
    'did',
    'will',
    'would',
    'could',
    'should',
    'may',
    'might',
    'must',
    'and',
    'but',
    'or',
    'not',
    'for',
    'with',
    'about',
    'this',
    'that',
    'these',
    'those',
    'from',
    'your',
    'their',
    'which',
    'what',
    'when',
    'where',
    'who',
    'how',
    'very',
    'just',
    'only',
    'also',
    'than',
    'then',
    'because',
  ]),
  IT: new Set([
    'il',
    'la',
    'le',
    'lo',
    'gli',
    'un',
    'una',
    'uno',
    'e',
    'è',
    'che',
    'di',
    'del',
    'della',
    'dei',
    'delle',
    'a',
    'al',
    'alla',
    'ai',
    'alle',
    'da',
    'dal',
    'dalla',
    'in',
    'nel',
    'nella',
    'su',
    'con',
    'per',
    'come',
    'sono',
    'siamo',
    'sei',
    'hanno',
    'ha',
    'ho',
    'questo',
    'questa',
    'questi',
    'queste',
    'quello',
    'quella',
    'non',
    'più',
    'ma',
    'anche',
    'molto',
    'tutto',
    'tutti',
  ]),
  NL: new Set([
    'de',
    'het',
    'een',
    'en',
    'van',
    'in',
    'is',
    'op',
    'te',
    'dat',
    'die',
    'voor',
    'met',
    'zijn',
    'was',
    'aan',
    'bij',
    'om',
    'uit',
    'naar',
    'ook',
    'niet',
    'maar',
    'dan',
    'nog',
    'wel',
    'kan',
    'heeft',
    'hebben',
    'dit',
    'deze',
    'wat',
    'als',
    'worden',
    'over',
    'veel',
    'zou',
    'geen',
    'meer',
    'tot',
    'hun',
    'moet',
  ]),
  DE: new Set([
    'der',
    'die',
    'das',
    'den',
    'dem',
    'ein',
    'eine',
    'einer',
    'und',
    'ist',
    'sind',
    'war',
    'von',
    'zu',
    'mit',
    'auf',
    'für',
    'an',
    'im',
    'bei',
    'nach',
    'aus',
    'über',
    'als',
    'auch',
    'es',
    'sich',
    'so',
    'wie',
    'wenn',
    'noch',
    'dass',
    'nicht',
    'kann',
    'haben',
    'hat',
    'werden',
    'wird',
    'nur',
    'oder',
    'aber',
    'sehr',
    'schon',
  ]),
  FR: new Set([
    'le',
    'la',
    'les',
    'un',
    'une',
    'des',
    'de',
    'du',
    'et',
    'est',
    'sont',
    'être',
    'avoir',
    'a',
    'en',
    'à',
    'pour',
    'dans',
    'sur',
    'avec',
    'ce',
    'cette',
    'ces',
    'qui',
    'que',
    'quoi',
    'ne',
    'pas',
    'plus',
    'mais',
    'ou',
    'où',
    'si',
    'comme',
    'tout',
    'tous',
    'toute',
    'nous',
    'vous',
    'ils',
    'elles',
    'leur',
    'très',
    'aussi',
  ]),
  ES: new Set([
    'el',
    'la',
    'los',
    'las',
    'un',
    'una',
    'unos',
    'unas',
    'de',
    'del',
    'y',
    'e',
    'en',
    'es',
    'son',
    'está',
    'están',
    'ser',
    'con',
    'para',
    'por',
    'a',
    'al',
    'que',
    'qué',
    'como',
    'no',
    'más',
    'pero',
    'muy',
    'también',
    'este',
    'esta',
    'estos',
    'estas',
    'ese',
    'esa',
    'su',
    'sus',
    'lo',
    'se',
    'le',
    'les',
    'si',
    'todo',
    'todos',
  ]),
  PT_BR: new Set([
    'o',
    'a',
    'os',
    'as',
    'um',
    'uma',
    'uns',
    'umas',
    'de',
    'do',
    'da',
    'dos',
    'das',
    'e',
    'é',
    'em',
    'no',
    'na',
    'nos',
    'nas',
    'para',
    'por',
    'com',
    'que',
    'não',
    'mais',
    'mas',
    'como',
    'se',
    'seu',
    'sua',
    'seus',
    'suas',
    'este',
    'esta',
    'estes',
    'estas',
    'esse',
    'essa',
    'muito',
    'também',
    'já',
    'ou',
    'quando',
    'onde',
    'foi',
    'são',
    'tem',
  ]),
};

/**
 * Result of language detection.
 */
export interface LanguageDetectionResult {
  /** Detected language code (best guess) */
  detectedLanguage: string;
  /** Confidence score (0-1) */
  confidence: number;
  /** Scores per language */
  scores: Record<string, number>;
  /** Whether detection matches expected language */
  matchesExpected: boolean;
}

/**
 * Result of language consistency check.
 */
export interface LanguageConsistencyResult {
  /** Whether the content is consistent with expected language */
  isConsistent: boolean;
  /** Expected language tag */
  expectedLanguage: string;
  /** Detected language (best guess) */
  detectedLanguage: string;
  /** Confidence of detection (0-1) */
  confidence: number;
  /** Number of text segments checked */
  segmentsChecked: number;
  /** Number of segments that matched expected language */
  segmentsMatched: number;
  /** Warning message if mismatch detected */
  warningMessage?: string;
}

/**
 * Tokenize text into lowercase words.
 */
function tokenize(text: string): string[] {
  return text
    .toLowerCase()
    .replace(/[^\p{L}\s]/gu, ' ')
    .split(/\s+/)
    .filter((word) => word.length >= 2);
}

/**
 * Detect the language of a text using stopword matching.
 */
export function detectLanguage(text: string, expectedLanguage?: string): LanguageDetectionResult {
  const words = tokenize(text);
  const scores: Record<string, number> = {};

  // Count stopword matches for each language
  for (const [lang, stopwords] of Object.entries(STOPWORDS)) {
    let matches = 0;
    for (const word of words) {
      if (stopwords.has(word)) {
        matches++;
      }
    }
    scores[lang] = matches;
  }

  // Find best match
  let bestLang = 'EN';
  let bestScore = 0;
  for (const [lang, score] of Object.entries(scores)) {
    if (score > bestScore) {
      bestScore = score;
      bestLang = lang;
    }
  }

  // Calculate confidence (ratio of best score to word count)
  const totalWords = words.length;
  const confidence = totalWords > 0 ? Math.min(1, bestScore / Math.max(5, totalWords * 0.2)) : 0;

  const expected = expectedLanguage?.toUpperCase().replace('-', '_') ?? 'EN';

  return {
    detectedLanguage: bestLang,
    confidence,
    scores,
    matchesExpected: bestLang === expected || (expected === 'PT_BR' && bestLang === 'PT_BR'),
  };
}

/**
 * Check language consistency of attribute values and prompt text.
 *
 * @param texts Array of text segments to check
 * @param expectedLanguage Expected language tag
 * @param correlationId Optional correlation ID for logging
 * @returns Consistency check result
 */
export function checkLanguageConsistency(
  texts: string[],
  expectedLanguage: string,
  correlationId?: string
): LanguageConsistencyResult {
  const expected = expectedLanguage.toUpperCase().replace('-', '_');

  // Filter out empty or very short texts
  const validTexts = texts.filter((t) => t && t.trim().length >= 10);

  if (validTexts.length === 0) {
    return {
      isConsistent: true,
      expectedLanguage: expected,
      detectedLanguage: expected,
      confidence: 1,
      segmentsChecked: 0,
      segmentsMatched: 0,
    };
  }

  let matchedSegments = 0;
  const langScores: Record<string, number> = {};

  for (const text of validTexts) {
    const result = detectLanguage(text, expected);

    // Accumulate scores
    for (const [lang, score] of Object.entries(result.scores)) {
      langScores[lang] = (langScores[lang] ?? 0) + score;
    }

    if (result.matchesExpected) {
      matchedSegments++;
    }
  }

  // Find overall best language
  let bestLang = expected;
  let bestScore = 0;
  for (const [lang, score] of Object.entries(langScores)) {
    if (score > bestScore) {
      bestScore = score;
      bestLang = lang;
    }
  }

  const isConsistent = bestLang === expected || matchedSegments >= validTexts.length * 0.5;
  const confidence = validTexts.length > 0 ? matchedSegments / validTexts.length : 1;

  const result: LanguageConsistencyResult = {
    isConsistent,
    expectedLanguage: expected,
    detectedLanguage: bestLang,
    confidence,
    segmentsChecked: validTexts.length,
    segmentsMatched: matchedSegments,
  };

  // Log warning in DEV mode if mismatch detected
  if (!isConsistent) {
    result.warningMessage = `Language mismatch: expected ${expected}, detected ${bestLang} (confidence: ${(confidence * 100).toFixed(0)}%)`;

    if (process.env.NODE_ENV === 'development') {
      console.warn(
        '[LANG_MISMATCH]',
        JSON.stringify({
          correlationId,
          expectedLanguage: expected,
          detectedLanguage: bestLang,
          confidence,
          segmentsChecked: validTexts.length,
          segmentsMatched: matchedSegments,
          message: 'Language consistency check failed - potential mismatch detected',
        })
      );
    }
  }

  return result;
}

/**
 * Check if response text matches expected language.
 * Used to verify LLM output is in correct language.
 */
export function checkResponseLanguage(
  responseText: string,
  expectedLanguage: string,
  correlationId?: string
): LanguageDetectionResult {
  const result = detectLanguage(responseText, expectedLanguage);

  if (!result.matchesExpected && result.confidence >= 0.3) {
    if (process.env.NODE_ENV === 'development') {
      console.warn(
        '[RESPONSE_LANG_MISMATCH]',
        JSON.stringify({
          correlationId,
          expectedLanguage,
          detectedLanguage: result.detectedLanguage,
          confidence: result.confidence,
          message: 'Response language mismatch detected',
        })
      );
    }
  }

  return result;
}

/**
 * Language consistency metrics for monitoring.
 */
export interface LanguageMetrics {
  /** Total number of requests checked */
  requestsChecked: number;
  /** Number of requests with attribute language mismatch */
  attributeMismatches: number;
  /** Number of requests with prompt language mismatch */
  promptMismatches: number;
  /** Number of responses with language mismatch */
  responseMismatches: number;
}

// In-memory metrics counter (reset on server restart)
let metrics: LanguageMetrics = {
  requestsChecked: 0,
  attributeMismatches: 0,
  promptMismatches: 0,
  responseMismatches: 0,
};

/**
 * Record a language consistency check.
 */
export function recordLanguageCheck(
  type: 'attribute' | 'prompt' | 'response',
  isConsistent: boolean
): void {
  metrics.requestsChecked++;
  if (!isConsistent) {
    switch (type) {
      case 'attribute':
        metrics.attributeMismatches++;
        break;
      case 'prompt':
        metrics.promptMismatches++;
        break;
      case 'response':
        metrics.responseMismatches++;
        break;
    }
  }
}

/**
 * Get current language metrics.
 */
export function getLanguageMetrics(): LanguageMetrics {
  return { ...metrics };
}

/**
 * Reset language metrics (for testing).
 */
export function resetLanguageMetrics(): void {
  metrics = {
    requestsChecked: 0,
    attributeMismatches: 0,
    promptMismatches: 0,
    responseMismatches: 0,
  };
}
