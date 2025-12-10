package com.scanium.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scanium.app.items.ScannedItem
import com.scanium.app.ml.ItemCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for ItemsRepositoryImpl.
 *
 * Tests verify:
 * - Repository wraps DAO operations correctly
 * - Domain models (ScannedItem) are converted to/from entities
 * - Result types wrap successes and failures appropriately
 * - Flow-based reactive queries work correctly
 * - All CRUD operations function as expected
 */
@RunWith(RobolectricTestRunner::class)
class ItemsRepositoryTest {

    private lateinit var database: ScaniumDatabase
    private lateinit var repository: ItemsRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ScaniumDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ItemsRepositoryImpl(database.itemsDao(), testDispatcher)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun whenAddingItem_thenSuccessResultReturned() = runTest(testDispatcher) {
        // Arrange
        val item = createTestItem(id = "test-1")

        // Act
        val result = repository.addItem(item)

        // Assert
        assertThat(result.isSuccess()).isTrue()
    }

    @Test
    fun whenAddingItem_thenItemCanBeRetrieved() = runTest(testDispatcher) {
        // Arrange
        val item = createTestItem(id = "test-1", category = ItemCategory.FASHION)

        // Act
        repository.addItem(item)
        val result = repository.getItemById("test-1")

        // Assert
        assertThat(result.isSuccess()).isTrue()
        val retrieved = result.getOrNull()
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.id).isEqualTo("test-1")
        assertThat(retrieved?.category).isEqualTo(ItemCategory.FASHION)
    }

    @Test
    fun whenAddingMultipleItems_thenAllItemsRetrievable() = runTest(testDispatcher) {
        // Arrange
        val items = listOf(
            createTestItem(id = "test-1", category = ItemCategory.FASHION),
            createTestItem(id = "test-2", category = ItemCategory.ELECTRONICS),
            createTestItem(id = "test-3", category = ItemCategory.HOME_GOOD)
        )

        // Act
        val result = repository.addItems(items)
        val getResult = repository.getItems()

        // Assert
        assertThat(result.isSuccess()).isTrue()
        assertThat(getResult.isSuccess()).isTrue()
        val allItems = getResult.getOrNull()
        assertThat(allItems).hasSize(3)
        assertThat(allItems?.map { it.id }).containsExactly("test-1", "test-2", "test-3")
    }

    @Test
    fun whenRemovingItem_thenItemNoLongerExists() = runTest(testDispatcher) {
        // Arrange
        val item = createTestItem(id = "test-1")
        repository.addItem(item)

        // Act
        val removeResult = repository.removeItem("test-1")
        val getResult = repository.getItemById("test-1")

        // Assert
        assertThat(removeResult.isSuccess()).isTrue()
        assertThat(getResult.getOrNull()).isNull()
    }

    @Test
    fun whenRemovingNonExistentItem_thenSuccessReturned() = runTest(testDispatcher) {
        // Act
        val result = repository.removeItem("non-existent")

        // Assert - Operation is idempotent
        assertThat(result.isSuccess()).isTrue()
    }

    @Test
    fun whenClearingAll_thenAllItemsRemoved() = runTest(testDispatcher) {
        // Arrange
        repository.addItems(
            listOf(
                createTestItem(id = "test-1"),
                createTestItem(id = "test-2"),
                createTestItem(id = "test-3")
            )
        )

        // Act
        val clearResult = repository.clearAll()
        val getResult = repository.getItems()

        // Assert
        assertThat(clearResult.isSuccess()).isTrue()
        assertThat(getResult.getOrNull()).isEmpty()
    }

    @Test
    fun whenCheckingItemExists_thenCorrectValueReturned() = runTest(testDispatcher) {
        // Arrange
        val item = createTestItem(id = "test-1")
        repository.addItem(item)

        // Act
        val existsResult = repository.itemExists("test-1")
        val notExistsResult = repository.itemExists("non-existent")

        // Assert
        assertThat(existsResult.getOrNull()).isTrue()
        assertThat(notExistsResult.getOrNull()).isFalse()
    }

    @Test
    fun whenGettingCount_thenCorrectNumberReturned() = runTest(testDispatcher) {
        // Act & Assert - Initially zero
        assertThat(repository.getItemCount().getOrNull()).isEqualTo(0)

        // Add items
        repository.addItem(createTestItem(id = "test-1"))
        assertThat(repository.getItemCount().getOrNull()).isEqualTo(1)

        repository.addItem(createTestItem(id = "test-2"))
        assertThat(repository.getItemCount().getOrNull()).isEqualTo(2)

        // Remove item
        repository.removeItem("test-1")
        assertThat(repository.getItemCount().getOrNull()).isEqualTo(1)
    }

    @Test
    fun whenObservingItems_thenFlowEmitsUpdates() = runTest(testDispatcher) {
        // Act & Assert - Initially empty
        val initialItems = repository.observeItems().first()
        assertThat(initialItems).isEmpty()

        // Add item
        repository.addItem(createTestItem(id = "test-1"))
        val afterInsert = repository.observeItems().first()
        assertThat(afterInsert).hasSize(1)

        // Add another
        repository.addItem(createTestItem(id = "test-2"))
        val afterSecondInsert = repository.observeItems().first()
        assertThat(afterSecondInsert).hasSize(2)

        // Remove item
        repository.removeItem("test-1")
        val afterDelete = repository.observeItems().first()
        assertThat(afterDelete).hasSize(1)
        assertThat(afterDelete[0].id).isEqualTo("test-2")
    }

    @Test
    fun whenGettingByCategory_thenOnlyMatchingItemsReturned() = runTest(testDispatcher) {
        // Arrange
        repository.addItems(
            listOf(
                createTestItem(id = "test-1", category = ItemCategory.FASHION),
                createTestItem(id = "test-2", category = ItemCategory.ELECTRONICS),
                createTestItem(id = "test-3", category = ItemCategory.FASHION)
            )
        )

        // Act
        val fashionResult = repository.getItemsByCategory("FASHION")
        val electronicsResult = repository.getItemsByCategory("ELECTRONICS")

        // Assert
        assertThat(fashionResult.isSuccess()).isTrue()
        val fashionItems = fashionResult.getOrNull()
        assertThat(fashionItems).hasSize(2)
        assertThat(fashionItems?.map { it.id }).containsExactly("test-1", "test-3")

        val electronicsItems = electronicsResult.getOrNull()
        assertThat(electronicsItems).hasSize(1)
        assertThat(electronicsItems?.get(0)?.id).isEqualTo("test-2")
    }

    @Test
    fun whenGettingByMinConfidence_thenOnlyQualifyingItemsReturned() = runTest(testDispatcher) {
        // Arrange
        repository.addItems(
            listOf(
                createTestItem(id = "test-1", confidence = 0.9f),
                createTestItem(id = "test-2", confidence = 0.5f),
                createTestItem(id = "test-3", confidence = 0.3f)
            )
        )

        // Act
        val highConfidenceResult = repository.getItemsByMinConfidence(0.7f)
        val mediumConfidenceResult = repository.getItemsByMinConfidence(0.4f)

        // Assert
        val highConfidence = highConfidenceResult.getOrNull()
        assertThat(highConfidence).hasSize(1)
        assertThat(highConfidence?.get(0)?.id).isEqualTo("test-1")

        val mediumConfidence = mediumConfidenceResult.getOrNull()
        assertThat(mediumConfidence).hasSize(2)
        assertThat(mediumConfidence?.map { it.id }).containsExactly("test-1", "test-2")
    }

    @Test
    fun whenAddingItemWithThumbnail_thenThumbnailPreserved() = runTest(testDispatcher) {
        // Arrange
        val bitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
        val item = createTestItem(id = "test-1", thumbnail = bitmap)

        // Act
        repository.addItem(item)
        val result = repository.getItemById("test-1")

        // Assert
        val retrieved = result.getOrNull()
        assertThat(retrieved?.thumbnail).isNotNull()
        assertThat(retrieved?.thumbnail?.width).isEqualTo(100)
        assertThat(retrieved?.thumbnail?.height).isEqualTo(100)
    }

    @Test
    fun whenAddingItemWithAllFields_thenAllFieldsPreserved() = runTest(testDispatcher) {
        // Arrange
        val item = ScannedItem(
            id = "test-1",
            thumbnail = null,
            category = ItemCategory.DOCUMENT,
            priceRange = 5.0 to 15.0,
            confidence = 0.85f,
            timestamp = 1234567890L,
            recognizedText = null,
            barcodeValue = "1234567890",
            boundingBox = android.graphics.RectF(0.1f, 0.2f, 0.3f, 0.4f),
            labelText = "QR Code"
        )

        // Act
        repository.addItem(item)
        val result = repository.getItemById("test-1")

        // Assert
        val retrieved = result.getOrNull()
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.id).isEqualTo("test-1")
        assertThat(retrieved?.category).isEqualTo(ItemCategory.DOCUMENT)
        assertThat(retrieved?.priceRange).isEqualTo(5.0 to 15.0)
        assertThat(retrieved?.confidence).isWithin(0.001f).of(0.85f)
        assertThat(retrieved?.timestamp).isEqualTo(1234567890L)
        assertThat(retrieved?.barcodeValue).isEqualTo("1234567890")
        assertThat(retrieved?.labelText).isEqualTo("QR Code")
        assertThat(retrieved?.boundingBox).isNotNull()
    }

    @Test
    fun whenReplacingItem_thenNewVersionRetrieved() = runTest(testDispatcher) {
        // Arrange
        val original = createTestItem(id = "test-1", category = ItemCategory.FASHION, confidence = 0.5f)
        val replacement = createTestItem(id = "test-1", category = ItemCategory.ELECTRONICS, confidence = 0.9f)

        // Act
        repository.addItem(original)
        repository.addItem(replacement) // Should replace

        val result = repository.getItemById("test-1")
        val count = repository.getItemCount()

        // Assert
        assertThat(count.getOrNull()).isEqualTo(1) // Still only one item
        val retrieved = result.getOrNull()
        assertThat(retrieved?.category).isEqualTo(ItemCategory.ELECTRONICS) // New version
        assertThat(retrieved?.confidence).isWithin(0.001f).of(0.9f)
    }

    @Test
    fun whenOperationSucceeds_thenResultIsSuccess() = runTest(testDispatcher) {
        // Act
        val addResult = repository.addItem(createTestItem(id = "test-1"))
        val getResult = repository.getItems()
        val removeResult = repository.removeItem("test-1")
        val clearResult = repository.clearAll()

        // Assert
        assertThat(addResult).isInstanceOf(Result.Success::class.java)
        assertThat(getResult).isInstanceOf(Result.Success::class.java)
        assertThat(removeResult).isInstanceOf(Result.Success::class.java)
        assertThat(clearResult).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun whenGettingNonExistentItem_thenSuccessWithNull() = runTest(testDispatcher) {
        // Act
        val result = repository.getItemById("non-existent")

        // Assert
        assertThat(result.isSuccess()).isTrue()
        assertThat(result.getOrNull()).isNull()
    }

    @Test
    fun whenObservingEmptyRepository_thenEmptyListEmitted() = runTest(testDispatcher) {
        // Act
        val items = repository.observeItems().first()

        // Assert
        assertThat(items).isEmpty()
    }

    @Test
    fun whenAddingAndObserving_thenLatestStateReflected() = runTest(testDispatcher) {
        // Arrange & Act - Add items incrementally and observe
        repository.addItem(createTestItem(id = "test-1"))
        val afterFirst = repository.observeItems().first()

        repository.addItem(createTestItem(id = "test-2"))
        val afterSecond = repository.observeItems().first()

        repository.removeItem("test-1")
        val afterRemove = repository.observeItems().first()

        // Assert - Each observation reflects the current state
        assertThat(afterFirst).hasSize(1)
        assertThat(afterSecond).hasSize(2)
        assertThat(afterRemove).hasSize(1)
        assertThat(afterRemove[0].id).isEqualTo("test-2")
    }

    // Helper function to create test items
    private fun createTestItem(
        id: String = "test-id",
        thumbnail: android.graphics.Bitmap? = null,
        category: ItemCategory = ItemCategory.FASHION,
        priceRange: Pair<Double, Double> = 10.0 to 20.0,
        confidence: Float = 0.5f,
        timestamp: Long = System.currentTimeMillis()
    ): ScannedItem {
        return ScannedItem(
            id = id,
            thumbnail = thumbnail,
            category = category,
            priceRange = priceRange,
            confidence = confidence,
            timestamp = timestamp
        )
    }
}
