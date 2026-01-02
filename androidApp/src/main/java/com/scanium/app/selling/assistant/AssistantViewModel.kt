package com.scanium.app.selling.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scanium.app.data.ExportProfilePreferences
import com.scanium.app.data.SettingsRepository
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.listing.DraftField
import com.scanium.app.listing.DraftFieldKey
import com.scanium.app.listing.DraftProvenance
import com.scanium.app.listing.ExportProfileDefinition
import com.scanium.app.listing.ExportProfileRepository
import com.scanium.app.listing.ExportProfiles
import com.scanium.app.listing.ListingDraft
import com.scanium.app.listing.ListingDraftBuilder
import com.scanium.app.logging.CorrelationIds
import com.scanium.app.logging.ScaniumLog
import com.scanium.app.model.AssistantAction
import com.scanium.app.model.AssistantActionType
import com.scanium.app.model.AssistantMessage
import com.scanium.app.model.AssistantRole
import com.scanium.app.model.ConfidenceTier
import com.scanium.app.model.EvidenceBullet
import com.scanium.app.model.ItemContextSnapshot
import com.scanium.app.model.ItemContextSnapshotBuilder
import com.scanium.app.platform.ConnectivityStatus
import com.scanium.app.platform.ConnectivityStatusProvider
import com.scanium.app.selling.assistant.local.LocalSuggestionEngine
import com.scanium.app.selling.assistant.local.LocalSuggestions
import com.scanium.app.selling.persistence.ListingDraftStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssistantChatEntry(
    val message: AssistantMessage,
    val actions: List<AssistantAction> = emptyList(),
    val confidenceTier: ConfidenceTier? = null,
    val evidence: List<EvidenceBullet> = emptyList(),
    val suggestedNextPhoto: String? = null,
    /** Whether this entry represents a failed request */
    val isFailed: Boolean = false,
    /** Error message if failed */
    val errorMessage: String? = null,
)

/**
 * Loading stage for staged responses.
 */
enum class LoadingStage {
    IDLE,
    VISION_PROCESSING,
    LLM_PROCESSING,
    DONE,
    ERROR,
}

enum class AssistantMode {
    ONLINE,
    OFFLINE,
    LIMITED,
}

/**
 * Explicit availability state for the AI assistant.
 * This is a first-class concept that determines what actions the user can take.
 */
sealed class AssistantAvailability {
    /**
     * Assistant is ready to accept messages. Online AI features work.
     */
    data object Available : AssistantAvailability()

    /**
     * Currently checking availability (e.g., on screen entry).
     */
    data object Checking : AssistantAvailability()

    /**
     * Assistant is unavailable. User cannot send messages.
     * Local suggestions may still be shown.
     */
    data class Unavailable(
        val reason: UnavailableReason,
        val canRetry: Boolean,
        val retryAfterSeconds: Int? = null,
    ) : AssistantAvailability()

    /**
     * Returns true if the user can currently send messages.
     */
    val canSendMessages: Boolean
        get() = this is Available

    /**
     * Returns true if local-only features should be promoted.
     */
    val isLocalOnly: Boolean
        get() = this !is Available
}

/**
 * Reasons why the assistant might be unavailable.
 */
enum class UnavailableReason {
    /** Device is offline */
    OFFLINE,
    /** Backend returned an error */
    BACKEND_ERROR,
    /** Rate limited - too many requests */
    RATE_LIMITED,
    /** Not authorized (subscription/auth issue) */
    UNAUTHORIZED,
    /** Backend not configured */
    NOT_CONFIGURED,
    /** Request validation error */
    VALIDATION_ERROR,
    /** Currently processing a request */
    LOADING,
}

data class AssistantUiState(
    val itemIds: List<String> = emptyList(),
    val snapshots: List<ItemContextSnapshot> = emptyList(),
    val profile: ExportProfileDefinition = ExportProfiles.generic(),
    val entries: List<AssistantChatEntry> = emptyList(),
    val isLoading: Boolean = false,
    /** Current loading stage for progress indication */
    val loadingStage: LoadingStage = LoadingStage.IDLE,
    /** Failed message text to preserve for retry */
    val failedMessageText: String? = null,
    /** Last user message for retry */
    val lastUserMessage: String? = null,
    /** Smart suggested questions based on context */
    val suggestedQuestions: List<String> = emptyList(),
    /** Current assistant mode */
    val assistantMode: AssistantMode = AssistantMode.ONLINE,
    /** Whether device is online */
    val isOnline: Boolean = true,
    /** Last backend failure for messaging */
    val lastBackendFailure: AssistantBackendFailure? = null,
    /** Explicit availability state - determines if user can send messages */
    val availability: AssistantAvailability = AssistantAvailability.Available,
    /** Local suggestions when assistant is unavailable */
    val localSuggestions: LocalSuggestions? = null,
) {
    /**
     * Returns true if the text input should be enabled.
     * Input is disabled when:
     * - Assistant is unavailable (offline, error, rate limited)
     * - A request is currently loading
     */
    val isInputEnabled: Boolean
        get() = availability.canSendMessages && !isLoading

    /**
     * Returns a user-friendly placeholder for the input field based on availability.
     */
    val inputPlaceholder: String
        get() = when (availability) {
            is AssistantAvailability.Available -> "Ask about listing improvements..."
            is AssistantAvailability.Checking -> "Checking availability..."
            is AssistantAvailability.Unavailable -> when (availability.reason) {
                UnavailableReason.OFFLINE -> "You're offline. Connect to use assistant."
                UnavailableReason.RATE_LIMITED -> "Rate limited. Please wait."
                UnavailableReason.UNAUTHORIZED -> "Authorization required."
                UnavailableReason.NOT_CONFIGURED -> "Assistant not configured."
                UnavailableReason.BACKEND_ERROR -> "Assistant temporarily unavailable."
                UnavailableReason.VALIDATION_ERROR -> "Service error. Try again."
                UnavailableReason.LOADING -> "Processing..."
            }
        }
}

sealed class AssistantUiEvent {
    data class ShowSnackbar(val message: String) : AssistantUiEvent()
}

/**
 * ViewModel for the Assistant screen that manages AI-powered listing assistance.
 *
 * Part of ARCH-001/DX-003: Migrated to Hilt assisted injection to reduce boilerplate.
 * Runtime parameters (itemIds, itemsViewModel) are passed via @Assisted annotation,
 * while singleton dependencies are injected by Hilt.
 */
class AssistantViewModel
    @AssistedInject
    constructor(
        @Assisted private val itemIds: List<String>,
        @Assisted private val itemsViewModel: ItemsViewModel,
        private val draftStore: ListingDraftStore,
        private val exportProfileRepository: ExportProfileRepository,
        private val exportProfilePreferences: ExportProfilePreferences,
        private val assistantRepository: AssistantRepository,
        private val settingsRepository: SettingsRepository,
        private val localAssistantHelper: LocalAssistantHelper,
        private val localSuggestionEngine: LocalSuggestionEngine,
        private val connectivityStatusProvider: ConnectivityStatusProvider,
        private val preflightManager: AssistantPreflightManager,
    ) : ViewModel() {
        /**
         * Factory for creating AssistantViewModel instances with assisted injection.
         * Part of ARCH-001/DX-003: Replaces verbose manual Factory class.
         */
        @AssistedFactory
        interface Factory {
            fun create(
                itemIds: List<String>,
                itemsViewModel: ItemsViewModel,
            ): AssistantViewModel
        }

        private val _uiState = MutableStateFlow(
            AssistantUiState(
                itemIds = itemIds,
                availability = AssistantAvailability.Checking,
            ),
        )
        val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

        private val _events = MutableSharedFlow<AssistantUiEvent>()
        val events = _events.asSharedFlow()

        init {
            loadProfileAndSnapshots()
            observeConnectivity()
            observePreflightStatus()
            runInitialPreflight()
        }

        fun sendMessage(text: String) {
            val trimmed = text.trim()
            if (trimmed.isBlank()) return
            val correlationId = CorrelationIds.newAssistRequestId()

            val userMessage =
                AssistantMessage(
                    role = AssistantRole.USER,
                    content = trimmed,
                    timestamp = System.currentTimeMillis(),
                    itemContextIds = itemIds,
                )

            _uiState.update { state ->
                state.copy(
                    entries = state.entries + AssistantChatEntry(userMessage),
                    isLoading = true,
                    loadingStage = LoadingStage.VISION_PROCESSING,
                    failedMessageText = null,
                    lastUserMessage = trimmed,
                    // Disable input while loading
                    availability = AssistantAvailability.Unavailable(
                        reason = UnavailableReason.LOADING,
                        canRetry = false,
                    ),
                )
            }

            viewModelScope.launch {
                val state = _uiState.value

                ScaniumLog.i(
                    TAG,
                    "Assist request correlationId=$correlationId items=${state.snapshots.size} messageLength=${trimmed.length}",
                )

                // Note: Currently images are not attached in this implementation.
                // When image support is added, check settingsRepository.allowAssistantImagesFlow
                // and only include thumbnails if that toggle is ON.

                if (!state.isOnline) {
                    applyLocalFallback(
                        trimmed,
                        state,
                        AssistantBackendFailure(
                            type = AssistantBackendErrorType.NETWORK_TIMEOUT,
                            category = AssistantBackendErrorCategory.TEMPORARY,
                            retryable = true,
                            message = "Offline",
                        ),
                    )
                    return@launch
                }

                try {
                    // Update stage to LLM processing
                    _uiState.update { it.copy(loadingStage = LoadingStage.LLM_PROCESSING) }

                    // Get current assistant preferences
                    val prefs = settingsRepository.assistantPrefsFlow.first()

                    // Images are currently not attached (empty list).
                    // Future implementation should only include images if allowImages is true.
                    val response =
                        assistantRepository.send(
                            items = state.snapshots,
                            history = state.entries.map { it.message },
                            userMessage = trimmed,
                            exportProfile = state.profile,
                            correlationId = correlationId,
                            imageAttachments = emptyList(),
// Explicit: no images sent
                            assistantPrefs = prefs,
                        )

                    val assistantMessage =
                        AssistantMessage(
                            role = AssistantRole.ASSISTANT,
                            content = response.text,
                            timestamp = System.currentTimeMillis(),
                            itemContextIds = itemIds,
                        )

                    _uiState.update { current ->
                        current.copy(
                            entries =
                                current.entries +
                                    AssistantChatEntry(
                                        message = assistantMessage,
                                        actions = response.actions,
                                        confidenceTier = response.confidenceTier,
                                        evidence = response.evidence,
                                        suggestedNextPhoto = response.suggestedNextPhoto,
                                    ),
                            isLoading = false,
                            loadingStage = LoadingStage.DONE,
                            suggestedQuestions = computeSuggestedQuestions(current.snapshots),
                            lastBackendFailure = null,
                            assistantMode = resolveMode(current.isOnline, null),
                            // Success - restore availability
                            availability = AssistantAvailability.Available,
                        )
                    }
                    ScaniumLog.i(TAG, "Assistant availability restored: Available (success)")

                    ScaniumLog.i(
                        TAG,
                        "Assist response correlationId=$correlationId actions=${response.actions.size} fromCache=${response.citationsMetadata?.get(
                            "fromCache",
                        )}",
                    )
                } catch (e: AssistantBackendException) {
                    ScaniumLog.w(TAG, "Assist backend failure correlationId=$correlationId type=${e.failure.type}", e)
                    applyLocalFallback(trimmed, _uiState.value, e.failure)
                } catch (e: Exception) {
                    val failure =
                        AssistantBackendFailure(
                            type = AssistantBackendErrorType.PROVIDER_UNAVAILABLE,
                            category = AssistantBackendErrorCategory.TEMPORARY,
                            retryable = true,
                            message = "Assistant request failed",
                        )
                    ScaniumLog.e(TAG, "Assist request failed correlationId=$correlationId", e)
                    applyLocalFallback(trimmed, _uiState.value, failure)
                }
            }
        }

        /**
         * Retry the last failed message.
         */
        fun retryLastMessage() {
            val failedText = _uiState.value.lastUserMessage ?: return
            if (!_uiState.value.isOnline) {
                viewModelScope.launch {
                    _events.emit(AssistantUiEvent.ShowSnackbar("You're offline. Retry when back online."))
                }
                return
            }
            _uiState.update { it.copy(loadingStage = LoadingStage.IDLE, failedMessageText = null) }
            sendMessage(failedText)
        }

        /**
         * Compute context-aware suggested questions based on item category.
         */
        private fun computeSuggestedQuestions(snapshots: List<ItemContextSnapshot>): List<String> {
            val suggestions = mutableListOf<String>()
            val snapshot = snapshots.firstOrNull() ?: return defaultSuggestions()
            val category = snapshot.category?.lowercase() ?: ""

            // Check what's missing
            val hasBrand = snapshot.attributes?.any { it.key.equals("brand", ignoreCase = true) } == true
            val hasColor = snapshot.attributes?.any { it.key.equals("color", ignoreCase = true) } == true
            val title = snapshot.title
            val description = snapshot.description
            val hasTitle = !title.isNullOrBlank() && title.length > 5
            val hasDescription = !description.isNullOrBlank() && description.length > 20
            val priceEstimate = snapshot.priceEstimate
            val hasPrice = priceEstimate != null && priceEstimate > 0

            // Category-specific suggestions
            when {
                category.contains("electronic") || category.contains("phone") || category.contains("computer") || category.contains("camera") -> {
                    if (!hasBrand) suggestions.add("What brand and model is this?")
                    suggestions.add("What's the storage capacity?")
                    suggestions.add("Does it power on? Any screen issues?")
                    suggestions.add("Are all accessories included?")
                    suggestions.add("Any scratches or dents?")
                }
                category.contains("furniture") || category.contains("home") || category.contains("decor") || category.contains("chair") || category.contains("table") -> {
                    suggestions.add("What are the dimensions (H x W x D)?")
                    suggestions.add("What material is it made of?")
                    suggestions.add("Any scratches, stains, or wear?")
                    suggestions.add("Is assembly required?")
                    if (!hasColor) suggestions.add("What color/finish is it?")
                }
                category.contains("fashion") || category.contains("clothing") || category.contains("shoes") || category.contains("apparel") -> {
                    if (!hasBrand) suggestions.add("What brand is this?")
                    suggestions.add("What size is this?")
                    if (!hasColor) suggestions.add("What color is it?")
                    suggestions.add("What's the fabric/material?")
                    suggestions.add("Any signs of wear or defects?")
                }
                category.contains("toy") || category.contains("game") || category.contains("puzzle") -> {
                    suggestions.add("Is it complete with all pieces?")
                    suggestions.add("What age range is it for?")
                    suggestions.add("Does it require batteries?")
                    if (!hasBrand) suggestions.add("What brand is this?")
                }
                category.contains("book") || category.contains("media") || category.contains("dvd") || category.contains("vinyl") -> {
                    suggestions.add("Who is the author/artist?")
                    suggestions.add("Is this a first edition?")
                    suggestions.add("Condition of binding/pages?")
                    suggestions.add("Any markings or highlights?")
                }
                category.contains("sport") || category.contains("fitness") || category.contains("outdoor") || category.contains("bike") -> {
                    if (!hasBrand) suggestions.add("What brand is this?")
                    suggestions.add("What size is it?")
                    suggestions.add("Any damage or wear?")
                    suggestions.add("Does it include accessories?")
                }
                else -> {
                    // General fallback suggestions
                    if (!hasBrand) suggestions.add("What brand is this?")
                    if (!hasColor) suggestions.add("What color is this item?")
                    suggestions.add("What details should I add?")
                    suggestions.add("Any defects to mention?")
                }
            }

            // Always add these if not covered
            if (!hasTitle || (snapshot.title?.length ?: 0) < 15) {
                suggestions.add("Suggest a better title")
            }
            if (!hasDescription) {
                suggestions.add("Help me write a description")
            }
            if (!hasPrice) {
                suggestions.add("What should I price this at?")
            }

            // Remove duplicates, shuffle, and take 3
            return suggestions.distinct().shuffled().take(3)
        }

        private fun defaultSuggestions(): List<String> =
            listOf(
                "Suggest a better title",
                "What details should I add?",
                "Estimate price range",
            )

        fun applyDraftUpdate(action: AssistantAction) {
            if (action.type != AssistantActionType.APPLY_DRAFT_UPDATE) return
            val payload = action.payload
            val itemId = payload["itemId"] ?: itemIds.firstOrNull() ?: return
            val correlationId = CorrelationIds.newDraftRequestId()

            viewModelScope.launch {
                val draft =
                    draftStore.getByItemId(itemId)
                        ?: itemsViewModel.items.value.firstOrNull { it.id == itemId }
                            ?.let { ListingDraftBuilder.build(it) }
                if (draft == null) {
                    _events.emit(AssistantUiEvent.ShowSnackbar("No draft available"))
                    return@launch
                }

                val updated = updateDraftFromPayload(draft, payload)
                draftStore.upsert(updated)
                ScaniumLog.i(
                    TAG,
                    "Draft update applied correlationId=$correlationId itemId=$itemId",
                )
                _events.emit(AssistantUiEvent.ShowSnackbar("Draft updated"))
                refreshSnapshots()
            }
        }

        fun addAttributes(action: AssistantAction) {
            if (action.type != AssistantActionType.ADD_ATTRIBUTES) return
            val payload = action.payload
            val itemId = payload["itemId"] ?: itemIds.firstOrNull() ?: return
            val correlationId = CorrelationIds.newDraftRequestId()

            viewModelScope.launch {
                val draft =
                    draftStore.getByItemId(itemId)
                        ?: itemsViewModel.items.value.firstOrNull { it.id == itemId }
                            ?.let { ListingDraftBuilder.build(it) }
                if (draft == null) {
                    _events.emit(AssistantUiEvent.ShowSnackbar("No draft available"))
                    return@launch
                }

                val updated = updateDraftFromPayload(draft, payload)
                draftStore.upsert(updated)
                ScaniumLog.i(
                    TAG,
                    "Attributes added correlationId=$correlationId itemId=$itemId",
                )
                _events.emit(AssistantUiEvent.ShowSnackbar("Attributes added"))
                refreshSnapshots()
            }
        }

        /**
         * Apply a suggested title from local suggestions.
         */
        fun applyLocalSuggestedTitle(title: String) {
            val itemId = itemIds.firstOrNull() ?: return
            viewModelScope.launch {
                val draft =
                    draftStore.getByItemId(itemId)
                        ?: itemsViewModel.items.value.firstOrNull { it.id == itemId }
                            ?.let { ListingDraftBuilder.build(it) }
                if (draft == null) {
                    _events.emit(AssistantUiEvent.ShowSnackbar("No draft available"))
                    return@launch
                }
                val updated = updateDraftFromPayload(draft, mapOf("title" to title))
                draftStore.upsert(updated)
                ScaniumLog.i(TAG, "Local suggestion title applied itemId=$itemId")
                _events.emit(AssistantUiEvent.ShowSnackbar("Title updated"))
                refreshSnapshots()
            }
        }

        /**
         * Apply a suggested description from local suggestions.
         */
        fun applyLocalSuggestedDescription(description: String) {
            val itemId = itemIds.firstOrNull() ?: return
            viewModelScope.launch {
                val draft =
                    draftStore.getByItemId(itemId)
                        ?: itemsViewModel.items.value.firstOrNull { it.id == itemId }
                            ?.let { ListingDraftBuilder.build(it) }
                if (draft == null) {
                    _events.emit(AssistantUiEvent.ShowSnackbar("No draft available"))
                    return@launch
                }
                val updated = updateDraftFromPayload(draft, mapOf("description" to description))
                draftStore.upsert(updated)
                ScaniumLog.i(TAG, "Local suggestion description applied itemId=$itemId")
                _events.emit(AssistantUiEvent.ShowSnackbar("Description updated"))
                refreshSnapshots()
            }
        }

        private fun loadProfileAndSnapshots() {
            viewModelScope.launch {
                val profiles = exportProfileRepository.getProfiles().ifEmpty { listOf(ExportProfiles.generic()) }
                val defaultId = exportProfileRepository.getDefaultProfileId()
                val persistedId = exportProfilePreferences.getLastProfileId(defaultId)
                val profile =
                    profiles.firstOrNull { it.id == persistedId }
                        ?: profiles.firstOrNull { it.id == defaultId }
                        ?: ExportProfiles.generic()
                _uiState.update { it.copy(profile = profile) }
                refreshSnapshots()
            }
        }

        private suspend fun refreshSnapshots() {
            val snapshots =
                itemIds.mapNotNull { itemId ->
                    val draft =
                        draftStore.getByItemId(itemId)
                            ?: itemsViewModel.items.value.firstOrNull { it.id == itemId }
                                ?.let { ListingDraftBuilder.build(it) }
                    draft?.let { ItemContextSnapshotBuilder.fromDraft(it) }
                }
            val localSuggestions = localSuggestionEngine.generateSuggestions(snapshots)
            _uiState.update { it.copy(snapshots = snapshots, localSuggestions = localSuggestions) }
        }

        private fun observeConnectivity() {
            viewModelScope.launch {
                connectivityStatusProvider.statusFlow.collect { status ->
                    val online = status == ConnectivityStatus.ONLINE
                    _uiState.update { current ->
                        // When coming back online, clear transient failures if they were network-related
                        val updatedFailure = if (online && current.lastBackendFailure?.type in listOf(
                                AssistantBackendErrorType.NETWORK_TIMEOUT,
                                AssistantBackendErrorType.NETWORK_UNREACHABLE,
                            )
                        ) null else current.lastBackendFailure

                        val newAvailability = computeAvailability(
                            isOnline = online,
                            isLoading = current.isLoading,
                            failure = updatedFailure,
                        )

                        current.copy(
                            isOnline = online,
                            assistantMode = resolveMode(online, updatedFailure),
                            lastBackendFailure = updatedFailure,
                            availability = newAvailability,
                        )
                    }
                    ScaniumLog.i(TAG, "Assistant connectivity status=$status availability=${availabilityDebugString(_uiState.value.availability)}")

                    // When coming back online, trigger a fresh preflight check
                    if (online) {
                        runPreflight(forceRefresh = false)
                    }
                }
            }
        }

        /**
         * Observe preflight status changes and update availability accordingly.
         */
        private fun observePreflightStatus() {
            viewModelScope.launch {
                preflightManager.currentResult.collect { result ->
                    updateAvailabilityFromPreflight(result)
                }
            }
        }

        /**
         * Run initial preflight check on ViewModel creation.
         * Starts with Checking state, then transitions to actual state.
         */
        private fun runInitialPreflight() {
            viewModelScope.launch {
                val result = preflightManager.preflight(forceRefresh = false)
                updateAvailabilityFromPreflight(result)

                // If available, trigger warm-up in background
                if (result.isAvailable) {
                    preflightManager.warmUp()
                }
            }
        }

        /**
         * Run a preflight check, optionally forcing a refresh.
         * Called on retry button press or when connectivity changes.
         */
        fun runPreflight(forceRefresh: Boolean = true) {
            viewModelScope.launch {
                // Set to checking state
                _uiState.update { it.copy(availability = AssistantAvailability.Checking) }

                val result = preflightManager.preflight(forceRefresh = forceRefresh)
                updateAvailabilityFromPreflight(result)

                // If available after retry, trigger warm-up
                if (result.isAvailable) {
                    preflightManager.warmUp()
                }
            }
        }

        /**
         * Cancel any ongoing warm-up. Call this when leaving the screen.
         */
        fun cancelWarmUp() {
            preflightManager.cancelWarmUp()
        }

        /**
         * Update the availability state based on preflight result.
         */
        private fun updateAvailabilityFromPreflight(result: PreflightResult) {
            val availability = when (result.status) {
                PreflightStatus.AVAILABLE -> AssistantAvailability.Available
                PreflightStatus.CHECKING -> AssistantAvailability.Checking
                PreflightStatus.UNKNOWN -> {
                    // Unknown state - check connectivity to decide
                    if (_uiState.value.isOnline) {
                        AssistantAvailability.Checking
                    } else {
                        AssistantAvailability.Unavailable(
                            reason = UnavailableReason.OFFLINE,
                            canRetry = true,
                        )
                    }
                }
                PreflightStatus.OFFLINE -> AssistantAvailability.Unavailable(
                    reason = UnavailableReason.OFFLINE,
                    canRetry = true,
                )
                PreflightStatus.TEMPORARILY_UNAVAILABLE -> AssistantAvailability.Unavailable(
                    reason = UnavailableReason.BACKEND_ERROR,
                    canRetry = true,
                )
                PreflightStatus.RATE_LIMITED -> AssistantAvailability.Unavailable(
                    reason = UnavailableReason.RATE_LIMITED,
                    canRetry = true,
                    retryAfterSeconds = result.retryAfterSeconds,
                )
                PreflightStatus.UNAUTHORIZED -> AssistantAvailability.Unavailable(
                    reason = UnavailableReason.UNAUTHORIZED,
                    canRetry = false,
                )
                PreflightStatus.NOT_CONFIGURED -> AssistantAvailability.Unavailable(
                    reason = UnavailableReason.NOT_CONFIGURED,
                    canRetry = false,
                )
            }

            val mode = when (result.status) {
                PreflightStatus.AVAILABLE -> AssistantMode.ONLINE
                PreflightStatus.OFFLINE -> AssistantMode.OFFLINE
                else -> AssistantMode.LIMITED
            }

            _uiState.update { current ->
                // Don't override if we're in loading state (user already sent a message)
                if (current.isLoading) {
                    current
                } else {
                    current.copy(
                        availability = availability,
                        assistantMode = mode,
                        // Clear backend failure if preflight succeeded
                        lastBackendFailure = if (result.isAvailable) null else current.lastBackendFailure,
                    )
                }
            }

            ScaniumLog.i(
                TAG,
                "Preflight result: status=${result.status} latency=${result.latencyMs}ms availability=${availabilityDebugString(availability)}",
            )
        }

        private fun resolveMode(
            isOnline: Boolean,
            failure: AssistantBackendFailure?,
        ): AssistantMode {
            return if (!isOnline) {
                AssistantMode.OFFLINE
            } else if (failure != null) {
                AssistantMode.LIMITED
            } else {
                AssistantMode.ONLINE
            }
        }

        /**
         * Computes the explicit availability state from current conditions.
         */
        private fun computeAvailability(
            isOnline: Boolean,
            isLoading: Boolean,
            failure: AssistantBackendFailure?,
        ): AssistantAvailability {
            // Loading state - user must wait
            if (isLoading) {
                return AssistantAvailability.Unavailable(
                    reason = UnavailableReason.LOADING,
                    canRetry = false,
                )
            }

            // Offline - cannot send
            if (!isOnline) {
                return AssistantAvailability.Unavailable(
                    reason = UnavailableReason.OFFLINE,
                    canRetry = true,
                )
            }

            // Map backend failures to availability
            if (failure != null) {
                val (reason, canRetry, retryAfter) = when (failure.type) {
                    AssistantBackendErrorType.UNAUTHORIZED ->
                        Triple(UnavailableReason.UNAUTHORIZED, false, null)
                    AssistantBackendErrorType.PROVIDER_NOT_CONFIGURED ->
                        Triple(UnavailableReason.NOT_CONFIGURED, false, null)
                    AssistantBackendErrorType.RATE_LIMITED ->
                        Triple(UnavailableReason.RATE_LIMITED, true, failure.retryAfterSeconds)
                    AssistantBackendErrorType.VALIDATION_ERROR ->
                        Triple(UnavailableReason.VALIDATION_ERROR, false, null)
                    AssistantBackendErrorType.NETWORK_TIMEOUT,
                    AssistantBackendErrorType.NETWORK_UNREACHABLE,
                    AssistantBackendErrorType.VISION_UNAVAILABLE,
                    AssistantBackendErrorType.PROVIDER_UNAVAILABLE ->
                        Triple(UnavailableReason.BACKEND_ERROR, true, null)
                }
                return AssistantAvailability.Unavailable(
                    reason = reason,
                    canRetry = canRetry,
                    retryAfterSeconds = retryAfter,
                )
            }

            // All good
            return AssistantAvailability.Available
        }

        /**
         * Clears any previous failure state and marks assistant as available.
         * Called after successful retry or when conditions improve.
         */
        fun clearFailureState() {
            _uiState.update { current ->
                current.copy(
                    lastBackendFailure = null,
                    assistantMode = AssistantMode.ONLINE,
                    availability = computeAvailability(
                        isOnline = current.isOnline,
                        isLoading = false,
                        failure = null,
                    ),
                )
            }
            ScaniumLog.i(TAG, "Assistant availability cleared, now Available")
        }

        /**
         * Re-evaluate availability based on current state.
         * Call this on screen resume or when connectivity changes.
         */
        fun refreshAvailability() {
            val current = _uiState.value
            val newAvailability = computeAvailability(
                isOnline = current.isOnline,
                isLoading = current.isLoading,
                failure = current.lastBackendFailure,
            )
            if (newAvailability != current.availability) {
                _uiState.update { it.copy(availability = newAvailability) }
                ScaniumLog.i(TAG, "Assistant availability changed: ${availabilityDebugString(newAvailability)}")
            }
        }

        private fun availabilityDebugString(availability: AssistantAvailability): String {
            return when (availability) {
                is AssistantAvailability.Available -> "Available"
                is AssistantAvailability.Checking -> "Checking"
                is AssistantAvailability.Unavailable ->
                    "Unavailable(${availability.reason}, canRetry=${availability.canRetry}, retryAfter=${availability.retryAfterSeconds})"
            }
        }

        private suspend fun applyLocalFallback(
            message: String,
            state: AssistantUiState,
            failure: AssistantBackendFailure,
        ) {
            val response =
                localAssistantHelper.buildResponse(
                    items = state.snapshots,
                    userMessage = message,
                    failure = failure,
                )
            val assistantMessage =
                AssistantMessage(
                    role = AssistantRole.ASSISTANT,
                    content = response.text,
                    timestamp = System.currentTimeMillis(),
                    itemContextIds = itemIds,
                )
            val newAvailability = computeAvailability(
                isOnline = state.isOnline,
                isLoading = false,
                failure = failure,
            )
            _uiState.update { current ->
                current.copy(
                    entries =
                        current.entries +
                            AssistantChatEntry(
                                message = assistantMessage,
                                actions = response.actions,
                                confidenceTier = response.confidenceTier,
                                evidence = response.evidence,
                                suggestedNextPhoto = response.suggestedNextPhoto,
                            ),
                    isLoading = false,
                    loadingStage = LoadingStage.DONE,
                    suggestedQuestions = computeSuggestedQuestions(current.snapshots),
                    lastBackendFailure = failure,
                    assistantMode = resolveMode(current.isOnline, failure),
                    availability = newAvailability,
                )
            }
            val debugReason = AssistantErrorDisplay.getDebugReason(failure)
            ScaniumLog.i(TAG, "Assistant fallback mode=${_uiState.value.assistantMode} availability=${availabilityDebugString(newAvailability)} $debugReason")

            val snackbarMessage = buildFallbackSnackbarMessage(failure)
            _events.emit(AssistantUiEvent.ShowSnackbar(snackbarMessage))
        }

        private fun buildFallbackSnackbarMessage(failure: AssistantBackendFailure): String {
            val statusLabel = AssistantErrorDisplay.getStatusLabel(failure)
            val base = "Switched to Local Helper"

            return when (failure.type) {
                AssistantBackendErrorType.UNAUTHORIZED ->
                    "$base: $statusLabel. Check your account."
                AssistantBackendErrorType.PROVIDER_NOT_CONFIGURED ->
                    "$base: $statusLabel. Contact support."
                AssistantBackendErrorType.RATE_LIMITED -> {
                    val retryHint = failure.retryAfterSeconds?.let { " (wait ${it}s)" } ?: ""
                    "$base: $statusLabel$retryHint."
                }
                AssistantBackendErrorType.NETWORK_TIMEOUT ->
                    "$base: $statusLabel. Check your connection."
                AssistantBackendErrorType.NETWORK_UNREACHABLE ->
                    "$base: $statusLabel. Connect to internet."
                AssistantBackendErrorType.VISION_UNAVAILABLE ->
                    "$base: Image analysis unavailable."
                AssistantBackendErrorType.VALIDATION_ERROR ->
                    "$base: Invalid request. Try rephrasing."
                AssistantBackendErrorType.PROVIDER_UNAVAILABLE ->
                    "$base: Service temporarily unavailable."
            }
        }

        private fun updateDraftFromPayload(
            draft: ListingDraft,
            payload: Map<String, String>,
        ): ListingDraft {
            var updated = draft
            val now = System.currentTimeMillis()

            payload["title"]?.let { title ->
                updated =
                    updated.copy(
                        title = DraftField(title, confidence = 1f, source = DraftProvenance.USER_EDITED),
                        updatedAt = now,
                    )
            }

            payload["description"]?.let { description ->
                updated =
                    updated.copy(
                        description = DraftField(description, confidence = 1f, source = DraftProvenance.USER_EDITED),
                        updatedAt = now,
                    )
            }

            payload["price"]?.toDoubleOrNull()?.let { price ->
                updated =
                    updated.copy(
                        price = DraftField(price, confidence = 1f, source = DraftProvenance.USER_EDITED),
                        updatedAt = now,
                    )
            }

            val updatedFields = updated.fields.toMutableMap()
            payload.filterKeys { it.startsWith("field.") }.forEach { (key, value) ->
                val fieldKey = DraftFieldKey.fromWireValue(key.removePrefix("field."))
                if (fieldKey != null) {
                    updatedFields[fieldKey] = DraftField(value, confidence = 1f, source = DraftProvenance.USER_EDITED)
                }
            }
            updated = updated.copy(fields = updatedFields, updatedAt = now)

            return updated.recomputeCompleteness()
        }

        companion object {
            private const val TAG = "Assistant"

            /**
             * Creates a ViewModelProvider.Factory for AssistantViewModel using Hilt's assisted factory.
             * Part of ARCH-001/DX-003: Simplified factory creation with Hilt.
             *
             * @param assistedFactory The Hilt-generated assisted factory
             * @param itemIds The list of item IDs to assist with
             * @param itemsViewModel The shared ItemsViewModel instance
             */
            fun provideFactory(
                assistedFactory: Factory,
                itemIds: List<String>,
                itemsViewModel: ItemsViewModel,
            ): ViewModelProvider.Factory {
                return object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return assistedFactory.create(itemIds, itemsViewModel) as T
                    }
                }
            }
        }
    }
