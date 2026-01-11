import { describe, expect, it, beforeAll, afterAll } from 'vitest';
import { buildApp } from '../../app.js';
import type { FastifyInstance } from 'fastify';
import { Config } from '../../config/index.js';

describe('Marketplaces Routes', () => {
  let app: FastifyInstance;
  let testConfig: Config;
  const TEST_API_KEY = 'test-api-key-12345';

  beforeAll(async () => {
    // Create minimal test config
    testConfig = {
      nodeEnv: 'test',
      port: 3000,
      publicBaseUrl: 'http://localhost:3000',
      databaseUrl: 'file:./test.db',
      classifier: {
        provider: 'mock',
        visionFeature: ['LABEL_DETECTION'],
        maxUploadBytes: 5 * 1024 * 1024,
        rateLimitPerMinute: 60,
        ipRateLimitPerMinute: 60,
        rateLimitWindowSeconds: 60,
        rateLimitBackoffSeconds: 30,
        rateLimitBackoffMaxSeconds: 900,
        concurrentLimit: 2,
        apiKeys: [TEST_API_KEY],
        domainPackId: 'home_resale',
        domainPackPath: 'src/modules/classifier/domain/home-resale.json',
        retainUploads: false,
        mockSeed: 'test',
        visionTimeoutMs: 10000,
        visionMaxRetries: 2,
        cacheTtlSeconds: 300,
        cacheMaxEntries: 1000,
        circuitBreakerFailureThreshold: 5,
        circuitBreakerCooldownSeconds: 60,
        circuitBreakerMinimumRequests: 3,
        enableAttributeEnrichment: true,
      },
      assistant: {
        provider: 'mock',
        apiKeys: [TEST_API_KEY],
        rateLimitPerMinute: 10,
        rateLimitWindowSeconds: 60,
        maxConversationHistory: 10,
        maxVisionImages: 3,
        quotaConfig: { enabled: false, dailyLimit: 100, resetAtUtc: 0 },
        debugMode: false,
        logVisionRequests: false,
        enableVisionExtractionCache: true,
        visionCacheTtlSeconds: 3600,
        enableResponseCache: false,
        responseCacheTtlSeconds: 600,
      },
      vision: {
        provider: 'mock',
        enableOcr: true,
        ocrMode: 'TEXT_DETECTION',
        enableLabels: true,
        enableLogos: true,
        enableColors: true,
        maxOcrSnippets: 10,
        maxLabelHints: 10,
        maxLogoHints: 5,
        maxColors: 5,
        maxOcrSnippetLength: 100,
        minOcrConfidence: 0.5,
        minLabelConfidence: 0.5,
        minLogoConfidence: 0.5,
        timeoutMs: 10000,
        maxRetries: 2,
        cacheTtlSeconds: 3600,
        cacheMaxEntries: 500,
      },
      security: {
        enforceHttps: false,
        logApiKeyUsage: false,
      },
      cors: {
        allowedOrigins: ['http://localhost:3000'],
        allowCredentials: true,
      },
      csrf: {
        enabled: false,
        ignoredPaths: [],
      },
      sessionSigningSecret: 'test-secret-key-min-32-chars-long-12345',
      ebay: {
        env: 'SANDBOX',
        appId: 'test',
        certId: 'test',
        devId: 'test',
        redirectUrl: 'http://localhost:3000/auth/ebay/callback',
      },
    } as Config;

    app = await buildApp(testConfig);
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
  });

  describe('GET /v1/marketplaces/countries', () => {
    it('returns 401 when API key is missing', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/v1/marketplaces/countries',
      });

      expect(response.statusCode).toBe(401);
      const body = JSON.parse(response.body);
      expect(body.error.code).toBe('UNAUTHORIZED');
    });

    it('returns 401 when API key is invalid', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/v1/marketplaces/countries',
        headers: {
          'x-api-key': 'invalid-key',
        },
      });

      expect(response.statusCode).toBe(401);
      const body = JSON.parse(response.body);
      expect(body.error.code).toBe('UNAUTHORIZED');
    });

    it('returns 200 with country list when API key is valid', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/v1/marketplaces/countries',
        headers: {
          'x-api-key': TEST_API_KEY,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.countries).toBeDefined();
      expect(Array.isArray(body.countries)).toBe(true);
      expect(body.countries.length).toBeGreaterThan(0);
      // Should include common EU countries
      expect(body.countries).toContain('NL');
      expect(body.countries).toContain('DE');
    });
  });

  describe('GET /v1/marketplaces/:countryCode', () => {
    it('returns 401 when API key is missing', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/v1/marketplaces/NL',
      });

      expect(response.statusCode).toBe(401);
    });

    it('returns 401 when API key is invalid', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/v1/marketplaces/NL',
        headers: {
          'x-api-key': 'invalid-key',
        },
      });

      expect(response.statusCode).toBe(401);
    });

    it('returns 200 with marketplaces for known country', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/v1/marketplaces/NL',
        headers: {
          'x-api-key': TEST_API_KEY,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.countryCode).toBe('NL');
      expect(body.defaultCurrency).toBe('EUR');
      expect(body.marketplaces).toBeDefined();
      expect(Array.isArray(body.marketplaces)).toBe(true);
      expect(body.marketplaces.length).toBeGreaterThan(0);

      // Verify marketplace structure
      const firstMarketplace = body.marketplaces[0];
      expect(firstMarketplace.id).toBeDefined();
      expect(firstMarketplace.name).toBeDefined();
      expect(firstMarketplace.domains).toBeDefined();
      expect(firstMarketplace.type).toBeDefined();
    });

    it('returns 404 for unknown country', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/v1/marketplaces/US',
        headers: {
          'x-api-key': TEST_API_KEY,
        },
      });

      expect(response.statusCode).toBe(404);
      const body = JSON.parse(response.body);
      expect(body.error.code).toBe('NOT_FOUND');
    });

    it('normalizes country code case', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/v1/marketplaces/nl', // lowercase
        headers: {
          'x-api-key': TEST_API_KEY,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.countryCode).toBe('NL'); // normalized to uppercase
    });

    it('returns 400 for invalid country code format', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/v1/marketplaces/USA', // 3 characters instead of 2
        headers: {
          'x-api-key': TEST_API_KEY,
        },
      });

      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.body);
      expect(body.error.code).toBe('INVALID_COUNTRY_CODE');
    });
  });
});
