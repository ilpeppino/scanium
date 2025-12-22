import { ClassificationResult } from './types.js';

type CacheEntry = {
  value: ClassificationResult;
  expiresAt: number;
  insertedAt: number;
};

export class ClassifierCache {
  private readonly store = new Map<string, CacheEntry>();

  constructor(
    private readonly ttlMs: number,
    private readonly maxEntries: number
  ) {}

  get(key: string): ClassificationResult | null {
    const entry = this.store.get(key);
    if (!entry) return null;
    if (entry.expiresAt <= Date.now()) {
      this.store.delete(key);
      return null;
    }
    return entry.value;
  }

  set(key: string, value: ClassificationResult): void {
    const now = Date.now();
    this.store.set(key, {
      value,
      insertedAt: now,
      expiresAt: now + this.ttlMs,
    });
    this.prune();
  }

  private prune(): void {
    if (this.store.size <= this.maxEntries) return;
    const entries = [...this.store.entries()].sort(
      (a, b) => a[1].insertedAt - b[1].insertedAt
    );
    const toRemove = entries.slice(0, this.store.size - this.maxEntries);
    toRemove.forEach(([key]) => this.store.delete(key));
  }
}
