import { afterAll, describe, expect, it } from 'vitest';
import { buildApp } from '../../app.js';
import { configSchema } from '../../config/index.js';
import FormData from 'form-data';

// Minimal 1x1 PNG for testing (base64)
const TEST_IMAGE_BASE64 =
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==';

const config = configSchema.parse({
  nodeEnv: 'test',
  port: 8080,
  publicBaseUrl: 'http://localhost:8080',
  databaseUrl: 'postgresql://user:pass@localhost:5432/db',
  classifier: {
    provider: 'mock',
    apiKeys: 'test-key',
    domainPackPath: 'src/modules/classifier/domain/home-resale.json',
  },
  assistant: {
    provider: 'mock',
    apiKeys: 'assist-key',
  },
  vision: {
    enabled: true,
    provider: 'mock',
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

const appPromise = buildApp(config);

afterAll(async () => {
  const app = await appPromise;
  await app.close();
});

describe('POST /v1/assist/chat', () => {
  it('rejects requests without API key', async () => {
    const app = await appPromise;
    const res = await app.inject({
      method: 'POST',
      url: '/v1/assist/chat',
      payload: { items: [], message: 'Hi' },
    });

    expect(res.statusCode).toBe(401);
  });

  describe('empty items handling (allowEmptyItems flag)', () => {
    it('returns default "attach item" message when flag is OFF and items is empty', async () => {
      const app = await appPromise;
      const res = await app.inject({
        method: 'POST',
        url: '/v1/assist/chat',
        headers: { 'x-api-key': 'assist-key' },
        payload: {
          items: [],
          message: 'Help me with my listing',
        },
      });

      expect(res.statusCode).toBe(200);
      const body = JSON.parse(res.body);
      expect(body.reply).toContain('Attach at least one item');
      expect(body.correlationId).toBeDefined();
    });

    it('returns helpful message when flag is ON and items is empty', async () => {
      // Create a separate app with allowEmptyItems enabled
      const configWithFlag = configSchema.parse({
        nodeEnv: 'test',
        port: 8081,
        publicBaseUrl: 'http://localhost:8081',
        databaseUrl: 'postgresql://user:pass@localhost:5432/db',
        classifier: {
          provider: 'mock',
          apiKeys: 'test-key',
          domainPackPath: 'src/modules/classifier/domain/home-resale.json',
        },
        assistant: {
          provider: 'mock',
          apiKeys: 'assist-key',
          allowEmptyItems: true,
        },
        vision: {
          enabled: true,
          provider: 'mock',
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

      const appWithFlag = await buildApp(configWithFlag);

      try {
        const res = await appWithFlag.inject({
          method: 'POST',
          url: '/v1/assist/chat',
          headers: { 'x-api-key': 'assist-key' },
          payload: {
            items: [],
            message: 'Help me with my listing',
          },
        });

        expect(res.statusCode).toBe(200);
        const body = JSON.parse(res.body);
        expect(body.reply).toBe('Assistant is enabled. Add an item to get listing advice.');
        expect(body.actions).toEqual([]);
        expect(body.citationsMetadata).toEqual({});
        expect(body.safety).toBeDefined();
        expect(body.safety.blocked).toBe(false);
        expect(body.correlationId).toBeDefined();
      } finally {
        await appWithFlag.close();
      }
    });

    it('still requires API key when flag is ON', async () => {
      const configWithFlag = configSchema.parse({
        nodeEnv: 'test',
        port: 8082,
        publicBaseUrl: 'http://localhost:8082',
        databaseUrl: 'postgresql://user:pass@localhost:5432/db',
        classifier: {
          provider: 'mock',
          apiKeys: 'test-key',
          domainPackPath: 'src/modules/classifier/domain/home-resale.json',
        },
        assistant: {
          provider: 'mock',
          apiKeys: 'assist-key',
          allowEmptyItems: true,
        },
        vision: {
          enabled: true,
          provider: 'mock',
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

      const appWithFlag = await buildApp(configWithFlag);

      try {
        const res = await appWithFlag.inject({
          method: 'POST',
          url: '/v1/assist/chat',
          payload: {
            items: [],
            message: 'Help me',
          },
        });

        expect(res.statusCode).toBe(401);
      } finally {
        await appWithFlag.close();
      }
    });
  });

  it('returns assistant response for mock provider', async () => {
    const app = await appPromise;
    const res = await app.inject({
      method: 'POST',
      url: '/v1/assist/chat',
      headers: { 'x-api-key': 'assist-key' },
      payload: {
        items: [
          {
            itemId: 'item-1',
            title: 'Vintage Lamp',
            category: 'Lighting',
            priceEstimate: 40,
            photosCount: 2,
          },
        ],
        message: 'Suggest a better title',
      },
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(typeof body.reply).toBe('string');
    expect(body.actions?.length).toBeGreaterThan(0);
  });

  it('returns assistant error payload for validation failures', async () => {
    const app = await appPromise;
    const res = await app.inject({
      method: 'POST',
      url: '/v1/assist/chat',
      headers: { 'x-api-key': 'assist-key' },
      payload: {
        items: 'invalid',
        message: 42,
      },
    });

    expect(res.statusCode).toBe(400);
    const body = JSON.parse(res.body);
    expect(body.assistantError?.type).toBe('validation_error');
    expect(body.assistantError?.category).toBe('policy');
  });

  it('refuses disallowed scraping requests', async () => {
    const app = await appPromise;
    const res = await app.inject({
      method: 'POST',
      url: '/v1/assist/chat',
      headers: { 'x-api-key': 'assist-key' },
      payload: {
        items: [],
        message: 'Scrape prices from random webshop',
      },
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    // Refusal response is generic - doesn't reveal why the request was blocked
    expect(body.reply.toLowerCase()).toContain("can't assist");
    expect(body.actions?.length || 0).toBe(0);
  });

  describe('Vision-grounded responses', () => {
    it('returns confidenceTier and evidence for color questions with images', async () => {
      const app = await appPromise;

      const form = new FormData();
      form.append(
        'payload',
        JSON.stringify({
          items: [
            {
              itemId: 'item-vision-1',
              title: 'Blue Chair',
              category: 'Furniture',
            },
          ],
          message: 'What color is it?',
        })
      );
      form.append('itemImages[item-vision-1]', Buffer.from(TEST_IMAGE_BASE64, 'base64'), {
        filename: 'test.png',
        contentType: 'image/png',
      });

      const res = await app.inject({
        method: 'POST',
        url: '/v1/assist/chat',
        headers: {
          'x-api-key': 'assist-key',
          ...form.getHeaders(),
        },
        payload: form.getBuffer(),
      });

      expect(res.statusCode).toBe(200);
      const result = JSON.parse(res.body);

      // Should have grounded response elements
      expect(result.confidenceTier).toBeDefined();
      expect(['HIGH', 'MED', 'LOW']).toContain(result.confidenceTier);
      expect(Array.isArray(result.evidence)).toBe(true);
      expect(result.evidence.length).toBeGreaterThan(0);

      // Check evidence structure
      const colorEvidence = result.evidence.find(
        (e: { type: string }) => e.type === 'color'
      );
      expect(colorEvidence).toBeDefined();
      expect(colorEvidence.text).toContain('Dominant color');
    });

    it('returns suggestedAttributes for brand questions with images', async () => {
      const app = await appPromise;

      const form = new FormData();
      form.append(
        'payload',
        JSON.stringify({
          items: [
            {
              itemId: 'item-vision-2',
              title: 'Shelf Unit',
              category: 'Furniture',
            },
          ],
          message: 'Which brand is this?',
        })
      );
      form.append('itemImages[item-vision-2]', Buffer.from(TEST_IMAGE_BASE64, 'base64'), {
        filename: 'test.png',
        contentType: 'image/png',
      });

      const res = await app.inject({
        method: 'POST',
        url: '/v1/assist/chat',
        headers: {
          'x-api-key': 'assist-key',
          ...form.getHeaders(),
        },
        payload: form.getBuffer(),
      });

      expect(res.statusCode).toBe(200);
      const result = JSON.parse(res.body);

      // Should have grounded response elements
      expect(result.confidenceTier).toBeDefined();

      // Should have evidence from OCR/logo detection
      if (result.evidence && result.evidence.length > 0) {
        const ocrEvidence = result.evidence.find(
          (e: { type: string }) => e.type === 'ocr' || e.type === 'logo'
        );
        expect(ocrEvidence).toBeDefined();
      }

      // Should have suggested brand attribute
      if (result.suggestedAttributes && result.suggestedAttributes.length > 0) {
        const brandSuggestion = result.suggestedAttributes.find(
          (a: { key: string }) => a.key === 'brand'
        );
        expect(brandSuggestion).toBeDefined();
        expect(brandSuggestion.value).toBeDefined();
        expect(['HIGH', 'MED', 'LOW']).toContain(brandSuggestion.confidence);
      }
    });

    it('handles vision cache hit on repeated requests', async () => {
      const app = await appPromise;

      const buildForm = () => {
        const form = new FormData();
        form.append(
          'payload',
          JSON.stringify({
            items: [
              {
                itemId: 'item-cache-test',
                title: 'Test Item',
                category: 'Test',
              },
            ],
            message: 'What color is this item?',
          })
        );
        form.append('itemImages[item-cache-test]', Buffer.from(TEST_IMAGE_BASE64, 'base64'), {
          filename: 'test.png',
          contentType: 'image/png',
        });
        return form;
      };

      // First request - should extract
      const form1 = buildForm();
      const res1 = await app.inject({
        method: 'POST',
        url: '/v1/assist/chat',
        headers: {
          'x-api-key': 'assist-key',
          ...form1.getHeaders(),
        },
        payload: form1.getBuffer(),
      });

      expect(res1.statusCode).toBe(200);
      const result1 = JSON.parse(res1.body);
      expect(result1.confidenceTier).toBeDefined();

      // Second request with same image - should hit cache
      const form2 = buildForm();
      const res2 = await app.inject({
        method: 'POST',
        url: '/v1/assist/chat',
        headers: {
          'x-api-key': 'assist-key',
          ...form2.getHeaders(),
        },
        payload: form2.getBuffer(),
      });

      expect(res2.statusCode).toBe(200);
      const result2 = JSON.parse(res2.body);
      expect(result2.confidenceTier).toBeDefined();

      // Both responses should have similar evidence (from cache)
      expect(result1.evidence?.length).toBe(result2.evidence?.length);
    });

    it('falls back gracefully without images for non-visual questions', async () => {
      const app = await appPromise;
      const res = await app.inject({
        method: 'POST',
        url: '/v1/assist/chat',
        headers: { 'x-api-key': 'assist-key' },
        payload: {
          items: [
            {
              itemId: 'item-no-vision',
              title: 'Vintage Lamp',
              category: 'Lighting',
              priceEstimate: 40,
            },
          ],
          message: 'What price should I set?',
        },
      });

      expect(res.statusCode).toBe(200);
      const body = JSON.parse(res.body);

      // Should get a response but no vision-specific fields
      expect(typeof body.reply).toBe('string');
      // For non-visual questions without images, these fields should be absent
      expect(body.confidenceTier).toBeUndefined();
      expect(body.evidence).toBeUndefined();
    });
  });
});
