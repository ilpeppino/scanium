import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Config } from '../../../config/index.js';
import { getEbayConnectionStatus, storeEbayTokens } from './token-storage.js';

const mockPrisma = vi.hoisted(() => ({
  user: {
    findUnique: vi.fn(),
    create: vi.fn(),
  },
  ebayConnection: {
    upsert: vi.fn(),
    findUnique: vi.fn(),
  },
  $queryRaw: vi.fn(),
  $executeRaw: vi.fn(),
}));

const encryptSecretMock = vi.hoisted(() => vi.fn((value: string) => `encrypted:${value}`));

vi.mock('../../../infra/db/prisma.js', () => ({
  prisma: mockPrisma,
}));

vi.mock('../../../shared/security/secret-crypto.js', () => ({
  encryptSecret: encryptSecretMock,
}));

const baseConfig: Config = {
  nodeEnv: 'test',
  port: 3000,
  publicBaseUrl: 'http://localhost:3000',
  databaseUrl: 'postgresql://user:pass@localhost:5432/db',
  classifier: {
    provider: 'mock',
    visionFeature: 'LABEL_DETECTION',
    maxUploadBytes: 1024,
    rateLimitPerMinute: 60,
    ipRateLimitPerMinute: 60,
    rateLimitWindowSeconds: 60,
    rateLimitBackoffSeconds: 30,
    rateLimitBackoffMaxSeconds: 900,
    rateLimitRedisUrl: undefined,
    concurrentLimit: 2,
    apiKeys: ['dev-key'],
    domainPackId: 'home_resale',
    domainPackPath: 'src/modules/classifier/domain/home-resale.json',
    retainUploads: false,
    mockSeed: 'scanium-mock',
    visionTimeoutMs: 10000,
    visionMaxRetries: 2,
  },
  googleCredentialsPath: '/tmp/creds.json',
  ebay: {
    env: 'sandbox',
    clientId: 'client-id',
    clientSecret: 'client-secret',
    redirectPath: '/auth/ebay/callback',
    scopes: 'scope-1 scope-2',
    tokenEncryptionKey: '12345678901234567890123456789012',
  },
  sessionSigningSecret: 'a'.repeat(64),
  security: {
    enforceHttps: true,
    enableHsts: true,
    apiKeyRotationEnabled: true,
    apiKeyExpirationDays: 90,
    logApiKeyUsage: true,
  },
  corsOrigins: ['http://localhost:3000'],
};

const testTokens = {
  accessToken: "abc123'; DROP TABLE users;--",
  refreshToken: 'refresh-abc123',
  expiresIn: 3600,
  tokenType: 'Bearer',
  scope: 'read write',
};

describe('token-storage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockPrisma.user.findUnique.mockResolvedValue(undefined);
    mockPrisma.user.create.mockResolvedValue({ id: 'user-1' });
    mockPrisma.ebayConnection.upsert.mockResolvedValue({});
    mockPrisma.ebayConnection.findUnique.mockResolvedValue(null);
  });

  it('stores tokens via Prisma upsert without using raw SQL helpers', async () => {
    await storeEbayTokens(baseConfig, testTokens);

    expect(mockPrisma.$queryRaw).not.toHaveBeenCalled();
    expect(mockPrisma.$executeRaw).not.toHaveBeenCalled();
    expect(mockPrisma.ebayConnection.upsert).toHaveBeenCalledWith(
      expect.objectContaining({
        create: expect.objectContaining({
          accessToken: 'encrypted:abc123\'; DROP TABLE users;--',
          refreshToken: 'encrypted:refresh-abc123',
          scopes: testTokens.scope,
        }),
        update: expect.objectContaining({
          accessToken: 'encrypted:abc123\'; DROP TABLE users;--',
          refreshToken: 'encrypted:refresh-abc123',
        }),
      })
    );
  });

  it('returns disconnected status when no connection exists', async () => {
    const status = await getEbayConnectionStatus(baseConfig);

    expect(status).toEqual({ connected: false });
    expect(mockPrisma.$queryRaw).not.toHaveBeenCalled();
    expect(mockPrisma.$executeRaw).not.toHaveBeenCalled();
  });

  it('returns connection details when found', async () => {
    mockPrisma.user.findUnique.mockResolvedValue({ id: 'user-1' });
    mockPrisma.ebayConnection.findUnique.mockResolvedValue({
      environment: 'sandbox',
      scopes: 'scope-1 scope-2',
      expiresAt: new Date('2024-01-01T00:00:00Z'),
    });

    const status = await getEbayConnectionStatus(baseConfig);

    expect(status.connected).toBe(true);
    expect(status.environment).toBe('sandbox');
    expect(status.scopes).toBe('scope-1 scope-2');
    expect(mockPrisma.$queryRaw).not.toHaveBeenCalled();
    expect(mockPrisma.$executeRaw).not.toHaveBeenCalled();
  });
});
