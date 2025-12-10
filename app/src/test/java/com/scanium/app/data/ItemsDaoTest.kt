package com.scanium.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for ItemsDao using in-memory Room database.
 *
 * Uses Robolectric to provide Android context for Room database testing.
 * Tests verify all DAO operations including:
 * - Insert, update, delete operations
 * - Query operations (getAll, getById, exists)
 * - Flow-based reactive queries
 * - Filtering by category and confidence
 */
@RunWith(RobolectricTestRunner::class)
class ItemsDaoTest {

    private lateinit var database: ScaniumDatabase
    private lateinit var dao: ItemsDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Create an in-memory database for testing
        database = Room.inMemoryDatabaseBuilder(context, ScaniumDatabase::class.java)
            .allowMainThreadQueries() // Only for testing
            .build()
        dao = database.itemsDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun whenInsertingItem_thenItemCanBeRetrieved() = runTest {
        // Arrange
        val item = createTestEntity(id = "test-1")

        // Act
        dao.insert(item)
        val retrieved = dao.getById("test-1")

        // Assert
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.id).isEqualTo("test-1")
        assertThat(retrieved?.category).isEqualTo("FASHION")
    }

    @Test
    fun whenInsertingMultipleItems_thenAllCanBeRetrieved() = runTest {
        // Arrange
        val items = listOf(
            createTestEntity(id = "test-1", category = "FASHION"),
            createTestEntity(id = "test-2", category = "ELECTRONICS"),
            createTestEntity(id = "test-3", category = "HOME_GOOD")
        )

        // Act
        dao.insertAll(items)
        val allItems = dao.getAll()

        // Assert
        assertThat(allItems).hasSize(3)
        assertThat(allItems.map { it.id }).containsExactly("test-1", "test-2", "test-3")
    }

    @Test
    fun whenInsertingDuplicateId_thenItemIsReplaced() = runTest {
        // Arrange
        val item1 = createTestEntity(id = "test-1", category = "FASHION", priceLow = 10.0)
        val item2 = createTestEntity(id = "test-1", category = "ELECTRONICS", priceLow = 50.0)

        // Act
        dao.insert(item1)
        dao.insert(item2) // Should replace item1

        val retrieved = dao.getById("test-1")
        val count = dao.getCount()

        // Assert
        assertThat(count).isEqualTo(1) // Only one item
        assertThat(retrieved?.category).isEqualTo("ELECTRONICS") // Second item kept
        assertThat(retrieved?.priceLow).isWithin(0.01).of(50.0)
    }

    @Test
    fun whenDeletingItem_thenItemIsRemoved() = runTest {
        // Arrange
        val item = createTestEntity(id = "test-1")
        dao.insert(item)

        // Act
        val deleted = dao.delete(item)
        val retrieved = dao.getById("test-1")

        // Assert
        assertThat(deleted).isEqualTo(1) // One row deleted
        assertThat(retrieved).isNull()
    }

    @Test
    fun whenDeletingById_thenItemIsRemoved() = runTest {
        // Arrange
        val item = createTestEntity(id = "test-1")
        dao.insert(item)

        // Act
        val deleted = dao.deleteById("test-1")
        val retrieved = dao.getById("test-1")

        // Assert
        assertThat(deleted).isEqualTo(1)
        assertThat(retrieved).isNull()
    }

    @Test
    fun whenDeletingNonExistentItem_thenReturnsZero() = runTest {
        // Act
        val deleted = dao.deleteById("non-existent")

        // Assert
        assertThat(deleted).isEqualTo(0)
    }

    @Test
    fun whenDeletingAll_thenAllItemsRemoved() = runTest {
        // Arrange
        dao.insertAll(
            listOf(
                createTestEntity(id = "test-1"),
                createTestEntity(id = "test-2"),
                createTestEntity(id = "test-3")
            )
        )

        // Act
        val deleted = dao.deleteAll()
        val allItems = dao.getAll()

        // Assert
        assertThat(deleted).isEqualTo(3)
        assertThat(allItems).isEmpty()
    }

    @Test
    fun whenUpdatingItem_thenChangesArePersisted() = runTest {
        // Arrange
        val original = createTestEntity(id = "test-1", priceLow = 10.0)
        dao.insert(original)

        // Act
        val updated = original.copy(priceLow = 50.0)
        val rowsUpdated = dao.update(updated)
        val retrieved = dao.getById("test-1")

        // Assert
        assertThat(rowsUpdated).isEqualTo(1)
        assertThat(retrieved?.priceLow).isWithin(0.01).of(50.0)
    }

    @Test
    fun whenCheckingExists_thenReturnsCorrectValue() = runTest {
        // Arrange
        val item = createTestEntity(id = "test-1")
        dao.insert(item)

        // Act & Assert
        assertThat(dao.exists("test-1")).isTrue()
        assertThat(dao.exists("non-existent")).isFalse()
    }

    @Test
    fun whenGettingCount_thenReturnsCorrectNumber() = runTest {
        // Act & Assert - Initially empty
        assertThat(dao.getCount()).isEqualTo(0)

        // Add items
        dao.insert(createTestEntity(id = "test-1"))
        assertThat(dao.getCount()).isEqualTo(1)

        dao.insert(createTestEntity(id = "test-2"))
        assertThat(dao.getCount()).isEqualTo(2)

        // Remove item
        dao.deleteById("test-1")
        assertThat(dao.getCount()).isEqualTo(1)
    }

    @Test
    fun whenObservingItems_thenFlowEmitsUpdates() = runTest {
        // Act & Assert - Initially empty
        val initialItems = dao.observeAll().first()
        assertThat(initialItems).isEmpty()

        // Add item and observe
        dao.insert(createTestEntity(id = "test-1"))
        val afterInsert = dao.observeAll().first()
        assertThat(afterInsert).hasSize(1)

        // Delete item and observe
        dao.deleteById("test-1")
        val afterDelete = dao.observeAll().first()
        assertThat(afterDelete).isEmpty()
    }

    @Test
    fun whenObservingCount_thenFlowEmitsUpdates() = runTest {
        // Act & Assert - Initially zero
        assertThat(dao.observeCount().first()).isEqualTo(0)

        // Add item
        dao.insert(createTestEntity(id = "test-1"))
        assertThat(dao.observeCount().first()).isEqualTo(1)

        // Add another
        dao.insert(createTestEntity(id = "test-2"))
        assertThat(dao.observeCount().first()).isEqualTo(2)
    }

    @Test
    fun whenGettingByCategory_thenOnlyMatchingItemsReturned() = runTest {
        // Arrange
        dao.insertAll(
            listOf(
                createTestEntity(id = "test-1", category = "FASHION"),
                createTestEntity(id = "test-2", category = "ELECTRONICS"),
                createTestEntity(id = "test-3", category = "FASHION")
            )
        )

        // Act
        val fashionItems = dao.getByCategory("FASHION")
        val electronicsItems = dao.getByCategory("ELECTRONICS")

        // Assert
        assertThat(fashionItems).hasSize(2)
        assertThat(fashionItems.map { it.id }).containsExactly("test-1", "test-3")
        assertThat(electronicsItems).hasSize(1)
        assertThat(electronicsItems[0].id).isEqualTo("test-2")
    }

    @Test
    fun whenGettingByMinConfidence_thenOnlyQualifyingItemsReturned() = runTest {
        // Arrange
        dao.insertAll(
            listOf(
                createTestEntity(id = "test-1", confidence = 0.9f),
                createTestEntity(id = "test-2", confidence = 0.5f),
                createTestEntity(id = "test-3", confidence = 0.3f)
            )
        )

        // Act
        val highConfidence = dao.getByMinConfidence(0.7f)
        val mediumConfidence = dao.getByMinConfidence(0.4f)

        // Assert
        assertThat(highConfidence).hasSize(1)
        assertThat(highConfidence[0].id).isEqualTo("test-1")
        assertThat(mediumConfidence).hasSize(2)
        assertThat(mediumConfidence.map { it.id }).containsExactly("test-1", "test-2")
    }

    @Test
    fun whenGettingAll_thenItemsOrderedByTimestampDesc() = runTest {
        // Arrange - Insert in random order with different timestamps
        dao.insertAll(
            listOf(
                createTestEntity(id = "test-2", timestamp = 2000L),
                createTestEntity(id = "test-1", timestamp = 1000L),
                createTestEntity(id = "test-3", timestamp = 3000L)
            )
        )

        // Act
        val items = dao.getAll()

        // Assert - Should be ordered newest first
        assertThat(items.map { it.id }).containsExactly("test-3", "test-2", "test-1").inOrder()
    }

    @Test
    fun whenGettingByCategory_thenItemsOrderedByTimestampDesc() = runTest {
        // Arrange
        dao.insertAll(
            listOf(
                createTestEntity(id = "test-1", category = "FASHION", timestamp = 1000L),
                createTestEntity(id = "test-2", category = "FASHION", timestamp = 3000L),
                createTestEntity(id = "test-3", category = "FASHION", timestamp = 2000L)
            )
        )

        // Act
        val items = dao.getByCategory("FASHION")

        // Assert - Newest first
        assertThat(items.map { it.id }).containsExactly("test-2", "test-3", "test-1").inOrder()
    }

    @Test
    fun whenGettingByMinConfidence_thenItemsOrderedByConfidenceDesc() = runTest {
        // Arrange
        dao.insertAll(
            listOf(
                createTestEntity(id = "test-1", confidence = 0.5f),
                createTestEntity(id = "test-2", confidence = 0.9f),
                createTestEntity(id = "test-3", confidence = 0.7f)
            )
        )

        // Act
        val items = dao.getByMinConfidence(0.4f)

        // Assert - Highest confidence first
        assertThat(items.map { it.id }).containsExactly("test-2", "test-3", "test-1").inOrder()
    }

    // Helper function to create test entities
    private fun createTestEntity(
        id: String = "test-id",
        thumbnailBytes: ByteArray? = null,
        category: String = "FASHION",
        priceLow: Double = 10.0,
        priceHigh: Double = 20.0,
        confidence: Float = 0.5f,
        timestamp: Long = System.currentTimeMillis(),
        recognizedText: String? = null,
        barcodeValue: String? = null,
        bboxLeft: Float? = null,
        bboxTop: Float? = null,
        bboxRight: Float? = null,
        bboxBottom: Float? = null,
        labelText: String? = null
    ): ScannedItemEntity {
        return ScannedItemEntity(
            id = id,
            thumbnailBytes = thumbnailBytes,
            category = category,
            priceLow = priceLow,
            priceHigh = priceHigh,
            confidence = confidence,
            timestamp = timestamp,
            recognizedText = recognizedText,
            barcodeValue = barcodeValue,
            bboxLeft = bboxLeft,
            bboxTop = bboxTop,
            bboxRight = bboxRight,
            bboxBottom = bboxBottom,
            labelText = labelText
        )
    }
}
