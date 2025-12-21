package com.scanium.app.selling.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.items.ScannedItem
import com.scanium.app.ml.ItemCategory
import com.scanium.app.selling.persistence.ListingDraftStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class DraftReviewViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var itemsViewModel: ItemsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        itemsViewModel = ItemsViewModel(
            workerDispatcher = testDispatcher,
            mainDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun navigationMovesBetweenItemIds() = runTest {
        val items = listOf(
            testItem(id = "item-1", label = "Lamp"),
            testItem(id = "item-2", label = "Chair")
        )
        itemsViewModel.addItems(items)
        testDispatcher.scheduler.advanceUntilIdle()

        val store = FakeDraftStore()
        val viewModel = DraftReviewViewModel(
            itemIds = items.map { it.id },
            itemsViewModel = itemsViewModel,
            draftStore = store
        )

        advanceUntilIdle()
        assertThat(viewModel.uiState.value.currentItemId).isEqualTo("item-1")

        viewModel.goToNext()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.currentItemId).isEqualTo("item-2")

        viewModel.goToPrevious()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.currentItemId).isEqualTo("item-1")
    }

    @Test
    fun dirtyDraftIsSavedAndRestoredOnNavigation() = runTest {
        val items = listOf(
            testItem(id = "item-1", label = "Lamp"),
            testItem(id = "item-2", label = "Chair")
        )
        itemsViewModel.addItems(items)
        testDispatcher.scheduler.advanceUntilIdle()

        val store = FakeDraftStore()
        val viewModel = DraftReviewViewModel(
            itemIds = items.map { it.id },
            itemsViewModel = itemsViewModel,
            draftStore = store
        )

        advanceUntilIdle()
        viewModel.updateTitle("Edited Lamp")
        advanceUntilIdle()

        viewModel.goToNext()
        advanceUntilIdle()

        val savedDraft = store.getByItemId("item-1")
        assertThat(savedDraft?.title?.value).isEqualTo("Edited Lamp")

        viewModel.goToPrevious()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.draft?.title?.value).isEqualTo("Edited Lamp")
    }

    private fun testItem(id: String, label: String): ScannedItem {
        return ScannedItem(
            id = id,
            category = ItemCategory.HOME_GOOD,
            priceRange = 5.0 to 10.0,
            confidence = 0.8f,
            labelText = label,
            timestamp = 1000L
        )
    }

    private class FakeDraftStore : ListingDraftStore {
        private val drafts = mutableMapOf<String, com.scanium.app.listing.ListingDraft>()

        override suspend fun getAll(): List<com.scanium.app.listing.ListingDraft> = drafts.values.toList()

        override suspend fun getByItemId(itemId: String): com.scanium.app.listing.ListingDraft? = drafts[itemId]

        override suspend fun upsert(draft: com.scanium.app.listing.ListingDraft) {
            drafts[draft.itemId] = draft
        }
    }
}
