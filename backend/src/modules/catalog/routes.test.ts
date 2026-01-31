/**
 * Catalog API routes tests
 * Uses mocked Prisma for fast, reliable route tests.
 */

import { beforeAll, afterAll, beforeEach, describe, expect, it, vi } from 'vitest';
import Fastify, { FastifyInstance } from 'fastify';
import { catalogRoutes } from './routes.js';
import { errorHandlerPlugin } from '../../infra/http/plugins/error-handler.js';

const mockPrisma = vi.hoisted(() => ({
  catalogBrandWikidataMap: {
    findMany: vi.fn(),
    findUnique: vi.fn(),
  },
  catalogModel: {
    findMany: vi.fn(),
  },
}));

vi.mock('../../infra/db/prisma.js', () => ({
  prisma: mockPrisma,
}));

describe('Catalog Routes', () => {
  let app: FastifyInstance;

  beforeAll(async () => {
    app = Fastify({ logger: false });
    app.setErrorHandler(errorHandlerPlugin);
    await app.register(catalogRoutes);
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
  });

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('GET /v1/catalog/:subtype/brands returns 200 with array', async () => {
    mockPrisma.catalogBrandWikidataMap.findMany.mockResolvedValue([
      { brandString: 'Apple' },
      { brandString: 'Samsung' },
      { brandString: 'Samsung' },
    ]);

    const response = await app.inject({
      method: 'GET',
      url: '/v1/catalog/electronics_phone/brands',
    });

    expect(response.statusCode).toBe(200);
    expect(response.headers['cache-control']).toBe('public, max-age=3600');

    const body = JSON.parse(response.body);
    expect(body.subtype).toBe('electronics_phone');
    expect(body.brands).toEqual(['Apple', 'Samsung']);
  });

  it('GET /v1/catalog/:subtype/models returns empty list for unknown brand', async () => {
    mockPrisma.catalogBrandWikidataMap.findUnique.mockResolvedValue(null);

    const response = await app.inject({
      method: 'GET',
      url: '/v1/catalog/electronics_phone/models?brand=Unknown',
    });

    expect(response.statusCode).toBe(200);
    expect(response.headers['cache-control']).toBe('public, max-age=3600');

    const body = JSON.parse(response.body);
    expect(body.subtype).toBe('electronics_phone');
    expect(body.brand).toBe('Unknown');
    expect(body.models).toEqual([]);
    expect(mockPrisma.catalogModel.findMany).not.toHaveBeenCalled();
  });

  it('GET /v1/catalog/:subtype/models returns models sorted by label', async () => {
    mockPrisma.catalogBrandWikidataMap.findUnique.mockResolvedValue({
      wikidataQid: 'Q99',
    });
    mockPrisma.catalogModel.findMany.mockResolvedValue([
      { modelLabel: 'Galaxy S23', modelQid: 'Q2' },
      { modelLabel: 'Galaxy S24', modelQid: 'Q1' },
    ]);

    const response = await app.inject({
      method: 'GET',
      url: '/v1/catalog/electronics_phone/models?brand=Samsung',
    });

    expect(response.statusCode).toBe(200);

    const body = JSON.parse(response.body);
    expect(body.brand).toBe('Samsung');
    expect(body.models).toEqual([
      { label: 'Galaxy S23', id: 'Q2' },
      { label: 'Galaxy S24', id: 'Q1' },
    ]);
  });
});
