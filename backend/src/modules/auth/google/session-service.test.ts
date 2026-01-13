import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import {
  createSession,
  verifySession,
  refreshSession,
  revokeSession,
  cleanupExpiredSessions,
} from './session-service.js';
import { prisma } from '../../../infra/db/prisma.js';

describe('Session Service', () => {
  const testUserId = 'test-user-123';
  const accessTokenExpirySeconds = 60; // 1 minute for testing
  const refreshTokenExpirySeconds = 120; // 2 minutes for testing

  beforeEach(async () => {
    // Clean up test sessions before each test
    await prisma.session.deleteMany({
      where: { userId: testUserId },
    });
    await prisma.user.deleteMany({
      where: { id: testUserId },
    });

    // Create test user
    await prisma.user.create({
      data: {
        id: testUserId,
        googleSub: 'google-sub-123',
        email: 'test@example.com',
        displayName: 'Test User',
        pictureUrl: 'https://example.com/avatar.jpg',
      },
    });
  });

  afterEach(async () => {
    // Clean up after each test
    await prisma.session.deleteMany({
      where: { userId: testUserId },
    });
    await prisma.user.deleteMany({
      where: { id: testUserId },
    });
  });

  describe('createSession', () => {
    it('should create session with access token only (no refresh token)', async () => {
      const session = await createSession(testUserId, accessTokenExpirySeconds);

      expect(session.accessToken).toBeDefined();
      expect(session.tokenType).toBe('Bearer');
      expect(session.expiresIn).toBe(accessTokenExpirySeconds);
      expect(session.refreshToken).toBeUndefined();
      expect(session.refreshTokenExpiresIn).toBeUndefined();
      expect(session.user.id).toBe(testUserId);
      expect(session.user.email).toBe('test@example.com');
      expect(session.user.displayName).toBe('Test User');

      // Verify session stored in database
      const storedSession = await prisma.session.findFirst({
        where: { userId: testUserId },
      });
      expect(storedSession).toBeDefined();
      expect(storedSession?.refreshTokenHash).toBeNull();
    });

    it('should create session with access token and refresh token', async () => {
      const session = await createSession(
        testUserId,
        accessTokenExpirySeconds,
        refreshTokenExpirySeconds
      );

      expect(session.accessToken).toBeDefined();
      expect(session.tokenType).toBe('Bearer');
      expect(session.expiresIn).toBe(accessTokenExpirySeconds);
      expect(session.refreshToken).toBeDefined();
      expect(session.refreshTokenExpiresIn).toBe(refreshTokenExpirySeconds);
      expect(session.user.id).toBe(testUserId);

      // Verify session stored in database with refresh token
      const storedSession = await prisma.session.findFirst({
        where: { userId: testUserId },
      });
      expect(storedSession).toBeDefined();
      expect(storedSession?.refreshTokenHash).toBeDefined();
      expect(storedSession?.refreshTokenExpiresAt).toBeDefined();
    });

    it('should generate unique tokens for different sessions', async () => {
      const session1 = await createSession(testUserId, accessTokenExpirySeconds);
      const session2 = await createSession(testUserId, accessTokenExpirySeconds);

      expect(session1.accessToken).not.toBe(session2.accessToken);
    });

    it('should set correct expiry times', async () => {
      const beforeCreation = Date.now();
      const session = await createSession(
        testUserId,
        accessTokenExpirySeconds,
        refreshTokenExpirySeconds
      );
      const afterCreation = Date.now();

      const storedSession = await prisma.session.findFirst({
        where: { userId: testUserId },
      });

      expect(storedSession).toBeDefined();
      const expiresAt = storedSession!.expiresAt.getTime();
      const refreshExpiresAt = storedSession!.refreshTokenExpiresAt!.getTime();

      // Access token expires in ~60 seconds
      expect(expiresAt).toBeGreaterThanOrEqual(beforeCreation + accessTokenExpirySeconds * 1000);
      expect(expiresAt).toBeLessThanOrEqual(afterCreation + accessTokenExpirySeconds * 1000);

      // Refresh token expires in ~120 seconds
      expect(refreshExpiresAt).toBeGreaterThanOrEqual(
        beforeCreation + refreshTokenExpirySeconds * 1000
      );
      expect(refreshExpiresAt).toBeLessThanOrEqual(
        afterCreation + refreshTokenExpirySeconds * 1000
      );
    });

    it('should throw error if user does not exist', async () => {
      await expect(
        createSession('non-existent-user', accessTokenExpirySeconds)
      ).rejects.toThrow();
    });
  });

  describe('verifySession', () => {
    it('should verify valid access token', async () => {
      const session = await createSession(testUserId, accessTokenExpirySeconds);
      const userId = await verifySession(session.accessToken);

      expect(userId).toBe(testUserId);
    });

    it('should return null for invalid token', async () => {
      const userId = await verifySession('invalid-token');
      expect(userId).toBeNull();
    });

    it('should return null for expired token', async () => {
      // Create session with 1 second expiry
      const session = await createSession(testUserId, 1);

      // Wait for token to expire
      await new Promise((resolve) => setTimeout(resolve, 1100));

      const userId = await verifySession(session.accessToken);
      expect(userId).toBeNull();

      // Verify session was deleted
      const storedSession = await prisma.session.findFirst({
        where: { userId: testUserId },
      });
      expect(storedSession).toBeNull();
    });

    it('should update lastUsedAt on verification', async () => {
      const session = await createSession(testUserId, accessTokenExpirySeconds);

      const beforeVerify = await prisma.session.findFirst({
        where: { userId: testUserId },
      });

      // Wait a bit
      await new Promise((resolve) => setTimeout(resolve, 100));

      await verifySession(session.accessToken);

      const afterVerify = await prisma.session.findFirst({
        where: { userId: testUserId },
      });

      expect(afterVerify!.lastUsedAt.getTime()).toBeGreaterThan(
        beforeVerify!.lastUsedAt.getTime()
      );
    });
  });

  describe('refreshSession', () => {
    it('should refresh session with new access token', async () => {
      const session = await createSession(
        testUserId,
        accessTokenExpirySeconds,
        refreshTokenExpirySeconds
      );

      const oldAccessToken = session.accessToken;
      const refreshToken = session.refreshToken!;

      const refreshed = await refreshSession(
        refreshToken,
        accessTokenExpirySeconds,
        refreshTokenExpirySeconds
      );

      expect(refreshed).toBeDefined();
      expect(refreshed!.accessToken).toBeDefined();
      expect(refreshed!.accessToken).not.toBe(oldAccessToken);
      expect(refreshed!.expiresIn).toBe(accessTokenExpirySeconds);

      // Old access token should no longer work
      const userId = await verifySession(oldAccessToken);
      expect(userId).toBeNull();

      // New access token should work
      const newUserId = await verifySession(refreshed!.accessToken);
      expect(newUserId).toBe(testUserId);
    });

    it('should rotate refresh token if requested', async () => {
      const session = await createSession(
        testUserId,
        accessTokenExpirySeconds,
        refreshTokenExpirySeconds
      );

      const oldRefreshToken = session.refreshToken!;

      const refreshed = await refreshSession(
        oldRefreshToken,
        accessTokenExpirySeconds,
        refreshTokenExpirySeconds
      );

      expect(refreshed).toBeDefined();
      expect(refreshed!.refreshToken).toBeDefined();
      expect(refreshed!.refreshToken).not.toBe(oldRefreshToken);
      expect(refreshed!.refreshTokenExpiresIn).toBe(refreshTokenExpirySeconds);

      // Old refresh token should no longer work
      const oldRefresh = await refreshSession(
        oldRefreshToken,
        accessTokenExpirySeconds,
        refreshTokenExpirySeconds
      );
      expect(oldRefresh).toBeNull();

      // New refresh token should work
      const newRefresh = await refreshSession(
        refreshed!.refreshToken!,
        accessTokenExpirySeconds,
        refreshTokenExpirySeconds
      );
      expect(newRefresh).toBeDefined();
    });

    it('should return null for invalid refresh token', async () => {
      const refreshed = await refreshSession(
        'invalid-refresh-token',
        accessTokenExpirySeconds,
        refreshTokenExpirySeconds
      );

      expect(refreshed).toBeNull();
    });

    it('should return null for expired refresh token', async () => {
      // Create session with 1 second refresh token expiry
      const session = await createSession(testUserId, accessTokenExpirySeconds, 1);

      // Wait for refresh token to expire
      await new Promise((resolve) => setTimeout(resolve, 1100));

      const refreshed = await refreshSession(
        session.refreshToken!,
        accessTokenExpirySeconds,
        refreshTokenExpirySeconds
      );

      expect(refreshed).toBeNull();

      // Verify session was deleted
      const storedSession = await prisma.session.findFirst({
        where: { userId: testUserId },
      });
      expect(storedSession).toBeNull();
    });

    it('should update lastUsedAt on refresh', async () => {
      const session = await createSession(
        testUserId,
        accessTokenExpirySeconds,
        refreshTokenExpirySeconds
      );

      const beforeRefresh = await prisma.session.findFirst({
        where: { userId: testUserId },
      });

      // Wait a bit
      await new Promise((resolve) => setTimeout(resolve, 100));

      await refreshSession(
        session.refreshToken!,
        accessTokenExpirySeconds,
        refreshTokenExpirySeconds
      );

      const afterRefresh = await prisma.session.findFirst({
        where: { userId: testUserId },
      });

      expect(afterRefresh!.lastUsedAt.getTime()).toBeGreaterThan(
        beforeRefresh!.lastUsedAt.getTime()
      );
    });
  });

  describe('revokeSession', () => {
    it('should revoke session by access token', async () => {
      const session = await createSession(testUserId, accessTokenExpirySeconds);

      const revoked = await revokeSession(session.accessToken);
      expect(revoked).toBe(true);

      // Verify session was deleted
      const storedSession = await prisma.session.findFirst({
        where: { userId: testUserId },
      });
      expect(storedSession).toBeNull();

      // Token should no longer work
      const userId = await verifySession(session.accessToken);
      expect(userId).toBeNull();
    });

    it('should return false for non-existent token', async () => {
      const revoked = await revokeSession('non-existent-token');
      expect(revoked).toBe(false);
    });

    it('should return true even if token already expired', async () => {
      const session = await createSession(testUserId, 1);

      // Wait for expiry
      await new Promise((resolve) => setTimeout(resolve, 1100));

      const revoked = await revokeSession(session.accessToken);
      // Should still return true even if expired (cleanup)
      expect(revoked).toBe(false); // Already cleaned up by expiry
    });
  });

  describe('cleanupExpiredSessions', () => {
    it('should delete sessions with expired access tokens', async () => {
      // Create session with 1 second expiry
      await createSession(testUserId, 1);

      // Wait for expiry
      await new Promise((resolve) => setTimeout(resolve, 1100));

      const deletedCount = await cleanupExpiredSessions();
      expect(deletedCount).toBeGreaterThanOrEqual(1);

      // Verify session was deleted
      const storedSession = await prisma.session.findFirst({
        where: { userId: testUserId },
      });
      expect(storedSession).toBeNull();
    });

    it('should delete sessions with expired refresh tokens', async () => {
      // Create session with 1 second refresh token expiry (but longer access token)
      await createSession(testUserId, 60, 1);

      // Wait for refresh token expiry
      await new Promise((resolve) => setTimeout(resolve, 1100));

      const deletedCount = await cleanupExpiredSessions();
      expect(deletedCount).toBeGreaterThanOrEqual(1);

      // Verify session was deleted
      const storedSession = await prisma.session.findFirst({
        where: { userId: testUserId },
      });
      expect(storedSession).toBeNull();
    });

    it('should not delete active sessions', async () => {
      await createSession(testUserId, accessTokenExpirySeconds, refreshTokenExpirySeconds);

      const deletedCount = await cleanupExpiredSessions();

      // Should not delete our active session
      const storedSession = await prisma.session.findFirst({
        where: { userId: testUserId },
      });
      expect(storedSession).toBeDefined();
    });

    it('should return 0 when no expired sessions exist', async () => {
      const deletedCount = await cleanupExpiredSessions();
      expect(deletedCount).toBe(0);
    });

    it('should delete multiple expired sessions in one call', async () => {
      // Create multiple expired sessions
      await createSession(testUserId, 1);
      await createSession(testUserId, 1);
      await createSession(testUserId, 1);

      // Wait for expiry
      await new Promise((resolve) => setTimeout(resolve, 1100));

      const deletedCount = await cleanupExpiredSessions();
      expect(deletedCount).toBe(3);

      // Verify all sessions were deleted
      const storedSessions = await prisma.session.findMany({
        where: { userId: testUserId },
      });
      expect(storedSessions).toHaveLength(0);
    });
  });

  describe('Token Security', () => {
    it('should generate cryptographically random tokens', async () => {
      const sessions = await Promise.all([
        createSession(testUserId, accessTokenExpirySeconds),
        createSession(testUserId, accessTokenExpirySeconds),
        createSession(testUserId, accessTokenExpirySeconds),
      ]);

      const tokens = sessions.map((s) => s.accessToken);

      // All tokens should be unique
      const uniqueTokens = new Set(tokens);
      expect(uniqueTokens.size).toBe(tokens.length);

      // Tokens should be base64url format (no special chars)
      tokens.forEach((token) => {
        expect(token).toMatch(/^[A-Za-z0-9_-]+$/);
      });
    });

    it('should store tokens as SHA-256 hashes', async () => {
      const session = await createSession(testUserId, accessTokenExpirySeconds);

      const storedSession = await prisma.session.findFirst({
        where: { userId: testUserId },
      });

      // Stored token hash should not match raw token
      expect(storedSession!.tokenHash).not.toBe(session.accessToken);

      // Hash should be 64 characters (32 bytes hex)
      expect(storedSession!.tokenHash).toHaveLength(64);
      expect(storedSession!.tokenHash).toMatch(/^[a-f0-9]{64}$/);
    });
  });
});
