package com.scanium.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for scanned items.
 *
 * Provides reactive queries using Flow for observing database changes,
 * and suspend functions for write operations.
 *
 * All database operations run on background threads (Room handles this automatically).
 */
@Dao
interface ItemsDao {
    /**
     * Observes all scanned items in the database.
     * Returns a Flow that emits whenever the items table changes.
     * Items are ordered by timestamp (newest first).
     */
    @Query("SELECT * FROM scanned_items ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ScannedItemEntity>>

    /**
     * Gets all scanned items as a one-time query (suspend function).
     * Items are ordered by timestamp (newest first).
     */
    @Query("SELECT * FROM scanned_items ORDER BY timestamp DESC")
    suspend fun getAll(): List<ScannedItemEntity>

    /**
     * Gets a specific item by ID.
     * Returns null if the item doesn't exist.
     */
    @Query("SELECT * FROM scanned_items WHERE id = :id")
    suspend fun getById(id: String): ScannedItemEntity?

    /**
     * Checks if an item with the given ID exists.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM scanned_items WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    /**
     * Gets the count of items in the database.
     */
    @Query("SELECT COUNT(*) FROM scanned_items")
    suspend fun getCount(): Int

    /**
     * Observes the count of items in the database.
     */
    @Query("SELECT COUNT(*) FROM scanned_items")
    fun observeCount(): Flow<Int>

    /**
     * Inserts a new item into the database.
     * If an item with the same ID already exists, it will be replaced.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ScannedItemEntity)

    /**
     * Inserts multiple items into the database.
     * If items with the same IDs already exist, they will be replaced.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ScannedItemEntity>)

    /**
     * Updates an existing item in the database.
     * Returns the number of rows updated (0 if the item doesn't exist).
     */
    @Update
    suspend fun update(item: ScannedItemEntity): Int

    /**
     * Deletes a specific item from the database.
     * Returns the number of rows deleted (0 if the item doesn't exist).
     */
    @Delete
    suspend fun delete(item: ScannedItemEntity): Int

    /**
     * Deletes an item by its ID.
     * Returns the number of rows deleted (0 if the item doesn't exist).
     */
    @Query("DELETE FROM scanned_items WHERE id = :id")
    suspend fun deleteById(id: String): Int

    /**
     * Deletes all items from the database.
     * Returns the number of rows deleted.
     */
    @Query("DELETE FROM scanned_items")
    suspend fun deleteAll(): Int

    /**
     * Gets items by category.
     */
    @Query("SELECT * FROM scanned_items WHERE category = :category ORDER BY timestamp DESC")
    suspend fun getByCategory(category: String): List<ScannedItemEntity>

    /**
     * Gets items with confidence above a threshold.
     */
    @Query("SELECT * FROM scanned_items WHERE confidence >= :minConfidence ORDER BY confidence DESC")
    suspend fun getByMinConfidence(minConfidence: Float): List<ScannedItemEntity>
}
