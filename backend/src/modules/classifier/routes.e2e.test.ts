import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';
import FormData from 'form-data';

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

const tinyJpeg = Buffer.from(
  '/9j/2wBDAAYEBQYFBAYGBQYHBwYIChAKCgkJChQODwwQFxQYGBcUFhYaHSUfGhsjHBYWICwgIyYnKSopGR8tMC0oMCUoKSj/2wBDAQcHBwoIChMKChMoGhYaKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCj/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAj/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFAEBAAAAAAAAAAAAAAAAAAAAAP/EABQRAQAAAAAAAAAAAAAAAAAAAAD/2gAMAwEAAhEDEQA/AKpAB//Z',
  'base64'
);

let buildApp: typeof import('../../app.js').buildApp;
let configSchema: typeof import('../../config/index.js').configSchema;
let appPromise: ReturnType<typeof import('../../app.js').buildApp>;

beforeAll(async () => {
  ({ buildApp } = await import('../../app.js'));
  ({ configSchema } = await import('../../config/index.js'));

  const config = configSchema.parse({
    nodeEnv: 'test',
    port: 8080,
    publicBaseUrl: 'http://localhost:8080',
    databaseUrl: 'postgresql://user:pass@localhost:5432/db',
    classifier: {
      provider: 'google',
      visionFeature: 'LABEL_DETECTION,TEXT_DETECTION,IMAGE_PROPERTIES,LOGO_DETECTION',
      apiKeys: 'test-key',
      domainPackPath: 'src/modules/classifier/domain/home-resale.json',
      enableAttributeEnrichment: true,
    },
    assistant: {
      provider: 'disabled',
      apiKeys: '',
    },
    vision: {
      enabled: true,
      provider: 'mock',
      enableOcr: true,
      ocrMode: 'TEXT_DETECTION',
      enableLabels: true,
      enableLogos: true,
      enableColors: true,
    },
    ebay: {
      env: 'sandbox',
      clientId: 'client',
      clientSecret: 'client-secret-minimum-length-please',
      scopes: 'scope',
      tokenEncryptionKey: 'x'.repeat(32),
    },
    sessionSigningSecret: 'x'.repeat(64),
    security: {
      enforceHttps: false,
      enableHsts: false,
      apiKeyRotationEnabled: false,
      apiKeyExpirationDays: 90,
      logApiKeyUsage: false,
    },
    corsOrigins: 'http://localhost',
  });

  appPromise = buildApp(config);
});

afterAll(async () => {
  const app = await appPromise;
  await app.close();
});

describe('POST /v1/classify (vision attributes)', () => {
  it('returns visualFacts and enrichedAttributes together', async () => {
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

    const app = await appPromise;
    const form = new FormData();
    form.append('image', tinyJpeg, {
      filename: 'tiny.jpg',
      contentType: 'image/jpeg',
    });
    form.append('domainPackId', 'home_resale');
    form.append('enrichAttributes', 'true');

    const res = await app.inject({
      method: 'POST',
      url: '/v1/classify',
      headers: {
        ...form.getHeaders(),
        'x-api-key': 'test-key',
      },
      payload: form,
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.visualFacts).toBeDefined();
    expect(body.visualFacts.ocrSnippets.some((s: { text: string }) => s.text === 'IKEA')).toBe(
      true
    );
    expect(body.visualFacts.dominantColors[0].rgbHex).toBe('***REMOVED***FF0000');
    expect(body.enrichedAttributes.brand.value).toBe('IKEA');
    expect(body.enrichedAttributes.color.value).toBe('red');
  });
});
