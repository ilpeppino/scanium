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
import com.scanium.app.model.AssistantPrefs
import com.scanium.app.data.SettingsRepository
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
    private lateinit var settingsRepository: SettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        itemsViewModel = ItemsViewModel(
            workerDispatcher = testDispatcher,
            mainDispatcher = testDispatcher
        )
        settingsRepository = SettingsRepository(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun sendMessage_setsLoadingState() = runTest {
        val store = FakeDraftStore()
        val profileRepository = FakeExportProfileRepository()
        val profilePreferences = ExportProfilePreferences(ApplicationProvider.getApplicationContext())
        val repository = DelayingAssistantRepository()

        val viewModel = AssistantViewModel(
            itemIds = listOf("item-1"),
            itemsViewModel = itemsViewModel,
            draftStore = store,
            exportProfileRepository = profileRepository,
            exportProfilePreferences = profilePreferences,
            assistantRepository = repository,
            settingsRepository = settingsRepository
        )

        // Initial state should be IDLE
        assertThat(viewModel.uiState.value.loadingStage).isEqualTo(LoadingStage.IDLE)
        assertThat(viewModel.uiState.value.isLoading).isFalse()

        // Send message - with UnconfinedTestDispatcher, coroutine starts immediately
        // so we'll be in LLM_PROCESSING (since VISION_PROCESSING is set sync, then updated to LLM)
        viewModel.sendMessage("What color is this?")

        // Should be loading now (at LLM_PROCESSING stage since coroutine ran immediately)
        assertThat(viewModel.uiState.value.isLoading).isTrue()
        // Stage should be in a processing state (either VISION or LLM)
        assertThat(viewModel.uiState.value.loadingStage).isIn(
            listOf(LoadingStage.VISION_PROCESSING, LoadingStage.LLM_PROCESSING)
        )
    }

    @Test
    fun sendMessage_updatesToLLMProcessingAfterVision() = runTest {
        val store = FakeDraftStore()
        val profileRepository = FakeExportProfileRepository()
        val profilePreferences = ExportProfilePreferences(ApplicationProvider.getApplicationContext())
        val repository = FakeAssistantRepository()

        val viewModel = AssistantViewModel(
            itemIds = listOf("item-1"),
            itemsViewModel = itemsViewModel,
            draftStore = store,
            exportProfileRepository = profileRepository,
            exportProfilePreferences = profilePreferences,
            assistantRepository = repository,
            settingsRepository = settingsRepository
        )

        viewModel.sendMessage("What color is this?")
        advanceUntilIdle()

        // After completion, stage should be DONE
        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(viewModel.uiState.value.loadingStage).isEqualTo(LoadingStage.DONE)
    }

    @Test
    fun sendMessage_failure_setsErrorStage() = runTest {
        val store = FakeDraftStore()
        val profileRepository = FakeExportProfileRepository()
        val profilePreferences = ExportProfilePreferences(ApplicationProvider.getApplicationContext())
        val repository = FailingAssistantRepository()

        val viewModel = AssistantViewModel(
            itemIds = listOf("item-1"),
            itemsViewModel = itemsViewModel,
            draftStore = store,
            exportProfileRepository = profileRepository,
            exportProfilePreferences = profilePreferences,
            assistantRepository = repository,
            settingsRepository = settingsRepository
        )

        viewModel.sendMessage("What color is this?")
        advanceUntilIdle()

        // After failure, stage should be ERROR and message should be preserved
        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(viewModel.uiState.value.loadingStage).isEqualTo(LoadingStage.ERROR)
        assertThat(viewModel.uiState.value.failedMessageText).isEqualTo("What color is this?")
    }

    @Test
    fun retryLastMessage_resetsStageAndRetries() = runTest {
        val store = FakeDraftStore()
        val profileRepository = FakeExportProfileRepository()
        val profilePreferences = ExportProfilePreferences(ApplicationProvider.getApplicationContext())
        val repository = SuccessAfterFailureRepository()

        val viewModel = AssistantViewModel(
            itemIds = listOf("item-1"),
            itemsViewModel = itemsViewModel,
            draftStore = store,
            exportProfileRepository = profileRepository,
            exportProfilePreferences = profilePreferences,
            assistantRepository = repository,
            settingsRepository = settingsRepository
        )

        // First send should fail
        viewModel.sendMessage("Test message")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.loadingStage).isEqualTo(LoadingStage.ERROR)
        assertThat(viewModel.uiState.value.failedMessageText).isEqualTo("Test message")

        // Retry should succeed
        viewModel.retryLastMessage()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.loadingStage).isEqualTo(LoadingStage.DONE)
        assertThat(viewModel.uiState.value.failedMessageText).isNull()
    }

    @Test
    fun sendMessage_computesSuggestedQuestions() = runTest {
        val store = FakeDraftStore()
        val profileRepository = FakeExportProfileRepository()
        val profilePreferences = ExportProfilePreferences(ApplicationProvider.getApplicationContext())
        val repository = FakeAssistantRepository()

        val viewModel = AssistantViewModel(
            itemIds = listOf("item-1"),
            itemsViewModel = itemsViewModel,
            draftStore = store,
            exportProfileRepository = profileRepository,
            exportProfilePreferences = profilePreferences,
            assistantRepository = repository,
            settingsRepository = settingsRepository
        )

        viewModel.sendMessage("Hi")
        advanceUntilIdle()

        // Suggested questions should be computed
        val suggestions = viewModel.uiState.value.suggestedQuestions
        assertThat(suggestions).isNotEmpty()
        assertThat(suggestions.size).isAtMost(3)
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
            assistantRepository = FakeAssistantRepository(),
            settingsRepository = settingsRepository
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
            correlationId: String,
            imageAttachments: List<ItemImageAttachment>,
            assistantPrefs: AssistantPrefs?
        ): com.scanium.app.model.AssistantResponse {
            return com.scanium.app.model.AssistantResponse("ok")
        }
    }

    private class DelayingAssistantRepository : AssistantRepository {
        override suspend fun send(
            items: List<com.scanium.app.model.ItemContextSnapshot>,
            history: List<com.scanium.app.model.AssistantMessage>,
            userMessage: String,
            exportProfile: ExportProfileDefinition,
            correlationId: String,
            imageAttachments: List<ItemImageAttachment>,
            assistantPrefs: AssistantPrefs?
        ): com.scanium.app.model.AssistantResponse {
            // Simulate a long-running request that never completes
            kotlinx.coroutines.delay(Long.MAX_VALUE)
            return com.scanium.app.model.AssistantResponse("ok")
        }
    }

    private class FailingAssistantRepository : AssistantRepository {
        override suspend fun send(
            items: List<com.scanium.app.model.ItemContextSnapshot>,
            history: List<com.scanium.app.model.AssistantMessage>,
            userMessage: String,
            exportProfile: ExportProfileDefinition,
            correlationId: String,
            imageAttachments: List<ItemImageAttachment>,
            assistantPrefs: AssistantPrefs?
        ): com.scanium.app.model.AssistantResponse {
            throw RuntimeException("Network error")
        }
    }

    private class SuccessAfterFailureRepository : AssistantRepository {
        private var callCount = 0

        override suspend fun send(
            items: List<com.scanium.app.model.ItemContextSnapshot>,
            history: List<com.scanium.app.model.AssistantMessage>,
            userMessage: String,
            exportProfile: ExportProfileDefinition,
            correlationId: String,
            imageAttachments: List<ItemImageAttachment>,
            assistantPrefs: AssistantPrefs?
        ): com.scanium.app.model.AssistantResponse {
            callCount++
            if (callCount == 1) {
                throw RuntimeException("Network error")
            }
            return com.scanium.app.model.AssistantResponse("ok")
        }
    }
}
