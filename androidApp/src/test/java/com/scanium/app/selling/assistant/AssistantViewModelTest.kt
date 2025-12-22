package com.scanium.app.selling.assistant

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scanium.app.data.ExportProfilePreferences
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.listing.DraftField
import com.scanium.app.listing.DraftProvenance
import com.scanium.app.listing.ExportProfileDefinition
import com.scanium.app.listing.ExportProfileId
import com.scanium.app.listing.ExportProfileRepository
import com.scanium.app.listing.ExportProfiles
import com.scanium.app.listing.ListingDraft
import com.scanium.app.model.AssistantAction
import com.scanium.app.model.AssistantActionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModelTest {
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
    fun applyDraftUpdate_persistsChanges() = runTest {
        val store = FakeDraftStore()
        val profileRepository = FakeExportProfileRepository()
        val profilePreferences = ExportProfilePreferences(ApplicationProvider.getApplicationContext())
        val draft = ListingDraft(
            id = "draft-1",
            itemId = "item-1",
            profile = ExportProfileId.GENERIC,
            title = DraftField("Old Title", confidence = 0.5f, source = DraftProvenance.DEFAULT),
            description = DraftField("Old description", confidence = 0.5f, source = DraftProvenance.DEFAULT),
            fields = emptyMap(),
            price = DraftField(10.0, confidence = 0.5f, source = DraftProvenance.DEFAULT),
            photos = emptyList(),
            status = com.scanium.app.listing.DraftStatus.DRAFT,
            createdAt = 1L,
            updatedAt = 1L
        )
        store.upsert(draft)

        val viewModel = AssistantViewModel(
            itemIds = listOf("item-1"),
            itemsViewModel = itemsViewModel,
            draftStore = store,
            exportProfileRepository = profileRepository,
            exportProfilePreferences = profilePreferences,
            assistantRepository = FakeAssistantRepository()
        )

        val action = AssistantAction(
            type = AssistantActionType.APPLY_DRAFT_UPDATE,
            payload = mapOf("itemId" to "item-1", "title" to "Updated Title")
        )
        viewModel.applyDraftUpdate(action)
        advanceUntilIdle()

        val updated = store.getByItemId("item-1")
        assertThat(updated?.title?.value).isEqualTo("Updated Title")
    }

    private class FakeDraftStore : com.scanium.app.selling.persistence.ListingDraftStore {
        private val drafts = mutableMapOf<String, ListingDraft>()

        override suspend fun getAll(): List<ListingDraft> = drafts.values.toList()

        override suspend fun getByItemId(itemId: String): ListingDraft? = drafts[itemId]

        override suspend fun upsert(draft: ListingDraft) {
            drafts[draft.itemId] = draft
        }

        override suspend fun deleteById(id: String) {
            drafts.remove(id)
        }
    }

    private class FakeExportProfileRepository : ExportProfileRepository {
        override suspend fun getProfiles(): List<ExportProfileDefinition> = listOf(ExportProfiles.generic())

        override suspend fun getProfile(id: ExportProfileId): ExportProfileDefinition? {
            return ExportProfiles.generic().takeIf { it.id == id }
        }

        override suspend fun getDefaultProfileId(): ExportProfileId {
            return ExportProfiles.generic().id
        }
    }

    private class FakeAssistantRepository : AssistantRepository {
        override suspend fun send(
            items: List<com.scanium.app.model.ItemContextSnapshot>,
            history: List<com.scanium.app.model.AssistantMessage>,
            userMessage: String,
            exportProfile: ExportProfileDefinition,
            correlationId: String
        ): com.scanium.app.model.AssistantResponse {
            return com.scanium.app.model.AssistantResponse("ok")
        }
    }
}
