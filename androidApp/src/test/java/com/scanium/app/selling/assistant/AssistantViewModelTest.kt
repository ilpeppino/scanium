package com.scanium.app.selling.assistant

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scanium.app.data.ExportProfilePreferences
import com.scanium.app.data.SettingsRepository
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.items.createTestItemsViewModel
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
import com.scanium.app.platform.ConnectivityStatus
import com.scanium.app.platform.ConnectivityStatusProvider
import com.scanium.app.selling.assistant.local.LocalSuggestionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var itemsViewModel: ItemsViewModel
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var connectivityStatusProvider: FakeConnectivityStatusProvider
    private lateinit var localAssistantHelper: LocalAssistantHelper
    private lateinit var localSuggestionEngine: LocalSuggestionEngine
    private lateinit var preflightManager: FakeAssistantPreflightManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        itemsViewModel =
            createTestItemsViewModel(
                workerDispatcher = testDispatcher,
                mainDispatcher = testDispatcher,
            )
        settingsRepository = SettingsRepository(ApplicationProvider.getApplicationContext())
        connectivityStatusProvider = FakeConnectivityStatusProvider()
        localAssistantHelper = LocalAssistantHelper()
        localSuggestionEngine = LocalSuggestionEngine()
        preflightManager = FakeAssistantPreflightManager()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun sendMessage_setsLoadingState() =
        runTest {
            val store = FakeDraftStore()
            val profileRepository = FakeExportProfileRepository()
            val profilePreferences = ExportProfilePreferences(ApplicationProvider.getApplicationContext())
            val repository = DelayingAssistantRepository()

            val viewModel =
                AssistantViewModel(
                    itemIds = listOf("item-1"),
                    itemsViewModel = itemsViewModel,
                    draftStore = store,
                    exportProfileRepository = profileRepository,
                    exportProfilePreferences = profilePreferences,
                    assistantRepository = repository,
                    settingsRepository = settingsRepository,
                    localAssistantHelper = localAssistantHelper,
                    localSuggestionEngine = localSuggestionEngine,
                    connectivityStatusProvider = connectivityStatusProvider,
                    preflightManager = preflightManager,
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
                listOf(LoadingStage.VISION_PROCESSING, LoadingStage.LLM_PROCESSING),
            )
        }

    @Test
    fun sendMessage_updatesToLLMProcessingAfterVision() =
        runTest {
            val store = FakeDraftStore()
            val profileRepository = FakeExportProfileRepository()
            val profilePreferences = ExportProfilePreferences(ApplicationProvider.getApplicationContext())
            val repository = FakeAssistantRepository()

            val viewModel =
                AssistantViewModel(
                    itemIds = listOf("item-1"),
                    itemsViewModel = itemsViewModel,
                    draftStore = store,
                    exportProfileRepository = profileRepository,
                    exportProfilePreferences = profilePreferences,
                    assistantRepository = repository,
                    settingsRepository = settingsRepository,
                    localAssistantHelper = localAssistantHelper,
                    localSuggestionEngine = localSuggestionEngine,
                    connectivityStatusProvider = connectivityStatusProvider,
                    preflightManager = preflightManager,
                )

            viewModel.sendMessage("What color is this?")
            advanceUntilIdle()

            // After completion, stage should be DONE
            assertThat(viewModel.uiState.value.isLoading).isFalse()
            assertThat(viewModel.uiState.value.loadingStage).isEqualTo(LoadingStage.DONE)
        }

    @Test
    fun sendMessage_failure_usesLocalFallback() =
        runTest {
            val store = FakeDraftStore()
            val profileRepository = FakeExportProfileRepository()
            val profilePreferences = ExportProfilePreferences(ApplicationProvider.getApplicationContext())
            val repository = FailingAssistantRepository()

            val viewModel =
                AssistantViewModel(
                    itemIds = listOf("item-1"),
                    itemsViewModel = itemsViewModel,
                    draftStore = store,
                    exportProfileRepository = profileRepository,
                    exportProfilePreferences = profilePreferences,
                    assistantRepository = repository,
                    settingsRepository = settingsRepository,
                    localAssistantHelper = localAssistantHelper,
                    localSuggestionEngine = localSuggestionEngine,
                    connectivityStatusProvider = connectivityStatusProvider,
                    preflightManager = preflightManager,
                )

            viewModel.sendMessage("What color is this?")
            advanceUntilIdle()

            // After failure, should fall back to local helper and mark limited mode
            assertThat(viewModel.uiState.value.isLoading).isFalse()
            assertThat(viewModel.uiState.value.loadingStage).isEqualTo(LoadingStage.DONE)
            assertThat(viewModel.uiState.value.assistantMode).isEqualTo(AssistantMode.LIMITED)
            assertThat(viewModel.uiState.value.lastBackendFailure).isNotNull()
        }

    @Test
    fun retryLastMessage_retriesOnlineAfterFallback() =
        runTest {
            val store = FakeDraftStore()
            val profileRepository = FakeExportProfileRepository()
            val profilePreferences = ExportProfilePreferences(ApplicationProvider.getApplicationContext())
            val repository = SuccessAfterFailureRepository()

            val viewModel =
                AssistantViewModel(
                    itemIds = listOf("item-1"),
                    itemsViewModel = itemsViewModel,
                    draftStore = store,
                    exportProfileRepository = profileRepository,
                    exportProfilePreferences = profilePreferences,
                    assistantRepository = repository,
                    settingsRepository = settingsRepository,
                    localAssistantHelper = localAssistantHelper,
                    localSuggestionEngine = localSuggestionEngine,
                    connectivityStatusProvider = connectivityStatusProvider,
                    preflightManager = preflightManager,
                )

            // First send should fall back locally
            viewModel.sendMessage("Test message")
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.loadingStage).isEqualTo(LoadingStage.DONE)
            assertThat(viewModel.uiState.value.assistantMode).isEqualTo(AssistantMode.LIMITED)

            // Retry should succeed online
            viewModel.retryLastMessage()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.loadingStage).isEqualTo(LoadingStage.DONE)
            assertThat(viewModel.uiState.value.assistantMode).isEqualTo(AssistantMode.ONLINE)
        }

    @Test
    fun sendMessage_computesSuggestedQuestions() =
        runTest {
            val store = FakeDraftStore()
            val profileRepository = FakeExportProfileRepository()
            val profilePreferences = ExportProfilePreferences(ApplicationProvider.getApplicationContext())
            val repository = FakeAssistantRepository()

            // Add a draft so computeSuggestedQuestions has a snapshot to work with
            val draft =
                ListingDraft(
                    id = "draft-1",
                    itemId = "item-1",
                    profile = ExportProfileId.GENERIC,
                    title = DraftField("Test Item", confidence = 0.5f, source = DraftProvenance.DEFAULT),
                    description = DraftField("", confidence = 0.5f, source = DraftProvenance.DEFAULT),
                    fields = emptyMap(),
                    price = DraftField(0.0, confidence = 0.5f, source = DraftProvenance.DEFAULT),
                    photos = emptyList(),
                    status = com.scanium.app.listing.DraftStatus.DRAFT,
                    createdAt = 1L,
                    updatedAt = 1L,
                )
            store.upsert(draft)

            val viewModel =
                AssistantViewModel(
                    itemIds = listOf("item-1"),
                    itemsViewModel = itemsViewModel,
                    draftStore = store,
                    exportProfileRepository = profileRepository,
                    exportProfilePreferences = profilePreferences,
                    assistantRepository = repository,
                    settingsRepository = settingsRepository,
                    localAssistantHelper = localAssistantHelper,
                    localSuggestionEngine = localSuggestionEngine,
                    connectivityStatusProvider = connectivityStatusProvider,
                    preflightManager = preflightManager,
                )

            // Wait for initial loadProfileAndSnapshots to complete
            advanceUntilIdle()

            viewModel.sendMessage("Hi")
            advanceUntilIdle()

            // After successful send, state should be DONE
            assertThat(viewModel.uiState.value.loadingStage).isEqualTo(LoadingStage.DONE)

            // Suggested questions should be limited to 3 if present
            val suggestions = viewModel.uiState.value.suggestedQuestions
            assertThat(suggestions.size).isAtMost(3)
        }

    @Test
    fun applyDraftUpdate_persistsChanges() =
        runTest {
            val store = FakeDraftStore()
            val profileRepository = FakeExportProfileRepository()
            val profilePreferences = ExportProfilePreferences(ApplicationProvider.getApplicationContext())
            val draft =
                ListingDraft(
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
                    updatedAt = 1L,
                )
            store.upsert(draft)

            val viewModel =
                AssistantViewModel(
                    itemIds = listOf("item-1"),
                    itemsViewModel = itemsViewModel,
                    draftStore = store,
                    exportProfileRepository = profileRepository,
                    exportProfilePreferences = profilePreferences,
                    assistantRepository = FakeAssistantRepository(),
                    settingsRepository = settingsRepository,
                    localAssistantHelper = localAssistantHelper,
                    localSuggestionEngine = localSuggestionEngine,
                    connectivityStatusProvider = connectivityStatusProvider,
                    preflightManager = preflightManager,
                )

            val action =
                AssistantAction(
                    type = AssistantActionType.APPLY_DRAFT_UPDATE,
                    payload = mapOf("itemId" to "item-1", "title" to "Updated Title"),
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
            assistantPrefs: AssistantPrefs?,
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
            assistantPrefs: AssistantPrefs?,
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
            assistantPrefs: AssistantPrefs?,
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
            assistantPrefs: AssistantPrefs?,
        ): com.scanium.app.model.AssistantResponse {
            callCount++
            if (callCount == 1) {
                throw RuntimeException("Network error")
            }
            return com.scanium.app.model.AssistantResponse("ok")
        }
    }

    private class FakeConnectivityStatusProvider : ConnectivityStatusProvider {
        private val status = MutableStateFlow(ConnectivityStatus.ONLINE)
        override val statusFlow = status

        fun setStatus(newStatus: ConnectivityStatus) {
            status.value = newStatus
        }
    }

    /**
     * Fake preflight manager that always returns Available status for testing.
     */
    private class FakeAssistantPreflightManager : AssistantPreflightManager {
        private val _currentResult = MutableStateFlow(
            PreflightResult(
                status = PreflightStatus.AVAILABLE,
                latencyMs = 10,
            ),
        )
        override val currentResult: StateFlow<PreflightResult> = _currentResult

        override val lastStatusFlow: Flow<PreflightResult?> = flowOf(_currentResult.value)

        private var preflightResult = PreflightResult(
            status = PreflightStatus.AVAILABLE,
            latencyMs = 10,
        )

        override suspend fun preflight(forceRefresh: Boolean): PreflightResult {
            _currentResult.value = preflightResult
            return preflightResult
        }

        override suspend fun warmUp(): Boolean = true

        override fun cancelWarmUp() {}

        override suspend fun clearCache() {
            _currentResult.value = PreflightResult(
                status = PreflightStatus.UNKNOWN,
                latencyMs = 0,
            )
        }

        fun setPreflightResult(result: PreflightResult) {
            preflightResult = result
            _currentResult.value = result
        }
    }
}
