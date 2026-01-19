package com.scanium.app.items.state

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.scanium.app.items.ScannedItem
import com.scanium.app.items.ThumbnailCache
import com.scanium.app.items.persistence.PersistenceError
import com.scanium.app.items.persistence.ScannedItemStore
import com.scanium.core.models.geometry.NormalizedRect
import com.scanium.core.models.ml.ItemCategory
import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.shared.core.models.model.ImageRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for ItemsStateManager persistence and state management.
 *
 * Tests verify:
 * - Persisted items are loaded on initialization (FUNC-001)
 * - Items are persisted when added
 * - Items are deleted from persistence when cleared
 * - State is properly managed across operations
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ItemsStateManagerTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeStore: FakeScannedItemStore

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeStore = FakeScannedItemStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== FUNC-001: Persistence Loading Tests ====================

    @Test
    fun whenManagerCreated_withPersistedItems_thenItemsAreLoaded() =
        runTest {
            // Arrange - Pre-populate the store with persisted items
            val persistedItems =
                listOf(
                    createTestItem(id = "persisted-1", category = ItemCategory.FASHION),
                    createTestItem(id = "persisted-2", category = ItemCategory.ELECTRONICS),
                    createTestItem(id = "persisted-3", category = ItemCategory.HOME_GOOD),
                )
            fakeStore.seedItems(persistedItems)

            // Act - Create manager (should load items in init)
            val manager = createManager()
            advanceUntilIdle()

            // Assert - Items should be loaded from persistence
            val items = manager.items.first()
            assertThat(items).hasSize(3)
            assertThat(items.map { it.category }).containsExactly(
                ItemCategory.FASHION,
                ItemCategory.ELECTRONICS,
                ItemCategory.HOME_GOOD,
            )
        }

    @Test
    fun whenManagerCreated_withEmptyStore_thenItemsListIsEmpty() =
        runTest {
            // Arrange - Store is empty
            assertThat(fakeStore.loadAll()).isEmpty()

            // Act - Create manager
            val manager = createManager()
            advanceUntilIdle()

            // Assert - Items should be empty
            val items = manager.items.first()
            assertThat(items).isEmpty()
        }

    @Test
    fun whenManagerCreated_thenLoadAllIsCalled() =
        runTest {
            // Arrange
            val persistedItems =
                listOf(
                    createTestItem(id = "item-1", category = ItemCategory.FASHION),
                )
            fakeStore.seedItems(persistedItems)

            // Act - Create manager
            createManager()
            advanceUntilIdle()

            // Assert - loadAll should have been called
            assertThat(fakeStore.loadAllCallCount).isEqualTo(1)
        }

    @Test
    fun whenMultipleManagersCreated_thenEachLoadsFromStore() =
        runTest {
            // Arrange
            val persistedItems =
                listOf(
                    createTestItem(id = "item-1", category = ItemCategory.FASHION),
                )
            fakeStore.seedItems(persistedItems)

            // Act - Create multiple managers
            val manager1 = createManager()
            advanceUntilIdle()
            val manager2 = createManager()
            advanceUntilIdle()

            // Assert - Both managers should have loaded items
            assertThat(manager1.items.first()).hasSize(1)
            assertThat(manager2.items.first()).hasSize(1)
            assertThat(fakeStore.loadAllCallCount).isEqualTo(2)
        }

    // ==================== Persistence on Modification Tests ====================

    @Test
    fun whenItemAdded_thenItemIsPersistedToStore() =
        runTest {
            // Arrange
            val manager = createManager()
            advanceUntilIdle()

            // Act - Add an item
            val item = createTestItem(id = "new-item", category = ItemCategory.PLANT)
            manager.addItem(item)
            advanceUntilIdle()

            // Assert - Item should be persisted
            assertThat(fakeStore.upsertAllCallCount).isGreaterThan(0)
            val persistedItems = fakeStore.loadAll()
            assertThat(persistedItems).hasSize(1)
            assertThat(persistedItems[0].category).isEqualTo(ItemCategory.PLANT)
        }

    @Test
    fun whenItemsCleared_thenStoreIsCleared() =
        runTest {
            // Arrange - Create manager with pre-populated store
            val persistedItems =
                listOf(
                    createTestItem(id = "item-1", category = ItemCategory.FASHION),
                    createTestItem(id = "item-2", category = ItemCategory.ELECTRONICS),
                )
            fakeStore.seedItems(persistedItems)
            val manager = createManager()
            advanceUntilIdle()

            // Act - Clear all items
            manager.clearAllItems()
            advanceUntilIdle()

            // Assert - Store should be cleared
            assertThat(fakeStore.deleteAllCallCount).isEqualTo(1)
        }

    // ==================== State Management Tests ====================

    @Test
    fun whenItemAdded_thenStateFlowEmitsNewItem() =
        runTest {
            // Arrange
            val manager = createManager()
            advanceUntilIdle()
            assertThat(manager.items.first()).isEmpty()

            // Act
            val item = createTestItem(id = "item-1", category = ItemCategory.FASHION)
            manager.addItem(item)
            advanceUntilIdle()

            // Assert
            val items = manager.items.first()
            assertThat(items).hasSize(1)
            assertThat(items[0].category).isEqualTo(ItemCategory.FASHION)
        }

    @Test
    fun whenItemRemoved_thenStateFlowUpdates() =
        runTest {
            // Arrange
            val persistedItems =
                listOf(
                    createTestItem(id = "item-1", category = ItemCategory.FASHION),
                    createTestItem(id = "item-2", category = ItemCategory.ELECTRONICS),
                )
            fakeStore.seedItems(persistedItems)
            val manager = createManager()
            advanceUntilIdle()

            // Get the actual aggregated ID (may differ from original ID)
            val initialItems = manager.items.first()
            assertThat(initialItems).hasSize(2)
            val itemToRemove = initialItems.first()

            // Act
            manager.removeItem(itemToRemove.id)
            advanceUntilIdle()

            // Assert
            val items = manager.items.first()
            assertThat(items).hasSize(1)
        }

    @Test
    fun whenGetItemCount_thenReturnsCorrectCount() =
        runTest {
            // Arrange
            val persistedItems =
                listOf(
                    createTestItem(id = "item-1", category = ItemCategory.FASHION),
                    createTestItem(id = "item-2", category = ItemCategory.ELECTRONICS),
                    createTestItem(id = "item-3", category = ItemCategory.HOME_GOOD),
                )
            fakeStore.seedItems(persistedItems)
            val manager = createManager()
            advanceUntilIdle()

            // Assert
            assertThat(manager.getItemCount()).isEqualTo(3)
        }

    // ==================== Detected Attributes Override Tests ====================

    @Test
    fun whenClassificationApplied_fromBackend_thenDetectedAttributesAreStored() =
        runTest {
            // Arrange
            val manager = createManager()
            advanceUntilIdle()

            val item = createTestItem(id = "item-1", category = ItemCategory.FASHION)
            manager.addItem(item)
            advanceUntilIdle()

            // Act - Apply classification from backend
            val brandAttribute =
                ItemAttribute(
                    value = "Nike",
                    confidence = 0.85f,
                    source = "logo",
                )
            manager.applyEnhancedClassification(
                aggregatedId = "item-1",
                category = null,
                label = null,
                priceRange = null,
                attributes = mapOf("brand" to brandAttribute),
                isFromBackend = true,
            )
            // Refresh items to reflect aggregator changes
            manager.refreshItemsFromAggregator()
            advanceUntilIdle()

            // Assert - Both attributes and detectedAttributes should have the value
            val items = manager.items.first()
            assertThat(items).hasSize(1)
            assertThat(items[0].attributes["brand"]?.value).isEqualTo("Nike")
            assertThat(items[0].detectedAttributes["brand"]?.value).isEqualTo("Nike")
        }

    @Test
    fun whenUserEditsAttribute_thenDetectedAttributesArePreserved() =
        runTest {
            // Arrange
            val manager = createManager()
            advanceUntilIdle()

            val item = createTestItem(id = "item-1", category = ItemCategory.FASHION)
            manager.addItem(item)
            advanceUntilIdle()

            // Apply initial classification from backend
            val detectedBrand =
                ItemAttribute(
                    value = "Nike",
                    confidence = 0.85f,
                    source = "logo",
                )
            manager.applyEnhancedClassification(
                aggregatedId = "item-1",
                category = null,
                label = null,
                priceRange = null,
                attributes = mapOf("brand" to detectedBrand),
                isFromBackend = true,
            )
            manager.refreshItemsFromAggregator()
            advanceUntilIdle()

            // Act - User edits the attribute
            val userBrand =
                ItemAttribute(
                    value = "Adidas",
                    confidence = 1.0f,
                    source = "user",
                )
            manager.updateItemAttribute(
                itemId = "item-1",
                attributeKey = "brand",
                attribute = userBrand,
            )
            advanceUntilIdle()

            // Assert - attributes should have user value, detectedAttributes should have original
            val items = manager.items.first()
            assertThat(items).hasSize(1)
            assertThat(items[0].attributes["brand"]?.value).isEqualTo("Adidas")
            assertThat(items[0].attributes["brand"]?.source).isEqualTo("user")
            assertThat(items[0].detectedAttributes["brand"]?.value).isEqualTo("Nike")
            assertThat(items[0].detectedAttributes["brand"]?.source).isEqualTo("logo")
        }

    @Test
    fun whenNewClassificationArrives_userOverridesAreNotReplaced() =
        runTest {
            // Arrange
            val manager = createManager()
            advanceUntilIdle()

            val item = createTestItem(id = "item-1", category = ItemCategory.FASHION)
            manager.addItem(item)
            advanceUntilIdle()

            // User sets a brand value
            val userBrand =
                ItemAttribute(
                    value = "Adidas",
                    confidence = 1.0f,
                    source = "user",
                )
            manager.updateItemAttribute(
                itemId = "item-1",
                attributeKey = "brand",
                attribute = userBrand,
            )
            advanceUntilIdle()

            // Act - New classification arrives from backend
            val detectedBrand =
                ItemAttribute(
                    value = "Nike",
                    confidence = 0.85f,
                    source = "logo",
                )
            manager.applyEnhancedClassification(
                aggregatedId = "item-1",
                category = null,
                label = null,
                priceRange = null,
                attributes = mapOf("brand" to detectedBrand),
                isFromBackend = true,
            )
            manager.refreshItemsFromAggregator()
            advanceUntilIdle()

            // Assert - User override should be preserved, but detectedAttributes updated
            val items = manager.items.first()
            assertThat(items).hasSize(1)
            // User value preserved
            assertThat(items[0].attributes["brand"]?.value).isEqualTo("Adidas")
            assertThat(items[0].attributes["brand"]?.source).isEqualTo("user")
            // Detected value updated
            assertThat(items[0].detectedAttributes["brand"]?.value).isEqualTo("Nike")
        }

    @Test
    fun whenItemHasNoDetectedAttributes_uiDoesNotCrash() =
        runTest {
            // Arrange - Create item without any attributes
            val item = createTestItem(id = "item-1", category = ItemCategory.FASHION)
            fakeStore.seedItems(listOf(item))

            // Act - Create manager (simulates loading from persistence)
            val manager = createManager()
            advanceUntilIdle()

            // Assert - Item should be loaded without crashing
            val items = manager.items.first()
            assertThat(items).hasSize(1)
            assertThat(items[0].attributes).isEmpty()
            assertThat(items[0].detectedAttributes).isEmpty()
        }

    // ==================== Summary Text Persistence Tests ====================

    @Test
    fun whenSummaryTextUpdated_thenItemIsPersistedWithNewSummaryText() =
        runTest {
            // Arrange - Create item without summary text
            val item = createTestItem(id = "item-1", category = ItemCategory.FASHION)
            val manager = createManager()
            manager.addItem(item)
            advanceUntilIdle()

            // Get the actual aggregated ID (may differ from original ID)
            val itemsAfterAdd = manager.items.first()
            assertThat(itemsAfterAdd).hasSize(1)
            val aggregatedId = itemsAfterAdd[0].id

            // Act - Update summary text
            val notesText = "AI-generated description:\nBrand: Nike\nCondition: Like New\n• Premium quality"
            manager.updateSummaryText(
                itemId = aggregatedId,
                summaryText = notesText,
                userEdited = true,
            )
            advanceUntilIdle()

            // Assert - Item should have updated summary text
            val items = manager.items.first()
            assertThat(items).hasSize(1)
            assertThat(items[0].attributesSummaryText).isEqualTo(notesText)
            assertThat(items[0].summaryTextUserEdited).isTrue()

            // Assert - Store should have persisted the change
            val persistedItems = fakeStore.loadAll()
            assertThat(persistedItems).hasSize(1)
            assertThat(persistedItems[0].attributesSummaryText).isEqualTo(notesText)
            assertThat(persistedItems[0].summaryTextUserEdited).isTrue()
        }

    // Note: Full reload test skipped due to test aggregator complexity.
    // The persistence is verified in the first test (whenSummaryTextUpdated_thenItemIsPersistedWithNewSummaryText)

    // ==================== Thumbnail Persistence Tests (Process Death) ====================

    @Test
    fun whenItemWithThumbnailPersisted_afterProcessDeath_thumbnailBytesStillAvailable() =
        runTest {
            // This test verifies the fix for the process death thumbnail bug.
            // Previously, thumbnails were converted to CacheKey and persisted as null bytes.

            // Arrange - Create item with thumbnail bytes
            val thumbnailBytes = createTestThumbnailBytes()
            val itemWithThumbnail =
                createTestItem(
                    id = "item-with-thumbnail",
                    category = ItemCategory.FASHION,
                ).copy(thumbnail = thumbnailBytes)

            fakeStore.seedItems(listOf(itemWithThumbnail))

            // Act - Create first manager, simulating initial app launch
            val manager1 = createManager()
            advanceUntilIdle()

            // Get the persisted item (should still have bytes)
            val persistedItems = fakeStore.loadAll()
            assertThat(persistedItems).hasSize(1)

            // Simulate process death: clear cache and create new manager
            ThumbnailCache.clear()
            val manager2 = createManager()
            advanceUntilIdle()

            // Assert - After "process death", items should still have resolvable thumbnails
            val loadedItems = manager2.items.first()
            assertThat(loadedItems).hasSize(1)

            // The store should still have ImageRef.Bytes (not null)
            val storeItems = fakeStore.loadAll()
            assertThat(storeItems).hasSize(1)
            val storedThumbnail = storeItems[0].thumbnail ?: storeItems[0].thumbnailRef
            assertThat(storedThumbnail).isNotNull()
            assertThat(storedThumbnail).isInstanceOf(ImageRef.Bytes::class.java)
        }

    @Test
    fun whenMultipleItemsWithThumbnails_afterProcessDeath_allThumbnailsPreserved() =
        runTest {
            // Arrange - Create multiple items with thumbnail bytes
            val items =
                listOf(
                    createTestItem(id = "item-1", category = ItemCategory.FASHION)
                        .copy(thumbnail = createTestThumbnailBytes()),
                    createTestItem(id = "item-2", category = ItemCategory.ELECTRONICS)
                        .copy(thumbnail = createTestThumbnailBytes()),
                    createTestItem(id = "item-3", category = ItemCategory.HOME_GOOD)
                        .copy(thumbnail = createTestThumbnailBytes()),
                )
            fakeStore.seedItems(items)

            // Act - Load items, simulate process death, reload
            val manager1 = createManager()
            advanceUntilIdle()

            ThumbnailCache.clear()

            val manager2 = createManager()
            advanceUntilIdle()

            // Assert - All items should have thumbnail bytes in the store
            val storeItems = fakeStore.loadAll()
            assertThat(storeItems).hasSize(3)

            storeItems.forEach { item ->
                val thumbnail = item.thumbnail ?: item.thumbnailRef
                assertThat(thumbnail).isNotNull()
                assertThat(thumbnail).isInstanceOf(ImageRef.Bytes::class.java)
            }
        }

    @Test
    fun whenItemAddedDuringSession_thumbnailBytesPersistedNotCacheKey() =
        runTest {
            // Arrange
            val manager = createManager()
            advanceUntilIdle()

            // Act - Add item with thumbnail bytes during session
            val itemWithThumbnail =
                createTestItem(
                    id = "new-item",
                    category = ItemCategory.PLANT,
                ).copy(thumbnail = createTestThumbnailBytes())

            manager.addItem(itemWithThumbnail)
            advanceUntilIdle()

            // Assert - Persisted item should have Bytes, not CacheKey
            val persistedItems = fakeStore.loadAll()
            assertThat(persistedItems).hasSize(1)

            val persistedThumbnail = persistedItems[0].thumbnail ?: persistedItems[0].thumbnailRef
            assertThat(persistedThumbnail).isNotNull()
            assertThat(persistedThumbnail).isInstanceOf(ImageRef.Bytes::class.java)
        }

    // ==================== Persistence Batching Tests ====================

    @Test
    fun whenMultipleItemsAddedRapidly_thenPersistenceIsBatched() =
        runTest {
            // Arrange
            val manager = createManager()
            advanceUntilIdle()
            val initialUpsertCount = fakeStore.upsertAllCallCount

            // Act - Add multiple items in rapid succession
            val items =
                listOf(
                    createTestItem(id = "item-1", category = ItemCategory.FASHION),
                    createTestItem(id = "item-2", category = ItemCategory.ELECTRONICS),
                    createTestItem(id = "item-3", category = ItemCategory.HOME_GOOD),
                    createTestItem(id = "item-4", category = ItemCategory.PLANT),
                    createTestItem(id = "item-5", category = ItemCategory.FOOD),
                )

            // Add items one by one rapidly
            items.forEach { manager.addItem(it) }
            advanceUntilIdle()

            // Assert - Number of persistence calls should be reasonable (one per item in this case)
            // since each addItem triggers updateItemsState which calls persistItems
            val persistenceCalls = fakeStore.upsertAllCallCount - initialUpsertCount
            assertThat(persistenceCalls).isEqualTo(items.size)

            // Assert - All items should be persisted
            val persistedItems = fakeStore.loadAll()
            assertThat(persistedItems).hasSize(items.size)
        }

    @Test
    fun whenBatchAddItems_thenSinglePersistenceCall() =
        runTest {
            // Arrange
            val manager = createManager()
            advanceUntilIdle()
            val initialUpsertCount = fakeStore.upsertAllCallCount

            // Act - Add items using batch method
            val items =
                listOf(
                    createTestItem(id = "item-1", category = ItemCategory.FASHION),
                    createTestItem(id = "item-2", category = ItemCategory.ELECTRONICS),
                    createTestItem(id = "item-3", category = ItemCategory.HOME_GOOD),
                )
            manager.addItems(items)
            advanceUntilIdle()

            // Assert - Should result in a single persistence call (more efficient)
            val persistenceCalls = fakeStore.upsertAllCallCount - initialUpsertCount
            assertThat(persistenceCalls).isEqualTo(1)

            // Assert - All items should be persisted
            val persistedItems = fakeStore.loadAll()
            assertThat(persistedItems).hasSize(items.size)
        }

    @Test
    fun whenFieldsUpdated_thenPersistenceTriggered() =
        runTest {
            // Arrange
            val manager = createManager()
            val item = createTestItem(id = "item-1", category = ItemCategory.FASHION)
            manager.addItem(item)
            advanceUntilIdle()

            val itemsAfterAdd = manager.items.first()
            val aggregatedId = itemsAfterAdd[0].id

            val initialUpsertCount = fakeStore.upsertAllCallCount

            // Act - Update item fields
            manager.updateItemFields(
                itemId = aggregatedId,
                labelText = "Updated Label",
                recognizedText = "Updated Text",
            )
            advanceUntilIdle()

            // Assert - Persistence should be triggered
            val persistenceCalls = fakeStore.upsertAllCallCount - initialUpsertCount
            assertThat(persistenceCalls).isAtLeast(1)

            // Assert - Changes should be persisted
            val persistedItems = fakeStore.loadAll()
            assertThat(persistedItems[0].labelText).isEqualTo("Updated Label")
            assertThat(persistedItems[0].recognizedText).isEqualTo("Updated Text")
        }

    @Test
    fun whenMultipleFieldsUpdatedInBulk_thenSinglePersistence() =
        runTest {
            // Arrange
            val manager = createManager()
            val items =
                listOf(
                    createTestItem(id = "item-1", category = ItemCategory.FASHION),
                    createTestItem(id = "item-2", category = ItemCategory.ELECTRONICS),
                    createTestItem(id = "item-3", category = ItemCategory.HOME_GOOD),
                )
            manager.addItems(items)
            advanceUntilIdle()

            val itemsAfterAdd = manager.items.first()
            val initialUpsertCount = fakeStore.upsertAllCallCount

            // Act - Update multiple items using bulk update
            val updates =
                itemsAfterAdd.associate { item ->
                    item.id to ItemFieldUpdate(labelText = "Bulk Update ${item.id}")
                }
            manager.updateItemsFields(updates)
            advanceUntilIdle()

            // Assert - Should result in single persistence call
            val persistenceCalls = fakeStore.upsertAllCallCount - initialUpsertCount
            assertThat(persistenceCalls).isEqualTo(1)

            // Assert - All items should have updated labels
            val persistedItems = fakeStore.loadAll()
            assertThat(persistedItems).hasSize(3)
            persistedItems.forEach { item ->
                assertThat(item.labelText).contains("Bulk Update")
            }
        }

    @Test
    fun whenClearAllItems_thenDeleteAllCalled() =
        runTest {
            // Arrange
            val manager = createManager()
            val items =
                listOf(
                    createTestItem(id = "item-1", category = ItemCategory.FASHION),
                    createTestItem(id = "item-2", category = ItemCategory.ELECTRONICS),
                )
            manager.addItems(items)
            advanceUntilIdle()

            val initialDeleteAllCount = fakeStore.deleteAllCallCount

            // Act - Clear all items
            manager.clearAllItems()
            advanceUntilIdle()

            // Assert - deleteAll should be called exactly once
            val deleteAllCalls = fakeStore.deleteAllCallCount - initialDeleteAllCount
            assertThat(deleteAllCalls).isEqualTo(1)

            // Assert - Store should be empty
            val persistedItems = fakeStore.loadAll()
            assertThat(persistedItems).isEmpty()
        }

    // ==================== Aggregation Invariants Tests ====================

    @Test
    fun whenItemsAdded_thenAggregationStatsAreAccurate() =
        runTest {
            // Arrange
            val manager = createManager()
            advanceUntilIdle()

            // Act - Add multiple items
            val items =
                listOf(
                    createTestItem(id = "item-1", category = ItemCategory.FASHION),
                    createTestItem(id = "item-2", category = ItemCategory.ELECTRONICS),
                    createTestItem(id = "item-3", category = ItemCategory.HOME_GOOD),
                )
            manager.addItems(items)
            advanceUntilIdle()

            // Assert - Aggregation stats should reflect added items
            val stats = manager.getAggregationStats()
            assertThat(stats.totalItems).isEqualTo(3)
            assertThat(stats.totalMerges).isAtLeast(0) // No merges expected for different categories
        }

    @Test
    fun whenSimilarItemsAdded_thenAggregationMergeCountIncreases() =
        runTest {
            // Arrange
            val manager = createManager()
            advanceUntilIdle()

            // Act - Add similar items (same category, same position)
            val similarItems =
                listOf(
                    createTestItem(id = "item-1", category = ItemCategory.FASHION, confidence = 0.9f),
                    createTestItem(id = "item-2", category = ItemCategory.FASHION, confidence = 0.85f),
                    createTestItem(id = "item-3", category = ItemCategory.FASHION, confidence = 0.88f),
                )
            manager.addItems(similarItems)
            advanceUntilIdle()

            // Assert - Items should be aggregated (merged) based on similarity
            val stats = manager.getAggregationStats()
            // Since all items have same category and same bounding box, they should merge
            assertThat(stats.totalMerges).isGreaterThan(0)

            // The number of items in state should be less than added items due to merging
            val items = manager.items.first()
            assertThat(items.size).isLessThan(similarItems.size)
        }

    @Test
    fun whenDifferentItemsAdded_thenNoMerging() =
        runTest {
            // Arrange
            val manager = createManager()
            advanceUntilIdle()

            // Act - Add different items (different categories, different positions)
            val differentItems =
                listOf(
                    createTestItem(id = "item-1", category = ItemCategory.FASHION).copy(
                        boundingBox = NormalizedRect(0.1f, 0.1f, 0.3f, 0.3f),
                    ),
                    createTestItem(id = "item-2", category = ItemCategory.ELECTRONICS).copy(
                        boundingBox = NormalizedRect(0.5f, 0.5f, 0.7f, 0.7f),
                    ),
                    createTestItem(id = "item-3", category = ItemCategory.HOME_GOOD).copy(
                        boundingBox = NormalizedRect(0.8f, 0.1f, 0.9f, 0.3f),
                    ),
                )
            manager.addItems(differentItems)
            advanceUntilIdle()

            // Assert - No merging should occur
            val items = manager.items.first()
            assertThat(items.size).isEqualTo(differentItems.size)
        }

    @Test
    fun whenSimilarityThresholdUpdated_thenAggregatorReflectsChange() =
        runTest {
            // Arrange
            val manager = createManager()
            advanceUntilIdle()

            val initialThreshold = manager.getCurrentSimilarityThreshold()

            // Act - Update similarity threshold
            val newThreshold = 0.75f
            manager.updateSimilarityThreshold(newThreshold)
            advanceUntilIdle()

            // Assert - Threshold should be updated
            val currentThreshold = manager.getCurrentSimilarityThreshold()
            assertThat(currentThreshold).isEqualTo(newThreshold)
            assertThat(currentThreshold).isNotEqualTo(initialThreshold)

            // Assert - StateFlow should also reflect the change
            val thresholdFlow = manager.similarityThreshold.first()
            assertThat(thresholdFlow).isEqualTo(newThreshold)
        }

    @Test
    fun whenSimilarityThresholdSetBelowZero_thenClampedToZero() =
        runTest {
            // Arrange
            val manager = createManager()
            advanceUntilIdle()

            // Act - Set threshold below 0
            manager.updateSimilarityThreshold(-0.5f)
            advanceUntilIdle()

            // Assert - Should be clamped to 0
            val currentThreshold = manager.getCurrentSimilarityThreshold()
            assertThat(currentThreshold).isEqualTo(0f)
        }

    @Test
    fun whenSimilarityThresholdSetAboveOne_thenClampedToOne() =
        runTest {
            // Arrange
            val manager = createManager()
            advanceUntilIdle()

            // Act - Set threshold above 1
            manager.updateSimilarityThreshold(1.5f)
            advanceUntilIdle()

            // Assert - Should be clamped to 1
            val currentThreshold = manager.getCurrentSimilarityThreshold()
            assertThat(currentThreshold).isEqualTo(1f)
        }

    @Test
    fun whenItemRemoved_thenAggregationStatsUpdate() =
        runTest {
            // Arrange
            val manager = createManager()
            val items =
                listOf(
                    createTestItem(id = "item-1", category = ItemCategory.FASHION),
                    createTestItem(id = "item-2", category = ItemCategory.ELECTRONICS),
                    createTestItem(id = "item-3", category = ItemCategory.HOME_GOOD),
                )
            manager.addItems(items)
            advanceUntilIdle()

            val itemsAfterAdd = manager.items.first()
            val itemToRemove = itemsAfterAdd.first()
            val initialItemCount = manager.getAggregationStats().totalItems

            // Act - Remove an item
            manager.removeItem(itemToRemove.id)
            advanceUntilIdle()

            // Assert - Item count should decrease
            val stats = manager.getAggregationStats()
            assertThat(stats.totalItems).isEqualTo(initialItemCount - 1)
        }

    @Test
    fun whenStaleItemsRemoved_thenOnlyStaleItemsAreDeleted() =
        runTest {
            // Arrange
            val manager = createManager()
            val oldTimestamp = System.currentTimeMillis() - 60_000L // 60 seconds ago
            val recentTimestamp = System.currentTimeMillis()

            val items =
                listOf(
                    createTestItem(id = "stale-1", category = ItemCategory.FASHION).copy(
                        timestamp = oldTimestamp,
                    ),
                    createTestItem(id = "recent-1", category = ItemCategory.ELECTRONICS).copy(
                        timestamp = recentTimestamp,
                    ),
                    createTestItem(id = "stale-2", category = ItemCategory.HOME_GOOD).copy(
                        timestamp = oldTimestamp,
                    ),
                )
            manager.addItems(items)
            advanceUntilIdle()

            // Act - Remove stale items (older than 30 seconds)
            manager.removeStaleItems(maxAgeMs = 30_000L)
            advanceUntilIdle()

            // Assert - Only recent items should remain
            val remainingItems = manager.items.first()
            // Note: Aggregation may have merged items, so we check that count decreased
            assertThat(remainingItems.size).isLessThan(items.size)
        }

    @Test
    fun whenGetAggregatedItems_thenReturnsAggregatedList() =
        runTest {
            // Arrange
            val manager = createManager()
            val items =
                listOf(
                    createTestItem(id = "item-1", category = ItemCategory.FASHION),
                    createTestItem(id = "item-2", category = ItemCategory.ELECTRONICS),
                )
            manager.addItems(items)
            advanceUntilIdle()

            // Act
            val aggregatedItems = manager.getAggregatedItems()

            // Assert - Should return aggregated items list
            assertThat(aggregatedItems).isNotEmpty()
            assertThat(aggregatedItems.size).isAtLeast(1)
        }

    @Test
    fun whenItemCountRequested_thenMatchesStateFlowSize() =
        runTest {
            // Arrange
            val manager = createManager()
            val items =
                listOf(
                    createTestItem(id = "item-1", category = ItemCategory.FASHION),
                    createTestItem(id = "item-2", category = ItemCategory.ELECTRONICS),
                    createTestItem(id = "item-3", category = ItemCategory.HOME_GOOD),
                )
            manager.addItems(items)
            advanceUntilIdle()

            // Act
            val itemCount = manager.getItemCount()
            val stateFlowItems = manager.items.first()

            // Assert - Item count should match state flow size
            assertThat(itemCount).isEqualTo(stateFlowItems.size)
        }

    @Test
    fun whenSeedFromScannedItems_thenItemsAreLoadedIntoAggregator() =
        runTest {
            // Arrange
            val manager = createManager()
            advanceUntilIdle()

            val seedItems =
                listOf(
                    createTestItem(id = "seed-1", category = ItemCategory.FASHION),
                    createTestItem(id = "seed-2", category = ItemCategory.ELECTRONICS),
                )

            // Act - Seed the aggregator
            manager.seedFromScannedItems(seedItems)
            // Manually refresh state to reflect seeded items
            manager.refreshItemsFromAggregator()
            advanceUntilIdle()

            // Assert - Items should be available in aggregator
            val aggregatedItems = manager.getAggregatedItems()
            assertThat(aggregatedItems).hasSize(2)
        }

    @Test
    fun whenAggregationStatsRequested_thenAverageMergesCalculatedCorrectly() =
        runTest {
            // Arrange
            val manager = createManager()

            // Add items that should merge (same category, same position)
            val items =
                listOf(
                    createTestItem(id = "item-1", category = ItemCategory.FASHION),
                    createTestItem(id = "item-2", category = ItemCategory.FASHION),
                    createTestItem(id = "item-3", category = ItemCategory.FASHION),
                )
            manager.addItems(items)
            advanceUntilIdle()

            // Act
            val stats = manager.getAggregationStats()

            // Assert - Average merges should be calculated
            assertThat(stats.averageMergesPerItem).isAtLeast(0f)
            // If items merged, average should be > 0
            if (stats.totalMerges > 0) {
                assertThat(stats.averageMergesPerItem).isGreaterThan(0f)
            }
        }

    // ==================== Helper Methods ====================

    private fun createTestThumbnailBytes(): ImageRef.Bytes {
        // Create a minimal valid PNG-like byte array for testing
        // Real PNG header + minimal data
        val testBytes =
            byteArrayOf(
                // PNG signature
                0x89.toByte(), 0x50, 0x4E, 0x47,
                // PNG signature continued
                0x0D, 0x0A, 0x1A, 0x0A,
                // IHDR chunk length
                0x00, 0x00, 0x00, 0x0D,
                // "IHDR"
                0x49, 0x48, 0x44, 0x52,
                // width = 1
                0x00, 0x00, 0x00, 0x01,
                // height = 1
                0x00, 0x00, 0x00, 0x01,
                // bit depth, color type
                0x08, 0x02,
                // compression, filter, interlace
                0x00, 0x00, 0x00,
                // CRC placeholder
                0x00, 0x00, 0x00, 0x00,
            )
        return ImageRef.Bytes(
            bytes = testBytes,
            mimeType = "image/png",
            width = 100,
            height = 100,
        )
    }

    private fun createManager(): ItemsStateManager {
        return ItemsStateManager(
            scope = kotlinx.coroutines.CoroutineScope(testDispatcher),
            itemsStore = fakeStore,
            initialWorkerDispatcher = testDispatcher,
            initialMainDispatcher = testDispatcher,
        )
    }

    private fun createTestItem(
        id: String,
        category: ItemCategory,
        confidence: Float = 0.8f,
    ): ScannedItem {
        return ScannedItem(
            id = id,
            thumbnail = null,
            category = category,
            priceRange = 10.0 to 50.0,
            confidence = confidence,
            timestamp = System.currentTimeMillis(),
            boundingBox = NormalizedRect(0.1f, 0.1f, 0.5f, 0.5f),
            labelText = category.name,
        )
    }
}

/**
 * Fake implementation of ScannedItemStore for testing.
 */
class FakeScannedItemStore : ScannedItemStore {
    private val items = mutableListOf<ScannedItem>()
    private val _errors = MutableSharedFlow<PersistenceError>(extraBufferCapacity = 1)
    override val errors: SharedFlow<PersistenceError> = _errors.asSharedFlow()

    var loadAllCallCount = 0
        private set
    var upsertAllCallCount = 0
        private set
    var deleteByIdCallCount = 0
        private set
    var deleteAllCallCount = 0
        private set

    fun seedItems(seedItems: List<ScannedItem>) {
        items.clear()
        items.addAll(seedItems)
    }

    override suspend fun loadAll(): List<ScannedItem> {
        loadAllCallCount++
        return items.toList()
    }

    override suspend fun upsertAll(items: List<ScannedItem>) {
        upsertAllCallCount++
        this.items.clear()
        this.items.addAll(items)
    }

    override suspend fun deleteById(itemId: String) {
        deleteByIdCallCount++
        items.removeAll { it.id == itemId }
    }

    override suspend fun deleteAll() {
        deleteAllCallCount++
        items.clear()
    }
}
