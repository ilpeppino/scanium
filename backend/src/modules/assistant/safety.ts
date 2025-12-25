import { AssistantAction, AssistantResponse, AssistantChatRequest } from './types.js';

/**
 * Security reason codes - stable enum for client handling
 */
export type SecurityReasonCode =
  | 'POLICY_VIOLATION'
  | 'INJECTION_ATTEMPT'
  | 'DATA_EXFIL_ATTEMPT'
  | 'VALIDATION_ERROR'
  | 'RATE_LIMITED'
  | 'QUOTA_EXCEEDED'
  | 'PROVIDER_UNAVAILABLE'
  | 'PROVIDER_NOT_CONFIGURED';

export type SecurityCheckResult = {
  allowed: boolean;
  reasonCode?: SecurityReasonCode;
};

// ============================================================================
// PROMPT INJECTION DETECTION
// ============================================================================

/**
 * Patterns for detecting common prompt injection attempts.
 * Organized by category for easier maintenance and logging.
 */
const INJECTION_PATTERNS: { category: string; patterns: RegExp[] }[] = [
  {
    category: 'system_prompt_extraction',
    patterns: [
      /(?:what|show|reveal|tell|print|display|output|repeat|echo).*(?:system|initial|original|hidden|secret).*(?:prompt|instruction|message|rules?)/i,
      /(?:your|the)\s+system\s+prompt/i,
      /(?:ignore|disregard|forget|skip|bypass|override).*(?:previous|above|prior|earlier).*(?:instructions?|prompts?|rules?|commands?)/i,
      /you\s*are\s*now\s*(?:in|entering)?\s*(?:debug|admin|developer|unrestricted|jailbreak)\s*mode/i,
      /(?:enter|enable|activate|switch\s*to)\s*(?:debug|admin|developer|unrestricted|jailbreak)\s*mode/i,
      /(?:pretend|act|behave)\s*(?:as\s*if\s*)?(?:you\s*(?:are|were)|to\s*be)\s*(?:a\s*different|another|unrestricted)/i,
      /what\s*(?:were|are)\s*you\s*(?:told|instructed|programmed)/i,
      /(?:reveal|show|tell\s*me)\s*(?:your|the)\s*(?:role|purpose|objective|goal)/i,
    ],
  },
  {
    category: 'data_exfiltration',
    patterns: [
      /(?:show|list|get|fetch|retrieve|dump|export|display).*(?:all|other|every).*(?:users?|customers?|accounts?|data|records?|items?)/i,
      /(?:access|query|search).*(?:database|db|backend|server|api)/i,
      /(?:other|different|all)\s*users?.*(?:data|info|information|items?|listings?)/i,
      /(?:dump|export|backup|copy).*(?:database|db|everything|all\s*data)/i,
      /dump.*(?:entire|the).*database/i,
      /(?:internal|admin|backend|server).*(?:api|endpoint|route|function|data)/i,
    ],
  },
  {
    category: 'jailbreak_attempts',
    patterns: [
      /\bDAN\b/i, // "Do Anything Now" jailbreak
      /\bdeveloper\s*mode\b/i,
      /\bunrestricted\s*mode\b/i,
      /\bjailbreak(?:ed)?\b/i,
      /hypothetically\s*(?:speaking)?.*(?:if\s*you\s*could|pretend|imagine)/i,
      /in\s*(?:a\s*)?fiction(?:al)?.*(?:you\s*can|pretend|imagine)/i,
      /(?:roleplay|role-play)\s*(?:as|that)\s*(?:you\s*(?:are|have)|a)/i,
      /(?:bypass|circumvent|avoid|ignore).*(?:safety|security|restriction|limitation|filter|rule)/i,
    ],
  },
  {
    category: 'credential_access',
    patterns: [
      /\b(?:password|passwd|pwd|credential|secret|api[\s_-]?key|token|oauth|auth[\s_-]?token)\b/i,
      /\b(?:login|sign[\s_-]?in|authenticate)\s*(?:as|to|with|for)\b/i,
    ],
  },
  {
    category: 'automation_abuse',
    patterns: [
      /\b(?:scrape|scraping|crawl|crawling|spider)\b/i,
      /\b(?:headless|puppeteer|selenium|playwright)\s*(?:browser)?\b/i,
      /\b(?:browser\s*)?automation\b/i,
      /automate.*(?:login|posting|form)/i,
      /\b(?:autofill|auto[\s_-]?fill|auto[\s_-]?complete)\b/i,
      /\baccessibility[\s_-]?(?:api|service|hack)\b/i,
    ],
  },
  {
    category: 'encoded_payloads',
    patterns: [
      // Base64-like patterns longer than reasonable
      /[A-Za-z0-9+/]{50,}={0,2}/,
      // URL-encoded suspicious patterns
      /%(?:00|20|22|27|3C|3E|5C){3,}/i,
    ],
  },
];

/**
 * Disallowed patterns from original implementation - kept for backwards compatibility
 */
const LEGACY_DISALLOWED_PATTERNS = [
  /scrape/i,
  /scraping/i,
  /crawl/i,
  /headless/i,
  /browser automation/i,
  /autofill/i,
  /accessibility/i,
  /password/i,
  /login/i,
  /credential/i,
];

/**
 * Check if a message contains prompt injection patterns.
 * Returns the category of the matched pattern for logging (but not exposed to client).
 */
export function detectInjection(message: string): { detected: boolean; category?: string } {
  const normalizedMessage = normalizeForSecurity(message);

  for (const group of INJECTION_PATTERNS) {
    for (const pattern of group.patterns) {
      if (pattern.test(normalizedMessage)) {
        return { detected: true, category: group.category };
      }
    }
  }

  return { detected: false };
}

/**
 * Legacy function - checks if message should be refused.
 * Combines new injection detection with legacy patterns.
 */
export function shouldRefuse(message: string): boolean {
  // Check new injection patterns
  const injection = detectInjection(message);
  if (injection.detected) {
    return true;
  }

  // Check legacy patterns for backwards compatibility
  return LEGACY_DISALLOWED_PATTERNS.some((pattern) => pattern.test(message));
}

/**
 * Get the security reason code for a refused message.
 */
export function getRefusalReasonCode(message: string): SecurityReasonCode {
  const injection = detectInjection(message);
  if (injection.detected) {
    if (injection.category === 'data_exfiltration') {
      return 'DATA_EXFIL_ATTEMPT';
    }
    return 'INJECTION_ATTEMPT';
  }
  return 'POLICY_VIOLATION';
}

/**
 * Standard refusal response that doesn't reveal internal policy details.
 */
export function refusalResponse(): AssistantResponse {
  return {
    content:
      "I can help with listing improvements, pricing guidance, and item details. I can't assist with that particular request.",
    actions: [],
  };
}

// ============================================================================
// PII REDACTION
// ============================================================================

/**
 * Patterns for detecting PII that should be redacted before sending to LLM.
 */
/**
 * PII patterns ordered from most specific to least specific.
 * Order matters - more specific patterns (like IBAN, credit cards) should come
 * before general phone patterns to prevent partial matches.
 */
const PII_PATTERNS: { type: string; pattern: RegExp; replacement: string }[] = [
  // Most specific patterns first
  {
    type: 'email',
    pattern: /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/g,
    replacement: '[EMAIL]',
  },
  {
    type: 'iban',
    // IBAN: 2 letters + 2 digits + up to 30 alphanumeric chars
    pattern: /\b[A-Z]{2}[0-9]{2}[A-Z0-9]{10,30}\b/g,
    replacement: '[IBAN]',
  },
  {
    type: 'credit_card',
    // Major card patterns: Visa, MC, Amex, Discover
    pattern: /\b(?:4[0-9]{15}|4[0-9]{12}|5[1-5][0-9]{14}|3[47][0-9]{13}|6(?:011|5[0-9]{2})[0-9]{12})\b/g,
    replacement: '[CARD]',
  },
  {
    type: 'ssn',
    pattern: /\b[0-9]{3}[-\s]?[0-9]{2}[-\s]?[0-9]{4}\b/g,
    replacement: '[SSN]',
  },
  {
    type: 'ip_address',
    pattern: /\b(?:\d{1,3}\.){3}\d{1,3}\b/g,
    replacement: '[IP]',
  },
  {
    type: 'postal_code_nl',
    pattern: /\b[1-9][0-9]{3}\s?[A-Za-z]{2}\b/g,
    replacement: '[POSTAL]',
  },
  // Phone patterns - less specific, so check after more specific patterns
  {
    type: 'phone_nl',
    pattern: /(?:0|\+31)[-\s]?[0-9][-\s0-9]{7,13}/g,
    replacement: '[PHONE]',
  },
  {
    type: 'phone_us',
    pattern: /(?:\+1[-.\s]?)?\(?[0-9]{3}\)?[-.\s]?[0-9]{3}[-.\s]?[0-9]{4}/g,
    replacement: '[PHONE]',
  },
  {
    type: 'phone_international',
    // International format: optional + followed by 10-15 digits with optional separators
    pattern: /\+[0-9]{1,3}[-.\s]?[0-9][-.\s0-9]{8,14}/g,
    replacement: '[PHONE]',
  },
  {
    type: 'address_keywords',
    pattern: /\b(?:straat|weg|laan|plein|avenue|street|road|lane|drive)\s+\d+[a-z]?\b/gi,
    replacement: '[ADDRESS]',
  },
];

/**
 * Redact PII from text before sending to LLM.
 * Conservative approach: better to over-redact than under-redact.
 */
export function redactPii(text: string): { redacted: string; piiFound: boolean } {
  if (!text) {
    return { redacted: text, piiFound: false };
  }

  let result = text;
  let piiFound = false;

  for (const { pattern, replacement } of PII_PATTERNS) {
    if (pattern.test(result)) {
      piiFound = true;
      // Reset lastIndex for global patterns
      pattern.lastIndex = 0;
      result = result.replace(pattern, replacement);
    }
  }

  return { redacted: result, piiFound };
}

/**
 * Redact PII from all text fields in an assistant request.
 */
export function redactRequestPii(request: AssistantChatRequest): {
  request: AssistantChatRequest;
  piiRedacted: boolean;
} {
  let piiRedacted = false;

  // Redact message
  const messageResult = redactPii(request.message);
  const redactedMessage = messageResult.redacted;
  if (messageResult.piiFound) piiRedacted = true;

  // Redact item titles and descriptions
  const redactedItems = request.items.map((item) => {
    const titleResult = redactPii(item.title ?? '');
    const descResult = redactPii(item.description ?? '');
    if (titleResult.piiFound || descResult.piiFound) piiRedacted = true;

    return {
      ...item,
      title: titleResult.redacted || item.title,
      description: descResult.redacted || item.description,
    };
  });

  // Redact history messages
  const redactedHistory = request.history?.map((msg) => {
    const contentResult = redactPii(msg.content);
    if (contentResult.piiFound) piiRedacted = true;
    return {
      ...msg,
      content: contentResult.redacted,
    };
  });

  return {
    request: {
      ...request,
      message: redactedMessage,
      items: redactedItems,
      history: redactedHistory,
    },
    piiRedacted,
  };
}

// ============================================================================
// INPUT NORMALIZATION
// ============================================================================

/**
 * Normalize text for security checks:
 * - Unicode NFKC normalization
 * - Remove control characters (except newlines)
 * - Collapse multiple whitespace
 */
export function normalizeForSecurity(text: string): string {
  if (!text) return '';

  // Unicode NFKC normalization
  let normalized = text.normalize('NFKC');

  // Remove control characters except newlines and tabs
  normalized = normalized.replace(/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/g, '');

  // Collapse multiple whitespace into single space (preserve newlines)
  normalized = normalized.replace(/[^\S\n]+/g, ' ');

  return normalized.trim();
}

/**
 * Normalize and validate message text.
 */
export function normalizeMessage(text: string, maxLength: number): {
  normalized: string;
  valid: boolean;
  error?: string;
} {
  if (!text) {
    return { normalized: '', valid: false, error: 'Message is required' };
  }

  const normalized = normalizeForSecurity(text);

  if (normalized.length === 0) {
    return { normalized: '', valid: false, error: 'Message cannot be empty or whitespace-only' };
  }

  if (normalized.length > maxLength) {
    return { normalized: '', valid: false, error: `Message exceeds maximum length of ${maxLength} characters` };
  }

  return { normalized, valid: true };
}

// ============================================================================
// ACTION SANITIZATION
// ============================================================================

const SAFE_ACTIONS = new Set([
  'ADD_ATTRIBUTES',
  'APPLY_DRAFT_UPDATE',
  'COPY_TEXT',
  'OPEN_POSTING_ASSIST',
  'OPEN_SHARE',
  'OPEN_URL',
  'SUGGEST_NEXT_PHOTO',
]);

const MAX_PAYLOAD_VALUE_LENGTH = 1000;

function sanitizePayload(payload?: Record<string, string>): Record<string, string> | undefined {
  if (!payload) {
    return undefined;
  }
  const sanitized: Record<string, string> = {};
  for (const [key, value] of Object.entries(payload)) {
    if (typeof value !== 'string') continue;
    sanitized[key] = value.trim().slice(0, MAX_PAYLOAD_VALUE_LENGTH);
  }
  return sanitized;
}

function isSafeUrl(url?: string): boolean {
  if (!url) return false;
  try {
    const parsed = new URL(url);
    return parsed.protocol === 'http:' || parsed.protocol === 'https:';
  } catch (_error) {
    return false;
  }
}

/**
 * Escape HTML special characters to prevent XSS.
 */
function sanitizeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&***REMOVED***39;');
}

/**
 * Sanitize actions returned by the LLM.
 * Ensures only safe action types and validated payloads are returned to client.
 */
export function sanitizeActions(actions: AssistantAction[] = []): AssistantAction[] {
  const mapped: (AssistantAction | null)[] = actions
    .filter((action) => SAFE_ACTIONS.has(action.type))
    .map((action): AssistantAction | null => {
      const payload = sanitizePayload(action.payload);
      if (action.type === 'OPEN_URL') {
        const url = payload?.url;
        if (!isSafeUrl(url)) {
          return null;
        }
      }
      return {
        type: action.type,
        payload,
        label: action.label ? sanitizeHtml(action.label).slice(0, 100) : undefined,
        requiresConfirmation: action.requiresConfirmation,
      };
    });
  return mapped.filter((action): action is AssistantAction => action !== null);
}

// ============================================================================
// REQUEST VALIDATION
// ============================================================================

export type ValidationLimits = {
  maxInputChars: number;
  maxContextItems: number;
  maxAttributesPerItem: number;
};

/**
 * Validate and sanitize an entire assistant chat request.
 * Returns sanitized request or validation error.
 */
export function validateAndSanitizeRequest(
  request: AssistantChatRequest,
  limits: ValidationLimits
): { valid: true; request: AssistantChatRequest } | { valid: false; error: string } {
  // Validate message
  const messageResult = normalizeMessage(request.message, limits.maxInputChars);
  if (!messageResult.valid) {
    return { valid: false, error: messageResult.error! };
  }

  // Validate context items count
  if (request.items.length > limits.maxContextItems) {
    return {
      valid: false,
      error: `Too many context items: ${request.items.length} exceeds limit of ${limits.maxContextItems}`,
    };
  }

  // Validate and sanitize items
  const sanitizedItems = request.items.slice(0, limits.maxContextItems).map((item) => {
    // Limit attributes
    const limitedAttributes = (item.attributes || []).slice(0, limits.maxAttributesPerItem);

    return {
      ...item,
      // Truncate long strings
      itemId: (item.itemId || '').slice(0, 100),
      title: (item.title || '').slice(0, 200),
      description: (item.description || '').slice(0, 1000),
      category: (item.category || '').slice(0, 100),
      attributes: limitedAttributes.map((attr) => ({
        key: (attr.key || '').slice(0, 100),
        value: (attr.value || '').slice(0, 200),
        confidence: attr.confidence,
      })),
    };
  });

  // Validate history length (keep last 10 messages)
  const limitedHistory = (request.history || []).slice(-10);

  return {
    valid: true,
    request: {
      ...request,
      message: messageResult.normalized,
      items: sanitizedItems,
      history: limitedHistory,
    },
  };
}
