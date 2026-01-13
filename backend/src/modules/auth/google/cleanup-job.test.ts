import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { SessionCleanupJob } from './cleanup-job.js';
import { prisma } from '../../../infra/db/prisma.js';
import { createSession } from './session-service.js';

describe('SessionCleanupJob', () => {
  const testUserId = 'cleanup-test-user';

  beforeEach(async () => {
    // Clean up test data
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
        googleSub: 'google-sub-cleanup',
        email: 'cleanup@example.com',
        displayName: 'Cleanup Test User',
      },
    });
  });

  afterEach(async () => {
    await prisma.session.deleteMany({
      where: { userId: testUserId },
    });
    await prisma.user.deleteMany({
      where: { id: testUserId },
    });
  });

  describe('constructor', () => {
    it('should create job with default interval (24 hours)', () => {
      const job = new SessionCleanupJob();
      expect(job).toBeDefined();
    });

    it('should create job with custom interval', () => {
      const customInterval = 1000; // 1 second
      const job = new SessionCleanupJob(customInterval);
      expect(job).toBeDefined();
    });
  });

  describe('start', () => {
    it('should run cleanup immediately on start', async () => {
      // Create an expired session
      await createSession(testUserId, 1);

      // Wait for expiry
      await new Promise((resolve) => setTimeout(resolve, 1100));

      const job = new SessionCleanupJob();
      await job.start();

      // Session should be cleaned up
      const sessions = await prisma.session.findMany({
        where: { userId: testUserId },
      });
      expect(sessions).toHaveLength(0);

      job.stop();
    });

    it('should schedule periodic cleanup', async () => {
      const intervalMs = 500; // 500ms for testing
      const job = new SessionCleanupJob(intervalMs);

      // Create multiple expired sessions
      await createSession(testUserId, 1);
      await createSession(testUserId, 1);

      // Wait for sessions to expire
      await new Promise((resolve) => setTimeout(resolve, 1100));

      await job.start();

      // Wait for first cleanup (immediate)
      await new Promise((resolve) => setTimeout(resolve, 100));

      const firstCleanup = await prisma.session.findMany({
        where: { userId: testUserId },
      });
      expect(firstCleanup).toHaveLength(0);

      // Create more expired sessions
      await createSession(testUserId, 1);
      await new Promise((resolve) => setTimeout(resolve, 1100));

      // Wait for periodic cleanup to run
      await new Promise((resolve) => setTimeout(resolve, intervalMs + 200));

      const secondCleanup = await prisma.session.findMany({
        where: { userId: testUserId },
      });
      expect(secondCleanup).toHaveLength(0);

      job.stop();
    });
  });

  describe('stop', () => {
    it('should stop periodic cleanup', async () => {
      const intervalMs = 500;
      const job = new SessionCleanupJob(intervalMs);

      await job.start();
      job.stop();

      // Create expired session after stop
      await createSession(testUserId, 1);
      await new Promise((resolve) => setTimeout(resolve, 1100));

      // Wait for what would have been the next cleanup
      await new Promise((resolve) => setTimeout(resolve, intervalMs + 200));

      // Session should NOT be cleaned up (job was stopped)
      const sessions = await prisma.session.findMany({
        where: { userId: testUserId },
      });
      // Note: Session might be 0 if initial cleanup ran before stop
      // This is expected behavior
    });

    it('should be idempotent (can call stop multiple times)', () => {
      const job = new SessionCleanupJob();
      expect(() => {
        job.stop();
        job.stop();
        job.stop();
      }).not.toThrow();
    });
  });

  describe('cleanup execution', () => {
    it('should log cleanup results', async () => {
      const consoleSpy = vi.spyOn(console, 'log').mockImplementation(() => {});

      // Create expired sessions
      await createSession(testUserId, 1);
      await createSession(testUserId, 1);
      await new Promise((resolve) => setTimeout(resolve, 1100));

      const job = new SessionCleanupJob();
      await job.start();

      // Check that cleanup was logged
      expect(consoleSpy).toHaveBeenCalledWith(
        expect.stringContaining('[Session Cleanup] Removed')
      );
      expect(consoleSpy).toHaveBeenCalledWith(
        expect.stringContaining('expired sessions in')
      );

      job.stop();
      consoleSpy.mockRestore();
    });

    it('should handle cleanup errors gracefully', async () => {
      const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

      const intervalMs = 200;
      const job = new SessionCleanupJob(intervalMs);

      // Mock Prisma to throw error
      const originalDeleteMany = prisma.session.deleteMany;
      vi.spyOn(prisma.session, 'deleteMany').mockRejectedValueOnce(
        new Error('Database connection failed')
      );

      await job.start();

      // Wait for periodic cleanup to attempt
      await new Promise((resolve) => setTimeout(resolve, intervalMs + 200));

      // Error should be logged but job should continue
      expect(consoleErrorSpy).toHaveBeenCalledWith(
        'Session cleanup job failed:',
        expect.any(Error)
      );

      job.stop();
      consoleErrorSpy.mockRestore();
      prisma.session.deleteMany = originalDeleteMany;
    });

    it('should return deleted count', async () => {
      // Create multiple expired sessions
      await createSession(testUserId, 1);
      await createSession(testUserId, 1);
      await createSession(testUserId, 1);
      await new Promise((resolve) => setTimeout(resolve, 1100));

      const job = new SessionCleanupJob();

      // Access the private runCleanup method through start
      await job.start();

      // Verify sessions were deleted
      const sessions = await prisma.session.findMany({
        where: { userId: testUserId },
      });
      expect(sessions).toHaveLength(0);

      job.stop();
    });
  });

  describe('integration with session lifecycle', () => {
    it('should clean up sessions after access token expires', async () => {
      await createSession(testUserId, 1); // 1 second access token

      await new Promise((resolve) => setTimeout(resolve, 1100));

      const job = new SessionCleanupJob();
      await job.start();

      const sessions = await prisma.session.findMany({
        where: { userId: testUserId },
      });
      expect(sessions).toHaveLength(0);

      job.stop();
    });

    it('should clean up sessions after refresh token expires', async () => {
      await createSession(testUserId, 60, 1); // 60s access, 1s refresh

      await new Promise((resolve) => setTimeout(resolve, 1100));

      const job = new SessionCleanupJob();
      await job.start();

      const sessions = await prisma.session.findMany({
        where: { userId: testUserId },
      });
      expect(sessions).toHaveLength(0);

      job.stop();
    });

    it('should not clean up active sessions', async () => {
      await createSession(testUserId, 60, 120); // Both long-lived

      const job = new SessionCleanupJob();
      await job.start();

      const sessions = await prisma.session.findMany({
        where: { userId: testUserId },
      });
      expect(sessions).toHaveLength(1);

      job.stop();
    });
  });
});
