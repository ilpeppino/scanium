package com.scanium.app.items

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.scanium.core.models.geometry.NormalizedRect
import com.scanium.core.models.image.ImageRef
import com.scanium.core.models.ml.ItemCategory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for ItemsViewModel state management and detection handling.
 *
 * Tests verify:
 * - Adding single and multiple items through repository
 * - De-duplication based on item ID (and now similarity-based too)
 * - Removing items from repository
 * - Clearing all items from repository
 * - StateFlow emissions from repository
 * - Item count tracking
 * - Integration with repository layer
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ItemsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: ItemsViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ItemsViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun whenViewModelCreated_thenItemsListIsEmpty() = runTest {
        // Act
        val items = viewModel.items.first()

        // Assert
        assertThat(items).isEmpty()
        assertThat(viewModel.getItemCount()).isEqualTo(0)
    }

    @Test
    fun whenAddingSingleItem_thenItemAppearsInList() = runTest {
        // Arrange
        val item = createTestItem(id = "item-1", category = ItemCategory.FASHION)

        // Act
        viewModel.addItem(item)
        testDispatcher.scheduler.advanceUntilIdle() // Wait for coroutine to complete

        // Assert
        val items = viewModel.items.first()
        assertThat(items).hasSize(1)
        assertThat(items[0].category).isEqualTo(ItemCategory.FASHION)
        assertThat(viewModel.getItemCount()).isEqualTo(1)
    }

    @Test
    fun whenAddingMultipleItems_thenAllItemsAppear() = runTest {
        // Arrange
        val items = listOf(
            createTestItem(id = "item-1", category = ItemCategory.FASHION),
            createTestItem(id = "item-2", category = ItemCategory.ELECTRONICS),
            createTestItem(id = "item-3", category = ItemCategory.HOME_GOOD)
        )

        // Act
        viewModel.addItems(items)

        // Assert
        val resultItems = viewModel.items.first()
        assertThat(resultItems).hasSize(3)
        assertThat(resultItems.map { it.category }).containsExactly(
            ItemCategory.FASHION,
            ItemCategory.ELECTRONICS,
            ItemCategory.HOME_GOOD
        )
        assertThat(viewModel.getItemCount()).isEqualTo(3)
    }

    @Test
    fun whenAddingDuplicateItem_thenOnlyOneInstanceKept() = runTest {
        // Arrange
        val item1 = createTestItem(id = "item-1", category = ItemCategory.FASHION)
        val item2 = createTestItem(id = "item-1", category = ItemCategory.FASHION) // Same ID + category

        // Act
        viewModel.addItem(item1)
        viewModel.addItem(item2) // Should be ignored

        // Assert
        val items = viewModel.items.first()
        assertThat(items).hasSize(1)
        assertThat(items[0].category).isEqualTo(ItemCategory.FASHION) // Merged into one aggregate
    }

    @Test
    fun whenAddingItemsWithDuplicateIds_thenDuplicatesFiltered() = runTest {
        // Arrange
        val items = listOf(
            createTestItem(id = "item-1", category = ItemCategory.FASHION),
            createTestItem(id = "item-2", category = ItemCategory.ELECTRONICS),
            createTestItem(id = "item-1", category = ItemCategory.FASHION) // Duplicate (same category for merge)
        )

        // Act
        viewModel.addItems(items)

        // Assert
        val resultItems = viewModel.items.first()
        assertThat(resultItems).hasSize(2) // Only unique items
        assertThat(resultItems.map { it.category }).containsExactly(
            ItemCategory.FASHION,
            ItemCategory.ELECTRONICS
        )
    }

    @Test
    fun whenRemovingItem_thenItemDisappearsFromList() = runTest {
        // Arrange
        viewModel.addItem(createTestItem(id = "item-1", category = ItemCategory.FASHION))
        viewModel.addItem(createTestItem(id = "item-2", category = ItemCategory.ELECTRONICS))

        // Act
        val currentItems = viewModel.items.first()
        viewModel.removeItem(currentItems.first().id)

        // Assert
        val items = viewModel.items.first()
        assertThat(items).hasSize(1)
        assertThat(viewModel.getItemCount()).isEqualTo(1)
    }

    @Test
    fun whenRemovingNonExistentItem_thenListUnchanged() = runTest {
        // Arrange
        viewModel.addItem(createTestItem(id = "item-1", category = ItemCategory.FASHION))

        // Act
        viewModel.removeItem("non-existent-id")

        // Assert
        val items = viewModel.items.first()
        assertThat(items).hasSize(1)
    }

    @Test
    fun whenClearingAllItems_thenListBecomesEmpty() = runTest {
        // Arrange
        viewModel.addItem(createTestItem(id = "item-1", category = ItemCategory.FASHION))
        viewModel.addItem(createTestItem(id = "item-2", category = ItemCategory.ELECTRONICS))

        // Act
        viewModel.clearAllItems()

        // Assert
        val items = viewModel.items.first()
        assertThat(items).isEmpty()
        assertThat(viewModel.getItemCount()).isEqualTo(0)
    }

    @Test
    fun whenClearingEmptyList_thenNoError() = runTest {
        // Act
        viewModel.clearAllItems()

        // Assert
        val items = viewModel.items.first()
        assertThat(items).isEmpty()
    }

    @Test
    fun whenAddingAfterClearing_thenNewItemsAppear() = runTest {
        // Arrange
        viewModel.addItem(createTestItem(id = "item-1", category = ItemCategory.FASHION))
        viewModel.clearAllItems()

        // Act
        viewModel.addItem(createTestItem(id = "item-2", category = ItemCategory.ELECTRONICS))

        // Assert
        val items = viewModel.items.first()
        assertThat(items).hasSize(1)
    }

    @Test
    fun whenAddingSameItemAfterRemoval_thenItemCanBeAddedAgain() = runTest {
        // Arrange
        val item = createTestItem(id = "item-1", category = ItemCategory.FASHION)
        viewModel.addItem(item)
        val aggregatedId = viewModel.items.first().first().id
        viewModel.removeItem(aggregatedId)

        // Act - Add same ID again
        viewModel.addItem(item)

        // Assert
        val items = viewModel.items.first()
        assertThat(items).hasSize(1)
    }

    @Test
    fun whenAddingItemsSequentially_thenMaintainsOrder() = runTest {
        // Act
        viewModel.addItem(createTestItem(id = "item-1", category = ItemCategory.FASHION))
        viewModel.addItem(createTestItem(id = "item-2", category = ItemCategory.ELECTRONICS))
        viewModel.addItem(createTestItem(id = "item-3", category = ItemCategory.HOME_GOOD))

        // Assert
        val items = viewModel.items.first()
        assertThat(items).hasSize(3)
        assertThat(items.map { it.category }).containsExactly(
            ItemCategory.FASHION,
            ItemCategory.ELECTRONICS,
            ItemCategory.HOME_GOOD
        )
    }

    @Test
    fun whenAddingEmptyList_thenNoChange() = runTest {
        // Arrange
        viewModel.addItem(createTestItem(id = "item-1", category = ItemCategory.FASHION))

        // Act
        viewModel.addItems(emptyList())

        // Assert
        val items = viewModel.items.first()
        assertThat(items).hasSize(1)
    }

    @Test
    fun whenAddingMixOfNewAndDuplicateItems_thenOnlyNewOnesAdded() = runTest {
        // Arrange - Add initial item
        viewModel.addItem(createTestItem(id = "item-1", category = ItemCategory.FASHION))

        // Act - Add mix of new and duplicate
        viewModel.addItems(
            listOf(
                createTestItem(id = "item-1", category = ItemCategory.FASHION), // Duplicate (merge)
                createTestItem(id = "item-2", category = ItemCategory.HOME_GOOD), // New
                createTestItem(id = "item-3", category = ItemCategory.PLANT) // New
            )
        )

        // Assert
        val items = viewModel.items.first()
        assertThat(items).hasSize(3)
        assertThat(items.map { it.category }).containsAtLeastElementsIn(
            listOf(ItemCategory.FASHION, ItemCategory.HOME_GOOD, ItemCategory.PLANT)
        )
    }

    @Test
    fun whenAddingItemsWithDifferentConfidenceLevels_thenAllStored() = runTest {
        // Arrange
        val items = listOf(
            createTestItem(id = "item-1", category = ItemCategory.FASHION, confidence = 0.9f),
            createTestItem(id = "item-2", category = ItemCategory.ELECTRONICS, confidence = 0.5f),
            createTestItem(id = "item-3", category = ItemCategory.HOME_GOOD, confidence = 0.3f)
        )

        // Act
        viewModel.addItems(items)

        // Assert
        val resultItems = viewModel.items.first()
        assertThat(resultItems).hasSize(3)
        assertThat(resultItems[0].confidence).isWithin(0.01f).of(0.9f)
        assertThat(resultItems[1].confidence).isWithin(0.01f).of(0.5f)
        assertThat(resultItems[2].confidence).isWithin(0.01f).of(0.3f)
    }

    @Test
    fun whenItemCountQueried_thenMatchesActualListSize() = runTest {
        // Act & Assert - Empty
        assertThat(viewModel.getItemCount()).isEqualTo(0)

        // Add items
        viewModel.addItem(createTestItem(id = "item-1", boundingBox = defaultBoundingBox(offsetX = 0.0f)))
        assertThat(viewModel.getItemCount()).isEqualTo(1)

        viewModel.addItem(createTestItem(id = "item-2", boundingBox = defaultBoundingBox(offsetX = 0.5f)))
        assertThat(viewModel.getItemCount()).isEqualTo(2)

        // Remove item
        val idToRemove = viewModel.items.first().first().id
        viewModel.removeItem(idToRemove)
        assertThat(viewModel.getItemCount()).isEqualTo(1)

        // Clear
        viewModel.clearAllItems()
        assertThat(viewModel.getItemCount()).isEqualTo(0)
    }

    // ==================== Session-Level De-Duplication Tests ====================

    @Test
    fun whenAddingSimilarItemWithDifferentId_thenRejectedBySimilarityCheck() = runTest {
        // This tests the session-level de-duplication layer

        // Arrange - Add first item with thumbnail
        val item1 = createItemWithThumbnail(
            id = "track-1",
            category = ItemCategory.FASHION,
            thumbnailWidth = 200,
            thumbnailHeight = 200
        )
        viewModel.addItem(item1)

        // Act - Add similar item with different ID
        val item2 = createItemWithThumbnail(
            id = "track-2", // Different ID
            category = ItemCategory.FASHION,
            thumbnailWidth = 205, // Similar size
            thumbnailHeight = 205
        )
        viewModel.addItem(item2)

        // Assert - Should only have one item (similar item rejected)
        val items = viewModel.items.first()
        assertThat(items).hasSize(1)
    }

    @Test
    fun whenAddingDissimilarItems_thenBothAdded() = runTest {
        // Arrange - Add first item
        val item1 = createItemWithThumbnail(
            id = "item-1",
            category = ItemCategory.FASHION,
            thumbnailWidth = 200,
            thumbnailHeight = 200
        )
        viewModel.addItem(item1)

        // Act - Add different item (different category)
        val item2 = createItemWithThumbnail(
            id = "item-2",
            category = ItemCategory.ELECTRONICS, // Different category
            thumbnailWidth = 200,
            thumbnailHeight = 200
        )
        viewModel.addItem(item2)

        // Assert - Should have both items
        val items = viewModel.items.first()
        assertThat(items).hasSize(2)
    }

    @Test
    fun whenAddingItemsWithoutDistinguishingFeatures_thenBothAdded() = runTest {
        // Safety check: items without thumbnails should not be considered similar

        // Arrange - Add first item without thumbnail
        val item1 = createTestItem(
            id = "item-1",
            category = ItemCategory.FASHION
        )
        viewModel.addItem(item1)

        // Act - Add another item without thumbnail (same category)
        val item2 = createTestItem(
            id = "item-2",
            category = ItemCategory.FASHION, // Same category but no thumbnail
            boundingBox = defaultBoundingBox(offsetX = 0.5f) // Spatially separated
        )
        viewModel.addItem(item2)

        // Assert - Should have both items (safety check prevents false positive)
        val items = viewModel.items.first()
        assertThat(items).hasSize(2)
    }

    @Test
    fun whenAddingBatchWithSimilarItems_thenOnlyUniqueOnesAdded() = runTest {
        // Arrange - Add initial item
        val existingItem = createItemWithThumbnail(
            id = "item-1",
            category = ItemCategory.ELECTRONICS,
            thumbnailWidth = 200,
            thumbnailHeight = 200
        )
        viewModel.addItem(existingItem)

        // Act - Add batch with similar and dissimilar items
        val batch = listOf(
            createItemWithThumbnail(
                id = "item-2",
                category = ItemCategory.ELECTRONICS,
                thumbnailWidth = 205, // Similar to item-1
                thumbnailHeight = 205
            ),
            createItemWithThumbnail(
                id = "item-3",
                category = ItemCategory.FASHION, // Different category
                thumbnailWidth = 200,
                thumbnailHeight = 200
            ),
            createItemWithThumbnail(
                id = "item-4",
                category = ItemCategory.HOME_GOOD, // Different category
                thumbnailWidth = 200,
                thumbnailHeight = 200
            )
        )
        viewModel.addItems(batch)

        // Assert - Should have 3 items (item-2 rejected as similar to item-1)
        val items = viewModel.items.first()
        assertThat(items).hasSize(3)
        assertThat(items.map { it.category }).containsExactly(
            ItemCategory.ELECTRONICS,
            ItemCategory.FASHION,
            ItemCategory.HOME_GOOD
        )
    }

    @Test
    fun whenAddingBatchWithInternalSimilarItems_thenFirstOccurrenceKept() = runTest {
        // Act - Add batch with internally similar items
        val batch = listOf(
            createItemWithThumbnail(
                id = "item-1",
                category = ItemCategory.FASHION,
                thumbnailWidth = 200,
                thumbnailHeight = 200
            ),
            createItemWithThumbnail(
                id = "item-2",
                category = ItemCategory.FASHION,
                thumbnailWidth = 205, // Similar to item-1
                thumbnailHeight = 205
            ),
            createItemWithThumbnail(
                id = "item-3",
                category = ItemCategory.FASHION,
                thumbnailWidth = 210, // Similar to item-1 and item-2
                thumbnailHeight = 210
            )
        )
        viewModel.addItems(batch)

        // Assert - Should only have first item (others rejected as similar)
        val items = viewModel.items.first()
        assertThat(items).hasSize(1)
    }

    @Test
    fun whenClearingItems_thenSessionDeduplicatorReset() = runTest {
        // Arrange - Add item
        val item1 = createItemWithThumbnail(
            id = "item-1",
            category = ItemCategory.FASHION,
            thumbnailWidth = 200,
            thumbnailHeight = 200
        )
        viewModel.addItem(item1)

        // Act - Clear all items
        viewModel.clearAllItems()

        // Add same item again (should be allowed after clear)
        val item2 = createItemWithThumbnail(
            id = "item-2",
            category = ItemCategory.FASHION,
            thumbnailWidth = 205, // Similar to item-1
            thumbnailHeight = 205
        )
        viewModel.addItem(item2)

        // Assert - Should have the new item (deduplicator was reset)
        val items = viewModel.items.first()
        assertThat(items).hasSize(1)
    }

    @Test
    fun whenRemovingItem_thenCanAddSimilarItemAgain() = runTest {
        // Arrange - Add and then remove item
        val item1 = createItemWithThumbnail(
            id = "item-1",
            category = ItemCategory.ELECTRONICS,
            thumbnailWidth = 200,
            thumbnailHeight = 200
        )
        viewModel.addItem(item1)
        val aggregatedId = viewModel.items.first().first().id
        viewModel.removeItem(aggregatedId)

        // Act - Add similar item
        val item2 = createItemWithThumbnail(
            id = "item-2",
            category = ItemCategory.ELECTRONICS,
            thumbnailWidth = 205, // Similar to removed item-1
            thumbnailHeight = 205
        )
        viewModel.addItem(item2)

        // Assert - Should be added (item-1 was removed)
        val items = viewModel.items.first()
        assertThat(items).hasSize(1)
    }

    @Test
    fun whenSamePhysicalObjectDetectedWithDifferentTrackingIds_thenOnlyOneKept() = runTest {
        // Simulates the real-world scenario where ML Kit changes tracking IDs

        // Arrange - Simulate first detection
        val detection1 = createItemWithThumbnail(
            id = "mlkit-track-123",
            category = ItemCategory.FASHION,
            thumbnailWidth = 200,
            thumbnailHeight = 200
        )
        viewModel.addItem(detection1)

        // Act - Simulate ML Kit changing tracking ID for same physical object
        val detection2 = createItemWithThumbnail(
            id = "mlkit-track-456", // Different tracking ID
            category = ItemCategory.FASHION,
            thumbnailWidth = 205, // Slightly different (camera moved)
            thumbnailHeight = 205
        )
        viewModel.addItem(detection2)

        // Assert - Should only have one item (duplicate prevented)
        val items = viewModel.items.first()
        assertThat(items).hasSize(1)
    }

    @Test
    fun whenMultipleDifferentObjectsScanned_thenAllAdded() = runTest {
        // Test the "no zero items" scenario - system should add items

        // Act - Add multiple different items
        val items = listOf(
            createItemWithThumbnail("item-1", ItemCategory.FASHION, 100, 100),
            createItemWithThumbnail("item-2", ItemCategory.ELECTRONICS, 200, 200),
            createItemWithThumbnail("item-3", ItemCategory.FOOD, 300, 300),
            createItemWithThumbnail("item-4", ItemCategory.HOME_GOOD, 400, 400)
        )
        viewModel.addItems(items)

        // Assert - All items should be added (no false positives)
        val resultItems = viewModel.items.first()
        assertThat(resultItems).hasSize(4)
    }

    @Test
    fun whenAddingSimilarItemsWithDifferentSizes_thenOnlyDissimilarOnesAdded() = runTest {
        // Arrange - Add base item
        val baseItem = createItemWithThumbnail(
            id = "item-1",
            category = ItemCategory.PLANT,
            thumbnailWidth = 200,
            thumbnailHeight = 200
        )
        viewModel.addItem(baseItem)

        // Act - Add items with varying sizes
        viewModel.addItem(
            createItemWithThumbnail(
                id = "item-2",
                category = ItemCategory.PLANT,
                thumbnailWidth = 210, // Within 40% tolerance - similar
                thumbnailHeight = 210
            )
        )
        viewModel.addItem(
            createItemWithThumbnail(
                id = "item-3",
                category = ItemCategory.PLANT,
                thumbnailWidth = 400, // Size difference > 40% - dissimilar
                thumbnailHeight = 400
            )
        )

        // Assert
        val items = viewModel.items.first()
        assertThat(items).hasSize(2)
    }

    // Helper function to create test items
    private fun createTestItem(
        id: String = "test-id",
        category: ItemCategory = ItemCategory.UNKNOWN,
        confidence: Float = 0.5f,
        boundingBox: NormalizedRect = defaultBoundingBox(),
        labelText: String = category.name
    ): ScannedItem {
        return ScannedItem(
            id = id,
            thumbnail = null,
            category = category,
            priceRange = 10.0 to 20.0,
            confidence = confidence,
            timestamp = System.currentTimeMillis(),
            boundingBox = boundingBox,
            labelText = labelText
        )
    }

    private fun createItemWithThumbnail(
        id: String,
        category: ItemCategory,
        thumbnailWidth: Int,
        thumbnailHeight: Int,
        confidence: Float = 0.5f,
        boundingBox: NormalizedRect = defaultBoundingBox(
            size = (minOf(thumbnailWidth, thumbnailHeight) / 1000f).coerceAtMost(0.9f)
        ),
        labelText: String = category.name
    ): ScannedItem {
        return ScannedItem(
            id = id,
            thumbnail = testImageRef(thumbnailWidth, thumbnailHeight),
            category = category,
            priceRange = 10.0 to 20.0,
            confidence = confidence,
            timestamp = System.currentTimeMillis(),
            boundingBox = boundingBox,
            labelText = labelText
        )
    }

    private fun defaultBoundingBox(size: Float = 0.2f, offsetX: Float = 0.0f, offsetY: Float = 0.0f): NormalizedRect {
        val clampedSize = size.coerceIn(0.05f, 0.9f)
        val left = offsetX.coerceIn(0f, 1f - clampedSize)
        val top = offsetY.coerceIn(0f, 1f - clampedSize)
        return NormalizedRect(left, top, left + clampedSize, top + clampedSize)
    }

    private fun testImageRef(width: Int, height: Int): ImageRef.Bytes {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val bytes = ByteArray((safeWidth * safeHeight).coerceAtLeast(1)) { 1 }
        return ImageRef.Bytes(
            bytes = bytes,
            mimeType = "image/jpeg",
            width = safeWidth,
            height = safeHeight
        )
    }

    // ==================== Threshold Control Tests ====================

    @Test
    fun whenViewModelCreated_thenThresholdIsDefault() = runTest {
        // Assert - Default threshold should be REALTIME preset value (0.55)
        val threshold = viewModel.similarityThreshold.first()
        assertThat(threshold).isWithin(0.001f).of(0.55f)
    }

    @Test
    fun whenUpdatingThreshold_thenStateFlowEmitsNewValue() = runTest {
        // Act - Update threshold
        viewModel.updateSimilarityThreshold(0.75f)

        // Assert - StateFlow should emit new value
        val threshold = viewModel.similarityThreshold.first()
        assertThat(threshold).isWithin(0.001f).of(0.75f)
    }

    @Test
    fun whenUpdatingThresholdToZero_thenAcceptsValue() = runTest {
        // Act - Set to minimum
        viewModel.updateSimilarityThreshold(0.0f)

        // Assert
        val threshold = viewModel.similarityThreshold.first()
        assertThat(threshold).isWithin(0.001f).of(0.0f)
    }

    @Test
    fun whenUpdatingThresholdToOne_thenAcceptsValue() = runTest {
        // Act - Set to maximum
        viewModel.updateSimilarityThreshold(1.0f)

        // Assert
        val threshold = viewModel.similarityThreshold.first()
        assertThat(threshold).isWithin(0.001f).of(1.0f)
    }

    @Test
    fun whenUpdatingThresholdAboveOne_thenClampsToOne() = runTest {
        // Act - Try to set above maximum
        viewModel.updateSimilarityThreshold(1.5f)

        // Assert - Should be clamped to 1.0
        val threshold = viewModel.similarityThreshold.first()
        assertThat(threshold).isWithin(0.001f).of(1.0f)
    }

    @Test
    fun whenUpdatingThresholdBelowZero_thenClampsToZero() = runTest {
        // Act - Try to set below minimum
        viewModel.updateSimilarityThreshold(-0.5f)

        // Assert - Should be clamped to 0.0
        val threshold = viewModel.similarityThreshold.first()
        assertThat(threshold).isWithin(0.001f).of(0.0f)
    }

    @Test
    fun whenUpdatingThresholdMultipleTimes_thenAlwaysReflectsLatest() = runTest {
        // Act - Update multiple times
        viewModel.updateSimilarityThreshold(0.3f)
        viewModel.updateSimilarityThreshold(0.7f)
        viewModel.updateSimilarityThreshold(0.5f)

        // Assert - Should have latest value
        val threshold = viewModel.similarityThreshold.first()
        assertThat(threshold).isWithin(0.001f).of(0.5f)
    }

    @Test
    fun whenGettingCurrentThreshold_thenMatchesStateFlow() = runTest {
        // Arrange - Set specific threshold
        viewModel.updateSimilarityThreshold(0.65f)

        // Act
        val directValue = viewModel.getCurrentSimilarityThreshold()
        val flowValue = viewModel.similarityThreshold.first()

        // Assert - Both should match
        assertThat(directValue).isWithin(0.001f).of(0.65f)
        assertThat(flowValue).isWithin(0.001f).of(0.65f)
        assertThat(directValue).isWithin(0.001f).of(flowValue)
    }

    @Test
    fun whenThresholdUpdated_thenAggregatorUsesNewValue() = runTest {
        // This test verifies the threshold propagates to the aggregator
        // by checking the aggregator's current threshold

        // Arrange - Set custom threshold
        val customThreshold = 0.42f
        viewModel.updateSimilarityThreshold(customThreshold)

        // Act
        val aggregatorThreshold = viewModel.getCurrentSimilarityThreshold()

        // Assert - Aggregator should use the updated threshold
        assertThat(aggregatorThreshold).isWithin(0.001f).of(customThreshold)
    }

    @Test
    fun whenLowerThreshold_thenMoreItemsMerged() = runTest {
        // Test that lowering threshold results in more aggressive merging

        // Arrange - Set high threshold (strict)
        viewModel.updateSimilarityThreshold(0.9f)
        val baseBox = defaultBoundingBox(size = 0.2f, offsetX = 0.0f)
        val shiftedBox = defaultBoundingBox(size = 0.2f, offsetX = 0.25f) // Similar but offset to reduce score
        viewModel.addItem(createItemWithThumbnail("item-1", ItemCategory.FASHION, 200, 200, boundingBox = baseBox))
        viewModel.addItem(createItemWithThumbnail("item-2", ItemCategory.FASHION, 210, 210, boundingBox = shiftedBox))

        val strictCount = viewModel.items.first().size

        // Clear and test with low threshold
        viewModel.clearAllItems()
        viewModel.updateSimilarityThreshold(0.3f)
        viewModel.addItem(createItemWithThumbnail("item-3", ItemCategory.FASHION, 200, 200, boundingBox = baseBox))
        viewModel.addItem(createItemWithThumbnail("item-4", ItemCategory.FASHION, 210, 210, boundingBox = shiftedBox))

        val looseCount = viewModel.items.first().size

        // Assert - Lower threshold should result in fewer distinct items (more merging)
        assertThat(looseCount).isLessThan(strictCount)
    }

    @Test
    fun whenHigherThreshold_thenFewerItemsMerged() = runTest {
        // Test that raising threshold results in stricter matching

        // Arrange - Set low threshold (loose)
        viewModel.updateSimilarityThreshold(0.3f)
        val baseBox = defaultBoundingBox(size = 0.2f, offsetX = 0.0f)
        val shiftedBox = defaultBoundingBox(size = 0.2f, offsetX = 0.25f)
        viewModel.addItem(createItemWithThumbnail("item-1", ItemCategory.FASHION, 200, 200, boundingBox = baseBox))
        viewModel.addItem(createItemWithThumbnail("item-2", ItemCategory.FASHION, 220, 220, boundingBox = shiftedBox))

        val looseCount = viewModel.items.first().size

        // Clear and test with high threshold
        viewModel.clearAllItems()
        viewModel.updateSimilarityThreshold(0.9f)
        viewModel.addItem(createItemWithThumbnail("item-3", ItemCategory.FASHION, 200, 200, boundingBox = baseBox))
        viewModel.addItem(createItemWithThumbnail("item-4", ItemCategory.FASHION, 220, 220, boundingBox = shiftedBox))

        val strictCount = viewModel.items.first().size

        // Assert - Higher threshold should result in more distinct items (less merging)
        assertThat(strictCount).isGreaterThan(looseCount)
    }

    @Test
    fun whenThresholdChangedDuringScanning_thenImmediatelyAffectsNewDetections() = runTest {
        // Simulate scanning with threshold changes mid-session

        // Arrange - Start with default threshold, add item
        viewModel.addItem(createItemWithThumbnail("item-1", ItemCategory.ELECTRONICS, 200, 200))

        // Act - Change threshold mid-session
        viewModel.updateSimilarityThreshold(0.9f) // Very strict

        // Add similar item that would normally merge
        viewModel.addItem(
            createItemWithThumbnail(
                "item-2",
                ItemCategory.ELECTRONICS,
                205,
                205,
                boundingBox = defaultBoundingBox(size = 0.2f, offsetX = 0.25f)
            )
        )

        // Assert - With strict threshold, should have 2 items (not merged)
        val items = viewModel.items.first()
        assertThat(items).hasSize(2)
    }
}
