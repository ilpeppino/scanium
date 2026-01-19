package com.scanium.app.items.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

data class ItemHashPair(
    val itemId: String,
    val snapshotHash: String,
)

@Dao
interface ScannedItemDao {
    @Query("SELECT * FROM scanned_items ORDER BY timestamp DESC")
    suspend fun getAll(): List<ScannedItemEntity>

    @Upsert
    suspend fun upsertAll(items: List<ScannedItemEntity>)

    @Insert
    suspend fun insertHistory(entry: ScannedItemHistoryEntity)

    @Insert
    suspend fun insertHistoryBatch(entries: List<ScannedItemHistoryEntity>)

    @Query("SELECT snapshotHash FROM scanned_item_history WHERE itemId = :itemId ORDER BY changedAt DESC LIMIT 1")
    suspend fun getLatestHistoryHash(itemId: String): String?

    @Query(
        """
        SELECT h.itemId, h.snapshotHash
        FROM scanned_item_history h
        INNER JOIN (
            SELECT itemId, MAX(changedAt) as maxChangedAt
            FROM scanned_item_history
            WHERE itemId IN (:itemIds)
            GROUP BY itemId
        ) latest ON h.itemId = latest.itemId AND h.changedAt = latest.maxChangedAt
    """,
    )
    suspend fun getLatestHistoryHashes(itemIds: List<String>): List<ItemHashPair>

    @Query("DELETE FROM scanned_items WHERE id = :itemId")
    suspend fun deleteById(itemId: String)

    @Query("DELETE FROM scanned_item_history WHERE itemId = :itemId")
    suspend fun deleteHistoryByItemId(itemId: String)

    @Query("DELETE FROM scanned_items")
    suspend fun deleteAll()

    @Query("DELETE FROM scanned_item_history")
    suspend fun deleteAllHistory()

    // Phase E: Sync queries

    /**
     * Get all items that need to be synced (needsSync = 1)
     * Used by sync engine to push local changes to server
     */
    @Query("SELECT * FROM scanned_items WHERE needsSync = 1 ORDER BY clientUpdatedAt ASC")
    suspend fun getPendingSync(): List<ScannedItemEntity>

    /**
     * Update serverId for an item (after successful CREATE on server)
     * Maps localId -> serverId
     */
    @Query("UPDATE scanned_items SET serverId = :serverId WHERE id = :localId")
    suspend fun updateServerId(
        localId: String,
        serverId: String,
    )

    /**
     * Update sync state after successful sync
     * Marks item as synced and updates lastSyncedAt
     */
    @Query(
        """
        UPDATE scanned_items
        SET needsSync = :needsSync,
            lastSyncedAt = :lastSyncedAt,
            syncVersion = :syncVersion
        WHERE id = :localId
        """,
    )
    suspend fun updateSyncState(
        localId: String,
        needsSync: Int,
        lastSyncedAt: Long?,
        syncVersion: Int,
    )

    /**
     * Mark item as deleted (soft delete)
     * Sets deletedAt timestamp and marks for sync
     */
    @Query(
        """
        UPDATE scanned_items
        SET deletedAt = :deletedAt,
            needsSync = 1,
            clientUpdatedAt = :deletedAt
        WHERE id = :itemId
        """,
    )
    suspend fun markDeleted(
        itemId: String,
        deletedAt: Long,
    )

    /**
     * Mark all items as needing sync
     * Used on first sign-in to sync existing local items
     */
    @Query("UPDATE scanned_items SET needsSync = 1")
    suspend fun markAllForSync()

    /**
     * Get count of all items (excluding deleted)
     * Used to show user how many items will be synced
     */
    @Query("SELECT COUNT(*) FROM scanned_items WHERE deletedAt IS NULL")
    suspend fun countItems(): Int

    /**
     * Delete tombstones older than specified timestamp
     * Cleanup job to remove old soft-deleted items after they've been synced
     */
    @Query(
        """
        DELETE FROM scanned_items
        WHERE deletedAt IS NOT NULL
        AND deletedAt < :deletedBefore
        AND needsSync = 0
        """,
    )
    suspend fun deleteTombstonesOlderThan(deletedBefore: Long): Int

    /**
     * Get item by serverId
     * Used to find local item when processing server changes
     */
    @Query("SELECT * FROM scanned_items WHERE serverId = :serverId LIMIT 1")
    suspend fun getByServerId(serverId: String): ScannedItemEntity?
}
