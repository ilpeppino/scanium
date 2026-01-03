import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Config } from '../../config/index.js';
import { ClassifierService } from './service.js';

const mockBatchAnnotateImages = vi.hoisted(() => vi.fn());
const mockImageAnnotatorClient = vi.hoisted(
  () => vi.fn(() => ({ batchAnnotateImages: mockBatchAnnotateImages }))
);

vi.mock('@google-cloud/vision', () => ({
  ImageAnnotatorClient: mockImageAnnotatorClient,
  protos: {
    google: {
      cloud: {
        vision: {
          v1: {
            Feature: {
              Type: {
                OBJECT_LOCALIZATION: 1,
                LABEL_DETECTION: 2,
                TEXT_DETECTION: 3,
                DOCUMENT_TEXT_DETECTION: 4,
                LOGO_DETECTION: 5,
                IMAGE_PROPERTIES: 6,
              },
            },
          },
        },
      },
    },
  },
}));

describe('ClassifierService', () => {
  beforeEach(() => {
    mockBatchAnnotateImages.mockReset();
  });

  it('returns visualFacts and enrichedAttributes from Vision response', async () => {
    const response = {
      textAnnotations: [
        { description: 'FULL\nTEXT' },
        { description: 'IKEA', score: 0.92 },
        { description: 'KALLAX', score: 0.86 },
      ],
      labelAnnotations: [{ description: 'Furniture', score: 0.9 }],
      logoAnnotations: [{ description: 'IKEA', score: 0.88 }],
      imagePropertiesAnnotation: {
        dominantColors: {
          colors: [
            { color: { red: 255, green: 0, blue: 0 }, pixelFraction: 0.6 },
            { color: { red: 0, green: 0, blue: 0 }, pixelFraction: 0.2 },
          ],
        },
      },
    };

    mockBatchAnnotateImages.mockResolvedValueOnce([{ responses: [response] }]);

    const config: Config = {
      nodeEnv: 'test',
      port: 8080,
      publicBaseUrl: 'http://localhost:8080',
      databaseUrl: 'postgresql://user:pass@localhost:5432/db',
      classifier: {
        provider: 'google',
        visionFeature: [
          'LABEL_DETECTION',
          'TEXT_DETECTION',
          'IMAGE_PROPERTIES',
          'LOGO_DETECTION',
        ],
        maxUploadBytes: 5242880,
        rateLimitPerMinute: 60,
        ipRateLimitPerMinute: 60,
        rateLimitWindowSeconds: 60,
        rateLimitBackoffSeconds: 30,
        rateLimitBackoffMaxSeconds: 900,
        rateLimitRedisUrl: undefined,
        concurrentLimit: 2,
        apiKeys: ['test-key'],
        domainPackId: 'home_resale',
        domainPackPath: 'src/modules/classifier/domain/home-resale.json',
        retainUploads: false,
        mockSeed: 'test',
        visionTimeoutMs: 10000,
        visionMaxRetries: 2,
        cacheTtlSeconds: 300,
        cacheMaxEntries: 1000,
        circuitBreakerFailureThreshold: 5,
        circuitBreakerCooldownSeconds: 60,
        circuitBreakerMinimumRequests: 3,
        enableAttributeEnrichment: true,
      },
      assistant: {
        provider: 'disabled',
        apiKeys: [],
      },
      vision: {
        enabled: true,
        provider: 'mock',
        enableOcr: true,
        ocrMode: 'TEXT_DETECTION',
        enableLabels: true,
        enableLogos: true,
        enableColors: true,
        timeoutMs: 10000,
        maxRetries: 2,
        cacheTtlSeconds: 3600,
        cacheMaxEntries: 500,
        maxOcrSnippets: 10,
        maxLabelHints: 10,
        maxLogoHints: 5,
        maxColors: 5,
        maxOcrSnippetLength: 100,
        minOcrConfidence: 0.5,
        minLabelConfidence: 0.5,
        minLogoConfidence: 0.5,
      },
      googleCredentialsPath: undefined,
      ebay: {
        env: 'sandbox',
        clientId: 'test-client-id',
        clientSecret: 'test-client-secret',
        redirectPath: '/auth/ebay/callback',
        scopes: 'https://api.ebay.com/oauth/api_scope',
        tokenEncryptionKey: 'test-secret-must-be-at-least-32-chars-long-for-security',
      },
      sessionSigningSecret: 'test-secret-must-be-at-least-32-chars-long-for-security',
      security: {
        enforceHttps: true,
        enableHsts: true,
        apiKeyRotationEnabled: true,
        apiKeyExpirationDays: 90,
        logApiKeyUsage: false,
      },
      corsOrigins: ['http://localhost:3000'],
    };

    const service = new ClassifierService({ config });

    const result = await service.classify({
      requestId: 'req-1',
      correlationId: 'corr-1',
      imageHash: 'hash-1',
      buffer: Buffer.from('test-image'),
      contentType: 'image/jpeg',
      fileName: 'test.jpg',
      domainPackId: 'home_resale',
      enrichAttributes: true,
    });

    expect(result.visualFacts).toBeDefined();
    expect(result.visualFacts?.ocrSnippets.some((s) => s.text === 'IKEA')).toBe(true);
    expect(result.visualFacts?.dominantColors[0]?.rgbHex).toBe('#FF0000');

    expect(result.enrichedAttributes?.brand?.value).toBe('IKEA');
    expect(result.enrichedAttributes?.brand?.evidenceRefs[0]?.type).toBe('logo');
    expect(result.enrichedAttributes?.color?.value).toBe('red');
    expect(mockBatchAnnotateImages).toHaveBeenCalledTimes(1);
  });
});
