import { describe, it, expect, beforeAll, afterAll, vi } from 'vitest';
import { buildApp } from '../../app.js';
import { loadConfig } from '../../config/index.js';
import { FastifyInstance } from 'fastify';

describe('Mobile Telemetry Endpoint', () => {
  let app: FastifyInstance;

  beforeAll(async () => {
    const config = loadConfig();
    app = await buildApp(config);
  });

  afterAll(async () => {
    await app.close();
  });

  it('should accept valid mobile telemetry event', async () => {
    const event = {
      event_name: 'app_launch',
      platform: 'android',
      app_version: '1.2.3',
      build_type: 'beta',
      timestamp_ms: Date.now(),
      session_id: 'test-session-123',
      attributes: {
        launch_type: 'cold_start',
      },
    };

    const response = await app.inject({
      method: 'POST',
      url: '/v1/telemetry/mobile',
      payload: event,
    });

    expect(response.statusCode).toBe(202);
    expect(response.json()).toEqual({ success: true });
  });

  it('should reject event missing required fields', async () => {
    const invalidEvent = {
      event_name: 'app_launch',
      platform: 'android',
      // Missing app_version, build_type, timestamp_ms
    };

    const response = await app.inject({
      method: 'POST',
      url: '/v1/telemetry/mobile',
      payload: invalidEvent,
    });

    expect(response.statusCode).toBe(400);
  });

  it('should reject event with invalid platform', async () => {
    const invalidEvent = {
      event_name: 'app_launch',
      platform: 'windows', // Invalid platform
      app_version: '1.2.3',
      build_type: 'beta',
      timestamp_ms: Date.now(),
    };

    const response = await app.inject({
      method: 'POST',
      url: '/v1/telemetry/mobile',
      payload: invalidEvent,
    });

    expect(response.statusCode).toBe(400);
  });

  it('should reject event with invalid build_type', async () => {
    const invalidEvent = {
      event_name: 'app_launch',
      platform: 'android',
      app_version: '1.2.3',
      build_type: 'invalid', // Invalid build_type
      timestamp_ms: Date.now(),
    };

    const response = await app.inject({
      method: 'POST',
      url: '/v1/telemetry/mobile',
      payload: invalidEvent,
    });

    expect(response.statusCode).toBe(400);
  });

  it('should reject event with invalid app_version format', async () => {
    const invalidEvent = {
      event_name: 'app_launch',
      platform: 'android',
      app_version: 'v1.2', // Invalid format (doesn't match semver)
      build_type: 'beta',
      timestamp_ms: Date.now(),
    };

    const response = await app.inject({
      method: 'POST',
      url: '/v1/telemetry/mobile',
      payload: invalidEvent,
    });

    expect(response.statusCode).toBe(400);
  });

  it('should accept event without optional fields', async () => {
    const minimalEvent = {
      event_name: 'scan_started',
      platform: 'ios',
      app_version: '2.0.0',
      build_type: 'prod',
      timestamp_ms: Date.now(),
    };

    const response = await app.inject({
      method: 'POST',
      url: '/v1/telemetry/mobile',
      payload: minimalEvent,
    });

    expect(response.statusCode).toBe(202);
    expect(response.json()).toEqual({ success: true });
  });

  it('should sanitize attributes by removing PII-like keys', async () => {
    // Mock console.log to capture the structured log output
    const consoleLogSpy = vi.spyOn(console, 'log').mockImplementation(() => {});

    const eventWithPII = {
      event_name: 'scan_completed',
      platform: 'android',
      app_version: '1.3.0',
      build_type: 'dev',
      timestamp_ms: Date.now(),
      attributes: {
        duration_ms: 5000,
        item_count: 3,
        user_id: 'user123', // Should be removed
        email: 'test@example.com', // Should be removed
        barcode: '1234567890', // Should be removed
        has_nutrition_data: true, // Should be kept
      },
    };

    const response = await app.inject({
      method: 'POST',
      url: '/v1/telemetry/mobile',
      payload: eventWithPII,
    });

    expect(response.statusCode).toBe(202);

    // Verify that the logged event doesn't contain PII
    expect(consoleLogSpy).toHaveBeenCalled();
    const loggedEvent = JSON.parse(consoleLogSpy.mock.calls[0][0]);

    expect(loggedEvent.attributes).toBeDefined();
    expect(loggedEvent.attributes.duration_ms).toBe(5000);
    expect(loggedEvent.attributes.item_count).toBe(3);
    expect(loggedEvent.attributes.has_nutrition_data).toBe(true);

    // PII fields should not be present
    expect(loggedEvent.attributes.user_id).toBeUndefined();
    expect(loggedEvent.attributes.email).toBeUndefined();
    expect(loggedEvent.attributes.barcode).toBeUndefined();

    consoleLogSpy.mockRestore();
  });

  // Note: Rate limiting is configured at the route level (max 100 per minute)
  // Testing rate limiting in unit tests is unreliable due to timing issues
  // Rate limiting should be verified manually or in integration tests
});
