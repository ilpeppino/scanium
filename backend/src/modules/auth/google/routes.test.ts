import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { buildApp } from '../../../app.js';
import { FastifyInstance } from 'fastify';
import { prisma } from '../../../infra/db/prisma.js';
import { Config } from '../../../config/index.js';
import * as tokenVerifier from './token-verifier.js';

describe('Google Auth Routes', () => {
  let app: FastifyInstance;
  const testUserId = 'routes-test-user';
  const testGoogleSub = 'google-sub-routes-test';
  const testEmail = 'routes-test@example.com';
  const testDisplayName = 'Routes Test User';

  // Mock config with auth enabled
  const mockConfig: Config = {
    nodeEnv: 'test',
    port: 3000,
    publicBaseUrl: 'http://localhost:3000',
    ebay: {
      env: 'sandbox',
      clientId: 'test-client-id',
      clientSecret: 'test-client-secret',
      scopes: ['test-scope'],
      tokenEncryptionKey: 'x'.repeat(32),
      redirectPath: '/auth/ebay/callback',
    },
    sessionSigningSecret: 'x'.repeat(64),
    corsOrigins: ['http://localhost:3000'],
    classifier: {
      provider: 'mock',
      apiKeys: ['test-key'],
      domainPackId: 'test',
      domainPackPath: 'test.json',
      maxUploadBytes: 5242880,
      rateLimitPerMinute: 60,
      concurrencyLimit: 2,
      retainUploads: false,
      mockSeed: 'test',
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
    },
    assistant: {
      provider: 'mock',
      apiKeys: ['test-key'],
      allowEmptyItems: false,
    },
    auth: {
      googleClientId: 'test-google-client-id.apps.googleusercontent.com',
      sessionSecret: 'test-session-secret',
      sessionExpirySeconds: 60,
      refreshTokenExpirySeconds: 120,
    },
  };

  beforeEach(async () => {
    // Clean up test data
    await prisma.session.deleteMany({
      where: { userId: testUserId },
    });
    await prisma.user.deleteMany({
      where: { id: testUserId },
    });

    // Build app with auth enabled
    app = await buildApp(mockConfig);
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
    await prisma.session.deleteMany({
      where: { userId: testUserId },
    });
    await prisma.user.deleteMany({
      where: { id: testUserId },
    });
  });

  describe('POST /v1/auth/google', () => {
    it('should create session for new user', async () => {
      // Mock Google token verifier
      vi.spyOn(tokenVerifier.GoogleOAuth2Verifier.prototype, 'verify').mockResolvedValue({
        sub: testGoogleSub,
        email: testEmail,
        name: testDisplayName,
        picture: 'https://example.com/avatar.jpg',
      });

      const response = await app.inject({
        method: 'POST',
        url: '/v1/auth/google',
        payload: {
          idToken: 'valid-google-id-token',
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.accessToken).toBeDefined();
      expect(body.tokenType).toBe('Bearer');
      expect(body.expiresIn).toBe(60);
      expect(body.refreshToken).toBeDefined();
      expect(body.refreshTokenExpiresIn).toBe(120);
      expect(body.user.email).toBe(testEmail);
      expect(body.user.displayName).toBe(testDisplayName);
      expect(body.correlationId).toBeDefined();

      // Verify user created
      const user = await prisma.user.findUnique({
        where: { googleSub: testGoogleSub },
      });
      expect(user).toBeDefined();
      expect(user!.email).toBe(testEmail);

      // Verify session created
      const sessions = await prisma.session.findMany({
        where: { userId: user!.id },
      });
      expect(sessions).toHaveLength(1);
      expect(sessions[0].refreshTokenHash).toBeDefined();
    });

    it('should update existing user on login', async () => {
      // Create existing user
      await prisma.user.create({
        data: {
          id: testUserId,
          googleSub: testGoogleSub,
          email: 'old@example.com',
          displayName: 'Old Name',
        },
      });

      // Mock Google token verifier with updated info
      vi.spyOn(tokenVerifier.GoogleOAuth2Verifier.prototype, 'verify').mockResolvedValue({
        sub: testGoogleSub,
        email: testEmail,
        name: testDisplayName,
        picture: 'https://example.com/new-avatar.jpg',
      });

      const response = await app.inject({
        method: 'POST',
        url: '/v1/auth/google',
        payload: {
          idToken: 'valid-google-id-token',
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.user.email).toBe(testEmail);
      expect(body.user.displayName).toBe(testDisplayName);

      // Verify user updated
      const user = await prisma.user.findUnique({
        where: { id: testUserId },
      });
      expect(user!.email).toBe(testEmail);
      expect(user!.displayName).toBe(testDisplayName);
      expect(user!.lastLoginAt).toBeDefined();
    });

    it('should reject invalid Google token', async () => {
      vi.spyOn(tokenVerifier.GoogleOAuth2Verifier.prototype, 'verify').mockRejectedValue(
        new Error('Invalid token')
      );

      const response = await app.inject({
        method: 'POST',
        url: '/v1/auth/google',
        payload: {
          idToken: 'invalid-token',
        },
      });

      expect(response.statusCode).toBe(401);
      const body = JSON.parse(response.body);
      expect(body.error.code).toBe('INVALID_TOKEN');
      expect(body.error.correlationId).toBeDefined();
    });

    it('should reject missing idToken', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/auth/google',
        payload: {},
      });

      expect(response.statusCode).toBe(400);
    });
  });

  describe('POST /v1/auth/refresh', () => {
    let accessToken: string;
    let refreshToken: string;

    beforeEach(async () => {
      // Create user and session
      vi.spyOn(tokenVerifier.GoogleOAuth2Verifier.prototype, 'verify').mockResolvedValue({
        sub: testGoogleSub,
        email: testEmail,
        name: testDisplayName,
        picture: 'https://example.com/avatar.jpg',
      });

      const loginResponse = await app.inject({
        method: 'POST',
        url: '/v1/auth/google',
        payload: {
          idToken: 'valid-google-id-token',
        },
      });

      const body = JSON.parse(loginResponse.body);
      accessToken = body.accessToken;
      refreshToken = body.refreshToken;
    });

    it('should refresh access token with valid refresh token', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/auth/refresh',
        payload: {
          refreshToken,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.accessToken).toBeDefined();
      expect(body.accessToken).not.toBe(accessToken);
      expect(body.tokenType).toBe('Bearer');
      expect(body.expiresIn).toBe(60);
      expect(body.correlationId).toBeDefined();
    });

    it('should rotate refresh token', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/auth/refresh',
        payload: {
          refreshToken,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.refreshToken).toBeDefined();
      expect(body.refreshToken).not.toBe(refreshToken);
      expect(body.refreshTokenExpiresIn).toBe(120);
    });

    it('should reject invalid refresh token', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/auth/refresh',
        payload: {
          refreshToken: 'invalid-refresh-token',
        },
      });

      expect(response.statusCode).toBe(401);
      const body = JSON.parse(response.body);
      expect(body.error.code).toBe('AUTH_INVALID');
    });

    it('should reject expired refresh token', async () => {
      // Wait for refresh token to expire (using short expiry in test)
      await new Promise((resolve) => setTimeout(resolve, 2200));

      const response = await app.inject({
        method: 'POST',
        url: '/v1/auth/refresh',
        payload: {
          refreshToken,
        },
      });

      expect(response.statusCode).toBe(401);
      const body = JSON.parse(response.body);
      expect(body.error.code).toBe('AUTH_INVALID');
    });

    it('should reject missing refresh token', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/auth/refresh',
        payload: {},
      });

      expect(response.statusCode).toBe(400);
    });
  });

  describe('POST /v1/auth/logout', () => {
    let accessToken: string;

    beforeEach(async () => {
      // Create user and session
      vi.spyOn(tokenVerifier.GoogleOAuth2Verifier.prototype, 'verify').mockResolvedValue({
        sub: testGoogleSub,
        email: testEmail,
        name: testDisplayName,
        picture: 'https://example.com/avatar.jpg',
      });

      const loginResponse = await app.inject({
        method: 'POST',
        url: '/v1/auth/google',
        payload: {
          idToken: 'valid-google-id-token',
        },
      });

      const body = JSON.parse(loginResponse.body);
      accessToken = body.accessToken;
    });

    it('should revoke session with valid access token', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/auth/logout',
        headers: {
          authorization: `Bearer ${accessToken}`,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.success).toBe(true);
      expect(body.correlationId).toBeDefined();

      // Verify session was deleted
      const user = await prisma.user.findUnique({
        where: { googleSub: testGoogleSub },
      });
      const sessions = await prisma.session.findMany({
        where: { userId: user!.id },
      });
      expect(sessions).toHaveLength(0);
    });

    it('should reject logout without auth token', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/auth/logout',
      });

      expect(response.statusCode).toBe(401);
      const body = JSON.parse(response.body);
      expect(body.error.code).toBe('AUTH_REQUIRED');
    });

    it('should reject logout with invalid auth token', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/auth/logout',
        headers: {
          authorization: 'Bearer invalid-token',
        },
      });

      expect(response.statusCode).toBe(401);
      const body = JSON.parse(response.body);
      expect(body.error.code).toBe('AUTH_INVALID');
    });

    it('should reject logout with expired token', async () => {
      // Wait for token to expire
      await new Promise((resolve) => setTimeout(resolve, 61000));

      const response = await app.inject({
        method: 'POST',
        url: '/v1/auth/logout',
        headers: {
          authorization: `Bearer ${accessToken}`,
        },
      });

      expect(response.statusCode).toBe(401);
      const body = JSON.parse(response.body);
      expect(body.error.code).toBe('AUTH_INVALID');
    });
  });

  describe('GET /v1/auth/me', () => {
    let accessToken: string;

    beforeEach(async () => {
      // Create user and session
      vi.spyOn(tokenVerifier.GoogleOAuth2Verifier.prototype, 'verify').mockResolvedValue({
        sub: testGoogleSub,
        email: testEmail,
        name: testDisplayName,
        picture: 'https://example.com/avatar.jpg',
      });

      const loginResponse = await app.inject({
        method: 'POST',
        url: '/v1/auth/google',
        payload: {
          idToken: 'valid-google-id-token',
        },
      });

      const body = JSON.parse(loginResponse.body);
      accessToken = body.accessToken;
    });

    it('should return user profile with valid token', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/v1/auth/me',
        headers: {
          authorization: `Bearer ${accessToken}`,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.user.email).toBe(testEmail);
      expect(body.user.displayName).toBe(testDisplayName);
      expect(body.user.pictureUrl).toBe('https://example.com/avatar.jpg');
      expect(body.user.id).toBeDefined();
      expect(body.user.createdAt).toBeDefined();
      expect(body.user.lastLoginAt).toBeDefined();
      expect(body.correlationId).toBeDefined();
    });

    it('should reject without auth token', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/v1/auth/me',
      });

      expect(response.statusCode).toBe(401);
      const body = JSON.parse(response.body);
      expect(body.error.code).toBe('AUTH_REQUIRED');
    });

    it('should reject with invalid token', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/v1/auth/me',
        headers: {
          authorization: 'Bearer invalid-token',
        },
      });

      expect(response.statusCode).toBe(401);
      const body = JSON.parse(response.body);
      expect(body.error.code).toBe('AUTH_INVALID');
    });

    it('should reject with expired token', async () => {
      // Wait for token to expire
      await new Promise((resolve) => setTimeout(resolve, 61000));

      const response = await app.inject({
        method: 'GET',
        url: '/v1/auth/me',
        headers: {
          authorization: `Bearer ${accessToken}`,
        },
      });

      expect(response.statusCode).toBe(401);
      const body = JSON.parse(response.body);
      expect(body.error.code).toBe('AUTH_INVALID');
    });
  });

  describe('Observability', () => {
    it('should emit metrics on login', async () => {
      vi.spyOn(tokenVerifier.GoogleOAuth2Verifier.prototype, 'verify').mockResolvedValue({
        sub: testGoogleSub,
        email: testEmail,
        name: testDisplayName,
        picture: 'https://example.com/avatar.jpg',
      });

      await app.inject({
        method: 'POST',
        url: '/v1/auth/google',
        payload: {
          idToken: 'valid-google-id-token',
        },
      });

      // Fetch metrics endpoint
      const metricsResponse = await app.inject({
        method: 'GET',
        url: '/metrics',
      });

      expect(metricsResponse.statusCode).toBe(200);
      expect(metricsResponse.body).toContain('scanium_auth_login_total');
    });

    it('should emit structured logs', async () => {
      const logSpy = vi.spyOn(app.log, 'info');

      vi.spyOn(tokenVerifier.GoogleOAuth2Verifier.prototype, 'verify').mockResolvedValue({
        sub: testGoogleSub,
        email: testEmail,
        name: testDisplayName,
        picture: 'https://example.com/avatar.jpg',
      });

      await app.inject({
        method: 'POST',
        url: '/v1/auth/google',
        payload: {
          idToken: 'valid-google-id-token',
        },
      });

      // Verify structured log was emitted
      expect(logSpy).toHaveBeenCalledWith(
        expect.objectContaining({
          event: 'auth_login_success',
        }),
        'Google auth successful'
      );
    });
  });
});
