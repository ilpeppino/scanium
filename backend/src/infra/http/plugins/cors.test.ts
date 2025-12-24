import { describe, it, expect } from 'vitest';
import { buildApp } from '../../../app.js';
import { Config } from '../../../config/index.js';

describe('CORS Plugin', () => {
  const createTestConfig = (overrides: Partial<Config> = {}): Config => ({
    nodeEnv: 'production',
    port: 8080,
    publicBaseUrl: 'https://api.example.com',
    databaseUrl: 'postgresql://test:test@localhost:5432/test',
    classifier: {
      provider: 'mock',
      visionFeature: 'LABEL_DETECTION',
      maxUploadBytes: 5242880,
      rateLimitPerMinute: 60,
      ipRateLimitPerMinute: 60,
      rateLimitWindowSeconds: 60,
      rateLimitBackoffSeconds: 30,
      rateLimitBackoffMaxSeconds: 900,
      rateLimitRedisUrl: undefined,
      concurrentLimit: 2,
      apiKeys: ['test-key-1'],
      domainPackId: 'home_resale',
      domainPackPath: 'src/modules/classifier/domain/home-resale.json',
      retainUploads: false,
      mockSeed: 'test',
      visionTimeoutMs: 10000,
      visionMaxRetries: 2,
    },
    assistant: {
      provider: 'disabled',
      apiKeys: [],
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
      logApiKeyUsage: true,
    },
    corsOrigins: ['https://allowed.example.com'],
    ...overrides,
  });

  it('allows configured origins and adds CORS headers', async () => {
    const app = await buildApp(createTestConfig());

    const response = await app.inject({
      method: 'OPTIONS',
      url: '/health',
      headers: {
        origin: 'https://allowed.example.com',
        'access-control-request-method': 'GET',
      },
    });

    expect(response.statusCode).toBe(204);
    expect(response.headers['access-control-allow-origin']).toBe(
      'https://allowed.example.com'
    );

    await app.close();
  });

  it('rejects origins not in the whitelist', async () => {
    const app = await buildApp(createTestConfig());

    const response = await app.inject({
      method: 'OPTIONS',
      url: '/health',
      headers: {
        origin: 'https://malicious.example.com',
        'access-control-request-method': 'GET',
      },
    });

    expect(response.statusCode).toBe(404);
    expect(response.headers['access-control-allow-origin']).toBeUndefined();

    await app.close();
  });
});
