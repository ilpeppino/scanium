import { describe, expect, it } from 'vitest';
import Fastify from 'fastify';
import fastifyMultipart from '@fastify/multipart';
import FormData from 'form-data';
import fs from 'node:fs';
import path from 'node:path';
import { visionInsightsRoutes } from './routes.js';
import { configSchema } from '../../config/index.js';

const credentialsPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
const imagePath = path.resolve(process.cwd(), '../tests/golden_images/kleenex-small-box.jpg');
const shouldRun = Boolean(credentialsPath && fs.existsSync(credentialsPath) && fs.existsSync(imagePath));

const describeIf = shouldRun ? describe : describe.skip;

describeIf('Vision Insights Golden', () => {
  it('extracts OCR/colors/labels from golden image', async () => {
    const config = configSchema.parse({
      nodeEnv: 'test',
      port: 3001,
      publicBaseUrl: 'http://localhost:3001',
      databaseUrl: 'postgresql://user:pass@localhost:5432/db',
      classifier: {
        provider: 'google',
        apiKeys: 'test-key',
      },
      assistant: {
        provider: 'mock',
        apiKeys: 'assist-key',
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

    const app = Fastify();
    await app.register(fastifyMultipart, {
      limits: { fileSize: 2 * 1024 * 1024 },
    });
    await app.register(visionInsightsRoutes, {
      prefix: '/v1',
      config,
    });

    const imageBuffer = fs.readFileSync(imagePath);
    const form = new FormData();
    form.append('image', imageBuffer, {
      filename: 'kleenex-small-box.jpg',
      contentType: 'image/jpeg',
    });
    form.append('itemId', 'golden-1');

    const res = await app.inject({
      method: 'POST',
      url: '/v1/vision/insights',
      headers: {
        'x-api-key': 'test-key',
        ...form.getHeaders(),
      },
      payload: form.getBuffer(),
    });

    expect(res.statusCode).toBe(200);

    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(body.ocrSnippets.length).toBeGreaterThan(0);
    expect(body.dominantColors.length).toBeGreaterThan(0);
    expect(body.labelHints.length).toBeGreaterThan(0);

    if ((body.logoHints ?? []).length === 0) {
      const ocrJoined = (body.ocrSnippets ?? []).join(' ').toLowerCase();
      expect(ocrJoined).toContain('kleenex');
    }

    await app.close();
  });
});
