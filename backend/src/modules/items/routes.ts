/**
 * Items API routes (Phase E: Multi-device sync)
 * Provides CRUD and sync endpoints for user-scoped items
 */

import { FastifyPluginAsync } from 'fastify';
import { Config } from '../../config/index.js';
import { requireAuth } from '../../infra/http/plugins/auth-middleware.js';
import { ValidationError, ConflictError, NotFoundError, ForbiddenError } from '../../shared/errors/index.js';
import {
  createItemSchema,
  updateItemSchema,
  getItemsQuerySchema,
  syncRequestSchema,
} from './schema.js';
import {
  createItem,
  getItems,
  getItemById,
  updateItem,
  deleteItem,
  syncItems,
} from './service.js';

export const itemsRoutes: FastifyPluginAsync<{ config: Config }> = async (
  fastify,
  opts
) => {
  /**
   * GET /v1/items?since=<timestamp>&limit=<n>&includeDeleted=<bool>
   * Get items for authenticated user with pagination
   */
  fastify.get('/', async (request, reply) => {
    const userId = requireAuth(request);

    // Validate query parameters
    const parseResult = getItemsQuerySchema.safeParse(request.query);
    if (!parseResult.success) {
      throw new ValidationError('Invalid query parameters', {
        errors: parseResult.error.errors,
      });
    }

    const query = parseResult.data;

    try {
      const { items, hasMore, nextSince } = await getItems(userId, query);

      return reply.status(200).send({
        items,
        hasMore,
        nextSince,
        correlationId: request.correlationId,
      });
    } catch (error) {
      request.log.error(
        {
          event: 'get_items_failed',
          userId,
          error: error instanceof Error ? error.message : 'Unknown error',
          correlationId: request.correlationId,
        },
        'Failed to get items'
      );

      return reply.status(500).send({
        error: {
          code: 'INTERNAL_ERROR',
          message: 'Failed to retrieve items',
          correlationId: request.correlationId,
        },
      });
    }
  });

  /**
   * GET /v1/items/:id
   * Get a single item by ID (ownership enforced)
   */
  fastify.get('/:id', async (request, reply) => {
    const userId = requireAuth(request);
    const { id } = request.params as { id: string };

    try {
      const item = await getItemById(userId, id);

      return reply.status(200).send({
        item,
        correlationId: request.correlationId,
      });
    } catch (error) {
      if (error instanceof NotFoundError) {
        return reply.status(404).send({
          error: {
            code: 'NOT_FOUND',
            message: 'Item not found',
            correlationId: request.correlationId,
          },
        });
      }

      if (error instanceof ForbiddenError) {
        return reply.status(403).send({
          error: {
            code: 'FORBIDDEN',
            message: 'You do not have access to this item',
            correlationId: request.correlationId,
          },
        });
      }

      request.log.error(
        {
          event: 'get_item_failed',
          userId,
          itemId: id,
          error: error instanceof Error ? error.message : 'Unknown error',
          correlationId: request.correlationId,
        },
        'Failed to get item'
      );

      return reply.status(500).send({
        error: {
          code: 'INTERNAL_ERROR',
          message: 'Failed to retrieve item',
          correlationId: request.correlationId,
        },
      });
    }
  });

  /**
   * POST /v1/items
   * Create a new item
   */
  fastify.post('/', async (request, reply) => {
    const userId = requireAuth(request);

    // Validate request body
    const parseResult = createItemSchema.safeParse(request.body);
    if (!parseResult.success) {
      throw new ValidationError('Invalid request body', {
        errors: parseResult.error.errors,
      });
    }

    const data = parseResult.data;

    try {
      const { item, localId } = await createItem(userId, data);

      request.log.info(
        {
          event: 'item_created',
          userId,
          itemId: item.id,
          localId,
          correlationId: request.correlationId,
        },
        'Item created successfully'
      );

      return reply.status(200).send({
        item,
        localId,
        correlationId: request.correlationId,
      });
    } catch (error) {
      request.log.error(
        {
          event: 'create_item_failed',
          userId,
          error: error instanceof Error ? error.message : 'Unknown error',
          correlationId: request.correlationId,
        },
        'Failed to create item'
      );

      return reply.status(500).send({
        error: {
          code: 'INTERNAL_ERROR',
          message: 'Failed to create item',
          correlationId: request.correlationId,
        },
      });
    }
  });

  /**
   * PATCH /v1/items/:id
   * Update an existing item with optimistic locking
   */
  fastify.patch('/:id', async (request, reply) => {
    const userId = requireAuth(request);
    const { id } = request.params as { id: string };

    // Validate request body
    const parseResult = updateItemSchema.safeParse(request.body);
    if (!parseResult.success) {
      throw new ValidationError('Invalid request body', {
        errors: parseResult.error.errors,
      });
    }

    const data = parseResult.data;

    try {
      const item = await updateItem(userId, id, data);

      request.log.info(
        {
          event: 'item_updated',
          userId,
          itemId: item.id,
          syncVersion: item.syncVersion,
          correlationId: request.correlationId,
        },
        'Item updated successfully'
      );

      return reply.status(200).send({
        item,
        correlationId: request.correlationId,
      });
    } catch (error) {
      if (error instanceof NotFoundError) {
        return reply.status(404).send({
          error: {
            code: 'NOT_FOUND',
            message: 'Item not found',
            correlationId: request.correlationId,
          },
        });
      }

      if (error instanceof ForbiddenError) {
        return reply.status(403).send({
          error: {
            code: 'FORBIDDEN',
            message: 'You do not have access to this item',
            correlationId: request.correlationId,
          },
        });
      }

      if (error instanceof ConflictError) {
        return reply.status(409).send({
          error: {
            code: 'CONFLICT',
            message: 'Item has been modified by another client. Please fetch the latest version and retry.',
            correlationId: request.correlationId,
            details: (error as any).details,
          },
        });
      }

      request.log.error(
        {
          event: 'update_item_failed',
          userId,
          itemId: id,
          error: error instanceof Error ? error.message : 'Unknown error',
          correlationId: request.correlationId,
        },
        'Failed to update item'
      );

      return reply.status(500).send({
        error: {
          code: 'INTERNAL_ERROR',
          message: 'Failed to update item',
          correlationId: request.correlationId,
        },
      });
    }
  });

  /**
   * DELETE /v1/items/:id
   * Soft delete an item (sets deletedAt timestamp)
   */
  fastify.delete('/:id', async (request, reply) => {
    const userId = requireAuth(request);
    const { id } = request.params as { id: string };

    try {
      const item = await deleteItem(userId, id);

      request.log.info(
        {
          event: 'item_deleted',
          userId,
          itemId: item.id,
          deletedAt: item.deletedAt,
          correlationId: request.correlationId,
        },
        'Item deleted successfully'
      );

      return reply.status(200).send({
        item,
        correlationId: request.correlationId,
      });
    } catch (error) {
      if (error instanceof NotFoundError) {
        return reply.status(404).send({
          error: {
            code: 'NOT_FOUND',
            message: 'Item not found',
            correlationId: request.correlationId,
          },
        });
      }

      if (error instanceof ForbiddenError) {
        return reply.status(403).send({
          error: {
            code: 'FORBIDDEN',
            message: 'You do not have access to this item',
            correlationId: request.correlationId,
          },
        });
      }

      request.log.error(
        {
          event: 'delete_item_failed',
          userId,
          itemId: id,
          error: error instanceof Error ? error.message : 'Unknown error',
          correlationId: request.correlationId,
        },
        'Failed to delete item'
      );

      return reply.status(500).send({
        error: {
          code: 'INTERNAL_ERROR',
          message: 'Failed to delete item',
          correlationId: request.correlationId,
        },
      });
    }
  });

  /**
   * POST /v1/items/sync
   * Batch sync endpoint - push local changes and fetch server changes
   */
  fastify.post('/sync', async (request, reply) => {
    const userId = requireAuth(request);

    // Validate request body
    const parseResult = syncRequestSchema.safeParse(request.body);
    if (!parseResult.success) {
      throw new ValidationError('Invalid request body', {
        errors: parseResult.error.errors,
      });
    }

    const syncReq = parseResult.data;

    try {
      const { results, serverChanges, syncTimestamp } = await syncItems(userId, syncReq);

      request.log.info(
        {
          event: 'items_synced',
          userId,
          changesCount: syncReq.changes.length,
          serverChangesCount: serverChanges.length,
          successCount: results.filter(r => r.status === 'SUCCESS').length,
          conflictCount: results.filter(r => r.status === 'CONFLICT').length,
          errorCount: results.filter(r => r.status === 'ERROR').length,
          correlationId: request.correlationId,
        },
        'Items sync completed'
      );

      return reply.status(200).send({
        results,
        serverChanges,
        syncTimestamp,
        correlationId: request.correlationId,
      });
    } catch (error) {
      request.log.error(
        {
          event: 'items_sync_failed',
          userId,
          error: error instanceof Error ? error.message : 'Unknown error',
          correlationId: request.correlationId,
        },
        'Failed to sync items'
      );

      return reply.status(500).send({
        error: {
          code: 'INTERNAL_ERROR',
          message: 'Failed to sync items',
          correlationId: request.correlationId,
        },
      });
    }
  });
};
