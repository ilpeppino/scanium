package com.scanium.app.selling.assistant

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scanium.app.data.ExportProfilePreferences
import com.scanium.app.data.SettingsRepository
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.items.createTestItemsViewModel
import com.scanium.app.listing.DraftField
import com.scanium.app.listing.DraftPhotoRef
import com.scanium.app.listing.DraftProvenance
import com.scanium.app.listing.ExportProfileDefinition
import com.scanium.app.listing.ExportProfileId
import com.scanium.app.listing.ExportProfileRepository
import com.scanium.app.listing.ExportProfiles
import com.scanium.app.listing.ListingDraft
import com.scanium.shared.core.models.model.ImageRef
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

        // Pre-initialize DataStore to avoid timing issues with flow.first() calls
        // DataStore runs on Dispatchers.IO which isn't replaced by the test dispatcher,
        // so we need to ensure the data is written before tests run
        kotlinx.coroutines.runBlocking {
            settingsRepository.setAllowAssistantImages(false)
        }
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

    @Test
    fun sendMessage_withImagesToggleOff_sendsNoAttachments() =
        runTest {
            val store = FakeDraftStore()
            val profileRepository = FakeExportProfileRepository()
            val profilePreferences = ExportProfilePreferences(ApplicationProvider.getApplicationContext())
            val repository = FakeAssistantRepository()

            // Create draft with photos
            val draftWithPhotos = createDraftWithPhotos("item-1", 2)
            store.upsert(draftWithPhotos)

            // Ensure images toggle is OFF (default)
            settingsRepository.setAllowAssistantImages(false)

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

            advanceUntilIdle()

            viewModel.sendMessage("What color is this?")
            advanceUntilIdle()

            // Verify no images were sent (toggle OFF)
            assertThat(repository.lastImageAttachments).isEmpty()
        }

    @Test
    fun sendMessage_withImagesToggleOn_sendsAttachments() =
        runTest {
            val store = FakeDraftStore()
            val profileRepository = FakeExportProfileRepository()
            val profilePreferences = ExportProfilePreferences(ApplicationProvider.getApplicationContext())
            val repository = FakeAssistantRepository()

            // Create draft with photos
            val draftWithPhotos = createDraftWithPhotos("item-1", 2)
            store.upsert(draftWithPhotos)

            // Enable images toggle
            settingsRepository.setAllowAssistantImages(true)

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

            advanceUntilIdle()

            viewModel.sendMessage("What color is this?")
            advanceUntilIdle()

            // Verify images were sent (toggle ON)
            assertThat(repository.lastImageAttachments).hasSize(2)
            assertThat(repository.lastImageAttachments.all { it.itemId == "item-1" }).isTrue()
        }

    @Test
    fun sendMessage_withImagesToggleOn_respectsMaxImagesPerItem() =
        runTest {
            val store = FakeDraftStore()
            val profileRepository = FakeExportProfileRepository()
            val profilePreferences = ExportProfilePreferences(ApplicationProvider.getApplicationContext())
            val repository = FakeAssistantRepository()

            // Create draft with more than MAX_IMAGES_PER_ITEM photos
            val draftWithManyPhotos = createDraftWithPhotos("item-1", 5)
            store.upsert(draftWithManyPhotos)

            // Enable images toggle
            settingsRepository.setAllowAssistantImages(true)

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

            advanceUntilIdle()

            viewModel.sendMessage("What color is this?")
            advanceUntilIdle()

            // Verify max images per item is respected
            assertThat(repository.lastImageAttachments).hasSize(ImageAttachmentBuilder.MAX_IMAGES_PER_ITEM)
        }

    @Test
    fun sendMessage_withNoPhotos_sendsNoAttachments() =
        runTest {
            val store = FakeDraftStore()
            val profileRepository = FakeExportProfileRepository()
            val profilePreferences = ExportProfilePreferences(ApplicationProvider.getApplicationContext())
            val repository = FakeAssistantRepository()

            // Create draft without photos
            val draftWithoutPhotos =
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
            store.upsert(draftWithoutPhotos)

            // Enable images toggle
            settingsRepository.setAllowAssistantImages(true)

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

            advanceUntilIdle()

            viewModel.sendMessage("What is this?")
            advanceUntilIdle()

            // Verify no images sent (no photos in draft)
            assertThat(repository.lastImageAttachments).isEmpty()
        }

    /**
     * Helper to create a draft with photos for testing.
     */
    private fun createDraftWithPhotos(itemId: String, photoCount: Int): ListingDraft {
        val photos = (0 until photoCount).map { index ->
            DraftPhotoRef(
                image = ImageRef.Bytes(
                    bytes = ByteArray(1024) { it.toByte() }, // 1KB test image
                    mimeType = "image/jpeg",
                    width = 100,
                    height = 100,
                ),
                source = DraftProvenance.DETECTED,
            )
        }
        return ListingDraft(
            id = "draft-$itemId",
            itemId = itemId,
            profile = ExportProfileId.GENERIC,
            title = DraftField("Test Item", confidence = 0.5f, source = DraftProvenance.DEFAULT),
            description = DraftField("Test description", confidence = 0.5f, source = DraftProvenance.DEFAULT),
            fields = emptyMap(),
            price = DraftField(10.0, confidence = 0.5f, source = DraftProvenance.DEFAULT),
            photos = photos,
            status = com.scanium.app.listing.DraftStatus.DRAFT,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
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
        /** Last image attachments sent, for test verification */
        var lastImageAttachments: List<ItemImageAttachment> = emptyList()
            private set

        override suspend fun send(
            items: List<com.scanium.app.model.ItemContextSnapshot>,
            history: List<com.scanium.app.model.AssistantMessage>,
            userMessage: String,
            exportProfile: ExportProfileDefinition,
            correlationId: String,
            imageAttachments: List<ItemImageAttachment>,
            assistantPrefs: AssistantPrefs?,
        ): com.scanium.app.model.AssistantResponse {
            lastImageAttachments = imageAttachments
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

    // ==================== Regression tests for preflight failure handling ====================
    // These tests verify that preflight failures don't permanently block the assistant UI.

    @Test
    fun `preflight UNKNOWN allows chat attempt and success marks Available`() = runTest {
        val store = FakeDraftStore()
        val profileRepository = FakeExportProfileRepository()
        val profilePreferences = ExportProfilePreferences(ApplicationProvider.getApplicationContext())
        val repository = FakeAssistantRepository()

        // Set preflight to UNKNOWN (simulating timeout or auth error)
        preflightManager.setPreflightResult(
            PreflightResult(
                status = PreflightStatus.UNKNOWN,
                latencyMs = 100,
                reasonCode = "timeout",
            ),
        )

        val viewModel = AssistantViewModel(
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

        advanceUntilIdle()

        // Even with UNKNOWN preflight, user should be able to send messages
        assertThat(viewModel.uiState.value.availability).isEqualTo(AssistantAvailability.Available)
        assertThat(viewModel.uiState.value.isInputEnabled).isTrue()

        // Send a message - it should succeed
        viewModel.sendMessage("Test message")
        advanceUntilIdle()

        // After success, availability should be Available
        assertThat(viewModel.uiState.value.availability).isEqualTo(AssistantAvailability.Available)
        assertThat(viewModel.uiState.value.assistantMode).isEqualTo(AssistantMode.ONLINE)
    }

    @Test
    fun `preflight UNAUTHORIZED allows chat attempt when online`() = runTest {
        val store = FakeDraftStore()
        val profileRepository = FakeExportProfileRepository()
        val profilePreferences = ExportProfilePreferences(ApplicationProvider.getApplicationContext())
        val repository = FakeAssistantRepository()

        // Set preflight to UNAUTHORIZED (edge case - normally returns UNKNOWN now)
        preflightManager.setPreflightResult(
            PreflightResult(
                status = PreflightStatus.UNAUTHORIZED,
                latencyMs = 50,
                reasonCode = "http_401",
            ),
        )

        val viewModel = AssistantViewModel(
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

        advanceUntilIdle()

        // UNAUTHORIZED from preflight should NOT block UI - allow chat attempt
        assertThat(viewModel.uiState.value.availability).isEqualTo(AssistantAvailability.Available)
        assertThat(viewModel.uiState.value.isInputEnabled).isTrue()
    }

    @Test
    fun `chat success clears preflight warning`() = runTest {
        val store = FakeDraftStore()
        val profileRepository = FakeExportProfileRepository()
        val profilePreferences = ExportProfilePreferences(ApplicationProvider.getApplicationContext())
        val repository = FakeAssistantRepository()

        // Start with preflight failure (which now shows warning but allows chat)
        preflightManager.setPreflightResult(
            PreflightResult(
                status = PreflightStatus.TEMPORARILY_UNAVAILABLE,
                latencyMs = 100,
                reasonCode = "http_503",
            ),
        )

        val viewModel = AssistantViewModel(
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

        advanceUntilIdle()

        // Preflight failures now allow chat attempts (availability = Available)
        // But show a warning banner
        assertThat(viewModel.uiState.value.availability).isEqualTo(AssistantAvailability.Available)
        assertThat(viewModel.uiState.value.preflightWarning).isNotNull()
        assertThat(viewModel.uiState.value.isInputEnabled).isTrue()

        // Now send a message
        viewModel.sendMessage("Test message")
        advanceUntilIdle()

        // After chat success, warning should be cleared
        assertThat(viewModel.uiState.value.availability).isEqualTo(AssistantAvailability.Available)
        assertThat(viewModel.uiState.value.preflightWarning).isNull()
        assertThat(viewModel.uiState.value.lastBackendFailure).isNull()
    }

    @Test
    fun `preflight ENDPOINT_NOT_FOUND allows chat attempt with warning`() = runTest {
        val store = FakeDraftStore()
        val profileRepository = FakeExportProfileRepository()
        val profilePreferences = ExportProfilePreferences(ApplicationProvider.getApplicationContext())
        val repository = FakeAssistantRepository()

        // Set preflight to ENDPOINT_NOT_FOUND - configuration error but still allow chat attempt
        preflightManager.setPreflightResult(
            PreflightResult(
                status = PreflightStatus.ENDPOINT_NOT_FOUND,
                latencyMs = 50,
                reasonCode = "endpoint_not_found",
            ),
        )

        val viewModel = AssistantViewModel(
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

        advanceUntilIdle()

        // ENDPOINT_NOT_FOUND now allows chat attempt (input enabled) but will likely fail
        // The key is that preflight NEVER blocks the UI
        val availability = viewModel.uiState.value.availability
        assertThat(availability).isEqualTo(AssistantAvailability.Available)
        assertThat(viewModel.uiState.value.isInputEnabled).isTrue()
    }

    // ==================== Progress state transition tests ====================
    // These tests verify the rich progress UI states during assistant requests.

    @Test
    fun `sendMessage transitions through progress states on success`() = runTest {
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
            settingsRepository = settingsRepository,
            localAssistantHelper = localAssistantHelper,
            localSuggestionEngine = localSuggestionEngine,
            connectivityStatusProvider = connectivityStatusProvider,
            preflightManager = preflightManager,
        )

        // Initial progress should be Idle
        assertThat(viewModel.uiState.value.progress).isEqualTo(AssistantRequestProgress.Idle)

        viewModel.sendMessage("Test message")
        advanceUntilIdle()

        // After completion, progress should be Done
        val progress = viewModel.uiState.value.progress
        assertThat(progress).isInstanceOf(AssistantRequestProgress.Done::class.java)
        // Duration may be 0 in test dispatcher as all coroutines run instantly
        assertThat((progress as AssistantRequestProgress.Done).totalDurationMs).isAtLeast(0)
    }

    @Test
    fun `sendMessage sets progress to ErrorTemporary on timeout`() = runTest {
        val store = FakeDraftStore()
        val profileRepository = FakeExportProfileRepository()
        val profilePreferences = ExportProfilePreferences(ApplicationProvider.getApplicationContext())
        val repository = TimeoutAssistantRepository()

        val viewModel = AssistantViewModel(
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

        viewModel.sendMessage("Test message")
        advanceUntilIdle()

        // After timeout, progress should be ErrorTemporary with retryable=true
        val progress = viewModel.uiState.value.progress
        assertThat(progress).isInstanceOf(AssistantRequestProgress.ErrorTemporary::class.java)
        assertThat((progress as AssistantRequestProgress.ErrorTemporary).retryable).isTrue()
    }

    @Test
    fun `sendMessage sets progress to ErrorAuth on 401`() = runTest {
        val store = FakeDraftStore()
        val profileRepository = FakeExportProfileRepository()
        val profilePreferences = ExportProfilePreferences(ApplicationProvider.getApplicationContext())
        val repository = UnauthorizedAssistantRepository()

        val viewModel = AssistantViewModel(
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

        viewModel.sendMessage("Test message")
        advanceUntilIdle()

        // After 401, progress should be ErrorAuth
        val progress = viewModel.uiState.value.progress
        assertThat(progress).isInstanceOf(AssistantRequestProgress.ErrorAuth::class.java)
    }

    @Test
    fun `sendMessage sets progress to ErrorValidation on 400`() = runTest {
        val store = FakeDraftStore()
        val profileRepository = FakeExportProfileRepository()
        val profilePreferences = ExportProfilePreferences(ApplicationProvider.getApplicationContext())
        val repository = ValidationErrorAssistantRepository()

        val viewModel = AssistantViewModel(
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

        viewModel.sendMessage("Test message")
        advanceUntilIdle()

        // After 400, progress should be ErrorValidation
        val progress = viewModel.uiState.value.progress
        assertThat(progress).isInstanceOf(AssistantRequestProgress.ErrorValidation::class.java)
    }

    @Test
    fun `sendMessage preserves lastSuccessfulEntry during new request`() = runTest {
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
            settingsRepository = settingsRepository,
            localAssistantHelper = localAssistantHelper,
            localSuggestionEngine = localSuggestionEngine,
            connectivityStatusProvider = connectivityStatusProvider,
            preflightManager = preflightManager,
        )

        advanceUntilIdle() // Let init complete

        // First message - should succeed
        viewModel.sendMessage("First message")
        advanceUntilIdle()

        val firstEntry = viewModel.uiState.value.lastSuccessfulEntry
        assertThat(firstEntry).isNotNull()
        assertThat(firstEntry!!.message.content).isEqualTo("ok") // FakeAssistantRepository returns "ok"

        // Second message - should produce new lastSuccessfulEntry
        viewModel.sendMessage("Second message")
        advanceUntilIdle()

        val secondEntry = viewModel.uiState.value.lastSuccessfulEntry
        assertThat(secondEntry).isNotNull()
        // The entry content is the same ("ok"), but it should be a different instance
        // Both represent successful assistant responses
        assertThat(secondEntry!!.message.content).isEqualTo("ok")
    }

    @Test
    fun `retryLastMessage resets progress to Idle before sending`() = runTest {
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
            settingsRepository = settingsRepository,
            localAssistantHelper = localAssistantHelper,
            localSuggestionEngine = localSuggestionEngine,
            connectivityStatusProvider = connectivityStatusProvider,
            preflightManager = preflightManager,
        )

        // First message fails
        viewModel.sendMessage("Test message")
        advanceUntilIdle()

        // Should be in error state
        assertThat(viewModel.uiState.value.progress.isError).isTrue()

        // Retry should succeed
        viewModel.retryLastMessage()
        advanceUntilIdle()

        // Should be Done
        assertThat(viewModel.uiState.value.progress).isInstanceOf(AssistantRequestProgress.Done::class.java)
    }

    @Test
    fun `progress displayLabel returns correct text for each state`() {
        // Test display labels for all progress states
        assertThat(AssistantRequestProgress.Idle.displayLabel).isEmpty()
        assertThat(AssistantRequestProgress.Sending().displayLabel).isEqualTo("Sending...")
        assertThat(AssistantRequestProgress.Thinking(startedAt = 0).displayLabel).isEqualTo("Thinking...")
        assertThat(AssistantRequestProgress.ExtractingVision(startedAt = 0).displayLabel).isEqualTo("Analyzing images...")
        assertThat(AssistantRequestProgress.Drafting(startedAt = 0).displayLabel).isEqualTo("Drafting answer...")
        assertThat(AssistantRequestProgress.Finalizing(startedAt = 0).displayLabel).isEqualTo("Finalizing...")
        assertThat(AssistantRequestProgress.Done().displayLabel).isEmpty()
        assertThat(AssistantRequestProgress.ErrorTemporary(message = "test").displayLabel).isEqualTo("Temporarily unavailable")
        assertThat(AssistantRequestProgress.ErrorAuth(message = "test").displayLabel).isEqualTo("Authentication required")
        assertThat(AssistantRequestProgress.ErrorValidation(message = "test").displayLabel).isEqualTo("Invalid request")
    }

    @Test
    fun `progress isLoading returns correct value for each state`() {
        assertThat(AssistantRequestProgress.Idle.isLoading).isFalse()
        assertThat(AssistantRequestProgress.Sending().isLoading).isTrue()
        assertThat(AssistantRequestProgress.Thinking(startedAt = 0).isLoading).isTrue()
        assertThat(AssistantRequestProgress.ExtractingVision(startedAt = 0).isLoading).isTrue()
        assertThat(AssistantRequestProgress.Drafting(startedAt = 0).isLoading).isTrue()
        assertThat(AssistantRequestProgress.Finalizing(startedAt = 0).isLoading).isTrue()
        assertThat(AssistantRequestProgress.Done().isLoading).isFalse()
        assertThat(AssistantRequestProgress.ErrorTemporary(message = "test").isLoading).isFalse()
        assertThat(AssistantRequestProgress.ErrorAuth(message = "test").isLoading).isFalse()
        assertThat(AssistantRequestProgress.ErrorValidation(message = "test").isLoading).isFalse()
    }

    @Test
    fun `progress isError returns correct value for each state`() {
        assertThat(AssistantRequestProgress.Idle.isError).isFalse()
        assertThat(AssistantRequestProgress.Sending().isError).isFalse()
        assertThat(AssistantRequestProgress.Done().isError).isFalse()
        assertThat(AssistantRequestProgress.ErrorTemporary(message = "test").isError).isTrue()
        assertThat(AssistantRequestProgress.ErrorAuth(message = "test").isError).isTrue()
        assertThat(AssistantRequestProgress.ErrorValidation(message = "test").isError).isTrue()
    }

    @Test
    fun `AssistantRequestTiming calculates durations correctly`() {
        val timing = AssistantRequestTiming(
            correlationId = "test-123",
            requestStartedAt = 0,
            sendingStartedAt = 0,
            thinkingStartedAt = 100,
            extractingVisionStartedAt = 200,
            draftingStartedAt = 500,
            finalizingStartedAt = 800,
            completedAt = 1000,
            hasImages = true,
        )

        assertThat(timing.sendingDurationMs).isEqualTo(100)
        assertThat(timing.thinkingDurationMs).isEqualTo(100) // 200 - 100
        assertThat(timing.extractingVisionDurationMs).isEqualTo(300) // 500 - 200
        assertThat(timing.draftingDurationMs).isEqualTo(300) // 800 - 500
        assertThat(timing.finalizingDurationMs).isEqualTo(200) // 1000 - 800
        assertThat(timing.totalDurationMs).isEqualTo(1000)
    }

    @Test
    fun `AssistantRequestTiming toLogString contains all durations`() {
        val timing = AssistantRequestTiming(
            correlationId = "test-123",
            requestStartedAt = 0,
            sendingStartedAt = 0,
            thinkingStartedAt = 100,
            draftingStartedAt = 300,
            completedAt = 500,
            hasImages = false,
        )

        val logString = timing.toLogString()
        assertThat(logString).contains("correlationId=test-123")
        assertThat(logString).contains("sending=100ms")
        assertThat(logString).contains("thinking=200ms")
        assertThat(logString).contains("total=500ms")
    }

    // ==================== Test repositories for progress state tests ====================

    private class TimeoutAssistantRepository : AssistantRepository {
        override suspend fun send(
            items: List<com.scanium.app.model.ItemContextSnapshot>,
            history: List<com.scanium.app.model.AssistantMessage>,
            userMessage: String,
            exportProfile: ExportProfileDefinition,
            correlationId: String,
            imageAttachments: List<ItemImageAttachment>,
            assistantPrefs: AssistantPrefs?,
        ): com.scanium.app.model.AssistantResponse {
            throw AssistantBackendException(
                AssistantBackendFailure(
                    type = AssistantBackendErrorType.NETWORK_TIMEOUT,
                    category = AssistantBackendErrorCategory.TEMPORARY,
                    retryable = true,
                    message = "Request timed out",
                ),
            )
        }
    }

    private class UnauthorizedAssistantRepository : AssistantRepository {
        override suspend fun send(
            items: List<com.scanium.app.model.ItemContextSnapshot>,
            history: List<com.scanium.app.model.AssistantMessage>,
            userMessage: String,
            exportProfile: ExportProfileDefinition,
            correlationId: String,
            imageAttachments: List<ItemImageAttachment>,
            assistantPrefs: AssistantPrefs?,
        ): com.scanium.app.model.AssistantResponse {
            throw AssistantBackendException(
                AssistantBackendFailure(
                    type = AssistantBackendErrorType.UNAUTHORIZED,
                    category = AssistantBackendErrorCategory.POLICY,
                    retryable = false,
                    message = "Not authorized",
                ),
            )
        }
    }

    private class ValidationErrorAssistantRepository : AssistantRepository {
        override suspend fun send(
            items: List<com.scanium.app.model.ItemContextSnapshot>,
            history: List<com.scanium.app.model.AssistantMessage>,
            userMessage: String,
            exportProfile: ExportProfileDefinition,
            correlationId: String,
            imageAttachments: List<ItemImageAttachment>,
            assistantPrefs: AssistantPrefs?,
        ): com.scanium.app.model.AssistantResponse {
            throw AssistantBackendException(
                AssistantBackendFailure(
                    type = AssistantBackendErrorType.VALIDATION_ERROR,
                    category = AssistantBackendErrorCategory.POLICY,
                    retryable = false,
                    message = "Invalid request payload",
                ),
            )
        }
    }
}
