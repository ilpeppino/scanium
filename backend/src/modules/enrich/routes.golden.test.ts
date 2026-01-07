/**
 * Golden tests for enrichment routes.
 *
 * These tests require:
 * - GOOGLE_APPLICATION_CREDENTIALS env var pointing to valid credentials
 * - Golden test images in ../tests/golden_images/
 *
 * The tests verify the full enrichment pipeline:
 * 1. Submit image for enrichment (POST /v1/items/enrich)
 * 2. Poll for completion (GET /v1/items/enrich/status/:requestId)
 * 3. Verify expected attributes are extracted
 */

import { describe, expect, it, beforeAll, afterAll } from 'vitest';
import Fastify, { FastifyInstance } from 'fastify';
import fastifyMultipart from '@fastify/multipart';
import FormData from 'form-data';
import fs from 'node:fs';
import path from 'node:path';
import { enrichRoutes } from './routes.js';
import { configSchema, Config } from '../../config/index.js';

// Check prerequisites for running golden tests
const credentialsPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
const kleenexImagePath = path.resolve(process.cwd(), '../tests/golden_images/kleenex-small-box.jpg');
const hasCredentials = Boolean(credentialsPath && fs.existsSync(credentialsPath));
const hasImages = fs.existsSync(kleenexImagePath);
const shouldRun = hasCredentials && hasImages;

const describeIf = shouldRun ? describe : describe.skip;

/**
 * Helper to poll for enrichment completion.
 */
async function pollForCompletion(
  app: FastifyInstance,
  requestId: string,
  apiKey: string,
  maxAttempts = 30,
  delayMs = 1000
): Promise<{
  stage: string;
  visionFacts?: {
    ocrSnippets: string[];
    logoHints: { name: string; confidence: number }[];
    dominantColors: { name: string; hex: string; pct: number }[];
    labelHints: string[];
  };
  normalizedAttributes?: { key: string; value: string; confidence: string; source: string }[];
  draft?: { title: string; description: string; confidence: string };
  error?: { code: string; message: string };
}> {
  for (let i = 0; i < maxAttempts; i++) {
    const res = await app.inject({
      method: 'GET',
      url: `/v1/items/enrich/status/${requestId}`,
      headers: {
        'x-api-key': apiKey,
      },
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);

    const { status } = body;
    if (status.stage === 'DRAFT_DONE' || status.stage === 'FAILED') {
      return status;
    }

    // Wait before next poll
    await new Promise((resolve) => setTimeout(resolve, delayMs));
  }

  throw new Error(`Enrichment did not complete after ${maxAttempts} attempts`);
}

describeIf('Enrichment Routes Golden Tests', () => {
  let app: FastifyInstance;
  let config: Config;
  const apiKey = 'test-golden-key';

  beforeAll(async () => {
    config = configSchema.parse({
      nodeEnv: 'test',
      port: 3002,
      publicBaseUrl: 'http://localhost:3002',
      databaseUrl: 'postgresql://user:pass@localhost:5432/db',
      classifier: {
        provider: 'google',
        apiKeys: apiKey,
      },
      assistant: {
        provider: 'mock', // Use mock for draft generation in tests
        apiKeys: 'assist-key',
        openaiModel: 'gpt-4o-mini',
      },
      vision: {
        enabled: true,
        provider: 'google',
        enableOcr: true,
        enableLabels: true,
        enableLogos: true,
        enableColors: true,
      },
      ebay: {
        env: 'sandbox',
        clientId: 'client',
        clientSecret: 'client-secret-minimum-length-please',
        scopes: 'scope',
        tokenEncryptionKey: 'x'.repeat(32),
      },
      sessionSigningSecret: 'x'.repeat(64),
      security: {
        enforceHttps: false,
        enableHsts: false,
        apiKeyRotationEnabled: false,
        apiKeyExpirationDays: 90,
        logApiKeyUsage: false,
      },
      corsOrigins: 'http://localhost',
    });

    app = Fastify({ logger: false });
    await app.register(fastifyMultipart, {
      limits: { fileSize: 5 * 1024 * 1024 },
    });
    await app.register(enrichRoutes, {
      prefix: '/v1',
      config,
    });
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
  });

  it('returns 401 without API key', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/v1/items/enrich',
      payload: {},
    });

    expect(res.statusCode).toBe(401);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(false);
    expect(body.error.code).toBe('UNAUTHORIZED');
  });

  it('returns 400 without image', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/v1/items/enrich',
      headers: {
        'x-api-key': apiKey,
        'content-type': 'multipart/form-data; boundary=----test',
      },
      payload: '------test--',
    });

    expect(res.statusCode).toBe(400);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(false);
    expect(body.error.code).toBe('INVALID_REQUEST');
  });

  it('enriches kleenex box image with vision facts', async () => {
    // Submit enrichment request
    const imageBuffer = fs.readFileSync(kleenexImagePath);
    const form = new FormData();
    form.append('image', imageBuffer, {
      filename: 'kleenex-small-box.jpg',
      contentType: 'image/jpeg',
    });
    form.append('itemId', 'golden-kleenex-001');

    const submitRes = await app.inject({
      method: 'POST',
      url: '/v1/items/enrich',
      headers: {
        'x-api-key': apiKey,
        ...form.getHeaders(),
      },
      payload: form.getBuffer(),
    });

    expect(submitRes.statusCode).toBe(202);
    const submitBody = JSON.parse(submitRes.body);
    expect(submitBody.success).toBe(true);
    expect(submitBody.requestId).toBeDefined();

    // Poll for completion
    const status = await pollForCompletion(app, submitBody.requestId, apiKey);

    // Verify successful completion
    expect(status.stage).toBe('DRAFT_DONE');

    // Verify vision facts were extracted
    expect(status.visionFacts).toBeDefined();
    expect(status.visionFacts!.ocrSnippets.length).toBeGreaterThan(0);
    expect(status.visionFacts!.dominantColors.length).toBeGreaterThan(0);

    // Verify OCR contains expected text (kleenex)
    const ocrText = status.visionFacts!.ocrSnippets.join(' ').toLowerCase();
    expect(ocrText).toContain('kleenex');

    // Verify normalized attributes were extracted
    expect(status.normalizedAttributes).toBeDefined();
    expect(status.normalizedAttributes!.length).toBeGreaterThan(0);

    // Should have brand attribute from logo/OCR
    const brandAttr = status.normalizedAttributes!.find((a) => a.key === 'brand');
    if (brandAttr) {
      expect(brandAttr.value.toLowerCase()).toContain('kleenex');
    }

    // Verify draft was generated
    expect(status.draft).toBeDefined();
    expect(status.draft!.title).toBeDefined();
    expect(status.draft!.title.length).toBeGreaterThan(0);
  }, 60000); // 60s timeout for full enrichment

  it('returns metrics endpoint without auth', async () => {
    const res = await app.inject({
      method: 'GET',
      url: '/v1/items/enrich/metrics',
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(body.metrics).toBeDefined();
    expect(body.metrics.activeJobs).toBeDefined();
  });

  it('returns 404 for unknown request ID', async () => {
    const fakeRequestId = '00000000-0000-0000-0000-000000000000';
    const res = await app.inject({
      method: 'GET',
      url: `/v1/items/enrich/status/${fakeRequestId}`,
      headers: {
        'x-api-key': apiKey,
      },
    });

    expect(res.statusCode).toBe(404);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(false);
    expect(body.error.code).toBe('NOT_FOUND');
  });

  it('returns 400 for invalid request ID format', async () => {
    const res = await app.inject({
      method: 'GET',
      url: '/v1/items/enrich/status/not-a-uuid',
      headers: {
        'x-api-key': apiKey,
      },
    });

    expect(res.statusCode).toBe(400);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(false);
    expect(body.error.code).toBe('INVALID_REQUEST');
  });
});

/**
 * Unit tests that don't require external services.
 */
describe('Enrichment Routes Unit Tests', () => {
  let app: FastifyInstance;
  let config: Config;
  const apiKey = 'test-unit-key';

  beforeAll(async () => {
    config = configSchema.parse({
      nodeEnv: 'test',
      port: 3003,
      publicBaseUrl: 'http://localhost:3003',
      databaseUrl: 'postgresql://user:pass@localhost:5432/db',
      classifier: {
        provider: 'mock', // Use mock to avoid real API calls
        apiKeys: apiKey,
      },
      assistant: {
        provider: 'mock',
        apiKeys: 'assist-key',
      },
      vision: {
        enabled: true,
        provider: 'mock', // Use mock vision provider
        enableOcr: true,
        enableLabels: true,
        enableLogos: true,
        enableColors: true,
      },
      ebay: {
        env: 'sandbox',
        clientId: 'client',
        clientSecret: 'client-secret-minimum-length-please',
        scopes: 'scope',
        tokenEncryptionKey: 'x'.repeat(32),
      },
      sessionSigningSecret: 'x'.repeat(64),
      security: {
        enforceHttps: false,
        enableHsts: false,
        apiKeyRotationEnabled: false,
        apiKeyExpirationDays: 90,
        logApiKeyUsage: false,
      },
      corsOrigins: 'http://localhost',
    });

    app = Fastify({ logger: false });
    await app.register(fastifyMultipart, {
      limits: { fileSize: 5 * 1024 * 1024 },
    });
    await app.register(enrichRoutes, {
      prefix: '/v1',
      config,
    });
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
  });

  it('accepts valid multipart request and returns 202', async () => {
    // Create a minimal valid JPEG (1x1 pixel)
    const minimalJpeg = Buffer.from([
      0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01,
      0x00, 0x01, 0x00, 0x00, 0xff, 0xdb, 0x00, 0x43, 0x00, 0x08, 0x06, 0x06, 0x07, 0x06, 0x05, 0x08,
      0x07, 0x07, 0x07, 0x09, 0x09, 0x08, 0x0a, 0x0c, 0x14, 0x0d, 0x0c, 0x0b, 0x0b, 0x0c, 0x19, 0x12,
      0x13, 0x0f, 0x14, 0x1d, 0x1a, 0x1f, 0x1e, 0x1d, 0x1a, 0x1c, 0x1c, 0x20, 0x24, 0x2e, 0x27, 0x20,
      0x22, 0x2c, 0x23, 0x1c, 0x1c, 0x28, 0x37, 0x29, 0x2c, 0x30, 0x31, 0x34, 0x34, 0x34, 0x1f, 0x27,
      0x39, 0x3d, 0x38, 0x32, 0x3c, 0x2e, 0x33, 0x34, 0x32, 0xff, 0xc0, 0x00, 0x0b, 0x08, 0x00, 0x01,
      0x00, 0x01, 0x01, 0x01, 0x11, 0x00, 0xff, 0xc4, 0x00, 0x1f, 0x00, 0x00, 0x01, 0x05, 0x01, 0x01,
      0x01, 0x01, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04,
      0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0xff, 0xc4, 0x00, 0xb5, 0x10, 0x00, 0x02, 0x01, 0x03,
      0x03, 0x02, 0x04, 0x03, 0x05, 0x05, 0x04, 0x04, 0x00, 0x00, 0x01, 0x7d, 0x01, 0x02, 0x03, 0x00,
      0x04, 0x11, 0x05, 0x12, 0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61, 0x07, 0x22, 0x71, 0x14, 0x32,
      0x81, 0x91, 0xa1, 0x08, 0x23, 0x42, 0xb1, 0xc1, 0x15, 0x52, 0xd1, 0xf0, 0x24, 0x33, 0x62, 0x72,
      0x82, 0x09, 0x0a, 0x16, 0x17, 0x18, 0x19, 0x1a, 0x25, 0x26, 0x27, 0x28, 0x29, 0x2a, 0x34, 0x35,
      0x36, 0x37, 0x38, 0x39, 0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4a, 0x53, 0x54, 0x55,
      0x56, 0x57, 0x58, 0x59, 0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6a, 0x73, 0x74, 0x75,
      0x76, 0x77, 0x78, 0x79, 0x7a, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89, 0x8a, 0x92, 0x93, 0x94,
      0x95, 0x96, 0x97, 0x98, 0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7, 0xa8, 0xa9, 0xaa, 0xb2,
      0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3, 0xc4, 0xc5, 0xc6, 0xc7, 0xc8, 0xc9,
      0xca, 0xd2, 0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda, 0xe1, 0xe2, 0xe3, 0xe4, 0xe5, 0xe6,
      0xe7, 0xe8, 0xe9, 0xea, 0xf1, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8, 0xf9, 0xfa, 0xff, 0xda,
      0x00, 0x08, 0x01, 0x01, 0x00, 0x00, 0x3f, 0x00, 0xfb, 0xd5, 0xdb, 0x20, 0xa8, 0xf0, 0x00, 0x00,
      0xff, 0xd9,
    ]);

    const form = new FormData();
    form.append('image', minimalJpeg, {
      filename: 'test.jpg',
      contentType: 'image/jpeg',
    });
    form.append('itemId', 'unit-test-001');

    const res = await app.inject({
      method: 'POST',
      url: '/v1/items/enrich',
      headers: {
        'x-api-key': apiKey,
        ...form.getHeaders(),
      },
      payload: form.getBuffer(),
    });

    expect(res.statusCode).toBe(202);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(body.requestId).toBeDefined();
    expect(body.correlationId).toBeDefined();
  });
});
