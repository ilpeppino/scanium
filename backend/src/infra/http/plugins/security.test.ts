import { describe, it, expect } from 'vitest';
import { buildApp } from '../../../app.js';
import { Config } from '../../../config/index.js';

describe('Security Plugin', () => {
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
      concurrentLimit: 2,
      apiKeys: ['test-key-1'],
      domainPackId: 'home_resale',
      domainPackPath: 'src/modules/classifier/domain/home-resale.json',
      retainUploads: false,
      mockSeed: 'test',
      visionTimeoutMs: 10000,
      visionMaxRetries: 2,
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
    corsOrigins: ['https://example.com'],
    ...overrides,
  });

  describe('Security Headers', () => {
    it('should add HSTS header in production', async () => {
      const config = createTestConfig({ nodeEnv: 'production' });
      const app = await buildApp(config);

      const response = await app.inject({
        method: 'GET',
        url: '/health',
      });

      expect(response.headers['strict-transport-security']).toBe(
        'max-age=31536000; includeSubDomains; preload'
      );

      await app.close();
    });

    it('should add Content-Security-Policy header', async () => {
      const config = createTestConfig();
      const app = await buildApp(config);

      const response = await app.inject({
        method: 'GET',
        url: '/health',
      });

      expect(response.headers['content-security-policy']).toContain(
        "default-src 'self'"
      );

      await app.close();
    });

    it('should add X-Content-Type-Options header', async () => {
      const config = createTestConfig();
      const app = await buildApp(config);

      const response = await app.inject({
        method: 'GET',
        url: '/health',
      });

      expect(response.headers['x-content-type-options']).toBe('nosniff');

      await app.close();
    });

    it('should add X-Frame-Options header', async () => {
      const config = createTestConfig();
      const app = await buildApp(config);

      const response = await app.inject({
        method: 'GET',
        url: '/health',
      });

      expect(response.headers['x-frame-options']).toBe('DENY');

      await app.close();
    });

    it('should add X-XSS-Protection header', async () => {
      const config = createTestConfig();
      const app = await buildApp(config);

      const response = await app.inject({
        method: 'GET',
        url: '/health',
      });

      expect(response.headers['x-xss-protection']).toBe('1; mode=block');

      await app.close();
    });

    it('should add Referrer-Policy header', async () => {
      const config = createTestConfig();
      const app = await buildApp(config);

      const response = await app.inject({
        method: 'GET',
        url: '/health',
      });

      expect(response.headers['referrer-policy']).toBe(
        'strict-origin-when-cross-origin'
      );

      await app.close();
    });

    it('should add Permissions-Policy header', async () => {
      const config = createTestConfig();
      const app = await buildApp(config);

      const response = await app.inject({
        method: 'GET',
        url: '/health',
      });

      expect(response.headers['permissions-policy']).toContain('camera=()');

      await app.close();
    });
  });

  describe('HTTPS Enforcement', () => {
    it('should reject HTTP requests when enforceHttps is enabled', async () => {
      const config = createTestConfig({
        nodeEnv: 'production',
        security: {
          enforceHttps: true,
          enableHsts: true,
          apiKeyRotationEnabled: true,
          apiKeyExpirationDays: 90,
          logApiKeyUsage: true,
        },
      });
      const app = await buildApp(config);

      const response = await app.inject({
        method: 'GET',
        url: '/health',
        headers: {
          'x-forwarded-proto': 'http',
        },
      });

      expect(response.statusCode).toBe(403);
      expect(response.json().error.code).toBe('HTTPS_REQUIRED');

      await app.close();
    });

    it('should allow HTTPS requests', async () => {
      const config = createTestConfig({
        nodeEnv: 'production',
        security: {
          enforceHttps: true,
          enableHsts: true,
          apiKeyRotationEnabled: true,
          apiKeyExpirationDays: 90,
          logApiKeyUsage: true,
        },
      });
      const app = await buildApp(config);

      const response = await app.inject({
        method: 'GET',
        url: '/health',
        headers: {
          'x-forwarded-proto': 'https',
        },
      });

      expect(response.statusCode).toBe(200);

      await app.close();
    });

    it('should not enforce HTTPS when disabled', async () => {
      const config = createTestConfig({
        nodeEnv: 'production',
        security: {
          enforceHttps: false,
          enableHsts: true,
          apiKeyRotationEnabled: true,
          apiKeyExpirationDays: 90,
          logApiKeyUsage: true,
        },
      });
      const app = await buildApp(config);

      const response = await app.inject({
        method: 'GET',
        url: '/health',
        headers: {
          'x-forwarded-proto': 'http',
        },
      });

      expect(response.statusCode).toBe(200);

      await app.close();
    });

    it('should not enforce HTTPS in development', async () => {
      const config = createTestConfig({
        nodeEnv: 'development',
        security: {
          enforceHttps: true,
          enableHsts: true,
          apiKeyRotationEnabled: true,
          apiKeyExpirationDays: 90,
          logApiKeyUsage: true,
        },
      });
      const app = await buildApp(config);

      const response = await app.inject({
        method: 'GET',
        url: '/health',
        headers: {
          'x-forwarded-proto': 'http',
        },
      });

      expect(response.statusCode).toBe(200);

      await app.close();
    });
  });
});
