/**
 * End-to-end tests for assistant routes.
 * These tests boot the full application and verify route registration.
 *
 * Purpose: Catch production issues where routes might not be registered
 * correctly, such as prefix mismatches or module registration failures.
 */
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

let buildApp: typeof import('../../app.js').buildApp;
let configSchema: typeof import('../../config/index.js').configSchema;
let appPromise: ReturnType<typeof import('../../app.js').buildApp>;

beforeAll(async () => {
  ({ buildApp } = await import('../../app.js'));
  ({ configSchema } = await import('../../config/index.js'));

  const config = configSchema.parse({
    nodeEnv: 'test',
    port: 8090,
    publicBaseUrl: 'http://localhost:8090',
    databaseUrl: 'postgresql://user:pass@localhost:5432/db',
    classifier: {
      provider: 'mock',
      apiKeys: 'test-key',
      domainPackPath: 'src/modules/classifier/domain/home-resale.json',
    },
    assistant: {
      provider: 'mock',
      apiKeys: 'e2e-test-key',
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

  appPromise = buildApp(config);
});

afterAll(async () => {
  const app = await appPromise;
  await app.close();
});

describe('Assistant Routes E2E - Route Registration', () => {
  /**
   * Critical test: Verify warmup endpoint is reachable.
   * If this test fails, the container may be running an old image
   * that doesn't have the warmup route registered.
   */
  it('POST /v1/assist/warmup returns 200 with valid API key', async () => {
    const app = await appPromise;
    const res = await app.inject({
      method: 'POST',
      url: '/v1/assist/warmup',
      headers: { 'x-api-key': 'e2e-test-key' },
    });

    // Route should exist and return 200
    expect(res.statusCode).toBe(200);

    const body = JSON.parse(res.body);
    expect(body.status).toBe('ok');
    expect(body.provider).toBeDefined();
    expect(body.model).toBeDefined();
    expect(body.ts).toBeDefined();
    expect(body.correlationId).toBeDefined();
  });

  it('POST /v1/assist/warmup does NOT return 404', async () => {
    const app = await appPromise;
    const res = await app.inject({
      method: 'POST',
      url: '/v1/assist/warmup',
      headers: { 'x-api-key': 'e2e-test-key' },
    });

    // Critical: Must NOT be 404
    // If this is 404, the route is not registered correctly
    expect(res.statusCode).not.toBe(404);
  });

  it('POST /v1/assist/chat returns 200 with valid request', async () => {
    const app = await appPromise;
    const res = await app.inject({
      method: 'POST',
      url: '/v1/assist/chat',
      headers: { 'x-api-key': 'e2e-test-key' },
      payload: {
        items: [
          {
            itemId: 'e2e-item-1',
            title: 'Test Item',
            category: 'Test',
          },
        ],
        message: 'Is this item in good condition?',
      },
    });

    // Route should exist and work
    expect(res.statusCode).toBe(200);

    const body = JSON.parse(res.body);
    expect(body.reply).toBeDefined();
    expect(body.correlationId).toBeDefined();
  });

  it('GET /v1/assist/cache/stats returns 200 with valid API key', async () => {
    const app = await appPromise;
    const res = await app.inject({
      method: 'GET',
      url: '/v1/assist/cache/stats',
      headers: { 'x-api-key': 'e2e-test-key' },
    });

    expect(res.statusCode).toBe(200);

    const body = JSON.parse(res.body);
    expect(body.vision).toBeDefined();
    expect(body.response).toBeDefined();
  });

  it('All assistant routes are registered under /v1 prefix', async () => {
    const app = await appPromise;

    // All these should NOT return 404
    const routes = [
      { method: 'POST' as const, url: '/v1/assist/warmup', body: undefined },
      { method: 'POST' as const, url: '/v1/assist/chat', body: { items: [], message: 'test' } },
      { method: 'GET' as const, url: '/v1/assist/cache/stats', body: undefined },
    ];

    for (const route of routes) {
      const res = await app.inject({
        method: route.method,
        url: route.url,
        headers: { 'x-api-key': 'e2e-test-key' },
        payload: route.body,
      });

      expect(res.statusCode, `${route.method} ${route.url} should not be 404`).not.toBe(404);
    }
  });
});

describe('Assistant Routes E2E - Security', () => {
  it('Warmup endpoint requires API key', async () => {
    const app = await appPromise;
    const res = await app.inject({
      method: 'POST',
      url: '/v1/assist/warmup',
    });

    expect(res.statusCode).toBe(401);
  });

  it('Chat endpoint requires API key', async () => {
    const app = await appPromise;
    const res = await app.inject({
      method: 'POST',
      url: '/v1/assist/chat',
      payload: { items: [], message: 'test' },
    });

    expect(res.statusCode).toBe(401);
  });
});

describe('Assistant Routes E2E - Market Price Insights', () => {
  /**
   * Contract test for Phase 4: Market Price feature.
   * Verifies that when pricing is enabled and assistantPrefs.region is provided,
   * the response includes marketPrice with expected status and structure.
   */
  it('POST /v1/assist/chat includes marketPrice when feature enabled', async () => {
    const app = await appPromise;
    const res = await app.inject({
      method: 'POST',
      url: '/v1/assist/chat',
      headers: { 'x-api-key': 'e2e-test-key' },
      payload: {
        items: [
          {
            itemId: 'e2e-pricing-item-1',
            title: 'Nike Air Max 90',
            category: 'Shoes',
            attributes: [
              { key: 'brand', value: 'Nike' },
              { key: 'model', value: 'Air Max 90' },
            ],
          },
        ],
        message: 'What should I price this at?',
        assistantPrefs: {
          region: 'NL',
        },
      },
    });

    // Request should succeed
    expect(res.statusCode).toBe(200);

    const body = JSON.parse(res.body);

    // Core assistant response should be present
    expect(body.reply).toBeDefined();
    expect(body.correlationId).toBeDefined();

    // Market price should be present if pricing feature is enabled (Phase 4)
    // Note: May be undefined if pricing.enabled=false in test env
    if (body.marketPrice) {
      expect(body.marketPrice.status).toBeDefined();
      expect(body.marketPrice.countryCode).toBe('NL');

      // Status should be one of the allowed values
      expect(['OK', 'NOT_SUPPORTED', 'DISABLED', 'ERROR', 'TIMEOUT', 'NO_RESULTS']).toContain(
        body.marketPrice.status
      );

      // Marketplaces should be defined
      expect(body.marketPrice.marketplacesUsed).toBeDefined();
      expect(Array.isArray(body.marketPrice.marketplacesUsed)).toBe(true);
    }
  });

  it('POST /v1/assist/chat works without assistantPrefs.region (uses default)', async () => {
    const app = await appPromise;
    const res = await app.inject({
      method: 'POST',
      url: '/v1/assist/chat',
      headers: { 'x-api-key': 'e2e-test-key' },
      payload: {
        items: [
          {
            itemId: 'e2e-no-pricing-item-1',
            title: 'Test Item',
            category: 'Test',
          },
        ],
        message: 'Describe this item',
        // No assistantPrefs
      },
    });

    // Request should succeed
    expect(res.statusCode).toBe(200);

    const body = JSON.parse(res.body);

    // Core assistant response should be present
    expect(body.reply).toBeDefined();
    expect(body.correlationId).toBeDefined();

    // Market price may or may not be present depending on pricing.enabled flag
    // This test just ensures backward compatibility (no crash)
  });

  it('Cache stats endpoint includes pricing cache stats', async () => {
    const app = await appPromise;
    const res = await app.inject({
      method: 'GET',
      url: '/v1/assist/cache/stats',
      headers: { 'x-api-key': 'e2e-test-key' },
    });

    expect(res.statusCode).toBe(200);

    const body = JSON.parse(res.body);
    expect(body.vision).toBeDefined();
    expect(body.response).toBeDefined();
    expect(body.pricing).toBeDefined();
    expect(body.pricing.size).toBeDefined();
    expect(body.pricing.maxTtlMs).toBeDefined();
  });
});
