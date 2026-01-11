/**
 * Marketplaces Catalog Routes
 *
 * GET /v1/marketplaces/countries - List supported country codes
 * GET /v1/marketplaces/:countryCode - Get marketplaces for a specific country
 */

import { FastifyPluginAsync, FastifyRequest, FastifyReply } from 'fastify';
import { z } from 'zod';
import { Config } from '../../config/index.js';
import { ApiKeyManager } from '../classifier/api-key-manager.js';
import { MarketplacesService } from './service.js';

type RouteOpts = { config: Config };

const countryCodeParamSchema = z.object({
  countryCode: z.string().length(2).toUpperCase(),
});

/**
 * Error response helper
 */
function errorResponse(
  reply: FastifyReply,
  statusCode: number,
  code: string,
  message: string
): FastifyReply {
  return reply.status(statusCode).send({
    error: { code, message },
  });
}

/**
 * Validate API key from request headers
 */
function validateApiKey(
  request: FastifyRequest,
  reply: FastifyReply,
  apiKeyManager: ApiKeyManager
): boolean {
  const apiKey = request.headers['x-api-key'] as string | undefined;

  if (!apiKey) {
    errorResponse(reply, 401, 'UNAUTHORIZED', 'API key required');
    return false;
  }

  if (!apiKeyManager.validateKey(apiKey)) {
    errorResponse(reply, 401, 'UNAUTHORIZED', 'Invalid API key');
    return false;
  }

  return true;
}

export const marketplacesRoutes: FastifyPluginAsync<RouteOpts> = async (
  fastify,
  opts
) => {
  const { config } = opts;

  // Initialize service with catalog path
  const catalogPath = 'backend/config/marketplaces/marketplaces.eu.json';
  const service = new MarketplacesService(catalogPath);

  // Initialize catalog at startup (fail-safe: logs error but doesn't crash server)
  const initResult = service.initialize();
  if (!initResult.ready) {
    fastify.log.error(
      {
        error: initResult.error,
        catalogPath,
      },
      'Marketplaces catalog failed to load - endpoints will return 503'
    );
  } else {
    const versionResult = service.getCatalogVersion();
    const countriesResult = service.listCountries();

    fastify.log.info(
      {
        catalogPath,
        version: versionResult.success ? versionResult.data : 'unknown',
        countriesCount: countriesResult.success ? countriesResult.data.length : 0,
      },
      'Marketplaces catalog loaded successfully'
    );
  }

  // Shared API key manager
  const apiKeyManager = new ApiKeyManager(config.classifier.apiKeys);

  /**
   * GET /marketplaces/countries
   *
   * Returns list of supported country codes
   */
  fastify.get('/marketplaces/countries', async (request, reply) => {
    // Validate API key
    if (!validateApiKey(request, reply, apiKeyManager)) {
      return reply;
    }

    // Check service readiness
    if (!service.isReady()) {
      return errorResponse(
        reply,
        503,
        'SERVICE_UNAVAILABLE',
        'Marketplaces catalog not available'
      );
    }

    const result = service.listCountries();

    if (!result.success) {
      fastify.log.error({ error: result.error }, 'Failed to list countries');
      return errorResponse(reply, 500, 'INTERNAL_ERROR', 'Failed to retrieve country list');
    }

    return reply.status(200).send({
      countries: result.data,
    });
  });

  /**
   * GET /marketplaces/:countryCode
   *
   * Returns marketplaces for a specific country
   */
  fastify.get<{ Params: { countryCode: string } }>(
    '/marketplaces/:countryCode',
    async (request, reply) => {
      // Validate API key
      if (!validateApiKey(request, reply, apiKeyManager)) {
        return reply;
      }

      // Check service readiness
      if (!service.isReady()) {
        return errorResponse(
          reply,
          503,
          'SERVICE_UNAVAILABLE',
          'Marketplaces catalog not available'
        );
      }

      // Validate country code parameter
      const paramValidation = countryCodeParamSchema.safeParse(request.params);
      if (!paramValidation.success) {
        return errorResponse(
          reply,
          400,
          'INVALID_COUNTRY_CODE',
          'Country code must be a 2-letter ISO code'
        );
      }

      const { countryCode } = paramValidation.data;

      // Get marketplaces
      const result = service.getMarketplaces(countryCode);

      if (!result.success) {
        if (result.errorCode === 'NOT_FOUND') {
          return errorResponse(
            reply,
            404,
            'NOT_FOUND',
            `Country '${countryCode}' not supported`
          );
        }

        fastify.log.error(
          { error: result.error, countryCode },
          'Failed to get marketplaces'
        );
        return errorResponse(reply, 500, 'INTERNAL_ERROR', 'Failed to retrieve marketplaces');
      }

      // Also get country config for currency info
      const configResult = service.getCountryConfig(countryCode);
      const defaultCurrency = configResult.success ? configResult.data.defaultCurrency : null;

      return reply.status(200).send({
        countryCode,
        defaultCurrency,
        marketplaces: result.data,
      });
    }
  );
};
