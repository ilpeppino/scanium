package com.scanium.app.selling.assistant

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scanium.app.ScannedItem
import com.scanium.app.items.createAndroidTestItemsViewModel
import com.scanium.app.listing.ListingDraft
import com.scanium.app.listing.ListingDraftBuilder
import com.scanium.app.ItemCategory
import com.scanium.app.selling.persistence.ListingDraftStore
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssistantScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun assistantScreenShowsSelectedItemContext() {
        val item =
            ScannedItem(
                id = "item-1",
                category = ItemCategory.HOME_GOOD,
                priceRange = 10.0 to 20.0,
                confidence = 0.9f,
                labelText = "Lamp",
                timestamp = 1000L,
            )
        val draft =
            ListingDraftBuilder.build(item).copy(
                title = ListingDraftBuilder.build(item).title.copy(value = "Vintage Lamp"),
            )
        val draftStore = FakeDraftStore(mapOf(item.id to draft))
        val itemsViewModel =
            createAndroidTestItemsViewModel(
                workerDispatcher = Dispatchers.Main.immediate,
                mainDispatcher = Dispatchers.Main.immediate,
            )

        composeTestRule.setContent {
            AssistantScreen(
                itemIds = listOf(item.id),
                onBack = {},
                onOpenPostingAssist = { _, _ -> },
                itemsViewModel = itemsViewModel,
                draftStore = draftStore,
            )
        }

        composeTestRule.onNodeWithText("Export Assistant").assertIsDisplayed()
        composeTestRule.onNodeWithText("Vintage Lamp").assertIsDisplayed()
    }

    private class FakeDraftStore(
        private val drafts: Map<String, ListingDraft>,
    ) : ListingDraftStore {
        override suspend fun getAll(): List<ListingDraft> = drafts.values.toList()

        override suspend fun getByItemId(itemId: String): ListingDraft? = drafts[itemId]

        override suspend fun upsert(draft: ListingDraft) = Unit

        override suspend fun deleteById(id: String) = Unit
    }
}
