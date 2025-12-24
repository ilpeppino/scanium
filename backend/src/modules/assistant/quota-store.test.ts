import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { DailyQuotaStore } from './quota-store.js';

describe('DailyQuotaStore', () => {
  let store: DailyQuotaStore;

  beforeEach(() => {
    store = new DailyQuotaStore({ dailyLimit: 10, cleanupIntervalMs: 1000000 });
  });

  afterEach(() => {
    store.stop();
  });

  describe('consume', () => {
    it('allows requests within quota', () => {
      const result = store.consume('user-1');
      expect(result.allowed).toBe(true);
      expect(result.remaining).toBe(9);
    });

    it('decrements remaining count correctly', () => {
      store.consume('user-1');
      store.consume('user-1');
      store.consume('user-1');

      const result = store.consume('user-1');
      expect(result.allowed).toBe(true);
      expect(result.remaining).toBe(6);
    });

    it('blocks requests when quota exceeded', () => {
      // Consume all 10 allowed requests
      for (let i = 0; i < 10; i++) {
        const result = store.consume('user-1');
        expect(result.allowed).toBe(true);
      }

      // 11th request should be blocked
      const result = store.consume('user-1');
      expect(result.allowed).toBe(false);
      expect(result.remaining).toBe(0);
    });

    it('tracks different keys separately', () => {
      for (let i = 0; i < 10; i++) {
        store.consume('user-1');
      }

      // user-1 is blocked
      expect(store.consume('user-1').allowed).toBe(false);

      // user-2 still has quota - first consume returns remaining 9
      const user2First = store.consume('user-2');
      expect(user2First.allowed).toBe(true);
      expect(user2First.remaining).toBe(9);

      // Second consume for user-2 returns remaining 8
      const user2Second = store.consume('user-2');
      expect(user2Second.allowed).toBe(true);
      expect(user2Second.remaining).toBe(8);
    });

    it('returns reset timestamp in the future', () => {
      const result = store.consume('user-1');
      expect(result.resetAt).toBeGreaterThan(Date.now());
    });
  });

  describe('check', () => {
    it('returns quota status without consuming', () => {
      const initial = store.check('user-1');
      expect(initial.allowed).toBe(true);
      expect(initial.remaining).toBe(10);

      // Consume some
      store.consume('user-1');
      store.consume('user-1');

      // Check again - remaining should reflect consumed
      const after = store.check('user-1');
      expect(after.allowed).toBe(true);
      expect(after.remaining).toBe(8);

      // Check doesn't consume
      const afterCheck = store.check('user-1');
      expect(afterCheck.remaining).toBe(8);
    });

    it('returns full quota for unknown keys', () => {
      const result = store.check('unknown-user');
      expect(result.allowed).toBe(true);
      expect(result.remaining).toBe(10);
    });
  });

  describe('getUsage', () => {
    it('returns usage statistics', () => {
      store.consume('user-1');
      store.consume('user-1');
      store.consume('user-1');

      const usage = store.getUsage('user-1');
      expect(usage.used).toBe(3);
      expect(usage.limit).toBe(10);
    });

    it('returns zero usage for unknown keys', () => {
      const usage = store.getUsage('unknown-user');
      expect(usage.used).toBe(0);
      expect(usage.limit).toBe(10);
    });
  });

  describe('cleanup', () => {
    it('removes expired entries', () => {
      // Mock time to make entries expire immediately
      const realDateNow = Date.now;
      const now = Date.now();

      // Create an entry
      store.consume('user-1');
      expect(store.size()).toBe(1);

      // Advance time past reset
      vi.spyOn(Date, 'now').mockImplementation(() => now + 25 * 60 * 60 * 1000); // 25 hours later

      const removed = store.cleanup();
      expect(removed).toBe(1);
      expect(store.size()).toBe(0);

      // Restore
      vi.restoreAllMocks();
    });
  });

  describe('clear', () => {
    it('removes all entries', () => {
      store.consume('user-1');
      store.consume('user-2');
      store.consume('user-3');
      expect(store.size()).toBe(3);

      store.clear();
      expect(store.size()).toBe(0);
    });
  });

  describe('stop', () => {
    it('stops the cleanup timer', () => {
      // Should not throw
      store.stop();
      store.stop(); // Should be safe to call multiple times
    });
  });
});
