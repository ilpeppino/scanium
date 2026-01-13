import { describe, it, expect, beforeAll, afterAll, beforeEach } from 'vitest';
import { FastifyInstance } from 'fastify';
import { buildApp } from '../../app.js';
import { prisma } from '../../infra/db/prisma.js';
import { Config } from '../../config/index.js';

describe('Items API', () => {
  let app: FastifyInstance;
  let authToken: string;
  let userId: string;
  let otherUserId: string;
  let otherAuthToken: string;

  // Mock config for testing
  const mockConfig: Config = {
    nodeEnv: 'test',
    port: 3000,
    publicBaseUrl: 'http://localhost:3000',
    databaseUrl: 'postgresql://user:pass@localhost:5432/testdb',
    security: {
      enforceHttps: false,
      enableHsts: false,
      allowInsecureLocalHttp: true,
      apiKeyRotationEnabled: false,
      apiKeyExpirationDays: 90,
      logApiKeyUsage: false,
    },
    ebay: {
      env: 'sandbox',
      clientId: 'test-client-id',
      clientSecret: 'test-client-secret',
      scopes: ['test-scope'],
      tokenEncryptionKey: 'x'.repeat(32),
      redirectPath: '/auth/ebay/callback',
    },
    sessionSigningSecret: 'x'.repeat(64),
    corsOrigins: ['http://localhost:3000'],
    classifier: {
      provider: 'mock',
      apiKeys: ['test-key'],
      domainPackId: 'test',
      domainPackPath: 'test.json',
      maxUploadBytes: 5242880,
      rateLimitPerMinute: 60,
      concurrencyLimit: 2,
      retainUploads: false,
      mockSeed: 'test',
    },
    vision: {
      enabled: true,
      provider: 'mock',
      enableOcr: true,
      enableLabels: true,
      enableLogos: true,
      enableColors: true,
      ocrMode: 'TEXT_DETECTION',
      timeoutMs: 10000,
      maxRetries: 2,
      cacheTtlSeconds: 3600,
      cacheMaxEntries: 100,
    },
    assistant: {
      provider: 'mock',
      apiKeys: ['test-key'],
      allowEmptyItems: false,
    },
    auth: {
      googleClientId: 'test-google-client-id.apps.googleusercontent.com',
      sessionSecret: 'test-session-secret',
      sessionExpirySeconds: 60,
      refreshTokenExpirySeconds: 120,
    },
    pricing: {
      enabled: false,
      timeoutMs: 6000,
      cacheTtlSeconds: 21600,
      catalogPath: 'config/marketplaces/marketplaces.eu.json',
      dailyQuota: 30,
      maxResults: 5,
      openaiModel: 'gpt-4o-mini',
    },
    admin: {
      enabled: false,
    },
  };

  beforeAll(async () => {
    app = await buildApp(mockConfig);
    await app.ready();

    // Create test user 1
    const user1 = await prisma.user.create({
      data: {
        email: 'test-items@example.com',
        googleId: 'google-items-123',
        displayName: 'Test Items User',
      },
    });
    userId = user1.id;
    authToken = app.jwt.sign({ userId: user1.id, email: user1.email });

    // Create test user 2 (for ownership tests)
    const user2 = await prisma.user.create({
      data: {
        email: 'test-items-other@example.com',
        googleId: 'google-items-456',
        displayName: 'Other Items User',
      },
    });
    otherUserId = user2.id;
    otherAuthToken = app.jwt.sign({ userId: user2.id, email: user2.email });
  });

  afterAll(async () => {
    // Cleanup test data
    if (userId && otherUserId) {
      await prisma.item.deleteMany({ where: { userId: { in: [userId, otherUserId] } } });
      await prisma.user.deleteMany({ where: { id: { in: [userId, otherUserId] } } });
    }
    if (app) {
      await app.close();
    }
  });

  beforeEach(async () => {
    // Clean up items before each test
    await prisma.item.deleteMany({ where: { userId: { in: [userId, otherUserId] } } });
  });

  describe('POST /v1/items', () => {
    it('should create item for authenticated user', async () => {
      const itemData = {
        localId: 'local-123',
        title: 'Test Item',
        description: 'Test description',
        category: 'Electronics',
        clientUpdatedAt: new Date().toISOString(),
      };

      const response = await app.inject({
        method: 'POST',
        url: '/v1/items',
        headers: {
          authorization: `Bearer ${authToken}`,
        },
        payload: itemData,
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.item).toBeDefined();
      expect(body.item.title).toBe('Test Item');
      expect(body.item.userId).toBe(userId);
      expect(body.item.syncVersion).toBe(1);
      expect(body.localId).toBe('local-123');
      expect(body.correlationId).toBeDefined();
    });

    it('should reject unauthenticated request', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/items',
        payload: {
          localId: 'local-456',
          title: 'Unauthorized Item',
        },
      });

      expect(response.statusCode).toBe(401);
    });

    it('should validate required fields', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/items',
        headers: {
          authorization: `Bearer ${authToken}`,
        },
        payload: {
          // Missing localId and clientUpdatedAt
          title: 'Invalid Item',
        },
      });

      expect(response.statusCode).toBe(400);
    });
  });

  describe('GET /v1/items', () => {
    beforeEach(async () => {
      // Create test items
      await prisma.item.createMany({
        data: [
          {
            userId,
            title: 'Item 1',
            syncVersion: 1,
            clientUpdatedAt: new Date('2025-01-01'),
          },
          {
            userId,
            title: 'Item 2',
            syncVersion: 1,
            clientUpdatedAt: new Date('2025-01-02'),
          },
          {
            userId: otherUserId,
            title: 'Other User Item',
            syncVersion: 1,
            clientUpdatedAt: new Date('2025-01-03'),
          },
        ],
      });
    });

    it('should return only user\'s items', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/v1/items',
        headers: {
          authorization: `Bearer ${authToken}`,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.items).toHaveLength(2);
      expect(body.items.every((item: any) => item.userId === userId)).toBe(true);
      expect(body.correlationId).toBeDefined();
    });

    it('should support incremental sync with since parameter', async () => {
      const sinceDate = new Date('2025-01-01T12:00:00Z');

      const response = await app.inject({
        method: 'GET',
        url: `/v1/items?since=${sinceDate.toISOString()}`,
        headers: {
          authorization: `Bearer ${authToken}`,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      // Should only return items updated after sinceDate (Item 2)
      expect(body.items.length).toBeGreaterThan(0);
      expect(
        body.items.every((item: any) => new Date(item.updatedAt) >= sinceDate)
      ).toBe(true);
    });

    it('should support pagination with limit', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/v1/items?limit=1',
        headers: {
          authorization: `Bearer ${authToken}`,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.items).toHaveLength(1);
      expect(body.hasMore).toBe(true);
      expect(body.nextSince).toBeDefined();
    });

    it('should include deleted items with includeDeleted=true', async () => {
      // Create a deleted item
      await prisma.item.create({
        data: {
          userId,
          title: 'Deleted Item',
          syncVersion: 1,
          deletedAt: new Date(),
          clientUpdatedAt: new Date(),
        },
      });

      const response = await app.inject({
        method: 'GET',
        url: '/v1/items?includeDeleted=true',
        headers: {
          authorization: `Bearer ${authToken}`,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.items.length).toBeGreaterThan(2); // 2 active + 1 deleted
      expect(body.items.some((item: any) => item.deletedAt !== null)).toBe(true);
    });
  });

  describe('GET /v1/items/:id', () => {
    let itemId: string;

    beforeEach(async () => {
      const item = await prisma.item.create({
        data: {
          userId,
          title: 'Single Item',
          syncVersion: 1,
          clientUpdatedAt: new Date(),
        },
      });
      itemId = item.id;
    });

    it('should fetch single item', async () => {
      const response = await app.inject({
        method: 'GET',
        url: `/v1/items/${itemId}`,
        headers: {
          authorization: `Bearer ${authToken}`,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.item.id).toBe(itemId);
      expect(body.item.title).toBe('Single Item');
      expect(body.correlationId).toBeDefined();
    });

    it('should enforce ownership', async () => {
      const response = await app.inject({
        method: 'GET',
        url: `/v1/items/${itemId}`,
        headers: {
          authorization: `Bearer ${otherAuthToken}`,
        },
      });

      expect(response.statusCode).toBe(403);
    });

    it('should return 404 for non-existent item', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/v1/items/non-existent-id',
        headers: {
          authorization: `Bearer ${authToken}`,
        },
      });

      expect(response.statusCode).toBe(404);
    });
  });

  describe('PATCH /v1/items/:id', () => {
    let itemId: string;

    beforeEach(async () => {
      const item = await prisma.item.create({
        data: {
          userId,
          title: 'Original Title',
          syncVersion: 1,
          clientUpdatedAt: new Date(),
        },
      });
      itemId = item.id;
    });

    it('should update item with matching syncVersion', async () => {
      const response = await app.inject({
        method: 'PATCH',
        url: `/v1/items/${itemId}`,
        headers: {
          authorization: `Bearer ${authToken}`,
        },
        payload: {
          title: 'Updated Title',
          syncVersion: 1,
          clientUpdatedAt: new Date().toISOString(),
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.item.title).toBe('Updated Title');
      expect(body.item.syncVersion).toBe(2);
      expect(body.correlationId).toBeDefined();
    });

    it('should return 409 on syncVersion mismatch', async () => {
      const response = await app.inject({
        method: 'PATCH',
        url: `/v1/items/${itemId}`,
        headers: {
          authorization: `Bearer ${authToken}`,
        },
        payload: {
          title: 'Conflict Update',
          syncVersion: 999, // Wrong version
          clientUpdatedAt: new Date().toISOString(),
        },
      });

      expect(response.statusCode).toBe(409);
    });

    it('should enforce ownership on update', async () => {
      const response = await app.inject({
        method: 'PATCH',
        url: `/v1/items/${itemId}`,
        headers: {
          authorization: `Bearer ${otherAuthToken}`,
        },
        payload: {
          title: 'Unauthorized Update',
          syncVersion: 1,
          clientUpdatedAt: new Date().toISOString(),
        },
      });

      expect(response.statusCode).toBe(403);
    });
  });

  describe('DELETE /v1/items/:id', () => {
    let itemId: string;

    beforeEach(async () => {
      const item = await prisma.item.create({
        data: {
          userId,
          title: 'To Delete',
          syncVersion: 1,
          clientUpdatedAt: new Date(),
        },
      });
      itemId = item.id;
    });

    it('should soft delete item', async () => {
      const response = await app.inject({
        method: 'DELETE',
        url: `/v1/items/${itemId}`,
        headers: {
          authorization: `Bearer ${authToken}`,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.item.deletedAt).toBeDefined();
      expect(body.item.syncVersion).toBe(2);
      expect(body.correlationId).toBeDefined();

      // Verify item still exists in DB
      const dbItem = await prisma.item.findUnique({ where: { id: itemId } });
      expect(dbItem).toBeDefined();
      expect(dbItem?.deletedAt).toBeDefined();
    });

    it('should enforce ownership on delete', async () => {
      const response = await app.inject({
        method: 'DELETE',
        url: `/v1/items/${itemId}`,
        headers: {
          authorization: `Bearer ${otherAuthToken}`,
        },
      });

      expect(response.statusCode).toBe(403);
    });
  });

  describe('POST /v1/items/sync', () => {
    it('should handle batch sync with no conflicts', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/v1/items/sync',
        headers: {
          authorization: `Bearer ${authToken}`,
        },
        payload: {
          clientTimestamp: new Date().toISOString(),
          lastSyncTimestamp: null,
          changes: [
            {
              action: 'CREATE',
              localId: 'local-1',
              clientUpdatedAt: new Date().toISOString(),
              data: {
                title: 'Synced Item 1',
                category: 'Books',
              },
            },
            {
              action: 'CREATE',
              localId: 'local-2',
              clientUpdatedAt: new Date().toISOString(),
              data: {
                title: 'Synced Item 2',
                category: 'Electronics',
              },
            },
          ],
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.results).toHaveLength(2);
      expect(body.results.every((r: any) => r.status === 'SUCCESS')).toBe(true);
      expect(body.serverChanges).toBeDefined();
      expect(body.syncTimestamp).toBeDefined();
      expect(body.correlationId).toBeDefined();
    });

    it('should handle UPDATE action', async () => {
      // Create item first
      const item = await prisma.item.create({
        data: {
          userId,
          title: 'Original',
          syncVersion: 1,
          clientUpdatedAt: new Date(),
        },
      });

      const response = await app.inject({
        method: 'POST',
        url: '/v1/items/sync',
        headers: {
          authorization: `Bearer ${authToken}`,
        },
        payload: {
          clientTimestamp: new Date().toISOString(),
          lastSyncTimestamp: null,
          changes: [
            {
              action: 'UPDATE',
              localId: 'local-1',
              serverId: item.id,
              syncVersion: 1,
              clientUpdatedAt: new Date().toISOString(),
              data: {
                title: 'Updated via Sync',
              },
            },
          ],
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.results[0].status).toBe('SUCCESS');
      expect(body.results[0].item.title).toBe('Updated via Sync');
    });

    it('should handle DELETE action', async () => {
      const item = await prisma.item.create({
        data: {
          userId,
          title: 'To Delete via Sync',
          syncVersion: 1,
          clientUpdatedAt: new Date(),
        },
      });

      const response = await app.inject({
        method: 'POST',
        url: '/v1/items/sync',
        headers: {
          authorization: `Bearer ${authToken}`,
        },
        payload: {
          clientTimestamp: new Date().toISOString(),
          lastSyncTimestamp: null,
          changes: [
            {
              action: 'DELETE',
              localId: 'local-1',
              serverId: item.id,
              syncVersion: 1,
              clientUpdatedAt: new Date().toISOString(),
            },
          ],
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.results[0].status).toBe('SUCCESS');
      expect(body.results[0].item.deletedAt).toBeDefined();
    });

    it('should handle conflicts with syncVersion mismatch', async () => {
      const item = await prisma.item.create({
        data: {
          userId,
          title: 'Conflict Item',
          syncVersion: 2, // Server has version 2
          clientUpdatedAt: new Date(),
        },
      });

      const response = await app.inject({
        method: 'POST',
        url: '/v1/items/sync',
        headers: {
          authorization: `Bearer ${authToken}`,
        },
        payload: {
          clientTimestamp: new Date().toISOString(),
          lastSyncTimestamp: null,
          changes: [
            {
              action: 'UPDATE',
              localId: 'local-1',
              serverId: item.id,
              syncVersion: 1, // Client has version 1
              clientUpdatedAt: new Date().toISOString(),
              data: {
                title: 'Conflict Update',
              },
            },
          ],
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.results[0].status).toBe('CONFLICT');
      expect(body.results[0].conflictResolution).toBeDefined();
    });

    it('should return server changes for incremental sync', async () => {
      // Create items on server
      await prisma.item.createMany({
        data: [
          {
            userId,
            title: 'Server Item 1',
            syncVersion: 1,
            clientUpdatedAt: new Date('2025-01-10'),
          },
          {
            userId,
            title: 'Server Item 2',
            syncVersion: 1,
            clientUpdatedAt: new Date('2025-01-11'),
          },
        ],
      });

      const response = await app.inject({
        method: 'POST',
        url: '/v1/items/sync',
        headers: {
          authorization: `Bearer ${authToken}`,
        },
        payload: {
          clientTimestamp: new Date().toISOString(),
          lastSyncTimestamp: new Date('2025-01-09').toISOString(),
          changes: [],
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.serverChanges.length).toBeGreaterThanOrEqual(2);
    });
  });
});
