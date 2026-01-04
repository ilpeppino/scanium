/**
 * Tests for attribute enrichment via VisionExtractor.
 *
 * Verifies that:
 * 1. enrichAttributes=true returns enriched attributes even with mock classifier
 * 2. Vision extraction is decoupled from classification provider
 * 3. Invalid images return proper error responses
 * 4. Vision cache is used correctly
 */
import { afterAll, describe, expect, it } from 'vitest';
import FormData from 'form-data';
import { buildApp } from '../../app.js';
import { configSchema } from '../../config/index.js';

// Minimal 1x1 PNG for testing
const tinyPng = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/wwAAgMBAp3N5WkAAAAASUVORK5CYII=',
  'base64'
);

// Minimal valid JPEG
const tinyJpeg = Buffer.from(
  '/9j/2wBDAAYEBQYFBAYGBQYHBwYIChAKCgkJChQODwwQFxQYGBcUFhYaHSUfGhsjHBYWICwgIyYnKSopGR8tMC0oMCUoKSj/2wBDAQcHBwoIChMKChMoGhYaKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCj/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAj/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFAEBAAAAAAAAAAAAAAAAAAAAAP/EABQRAQAAAAAAAAAAAAAAAAAAAAD/2gAMAwEAAhEDEQA/AKpAB//Z',
  'base64'
);

describe('Attribute Enrichment with Mock Providers', () => {
  /**
   * Test: Mock classifier + Mock vision enrichment
   * This verifies that enrichAttributes works even when both providers are mock.
   */
  describe('Mock Classifier + Mock Vision', () => {
    const config = configSchema.parse({
      nodeEnv: 'test',
      port: 8091,
      publicBaseUrl: 'http://localhost:8091',
      databaseUrl: 'postgresql://user:pass@localhost:5432/db',
      classifier: {
        provider: 'mock', // Mock classification
        apiKeys: 'test-key',
        domainPackPath: 'src/modules/classifier/domain/home-resale.json',
        enableAttributeEnrichment: true,
      },
      vision: {
        enabled: true,
        provider: 'mock', // Mock vision extraction
        enableOcr: true,
        enableLabels: true,
        enableLogos: true,
        enableColors: true,
      },
      assistant: {
        provider: 'disabled',
        apiKeys: '',
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

    it('returns enrichedAttributes with mock vision provider', async () => {
      const app = await appPromise;
      const form = new FormData();
      form.append('image', tinyPng, {
        filename: 'test.png',
        contentType: 'image/png',
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

      // Basic response fields
      expect(body.requestId).toBeDefined();
      expect(body.provider).toBe('mock');
      expect(body.providerUnavailable).toBe(true); // Mock classifier sets this

      // Vision stats should be populated
      expect(body.visionStats).toBeDefined();
      expect(body.visionStats.attempted).toBe(true);
      expect(body.visionStats.visionProvider).toBe('mock');

      // Visual facts from MockVisionExtractor
      expect(body.visualFacts).toBeDefined();
      expect(body.visualFacts.dominantColors.length).toBeGreaterThan(0);
      expect(body.visualFacts.ocrSnippets.length).toBeGreaterThan(0);
      expect(body.visualFacts.labelHints.length).toBeGreaterThan(0);

      // Enriched attributes should be derived from visual facts
      expect(body.enrichedAttributes).toBeDefined();
      expect(body.enrichedAttributes.brand).toBeDefined();
      expect(body.enrichedAttributes.brand.value).toBe('IKEA'); // From MockVisionExtractor
      expect(body.enrichedAttributes.color).toBeDefined();

      // Vision attributes summary
      expect(body.visionAttributes).toBeDefined();
      expect(body.visionAttributes.colors.length).toBeGreaterThan(0);
      expect(body.visionAttributes.brandCandidates).toContain('IKEA');
    });

    it('returns timingsMs.enrichment when enrichAttributes=true', async () => {
      const app = await appPromise;
      const form = new FormData();
      form.append('image', tinyJpeg, {
        filename: 'test.jpg',
        contentType: 'image/jpeg',
      });
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

      expect(body.timingsMs).toBeDefined();
      expect(typeof body.timingsMs.total).toBe('number');
      expect(typeof body.timingsMs.enrichment).toBe('number');
    });

    it('skips enrichment when enrichAttributes=false with unique image', async () => {
      const app = await appPromise;
      // Create a unique image to avoid cache hits
      const uniqueImage = Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
        'base64'
      );
      const form = new FormData();
      form.append('image', uniqueImage, {
        filename: 'no-enrich.png',
        contentType: 'image/png',
      });
      form.append('enrichAttributes', 'false');

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

      // No enrichment fields when enrichAttributes=false
      expect(body.enrichedAttributes).toBeUndefined();
      expect(body.visualFacts).toBeUndefined();
      expect(body.visionAttributes).toBeUndefined();
      expect(body.visionStats).toBeUndefined();
    });

    it('uses query param enrichAttributes=true', async () => {
      const app = await appPromise;
      const form = new FormData();
      form.append('image', tinyPng, {
        filename: 'test.png',
        contentType: 'image/png',
      });

      const res = await app.inject({
        method: 'POST',
        url: '/v1/classify?enrichAttributes=true',
        headers: {
          ...form.getHeaders(),
          'x-api-key': 'test-key',
        },
        payload: form,
      });

      expect(res.statusCode).toBe(200);
      const body = JSON.parse(res.body);

      expect(body.enrichedAttributes).toBeDefined();
      expect(body.visualFacts).toBeDefined();
    });

    it('uses classifier cache for repeated requests with same image', async () => {
      const app = await appPromise;

      // Use a unique image for this test to ensure no prior cache state
      const cacheTestImage = Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+P+/HgAFhAJ/wlseKgAAAABJRU5ErkJggg==',
        'base64'
      );

      // First request - should miss classifier cache
      const form1 = new FormData();
      form1.append('image', cacheTestImage, {
        filename: 'cache-test.png',
        contentType: 'image/png',
      });
      form1.append('enrichAttributes', 'true');

      const res1 = await app.inject({
        method: 'POST',
        url: '/v1/classify',
        headers: {
          ...form1.getHeaders(),
          'x-api-key': 'test-key',
        },
        payload: form1,
      });

      expect(res1.statusCode).toBe(200);
      const body1 = JSON.parse(res1.body);
      expect(body1.cacheHit).toBe(false);
      expect(body1.visionStats.visionExtractions).toBe(1);
      expect(body1.visionStats.visionCacheHits).toBe(0);

      // Second request with same image - should hit classifier cache
      // Note: The classifier has its own cache, so we get a full response cache hit
      const form2 = new FormData();
      form2.append('image', cacheTestImage, {
        filename: 'cache-test.png',
        contentType: 'image/png',
      });
      form2.append('enrichAttributes', 'true');

      const res2 = await app.inject({
        method: 'POST',
        url: '/v1/classify',
        headers: {
          ...form2.getHeaders(),
          'x-api-key': 'test-key',
        },
        payload: form2,
      });

      expect(res2.statusCode).toBe(200);
      const body2 = JSON.parse(res2.body);
      // On classifier cache hit, we return the cached full response
      expect(body2.cacheHit).toBe(true);
      // Vision stats show no new extraction attempt (from cache)
      expect(body2.visionStats.attempted).toBe(false);
      expect(body2.visionStats.visionExtractions).toBe(0);
    });
  });
});

describe('Invalid Image Handling', () => {
  const config = configSchema.parse({
    nodeEnv: 'test',
    port: 8092,
    publicBaseUrl: 'http://localhost:8092',
    databaseUrl: 'postgresql://user:pass@localhost:5432/db',
    classifier: {
      provider: 'mock',
      apiKeys: 'test-key',
      domainPackPath: 'src/modules/classifier/domain/home-resale.json',
    },
    assistant: {
      provider: 'disabled',
      apiKeys: '',
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

  it('rejects non-image content types', async () => {
    const app = await appPromise;
    const form = new FormData();
    form.append('image', Buffer.from('not an image'), {
      filename: 'test.txt',
      contentType: 'text/plain',
    });

    const res = await app.inject({
      method: 'POST',
      url: '/v1/classify',
      headers: {
        ...form.getHeaders(),
        'x-api-key': 'test-key',
      },
      payload: form,
    });

    expect(res.statusCode).toBe(400);
    const body = JSON.parse(res.body);
    expect(body.error.code).toBe('VALIDATION_ERROR');
    expect(body.error.message).toContain('Unsupported content type');
  });

  it('rejects requests without image', async () => {
    const app = await appPromise;
    const form = new FormData();
    form.append('domainPackId', 'home_resale');

    const res = await app.inject({
      method: 'POST',
      url: '/v1/classify',
      headers: {
        ...form.getHeaders(),
        'x-api-key': 'test-key',
      },
      payload: form,
    });

    expect(res.statusCode).toBe(400);
    const body = JSON.parse(res.body);
    expect(body.error.code).toBe('VALIDATION_ERROR');
    expect(body.error.message).toContain('image file is required');
  });
});
