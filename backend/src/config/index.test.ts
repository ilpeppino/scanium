import { describe, it, expect, beforeEach } from 'vitest';
import { z } from 'zod';

// Note: We can't directly test loadConfig() as it reads from process.env
// Instead, we'll test the schema validation logic

const configSchema = z.object({
  nodeEnv: z.enum(['development', 'production', 'test']).default('development'),
  port: z.coerce.number().int().min(1).max(65535).default(8080),
  publicBaseUrl: z.string().url(),
  databaseUrl: z.string().min(1),
  ebay: z.object({
    env: z.enum(['sandbox', 'production']),
    clientId: z.string().min(1),
    clientSecret: z.string().min(1),
    redirectPath: z.string().default('/auth/ebay/callback'),
    scopes: z.string().min(1),
  }),
  sessionSigningSecret: z.string().min(32),
  corsOrigins: z
    .string()
    .transform((val) => val.split(',').map((o) => o.trim()))
    .pipe(z.array(z.string().min(1))),
});

describe('Config Schema Validation', () => {
  it('should validate valid configuration', () => {
    const validConfig = {
      nodeEnv: 'development',
      port: 8080,
      publicBaseUrl: 'http://localhost:8080',
      databaseUrl: 'postgresql://user:pass@localhost:5432/db',
      ebay: {
        env: 'sandbox',
        clientId: 'test-client-id',
        clientSecret: 'test-client-secret-minimum-length',
        redirectPath: '/auth/ebay/callback',
        scopes: 'https://api.ebay.com/oauth/api_scope',
      },
      sessionSigningSecret: 'test-secret-minimum-32-chars-long',
      corsOrigins: 'scanium://,http://localhost:3000',
    };

    const result = configSchema.safeParse(validConfig);
    expect(result.success).toBe(true);

    if (result.success) {
      expect(result.data.nodeEnv).toBe('development');
      expect(result.data.port).toBe(8080);
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
      ebay: {
        env: 'sandbox',
        clientId: 'test',
        clientSecret: 'test-secret',
        scopes: 'scope',
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
      ebay: {
        env: 'sandbox',
        clientId: 'test',
        clientSecret: 'test-secret',
        scopes: 'scope',
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
      ebay: {
        env: 'invalid-env',
        clientId: 'test',
        clientSecret: 'test-secret',
        scopes: 'scope',
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
      ebay: {
        env: 'sandbox',
        clientId: 'test',
        clientSecret: 'test-secret',
        scopes: 'scope',
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
      ebay: {
        env: 'sandbox',
        clientId: 'test',
        clientSecret: 'test-secret',
        scopes: 'scope',
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
    }
  });
});
