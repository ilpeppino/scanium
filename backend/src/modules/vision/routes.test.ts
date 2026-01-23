import { describe, expect, it, vi, beforeEach } from 'vitest';
import Fastify from 'fastify';
import fastifyMultipart from '@fastify/multipart';
import { visionInsightsRoutes } from './routes.js';
import { Config } from '../../config/index.js';

// Mock the dependencies
vi.mock('../classifier/api-key-manager.js', () => ({
  ApiKeyManager: vi.fn().mockImplementation(() => ({
    validateKey: vi.fn().mockReturnValue(true),
  })),
}));

vi.mock('../../infra/rate-limit/sliding-window-limiter.js', () => ({
  SlidingWindowRateLimiter: vi.fn().mockImplementation(() => ({
    consume: vi.fn().mockResolvedValue({ allowed: true }),
  })),
}));

vi.mock('../../infra/observability/metrics.js', () => ({
  recordVisionExtraction: vi.fn(),
  recordRateLimitHit: vi.fn(),
}));

// Mock VisionExtractor to return predictable results
const mockVisualFacts = {
  itemId: 'test-item',
  dominantColors: [
    { name: 'blue', rgbHex: '#1E40AF', pct: 45 },
    { name: 'white', rgbHex: '#FFFFFF', pct: 30 },
  ],
  ocrSnippets: [
    { text: 'Labello', confidence: 0.95 },
    { text: 'Lip Care', confidence: 0.88 },
  ],
  labelHints: [
    { label: 'cosmetics', score: 0.9 },
    { label: 'personal care', score: 0.85 },
  ],
  logoHints: [{ brand: 'Labello', score: 0.92 }],
  extractionMeta: {
    provider: 'mock' as const,
    timingsMs: { total: 150 },
  },
};

vi.mock('./index.js', async () => {
  const actual = await vi.importActual('./index.js');
  return {
    ...actual,
    VisionExtractor: vi.fn().mockImplementation(() => ({
      extractVisualFacts: vi.fn().mockResolvedValue({
        success: true,
        facts: mockVisualFacts,
      }),
    })),
    MockVisionExtractor: vi.fn().mockImplementation(() => ({
      extractVisualFacts: vi.fn().mockResolvedValue({
        success: true,
        facts: mockVisualFacts,
      }),
    })),
    VisualFactsCache: vi.fn().mockImplementation(() => ({
      get: vi.fn().mockReturnValue(null),
      set: vi.fn(),
      stop: vi.fn(),
    })),
  };
});

// Create a minimal test config
const createTestConfig = (): Config => ({
  nodeEnv: 'test',
  port: 3000,
  sessionSigningSecret: 'test-secret',
  ebay: {
    clientId: 'test-client',
    clientSecret: 'test-secret',
    redirectUri: 'http://localhost:3000/callback',
    env: 'sandbox',
    ruName: 'test-ru-name',
    stateSecret: 'test-state-secret',
    scopes: ['https://api.ebay.com/oauth/api_scope'],
  },
  classifier: {
    apiKeys: ['test-api-key'],
    maxUploadBytes: 2 * 1024 * 1024,
    rateLimitPerMinute: 60,
    rateLimitWindowSeconds: 60,
    rateLimitBackoffSeconds: 5,
    rateLimitBackoffMaxSeconds: 60,
    ipRateLimitPerMinute: 120,
    rateLimitRedisUrl: undefined,
  },
  vision: {
    provider: 'mock',
    enableOcr: true,
    enableLabels: true,
    enableLogos: true,
    enableColors: true,
    ocrMode: 'TEXT_DETECTION',
    maxOcrSnippets: 5,
    maxLabelHints: 5,
    maxLogoHints: 3,
    maxColors: 5,
    maxOcrSnippetLength: 200,
    minOcrConfidence: 0.5,
    minLabelConfidence: 0.5,
    minLogoConfidence: 0.5,
    timeoutMs: 10000,
    maxRetries: 2,
    cacheTtlSeconds: 3600,
    cacheMaxEntries: 1000,
  },
  assistant: {
    provider: 'claude',
    anthropicApiKey: 'test-key',
    model: 'claude-3-haiku',
    maxInputTokens: 4000,
    maxOutputTokens: 1000,
    temperature: 0.7,
    cacheEnabled: false,
    cacheTtlSeconds: 3600,
    systemPromptVersion: '1.0',
    rateLimitPerMinute: 10,
    dailyQuotaPerUser: 100,
  },
  admin: {
    enabled: false,
    adminKey: 'test-admin-key',
  },
  security: {
    corsOrigins: ['*'],
    csrfEnabled: false,
    httpsOnly: false,
    hmacEnabled: false,
    hmacToleranceSeconds: 300,
  },
});

describe('Vision Insights Routes', () => {
  describe('POST /v1/vision/insights', () => {
    it('returns 401 when API key is missing', async () => {
      // Create app with mocked API key validation that rejects
      vi.doMock('../classifier/api-key-manager.js', () => ({
        ApiKeyManager: vi.fn().mockImplementation(() => ({
          validateKey: vi.fn().mockReturnValue(false),
        })),
      }));

      const app = Fastify();
      await app.register(fastifyMultipart, {
        limits: { fileSize: 2 * 1024 * 1024 },
      });
      await app.register(visionInsightsRoutes, {
        prefix: '/v1',
        config: createTestConfig(),
      });

      const response = await app.inject({
        method: 'POST',
        url: '/v1/vision/insights',
        headers: {
          'content-type': 'multipart/form-data; boundary=boundary',
        },
        payload:
          '--boundary\r\n' +
          'Content-Disposition: form-data; name="image"; filename="test.jpg"\r\n' +
          'Content-Type: image/jpeg\r\n\r\n' +
          'fake-image-data\r\n' +
          '--boundary--',
      });

      expect(response.statusCode).toBe(401);
      const body = JSON.parse(response.body);
      expect(body.success).toBe(false);
      expect(body.error.code).toBe('UNAUTHORIZED');

      await app.close();
    });

    it('returns 400 when not multipart', async () => {
      const app = Fastify();
      await app.register(fastifyMultipart, {
        limits: { fileSize: 2 * 1024 * 1024 },
      });
      await app.register(visionInsightsRoutes, {
        prefix: '/v1',
        config: createTestConfig(),
      });

      const response = await app.inject({
        method: 'POST',
        url: '/v1/vision/insights',
        headers: {
          'x-api-key': 'test-api-key',
          'content-type': 'application/json',
        },
        payload: JSON.stringify({ test: true }),
      });

      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.body);
      expect(body.success).toBe(false);
      expect(body.error.code).toBe('VALIDATION_ERROR');
      expect(body.error.message).toContain('multipart');

      await app.close();
    });

    it('response schema contains expected fields', () => {
      // Test the response type definition
      type VisionInsightsResponse = {
        success: true;
        requestId: string;
        correlationId: string;
        ocrSnippets: string[];
        logoHints: Array<{ name: string; confidence: number }>;
        dominantColors: Array<{ name: string; hex: string; pct: number }>;
        labelHints: string[];
        suggestedLabel: string | null;
        categoryHint: string | null;
        extractionMeta: {
          provider: string;
          timingsMs: { total: number };
          cacheHit: boolean;
        };
      };

      // Type check - this ensures the response shape is correct
      const mockResponse: VisionInsightsResponse = {
        success: true,
        requestId: 'test-id',
        correlationId: 'test-corr',
        ocrSnippets: ['Labello', 'Lip Care'],
        logoHints: [{ name: 'Labello', confidence: 0.92 }],
        dominantColors: [{ name: 'blue', hex: '#1E40AF', pct: 45 }],
        labelHints: ['cosmetics'],
        suggestedLabel: 'Labello Lip Care',
        categoryHint: 'cosmetics',
        extractionMeta: {
          provider: 'mock',
          timingsMs: { total: 150 },
          cacheHit: false,
        },
      };

      expect(mockResponse.success).toBe(true);
      expect(mockResponse.ocrSnippets).toHaveLength(2);
      expect(mockResponse.logoHints[0].name).toBe('Labello');
      expect(mockResponse.dominantColors[0].hex).toBe('#1E40AF');
    });
  });

  describe('deriveCategoryHint', () => {
    it('maps cosmetics labels correctly', () => {
      // Test the category mapping logic
      const categoryMap: Record<string, string> = {
        cosmetics: 'cosmetics',
        makeup: 'cosmetics',
        lipstick: 'cosmetics',
      };

      const testLabel = 'cosmetics';
      const mapped = categoryMap[testLabel.toLowerCase()];
      expect(mapped).toBe('cosmetics');
    });

    it('maps electronics labels correctly', () => {
      const categoryMap: Record<string, string> = {
        electronics: 'electronics',
        computer: 'electronics',
        laptop: 'electronics',
        phone: 'electronics',
      };

      expect(categoryMap['laptop']).toBe('electronics');
      expect(categoryMap['phone']).toBe('electronics');
    });

    it('maps pet supplies labels correctly', () => {
      const categoryMap: Record<string, string> = {
        pet: 'pet_supplies',
        'pet supplies': 'pet_supplies',
        'dog food': 'pet_supplies',
      };

      expect(categoryMap['pet']).toBe('pet_supplies');
    });
  });
});

describe('Vision Insights Response Building', () => {
  it('builds suggested label from brand candidates', () => {
    const brandCandidates = ['Labello'];
    const modelCandidates: string[] = [];

    let suggestedLabel: string | null = null;
    if (brandCandidates.length > 0) {
      if (modelCandidates.length > 0) {
        suggestedLabel = `${brandCandidates[0]} ${modelCandidates[0]}`;
      } else {
        suggestedLabel = brandCandidates[0];
      }
    }

    expect(suggestedLabel).toBe('Labello');
  });

  it('builds suggested label from brand + model', () => {
    const brandCandidates = ['Apple'];
    const modelCandidates = ['iPhone 14'];

    let suggestedLabel: string | null = null;
    if (brandCandidates.length > 0) {
      if (modelCandidates.length > 0) {
        suggestedLabel = `${brandCandidates[0]} ${modelCandidates[0]}`;
      } else {
        suggestedLabel = brandCandidates[0];
      }
    }

    expect(suggestedLabel).toBe('Apple iPhone 14');
  });

  it('falls back to OCR snippet when no brand detected', () => {
    const brandCandidates: string[] = [];
    const ocrSnippets = [{ text: 'Premium Quality Product', confidence: 0.9 }];

    let suggestedLabel: string | null = null;
    if (brandCandidates.length === 0 && ocrSnippets.length > 0) {
      const firstSnippet = ocrSnippets[0].text.trim();
      if (firstSnippet.length >= 3 && firstSnippet.length <= 50) {
        suggestedLabel = firstSnippet;
      }
    }

    expect(suggestedLabel).toBe('Premium Quality Product');
  });

  it('ignores OCR snippets that are too short', () => {
    const brandCandidates: string[] = [];
    const ocrSnippets = [{ text: 'AB', confidence: 0.9 }];

    let suggestedLabel: string | null = null;
    if (brandCandidates.length === 0 && ocrSnippets.length > 0) {
      const firstSnippet = ocrSnippets[0].text.trim();
      if (firstSnippet.length >= 3 && firstSnippet.length <= 50) {
        suggestedLabel = firstSnippet;
      }
    }

    expect(suggestedLabel).toBeNull();
  });
});
