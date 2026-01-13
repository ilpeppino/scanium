import { cleanupExpiredSessions } from './session-service.js';

/**
 * Phase C: Session cleanup job
 * Runs on startup and then daily to remove expired sessions
 */
export class SessionCleanupJob {
  private intervalId: NodeJS.Timeout | null = null;
  private readonly intervalMs: number;

  constructor(intervalMs: number = 24 * 60 * 60 * 1000) {
    // Default: 24 hours
    this.intervalMs = intervalMs;
  }

  async start() {
    // Run immediately on startup
    await this.runCleanup();

    // Schedule periodic cleanup
    this.intervalId = setInterval(() => {
      this.runCleanup().catch((error) => {
        console.error('Session cleanup job failed:', error);
      });
    }, this.intervalMs);
  }

  stop() {
    if (this.intervalId) {
      clearInterval(this.intervalId);
      this.intervalId = null;
    }
  }

  private async runCleanup() {
    const startTime = Date.now();
    const deletedCount = await cleanupExpiredSessions();
    const duration = Date.now() - startTime;

    console.log(
      `[Session Cleanup] Removed ${deletedCount} expired sessions in ${duration}ms`
    );

    return deletedCount;
  }
}
