package com.scanium.app.items.persistence

import android.database.SQLException
import android.util.Log
import com.scanium.app.items.ScannedItem
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

interface ScannedItemStore {
    val errors: SharedFlow<PersistenceError>
    suspend fun loadAll(): List<ScannedItem>
    suspend fun upsertAll(items: List<ScannedItem>)
    suspend fun deleteById(itemId: String)
    suspend fun deleteAll()
}

object NoopScannedItemStore : ScannedItemStore {
    override val errors = MutableSharedFlow<PersistenceError>(extraBufferCapacity = 1)
    override suspend fun loadAll(): List<ScannedItem> = emptyList()
    override suspend fun upsertAll(items: List<ScannedItem>) = Unit
    override suspend fun deleteById(itemId: String) = Unit
    override suspend fun deleteAll() = Unit
}

class ScannedItemRepository(
    private val dao: ScannedItemDao,
    private val syncer: ScannedItemSyncer = NoopScannedItemSyncer
) : ScannedItemStore {
    companion object {
        private const val TAG = "ScannedItemRepository"
    }

    private val _errors = MutableSharedFlow<PersistenceError>(extraBufferCapacity = 1)
    override val errors: SharedFlow<PersistenceError> = _errors.asSharedFlow()

    override suspend fun loadAll(): List<ScannedItem> = runPersistence("load items", emptyList()) {
        dao.getAll().map { it.toModel() }
    }

    override suspend fun upsertAll(items: List<ScannedItem>) {
        runPersistence("save items", Unit) {
            if (items.isEmpty()) {
                dao.deleteAll()
                dao.deleteAllHistory()
                syncer.sync(emptyList())
                return@runPersistence
            }

            val entities = items.map { it.toEntity() }
            dao.upsertAll(entities)
            recordHistory(entities)
            syncer.sync(items)
        }
    }

    override suspend fun deleteById(itemId: String) {
        runPersistence("delete item", Unit) {
            dao.deleteById(itemId)
            dao.deleteHistoryByItemId(itemId)
        }
    }

    override suspend fun deleteAll() {
        runPersistence("clear items", Unit) {
            dao.deleteAll()
            dao.deleteAllHistory()
            syncer.sync(emptyList())
        }
    }

    private suspend fun recordHistory(entities: List<ScannedItemEntity>) {
        if (entities.isEmpty()) return

        val changedAt = System.currentTimeMillis()
        val ids = entities.map { it.id }
        val latestHashes = dao.getLatestHistoryHashes(ids).associate { it.itemId to it.snapshotHash }

        val toInsert = entities.mapNotNull { entity ->
            val snapshotHash = snapshotHash(entity)
            if (snapshotHash != latestHashes[entity.id]) {
                entity.toHistoryEntity(changedAt, snapshotHash)
            } else null
        }

        if (toInsert.isNotEmpty()) {
            dao.insertHistoryBatch(toInsert)
        }
    }

    private suspend fun <T> runPersistence(
        operation: String,
        fallback: T,
        block: suspend () -> T
    ): T {
        return try {
            block()
        } catch (e: SQLException) {
            Log.e(TAG, "Persistence failed during $operation", e)
            _errors.emit(PersistenceError(operation, e))
            fallback
        }
    }
}

data class PersistenceError(
    val operation: String,
    val throwable: Throwable
)

private fun snapshotHash(entity: ScannedItemEntity): String {
    val digest = MessageDigest.getInstance("SHA-256")

    fun updateLong(value: Long) {
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value)
        digest.update(buffer.array())
    }

    fun updateInt(value: Int) {
        val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value)
        digest.update(buffer.array())
    }

    fun updateBytes(bytes: ByteArray?) {
        updateInt(bytes?.size ?: -1)
        if (bytes != null) {
            digest.update(bytes)
        }
    }

    fun updateString(value: String?) {
        if (value == null) {
            updateInt(-1)
            return
        }
        val bytes = value.toByteArray(Charsets.UTF_8)
        updateInt(bytes.size)
        digest.update(bytes)
    }

    fun updateDouble(value: Double) {
        updateLong(java.lang.Double.doubleToLongBits(value))
    }

    fun updateFloat(value: Float) {
        updateInt(java.lang.Float.floatToIntBits(value))
    }

    updateString(entity.id)
    updateString(entity.category)
    updateDouble(entity.priceLow)
    updateDouble(entity.priceHigh)
    updateFloat(entity.confidence)
    updateLong(entity.timestamp)
    updateString(entity.labelText)
    updateString(entity.recognizedText)
    updateString(entity.barcodeValue)
    updateFloat(entity.boundingBoxLeft ?: Float.NaN)
    updateFloat(entity.boundingBoxTop ?: Float.NaN)
    updateFloat(entity.boundingBoxRight ?: Float.NaN)
    updateFloat(entity.boundingBoxBottom ?: Float.NaN)
    updateBytes(entity.thumbnailBytes)
    updateString(entity.thumbnailMimeType)
    updateInt(entity.thumbnailWidth ?: -1)
    updateInt(entity.thumbnailHeight ?: -1)
    updateBytes(entity.thumbnailRefBytes)
    updateString(entity.thumbnailRefMimeType)
    updateInt(entity.thumbnailRefWidth ?: -1)
    updateInt(entity.thumbnailRefHeight ?: -1)
    updateString(entity.fullImageUri)
    updateString(entity.fullImagePath)
    updateString(entity.listingStatus)
    updateString(entity.listingId)
    updateString(entity.listingUrl)
    updateString(entity.classificationStatus)
    updateString(entity.domainCategoryId)
    updateString(entity.classificationErrorMessage)
    updateString(entity.classificationRequestId)

    return digest.digest().joinToString("") { "%02x".format(it) }
}
