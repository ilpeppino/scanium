/**
 * Items service - Business logic for item CRUD and sync operations (Phase E)
 */

import { prisma } from '../../infra/db/prisma.js';
import { Item, Prisma } from '@prisma/client';
import {
  CreateItemRequest,
  UpdateItemRequest,
  GetItemsQuery,
  SyncRequest,
  SyncChange,
  SyncResult,
} from './schema.js';
import { ConflictError, NotFoundError, ForbiddenError } from '../../shared/errors/index.js';

/**
 * Create a new item for a user
 */
export async function createItem(
  userId: string,
  data: CreateItemRequest
): Promise<{ item: Item; localId: string }> {
  // Convert BigInt/number to BigInt for userPriceCents
  const userPriceCents = data.userPriceCents !== undefined
    ? BigInt(data.userPriceCents)
    : undefined;

  const item = await prisma.item.create({
    data: {
      userId,
      title: data.title,
      description: data.description,
      category: data.category,
      confidence: data.confidence,
      priceEstimateLow: data.priceEstimateLow,
      priceEstimateHigh: data.priceEstimateHigh,
      userPriceCents,
      condition: data.condition,

      // JSON fields
      attributesJson: data.attributesJson as Prisma.InputJsonValue,
      detectedAttributesJson: data.detectedAttributesJson as Prisma.InputJsonValue,
      visionAttributesJson: data.visionAttributesJson as Prisma.InputJsonValue,
      enrichmentStatusJson: data.enrichmentStatusJson as Prisma.InputJsonValue,

      // Quality metrics
      completenessScore: data.completenessScore,
      missingAttributesJson: data.missingAttributesJson as Prisma.InputJsonValue,
      capturedShotTypesJson: data.capturedShotTypesJson as Prisma.InputJsonValue,
      isReadyForListing: data.isReadyForListing,
      lastEnrichedAt: data.lastEnrichedAt ? new Date(data.lastEnrichedAt) : undefined,

      // Export Assistant
      exportTitle: data.exportTitle,
      exportDescription: data.exportDescription,
      exportBulletsJson: data.exportBulletsJson as Prisma.InputJsonValue,
      exportGeneratedAt: data.exportGeneratedAt ? new Date(data.exportGeneratedAt) : undefined,
      exportFromCache: data.exportFromCache,
      exportModel: data.exportModel,
      exportConfidenceTier: data.exportConfidenceTier,

      // Classification
      classificationStatus: data.classificationStatus,
      domainCategoryId: data.domainCategoryId,
      classificationErrorMessage: data.classificationErrorMessage,
      classificationRequestId: data.classificationRequestId,

      // Photo metadata
      photosMetadataJson: data.photosMetadataJson as Prisma.InputJsonValue,

      // Multi-object scanning
      attributesSummaryText: data.attributesSummaryText,
      summaryTextUserEdited: data.summaryTextUserEdited,
      sourcePhotoId: data.sourcePhotoId,

      // Listing associations
      listingStatus: data.listingStatus,
      listingId: data.listingId,
      listingUrl: data.listingUrl,

      // OCR/barcode
      recognizedText: data.recognizedText,
      barcodeValue: data.barcodeValue,
      labelText: data.labelText,

      // Sync metadata
      syncVersion: 1,
      clientUpdatedAt: new Date(data.clientUpdatedAt),
    },
  });

  return { item, localId: data.localId };
}

/**
 * Get items for a user with pagination and filtering
 */
export async function getItems(
  userId: string,
  query: GetItemsQuery
): Promise<{ items: Item[]; hasMore: boolean; nextSince: string | null }> {
  const limit = query.limit;
  const since = query.since ? new Date(parseInt(query.since)) : undefined;
  const includeDeleted = query.includeDeleted;

  // Build where clause
  const where: Prisma.ItemWhereInput = {
    userId,
    ...(since && { updatedAt: { gt: since } }),
    ...(!includeDeleted && { deletedAt: null }),
  };

  // Fetch limit + 1 to check if there are more items
  const items = await prisma.item.findMany({
    where,
    orderBy: { updatedAt: 'asc' },
    take: limit + 1,
  });

  const hasMore = items.length > limit;
  const returnedItems = hasMore ? items.slice(0, limit) : items;
  const nextSince = hasMore && returnedItems.length > 0
    ? returnedItems[returnedItems.length - 1].updatedAt.getTime().toString()
    : null;

  return { items: returnedItems, hasMore, nextSince };
}

/**
 * Get a single item by ID
 * Enforces ownership check
 */
export async function getItemById(userId: string, itemId: string): Promise<Item> {
  const item = await prisma.item.findUnique({
    where: { id: itemId },
  });

  if (!item) {
    throw new NotFoundError('Item not found');
  }

  if (item.userId !== userId) {
    throw new ForbiddenError('You do not have access to this item');
  }

  return item;
}

/**
 * Update an item with optimistic locking
 * Returns ConflictError if syncVersion doesn't match
 */
export async function updateItem(
  userId: string,
  itemId: string,
  data: UpdateItemRequest
): Promise<Item> {
  // First, check ownership and current syncVersion
  const currentItem = await prisma.item.findUnique({
    where: { id: itemId },
  });

  if (!currentItem) {
    throw new NotFoundError('Item not found');
  }

  if (currentItem.userId !== userId) {
    throw new ForbiddenError('You do not have access to this item');
  }

  if (currentItem.syncVersion !== data.syncVersion) {
    throw new ConflictError('Item has been modified by another client', {
      currentSyncVersion: currentItem.syncVersion,
      expectedSyncVersion: data.syncVersion,
    });
  }

  // Convert BigInt/number to BigInt for userPriceCents
  const userPriceCents = data.userPriceCents !== undefined
    ? BigInt(data.userPriceCents)
    : undefined;

  // Update with incremented syncVersion
  const updatedItem = await prisma.item.update({
    where: { id: itemId },
    data: {
      ...(data.title !== undefined && { title: data.title }),
      ...(data.description !== undefined && { description: data.description }),
      ...(data.category !== undefined && { category: data.category }),
      ...(data.confidence !== undefined && { confidence: data.confidence }),
      ...(data.priceEstimateLow !== undefined && { priceEstimateLow: data.priceEstimateLow }),
      ...(data.priceEstimateHigh !== undefined && { priceEstimateHigh: data.priceEstimateHigh }),
      ...(userPriceCents !== undefined && { userPriceCents }),
      ...(data.condition !== undefined && { condition: data.condition }),

      // JSON fields
      ...(data.attributesJson !== undefined && { attributesJson: data.attributesJson as Prisma.InputJsonValue }),
      ...(data.detectedAttributesJson !== undefined && { detectedAttributesJson: data.detectedAttributesJson as Prisma.InputJsonValue }),
      ...(data.visionAttributesJson !== undefined && { visionAttributesJson: data.visionAttributesJson as Prisma.InputJsonValue }),
      ...(data.enrichmentStatusJson !== undefined && { enrichmentStatusJson: data.enrichmentStatusJson as Prisma.InputJsonValue }),

      // Quality metrics
      ...(data.completenessScore !== undefined && { completenessScore: data.completenessScore }),
      ...(data.missingAttributesJson !== undefined && { missingAttributesJson: data.missingAttributesJson as Prisma.InputJsonValue }),
      ...(data.capturedShotTypesJson !== undefined && { capturedShotTypesJson: data.capturedShotTypesJson as Prisma.InputJsonValue }),
      ...(data.isReadyForListing !== undefined && { isReadyForListing: data.isReadyForListing }),
      ...(data.lastEnrichedAt !== undefined && { lastEnrichedAt: data.lastEnrichedAt ? new Date(data.lastEnrichedAt) : null }),

      // Export Assistant
      ...(data.exportTitle !== undefined && { exportTitle: data.exportTitle }),
      ...(data.exportDescription !== undefined && { exportDescription: data.exportDescription }),
      ...(data.exportBulletsJson !== undefined && { exportBulletsJson: data.exportBulletsJson as Prisma.InputJsonValue }),
      ...(data.exportGeneratedAt !== undefined && { exportGeneratedAt: data.exportGeneratedAt ? new Date(data.exportGeneratedAt) : null }),
      ...(data.exportFromCache !== undefined && { exportFromCache: data.exportFromCache }),
      ...(data.exportModel !== undefined && { exportModel: data.exportModel }),
      ...(data.exportConfidenceTier !== undefined && { exportConfidenceTier: data.exportConfidenceTier }),

      // Classification
      ...(data.classificationStatus !== undefined && { classificationStatus: data.classificationStatus }),
      ...(data.domainCategoryId !== undefined && { domainCategoryId: data.domainCategoryId }),
      ...(data.classificationErrorMessage !== undefined && { classificationErrorMessage: data.classificationErrorMessage }),
      ...(data.classificationRequestId !== undefined && { classificationRequestId: data.classificationRequestId }),

      // Photo metadata
      ...(data.photosMetadataJson !== undefined && { photosMetadataJson: data.photosMetadataJson as Prisma.InputJsonValue }),

      // Multi-object scanning
      ...(data.attributesSummaryText !== undefined && { attributesSummaryText: data.attributesSummaryText }),
      ...(data.summaryTextUserEdited !== undefined && { summaryTextUserEdited: data.summaryTextUserEdited }),
      ...(data.sourcePhotoId !== undefined && { sourcePhotoId: data.sourcePhotoId }),

      // Listing associations
      ...(data.listingStatus !== undefined && { listingStatus: data.listingStatus }),
      ...(data.listingId !== undefined && { listingId: data.listingId }),
      ...(data.listingUrl !== undefined && { listingUrl: data.listingUrl }),

      // OCR/barcode
      ...(data.recognizedText !== undefined && { recognizedText: data.recognizedText }),
      ...(data.barcodeValue !== undefined && { barcodeValue: data.barcodeValue }),
      ...(data.labelText !== undefined && { labelText: data.labelText }),

      // Sync metadata
      syncVersion: { increment: 1 },
      clientUpdatedAt: new Date(data.clientUpdatedAt),
    },
  });

  return updatedItem;
}

/**
 * Soft delete an item
 * Sets deletedAt timestamp and increments syncVersion
 */
export async function deleteItem(userId: string, itemId: string): Promise<Item> {
  // Check ownership first
  const item = await getItemById(userId, itemId);

  const deletedItem = await prisma.item.update({
    where: { id: itemId },
    data: {
      deletedAt: new Date(),
      syncVersion: { increment: 1 },
    },
  });

  return deletedItem;
}

/**
 * Process batch sync request with conflict resolution
 * Returns results for each change and server changes since last sync
 */
export async function syncItems(
  userId: string,
  syncReq: SyncRequest
): Promise<{
  results: SyncResult[];
  serverChanges: Item[];
  syncTimestamp: string;
}> {
  const results: SyncResult[] = [];

  // Process each change
  for (const change of syncReq.changes) {
    try {
      const result = await processSyncChange(userId, change);
      results.push(result);
    } catch (error) {
      results.push({
        localId: change.localId,
        status: 'ERROR',
        error: error instanceof Error ? error.message : 'Unknown error',
      });
    }
  }

  // Fetch server changes since last sync
  const since = syncReq.lastSyncTimestamp ? new Date(syncReq.lastSyncTimestamp) : undefined;
  const serverChanges = await prisma.item.findMany({
    where: {
      userId,
      ...(since && { updatedAt: { gt: since } }),
    },
    orderBy: { updatedAt: 'asc' },
  });

  return {
    results,
    serverChanges,
    syncTimestamp: new Date().toISOString(),
  };
}

/**
 * Process a single sync change with conflict resolution
 */
async function processSyncChange(userId: string, change: SyncChange): Promise<SyncResult> {
  switch (change.action) {
    case 'CREATE': {
      if (!change.data) {
        return {
          localId: change.localId,
          status: 'ERROR',
          error: 'Missing data for CREATE action',
        };
      }

      const { item, localId } = await createItem(userId, {
        ...change.data,
        localId: change.localId,
        clientUpdatedAt: change.clientUpdatedAt,
      });

      return {
        localId,
        serverId: item.id,
        status: 'SUCCESS',
      };
    }

    case 'UPDATE': {
      if (!change.serverId || !change.syncVersion || !change.data) {
        return {
          localId: change.localId,
          status: 'ERROR',
          error: 'Missing serverId, syncVersion, or data for UPDATE action',
        };
      }

      try {
        const item = await updateItem(userId, change.serverId, {
          ...change.data,
          syncVersion: change.syncVersion,
          clientUpdatedAt: change.clientUpdatedAt,
        });

        return {
          localId: change.localId,
          serverId: item.id,
          status: 'SUCCESS',
        };
      } catch (error) {
        if (error instanceof ConflictError) {
          // Conflict detected - resolve using last-write-wins
          return await resolveConflict(userId, change);
        }
        throw error;
      }
    }

    case 'DELETE': {
      if (!change.serverId) {
        return {
          localId: change.localId,
          status: 'ERROR',
          error: 'Missing serverId for DELETE action',
        };
      }

      const item = await deleteItem(userId, change.serverId);

      return {
        localId: change.localId,
        serverId: item.id,
        status: 'SUCCESS',
      };
    }

    default:
      return {
        localId: change.localId,
        status: 'ERROR',
        error: `Unknown action: ${(change as any).action}`,
      };
  }
}

/**
 * Resolve sync conflict using last-write-wins strategy
 * Compares clientUpdatedAt timestamps
 */
async function resolveConflict(userId: string, change: SyncChange): Promise<SyncResult> {
  if (!change.serverId) {
    return {
      localId: change.localId,
      status: 'ERROR',
      error: 'Missing serverId for conflict resolution',
    };
  }

  // Get current server item
  const serverItem = await getItemById(userId, change.serverId);

  // Compare timestamps
  const clientTime = new Date(change.clientUpdatedAt).getTime();
  const serverTime = serverItem.clientUpdatedAt?.getTime() || 0;

  if (clientTime > serverTime) {
    // Client wins: Force update server with latest syncVersion
    const userPriceCents = change.data?.userPriceCents !== undefined
      ? BigInt(change.data.userPriceCents)
      : undefined;

    const item = await prisma.item.update({
      where: { id: change.serverId },
      data: {
        ...(change.data?.title !== undefined && { title: change.data.title }),
        ...(change.data?.description !== undefined && { description: change.data.description }),
        ...(change.data?.category !== undefined && { category: change.data.category }),
        ...(userPriceCents !== undefined && { userPriceCents }),
        // ... include all other fields ...
        syncVersion: { increment: 1 },
        clientUpdatedAt: new Date(change.clientUpdatedAt),
      },
    });

    return {
      localId: change.localId,
      serverId: item.id,
      status: 'CONFLICT',
      conflictResolution: 'CLIENT_WINS',
      item,
    };
  } else {
    // Server wins: Return server state for client to apply
    return {
      localId: change.localId,
      serverId: serverItem.id,
      status: 'CONFLICT',
      conflictResolution: 'SERVER_WINS',
      item: serverItem,
    };
  }
}
