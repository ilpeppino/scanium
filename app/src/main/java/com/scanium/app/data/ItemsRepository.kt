package com.scanium.app.data

import com.scanium.app.items.ScannedItem
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for scanned items.
 *
 * Abstracts the data layer from the ViewModel, allowing for easier testing
 * and potential future addition of remote data sources.
 *
 * All operations return Flow for reactive updates or Result for error handling.
 */
interface ItemsRepository {
    /**
     * Observes all scanned items.
     * Emits a new list whenever items are added, removed, or modified.
     */
    fun observeItems(): Flow<List<ScannedItem>>

    /**
     * Gets all items as a one-time operation.
     */
    suspend fun getItems(): Result<List<ScannedItem>>

    /**
     * Gets a specific item by ID.
     */
    suspend fun getItemById(id: String): Result<ScannedItem?>

    /**
     * Checks if an item exists.
     */
    suspend fun itemExists(id: String): Result<Boolean>

    /**
     * Gets the count of items.
     */
    suspend fun getItemCount(): Result<Int>

    /**
     * Adds a single item to the repository.
     */
    suspend fun addItem(item: ScannedItem): Result<Unit>

    /**
     * Adds multiple items to the repository.
     */
    suspend fun addItems(items: List<ScannedItem>): Result<Unit>

    /**
     * Removes an item by ID.
     */
    suspend fun removeItem(itemId: String): Result<Unit>

    /**
     * Clears all items from the repository.
     */
    suspend fun clearAll(): Result<Unit>

    /**
     * Gets items by category.
     */
    suspend fun getItemsByCategory(category: String): Result<List<ScannedItem>>

    /**
     * Gets items with confidence above a threshold.
     */
    suspend fun getItemsByMinConfidence(minConfidence: Float): Result<List<ScannedItem>>
}
