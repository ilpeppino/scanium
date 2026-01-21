/**
 * Vision API Quota Service
 *
 * Manages per-user daily quota for Google Vision API requests.
 * Default quota: 50 requests per user per day.
 */

import { PrismaClient } from '@prisma/client';

export type QuotaCheckResult = {
  allowed: boolean;
  currentCount: number;
  limit: number;
  resetAt: Date; // When the quota resets (midnight UTC next day)
};

export class VisionQuotaService {
  private readonly dailyLimit: number;

  constructor(
    private readonly prisma: PrismaClient,
    options?: { dailyLimit?: number }
  ) {
    this.dailyLimit = options?.dailyLimit ?? 50;
  }

  /**
   * Check if user has available quota for a Vision API request.
   * Does NOT increment the quota - call incrementQuota() after successful request.
   */
  async checkQuota(userId: string): Promise<QuotaCheckResult> {
    const today = this.getTodayDateString();
    const todayDate = this.parseDate(today);

    // Get or create today's quota record
    const quota = await this.prisma.visionQuota.findUnique({
      where: {
        userId_date: {
          userId,
          date: todayDate,
        },
      },
    });

    const currentCount = quota?.count ?? 0;
    const allowed = currentCount < this.dailyLimit;

    // Calculate reset time (midnight UTC next day)
    const resetAt = new Date(todayDate);
    resetAt.setUTCDate(resetAt.getUTCDate() + 1);
    resetAt.setUTCHours(0, 0, 0, 0);

    return {
      allowed,
      currentCount,
      limit: this.dailyLimit,
      resetAt,
    };
  }

  /**
   * Increment the user's quota count after a successful Vision API request.
   * This is separate from checkQuota() to support transactional semantics.
   */
  async incrementQuota(userId: string): Promise<void> {
    const today = this.getTodayDateString();
    const todayDate = this.parseDate(today);

    await this.prisma.visionQuota.upsert({
      where: {
        userId_date: {
          userId,
          date: todayDate,
        },
      },
      create: {
        userId,
        date: todayDate,
        count: 1,
      },
      update: {
        count: {
          increment: 1,
        },
      },
    });
  }

  /**
   * Get current quota usage for a user (for informational purposes).
   */
  async getQuotaUsage(userId: string): Promise<{ used: number; limit: number; resetAt: Date }> {
    const result = await this.checkQuota(userId);
    return {
      used: result.currentCount,
      limit: result.limit,
      resetAt: result.resetAt,
    };
  }

  /**
   * Cleanup old quota records (optional maintenance task).
   * Removes records older than the specified number of days.
   */
  async cleanupOldRecords(daysToKeep: number = 7): Promise<number> {
    const cutoffDate = new Date();
    cutoffDate.setUTCDate(cutoffDate.getUTCDate() - daysToKeep);
    cutoffDate.setUTCHours(0, 0, 0, 0);

    const result = await this.prisma.visionQuota.deleteMany({
      where: {
        date: {
          lt: cutoffDate,
        },
      },
    });

    return result.count;
  }

  /**
   * Get today's date as YYYY-MM-DD string in UTC.
   */
  private getTodayDateString(): string {
    const now = new Date();
    return now.toISOString().slice(0, 10);
  }

  /**
   * Parse date string to Date object (date-only, no time component).
   */
  private parseDate(dateString: string): Date {
    const date = new Date(dateString + 'T00:00:00.000Z');
    return date;
  }
}
