import { describe, it, expect, afterAll } from 'vitest';
import FormData from 'form-data';
import { buildApp } from '../../app.js';
import { configSchema } from '../../config/index.js';

const tinyPng = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/wwAAgMBAp3N5WkAAAAASUVORK5CYII=',
  'base64'
);

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
  ebay: {
    env: 'sandbox',
    clientId: 'client',
    clientSecret: 'client-secret-minimum-length-please',
    scopes: 'scope',
  },
  sessionSigningSecret: 'x'.repeat(32),
  corsOrigins: 'http://localhost',
});

const appPromise = buildApp(config);

afterAll(async () => {
  const app = await appPromise;
  await app.close();
});

describe('POST /v1/classify', () => {
  it('rejects requests without API key', async () => {
    const app = await appPromise;
    const res = await app.inject({
      method: 'POST',
      url: '/v1/classify',
      headers: {
        'content-type': 'multipart/form-data',
      },
    });

    expect(res.statusCode).toBe(401);
  });

  it('classifies in mock mode and returns normalized response', async () => {
    const app = await appPromise;
    const form = new FormData();
    form.append('image', tinyPng, {
      filename: 'tiny.png',
      contentType: 'image/png',
    });
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

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.requestId).toBeTruthy();
    expect(body.domainPackId).toBe('home_resale');
    expect(body.provider).toBe('mock');
    if (body.domainCategoryId) {
      expect(typeof body.label).toBe('string');
      expect(body.label.length).toBeGreaterThan(0);
    } else {
      expect(body.label === null || body.label === undefined).toBe(true);
    }
  });
});
