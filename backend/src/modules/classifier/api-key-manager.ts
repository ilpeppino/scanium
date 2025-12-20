import { randomBytes } from 'node:crypto';

/**
 * API Key metadata for rotation and tracking
 */
export interface ApiKeyMetadata {
  key: string;
  name: string;
  createdAt: Date;
  expiresAt: Date | null;
  rotatedFrom?: string;
  isActive: boolean;
}

/**
 * API Key usage event for security monitoring
 */
export interface ApiKeyUsageEvent {
  apiKey: string;
  timestamp: Date;
  endpoint: string;
  method: string;
  success: boolean;
  ip: string;
  userAgent?: string;
  errorCode?: string;
}

/**
 * API Key Manager for rotation and monitoring
 * Implements recommendations from SEC-003
 */
export class ApiKeyManager {
  private keys: Map<string, ApiKeyMetadata>;
  private usageLog: ApiKeyUsageEvent[] = [];
  private readonly maxUsageLogSize = 10000;

  constructor(apiKeys: string[]) {
    this.keys = new Map();

    // Initialize keys from config (legacy support)
    for (const key of apiKeys) {
      if (key.trim()) {
        this.keys.set(key, {
          key,
          name: 'legacy-key',
          createdAt: new Date(),
          expiresAt: null,
          isActive: true,
        });
      }
    }
  }

  /**
   * Generate a new API key with cryptographically secure random bytes
   */
  generateKey(): string {
    // Generate 32 bytes (256 bits) of cryptographically secure random data
    // Encode as base64url for safe transmission in HTTP headers
    return randomBytes(32).toString('base64url');
  }

  /**
   * Create a new API key with metadata
   */
  createKey(name: string, expiresInDays: number | null = null): ApiKeyMetadata {
    const key = this.generateKey();
    const createdAt = new Date();
    const expiresAt = expiresInDays
      ? new Date(createdAt.getTime() + expiresInDays * 24 * 60 * 60 * 1000)
      : null;

    const metadata: ApiKeyMetadata = {
      key,
      name,
      createdAt,
      expiresAt,
      isActive: true,
    };

    this.keys.set(key, metadata);
    return metadata;
  }

  /**
   * Rotate an existing API key
   * Creates a new key and marks the old one for deprecation
   */
  rotateKey(oldKey: string, expiresInDays: number | null = null): ApiKeyMetadata | null {
    const oldMetadata = this.keys.get(oldKey);
    if (!oldMetadata) {
      return null;
    }

    // Create new key with reference to old one
    const newKey = this.generateKey();
    const createdAt = new Date();
    const expiresAt = expiresInDays
      ? new Date(createdAt.getTime() + expiresInDays * 24 * 60 * 60 * 1000)
      : null;

    const newMetadata: ApiKeyMetadata = {
      key: newKey,
      name: oldMetadata.name,
      createdAt,
      expiresAt,
      rotatedFrom: oldKey,
      isActive: true,
    };

    this.keys.set(newKey, newMetadata);

    // Mark old key for deprecation (expire in 30 days to allow transition)
    oldMetadata.expiresAt = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);

    return newMetadata;
  }

  /**
   * Validate an API key and check expiration
   */
  validateKey(key: string): boolean {
    const metadata = this.keys.get(key);
    if (!metadata || !metadata.isActive) {
      return false;
    }

    // Check expiration
    if (metadata.expiresAt && metadata.expiresAt < new Date()) {
      return false;
    }

    return true;
  }

  /**
   * Log API key usage for security monitoring
   */
  logUsage(event: ApiKeyUsageEvent): void {
    this.usageLog.push(event);

    // Trim log if it exceeds max size (keep most recent)
    if (this.usageLog.length > this.maxUsageLogSize) {
      this.usageLog = this.usageLog.slice(-this.maxUsageLogSize);
    }
  }

  /**
   * Get usage statistics for a specific API key
   */
  getKeyUsageStats(apiKey: string): {
    totalRequests: number;
    successfulRequests: number;
    failedRequests: number;
    lastUsed: Date | null;
    recentFailures: ApiKeyUsageEvent[];
  } {
    const events = this.usageLog.filter((e) => e.apiKey === apiKey);
    const successful = events.filter((e) => e.success);
    const failed = events.filter((e) => !e.success);

    return {
      totalRequests: events.length,
      successfulRequests: successful.length,
      failedRequests: failed.length,
      lastUsed: events.length > 0 ? events[events.length - 1].timestamp : null,
      recentFailures: failed.slice(-10), // Last 10 failures
    };
  }

  /**
   * Get all API keys metadata (excluding sensitive key values)
   */
  listKeys(): Array<Omit<ApiKeyMetadata, 'key'> & { keyPrefix: string }> {
    return Array.from(this.keys.values()).map((metadata) => ({
      name: metadata.name,
      keyPrefix: metadata.key.substring(0, 8) + '...',
      createdAt: metadata.createdAt,
      expiresAt: metadata.expiresAt,
      rotatedFrom: metadata.rotatedFrom,
      isActive: metadata.isActive,
    }));
  }

  /**
   * Revoke an API key immediately
   */
  revokeKey(key: string): boolean {
    const metadata = this.keys.get(key);
    if (!metadata) {
      return false;
    }

    metadata.isActive = false;
    metadata.expiresAt = new Date();
    return true;
  }

  /**
   * Clean up expired keys from memory
   */
  cleanupExpiredKeys(): number {
    const now = new Date();
    let removed = 0;

    for (const [key, metadata] of this.keys.entries()) {
      if (metadata.expiresAt && metadata.expiresAt < now) {
        // Keep in map but mark as inactive for audit trail
        // Could also remove entirely: this.keys.delete(key);
        metadata.isActive = false;
        removed++;
      }
    }

    return removed;
  }

  /**
   * Get metadata for a specific key
   */
  getKeyMetadata(key: string): ApiKeyMetadata | undefined {
    return this.keys.get(key);
  }
}
