import { describe, it, expect, beforeEach } from 'vitest';
import { ApiKeyManager } from './api-key-manager.js';

describe('ApiKeyManager', () => {
  let manager: ApiKeyManager;

  beforeEach(() => {
    manager = new ApiKeyManager(['test-key-1', 'test-key-2']);
  });

  describe('validateKey', () => {
    it('should validate existing keys', () => {
      expect(manager.validateKey('test-key-1')).toBe(true);
      expect(manager.validateKey('test-key-2')).toBe(true);
    });

    it('should reject non-existent keys', () => {
      expect(manager.validateKey('non-existent-key')).toBe(false);
    });

    it('should reject empty keys', () => {
      expect(manager.validateKey('')).toBe(false);
    });

    it('should reject revoked keys', () => {
      manager.revokeKey('test-key-1');
      expect(manager.validateKey('test-key-1')).toBe(false);
    });

    it('should reject expired keys', () => {
      const key = manager.createKey('test-key', -1); // Already expired
      expect(manager.validateKey(key.key)).toBe(false);
    });
  });

  describe('createKey', () => {
    it('should create a new key with metadata', () => {
      const metadata = manager.createKey('new-key', 30);
      expect(metadata.key).toBeTruthy();
      expect(metadata.name).toBe('new-key');
      expect(metadata.isActive).toBe(true);
      expect(metadata.expiresAt).toBeTruthy();
    });

    it('should create key without expiration', () => {
      const metadata = manager.createKey('permanent-key', null);
      expect(metadata.expiresAt).toBeNull();
    });

    it('should generate unique keys', () => {
      const key1 = manager.createKey('key1', 30);
      const key2 = manager.createKey('key2', 30);
      expect(key1.key).not.toBe(key2.key);
    });
  });

  describe('rotateKey', () => {
    it('should rotate an existing key', () => {
      const oldKey = 'test-key-1';
      const newMetadata = manager.rotateKey(oldKey, 30);

      expect(newMetadata).toBeTruthy();
      expect(newMetadata?.key).not.toBe(oldKey);
      expect(newMetadata?.rotatedFrom).toBe(oldKey);
      expect(manager.validateKey(newMetadata!.key)).toBe(true);
    });

    it('should mark old key for deprecation', () => {
      const oldKey = 'test-key-1';
      const oldMetadata = manager.getKeyMetadata(oldKey);
      expect(oldMetadata?.expiresAt).toBeNull();

      manager.rotateKey(oldKey, 30);

      const updatedMetadata = manager.getKeyMetadata(oldKey);
      expect(updatedMetadata?.expiresAt).toBeTruthy();
    });

    it('should return null for non-existent key', () => {
      const result = manager.rotateKey('non-existent', 30);
      expect(result).toBeNull();
    });
  });

  describe('revokeKey', () => {
    it('should revoke an active key', () => {
      expect(manager.validateKey('test-key-1')).toBe(true);
      manager.revokeKey('test-key-1');
      expect(manager.validateKey('test-key-1')).toBe(false);
    });

    it('should return false for non-existent key', () => {
      expect(manager.revokeKey('non-existent')).toBe(false);
    });

    it('should mark key as inactive and expired', () => {
      manager.revokeKey('test-key-1');
      const metadata = manager.getKeyMetadata('test-key-1');
      expect(metadata?.isActive).toBe(false);
      expect(metadata?.expiresAt).toBeTruthy();
    });
  });

  describe('logUsage', () => {
    it('should log API key usage events', () => {
      manager.logUsage({
        apiKey: 'test-key-1',
        timestamp: new Date(),
        endpoint: '/v1/classify',
        method: 'POST',
        success: true,
        ip: '127.0.0.1',
      });

      const stats = manager.getKeyUsageStats('test-key-1');
      expect(stats.totalRequests).toBe(1);
      expect(stats.successfulRequests).toBe(1);
      expect(stats.failedRequests).toBe(0);
    });

    it('should track successful and failed requests separately', () => {
      manager.logUsage({
        apiKey: 'test-key-1',
        timestamp: new Date(),
        endpoint: '/v1/classify',
        method: 'POST',
        success: true,
        ip: '127.0.0.1',
      });

      manager.logUsage({
        apiKey: 'test-key-1',
        timestamp: new Date(),
        endpoint: '/v1/classify',
        method: 'POST',
        success: false,
        ip: '127.0.0.1',
        errorCode: 'VALIDATION_ERROR',
      });

      const stats = manager.getKeyUsageStats('test-key-1');
      expect(stats.totalRequests).toBe(2);
      expect(stats.successfulRequests).toBe(1);
      expect(stats.failedRequests).toBe(1);
    });

    it('should track last used timestamp', () => {
      const timestamp = new Date();
      manager.logUsage({
        apiKey: 'test-key-1',
        timestamp,
        endpoint: '/v1/classify',
        method: 'POST',
        success: true,
        ip: '127.0.0.1',
      });

      const stats = manager.getKeyUsageStats('test-key-1');
      expect(stats.lastUsed).toEqual(timestamp);
    });

    it('should return recent failures', () => {
      // Log some failed requests
      for (let i = 0; i < 5; i++) {
        manager.logUsage({
          apiKey: 'test-key-1',
          timestamp: new Date(),
          endpoint: '/v1/classify',
          method: 'POST',
          success: false,
          ip: '127.0.0.1',
          errorCode: `ERROR_${i}`,
        });
      }

      const stats = manager.getKeyUsageStats('test-key-1');
      expect(stats.recentFailures).toHaveLength(5);
    });
  });

  describe('listKeys', () => {
    it('should list all keys without exposing full key values', () => {
      const keys = manager.listKeys();
      expect(keys.length).toBeGreaterThan(0);
      keys.forEach((key) => {
        expect(key.keyPrefix).toContain('...');
        expect(key.name).toBeTruthy();
      });
    });

    it('should include key metadata', () => {
      const keys = manager.listKeys();
      keys.forEach((key) => {
        expect(key.createdAt).toBeInstanceOf(Date);
        expect(key.isActive).toBeDefined();
      });
    });
  });

  describe('cleanupExpiredKeys', () => {
    it('should mark expired keys as inactive', () => {
      const expiredKey = manager.createKey('expired-key', -1); // Already expired
      expect(manager.validateKey(expiredKey.key)).toBe(false);

      const removed = manager.cleanupExpiredKeys();
      expect(removed).toBeGreaterThan(0);

      const metadata = manager.getKeyMetadata(expiredKey.key);
      expect(metadata?.isActive).toBe(false);
    });

    it('should not affect active keys', () => {
      const activeKey = manager.createKey('active-key', 30);
      manager.cleanupExpiredKeys();

      expect(manager.validateKey(activeKey.key)).toBe(true);
      const metadata = manager.getKeyMetadata(activeKey.key);
      expect(metadata?.isActive).toBe(true);
    });
  });

  describe('generateKey', () => {
    it('should generate cryptographically secure keys', () => {
      const key1 = manager.generateKey();
      const key2 = manager.generateKey();

      // Keys should be base64url encoded (alphanumeric + - and _)
      expect(key1).toMatch(/^[A-Za-z0-9_-]+$/);
      expect(key2).toMatch(/^[A-Za-z0-9_-]+$/);

      // Keys should be unique
      expect(key1).not.toBe(key2);

      // Keys should be 32 bytes = 43 chars in base64url (without padding)
      expect(key1.length).toBeGreaterThanOrEqual(40);
      expect(key2.length).toBeGreaterThanOrEqual(40);
    });
  });
});
