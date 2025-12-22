import { afterAll, describe, expect, it } from 'vitest';
import { buildApp } from '../../app.js';
import { configSchema } from '../../config/index.js';

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
    expect(typeof body.content).toBe('string');
    expect(body.actions?.length).toBeGreaterThan(0);
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
    expect(body.content.toLowerCase()).toContain('scraping');
    expect(body.actions?.length || 0).toBe(0);
  });
});
