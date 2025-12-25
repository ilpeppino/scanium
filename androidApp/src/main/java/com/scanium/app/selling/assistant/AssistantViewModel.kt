package com.scanium.app.selling.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scanium.app.data.ExportProfilePreferences
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.listing.DraftField
import com.scanium.app.listing.DraftFieldKey
import com.scanium.app.listing.DraftProvenance
import com.scanium.app.listing.ExportProfileDefinition
import com.scanium.app.listing.ExportProfiles
import com.scanium.app.listing.ListingDraft
import com.scanium.app.listing.ListingDraftBuilder
import com.scanium.app.model.AssistantAction
import com.scanium.app.model.AssistantActionType
import com.scanium.app.model.AssistantMessage
import com.scanium.app.model.AssistantRole
import com.scanium.app.model.ConfidenceTier
import com.scanium.app.model.EvidenceBullet
import com.scanium.app.model.ItemContextSnapshot
import com.scanium.app.model.ItemContextSnapshotBuilder
import com.scanium.app.data.SettingsRepository
import com.scanium.app.platform.ConnectivityStatus
import com.scanium.app.platform.ConnectivityStatusProvider
import com.scanium.app.selling.persistence.ListingDraftStore
import com.scanium.app.logging.CorrelationIds
import com.scanium.app.logging.ScaniumLog
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
    val errorMessage: String? = null
)

/**
 * Loading stage for staged responses.
 */
enum class LoadingStage {
    IDLE,
    VISION_PROCESSING,
    LLM_PROCESSING,
    DONE,
    ERROR
}

enum class AssistantMode {
    ONLINE,
    OFFLINE,
    LIMITED
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
    val lastBackendFailure: AssistantBackendFailure? = null
)

sealed class AssistantUiEvent {
    data class ShowSnackbar(val message: String) : AssistantUiEvent()
}

class AssistantViewModel(
    private val itemIds: List<String>,
    private val itemsViewModel: ItemsViewModel,
    private val draftStore: ListingDraftStore,
    private val exportProfileRepository: com.scanium.app.listing.ExportProfileRepository,
    private val exportProfilePreferences: ExportProfilePreferences,
    private val assistantRepository: AssistantRepository,
    private val settingsRepository: SettingsRepository,
    private val localAssistantHelper: LocalAssistantHelper,
    private val connectivityStatusProvider: ConnectivityStatusProvider
) : ViewModel() {
    private val _uiState = MutableStateFlow(AssistantUiState(itemIds = itemIds))
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AssistantUiEvent>()
    val events = _events.asSharedFlow()

    init {
        loadProfileAndSnapshots()
        observeConnectivity()
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        val correlationId = CorrelationIds.newAssistRequestId()

        val userMessage = AssistantMessage(
            role = AssistantRole.USER,
            content = trimmed,
            timestamp = System.currentTimeMillis(),
            itemContextIds = itemIds
        )

        _uiState.update { state ->
            state.copy(
                entries = state.entries + AssistantChatEntry(userMessage),
                isLoading = true,
                loadingStage = LoadingStage.VISION_PROCESSING,
                failedMessageText = null,
                lastUserMessage = trimmed
            )
        }

        viewModelScope.launch {
            val state = _uiState.value

            ScaniumLog.i(
                TAG,
                "Assist request correlationId=$correlationId items=${state.snapshots.size} messageLength=${trimmed.length}"
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
                        message = "Offline"
                    )
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
                val response = assistantRepository.send(
                    items = state.snapshots,
                    history = state.entries.map { it.message },
                    userMessage = trimmed,
                    exportProfile = state.profile,
                    correlationId = correlationId,
                    imageAttachments = emptyList(), // Explicit: no images sent
                    assistantPrefs = prefs
                )

                val assistantMessage = AssistantMessage(
                    role = AssistantRole.ASSISTANT,
                    content = response.text,
                    timestamp = System.currentTimeMillis(),
                    itemContextIds = itemIds
                )

                _uiState.update { current ->
                    current.copy(
                        entries = current.entries + AssistantChatEntry(
                            message = assistantMessage,
                            actions = response.actions,
                            confidenceTier = response.confidenceTier,
                            evidence = response.evidence,
                            suggestedNextPhoto = response.suggestedNextPhoto
                        ),
                        isLoading = false,
                        loadingStage = LoadingStage.DONE,
                        suggestedQuestions = computeSuggestedQuestions(current.snapshots),
                        lastBackendFailure = null,
                        assistantMode = resolveMode(current.isOnline, null)
                    )
                }

                ScaniumLog.i(
                    TAG,
                    "Assist response correlationId=$correlationId actions=${response.actions.size} fromCache=${response.citationsMetadata?.get("fromCache")}"
                )
            } catch (e: AssistantBackendException) {
                ScaniumLog.w(TAG, "Assist backend failure correlationId=$correlationId type=${e.failure.type}", e)
                applyLocalFallback(trimmed, _uiState.value, e.failure)
            } catch (e: Exception) {
                val failure = AssistantBackendFailure(
                    type = AssistantBackendErrorType.PROVIDER_UNAVAILABLE,
                    category = AssistantBackendErrorCategory.TEMPORARY,
                    retryable = true,
                    message = "Assistant request failed"
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

    private fun defaultSuggestions(): List<String> = listOf(
        "Suggest a better title",
        "What details should I add?",
        "Estimate price range"
    )

    fun applyDraftUpdate(action: AssistantAction) {
        if (action.type != AssistantActionType.APPLY_DRAFT_UPDATE) return
        val payload = action.payload
        val itemId = payload["itemId"] ?: itemIds.firstOrNull() ?: return
        val correlationId = CorrelationIds.newDraftRequestId()

        viewModelScope.launch {
            val draft = draftStore.getByItemId(itemId)
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
                "Draft update applied correlationId=$correlationId itemId=$itemId"
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
            val draft = draftStore.getByItemId(itemId)
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
                "Attributes added correlationId=$correlationId itemId=$itemId"
            )
            _events.emit(AssistantUiEvent.ShowSnackbar("Attributes added"))
            refreshSnapshots()
        }
    }

    private fun loadProfileAndSnapshots() {
        viewModelScope.launch {
            val profiles = exportProfileRepository.getProfiles().ifEmpty { listOf(ExportProfiles.generic()) }
            val defaultId = exportProfileRepository.getDefaultProfileId()
            val persistedId = exportProfilePreferences.getLastProfileId(defaultId)
            val profile = profiles.firstOrNull { it.id == persistedId }
                ?: profiles.firstOrNull { it.id == defaultId }
                ?: ExportProfiles.generic()
            _uiState.update { it.copy(profile = profile) }
            refreshSnapshots()
        }
    }

    private suspend fun refreshSnapshots() {
        val snapshots = itemIds.mapNotNull { itemId ->
            val draft = draftStore.getByItemId(itemId)
                ?: itemsViewModel.items.value.firstOrNull { it.id == itemId }
                    ?.let { ListingDraftBuilder.build(it) }
            draft?.let { ItemContextSnapshotBuilder.fromDraft(it) }
        }
        _uiState.update { it.copy(snapshots = snapshots) }
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityStatusProvider.statusFlow.collect { status ->
                val online = status == ConnectivityStatus.ONLINE
                _uiState.update { current ->
                    current.copy(
                        isOnline = online,
                        assistantMode = resolveMode(online, current.lastBackendFailure)
                    )
                }
                ScaniumLog.i(TAG, "Assistant connectivity status=$status")
            }
        }
    }

    private fun resolveMode(
        isOnline: Boolean,
        failure: AssistantBackendFailure?
    ): AssistantMode {
        return if (!isOnline) {
            AssistantMode.OFFLINE
        } else if (failure != null) {
            AssistantMode.LIMITED
        } else {
            AssistantMode.ONLINE
        }
    }

    private suspend fun applyLocalFallback(
        message: String,
        state: AssistantUiState,
        failure: AssistantBackendFailure
    ) {
        val response = localAssistantHelper.buildResponse(
            items = state.snapshots,
            userMessage = message,
            failure = failure
        )
        val assistantMessage = AssistantMessage(
            role = AssistantRole.ASSISTANT,
            content = response.text,
            timestamp = System.currentTimeMillis(),
            itemContextIds = itemIds
        )
        _uiState.update { current ->
            current.copy(
                entries = current.entries + AssistantChatEntry(
                    message = assistantMessage,
                    actions = response.actions,
                    confidenceTier = response.confidenceTier,
                    evidence = response.evidence,
                    suggestedNextPhoto = response.suggestedNextPhoto
                ),
                isLoading = false,
                loadingStage = LoadingStage.DONE,
                suggestedQuestions = computeSuggestedQuestions(current.snapshots),
                lastBackendFailure = failure,
                assistantMode = resolveMode(current.isOnline, failure)
            )
        }
        ScaniumLog.i(TAG, "Assistant fallback mode=${_uiState.value.assistantMode} type=${failure.type}")
        _events.emit(
            AssistantUiEvent.ShowSnackbar("Using limited offline assistance. Retry online when ready.")
        )
    }

    private fun updateDraftFromPayload(draft: ListingDraft, payload: Map<String, String>): ListingDraft {
        var updated = draft
        val now = System.currentTimeMillis()

        payload["title"]?.let { title ->
            updated = updated.copy(
                title = DraftField(title, confidence = 1f, source = DraftProvenance.USER_EDITED),
                updatedAt = now
            )
        }

        payload["description"]?.let { description ->
            updated = updated.copy(
                description = DraftField(description, confidence = 1f, source = DraftProvenance.USER_EDITED),
                updatedAt = now
            )
        }

        payload["price"]?.toDoubleOrNull()?.let { price ->
            updated = updated.copy(
                price = DraftField(price, confidence = 1f, source = DraftProvenance.USER_EDITED),
                updatedAt = now
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

        fun factory(
            itemIds: List<String>,
            itemsViewModel: ItemsViewModel,
            draftStore: ListingDraftStore,
            exportProfileRepository: com.scanium.app.listing.ExportProfileRepository,
            exportProfilePreferences: ExportProfilePreferences,
            assistantRepository: AssistantRepository,
            settingsRepository: SettingsRepository,
            localAssistantHelper: LocalAssistantHelper,
            connectivityStatusProvider: ConnectivityStatusProvider
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AssistantViewModel(
                    itemIds = itemIds,
                    itemsViewModel = itemsViewModel,
                    draftStore = draftStore,
                    exportProfileRepository = exportProfileRepository,
                    exportProfilePreferences = exportProfilePreferences,
                    assistantRepository = assistantRepository,
                    settingsRepository = settingsRepository,
                    localAssistantHelper = localAssistantHelper,
                    connectivityStatusProvider = connectivityStatusProvider
                ) as T
            }
        }
    }
}
