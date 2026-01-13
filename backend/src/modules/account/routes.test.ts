import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { FastifyInstance } from 'fastify';
import { buildApp } from '../../app.js';
import { prisma } from '../../infra/db/prisma.js';
import { Config } from '../../config/index.js';
import { createSession } from '../auth/google/session-service.js';

describe('Account Deletion Routes (Phase D)', () => {
  let app: FastifyInstance;
  let testUserId: string;
  let testAccessToken: string;
  let testEmail: string;

  // Mock config with auth enabled
  const mockConfig: Config = {
    nodeEnv: 'test',
    port: 3000,
    publicBaseUrl: 'http://localhost:3000',
    databaseUrl: 'postgresql://test:test@localhost:5432/testdb',
    ebay: {
      env: 'sandbox',
      clientId: 'test-client-id',
      clientSecret: 'test-client-secret-minimum-length',
      scopes: 'test-scope',
      tokenEncryptionKey: 'x'.repeat(32),
      redirectPath: '/auth/ebay/callback',
    },
    sessionSigningSecret: 'x'.repeat(64),
    corsOrigins: ['http://localhost:3000'],
    classifier: {
      provider: 'mock',
      visionFeature: ['LABEL_DETECTION'],
      apiKeys: ['test-key'],
      domainPackId: 'test',
      domainPackPath: 'test.json',
      maxUploadBytes: 5242880,
      rateLimitPerMinute: 60,
      ipRateLimitPerMinute: 60,
      rateLimitWindowSeconds: 60,
      rateLimitBackoffSeconds: 30,
      rateLimitBackoffMaxSeconds: 900,
      concurrentLimit: 2,
      retainUploads: false,
      mockSeed: 'test',
      visionTimeoutMs: 10000,
      visionMaxRetries: 2,
      cacheTtlSeconds: 300,
      cacheMaxEntries: 1000,
      circuitBreakerFailureThreshold: 5,
      circuitBreakerCooldownSeconds: 60,
      circuitBreakerMinimumRequests: 3,
      enableAttributeEnrichment: true,
    },
    vision: {
      enabled: true,
      provider: 'mock',
      enableOcr: true,
      enableLabels: true,
      enableLogos: true,
      enableColors: true,
      ocrMode: 'TEXT_DETECTION',
      timeoutMs: 10000,
      maxRetries: 2,
      cacheTtlSeconds: 3600,
      cacheMaxEntries: 100,
      maxOcrSnippets: 10,
      maxLabelHints: 10,
      maxLogoHints: 5,
      maxColors: 5,
      maxOcrSnippetLength: 100,
      minOcrConfidence: 0.5,
      minLabelConfidence: 0.5,
      minLogoConfidence: 0.5,
    },
    assistant: {
      provider: 'mock',
      apiKeys: ['test-key'],
      allowEmptyItems: false,
      rateLimitPerMinute: 60,
      ipRateLimitPerMinute: 60,
      deviceRateLimitPerMinute: 30,
      userRateLimitPerMinute: 20,
      rateLimitWindowSeconds: 60,
      rateLimitBackoffSeconds: 30,
      rateLimitBackoffMaxSeconds: 900,
      dailyQuota: 200,
      userDailyQuota: 100,
      maxInputChars: 2000,
      maxOutputTokens: 500,
      maxContextItems: 10,
      maxAttributesPerItem: 20,
      providerTimeoutMs: 30000,
      logContent: false,
      circuitBreakerFailureThreshold: 5,
      circuitBreakerCooldownSeconds: 60,
      circuitBreakerMinimumRequests: 3,
      responseCacheTtlSeconds: 3600,
      responseCacheMaxEntries: 1000,
      enableRequestDedup: true,
      stagedResponseTimeoutMs: 60000,
      enableEbayComps: false,
      ebayCompsCacheTtlSeconds: 3600,
      ebayCompsRateLimitPerMinute: 10,
      enableTemplatePacks: true,
      defaultLanguage: 'EN',
      defaultTone: 'NEUTRAL',
      defaultRegion: 'NL',
    },
    pricing: {
      enabled: false,
      timeoutMs: 6000,
      cacheTtlSeconds: 21600,
      catalogPath: 'config/marketplaces/marketplaces.eu.json',
      dailyQuota: 30,
      maxResults: 5,
      openaiModel: 'gpt-4o-mini',
    },
    security: {
      enforceHttps: false,
      enableHsts: false,
      allowInsecureLocalHttp: true,
      apiKeyRotationEnabled: false,
      apiKeyExpirationDays: 90,
      logApiKeyUsage: false,
    },
    admin: {
      enabled: false,
    },
    auth: {
      googleClientId: 'test-google-client-id.apps.googleusercontent.com',
      sessionSecret: 'x'.repeat(32),
      sessionExpirySeconds: 3600,
      refreshTokenExpirySeconds: 7200,
    },
  };

  beforeEach(async () => {
    app = await buildApp(mockConfig);

    // Create a test user
    testEmail = `test-${Date.now()}@example.com`;
    const user = await prisma.user.create({
      data: {
        googleSub: `google-${Date.now()}`,
        email: testEmail,
        displayName: 'Test User',
      },
    });
    testUserId = user.id;

    // Create a session for the test user
    const sessionInfo = await createSession(testUserId, 3600, 7200);
    testAccessToken = sessionInfo.accessToken;
  });

  afterEach(async () => {
    // Clean up test data
    await prisma.session.deleteMany({
      where: { userId: testUserId },
    });
    await prisma.user.deleteMany({
      where: { email: testEmail },
    });
    await app.close();
  });

  describe('POST /v1/account/delete', () => {
    it('should delete authenticated user account', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/account/delete',
        headers: {
          authorization: `Bearer ${testAccessToken}`,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.status).toBe('DELETED');
      expect(body.message).toContain('permanently deleted');

      // Verify user is actually deleted
      const deletedUser = await prisma.user.findUnique({
        where: { id: testUserId },
      });
      expect(deletedUser).toBeNull();

      // Verify sessions are deleted
      const sessions = await prisma.session.findMany({
        where: { userId: testUserId },
      });
      expect(sessions).toHaveLength(0);
    });

    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/account/delete',
      });

      expect(response.statusCode).toBe(401);
      const body = JSON.parse(response.body);
      expect(body.error.code).toBe('AUTH_REQUIRED');
    });

    it('should invalidate token after deletion', async () => {
      // Delete account
      await app.inject({
        method: 'POST',
        url: '/v1/account/delete',
        headers: {
          authorization: `Bearer ${testAccessToken}`,
        },
      });

      // Try to use the same token
      const response = await app.inject({
        method: 'GET',
        url: '/v1/auth/me',
        headers: {
          authorization: `Bearer ${testAccessToken}`,
        },
      });

      expect(response.statusCode).toBe(401);
    });
  });

  describe('GET /v1/account/deletion-status', () => {
    it('should return ACTIVE for existing user', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/v1/account/deletion-status',
        headers: {
          authorization: `Bearer ${testAccessToken}`,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.status).toBe('ACTIVE');
    });

    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/v1/account/deletion-status',
      });

      expect(response.statusCode).toBe(401);
    });
  });

  describe('POST /v1/account/delete-by-email (Web Flow)', () => {
    it('should request account deletion with valid email', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/account/delete-by-email',
        payload: {
          email: testEmail,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.message).toContain('Verification email sent');
      expect(body.verificationUrl).toBeDefined();
      expect(body.expiresAt).toBeDefined();
    });

    it('should reject invalid email', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/account/delete-by-email',
        payload: {
          email: 'invalid-email',
        },
      });

      expect(response.statusCode).toBe(400);
    });

    it('should enforce rate limiting', async () => {
      // Make 3 requests (max allowed)
      for (let i = 0; i < 3; i++) {
        await app.inject({
          method: 'POST',
          url: '/v1/account/delete-by-email',
          payload: { email: testEmail },
        });
      }

      // 4th request should be rate limited
      const response = await app.inject({
        method: 'POST',
        url: '/v1/account/delete-by-email',
        payload: { email: testEmail },
      });

      expect(response.statusCode).toBe(429);
    });
  });

  describe('POST /v1/account/delete/confirm (Web Flow)', () => {
    it('should confirm deletion with valid token', async () => {
      // Request deletion
      const requestResponse = await app.inject({
        method: 'POST',
        url: '/v1/account/delete-by-email',
        payload: { email: testEmail },
      });
      const requestBody = JSON.parse(requestResponse.body);
      const verificationUrl = new URL(requestBody.verificationUrl);
      const token = verificationUrl.searchParams.get('token');

      // Confirm deletion
      const confirmResponse = await app.inject({
        method: 'POST',
        url: '/v1/account/delete/confirm',
        payload: { token },
      });

      expect(confirmResponse.statusCode).toBe(200);
      const confirmBody = JSON.parse(confirmResponse.body);
      expect(confirmBody.success).toBe(true);
      expect(confirmBody.message).toContain('permanently deleted');

      // Verify user is deleted
      const deletedUser = await prisma.user.findUnique({
        where: { id: testUserId },
      });
      expect(deletedUser).toBeNull();
    });

    it('should reject invalid token', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/account/delete/confirm',
        payload: { token: 'invalid-token' },
      });

      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.body);
      expect(body.error.code).toBe('CONFIRMATION_FAILED');
    });

    it('should prevent token reuse', async () => {
      // Request deletion
      const requestResponse = await app.inject({
        method: 'POST',
        url: '/v1/account/delete-by-email',
        payload: { email: testEmail },
      });
      const requestBody = JSON.parse(requestResponse.body);
      const verificationUrl = new URL(requestBody.verificationUrl);
      const token = verificationUrl.searchParams.get('token');

      // Confirm deletion (first time)
      await app.inject({
        method: 'POST',
        url: '/v1/account/delete/confirm',
        payload: { token },
      });

      // Try to use token again
      const retryResponse = await app.inject({
        method: 'POST',
        url: '/v1/account/delete/confirm',
        payload: { token },
      });

      expect(retryResponse.statusCode).toBe(400);
    });
  });

  describe('Cascade Deletion', () => {
    it('should delete related data (sessions, listings, connections)', async () => {
      // Create related data
      await prisma.listing.create({
        data: {
          userId: testUserId,
          marketplace: 'ebay',
          status: 'draft',
        },
      });

      await prisma.ebayConnection.create({
        data: {
          userId: testUserId,
          environment: 'sandbox',
          accessToken: 'encrypted-token',
          refreshToken: 'encrypted-refresh',
          expiresAt: new Date(Date.now() + 3600000),
          scopes: 'test',
        },
      });

      // Delete account
      await app.inject({
        method: 'POST',
        url: '/v1/account/delete',
        headers: {
          authorization: `Bearer ${testAccessToken}`,
        },
      });

      // Verify all related data is deleted
      const listings = await prisma.listing.findMany({
        where: { userId: testUserId },
      });
      expect(listings).toHaveLength(0);

      const connections = await prisma.ebayConnection.findMany({
        where: { userId: testUserId },
      });
      expect(connections).toHaveLength(0);

      const sessions = await prisma.session.findMany({
        where: { userId: testUserId },
      });
      expect(sessions).toHaveLength(0);
    });
  });
});
