import { describe, it, expect, afterAll } from 'vitest';
import { buildApp } from '../../app.js';
import { configSchema } from '../../config/index.js';

const config = configSchema.parse({
  nodeEnv: 'test',
  port: 8080,
  publicBaseUrl: 'http://localhost:8080',
  databaseUrl: 'postgresql://user:pass@localhost:5432/db',
  classifier: {
    provider: 'mock',
    apiKeys: 'test-api-key-12345',
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

describe('GET /v1/admin/debug/auth', () => {
  it('returns 401 without API key', async () => {
    const app = await appPromise;
    const res = await app.inject({
      method: 'GET',
      url: '/v1/admin/debug/auth',
    });

    expect(res.statusCode).toBe(401);
    const body = JSON.parse(res.body);
    expect(body.error.code).toBe('UNAUTHORIZED');
  });

  it('returns 401 with invalid API key', async () => {
    const app = await appPromise;
    const res = await app.inject({
      method: 'GET',
      url: '/v1/admin/debug/auth',
      headers: {
        'x-api-key': 'invalid-key',
      },
    });

    expect(res.statusCode).toBe(401);
    const body = JSON.parse(res.body);
    expect(body.error.code).toBe('UNAUTHORIZED');
  });

  it('returns 200 with valid API key', async () => {
    const app = await appPromise;
    const res = await app.inject({
      method: 'GET',
      url: '/v1/admin/debug/auth',
      headers: {
        'x-api-key': 'test-api-key-12345',
        'user-agent': 'test-agent/1.0',
      },
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.hasApiKeyHeader).toBe(true);
    expect(body.apiKeyPrefix).toBe('test-a'); // First 6 chars
    expect(body.userAgent).toBe('test-agent/1.0');
    expect(body.clientIp).toBeDefined();
  });

  it('does not expose full API key in response', async () => {
    const app = await appPromise;
    const fullApiKey = 'test-api-key-12345';
    const res = await app.inject({
      method: 'GET',
      url: '/v1/admin/debug/auth',
      headers: {
        'x-api-key': fullApiKey,
      },
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    const bodyString = JSON.stringify(body);

    // Ensure full API key is NOT in response
    expect(bodyString).not.toContain(fullApiKey);
    // Only prefix should be present
    expect(body.apiKeyPrefix).toBe(fullApiKey.substring(0, 6));
    expect(body.apiKeyPrefix.length).toBe(6);
  });

  it('masks IPv4 address last octet', async () => {
    const app = await appPromise;
    const res = await app.inject({
      method: 'GET',
      url: '/v1/admin/debug/auth',
      headers: {
        'x-api-key': 'test-api-key-12345',
        'x-forwarded-for': '192.168.1.100',
      },
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.clientIp).toBe('192.168.1.xxx');
  });

  it('includes Cloudflare headers when present', async () => {
    const app = await appPromise;
    const res = await app.inject({
      method: 'GET',
      url: '/v1/admin/debug/auth',
      headers: {
        'x-api-key': 'test-api-key-12345',
        'cf-ray': '1234567890abcdef-SJC',
        'cf-connecting-ip': '203.0.113.50',
      },
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.cfRay).toBe('1234567890abcdef-SJC');
    expect(body.clientIp).toBe('203.0.113.xxx'); // Masked
  });
});
