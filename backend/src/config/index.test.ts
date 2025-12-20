import { describe, it, expect, beforeEach } from 'vitest';
import { configSchema } from './index.js';

describe('Config Schema Validation', () => {
  it('should validate valid configuration', () => {
    const validConfig = {
      nodeEnv: 'development',
      port: 8080,
      publicBaseUrl: 'http://localhost:8080',
      databaseUrl: 'postgresql://user:pass@localhost:5432/db',
      classifier: {
        provider: 'mock',
        visionFeature: 'LABEL_DETECTION',
        apiKeys: 'dev-key',
      },
      googleCredentialsPath: '/tmp/creds.json',
      ebay: {
        env: 'sandbox',
        clientId: 'test-client-id',
        clientSecret: 'test-client-secret-minimum-length',
        redirectPath: '/auth/ebay/callback',
        scopes: 'https://api.ebay.com/oauth/api_scope',
        tokenEncryptionKey: 'test-token-encryption-key-minimum-length-32',
      },
      sessionSigningSecret: 'test-secret-minimum-32-chars-long',
      corsOrigins: 'scanium://,http://localhost:3000',
    };

    const result = configSchema.safeParse(validConfig);
    expect(result.success).toBe(true);

    if (result.success) {
      expect(result.data.nodeEnv).toBe('development');
      expect(result.data.port).toBe(8080);
      expect(result.data.classifier.apiKeys).toEqual(['dev-key']);
      expect(result.data.corsOrigins).toEqual([
        'scanium://',
        'http://localhost:3000',
      ]);
    }
  });

  it('should reject invalid public base URL', () => {
    const invalidConfig = {
      publicBaseUrl: 'not-a-url',
      databaseUrl: 'postgresql://user:pass@localhost:5432/db',
      classifier: {
        provider: 'mock',
      },
      ebay: {
        env: 'sandbox',
        clientId: 'test',
        clientSecret: 'test-secret',
        scopes: 'scope',
        tokenEncryptionKey: 'test-token-encryption-key-minimum-length-32',
      },
      sessionSigningSecret: 'test-secret-minimum-32-chars-long',
      corsOrigins: 'http://localhost',
    };

    const result = configSchema.safeParse(invalidConfig);
    expect(result.success).toBe(false);
  });

  it('should reject session secret shorter than 32 chars', () => {
    const invalidConfig = {
      publicBaseUrl: 'http://localhost:8080',
      databaseUrl: 'postgresql://user:pass@localhost:5432/db',
      classifier: {
        provider: 'mock',
      },
      ebay: {
        env: 'sandbox',
        clientId: 'test',
        clientSecret: 'test-secret',
        scopes: 'scope',
        tokenEncryptionKey: 'test-token-encryption-key-minimum-length-32',
      },
      sessionSigningSecret: 'too-short',
      corsOrigins: 'http://localhost',
    };

    const result = configSchema.safeParse(invalidConfig);
    expect(result.success).toBe(false);

    if (!result.success) {
      const errors = result.error.errors.map((e) => e.path.join('.'));
      expect(errors).toContain('sessionSigningSecret');
    }
  });

  it('should reject invalid eBay environment', () => {
    const invalidConfig = {
      publicBaseUrl: 'http://localhost:8080',
      databaseUrl: 'postgresql://user:pass@localhost:5432/db',
      classifier: {
        provider: 'mock',
      },
      ebay: {
        env: 'invalid-env',
        clientId: 'test',
        clientSecret: 'test-secret',
        scopes: 'scope',
        tokenEncryptionKey: 'test-token-encryption-key-minimum-length-32',
      },
      sessionSigningSecret: 'test-secret-minimum-32-chars-long',
      corsOrigins: 'http://localhost',
    };

    const result = configSchema.safeParse(invalidConfig);
    expect(result.success).toBe(false);

    if (!result.success) {
      const errors = result.error.errors.map((e) => e.path.join('.'));
      expect(errors).toContain('ebay.env');
    }
  });

  it('should parse CORS origins correctly', () => {
    const config = {
      publicBaseUrl: 'http://localhost:8080',
      databaseUrl: 'postgresql://user:pass@localhost:5432/db',
      classifier: {
        provider: 'mock',
      },
      ebay: {
        env: 'sandbox',
        clientId: 'test',
        clientSecret: 'test-secret',
        scopes: 'scope',
        tokenEncryptionKey: 'test-token-encryption-key-minimum-length-32',
      },
      sessionSigningSecret: 'test-secret-minimum-32-chars-long',
      corsOrigins: 'scanium://, http://localhost:3000 , https://app.com',
    };

    const result = configSchema.safeParse(config);
    expect(result.success).toBe(true);

    if (result.success) {
      expect(result.data.corsOrigins).toEqual([
        'scanium://',
        'http://localhost:3000',
        'https://app.com',
      ]);
    }
  });

  it('should apply defaults for optional fields', () => {
    const minimalConfig = {
      publicBaseUrl: 'http://localhost:8080',
      databaseUrl: 'postgresql://user:pass@localhost:5432/db',
      classifier: {
        provider: 'mock',
      },
      ebay: {
        env: 'sandbox',
        clientId: 'test',
        clientSecret: 'test-secret',
        scopes: 'scope',
        tokenEncryptionKey: 'test-token-encryption-key-minimum-length-32',
      },
      sessionSigningSecret: 'test-secret-minimum-32-chars-long',
      corsOrigins: 'http://localhost',
    };

    const result = configSchema.safeParse(minimalConfig);
    expect(result.success).toBe(true);

    if (result.success) {
      expect(result.data.nodeEnv).toBe('development');
      expect(result.data.port).toBe(8080);
      expect(result.data.ebay.redirectPath).toBe('/auth/ebay/callback');
      expect(result.data.classifier.domainPackId).toBe('home_resale');
    }
  });
});
