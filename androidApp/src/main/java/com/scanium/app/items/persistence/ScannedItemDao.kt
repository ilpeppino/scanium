package com.scanium.app.items.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

data class ItemHashPair(val itemId: String, val snapshotHash: String)

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
}
