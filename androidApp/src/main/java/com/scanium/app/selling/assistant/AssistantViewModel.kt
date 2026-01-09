package com.scanium.app.selling.assistant

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scanium.app.data.ExportProfilePreferences
import com.scanium.app.data.SettingsRepository
import com.scanium.app.items.ItemLocalizer
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
import com.scanium.app.model.ItemAttributeSnapshot
import com.scanium.app.model.ItemContextSnapshot
import com.scanium.app.model.SuggestedAttribute
import com.scanium.app.items.ScannedItem
import com.scanium.shared.core.models.assistant.AttributeSource
import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.app.model.ItemContextSnapshotBuilder
import com.scanium.app.platform.ConnectivityStatus
import com.scanium.app.platform.ConnectivityStatusProvider
import com.scanium.app.selling.assistant.local.LocalSuggestionEngine
import com.scanium.app.selling.assistant.local.LocalSuggestions
import com.scanium.app.selling.persistence.ListingDraftStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
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
    /** Vision-derived attribute suggestions (brand, color, etc.) */
    val suggestedAttributes: List<SuggestedAttribute> = emptyList(),
    /** Whether this entry represents a failed request */
    val isFailed: Boolean = false,
    /** Error message if failed */
    val errorMessage: String? = null,
)

/**
 * Loading stage for staged responses.
 * @deprecated Use [AssistantRequestProgress] for richer progress tracking.
 */
@Deprecated("Use AssistantRequestProgress instead", ReplaceWith("AssistantRequestProgress"))
enum class LoadingStage {
    IDLE,
    VISION_PROCESSING,
    LLM_PROCESSING,
    DONE,
    ERROR,
}

/**
 * Detailed progress states for assistant request lifecycle.
 * Provides richer UI feedback and timing information for each stage.
 */
sealed class AssistantRequestProgress {
    /** No request in progress */
    data object Idle : AssistantRequestProgress()

    /** Request is being prepared and sent to the backend */
    data class Sending(
        val startedAt: Long = System.currentTimeMillis(),
        val correlationId: String? = null,
    ) : AssistantRequestProgress()

    /** Request sent, waiting for backend to start processing */
    data class Thinking(
        val startedAt: Long,
        val correlationId: String? = null,
    ) : AssistantRequestProgress()

    /** Backend is processing images (only when images are attached) */
    data class ExtractingVision(
        val startedAt: Long,
        val correlationId: String? = null,
        val imageCount: Int = 0,
    ) : AssistantRequestProgress()

    /** Backend is generating response */
    data class Drafting(
        val startedAt: Long,
        val correlationId: String? = null,
    ) : AssistantRequestProgress()

    /** Post-processing response (mapping suggestedDraftUpdates, etc.) */
    data class Finalizing(
        val startedAt: Long,
        val correlationId: String? = null,
    ) : AssistantRequestProgress()

    /** Request completed successfully */
    data class Done(
        val completedAt: Long = System.currentTimeMillis(),
        val totalDurationMs: Long = 0,
        val correlationId: String? = null,
    ) : AssistantRequestProgress()

    /** Temporary error (timeouts, 5xx errors) - retryable */
    data class ErrorTemporary(
        val occurredAt: Long = System.currentTimeMillis(),
        val message: String,
        val correlationId: String? = null,
        val retryable: Boolean = true,
    ) : AssistantRequestProgress()

    /** Authentication error (401) */
    data class ErrorAuth(
        val occurredAt: Long = System.currentTimeMillis(),
        val message: String,
        val correlationId: String? = null,
    ) : AssistantRequestProgress()

    /** Validation error (400) */
    data class ErrorValidation(
        val occurredAt: Long = System.currentTimeMillis(),
        val message: String,
        val correlationId: String? = null,
    ) : AssistantRequestProgress()

    /** Returns true if this is any loading state (not idle, done, or error) */
    val isLoading: Boolean
        get() = this is Sending || this is Thinking || this is ExtractingVision ||
            this is Drafting || this is Finalizing

    /** Returns true if this is an error state */
    val isError: Boolean
        get() = this is ErrorTemporary || this is ErrorAuth || this is ErrorValidation

    /** User-friendly label for the current progress state */
    val displayLabel: String
        get() = when (this) {
            is Idle -> ""
            is Sending -> "Sending..."
            is Thinking -> "Thinking..."
            is ExtractingVision -> "Analyzing images..."
            is Drafting -> "Drafting answer..."
            is Finalizing -> "Finalizing..."
            is Done -> ""
            is ErrorTemporary -> "Temporarily unavailable"
            is ErrorAuth -> "Authentication required"
            is ErrorValidation -> "Invalid request"
        }

    /** Maps to legacy LoadingStage for backwards compatibility */
    @Suppress("DEPRECATION")
    fun toLegacyStage(): LoadingStage = when (this) {
        is Idle -> LoadingStage.IDLE
        is Sending, is Thinking -> LoadingStage.VISION_PROCESSING
        is ExtractingVision -> LoadingStage.VISION_PROCESSING
        is Drafting, is Finalizing -> LoadingStage.LLM_PROCESSING
        is Done -> LoadingStage.DONE
        is ErrorTemporary, is ErrorAuth, is ErrorValidation -> LoadingStage.ERROR
    }

    companion object {
        /** Create an error progress state from a backend failure */
        fun fromBackendFailure(
            failure: AssistantBackendFailure,
            correlationId: String? = null,
        ): AssistantRequestProgress {
            return when (failure.type) {
                AssistantBackendErrorType.UNAUTHORIZED ->
                    ErrorAuth(
                        message = failure.message ?: "Authentication required",
                        correlationId = correlationId,
                    )
                AssistantBackendErrorType.VALIDATION_ERROR ->
                    ErrorValidation(
                        message = failure.message ?: "Invalid request",
                        correlationId = correlationId,
                    )
                else ->
                    ErrorTemporary(
                        message = failure.message ?: "Temporarily unavailable",
                        correlationId = correlationId,
                        retryable = failure.retryable,
                    )
            }
        }
    }
}

/**
 * Tracks timing for each stage of the assistant request lifecycle.
 * Used for telemetry and performance monitoring.
 */
data class AssistantRequestTiming(
    val correlationId: String,
    val requestStartedAt: Long = System.currentTimeMillis(),
    val sendingStartedAt: Long? = null,
    val thinkingStartedAt: Long? = null,
    val extractingVisionStartedAt: Long? = null,
    val draftingStartedAt: Long? = null,
    val finalizingStartedAt: Long? = null,
    val completedAt: Long? = null,
    val hasImages: Boolean = false,
) {
    val sendingDurationMs: Long?
        get() = if (sendingStartedAt != null && thinkingStartedAt != null) {
            thinkingStartedAt - sendingStartedAt
        } else null

    val thinkingDurationMs: Long?
        get() {
            val nextStage = extractingVisionStartedAt ?: draftingStartedAt ?: completedAt
            return if (thinkingStartedAt != null && nextStage != null) {
                nextStage - thinkingStartedAt
            } else null
        }

    val extractingVisionDurationMs: Long?
        get() = if (hasImages && extractingVisionStartedAt != null && draftingStartedAt != null) {
            draftingStartedAt - extractingVisionStartedAt
        } else null

    val draftingDurationMs: Long?
        get() {
            val nextStage = finalizingStartedAt ?: completedAt
            return if (draftingStartedAt != null && nextStage != null) {
                nextStage - draftingStartedAt
            } else null
        }

    val finalizingDurationMs: Long?
        get() = if (finalizingStartedAt != null && completedAt != null) {
            completedAt - finalizingStartedAt
        } else null

    val totalDurationMs: Long?
        get() = if (completedAt != null) completedAt - requestStartedAt else null

    /** Log timing summary for telemetry */
    fun toLogString(): String {
        val parts = mutableListOf<String>()
        parts.add("correlationId=$correlationId")
        sendingDurationMs?.let { parts.add("sending=${it}ms") }
        thinkingDurationMs?.let { parts.add("thinking=${it}ms") }
        if (hasImages) {
            extractingVisionDurationMs?.let { parts.add("vision=${it}ms") }
        }
        draftingDurationMs?.let { parts.add("drafting=${it}ms") }
        finalizingDurationMs?.let { parts.add("finalizing=${it}ms") }
        totalDurationMs?.let { parts.add("total=${it}ms") }
        return parts.joinToString(" ")
    }
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
    /** Endpoint not found (404) - wrong base URL or tunnel route */
    ENDPOINT_NOT_FOUND,
    /** Request validation error */
    VALIDATION_ERROR,
    /** Currently processing a request */
    LOADING,
}

/**
 * Non-blocking preflight warning for display purposes.
 * Does NOT block input - user can still type and send messages.
 */
data class PreflightWarning(
    val status: PreflightStatus,
    val reasonCode: String?,
    val latencyMs: Long,
)

data class AssistantUiState(
    val itemIds: List<String> = emptyList(),
    val snapshots: List<ItemContextSnapshot> = emptyList(),
    /** Drafts by itemId, used for building image attachments */
    val itemDrafts: Map<String, ListingDraft> = emptyMap(),
    val profile: ExportProfileDefinition = ExportProfiles.generic(),
    val entries: List<AssistantChatEntry> = emptyList(),
    val isLoading: Boolean = false,
    /** Current loading stage for progress indication */
    @Suppress("DEPRECATION")
    val loadingStage: LoadingStage = LoadingStage.IDLE,
    /** Detailed progress state for richer UI feedback */
    val progress: AssistantRequestProgress = AssistantRequestProgress.Idle,
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
    /** Last successful assistant response entry (preserved during new requests) */
    val lastSuccessfulEntry: AssistantChatEntry? = null,
    /**
     * Non-blocking preflight warning. When set, shows an informational banner
     * but does NOT disable input. User can still type and attempt to send.
     * Cleared automatically when chat succeeds.
     */
    val preflightWarning: PreflightWarning? = null,
) {
    /**
     * Returns true if the text input should be enabled.
     *
     * IMPORTANT: Preflight failures do NOT disable input.
     * Input is only disabled when:
     * - Device is offline (no network)
     * - A request is currently loading
     * - Backend is explicitly not configured (NOT_CONFIGURED)
     *
     * For CLIENT_ERROR, UNAUTHORIZED, and other preflight failures,
     * input remains enabled so users can attempt to chat.
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
                UnavailableReason.ENDPOINT_NOT_FOUND -> "Endpoint not found (check base URL / tunnel route)"
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
        @ApplicationContext private val context: Context,
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

        @Suppress("DEPRECATION")
        fun sendMessage(text: String) {
            val trimmed = text.trim()
            if (trimmed.isBlank()) return
            val correlationId = CorrelationIds.newAssistRequestId()
            val requestStartTime = System.currentTimeMillis()

            val userMessage =
                AssistantMessage(
                    role = AssistantRole.USER,
                    content = trimmed,
                    timestamp = System.currentTimeMillis(),
                    itemContextIds = itemIds,
                )

            // Preserve last successful assistant entry
            val lastSuccess = _uiState.value.entries
                .lastOrNull { it.message.role == AssistantRole.ASSISTANT && !it.isFailed }

            // Initialize timing tracker
            var timing = AssistantRequestTiming(
                correlationId = correlationId,
                requestStartedAt = requestStartTime,
                sendingStartedAt = requestStartTime,
            )

            _uiState.update { state ->
                state.copy(
                    entries = state.entries + AssistantChatEntry(userMessage),
                    isLoading = true,
                    loadingStage = LoadingStage.VISION_PROCESSING,
                    progress = AssistantRequestProgress.Sending(
                        startedAt = requestStartTime,
                        correlationId = correlationId,
                    ),
                    failedMessageText = null,
                    lastUserMessage = trimmed,
                    lastSuccessfulEntry = lastSuccess,
                    // Disable input while loading
                    availability = AssistantAvailability.Unavailable(
                        reason = UnavailableReason.LOADING,
                        canRetry = false,
                    ),
                )
            }

            viewModelScope.launch {
                val state = _uiState.value

                // Check if images are allowed
                val allowImages = settingsRepository.allowAssistantImagesFlow.first()

                // Build image attachments from drafts if toggle is enabled
                val attachmentResult = ImageAttachmentBuilder.buildAttachments(
                    itemDrafts = state.itemDrafts,
                    allowImages = allowImages,
                )

                val hasImages = attachmentResult.attachments.isNotEmpty()
                timing = timing.copy(hasImages = hasImages)

                ScaniumLog.i(
                    TAG,
                    "Assist request correlationId=$correlationId items=${state.snapshots.size} " +
                        "messageLength=${trimmed.length} imagesEnabled=$allowImages " +
                        "attachments=${attachmentResult.attachments.size} " +
                        "totalBytes=${attachmentResult.totalBytes} " +
                        "itemImageCounts=${attachmentResult.itemImageCounts}",
                )

                if (!state.isOnline) {
                    val failure = AssistantBackendFailure(
                        type = AssistantBackendErrorType.NETWORK_TIMEOUT,
                        category = AssistantBackendErrorCategory.TEMPORARY,
                        retryable = true,
                        message = "Offline",
                    )
                    applyLocalFallback(
                        trimmed,
                        state,
                        failure,
                        correlationId,
                    )
                    return@launch
                }

                try {
                    // Transition to Thinking state after request is prepared
                    val thinkingTime = System.currentTimeMillis()
                    timing = timing.copy(thinkingStartedAt = thinkingTime)
                    _uiState.update {
                        it.copy(
                            loadingStage = LoadingStage.VISION_PROCESSING,
                            progress = AssistantRequestProgress.Thinking(
                                startedAt = thinkingTime,
                                correlationId = correlationId,
                            ),
                        )
                    }
                    ScaniumLog.d(TAG, "Progress: THINKING correlationId=$correlationId")

                    // If images are attached, show ExtractingVision state
                    if (hasImages) {
                        val visionTime = System.currentTimeMillis()
                        timing = timing.copy(extractingVisionStartedAt = visionTime)
                        _uiState.update {
                            it.copy(
                                loadingStage = LoadingStage.VISION_PROCESSING,
                                progress = AssistantRequestProgress.ExtractingVision(
                                    startedAt = visionTime,
                                    correlationId = correlationId,
                                    imageCount = attachmentResult.attachments.size,
                                ),
                            )
                        }
                        ScaniumLog.d(TAG, "Progress: EXTRACTING_VISION correlationId=$correlationId images=${attachmentResult.attachments.size}")
                    }

                    // Get current assistant preferences
                    val prefs = settingsRepository.assistantPrefsFlow.first()

                    // Send request with image attachments (if toggle enabled and photos exist)
                    val response =
                        assistantRepository.send(
                            items = state.snapshots,
                            history = state.entries.map { it.message },
                            userMessage = trimmed,
                            exportProfile = state.profile,
                            correlationId = correlationId,
                            imageAttachments = attachmentResult.attachments,
                            assistantPrefs = prefs,
                        )

                    // Transition to Drafting state after receiving response
                    val draftingTime = System.currentTimeMillis()
                    timing = timing.copy(draftingStartedAt = draftingTime)
                    _uiState.update {
                        it.copy(
                            loadingStage = LoadingStage.LLM_PROCESSING,
                            progress = AssistantRequestProgress.Drafting(
                                startedAt = draftingTime,
                                correlationId = correlationId,
                            ),
                        )
                    }
                    ScaniumLog.d(TAG, "Progress: DRAFTING correlationId=$correlationId")

                    // Transition to Finalizing state if there are suggestedDraftUpdates to process
                    val hasDraftUpdates = response.suggestedDraftUpdates.isNotEmpty()
                    if (hasDraftUpdates) {
                        val finalizingTime = System.currentTimeMillis()
                        timing = timing.copy(finalizingStartedAt = finalizingTime)
                        _uiState.update {
                            it.copy(
                                progress = AssistantRequestProgress.Finalizing(
                                    startedAt = finalizingTime,
                                    correlationId = correlationId,
                                ),
                            )
                        }
                        ScaniumLog.d(TAG, "Progress: FINALIZING correlationId=$correlationId updates=${response.suggestedDraftUpdates.size}")
                    }

                    val assistantMessage =
                        AssistantMessage(
                            role = AssistantRole.ASSISTANT,
                            content = response.text,
                            timestamp = System.currentTimeMillis(),
                            itemContextIds = itemIds,
                        )

                    val completedTime = System.currentTimeMillis()
                    timing = timing.copy(completedAt = completedTime)

                    val newEntry = AssistantChatEntry(
                        message = assistantMessage,
                        actions = response.actions,
                        confidenceTier = response.confidenceTier,
                        evidence = response.evidence,
                        suggestedNextPhoto = response.suggestedNextPhoto,
                        suggestedAttributes = response.suggestedAttributes,
                    )

                    _uiState.update { current ->
                        current.copy(
                            entries = current.entries + newEntry,
                            isLoading = false,
                            loadingStage = LoadingStage.DONE,
                            progress = AssistantRequestProgress.Done(
                                completedAt = completedTime,
                                totalDurationMs = timing.totalDurationMs ?: 0,
                                correlationId = correlationId,
                            ),
                            suggestedQuestions = computeSuggestedQuestions(current.snapshots),
                            lastBackendFailure = null,
                            assistantMode = resolveMode(current.isOnline, null),
                            // Chat success - restore availability and clear any preflight warnings
                            // This ensures successful chat always overrides preflight failures
                            availability = AssistantAvailability.Available,
                            preflightWarning = null,
                            lastSuccessfulEntry = newEntry,
                        )
                    }

                    // Log timing summary
                    ScaniumLog.i(TAG, "Assist timing: ${timing.toLogString()}")
                    ScaniumLog.i(TAG, "Chat success - availability=Available, preflightWarning cleared")

                    ScaniumLog.i(
                        TAG,
                        "Assist response correlationId=$correlationId actions=${response.actions.size} fromCache=${response.citationsMetadata?.get(
                            "fromCache",
                        )}",
                    )
                } catch (e: AssistantBackendException) {
                    ScaniumLog.w(TAG, "Assist backend failure correlationId=$correlationId type=${e.failure.type}", e)
                    applyLocalFallback(trimmed, _uiState.value, e.failure, correlationId)
                } catch (e: Exception) {
                    val failure =
                        AssistantBackendFailure(
                            type = AssistantBackendErrorType.PROVIDER_UNAVAILABLE,
                            category = AssistantBackendErrorCategory.TEMPORARY,
                            retryable = true,
                            message = "Assistant request failed",
                        )
                    ScaniumLog.e(TAG, "Assist request failed correlationId=$correlationId", e)
                    applyLocalFallback(trimmed, _uiState.value, failure, correlationId)
                }
            }
        }

        /**
         * Retry the last failed message.
         */
        @Suppress("DEPRECATION")
        fun retryLastMessage() {
            val failedText = _uiState.value.lastUserMessage ?: return
            if (!_uiState.value.isOnline) {
                viewModelScope.launch {
                    _events.emit(AssistantUiEvent.ShowSnackbar("You're offline. Retry when back online."))
                }
                return
            }
            _uiState.update {
                it.copy(
                    loadingStage = LoadingStage.IDLE,
                    progress = AssistantRequestProgress.Idle,
                    failedMessageText = null,
                )
            }
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
         * Apply a vision-suggested attribute to an item.
         * @param suggestion The suggested attribute from vision analysis.
         * @param alternativeKey Optional alternative key to use (e.g., "secondaryColor" when "color" conflicts).
         */
        fun applyVisionAttribute(
            suggestion: SuggestedAttribute,
            alternativeKey: String? = null,
        ) {
            val itemId = itemIds.firstOrNull() ?: return
            val targetKey = alternativeKey ?: suggestion.key

            viewModelScope.launch {
                // Map confidence tier to float
                val confidence = when (suggestion.confidence) {
                    ConfidenceTier.HIGH -> 0.9f
                    ConfidenceTier.MED -> 0.6f
                    ConfidenceTier.LOW -> 0.3f
                }

                val attribute = ItemAttribute(
                    value = suggestion.value,
                    confidence = confidence,
                    source = suggestion.source ?: "detected",
                )

                // Update via ItemsViewModel
                itemsViewModel.updateItemAttribute(itemId, targetKey, attribute)

                // Log telemetry event
                val wasAlternative = alternativeKey != null
                ScaniumLog.i(
                    TAG,
                    "Vision attribute applied itemId=$itemId key=$targetKey value=${suggestion.value} " +
                        "source=${suggestion.source} confidence=${suggestion.confidence} wasAlternative=$wasAlternative",
                )

                val displayKey = targetKey.replaceFirstChar { it.uppercase() }
                _events.emit(AssistantUiEvent.ShowSnackbar("$displayKey updated to ${suggestion.value}"))
                refreshSnapshots()
            }
        }

        /**
         * Get an existing user-set attribute for conflict detection.
         * @param key The attribute key to check.
         * @return The existing attribute if set by user, null otherwise.
         */
        fun getExistingAttribute(key: String): ItemAttribute? {
            val itemId = itemIds.firstOrNull() ?: return null
            val item = itemsViewModel.items.value.firstOrNull { it.id == itemId } ?: return null
            val attr = item.attributes[key] ?: return null
            // Only return if it was user-set (source="user" or high confidence)
            return if (attr.source == "user" || attr.confidence >= 0.95f) attr else null
        }

        /**
         * Get the alternative key for an attribute when there's a conflict.
         * Maps: color -> secondaryColor, brand -> brand2, model -> model2
         */
        fun getAlternativeKey(key: String): String {
            return when (key.lowercase()) {
                "color" -> "secondaryColor"
                "brand" -> "brand2"
                "model" -> "model2"
                else -> "${key}2"
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
            val draftsMap = mutableMapOf<String, ListingDraft>()
            val itemsById = itemsViewModel.items.value.associateBy { it.id }
            val snapshots =
                itemIds.mapNotNull { itemId ->
                    val item = itemsById[itemId]
                    val draft =
                        draftStore.getByItemId(itemId)
                            ?: item?.let { ListingDraftBuilder.build(it) }
                    draft?.also { draftsMap[itemId] = it }
                        ?.let { ItemContextSnapshotBuilder.fromDraft(it) }
                        ?.let { snapshot -> mergeSnapshotAttributes(snapshot, item) }
                }
            val localSuggestions = localSuggestionEngine.generateSuggestions(snapshots)
            _uiState.update {
                it.copy(
                    snapshots = snapshots,
                    itemDrafts = draftsMap,
                    localSuggestions = localSuggestions,
                )
            }
        }

        private fun mergeSnapshotAttributes(
            snapshot: ItemContextSnapshot,
            item: ScannedItem?,
        ): ItemContextSnapshot {
            if (item == null) return snapshot

            val merged = linkedMapOf<String, ItemAttributeSnapshot>()

            fun keyFor(attributeKey: String) = attributeKey.lowercase()
            fun addIfMissing(attribute: ItemAttributeSnapshot) {
                val key = keyFor(attribute.key)
                if (key !in merged) {
                    merged[key] = attribute
                }
            }

            item.attributes.forEach { (key, attr) ->
                val source = if (attr.source == "user") AttributeSource.USER else AttributeSource.DETECTED
                merged[keyFor(key)] = ItemAttributeSnapshot(
                    key = key,
                    value = attr.value,
                    confidence = attr.confidence,
                    source = source,
                )
            }

            // Add localized condition
            item.condition?.let { condition ->
                addIfMissing(
                    ItemAttributeSnapshot(
                        key = "condition",
                        value = ItemLocalizer.getConditionName(context, condition),
                        confidence = 1.0f,
                        source = AttributeSource.USER,
                    ),
                )
            }

            snapshot.attributes.forEach { addIfMissing(it) }

            val vision = item.visionAttributes
            vision.primaryBrand?.let { brand ->
                addIfMissing(
                    ItemAttributeSnapshot(
                        key = "brand",
                        value = brand,
                        confidence = vision.logos.maxOfOrNull { it.score },
                        source = AttributeSource.DETECTED,
                    ),
                )
            }

            val colors = vision.colors.sortedByDescending { it.score }
            colors.firstOrNull()?.let { color ->
                addIfMissing(
                    ItemAttributeSnapshot(
                        key = "color",
                        value = color.name,
                        confidence = color.score,
                        source = AttributeSource.DETECTED,
                    ),
                )
            }

            vision.itemType?.takeIf { it.isNotBlank() }?.let { itemType ->
                addIfMissing(
                    ItemAttributeSnapshot(
                        key = "itemType",
                        value = itemType,
                        confidence = vision.labels.maxOfOrNull { it.score },
                        source = AttributeSource.DETECTED,
                    ),
                )
            }

            val labelHints = vision.labels.map { it.name }.distinct().take(3)
            if (labelHints.isNotEmpty()) {
                addIfMissing(
                    ItemAttributeSnapshot(
                        key = "labelHints",
                        value = labelHints.joinToString(", "),
                        confidence = vision.labels.maxOfOrNull { it.score },
                        source = AttributeSource.DETECTED,
                    ),
                )
            }

            val ocrText = item.recognizedText?.takeIf { it.isNotBlank() }
                ?: vision.ocrText?.takeIf { it.isNotBlank() }
            ocrText?.let { text ->
                addIfMissing(
                    ItemAttributeSnapshot(
                        key = "recognizedText",
                        value = if (text.length > 200) text.take(200) + "..." else text,
                        confidence = 0.8f,
                        source = AttributeSource.DETECTED,
                    ),
                )
            }

            return snapshot.copy(attributes = merged.values.toList())
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
         *
         * Key behavior:
         * - Preflight failure does NOT block typing or sending
         * - CLIENT_ERROR, UNAUTHORIZED show a non-blocking warning banner
         * - Chat success immediately clears any preflight warnings
         * - Only OFFLINE and NOT_CONFIGURED truly block sending
         */
        private fun updateAvailabilityFromPreflight(result: PreflightResult) {
            // Determine if this is a warning state (show banner but allow chat)
            val isWarningState = result.status in listOf(
                PreflightStatus.CLIENT_ERROR,
                PreflightStatus.UNAUTHORIZED,
                PreflightStatus.TEMPORARILY_UNAVAILABLE,
                PreflightStatus.RATE_LIMITED,
            )

            // Create warning for non-blocking display
            val warning = if (isWarningState) {
                PreflightWarning(
                    status = result.status,
                    reasonCode = result.reasonCode,
                    latencyMs = result.latencyMs,
                )
            } else {
                null
            }

            // Map preflight status to availability
            // IMPORTANT: Most failures still allow chat attempts
            val availability = when (result.status) {
                PreflightStatus.AVAILABLE -> AssistantAvailability.Available
                PreflightStatus.CHECKING -> AssistantAvailability.Checking

                PreflightStatus.UNKNOWN -> {
                    // UNKNOWN means preflight couldn't determine availability
                    // Allow chat attempt if online
                    if (_uiState.value.isOnline) {
                        ScaniumLog.i(
                            TAG,
                            "Preflight UNKNOWN (reason=${result.reasonCode}) - allowing chat attempt",
                        )
                        AssistantAvailability.Available
                    } else {
                        AssistantAvailability.Unavailable(
                            reason = UnavailableReason.OFFLINE,
                            canRetry = true,
                        )
                    }
                }

                PreflightStatus.CLIENT_ERROR -> {
                    // HTTP 400 = preflight schema issue. Does NOT mean assistant unavailable.
                    // Always allow chat attempt - real chat may work perfectly.
                    ScaniumLog.i(
                        TAG,
                        "Preflight CLIENT_ERROR (reason=${result.reasonCode}) - allowing chat attempt",
                    )
                    AssistantAvailability.Available
                }

                PreflightStatus.UNAUTHORIZED -> {
                    // HTTP 401 = API key issue. Still allow chat attempt (different auth path may work).
                    // Show warning banner but keep input enabled.
                    ScaniumLog.i(
                        TAG,
                        "Preflight UNAUTHORIZED (reason=${result.reasonCode}) - allowing chat attempt",
                    )
                    AssistantAvailability.Available
                }

                PreflightStatus.TEMPORARILY_UNAVAILABLE -> {
                    // Server error (5xx). Allow chat attempt - might be transient.
                    ScaniumLog.i(
                        TAG,
                        "Preflight TEMPORARILY_UNAVAILABLE (reason=${result.reasonCode}) - allowing chat attempt",
                    )
                    AssistantAvailability.Available
                }

                PreflightStatus.RATE_LIMITED -> {
                    // Rate limited. Allow chat attempt - actual chat may not be rate limited.
                    ScaniumLog.i(
                        TAG,
                        "Preflight RATE_LIMITED (reason=${result.reasonCode}) - allowing chat attempt",
                    )
                    AssistantAvailability.Available
                }

                // Only these truly block sending:
                PreflightStatus.OFFLINE -> AssistantAvailability.Unavailable(
                    reason = UnavailableReason.OFFLINE,
                    canRetry = true,
                )
                PreflightStatus.NOT_CONFIGURED -> AssistantAvailability.Unavailable(
                    reason = UnavailableReason.NOT_CONFIGURED,
                    canRetry = false,
                )
                PreflightStatus.ENDPOINT_NOT_FOUND -> {
                    // Endpoint not found is a config issue, but still allow typing
                    ScaniumLog.w(
                        TAG,
                        "Preflight ENDPOINT_NOT_FOUND - allowing chat attempt (may fail)",
                    )
                    AssistantAvailability.Available
                }
            }

            // Mode for the indicator
            val mode = when (result.status) {
                PreflightStatus.AVAILABLE -> AssistantMode.ONLINE
                PreflightStatus.UNKNOWN,
                PreflightStatus.CLIENT_ERROR,
                PreflightStatus.UNAUTHORIZED,
                PreflightStatus.TEMPORARILY_UNAVAILABLE,
                PreflightStatus.RATE_LIMITED,
                PreflightStatus.ENDPOINT_NOT_FOUND -> AssistantMode.ONLINE // Allow chat attempt
                PreflightStatus.OFFLINE -> AssistantMode.OFFLINE
                PreflightStatus.NOT_CONFIGURED,
                PreflightStatus.CHECKING -> AssistantMode.LIMITED
            }

            _uiState.update { current ->
                // Don't override if we're in loading state (user already sent a message)
                if (current.isLoading) {
                    current
                } else {
                    current.copy(
                        availability = availability,
                        assistantMode = mode,
                        preflightWarning = warning,
                        // Clear backend failure if preflight succeeded
                        lastBackendFailure = if (result.isAvailable) null else current.lastBackendFailure,
                    )
                }
            }

            ScaniumLog.i(
                TAG,
                "Preflight result: status=${result.status} latency=${result.latencyMs}ms " +
                    "availability=${availabilityDebugString(availability)} warning=${warning != null}",
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
         * Clears the preflight warning banner.
         * Does not affect availability state.
         */
        fun clearPreflightWarning() {
            _uiState.update { current ->
                current.copy(preflightWarning = null)
            }
            ScaniumLog.i(TAG, "Preflight warning cleared")
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

        @Suppress("DEPRECATION")
        private suspend fun applyLocalFallback(
            message: String,
            state: AssistantUiState,
            failure: AssistantBackendFailure,
            correlationId: String? = null,
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

            // Create error progress state from failure
            val errorProgress = AssistantRequestProgress.fromBackendFailure(failure, correlationId)

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
                                suggestedAttributes = response.suggestedAttributes,
                            ),
                    isLoading = false,
                    loadingStage = LoadingStage.DONE,
                    progress = errorProgress,
                    suggestedQuestions = computeSuggestedQuestions(current.snapshots),
                    lastBackendFailure = failure,
                    assistantMode = resolveMode(current.isOnline, failure),
                    availability = newAvailability,
                )
            }
            val debugReason = AssistantErrorDisplay.getDebugReason(failure)
            ScaniumLog.i(TAG, "Assistant fallback mode=${_uiState.value.assistantMode} availability=${availabilityDebugString(newAvailability)} $debugReason correlationId=$correlationId")

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
