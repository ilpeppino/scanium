package com.scanium.app.data

import com.scanium.app.items.ScannedItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Fake implementation of ItemsRepository for testing.
 *
 * Stores items in memory and provides the same interface as the real repository.
 * Useful for testing ViewModels and other components that depend on ItemsRepository.
 *
 * Unlike the real implementation, this doesn't use Room database and all operations
 * are synchronous (no actual IO dispatcher needed).
 */
class FakeItemsRepository : ItemsRepository {
    private val items = MutableStateFlow<List<ScannedItem>>(emptyList())

    // For testing error scenarios
    var shouldFailOnAdd = false
    var shouldFailOnRemove = false
    var shouldFailOnClear = false

    override fun observeItems(): Flow<List<ScannedItem>> = items

    override suspend fun getItems(): Result<List<ScannedItem>> {
        return Result.success(items.value)
    }

    override suspend fun getItemById(id: String): Result<ScannedItem?> {
        val item = items.value.find { it.id == id }
        return Result.success(item)
    }

    override suspend fun itemExists(id: String): Result<Boolean> {
        val exists = items.value.any { it.id == id }
        return Result.success(exists)
    }

    override suspend fun getItemCount(): Result<Int> {
        return Result.success(items.value.size)
    }

    override suspend fun addItem(item: ScannedItem): Result<Unit> {
        if (shouldFailOnAdd) {
            return Result.failure(AppError.DatabaseError("Simulated add failure"))
        }

        // Replace if exists, otherwise add
        val currentItems = items.value.toMutableList()
        val existingIndex = currentItems.indexOfFirst { it.id == item.id }
        if (existingIndex >= 0) {
            currentItems[existingIndex] = item
        } else {
            currentItems.add(item)
        }
        items.value = currentItems
        return Result.success(Unit)
    }

    override suspend fun addItems(itemsList: List<ScannedItem>): Result<Unit> {
        if (shouldFailOnAdd) {
            return Result.failure(AppError.DatabaseError("Simulated add failure"))
        }

        val currentItems = items.value.toMutableList()
        itemsList.forEach { item ->
            val existingIndex = currentItems.indexOfFirst { it.id == item.id }
            if (existingIndex >= 0) {
                currentItems[existingIndex] = item
            } else {
                currentItems.add(item)
            }
        }
        items.value = currentItems
        return Result.success(Unit)
    }

    override suspend fun removeItem(itemId: String): Result<Unit> {
        if (shouldFailOnRemove) {
            return Result.failure(AppError.DatabaseError("Simulated remove failure"))
        }

        items.value = items.value.filterNot { it.id == itemId }
        return Result.success(Unit)
    }

    override suspend fun clearAll(): Result<Unit> {
        if (shouldFailOnClear) {
            return Result.failure(AppError.DatabaseError("Simulated clear failure"))
        }

        items.value = emptyList()
        return Result.success(Unit)
    }

    override suspend fun getItemsByCategory(category: String): Result<List<ScannedItem>> {
        val filtered = items.value.filter { it.category.name == category }
        return Result.success(filtered)
    }

    override suspend fun getItemsByMinConfidence(minConfidence: Float): Result<List<ScannedItem>> {
        val filtered = items.value.filter { it.confidence >= minConfidence }
            .sortedByDescending { it.confidence }
        return Result.success(filtered)
    }

    /**
     * Test helper: Get current items synchronously
     */
    fun getItemsSync(): List<ScannedItem> = items.value

    /**
     * Test helper: Clear all items without error simulation
     */
    fun clearForTest() {
        items.value = emptyList()
    }
}
