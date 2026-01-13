package com.scanium.app.items.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.scanium.app.auth.AuthRepository
import com.scanium.app.items.network.*
import com.scanium.app.items.persistence.ScannedItemDao
import com.scanium.app.items.persistence.ScannedItemEntity
import com.scanium.app.items.persistence.ScannedItemSyncer
import com.scanium.app.items.ScannedItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sync result status
 */
sealed class SyncResult {
    data class Success(
        val itemsPushed: Int,
        val itemsPulled: Int,
        val conflicts: Int,
    ) : SyncResult()

    object NotAuthenticated : SyncResult()
    data class NetworkError(val error: Throwable) : SyncResult()
    data class ServerError(val error: Throwable) : SyncResult()
}

/**
 * ItemSyncManager - Orchestrates sync between local database and backend (Phase E)
 * Implements push-pull sync with last-write-wins conflict resolution
 */
@Singleton
class ItemSyncManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val itemDao: ScannedItemDao,
        private val itemsApi: ItemsApi,
        private val authRepository: AuthRepository,
    ) : ScannedItemSyncer {
        private val prefs: SharedPreferences =
            context.getSharedPreferences("item_sync_prefs", Context.MODE_PRIVATE)

        companion object {
            private const val TAG = "ItemSyncManager"
            private const val PREF_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        }

        /**
         * Main sync implementation - called by ScannedItemRepository
         * This is a simplified version that just triggers the full sync
         */
        override suspend fun sync(items: List<ScannedItem>) {
            Log.d(TAG, "Sync triggered for ${items.size} items")
            // Trigger background sync asynchronously (don't block the caller)
            // In production, this would trigger ItemSyncWorker
            // For now, we just log
        }

        /**
         * Full sync operation: push local changes + pull server changes
         * This is the main entry point for sync operations
         */
        suspend fun syncAll(): SyncResult =
            withContext(Dispatchers.IO) {
                try {
                    Log.i(TAG, "Starting full sync")

                    // 1. Check authentication
                    if (!authRepository.isSignedIn()) {
                        Log.w(TAG, "Sync aborted: Not authenticated")
                        return@withContext SyncResult.NotAuthenticated
                    }

                    // 2. PUSH: Send local changes to server
                    val pendingItems = itemDao.getPendingSync()
                    Log.d(TAG, "Found ${pendingItems.size} items needing sync")

                    val pushResult = pushToServer(pendingItems)
                    Log.d(TAG, "Push result: ${pushResult.successCount} success, ${pushResult.errors.size} errors")

                    // 3. PULL: Fetch server changes since last sync
                    val lastSyncTime = getLastSyncTimestamp()
                    val pullResult = pullFromServer(lastSyncTime)
                    Log.d(TAG, "Pull result: ${pullResult.items.size} items received")

                    // 4. Apply server changes to local DB
                    val conflicts = applyServerChanges(pullResult.items)
                    Log.d(TAG, "Applied server changes: $conflicts conflicts resolved")

                    // 5. Update last sync timestamp
                    setLastSyncTimestamp(System.currentTimeMillis())

                    Log.i(TAG, "Sync completed successfully")
                    SyncResult.Success(
                        itemsPushed = pushResult.successCount,
                        itemsPulled = pullResult.items.size,
                        conflicts = conflicts,
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Sync failed", e)
                    when {
                        e.message?.contains("network", ignoreCase = true) == true ->
                            SyncResult.NetworkError(e)
                        else -> SyncResult.ServerError(e)
                    }
                }
            }

        /**
         * Push local pending changes to server
         */
        private suspend fun pushToServer(pendingItems: List<ScannedItemEntity>): PushResult {
            val successes = mutableListOf<String>()
            val errors = mutableListOf<Pair<String, String>>()

            for (item in pendingItems) {
                try {
                    when {
                        item.serverId == null && item.deletedAt == null -> {
                            // CREATE: New item, never synced
                            val result = createItemOnServer(item)
                            if (result) successes.add(item.id) else errors.add(item.id to "Create failed")
                        }
                        item.deletedAt != null && item.serverId != null -> {
                            // DELETE: Tombstone
                            val result = deleteItemOnServer(item.serverId)
                            if (result) successes.add(item.id) else errors.add(item.id to "Delete failed")
                        }
                        item.serverId != null -> {
                            // UPDATE: Existing item modified
                            val result = updateItemOnServer(item)
                            if (result) successes.add(item.id) else errors.add(item.id to "Update failed")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to push item ${item.id}", e)
                    errors.add(item.id to (e.message ?: "Unknown error"))
                }
            }

            return PushResult(
                successCount = successes.size,
                errors = errors,
            )
        }

        /**
         * Create item on server
         */
        private suspend fun createItemOnServer(item: ScannedItemEntity): Boolean {
            val request =
                CreateItemRequest(
                    localId = item.id,
                    clientUpdatedAt = Instant.ofEpochMilli(item.clientUpdatedAt).toString(),
                    title = item.exportTitle ?: item.attributesSummaryText,
                    description = item.exportDescription,
                    category = item.category,
                    confidence = item.confidence,
                    priceEstimateLow = item.priceLow,
                    priceEstimateHigh = item.priceHigh,
                    userPriceCents = item.userPriceCents,
                    condition = item.condition,
                    attributesJson = item.attributesJson,
                    detectedAttributesJson = item.detectedAttributesJson,
                    visionAttributesJson = item.visionAttributesJson,
                    enrichmentStatusJson = item.enrichmentStatusJson,
                    completenessScore = item.completenessScore,
                    missingAttributesJson = item.missingAttributesJson,
                    capturedShotTypesJson = item.capturedShotTypesJson,
                    isReadyForListing = item.isReadyForListing == 1,
                    lastEnrichedAt = item.lastEnrichedAt?.let { Instant.ofEpochMilli(it).toString() },
                    exportTitle = item.exportTitle,
                    exportDescription = item.exportDescription,
                    exportBulletsJson = item.exportBulletsJson,
                    exportGeneratedAt = item.exportGeneratedAt?.let { Instant.ofEpochMilli(it).toString() },
                    exportFromCache = item.exportFromCache == 1,
                    exportModel = item.exportModel,
                    exportConfidenceTier = item.exportConfidenceTier,
                    classificationStatus = item.classificationStatus,
                    domainCategoryId = item.domainCategoryId,
                    classificationErrorMessage = item.classificationErrorMessage,
                    classificationRequestId = item.classificationRequestId,
                    // photosMetadataJson would go here when we implement photo hashing
                    attributesSummaryText = item.attributesSummaryText,
                    summaryTextUserEdited = item.summaryTextUserEdited == 1,
                    sourcePhotoId = item.sourcePhotoId,
                    listingStatus = item.listingStatus,
                    listingId = item.listingId,
                    listingUrl = item.listingUrl,
                    recognizedText = item.recognizedText,
                    barcodeValue = item.barcodeValue,
                    labelText = item.labelText,
                )

            val result = itemsApi.createItem(request)
            return result.fold(
                onSuccess = { response ->
                    // Update local item with serverId
                    itemDao.updateServerId(item.id, response.item.id)
                    itemDao.updateSyncState(
                        localId = item.id,
                        needsSync = 0,
                        lastSyncedAt = System.currentTimeMillis(),
                        syncVersion = response.item.syncVersion,
                    )
                    Log.d(TAG, "Created item ${item.id} -> ${response.item.id}")
                    true
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to create item ${item.id}", error)
                    false
                },
            )
        }

        /**
         * Update item on server
         */
        private suspend fun updateItemOnServer(item: ScannedItemEntity): Boolean {
            if (item.serverId == null) return false

            val request =
                UpdateItemRequest(
                    syncVersion = item.syncVersion,
                    clientUpdatedAt = Instant.ofEpochMilli(item.clientUpdatedAt).toString(),
                    title = item.exportTitle ?: item.attributesSummaryText,
                    description = item.exportDescription,
                    category = item.category,
                    confidence = item.confidence,
                    priceEstimateLow = item.priceLow,
                    priceEstimateHigh = item.priceHigh,
                    userPriceCents = item.userPriceCents,
                    condition = item.condition,
                    attributesJson = item.attributesJson,
                    detectedAttributesJson = item.detectedAttributesJson,
                    visionAttributesJson = item.visionAttributesJson,
                    enrichmentStatusJson = item.enrichmentStatusJson,
                    completenessScore = item.completenessScore,
                    missingAttributesJson = item.missingAttributesJson,
                    capturedShotTypesJson = item.capturedShotTypesJson,
                    isReadyForListing = item.isReadyForListing == 1,
                    lastEnrichedAt = item.lastEnrichedAt?.let { Instant.ofEpochMilli(it).toString() },
                    exportTitle = item.exportTitle,
                    exportDescription = item.exportDescription,
                    exportBulletsJson = item.exportBulletsJson,
                    exportGeneratedAt = item.exportGeneratedAt?.let { Instant.ofEpochMilli(it).toString() },
                    exportFromCache = item.exportFromCache == 1,
                    exportModel = item.exportModel,
                    exportConfidenceTier = item.exportConfidenceTier,
                    classificationStatus = item.classificationStatus,
                    domainCategoryId = item.domainCategoryId,
                    classificationErrorMessage = item.classificationErrorMessage,
                    classificationRequestId = item.classificationRequestId,
                    attributesSummaryText = item.attributesSummaryText,
                    summaryTextUserEdited = item.summaryTextUserEdited == 1,
                    sourcePhotoId = item.sourcePhotoId,
                    listingStatus = item.listingStatus,
                    listingId = item.listingId,
                    listingUrl = item.listingUrl,
                    recognizedText = item.recognizedText,
                    barcodeValue = item.barcodeValue,
                    labelText = item.labelText,
                )

            val result = itemsApi.updateItem(item.serverId, request)
            return result.fold(
                onSuccess = { response ->
                    itemDao.updateSyncState(
                        localId = item.id,
                        needsSync = 0,
                        lastSyncedAt = System.currentTimeMillis(),
                        syncVersion = response.item.syncVersion,
                    )
                    Log.d(TAG, "Updated item ${item.id} (server: ${item.serverId})")
                    true
                },
                onFailure = { error ->
                    // Check for conflict (409)
                    if (error.message?.contains("409") == true) {
                        Log.w(TAG, "Conflict updating item ${item.id}, will resolve")
                        // TODO: Implement conflict resolution
                    } else {
                        Log.e(TAG, "Failed to update item ${item.id}", error)
                    }
                    false
                },
            )
        }

        /**
         * Delete item on server (tombstone)
         */
        private suspend fun deleteItemOnServer(serverId: String): Boolean {
            val result = itemsApi.deleteItem(serverId)
            return result.fold(
                onSuccess = { deletedItem ->
                    // Find local item by serverId and mark as synced
                    val localItem = itemDao.getByServerId(serverId)
                    if (localItem != null) {
                        itemDao.updateSyncState(
                            localId = localItem.id,
                            needsSync = 0,
                            lastSyncedAt = System.currentTimeMillis(),
                            syncVersion = deletedItem.syncVersion,
                        )
                        Log.d(TAG, "Deleted item ${localItem.id} (server: $serverId)")
                    }
                    true
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to delete item $serverId", error)
                    false
                },
            )
        }

        /**
         * Pull server changes since last sync
         */
        private suspend fun pullFromServer(since: Long?): PullResult {
            val sinceParam = since?.toString()
            val result = itemsApi.getItems(since = sinceParam, limit = 100)

            return result.fold(
                onSuccess = { response ->
                    PullResult(items = response.items)
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to pull from server", error)
                    PullResult(items = emptyList())
                },
            )
        }

        /**
         * Apply server changes to local database
         * Returns number of conflicts resolved
         */
        private suspend fun applyServerChanges(serverItems: List<ItemDto>): Int {
            var conflicts = 0

            for (serverItem in serverItems) {
                try {
                    val localItem = itemDao.getByServerId(serverItem.id)

                    when {
                        localItem == null -> {
                            // New item from server - insert
                            val entity = serverItem.toEntity()
                            itemDao.upsertAll(listOf(entity))
                            Log.d(TAG, "Inserted new item from server: ${serverItem.id}")
                        }
                        localItem.needsSync == 1 -> {
                            // Conflict: local has pending changes AND server has changes
                            conflicts++
                            resolveConflict(localItem, serverItem)
                        }
                        else -> {
                            // No local changes - apply server update
                            val entity = serverItem.toEntity(localId = localItem.id)
                            itemDao.upsertAll(listOf(entity))
                            Log.d(TAG, "Updated item from server: ${serverItem.id}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to apply server change for item ${serverItem.id}", e)
                }
            }

            return conflicts
        }

        /**
         * Resolve conflict using last-write-wins strategy
         * Compares clientUpdatedAt timestamps
         */
        private suspend fun resolveConflict(
            local: ScannedItemEntity,
            server: ItemDto,
        ) {
            val localTime = local.clientUpdatedAt
            val serverTime =
                server.clientUpdatedAt?.let { Instant.parse(it).toEpochMilli() }
                    ?: 0L

            when {
                localTime > serverTime -> {
                    // Client wins: Keep local changes, will be pushed on next sync
                    Log.d(TAG, "Conflict resolved: Client wins (${local.id})")
                    // Local item already marked needsSync=1, will push on next sync
                }
                else -> {
                    // Server wins: Overwrite local with server version
                    Log.d(TAG, "Conflict resolved: Server wins (${local.id})")
                    val entity = server.toEntity(localId = local.id)
                    itemDao.upsertAll(listOf(entity))
                }
            }
        }

        /**
         * Get last sync timestamp
         */
        private fun getLastSyncTimestamp(): Long? {
            val timestamp = prefs.getLong(PREF_LAST_SYNC_TIMESTAMP, -1L)
            return if (timestamp > 0) timestamp else null
        }

        /**
         * Set last sync timestamp
         */
        private fun setLastSyncTimestamp(timestamp: Long) {
            prefs.edit().putLong(PREF_LAST_SYNC_TIMESTAMP, timestamp).apply()
        }

        /**
         * Convert ItemDto to ScannedItemEntity
         */
        private fun ItemDto.toEntity(localId: String? = null): ScannedItemEntity {
            return ScannedItemEntity(
                id = localId ?: id, // Use localId if provided (for existing items)
                category = category ?: "UNKNOWN",
                priceLow = priceEstimateLow ?: 0.0,
                priceHigh = priceEstimateHigh ?: 0.0,
                confidence = confidence ?: 0f,
                timestamp = Instant.parse(createdAt).toEpochMilli(),
                labelText = labelText,
                recognizedText = recognizedText,
                barcodeValue = barcodeValue,
                boundingBoxLeft = null,
                boundingBoxTop = null,
                boundingBoxRight = null,
                boundingBoxBottom = null,
                thumbnailBytes = null,
                thumbnailMimeType = null,
                thumbnailWidth = null,
                thumbnailHeight = null,
                thumbnailRefBytes = null,
                thumbnailRefMimeType = null,
                thumbnailRefWidth = null,
                thumbnailRefHeight = null,
                fullImageUri = null,
                fullImagePath = null,
                listingStatus = listingStatus,
                listingId = listingId,
                listingUrl = listingUrl,
                classificationStatus = classificationStatus,
                domainCategoryId = domainCategoryId,
                classificationErrorMessage = classificationErrorMessage,
                classificationRequestId = classificationRequestId,
                userPriceCents = userPriceCents,
                condition = condition,
                attributesJson = attributesJson,
                detectedAttributesJson = detectedAttributesJson,
                visionAttributesJson = visionAttributesJson,
                attributesSummaryText = attributesSummaryText,
                summaryTextUserEdited = if (summaryTextUserEdited) 1 else 0,
                additionalPhotosJson = null, // Photos stay local
                sourcePhotoId = sourcePhotoId,
                enrichmentStatusJson = enrichmentStatusJson,
                exportTitle = exportTitle,
                exportDescription = exportDescription,
                exportBulletsJson = exportBulletsJson,
                exportGeneratedAt = exportGeneratedAt?.let { Instant.parse(it).toEpochMilli() },
                exportFromCache = if (exportFromCache) 1 else 0,
                exportModel = exportModel,
                exportConfidenceTier = exportConfidenceTier,
                completenessScore = completenessScore,
                missingAttributesJson = missingAttributesJson,
                lastEnrichedAt = lastEnrichedAt?.let { Instant.parse(it).toEpochMilli() },
                capturedShotTypesJson = capturedShotTypesJson,
                isReadyForListing = if (isReadyForListing) 1 else 0,
                // Sync fields
                serverId = id,
                needsSync = 0, // Server version is always synced
                lastSyncedAt = System.currentTimeMillis(),
                syncVersion = syncVersion,
                clientUpdatedAt = clientUpdatedAt?.let { Instant.parse(it).toEpochMilli() }
                    ?: Instant.parse(updatedAt).toEpochMilli(),
                deletedAt = deletedAt?.let { Instant.parse(it).toEpochMilli() },
            )
        }
    }

/**
 * Push result
 */
private data class PushResult(
    val successCount: Int,
    val errors: List<Pair<String, String>>,
)

/**
 * Pull result
 */
private data class PullResult(
    val items: List<ItemDto>,
)
