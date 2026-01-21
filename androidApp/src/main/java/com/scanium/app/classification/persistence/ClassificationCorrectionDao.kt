package com.scanium.app.classification.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for classification corrections.
 * Supports local storage and sync tracking.
 */
@Dao
interface ClassificationCorrectionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(correction: ClassificationCorrectionEntity): Long

    @Query("SELECT * FROM classification_corrections WHERE itemId = :itemId ORDER BY correctedAt DESC")
    suspend fun getCorrectionsForItem(itemId: String): List<ClassificationCorrectionEntity>

    @Query("SELECT * FROM classification_corrections WHERE syncedToBackend = 0 ORDER BY correctedAt ASC")
    suspend fun getUnsyncedCorrections(): List<ClassificationCorrectionEntity>

    @Query("UPDATE classification_corrections SET syncedToBackend = 1, syncedAt = :syncedAt WHERE id = :id")
    suspend fun markAsSynced(id: String, syncedAt: Long)

    @Query("SELECT COUNT(*) FROM classification_corrections WHERE syncedToBackend = 0")
    fun observeUnsyncedCount(): Flow<Int>

    @Query("DELETE FROM classification_corrections WHERE correctedAt < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long): Int
}
