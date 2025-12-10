package com.scanium.app.data

import com.scanium.app.items.ScannedItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Implementation of ItemsRepository that uses Room database as the data source.
 *
 * All database operations are performed on the IO dispatcher to avoid blocking
 * the main thread.
 *
 * @param dao The Room DAO for database operations
 * @param ioDispatcher Coroutine dispatcher for IO operations (injectable for testing)
 */
class ItemsRepositoryImpl(
    private val dao: ItemsDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ItemsRepository {

    /**
     * Observes all items from the database and maps entities to domain models.
     * The Flow automatically emits whenever the database changes.
     */
    override fun observeItems(): Flow<List<ScannedItem>> {
        return dao.observeAll().map { entities ->
            entities.map { it.toScannedItem() }
        }
    }

    /**
     * Gets all items as a one-time operation.
     */
    override suspend fun getItems(): Result<List<ScannedItem>> = withContext(ioDispatcher) {
        Result.catching {
            dao.getAll().map { it.toScannedItem() }
        }
    }

    /**
     * Gets a specific item by ID.
     * Returns Success(null) if the item doesn't exist.
     */
    override suspend fun getItemById(id: String): Result<ScannedItem?> = withContext(ioDispatcher) {
        Result.catching {
            dao.getById(id)?.toScannedItem()
        }
    }

    /**
     * Checks if an item with the given ID exists in the database.
     */
    override suspend fun itemExists(id: String): Result<Boolean> = withContext(ioDispatcher) {
        Result.catching {
            dao.exists(id)
        }
    }

    /**
     * Gets the total count of items in the database.
     */
    override suspend fun getItemCount(): Result<Int> = withContext(ioDispatcher) {
        Result.catching {
            dao.getCount()
        }
    }

    /**
     * Adds a single item to the database.
     * If an item with the same ID exists, it will be replaced.
     */
    override suspend fun addItem(item: ScannedItem): Result<Unit> = withContext(ioDispatcher) {
        Result.catching {
            val entity = ScannedItemEntity.fromScannedItem(item)
            dao.insert(entity)
        }
    }

    /**
     * Adds multiple items to the database.
     * If items with the same IDs exist, they will be replaced.
     */
    override suspend fun addItems(items: List<ScannedItem>): Result<Unit> = withContext(ioDispatcher) {
        Result.catching {
            val entities = items.map { ScannedItemEntity.fromScannedItem(it) }
            dao.insertAll(entities)
        }
    }

    /**
     * Removes an item by ID.
     * Returns success even if the item doesn't exist (idempotent operation).
     */
    override suspend fun removeItem(itemId: String): Result<Unit> = withContext(ioDispatcher) {
        Result.catching {
            dao.deleteById(itemId)
            Unit
        }
    }

    /**
     * Clears all items from the database.
     */
    override suspend fun clearAll(): Result<Unit> = withContext(ioDispatcher) {
        Result.catching {
            dao.deleteAll()
            Unit
        }
    }

    /**
     * Gets items by category.
     */
    override suspend fun getItemsByCategory(category: String): Result<List<ScannedItem>> = withContext(ioDispatcher) {
        Result.catching {
            dao.getByCategory(category).map { it.toScannedItem() }
        }
    }

    /**
     * Gets items with confidence above the specified threshold.
     */
    override suspend fun getItemsByMinConfidence(minConfidence: Float): Result<List<ScannedItem>> = withContext(ioDispatcher) {
        Result.catching {
            dao.getByMinConfidence(minConfidence).map { it.toScannedItem() }
        }
    }
}
