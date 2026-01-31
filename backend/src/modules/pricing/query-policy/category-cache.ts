import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';

type CacheEntry = {
  categoryId: string;
  expiresAt: number;
  updatedAt: number;
};

type CacheData = {
  version: 1;
  entries: Record<string, CacheEntry>;
};

const DEFAULT_TTL_MS = 7 * 24 * 60 * 60 * 1000;

export type CategoryCacheOptions = {
  filePath: string;
  ttlMs?: number;
  now?: () => number;
};

export class CategoryResolutionCache {
  private readonly filePath: string;
  private readonly ttlMs: number;
  private readonly now: () => number;
  private loaded = false;
  private data: CacheData = { version: 1, entries: {} };

  constructor(options: CategoryCacheOptions) {
    this.filePath = options.filePath;
    this.ttlMs = options.ttlMs ?? DEFAULT_TTL_MS;
    this.now = options.now ?? (() => Date.now());
  }

  get(key: string): string | undefined {
    try {
      this.load();
      const entry = this.data.entries[key];
      if (!entry) return undefined;
      if (entry.expiresAt <= this.now()) {
        delete this.data.entries[key];
        this.persist();
        return undefined;
      }
      return entry.categoryId;
    } catch {
      return undefined;
    }
  }

  set(key: string, categoryId: string): void {
    try {
      this.load();
      const now = this.now();
      this.data.entries[key] = {
        categoryId,
        updatedAt: now,
        expiresAt: now + this.ttlMs,
      };
      this.persist();
    } catch {
      // Ignore cache write failures
    }
  }

  clear(): void {
    this.data = { version: 1, entries: {} };
    this.loaded = true;
    try {
      this.persist();
    } catch {
      // ignore
    }
  }

  private load(): void {
    if (this.loaded) return;
    this.loaded = true;
    if (!existsSync(this.filePath)) return;

    try {
      const raw = readFileSync(this.filePath, 'utf-8');
      const parsed = JSON.parse(raw) as CacheData;
      if (parsed && parsed.version === 1 && parsed.entries) {
        this.data = parsed;
      }
    } catch {
      this.data = { version: 1, entries: {} };
    }
  }

  private persist(): void {
    const dir = dirname(this.filePath);
    if (!existsSync(dir)) {
      mkdirSync(dir, { recursive: true });
    }
    writeFileSync(this.filePath, JSON.stringify(this.data, null, 2), 'utf-8');
  }
}
