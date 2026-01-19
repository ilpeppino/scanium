package com.scanium.app.items.state

import android.util.Log
import com.scanium.app.ScannedItem
import com.scanium.shared.core.models.model.ImageRef

internal class ItemsPersistence(
    private val itemsStore: com.scanium.app.items.persistence.ScannedItemStore,
) {
    suspend fun loadAll(): List<ScannedItem> = itemsStore.loadAll()

    suspend fun upsertAll(items: List<ScannedItem>) {
        itemsStore.upsertAll(items)
    }

    suspend fun deleteAll() {
        itemsStore.deleteAll()
    }

    fun logPersistenceStats(
        operation: String,
        items: List<ScannedItem>,
    ) {
        val totalItems = items.size
        val withThumbnailBytes = items.count { it.thumbnail is ImageRef.Bytes }
        val withThumbnailRefBytes = items.count { it.thumbnailRef is ImageRef.Bytes }
        val withCacheKey =
            items.count {
                it.thumbnail is ImageRef.CacheKey || it.thumbnailRef is ImageRef.CacheKey
            }
        val withNoThumbnail =
            items.count {
                it.thumbnail == null && it.thumbnailRef == null
            }
        val missingThumbnailWithUri =
            items.count {
                (it.thumbnail == null && it.thumbnailRef == null) && it.fullImageUri != null
            }

        Log.i(TAG, "┌─────────────────────────────────────────────────────────────")
        Log.i(TAG, "│ PERSISTENCE STATS ($operation)")
        Log.i(TAG, "├─────────────────────────────────────────────────────────────")
        Log.i(TAG, "│ Total items: $totalItems")
        Log.i(TAG, "│ With thumbnail Bytes: $withThumbnailBytes")
        Log.i(TAG, "│ With thumbnailRef Bytes: $withThumbnailRefBytes")
        Log.i(TAG, "│ With CacheKey (unexpected after load): $withCacheKey")
        Log.i(TAG, "│ With no thumbnail: $withNoThumbnail")
        Log.i(TAG, "│   - Recoverable (has fullImageUri): $missingThumbnailWithUri")
        Log.i(TAG, "└─────────────────────────────────────────────────────────────")

        if (withCacheKey > 0) {
            Log.w(TAG, "WARNING: $withCacheKey items have CacheKey thumbnails after DB load!")
        }

        if (withNoThumbnail > 0) {
            val affectedIds =
                items
                    .filter { it.thumbnail == null && it.thumbnailRef == null }
                    .map { it.id }
            Log.w(TAG, "Items with missing thumbnails (legacy bug): $affectedIds")
        }
    }

    companion object {
        private const val TAG = "ItemsStateManager"
    }
}
