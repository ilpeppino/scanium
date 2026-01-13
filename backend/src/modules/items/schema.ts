/**
 * Zod schemas for Items API (Phase E: Multi-device sync)
 * Validates requests and responses for item CRUD and sync operations
 */

import { z } from 'zod';

// Photo metadata structure (no actual image data)
export const photoMetadataSchema = z.object({
  id: z.string(),
  type: z.enum(['primary', 'additional', 'closeup']),
  capturedAt: z.string(), // ISO 8601
  hash: z.string(), // SHA-256 hash
  width: z.number().int().positive().optional(),
  height: z.number().int().positive().optional(),
  mimeType: z.string().optional(), // e.g., 'image/jpeg'
});

// Item create request
export const createItemSchema = z.object({
  localId: z.string().uuid(), // Client-generated UUID for correlation
  title: z.string().optional(),
  description: z.string().optional(),
  category: z.string().optional(),
  confidence: z.number().min(0).max(1).optional(),
  priceEstimateLow: z.number().optional(),
  priceEstimateHigh: z.number().optional(),
  userPriceCents: z.bigint().or(z.number()).optional(),
  condition: z.string().optional(),

  // JSON fields
  attributesJson: z.any().optional(),
  detectedAttributesJson: z.any().optional(),
  visionAttributesJson: z.any().optional(),
  enrichmentStatusJson: z.any().optional(),

  // Quality metrics
  completenessScore: z.number().int().min(0).max(100).default(0),
  missingAttributesJson: z.any().optional(),
  capturedShotTypesJson: z.any().optional(),
  isReadyForListing: z.boolean().default(false),
  lastEnrichedAt: z.string().optional(), // ISO 8601

  // Export Assistant
  exportTitle: z.string().optional(),
  exportDescription: z.string().optional(),
  exportBulletsJson: z.any().optional(),
  exportGeneratedAt: z.string().optional(),
  exportFromCache: z.boolean().default(false),
  exportModel: z.string().optional(),
  exportConfidenceTier: z.string().optional(),

  // Classification
  classificationStatus: z.string().default('PENDING'),
  domainCategoryId: z.string().optional(),
  classificationErrorMessage: z.string().optional(),
  classificationRequestId: z.string().optional(),

  // Photo metadata
  photosMetadataJson: z.object({
    photos: z.array(photoMetadataSchema),
  }).optional(),

  // Multi-object scanning
  attributesSummaryText: z.string().optional(),
  summaryTextUserEdited: z.boolean().default(false),
  sourcePhotoId: z.string().optional(),

  // Listing associations
  listingStatus: z.string().default('NOT_LISTED'),
  listingId: z.string().optional(),
  listingUrl: z.string().optional(),

  // OCR/barcode
  recognizedText: z.string().optional(),
  barcodeValue: z.string().optional(),
  labelText: z.string().optional(),

  // Sync metadata
  clientUpdatedAt: z.string(), // ISO 8601 - REQUIRED for conflict resolution
});

// Item update request (all fields optional except syncVersion and clientUpdatedAt)
export const updateItemSchema = z.object({
  title: z.string().optional(),
  description: z.string().optional(),
  category: z.string().optional(),
  confidence: z.number().min(0).max(1).optional(),
  priceEstimateLow: z.number().optional(),
  priceEstimateHigh: z.number().optional(),
  userPriceCents: z.bigint().or(z.number()).optional(),
  condition: z.string().optional(),

  // JSON fields
  attributesJson: z.any().optional(),
  detectedAttributesJson: z.any().optional(),
  visionAttributesJson: z.any().optional(),
  enrichmentStatusJson: z.any().optional(),

  // Quality metrics
  completenessScore: z.number().int().min(0).max(100).optional(),
  missingAttributesJson: z.any().optional(),
  capturedShotTypesJson: z.any().optional(),
  isReadyForListing: z.boolean().optional(),
  lastEnrichedAt: z.string().optional(),

  // Export Assistant
  exportTitle: z.string().optional(),
  exportDescription: z.string().optional(),
  exportBulletsJson: z.any().optional(),
  exportGeneratedAt: z.string().optional(),
  exportFromCache: z.boolean().optional(),
  exportModel: z.string().optional(),
  exportConfidenceTier: z.string().optional(),

  // Classification
  classificationStatus: z.string().optional(),
  domainCategoryId: z.string().optional(),
  classificationErrorMessage: z.string().optional(),
  classificationRequestId: z.string().optional(),

  // Photo metadata
  photosMetadataJson: z.object({
    photos: z.array(photoMetadataSchema),
  }).optional(),

  // Multi-object scanning
  attributesSummaryText: z.string().optional(),
  summaryTextUserEdited: z.boolean().optional(),
  sourcePhotoId: z.string().optional(),

  // Listing associations
  listingStatus: z.string().optional(),
  listingId: z.string().optional(),
  listingUrl: z.string().optional(),

  // OCR/barcode
  recognizedText: z.string().optional(),
  barcodeValue: z.string().optional(),
  labelText: z.string().optional(),

  // Sync metadata - REQUIRED for optimistic locking
  syncVersion: z.number().int().positive(),
  clientUpdatedAt: z.string(), // ISO 8601
});

// Get items query parameters
export const getItemsQuerySchema = z.object({
  since: z.string().optional(), // Unix timestamp in ms
  limit: z.coerce.number().int().min(1).max(500).default(100),
  includeDeleted: z.coerce.boolean().default(true),
});

// Sync change action
export const syncChangeSchema = z.object({
  action: z.enum(['CREATE', 'UPDATE', 'DELETE']),
  localId: z.string().uuid(),
  serverId: z.string().uuid().optional(), // Required for UPDATE/DELETE
  syncVersion: z.number().int().positive().optional(), // Required for UPDATE
  clientUpdatedAt: z.string(), // ISO 8601
  data: createItemSchema.omit({ localId: true, clientUpdatedAt: true }).optional(), // Required for CREATE/UPDATE
});

// Sync batch request
export const syncRequestSchema = z.object({
  clientTimestamp: z.string(), // ISO 8601 - client's current time
  lastSyncTimestamp: z.string().nullable().optional(), // Last successful sync time
  changes: z.array(syncChangeSchema),
});

// Sync result for individual change
export const syncResultSchema = z.object({
  localId: z.string().uuid(),
  serverId: z.string().uuid().optional(),
  status: z.enum(['SUCCESS', 'CONFLICT', 'ERROR']),
  error: z.string().optional(),
  conflictResolution: z.enum(['SERVER_WINS', 'CLIENT_WINS']).optional(),
  item: z.any().optional(), // Latest server state on conflict
});

// Sync response
export const syncResponseSchema = z.object({
  results: z.array(syncResultSchema),
  serverChanges: z.array(z.any()), // Full Item objects
  syncTimestamp: z.string(), // ISO 8601 - server timestamp for next sync
  correlationId: z.string(),
});

// Type exports
export type CreateItemRequest = z.infer<typeof createItemSchema>;
export type UpdateItemRequest = z.infer<typeof updateItemSchema>;
export type GetItemsQuery = z.infer<typeof getItemsQuerySchema>;
export type SyncChange = z.infer<typeof syncChangeSchema>;
export type SyncRequest = z.infer<typeof syncRequestSchema>;
export type SyncResult = z.infer<typeof syncResultSchema>;
export type SyncResponse = z.infer<typeof syncResponseSchema>;
