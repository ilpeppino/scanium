/**
 * Simple file-based cache for eBay API responses
 * Reduces API calls during development and testing
 */

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { createHash } from 'node:crypto';

export class CacheManager {
  private cacheDir: string;
  private enabled: boolean;

  constructor(cacheDir?: string) {
    this.cacheDir = cacheDir || '.cache/ebay';
    this.enabled = !!cacheDir;

    if (this.enabled) {
      // Ensure cache directory exists
      mkdirSync(this.cacheDir, { recursive: true });
    }
  }

  /**
   * Get a cached value by key
   */
  get<T>(key: string): T | null {
    if (!this.enabled) return null;

    const filePath = this.getFilePath(key);
    if (!existsSync(filePath)) {
      return null;
    }

    try {
      const content = readFileSync(filePath, 'utf-8');
      const cached = JSON.parse(content) as { timestamp: number; data: T };

      // Check if cache is from today (simple expiration)
      const today = new Date().toISOString().split('T')[0];
      const cacheDate = new Date(cached.timestamp).toISOString().split('T')[0];

      if (today === cacheDate) {
        return cached.data;
      }

      // Cache expired
      return null;
    } catch (error) {
      console.warn(`Failed to read cache for key ${key}:`, error);
      return null;
    }
  }

  /**
   * Set a cached value
   */
  set<T>(key: string, value: T): void {
    if (!this.enabled) return;

    const filePath = this.getFilePath(key);
    const cached = {
      timestamp: Date.now(),
      data: value,
    };

    try {
      writeFileSync(filePath, JSON.stringify(cached, null, 2), 'utf-8');
    } catch (error) {
      console.warn(`Failed to write cache for key ${key}:`, error);
    }
  }

  /**
   * Generate a cache file path from a key
   */
  private getFilePath(key: string): string {
    // Hash the key to create a safe filename
    const hash = createHash('sha256').update(key).digest('hex').substring(0, 16);
    return join(this.cacheDir, `${hash}.json`);
  }

  /**
   * Clear all cache
   */
  clear(): void {
    if (!this.enabled) return;

    try {
      const { readdirSync, unlinkSync } = require('node:fs');
      const files = readdirSync(this.cacheDir);
      for (const file of files) {
        if (file.endsWith('.json')) {
          unlinkSync(join(this.cacheDir, file));
        }
      }
    } catch (error) {
      console.warn('Failed to clear cache:', error);
    }
  }
}
