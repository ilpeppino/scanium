import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import Fastify, { FastifyInstance } from 'fastify';
import { correlationPlugin } from './correlation.js';

describe('correlationPlugin - W3C Trace Context', () => {
  let fastify: FastifyInstance;

  beforeEach(async () => {
    fastify = Fastify({ logger: false });
    await fastify.register(correlationPlugin);

    // Add route that exposes request properties
    fastify.get('/test', async (request, reply) => {
      return {
        correlationId: request.correlationId || null,
        traceId: request.traceId || null,
        spanId: request.spanId || null,
        parentSpanId: request.parentSpanId || null,
      };
    });

    await fastify.ready();
  });

  afterEach(async () => {
    await fastify.close();
  });

  describe('W3C traceparent header parsing', () => {
    it('should decorate request with properties', async () => {
      const response = await fastify.inject({
        method: 'GET',
        url: '/test',
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      // All properties should exist (even if null/empty)
      expect(body).toHaveProperty('correlationId');
      expect(body).toHaveProperty('traceId');
      expect(body).toHaveProperty('spanId');
      expect(body).toHaveProperty('parentSpanId');
    });

    it('should parse valid traceparent header', async () => {
      const response = await fastify.inject({
        method: 'GET',
        url: '/test',
        headers: {
          traceparent: '00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01',
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.traceId).toBe('4bf92f3577b34da6a3ce929d0e0e4736');
      expect(body.parentSpanId).toBe('00f067aa0ba902b7'); // Mobile's span ID
      expect(body.spanId).toMatch(/^[0-9a-f]{16}$/); // Backend's new span ID
      expect(body.spanId).not.toBe('00f067aa0ba902b7'); // Should be different from parent
    });

    it('should return traceparent header in response', async () => {
      const response = await fastify.inject({
        method: 'GET',
        url: '/test',
        headers: {
          traceparent: '00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01',
        },
      });

      expect(response.statusCode).toBe(200);
      expect(response.headers.traceparent).toBeDefined();

      const traceparent = response.headers.traceparent as string;
      const parts = traceparent.split('-');
      expect(parts).toHaveLength(4);
      expect(parts[0]).toBe('00'); // version
      expect(parts[1]).toBe('4bf92f3577b34da6a3ce929d0e0e4736'); // same traceId
      expect(parts[2]).toMatch(/^[0-9a-f]{16}$/); // backend span ID
      expect(parts[3]).toBe('01'); // flags preserved
    });

    it('should reject traceparent with wrong version', async () => {
      const response = await fastify.inject({
        method: 'GET',
        url: '/test',
        headers: {
          traceparent: '01-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01',
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.traceId).toBeNull();
      expect(body.spanId).toBeNull();
      expect(body.parentSpanId).toBeNull();
    });

    it('should reject traceparent with invalid traceId length', async () => {
      const response = await fastify.inject({
        method: 'GET',
        url: '/test',
        headers: {
          traceparent: '00-4bf92f3577b34da6a3ce929d0e0e473-00f067aa0ba902b7-01', // 31 chars
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.traceId).toBeNull();
    });

    it('should reject traceparent with invalid spanId length', async () => {
      const response = await fastify.inject({
        method: 'GET',
        url: '/test',
        headers: {
          traceparent: '00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902-01', // 14 chars
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.traceId).toBeNull();
    });

    it('should reject traceparent with non-hex characters', async () => {
      const response = await fastify.inject({
        method: 'GET',
        url: '/test',
        headers: {
          traceparent: '00-4bf92f3577b34da6a3ce929d0e0e473g-00f067aa0ba902b7-01',
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.traceId).toBeNull();
    });

    it('should reject malformed traceparent with wrong number of parts', async () => {
      const response = await fastify.inject({
        method: 'GET',
        url: '/test',
        headers: {
          traceparent: '00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7',
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.traceId).toBeNull();
    });
  });

  describe('Backward compatibility', () => {
    it('should generate correlationId when X-Scanium-Correlation-Id header is missing', async () => {
      const response = await fastify.inject({
        method: 'GET',
        url: '/test',
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.correlationId).toBeTruthy();
      expect(body.correlationId).toMatch(/^[0-9a-f-]{36}$/); // UUID format
      expect(response.headers['x-scanium-correlation-id']).toBeTruthy();
    });

    it('should preserve existing X-Scanium-Correlation-Id header', async () => {
      const correlationId = 'test-correlation-123';
      const response = await fastify.inject({
        method: 'GET',
        url: '/test',
        headers: {
          'x-scanium-correlation-id': correlationId,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.correlationId).toBe(correlationId);
      expect(response.headers['x-scanium-correlation-id']).toBe(correlationId);
    });

    it('should work with both traceparent and correlation ID', async () => {
      const correlationId = 'test-correlation-456';
      const response = await fastify.inject({
        method: 'GET',
        url: '/test',
        headers: {
          traceparent: '00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01',
          'x-scanium-correlation-id': correlationId,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.correlationId).toBe(correlationId);
      expect(body.traceId).toBe('4bf92f3577b34da6a3ce929d0e0e4736');
      expect(response.headers['x-scanium-correlation-id']).toBe(correlationId);
      expect(response.headers.traceparent).toBeDefined();
    });

    it('should not add traceparent header when no trace context present', async () => {
      const response = await fastify.inject({
        method: 'GET',
        url: '/test',
      });

      expect(response.statusCode).toBe(200);
      expect(response.headers.traceparent).toBeUndefined();
    });
  });
});
