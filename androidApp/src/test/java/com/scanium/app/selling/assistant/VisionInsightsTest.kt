package com.scanium.app.selling.assistant

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scanium.app.data.ExportProfilePreferences
import com.scanium.app.data.SettingsRepository
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.items.createTestItemsViewModel
import com.scanium.app.listing.ExportProfileDefinition
import com.scanium.app.listing.ExportProfileId
import com.scanium.app.listing.ExportProfileRepository
import com.scanium.app.listing.ExportProfiles
import com.scanium.app.listing.ListingDraft
import com.scanium.app.model.AssistantPrefs
import com.scanium.app.model.AssistantResponse
import com.scanium.app.model.ConfidenceTier
import com.scanium.app.model.SuggestedAttribute
import com.scanium.app.platform.ConnectivityStatus
import com.scanium.app.platform.ConnectivityStatusProvider
import com.scanium.app.selling.assistant.local.LocalSuggestionEngine
import com.scanium.app.selling.persistence.ListingDraftStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
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
 * Tests for Vision Insights feature.
 *
 * Tests cover:
 * - suggestedAttributes threading through AssistantChatEntry
 * - Confidence tier to float mapping
 * - Alternative key mapping for conflicts
 * - Empty suggestedAttributes handling
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class VisionInsightsTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var itemsViewModel: ItemsViewModel
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var connectivityStatusProvider: FakeConnectivityProvider
    private lateinit var localAssistantHelper: LocalAssistantHelper
    private lateinit var localSuggestionEngine: LocalSuggestionEngine
    private lateinit var preflightManager: FakePreflightManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        itemsViewModel =
            createTestItemsViewModel(
                workerDispatcher = testDispatcher,
                mainDispatcher = testDispatcher,
            )
        settingsRepository = SettingsRepository(ApplicationProvider.getApplicationContext())
        connectivityStatusProvider = FakeConnectivityProvider()
        localAssistantHelper = LocalAssistantHelper()
        localSuggestionEngine = LocalSuggestionEngine()
        preflightManager = FakePreflightManager()

        // Pre-initialize DataStore
        kotlinx.coroutines.runBlocking {
            settingsRepository.setAllowAssistantImages(false)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `suggestedAttributes are included in AssistantChatEntry after response`() =
        runTest {
            val suggestedAttrs =
                listOf(
                    SuggestedAttribute(
                        key = "brand",
                        value = "Nike",
                        confidence = ConfidenceTier.HIGH,
                        source = "logo",
                    ),
                    SuggestedAttribute(
                        key = "color",
                        value = "blue",
                        confidence = ConfidenceTier.MED,
                        source = "color",
                    ),
                )

            val repository =
                ConfigurableAssistantRepository(
                    response =
                        AssistantResponse(
                            reply = "Found brand and color info",
                            suggestedAttributes = suggestedAttrs,
                        ),
                )

            val viewModel = createViewModel(repository)
            viewModel.sendMessage("What brand is this?")
            advanceUntilIdle()

            val lastEntry = viewModel.uiState.value.entries.last()
            assertThat(lastEntry.suggestedAttributes).hasSize(2)
            assertThat(lastEntry.suggestedAttributes[0].key).isEqualTo("brand")
            assertThat(lastEntry.suggestedAttributes[0].value).isEqualTo("Nike")
            assertThat(lastEntry.suggestedAttributes[1].key).isEqualTo("color")
            assertThat(lastEntry.suggestedAttributes[1].value).isEqualTo("blue")
        }

    @Test
    fun `empty suggestedAttributes results in empty list not crash`() =
        runTest {
            val repository =
                ConfigurableAssistantRepository(
                    response =
                        AssistantResponse(
                            reply = "No vision data available",
                            suggestedAttributes = emptyList(),
                        ),
                )

            val viewModel = createViewModel(repository)
            viewModel.sendMessage("Hello")
            advanceUntilIdle()

            val lastEntry = viewModel.uiState.value.entries.last()
            assertThat(lastEntry.suggestedAttributes).isEmpty()
        }

    @Test
    fun `getAlternativeKey maps color to secondaryColor`() =
        runTest {
            val viewModel = createViewModel()

            assertThat(viewModel.getAlternativeKey("color")).isEqualTo("secondaryColor")
        }

    @Test
    fun `getAlternativeKey maps brand to brand2`() =
        runTest {
            val viewModel = createViewModel()

            assertThat(viewModel.getAlternativeKey("brand")).isEqualTo("brand2")
        }

    @Test
    fun `getAlternativeKey maps model to model2`() =
        runTest {
            val viewModel = createViewModel()

            assertThat(viewModel.getAlternativeKey("model")).isEqualTo("model2")
        }

    @Test
    fun `getAlternativeKey appends 2 for unknown keys`() =
        runTest {
            val viewModel = createViewModel()

            assertThat(viewModel.getAlternativeKey("size")).isEqualTo("size2")
            assertThat(viewModel.getAlternativeKey("material")).isEqualTo("material2")
        }

    @Test
    fun `AssistantChatEntry defaults to empty suggestedAttributes`() {
        val entry =
            AssistantChatEntry(
                message =
                    com.scanium.app.model.AssistantMessage(
                        role = com.scanium.app.model.AssistantRole.ASSISTANT,
                        content = "Test",
                        timestamp = System.currentTimeMillis(),
                    ),
            )

        assertThat(entry.suggestedAttributes).isEmpty()
    }

    @Test
    fun `response with all confidence tiers is handled correctly`() =
        runTest {
            val suggestedAttrs =
                listOf(
                    SuggestedAttribute("attr1", "val1", ConfidenceTier.HIGH, "test"),
                    SuggestedAttribute("attr2", "val2", ConfidenceTier.MED, "test"),
                    SuggestedAttribute("attr3", "val3", ConfidenceTier.LOW, "test"),
                )

            val repository =
                ConfigurableAssistantRepository(
                    response =
                        AssistantResponse(
                            reply = "Found attributes",
                            suggestedAttributes = suggestedAttrs,
                        ),
                )

            val viewModel = createViewModel(repository)
            viewModel.sendMessage("Analyze this")
            advanceUntilIdle()

            val lastEntry = viewModel.uiState.value.entries.last()
            assertThat(lastEntry.suggestedAttributes).hasSize(3)
            assertThat(lastEntry.suggestedAttributes[0].confidence).isEqualTo(ConfidenceTier.HIGH)
            assertThat(lastEntry.suggestedAttributes[1].confidence).isEqualTo(ConfidenceTier.MED)
            assertThat(lastEntry.suggestedAttributes[2].confidence).isEqualTo(ConfidenceTier.LOW)
        }

    @Test
    fun `response with various sources is handled correctly`() =
        runTest {
            val suggestedAttrs =
                listOf(
                    SuggestedAttribute("brand", "Nike", ConfidenceTier.HIGH, "logo"),
                    SuggestedAttribute("color", "red", ConfidenceTier.HIGH, "color"),
                    SuggestedAttribute("model", "Air Max", ConfidenceTier.MED, "ocr"),
                    SuggestedAttribute("category", "shoes", ConfidenceTier.HIGH, "label"),
                )

            val repository =
                ConfigurableAssistantRepository(
                    response =
                        AssistantResponse(
                            reply = "Found multiple attributes",
                            suggestedAttributes = suggestedAttrs,
                        ),
                )

            val viewModel = createViewModel(repository)
            viewModel.sendMessage("What is this?")
            advanceUntilIdle()

            val lastEntry = viewModel.uiState.value.entries.last()
            assertThat(lastEntry.suggestedAttributes.map { it.source }).containsExactly(
                "logo",
                "color",
                "ocr",
                "label",
            )
        }

    private fun createViewModel(repository: AssistantRepository = ConfigurableAssistantRepository()): AssistantViewModel {
        return AssistantViewModel(
            itemIds = listOf("item-1"),
            itemsViewModel = itemsViewModel,
            context = ApplicationProvider.getApplicationContext(),
            draftStore = FakeDraftStore(),
            exportProfileRepository = FakeExportProfileRepo(),
            exportProfilePreferences = ExportProfilePreferences(ApplicationProvider.getApplicationContext()),
            assistantRepository = repository,
            settingsRepository = settingsRepository,
            localAssistantHelper = localAssistantHelper,
            localSuggestionEngine = localSuggestionEngine,
            connectivityStatusProvider = connectivityStatusProvider,
            preflightManager = preflightManager,
        )
    }

    // Test doubles

    private class ConfigurableAssistantRepository(
        private val response: AssistantResponse = AssistantResponse(reply = "ok"),
    ) : AssistantRepository {
        override suspend fun send(
            items: List<com.scanium.app.model.ItemContextSnapshot>,
            history: List<com.scanium.app.model.AssistantMessage>,
            userMessage: String,
            exportProfile: ExportProfileDefinition,
            correlationId: String,
            imageAttachments: List<ItemImageAttachment>,
            assistantPrefs: AssistantPrefs?,
            includePricing: Boolean,
            pricingCountryCode: String?,
        ): AssistantResponse = response
    }

    private class FakeDraftStore : ListingDraftStore {
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

    private class FakeExportProfileRepo : ExportProfileRepository {
        override suspend fun getProfiles(): List<ExportProfileDefinition> = listOf(ExportProfiles.generic())

        override suspend fun getProfile(id: ExportProfileId): ExportProfileDefinition? {
            return ExportProfiles.generic().takeIf { it.id == id }
        }

        override suspend fun getDefaultProfileId(): ExportProfileId = ExportProfiles.generic().id
    }

    private class FakeConnectivityProvider : ConnectivityStatusProvider {
        private val status = MutableStateFlow(ConnectivityStatus.ONLINE)
        override val statusFlow: StateFlow<ConnectivityStatus> = status
    }

    private class FakePreflightManager : AssistantPreflightManager {
        private val _currentResult =
            MutableStateFlow(
                PreflightResult(
                    status = PreflightStatus.AVAILABLE,
                    latencyMs = 10,
                ),
            )
        override val currentResult: StateFlow<PreflightResult> = _currentResult
        override val lastStatusFlow: Flow<PreflightResult?> = flowOf(_currentResult.value)

        override suspend fun preflight(forceRefresh: Boolean): PreflightResult {
            return PreflightResult(PreflightStatus.AVAILABLE, latencyMs = 10)
        }

        override suspend fun warmUp(): Boolean = true

        override fun cancelWarmUp() {}

        override suspend fun clearCache() {}
    }
}
