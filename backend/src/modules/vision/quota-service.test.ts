/**
 * Tests for VisionQuotaService
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { PrismaClient } from '@prisma/client';
import { VisionQuotaService } from './quota-service.js';

describe('VisionQuotaService', () => {
  let prisma: PrismaClient;
  let quotaService: VisionQuotaService;
  let testUserId: string;

  beforeEach(async () => {
    prisma = new PrismaClient();
    quotaService = new VisionQuotaService(prisma, { dailyLimit: 5 }); // Low limit for testing

    // Create a test user
    const user = await prisma.user.create({
      data: {
        email: `test-${Date.now()}@example.com`,
      },
    });
    testUserId = user.id;
  });

  afterEach(async () => {
    // Clean up test data
    await prisma.visionQuota.deleteMany({
      where: { userId: testUserId },
    });
    await prisma.user.delete({
      where: { id: testUserId },
    });
    await prisma.$disconnect();
  });

  it('should allow requests within quota', async () => {
    const result = await quotaService.checkQuota(testUserId);

    expect(result.allowed).toBe(true);
    expect(result.currentCount).toBe(0);
    expect(result.limit).toBe(5);
  });

  it('should increment quota on successful request', async () => {
    await quotaService.incrementQuota(testUserId);

    const result = await quotaService.checkQuota(testUserId);
    expect(result.currentCount).toBe(1);
    expect(result.allowed).toBe(true);
  });

  it('should deny requests when quota exceeded', async () => {
    // Use up all quota
    for (let i = 0; i < 5; i++) {
      await quotaService.incrementQuota(testUserId);
    }

    const result = await quotaService.checkQuota(testUserId);
    expect(result.allowed).toBe(false);
    expect(result.currentCount).toBe(5);
    expect(result.limit).toBe(5);
  });

  it('should return correct quota usage', async () => {
    await quotaService.incrementQuota(testUserId);
    await quotaService.incrementQuota(testUserId);

    const usage = await quotaService.getQuotaUsage(testUserId);
    expect(usage.used).toBe(2);
    expect(usage.limit).toBe(5);
    expect(usage.resetAt).toBeInstanceOf(Date);
  });

  it('should have different quotas for different days', async () => {
    // This test would need to manipulate dates or use a time-travel library
    // For now, just verify the concept
    const today = new Date().toISOString().slice(0, 10);
    await quotaService.incrementQuota(testUserId);

    const quota = await prisma.visionQuota.findFirst({
      where: {
        userId: testUserId,
        date: new Date(today + 'T00:00:00.000Z'),
      },
    });

    expect(quota).not.toBeNull();
    expect(quota?.count).toBe(1);
  });

  it('should handle multiple increments correctly', async () => {
    await quotaService.incrementQuota(testUserId);
    await quotaService.incrementQuota(testUserId);
    await quotaService.incrementQuota(testUserId);

    const result = await quotaService.checkQuota(testUserId);
    expect(result.currentCount).toBe(3);
    expect(result.allowed).toBe(true);
  });

  it('should cleanup old records', async () => {
    // Create an old quota record
    const oldDate = new Date();
    oldDate.setUTCDate(oldDate.getUTCDate() - 10);
    oldDate.setUTCHours(0, 0, 0, 0);

    await prisma.visionQuota.create({
      data: {
        userId: testUserId,
        date: oldDate,
        count: 5,
      },
    });

    // Cleanup records older than 7 days
    const deletedCount = await quotaService.cleanupOldRecords(7);

    expect(deletedCount).toBeGreaterThan(0);

    // Verify the old record is gone
    const oldQuota = await prisma.visionQuota.findFirst({
      where: {
        userId: testUserId,
        date: oldDate,
      },
    });

    expect(oldQuota).toBeNull();
  });

  it('should respect custom daily limit', async () => {
    const customService = new VisionQuotaService(prisma, { dailyLimit: 10 });

    const result = await customService.checkQuota(testUserId);
    expect(result.limit).toBe(10);
  });

  it('should use default limit of 50 when not specified', async () => {
    const defaultService = new VisionQuotaService(prisma);

    const result = await defaultService.checkQuota(testUserId);
    expect(result.limit).toBe(50);
  });
});
