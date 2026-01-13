import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { buildApp } from '../../app.js';
import { configSchema } from '../../config/index.js';
import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

const config = configSchema.parse({
  nodeEnv: 'test',
  port: 8080,
  publicBaseUrl: 'http://localhost:8080',
  databaseUrl: process.env.DATABASE_URL || 'postgresql://user:pass@localhost:5432/testdb',
  classifier: {
    provider: 'mock',
    apiKeys: 'test-key',
    domainPackPath: 'src/modules/classifier/domain/home-resale.json',
  },
  assistant: {
    provider: 'mock',
    apiKeys: 'assist-key',
    userRateLimitPerMinute: 5, // Low limit for testing
    userDailyQuota: 10, // Low quota for testing
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
  googleOAuthClientId: 'test-client-id.apps.googleusercontent.com',
  authSessionSecret: 'test-session-secret-32-chars-long',
  authSessionExpirySeconds: 3600,
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

// Test user session token and ID
let testUserId: string;
let testSessionToken: string;

beforeAll(async () => {
  // Create a test user and session
  const testUser = await prisma.user.create({
    data: {
      googleId: 'test-google-id-phase-b',
      email: 'test@example.com',
      displayName: 'Test User',
    },
  });
  testUserId = testUser.id;

  const testSession = await prisma.userSession.create({
    data: {
      userId: testUser.id,
      sessionToken: 'test-session-token-phase-b-xyz',
      expiresAt: new Date(Date.now() + 3600000), // 1 hour from now
    },
  });
  testSessionToken = testSession.sessionToken;
});

afterAll(async () => {
  // Cleanup test data
  await prisma.userSession.deleteMany({ where: { userId: testUserId } });
  await prisma.user.delete({ where: { id: testUserId } });
  await prisma.$disconnect();

  const app = await appPromise;
  await app.close();
});

describe('Phase B: Auth enforcement on /assist/chat', () => {
  it('returns AUTH_REQUIRED (401) when no Authorization header', async () => {
    const app = await appPromise;

    const response = await app.inject({
      method: 'POST',
      url: '/v1/assist/chat',
      headers: {
        'x-api-key': 'assist-key',
        'content-type': 'application/json',
      },
      payload: {
        items: [{ itemId: '123', title: 'Test Item' }],
        message: 'Hello',
      },
    });

    expect(response.statusCode).toBe(401);
    const body = JSON.parse(response.body);
    expect(body.error.code).toBe('AUTH_REQUIRED');
    expect(body.error.message).toContain('Sign in is required');
    expect(body.error.correlationId).toBeDefined();
  });

  it('returns AUTH_INVALID (401) when token is invalid', async () => {
    const app = await appPromise;

    const response = await app.inject({
      method: 'POST',
      url: '/v1/assist/chat',
      headers: {
        'x-api-key': 'assist-key',
        'authorization': 'Bearer invalid-token-xyz',
        'content-type': 'application/json',
      },
      payload: {
        items: [{ itemId: '123', title: 'Test Item' }],
        message: 'Hello',
      },
    });

    expect(response.statusCode).toBe(401);
    const body = JSON.parse(response.body);
    expect(body.error.code).toBe('AUTH_INVALID');
    expect(body.error.message).toContain('invalid or expired');
    expect(body.error.correlationId).toBeDefined();
  });

  it('returns 200 with valid session token', async () => {
    const app = await appPromise;

    const response = await app.inject({
      method: 'POST',
      url: '/v1/assist/chat',
      headers: {
        'x-api-key': 'assist-key',
        'authorization': `Bearer ${testSessionToken}`,
        'content-type': 'application/json',
      },
      payload: {
        items: [{ itemId: '123', title: 'Test Item' }],
        message: 'Hello',
      },
    });

    expect(response.statusCode).toBe(200);
    const body = JSON.parse(response.body);
    expect(body.reply).toBeDefined();
    expect(body.citationsMetadata).toBeDefined();
    expect(body.citationsMetadata.fromCache).toBe('false'); // Mock provider returns string
  });
});

describe('Phase B: Auth enforcement on /assist/warmup', () => {
  it('returns AUTH_REQUIRED (401) when no Authorization header', async () => {
    const app = await appPromise;

    const response = await app.inject({
      method: 'POST',
      url: '/v1/assist/warmup',
      headers: {
        'x-api-key': 'assist-key',
      },
    });

    expect(response.statusCode).toBe(401);
    const body = JSON.parse(response.body);
    expect(body.error.code).toBe('AUTH_REQUIRED');
  });

  it('returns 200 with valid session token', async () => {
    const app = await appPromise;

    const response = await app.inject({
      method: 'POST',
      url: '/v1/assist/warmup',
      headers: {
        'x-api-key': 'assist-key',
        'authorization': `Bearer ${testSessionToken}`,
      },
    });

    expect(response.statusCode).toBe(200);
    const body = JSON.parse(response.body);
    expect(body.status).toBe('ok');
    expect(body.provider).toBe('mock');
  });
});

describe('Phase B: Per-user rate limiting', () => {
  it('returns RATE_LIMITED (429) with resetAt when user exceeds rate limit', async () => {
    const app = await appPromise;

    // Exhaust rate limit (config has userRateLimitPerMinute: 5)
    for (let i = 0; i < 5; i++) {
      await app.inject({
        method: 'POST',
        url: '/v1/assist/chat',
        headers: {
          'x-api-key': 'assist-key',
          'authorization': `Bearer ${testSessionToken}`,
          'content-type': 'application/json',
        },
        payload: {
          items: [{ itemId: '123', title: 'Test Item' }],
          message: `Message ${i}`,
        },
      });
    }

    // Next request should be rate limited
    const response = await app.inject({
      method: 'POST',
      url: '/v1/assist/chat',
      headers: {
        'x-api-key': 'assist-key',
        'authorization': `Bearer ${testSessionToken}`,
        'content-type': 'application/json',
      },
      payload: {
        items: [{ itemId: '123', title: 'Test Item' }],
        message: 'Rate limited',
      },
    });

    expect(response.statusCode).toBe(429);
    const body = JSON.parse(response.body);
    expect(body.error.code).toBe('RATE_LIMITED');
    expect(body.error.message).toContain('Rate limit reached');
    expect(body.error.resetAt).toBeDefined();
    expect(body.error.correlationId).toBeDefined();

    // Validate resetAt is a valid ISO timestamp
    const resetAt = new Date(body.error.resetAt);
    expect(resetAt.getTime()).toBeGreaterThan(Date.now());
  });
});

describe('Phase B: Response schema validation', () => {
  it('successful response includes expected fields', async () => {
    const app = await appPromise;

    const response = await app.inject({
      method: 'POST',
      url: '/v1/assist/chat',
      headers: {
        'x-api-key': 'assist-key',
        'authorization': `Bearer ${testSessionToken}`,
        'content-type': 'application/json',
      },
      payload: {
        items: [{ itemId: '123', title: 'Test Item' }],
        message: 'Test message',
      },
    });

    expect(response.statusCode).toBe(200);
    const body = JSON.parse(response.body);

    // Core response fields
    expect(body.reply).toBeDefined();
    expect(typeof body.reply).toBe('string');
    expect(body.actions).toBeDefined();
    expect(Array.isArray(body.actions)).toBe(true);
    expect(body.citationsMetadata).toBeDefined();
    expect(typeof body.citationsMetadata).toBe('object');

    // Phase A/B metadata
    expect(body.correlationId).toBeDefined();
    expect(body.safety).toBeDefined();

    // Ensure citationsMetadata.fromCache is a boolean or string (mock returns string)
    expect(['boolean', 'string']).toContain(typeof body.citationsMetadata.fromCache);
  });
});
