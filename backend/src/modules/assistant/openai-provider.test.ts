import { describe, it, expect, vi, beforeEach } from 'vitest';
import { OpenAIAssistantProvider, OpenAIProviderConfig } from './openai-provider.js';
import { AssistantChatRequestWithVision } from './types.js';
import { VisualFacts } from '../vision/types.js';

// Mock the OpenAI SDK
vi.mock('openai', () => {
  return {
    default: vi.fn().mockImplementation(() => ({
      chat: {
        completions: {
          create: vi.fn(),
        },
      },
    })),
  };
});

// Mock the prompts module
vi.mock('./prompts/listing-generation.js', () => ({
  buildListingSystemPrompt: vi.fn().mockReturnValue('System prompt'),
  buildListingUserPrompt: vi.fn().mockReturnValue('User prompt'),
  parseListingResponse: vi.fn().mockReturnValue({
    title: 'Test Title',
    description: 'Test Description',
    warnings: [],
    missingInfo: [],
  }),
}));

// Mock the attribute resolver
vi.mock('../vision/attribute-resolver.js', () => ({
  resolveAttributes: vi.fn().mockReturnValue({
    itemId: 'test-item',
    brand: {
      value: 'TestBrand',
      confidence: 'HIGH',
      evidenceRefs: [{ type: 'logo', value: 'TestBrand', score: 0.9 }],
    },
    color: {
      value: 'blue',
      confidence: 'HIGH',
      evidenceRefs: [{ type: 'color', value: 'blue', score: 0.8 }],
    },
  }),
}));

describe('OpenAIAssistantProvider', () => {
  const defaultConfig: OpenAIProviderConfig = {
    apiKey: 'test-api-key',
    model: 'gpt-4o-mini',
    maxTokens: 1024,
    timeoutMs: 30000,
  };

  describe('constructor', () => {
    it('creates provider with valid config', () => {
      const provider = new OpenAIAssistantProvider(defaultConfig);
      expect(provider).toBeDefined();
    });
  });

  describe('respond', () => {
    let provider: OpenAIAssistantProvider;

    beforeEach(() => {
      vi.clearAllMocks();
      provider = new OpenAIAssistantProvider(defaultConfig);
    });

    it('returns response with title and description from parsed LLM output', async () => {
      // Mock the OpenAI client response
      const mockCreate = vi.fn().mockResolvedValue({
        choices: [{ message: { content: '{"title": "Test Title", "description": "Test Desc"}' } }],
      });

      // Access the internal client and mock it
      (provider as unknown as { client: { chat: { completions: { create: typeof mockCreate } } } }).client = {
        chat: { completions: { create: mockCreate } },
      };

      const request: AssistantChatRequestWithVision = {
        items: [{ itemId: 'item-1', title: 'Test Item' }],
        message: 'Generate a listing',
      };

      const response = await provider.respond(request);

      expect(response.content).toContain('Test Title');
      expect(response.suggestedDraftUpdates).toBeDefined();
    });

    it('includes evidence from resolved attributes', async () => {
      const mockCreate = vi.fn().mockResolvedValue({
        choices: [{ message: { content: '{"title": "Test", "description": "Desc"}' } }],
      });

      (provider as unknown as { client: { chat: { completions: { create: typeof mockCreate } } } }).client = {
        chat: { completions: { create: mockCreate } },
      };

      const visualFacts = new Map<string, VisualFacts>();
      visualFacts.set('item-1', {
        itemId: 'item-1',
        dominantColors: [{ name: 'blue', rgbHex: '***REMOVED***0000FF', pct: 60 }],
        ocrSnippets: [],
        labelHints: [],
        logoHints: [{ brand: 'TestBrand', score: 0.9 }],
        extractionMeta: {
          provider: 'google',
          timingsMs: { total: 100 },
          imageCount: 1,
          imageHashes: ['abc123'],
        },
      });

      const request: AssistantChatRequestWithVision = {
        items: [{ itemId: 'item-1' }],
        message: 'Generate listing',
        visualFacts,
      };

      const response = await provider.respond(request);

      expect(response.evidence).toBeDefined();
      expect(response.suggestedAttributes).toBeDefined();
    });

    it('handles API errors gracefully', async () => {
      const mockCreate = vi.fn().mockRejectedValue(new Error('API error'));

      (provider as unknown as { client: { chat: { completions: { create: typeof mockCreate } } } }).client = {
        chat: { completions: { create: mockCreate } },
      };

      const request: AssistantChatRequestWithVision = {
        items: [{ itemId: 'item-1' }],
        message: 'Generate listing',
      };

      const response = await provider.respond(request);

      expect(response.assistantError).toBeDefined();
      expect(response.assistantError?.type).toBe('provider_unavailable');
      expect(response.assistantError?.retryable).toBe(true);
    });

    it('handles 401 unauthorized errors', async () => {
      const error = new Error('Invalid API key') as Error & { status?: number; code?: string };
      error.status = 401;
      error.code = 'invalid_api_key';
      const mockCreate = vi.fn().mockRejectedValue(error);

      (provider as unknown as { client: { chat: { completions: { create: typeof mockCreate } } } }).client = {
        chat: { completions: { create: mockCreate } },
      };

      const request: AssistantChatRequestWithVision = {
        items: [{ itemId: 'item-1' }],
        message: 'Generate listing',
      };

      const response = await provider.respond(request);

      expect(response.assistantError).toBeDefined();
      expect(response.assistantError?.type).toBe('unauthorized');
      expect(response.assistantError?.category).toBe('auth');
      expect(response.assistantError?.retryable).toBe(false);
      expect(response.assistantError?.reasonCode).toBe('UNAUTHORIZED');
    });

    it('handles 429 rate limit errors', async () => {
      const error = new Error('Rate limit exceeded') as Error & { status?: number };
      error.status = 429;
      const mockCreate = vi.fn().mockRejectedValue(error);

      (provider as unknown as { client: { chat: { completions: { create: typeof mockCreate } } } }).client = {
        chat: { completions: { create: mockCreate } },
      };

      const request: AssistantChatRequestWithVision = {
        items: [{ itemId: 'item-1' }],
        message: 'Generate listing',
      };

      const response = await provider.respond(request);

      expect(response.assistantError).toBeDefined();
      expect(response.assistantError?.type).toBe('rate_limited');
      expect(response.assistantError?.category).toBe('policy');
      expect(response.assistantError?.retryable).toBe(true);
      expect(response.assistantError?.reasonCode).toBe('RATE_LIMITED');
      expect(response.assistantError?.retryAfterSeconds).toBe(60);
    });

    it('handles 503 service unavailable errors', async () => {
      const error = new Error('Service unavailable') as Error & { status?: number };
      error.status = 503;
      const mockCreate = vi.fn().mockRejectedValue(error);

      (provider as unknown as { client: { chat: { completions: { create: typeof mockCreate } } } }).client = {
        chat: { completions: { create: mockCreate } },
      };

      const request: AssistantChatRequestWithVision = {
        items: [{ itemId: 'item-1' }],
        message: 'Generate listing',
      };

      const response = await provider.respond(request);

      expect(response.assistantError).toBeDefined();
      expect(response.assistantError?.type).toBe('provider_unavailable');
      expect(response.assistantError?.reasonCode).toBe('PROVIDER_UNAVAILABLE');
    });

    it('sets overall confidence to LOW when any draft update has LOW confidence', async () => {
      // Mock parseListingResponse to return LOW confidence
      const { parseListingResponse } = await import('./prompts/listing-generation.js');
      vi.mocked(parseListingResponse).mockReturnValueOnce({
        title: 'Test',
        suggestedDraftUpdates: [
          { field: 'title', value: 'Test', confidence: 'LOW', requiresConfirmation: true },
        ],
      });

      const mockCreate = vi.fn().mockResolvedValue({
        choices: [{ message: { content: '{}' } }],
      });

      (provider as unknown as { client: { chat: { completions: { create: typeof mockCreate } } } }).client = {
        chat: { completions: { create: mockCreate } },
      };

      const request: AssistantChatRequestWithVision = {
        items: [{ itemId: 'item-1' }],
        message: 'Generate listing',
      };

      const response = await provider.respond(request);

      expect(response.confidenceTier).toBe('LOW');
    });

    it('adds APPLY_DRAFT_UPDATE action when title/description present', async () => {
      const mockCreate = vi.fn().mockResolvedValue({
        choices: [{ message: { content: '{"title": "Title", "description": "Desc"}' } }],
      });

      (provider as unknown as { client: { chat: { completions: { create: typeof mockCreate } } } }).client = {
        chat: { completions: { create: mockCreate } },
      };

      const request: AssistantChatRequestWithVision = {
        items: [{ itemId: 'item-1' }],
        message: 'Generate listing',
      };

      const response = await provider.respond(request);

      const applyAction = response.actions?.find((a) => a.type === 'APPLY_DRAFT_UPDATE');
      expect(applyAction).toBeDefined();
      expect(applyAction?.payload?.itemId).toBe('item-1');
    });

    it('adds copy actions for title and description', async () => {
      const mockCreate = vi.fn().mockResolvedValue({
        choices: [{ message: { content: '{"title": "Copy Title", "description": "Copy Desc"}' } }],
      });

      (provider as unknown as { client: { chat: { completions: { create: typeof mockCreate } } } }).client = {
        chat: { completions: { create: mockCreate } },
      };

      const request: AssistantChatRequestWithVision = {
        items: [{ itemId: 'item-1' }],
        message: 'Generate listing',
      };

      const response = await provider.respond(request);

      const copyActions = response.actions?.filter((a) => a.type === 'COPY_TEXT');
      expect(copyActions?.length).toBeGreaterThanOrEqual(1);
    });

    it('handles empty items array', async () => {
      const mockCreate = vi.fn().mockResolvedValue({
        choices: [{ message: { content: 'No items provided' } }],
      });

      (provider as unknown as { client: { chat: { completions: { create: typeof mockCreate } } } }).client = {
        chat: { completions: { create: mockCreate } },
      };

      const request: AssistantChatRequestWithVision = {
        items: [],
        message: 'Generate listing',
      };

      const response = await provider.respond(request);

      expect(response.content).toBeDefined();
    });

    it('handles null message content gracefully', async () => {
      const mockCreate = vi.fn().mockResolvedValue({
        choices: [{ message: { content: null } }],
      });

      (provider as unknown as { client: { chat: { completions: { create: typeof mockCreate } } } }).client = {
        chat: { completions: { create: mockCreate } },
      };

      const request: AssistantChatRequestWithVision = {
        items: [{ itemId: 'item-1' }],
        message: 'Generate listing',
      };

      const response = await provider.respond(request);

      expect(response.content).toBeDefined();
    });

    it('adds missing info to warnings', async () => {
      const { parseListingResponse } = await import('./prompts/listing-generation.js');
      vi.mocked(parseListingResponse).mockReturnValueOnce({
        title: 'Test',
        description: 'Desc',
        missingInfo: ['Storage capacity', 'Screen size'],
      });

      const mockCreate = vi.fn().mockResolvedValue({
        choices: [{ message: { content: '{}' } }],
      });

      (provider as unknown as { client: { chat: { completions: { create: typeof mockCreate } } } }).client = {
        chat: { completions: { create: mockCreate } },
      };

      const request: AssistantChatRequestWithVision = {
        items: [{ itemId: 'item-1' }],
        message: 'Generate listing',
      };

      const response = await provider.respond(request);

      expect(response.content).toContain('Missing');
    });

    it('adds suggested next photo action when present', async () => {
      const { parseListingResponse } = await import('./prompts/listing-generation.js');
      vi.mocked(parseListingResponse).mockReturnValueOnce({
        title: 'Test',
        suggestedNextPhoto: 'Take a photo of the label',
      });

      const mockCreate = vi.fn().mockResolvedValue({
        choices: [{ message: { content: '{}' } }],
      });

      (provider as unknown as { client: { chat: { completions: { create: typeof mockCreate } } } }).client = {
        chat: { completions: { create: mockCreate } },
      };

      const request: AssistantChatRequestWithVision = {
        items: [{ itemId: 'item-1' }],
        message: 'Generate listing',
      };

      const response = await provider.respond(request);

      const photoAction = response.actions?.find((a) => a.type === 'SUGGEST_NEXT_PHOTO');
      expect(photoAction).toBeDefined();
      expect(response.suggestedNextPhoto).toBe('Take a photo of the label');
    });
  });
});
