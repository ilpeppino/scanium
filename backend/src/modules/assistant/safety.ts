import { AssistantAction, AssistantResponse } from './types.js';

const DISALLOWED_PATTERNS = [
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

const SAFE_ACTIONS = new Set([
  'APPLY_DRAFT_UPDATE',
  'COPY_TEXT',
  'OPEN_POSTING_ASSIST',
  'OPEN_SHARE',
  'OPEN_URL',
]);

const MAX_PAYLOAD_VALUE_LENGTH = 1000;

export function shouldRefuse(message: string): boolean {
  return DISALLOWED_PATTERNS.some((pattern) => pattern.test(message));
}

export function refusalResponse(): AssistantResponse {
  return {
    content:
      'I can’t help with scraping, automation, or anything that requires passwords. I can still help with listing improvements, safe pricing guidance, and what details to verify.',
    actions: [],
  };
}

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

export function sanitizeActions(actions: AssistantAction[] = []): AssistantAction[] {
  return actions
    .filter((action) => SAFE_ACTIONS.has(action.type))
    .map((action) => {
      const payload = sanitizePayload(action.payload);
      if (action.type === 'OPEN_URL') {
        const url = payload?.url;
        if (!isSafeUrl(url)) {
          return null;
        }
      }
      return { type: action.type, payload };
    })
    .filter((action): action is AssistantAction => Boolean(action));
}
