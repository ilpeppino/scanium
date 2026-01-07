/**
 * Enrichment Manager
 *
 * Manages async enrichment jobs with in-memory state tracking.
 * Handles job queuing, status updates, and result retention.
 */

import { randomUUID } from 'crypto';
import {
  EnrichmentStatus,
  EnrichmentStage,
  EnrichRequest,
  EnrichResponse,
  EnrichmentConfig,
  VisionFactsSummary,
  NormalizedAttribute,
  ListingDraft,
} from './types.js';
import { runEnrichmentPipeline } from './pipeline.js';
import { FastifyBaseLogger } from 'fastify';

/**
 * Default enrichment configuration.
 */
const DEFAULT_CONFIG: EnrichmentConfig = {
  visionTimeoutMs: 15000,
  draftTimeoutMs: 30000,
  maxConcurrent: 10,
  resultRetentionMs: 30 * 60 * 1000, // 30 minutes
  enableDraftGeneration: true,
  llmModel: 'gpt-4o-mini',
};

/**
 * Manages enrichment request lifecycle.
 */
export class EnrichmentManager {
  private readonly jobs = new Map<string, EnrichmentStatus>();
  private readonly config: EnrichmentConfig;
  private readonly logger: FastifyBaseLogger;
  private activeJobs = 0;
  private cleanupInterval: ReturnType<typeof setInterval> | null = null;

  constructor(logger: FastifyBaseLogger, config: Partial<EnrichmentConfig> = {}) {
    this.logger = logger;
    this.config = { ...DEFAULT_CONFIG, ...config };

    // Start cleanup interval
    this.cleanupInterval = setInterval(() => {
      this.cleanupExpiredJobs();
    }, 60000); // Every minute
  }

  /**
   * Submit a new enrichment request.
   */
  async submit(request: EnrichRequest, correlationId: string): Promise<EnrichResponse> {
    const requestId = randomUUID();
    const now = Date.now();

    // Create initial job status
    const status: EnrichmentStatus = {
      requestId,
      correlationId,
      itemId: request.itemId,
      stage: 'QUEUED',
      createdAt: now,
      updatedAt: now,
      timings: {},
    };

    this.jobs.set(requestId, status);

    this.logger.info({
      msg: 'Enrichment request submitted',
      requestId,
      correlationId,
      itemId: request.itemId,
      stage: 'QUEUED',
    });

    // Check concurrency limit
    if (this.activeJobs >= this.config.maxConcurrent) {
      this.logger.warn({
        msg: 'Enrichment queue at capacity',
        requestId,
        activeJobs: this.activeJobs,
        maxConcurrent: this.config.maxConcurrent,
      });
    }

    // Start async processing
    this.processAsync(requestId, request).catch((err) => {
      this.logger.error({
        msg: 'Unhandled error in enrichment pipeline',
        requestId,
        error: err instanceof Error ? err.message : String(err),
      });
    });

    return { requestId, correlationId };
  }

  /**
   * Get status of an enrichment request.
   */
  getStatus(requestId: string): EnrichmentStatus | null {
    return this.jobs.get(requestId) ?? null;
  }

  /**
   * Get all active job statuses.
   */
  getActiveJobs(): EnrichmentStatus[] {
    return Array.from(this.jobs.values()).filter(
      (job) => job.stage !== 'DRAFT_DONE' && job.stage !== 'FAILED'
    );
  }

  /**
   * Get metrics for observability.
   */
  getMetrics(): {
    activeJobs: number;
    totalJobs: number;
    maxConcurrent: number;
  } {
    return {
      activeJobs: this.activeJobs,
      totalJobs: this.jobs.size,
      maxConcurrent: this.config.maxConcurrent,
    };
  }

  /**
   * Shutdown the manager.
   */
  shutdown(): void {
    if (this.cleanupInterval) {
      clearInterval(this.cleanupInterval);
      this.cleanupInterval = null;
    }
  }

  /**
   * Process an enrichment request asynchronously.
   */
  private async processAsync(requestId: string, request: EnrichRequest): Promise<void> {
    this.activeJobs++;

    try {
      const startTime = Date.now();

      // Run the pipeline
      const result = await runEnrichmentPipeline(
        request,
        this.config,
        this.logger,
        (stage, data) => this.updateStatus(requestId, stage, data)
      );

      // Final update
      const totalTime = Date.now() - startTime;
      const status = this.jobs.get(requestId);

      if (status) {
        status.stage = result.success ? 'DRAFT_DONE' : 'FAILED';
        status.updatedAt = Date.now();
        status.timings = {
          ...status.timings,
          total: totalTime,
        };

        if (!result.success && result.error) {
          status.error = result.error;
        }

        this.logger.info({
          msg: 'Enrichment pipeline completed',
          requestId,
          correlationId: status.correlationId,
          itemId: status.itemId,
          stage: status.stage,
          totalMs: totalTime,
          success: result.success,
        });
      }
    } catch (err) {
      const status = this.jobs.get(requestId);
      if (status) {
        status.stage = 'FAILED';
        status.updatedAt = Date.now();
        status.error = {
          code: 'PIPELINE_ERROR',
          message: err instanceof Error ? err.message : 'Unknown error',
          stage: status.stage,
          retryable: true,
        };
      }

      this.logger.error({
        msg: 'Enrichment pipeline failed',
        requestId,
        error: err instanceof Error ? err.message : String(err),
      });
    } finally {
      this.activeJobs--;
    }
  }

  /**
   * Update job status with stage progress.
   */
  private updateStatus(
    requestId: string,
    stage: EnrichmentStage,
    data: Partial<{
      visionFacts: VisionFactsSummary;
      normalizedAttributes: NormalizedAttribute[];
      draft: ListingDraft;
      timingMs: number;
      timingKey: 'vision' | 'attributes' | 'draft';
    }>
  ): void {
    const status = this.jobs.get(requestId);
    if (!status) return;

    status.stage = stage;
    status.updatedAt = Date.now();

    if (data.visionFacts) {
      status.visionFacts = data.visionFacts;
    }
    if (data.normalizedAttributes) {
      status.normalizedAttributes = data.normalizedAttributes;
    }
    if (data.draft) {
      status.draft = data.draft;
    }
    if (data.timingMs && data.timingKey) {
      status.timings = status.timings ?? {};
      status.timings[data.timingKey] = data.timingMs;
    }

    this.logger.info({
      msg: 'Enrichment stage update',
      requestId,
      correlationId: status.correlationId,
      itemId: status.itemId,
      stage,
      timingMs: data.timingMs,
    });
  }

  /**
   * Remove expired jobs to prevent memory growth.
   */
  private cleanupExpiredJobs(): void {
    const now = Date.now();
    const expiredBefore = now - this.config.resultRetentionMs;
    let cleaned = 0;

    for (const [requestId, status] of this.jobs.entries()) {
      // Only clean completed/failed jobs
      if (
        (status.stage === 'DRAFT_DONE' || status.stage === 'FAILED') &&
        status.updatedAt < expiredBefore
      ) {
        this.jobs.delete(requestId);
        cleaned++;
      }
    }

    if (cleaned > 0) {
      this.logger.info({
        msg: 'Cleaned up expired enrichment jobs',
        cleaned,
        remaining: this.jobs.size,
      });
    }
  }
}

// Singleton instance (initialized by routes)
let enrichmentManager: EnrichmentManager | null = null;

/**
 * Get or create the enrichment manager singleton.
 */
export function getEnrichmentManager(
  logger: FastifyBaseLogger,
  config?: Partial<EnrichmentConfig>
): EnrichmentManager {
  if (!enrichmentManager) {
    enrichmentManager = new EnrichmentManager(logger, config);
  }
  return enrichmentManager;
}

/**
 * Shutdown the enrichment manager.
 */
export function shutdownEnrichmentManager(): void {
  if (enrichmentManager) {
    enrichmentManager.shutdown();
    enrichmentManager = null;
  }
}
