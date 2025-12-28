package com.scanium.app.items

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scanium.app.items.persistence.ScannedItemStore
import com.scanium.core.models.geometry.NormalizedRect
import com.scanium.core.models.ml.ItemCategory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for ItemsViewModel in Android environment.
 *
 * These tests verify the ViewModel works correctly on actual Android runtime,
 * testing StateFlow behavior, persistence loading, and Android-specific concerns.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ItemsViewModelInstrumentedTest {

    private lateinit var viewModel: ItemsViewModel
    private lateinit var fakeStore: InstrumentedFakeScannedItemStore
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeStore = InstrumentedFakeScannedItemStore()
        viewModel = createAndroidTestItemsViewModel(
            itemsStore = fakeStore,
            workerDispatcher = testDispatcher,
            mainDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun whenAddingItemsOnAndroid_thenStateFlowEmitsCorrectly() = runBlocking {
        // Arrange
        val item = ScannedItem(
            id = "test-1",
            thumbnail = null,
            category = ItemCategory.FASHION,
            priceRange = 10.0 to 20.0,
            confidence = 0.8f,
            timestamp = System.currentTimeMillis(),
            boundingBox = NormalizedRect(0.1f, 0.1f, 0.5f, 0.5f),
            labelText = "Fashion"
        )

        // Act
        viewModel.addItem(item)
        delay(100) // Give coroutine time to complete

        // Assert
        val items = viewModel.items.first()
        assertThat(items).hasSize(1)
    }

    @Test
    fun whenConcurrentAdditions_thenAllItemsSafelyAdded() = runBlocking {
        // Arrange - Create multiple items with distinct categories to avoid merging
        val items = listOf(
            createTestItem("item-1", ItemCategory.FASHION),
            createTestItem("item-2", ItemCategory.ELECTRONICS),
            createTestItem("item-3", ItemCategory.HOME_GOOD),
            createTestItem("item-4", ItemCategory.PLANT),
            createTestItem("item-5", ItemCategory.FOOD)
        )

        // Act - Add items
        items.forEach { item ->
            viewModel.addItem(item)
        }
        delay(200) // Give coroutines time to complete

        // Assert
        val resultItems = viewModel.items.first()
        assertThat(resultItems).hasSize(5)
    }

    @Test
    fun whenViewModelCreatedWithPersistedItems_thenItemsAreLoaded() = runBlocking {
        // Arrange - Pre-populate the store with persisted items
        val persistedItems = listOf(
            createTestItem("persisted-1", ItemCategory.FASHION),
            createTestItem("persisted-2", ItemCategory.ELECTRONICS)
        )
        fakeStore.seedItems(persistedItems)

        // Act - Create new ViewModel (should load persisted items)
        val newViewModel = createAndroidTestItemsViewModel(
            itemsStore = fakeStore,
            workerDispatcher = testDispatcher,
            mainDispatcher = testDispatcher
        )
        delay(100) // Give loading time to complete

        // Assert - Items should be loaded from persistence
        val items = newViewModel.items.first()
        assertThat(items).hasSize(2)
    }

    @Test
    fun whenRemovingAndReAdding_thenItemAppearsAgain() = runBlocking {
        // Arrange
        val item = createTestItem("test-1", ItemCategory.FASHION)
        viewModel.addItem(item)
        delay(100)

        // Get aggregated ID
        val aggregatedId = viewModel.items.first().first().id
        viewModel.removeItem(aggregatedId)
        delay(100)

        // Act - Re-add the same item
        viewModel.addItem(item)
        delay(100)

        // Assert
        val items = viewModel.items.first()
        assertThat(items).hasSize(1)
    }

    @Test
    fun whenItemsCleared_thenNewViewModelStartsEmpty() = runBlocking {
        // Arrange - Add items and then clear
        viewModel.addItem(createTestItem("item-1", ItemCategory.FASHION))
        delay(100)
        viewModel.clearAllItems()
        delay(100)

        // Act - Create new ViewModel
        val newViewModel = createAndroidTestItemsViewModel(
            itemsStore = fakeStore,
            workerDispatcher = testDispatcher,
            mainDispatcher = testDispatcher
        )
        delay(100)

        // Assert - New ViewModel should be empty (store was cleared)
        val items = newViewModel.items.first()
        assertThat(items).isEmpty()
    }

    private fun createTestItem(id: String, category: ItemCategory): ScannedItem {
        return ScannedItem(
            id = id,
            thumbnail = null,
            category = category,
            priceRange = 10.0 to 20.0,
            confidence = 0.8f,
            timestamp = System.currentTimeMillis(),
            boundingBox = NormalizedRect(0.1f, 0.1f, 0.5f, 0.5f),
            labelText = category.name
        )
    }
}

/**
 * Fake implementation of ScannedItemStore for instrumented testing.
 */
class InstrumentedFakeScannedItemStore : ScannedItemStore {
    private val items = mutableListOf<ScannedItem>()

    fun seedItems(seedItems: List<ScannedItem>) {
        items.clear()
        items.addAll(seedItems)
    }

    override suspend fun loadAll(): List<ScannedItem> {
        return items.toList()
    }

    override suspend fun upsertAll(items: List<ScannedItem>) {
        this.items.clear()
        this.items.addAll(items)
    }

    override suspend fun deleteById(itemId: String) {
        items.removeAll { it.id == itemId }
    }

    override suspend fun deleteAll() {
        items.clear()
    }
}
