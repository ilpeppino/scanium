package com.scanium.app.items.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ScannedItemDao {
    @Query("SELECT * FROM scanned_items ORDER BY timestamp DESC")
    suspend fun getAll(): List<ScannedItemEntity>

    @Upsert
    suspend fun upsertAll(items: List<ScannedItemEntity>)

    @Insert
    suspend fun insertHistory(entry: ScannedItemHistoryEntity)

    @Query("SELECT snapshotHash FROM scanned_item_history WHERE itemId = :itemId ORDER BY changedAt DESC LIMIT 1")
    suspend fun getLatestHistoryHash(itemId: String): String?

    @Query("DELETE FROM scanned_items WHERE id = :itemId")
    suspend fun deleteById(itemId: String)

    @Query("DELETE FROM scanned_item_history WHERE itemId = :itemId")
    suspend fun deleteHistoryByItemId(itemId: String)

    @Query("DELETE FROM scanned_items")
    suspend fun deleteAll()

    @Query("DELETE FROM scanned_item_history")
    suspend fun deleteAllHistory()
}
