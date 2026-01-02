/**
 * Staged Request Manager
 *
 * Handles async assistant requests with:
 * - Staged progress reporting (VISION_PENDING, LLM_PENDING, DONE)
 * - Request deduplication
 * - Timeout handling
 * - Result caching
 */

import { randomUUID } from 'node:crypto';
import { AssistantResponse } from './types.js';
import { VisualFacts } from '../vision/types.js';

/**
 * Request stages.
 */
export type RequestStage =
  | 'PENDING'
  | 'VISION_PROCESSING'
  | 'VISION_DONE'
  | 'LLM_PROCESSING'
  | 'LLM_DONE'
  | 'DONE'
  | 'ERROR';

/**
 * Vision preview extracted during staged processing.
 */
export type VisionPreview = {
  /** Top colors detected */
  topColors?: Array<{ name: string; pct: number }>;
  /** Detected brand (if any) */
  brandHint?: string;
  /** Detected text snippets */
  textSnippets?: string[];
  /** Item IDs with vision data */
  itemIds: string[];
};

/**
 * Staged request status.
 */
export type StagedRequestStatus = {
  requestId: string;
  stage: RequestStage;
  startedAt: number;
  updatedAt: number;
  /** Vision preview (available after VISION_DONE) */
  visionPreview?: VisionPreview;
  /** Final response (available after DONE) */
  response?: AssistantResponse;
  /** Error message (if ERROR stage) */
  error?: string;
  /** Correlation ID for tracing */
  correlationId: string;
};

/**
 * Staged request entry.
 */
type StagedRequestEntry = {
  status: StagedRequestStatus;
  /** Promise that resolves when complete */
  completionPromise: Promise<AssistantResponse>;
  /** Resolve function */
  resolve?: (response: AssistantResponse) => void;
  /** Reject function */
  reject?: (error: Error) => void;
  /** Visual facts collected during processing */
  visualFacts?: Map<string, VisualFacts>;
  /** Waiters for deduplication */
  waiters: number;
};

/**
 * Options for staged request manager.
 */
export type StagedRequestManagerOptions = {
  /** Request timeout in milliseconds */
  timeoutMs: number;
  /** Maximum concurrent requests */
  maxConcurrent: number;
  /** Cleanup interval in milliseconds */
  cleanupIntervalMs: number;
  /** Result retention time in milliseconds */
  resultRetentionMs: number;
};

const DEFAULT_OPTIONS: StagedRequestManagerOptions = {
  timeoutMs: 60000,
  maxConcurrent: 100,
  cleanupIntervalMs: 30000,
  resultRetentionMs: 300000, // 5 minutes
};

/**
 * Staged request manager.
 */
export class StagedRequestManager {
  private readonly requests = new Map<string, StagedRequestEntry>();
  private readonly options: StagedRequestManagerOptions;
  private cleanupTimer?: NodeJS.Timeout;

  // Stats
  private stats = {
    totalRequests: 0,
    completedRequests: 0,
    erroredRequests: 0,
    timedOutRequests: 0,
    coalescedRequests: 0,
  };

  constructor(options: Partial<StagedRequestManagerOptions> = {}) {
    // Merge with defaults, ensuring finite numbers (guards against undefined/NaN)
    this.options = {
      timeoutMs: Number.isFinite(options.timeoutMs) ? options.timeoutMs! : DEFAULT_OPTIONS.timeoutMs,
      maxConcurrent: Number.isFinite(options.maxConcurrent) ? options.maxConcurrent! : DEFAULT_OPTIONS.maxConcurrent,
      cleanupIntervalMs: Number.isFinite(options.cleanupIntervalMs) ? options.cleanupIntervalMs! : DEFAULT_OPTIONS.cleanupIntervalMs,
      resultRetentionMs: Number.isFinite(options.resultRetentionMs) ? options.resultRetentionMs! : DEFAULT_OPTIONS.resultRetentionMs,
    };

    this.cleanupTimer = setInterval(() => {
      this.cleanup();
    }, this.options.cleanupIntervalMs);
  }

  /**
   * Create a new staged request.
   */
  createRequest(correlationId: string): { requestId: string; status: StagedRequestStatus } {
    const requestId = randomUUID();
    const now = Date.now();

    let resolveFunc: ((response: AssistantResponse) => void) | undefined;
    let rejectFunc: ((error: Error) => void) | undefined;

    const completionPromise = new Promise<AssistantResponse>((resolve, reject) => {
      resolveFunc = resolve;
      rejectFunc = reject;
    });

    const status: StagedRequestStatus = {
      requestId,
      stage: 'PENDING',
      startedAt: now,
      updatedAt: now,
      correlationId,
    };

    const entry: StagedRequestEntry = {
      status,
      completionPromise,
      resolve: resolveFunc,
      reject: rejectFunc,
      waiters: 0,
    };

    this.requests.set(requestId, entry);
    this.stats.totalRequests++;

    // Set timeout
    setTimeout(() => {
      this.handleTimeout(requestId);
    }, this.options.timeoutMs);

    return { requestId, status };
  }

  /**
   * Get request status.
   */
  getStatus(requestId: string): StagedRequestStatus | null {
    const entry = this.requests.get(requestId);
    return entry?.status ?? null;
  }

  /**
   * Update request stage.
   */
  updateStage(requestId: string, stage: RequestStage): void {
    const entry = this.requests.get(requestId);
    if (!entry) return;

    entry.status.stage = stage;
    entry.status.updatedAt = Date.now();
  }

  /**
   * Set vision preview.
   */
  setVisionPreview(requestId: string, preview: VisionPreview): void {
    const entry = this.requests.get(requestId);
    if (!entry) return;

    entry.status.visionPreview = preview;
    entry.status.stage = 'VISION_DONE';
    entry.status.updatedAt = Date.now();
  }

  /**
   * Set visual facts (for caching).
   */
  setVisualFacts(requestId: string, facts: Map<string, VisualFacts>): void {
    const entry = this.requests.get(requestId);
    if (!entry) return;
    entry.visualFacts = facts;
  }

  /**
   * Get visual facts.
   */
  getVisualFacts(requestId: string): Map<string, VisualFacts> | undefined {
    return this.requests.get(requestId)?.visualFacts;
  }

  /**
   * Complete request with response.
   */
  complete(requestId: string, response: AssistantResponse): void {
    const entry = this.requests.get(requestId);
    if (!entry) return;

    entry.status.stage = 'DONE';
    entry.status.updatedAt = Date.now();
    entry.status.response = response;
    entry.resolve?.(response);
    this.stats.completedRequests++;
  }

  /**
   * Fail request with error.
   */
  fail(requestId: string, error: string): void {
    const entry = this.requests.get(requestId);
    if (!entry) return;

    entry.status.stage = 'ERROR';
    entry.status.updatedAt = Date.now();
    entry.status.error = error;
    entry.reject?.(new Error(error));
    this.stats.erroredRequests++;
  }

  /**
   * Wait for request completion.
   */
  async waitForCompletion(requestId: string): Promise<AssistantResponse | null> {
    const entry = this.requests.get(requestId);
    if (!entry) return null;

    entry.waiters++;
    try {
      return await entry.completionPromise;
    } catch {
      return null;
    } finally {
      entry.waiters--;
    }
  }

  /**
   * Check if request exists and is not expired.
   */
  hasRequest(requestId: string): boolean {
    return this.requests.has(requestId);
  }

  /**
   * Get manager stats.
   */
  getStats(): typeof this.stats & { activeRequests: number } {
    return {
      ...this.stats,
      activeRequests: this.requests.size,
    };
  }

  /**
   * Stop cleanup timer.
   */
  stop(): void {
    if (this.cleanupTimer) {
      clearInterval(this.cleanupTimer);
      this.cleanupTimer = undefined;
    }
  }

  private handleTimeout(requestId: string): void {
    const entry = this.requests.get(requestId);
    if (!entry) return;

    // Only timeout if not yet complete
    if (entry.status.stage !== 'DONE' && entry.status.stage !== 'ERROR') {
      entry.status.stage = 'ERROR';
      entry.status.updatedAt = Date.now();
      entry.status.error = 'Request timed out';
      entry.reject?.(new Error('Request timed out'));
      this.stats.timedOutRequests++;
    }
  }

  private cleanup(): void {
    const now = Date.now();
    const cutoff = now - this.options.resultRetentionMs;

    for (const [requestId, entry] of this.requests.entries()) {
      // Remove completed/errored requests that are old
      if (
        (entry.status.stage === 'DONE' || entry.status.stage === 'ERROR') &&
        entry.status.updatedAt < cutoff &&
        entry.waiters === 0
      ) {
        this.requests.delete(requestId);
      }
    }
  }
}

/**
 * Build vision preview from visual facts.
 */
export function buildVisionPreview(facts: Map<string, VisualFacts>): VisionPreview {
  const preview: VisionPreview = {
    itemIds: [...facts.keys()],
  };

  // Aggregate colors from all items
  const allColors: Array<{ name: string; pct: number }> = [];
  const allBrands: string[] = [];
  const allTexts: string[] = [];

  for (const fact of facts.values()) {
    // Colors
    for (const color of fact.dominantColors.slice(0, 2)) {
      allColors.push({ name: color.name, pct: color.pct });
    }

    // Brands from logos
    if (fact.logoHints) {
      for (const logo of fact.logoHints.slice(0, 1)) {
        if (logo.score >= 0.5) {
          allBrands.push(logo.brand);
        }
      }
    }

    // Text snippets (short ones only)
    for (const ocr of fact.ocrSnippets.slice(0, 3)) {
      if (ocr.text.length <= 30) {
        allTexts.push(ocr.text);
      }
    }
  }

  if (allColors.length > 0) {
    preview.topColors = allColors.slice(0, 3);
  }
  if (allBrands.length > 0) {
    preview.brandHint = allBrands[0];
  }
  if (allTexts.length > 0) {
    preview.textSnippets = allTexts.slice(0, 3);
  }

  return preview;
}
