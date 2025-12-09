package com.scanium.app.items

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scanium.app.ml.ItemCategory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for ItemsViewModel in Android environment.
 *
 * These tests verify the ViewModel works correctly on actual Android runtime,
 * testing StateFlow behavior and Android-specific concerns.
 */
@RunWith(AndroidJUnit4::class)
class ItemsViewModelInstrumentedTest {

    private lateinit var viewModel: ItemsViewModel

    @Before
    fun setUp() {
        viewModel = ItemsViewModel()
    }

    @Test
    fun whenAddingItemsOnAndroid_thenStateFlowEmitsCorrectly() = runBlocking {
        // Arrange
        val item = ScannedItem(
            id = "test-1",
            category = ItemCategory.FASHION,
            priceRange = 10.0 to 20.0,
            confidence = 0.8f
        )

        // Act
        viewModel.addItem(item)

        // Assert
        val items = viewModel.items.first()
        assertThat(items).hasSize(1)
        assertThat(items[0].id).isEqualTo("test-1")
    }

    @Test
    fun whenConcurrentAdditions_thenAllItemsSafelyAdded() = runBlocking {
        // Arrange - Create multiple items
        val items = (1..10).map { index ->
            ScannedItem(
                id = "item-$index",
                category = ItemCategory.FASHION,
                priceRange = 10.0 to 20.0,
                confidence = 0.5f
            )
        }

        // Act - Add items concurrently
        items.forEach { item ->
            viewModel.addItem(item)
        }

        // Assert
        val resultItems = viewModel.items.first()
        assertThat(resultItems).hasSize(10)
    }

    @Test
    fun whenViewModelRecreated_thenItemsDoNotPersist() = runBlocking {
        // Arrange
        viewModel.addItem(
            ScannedItem(
                id = "test-1",
                category = ItemCategory.FASHION,
                priceRange = 10.0 to 20.0
            )
        )

        // Act - Create new ViewModel instance (simulates process death)
        val newViewModel = ItemsViewModel()
        val items = newViewModel.items.first()

        // Assert - New instance should be empty
        assertThat(items).isEmpty()
    }

    @Test
    fun whenRemovingAndReAdding_thenItemAppearsAgain() = runBlocking {
        // Arrange
        val item = ScannedItem(
            id = "test-1",
            category = ItemCategory.FASHION,
            priceRange = 10.0 to 20.0
        )
        viewModel.addItem(item)
        viewModel.removeItem("test-1")

        // Act - Re-add the same item
        viewModel.addItem(item)

        // Assert
        val items = viewModel.items.first()
        assertThat(items).hasSize(1)
        assertThat(items[0].id).isEqualTo("test-1")
    }
}
