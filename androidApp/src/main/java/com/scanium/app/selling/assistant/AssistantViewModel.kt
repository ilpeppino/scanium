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
import com.scanium.app.model.ItemContextSnapshot
import com.scanium.app.model.ItemContextSnapshotBuilder
import com.scanium.app.selling.persistence.ListingDraftStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssistantChatEntry(
    val message: AssistantMessage,
    val actions: List<AssistantAction> = emptyList()
)

data class AssistantUiState(
    val itemIds: List<String> = emptyList(),
    val snapshots: List<ItemContextSnapshot> = emptyList(),
    val profile: ExportProfileDefinition = ExportProfiles.generic(),
    val entries: List<AssistantChatEntry> = emptyList(),
    val isLoading: Boolean = false
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
    private val assistantRepository: AssistantRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AssistantUiState(itemIds = itemIds))
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AssistantUiEvent>()
    val events = _events.asSharedFlow()

    init {
        loadProfileAndSnapshots()
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        val userMessage = AssistantMessage(
            role = AssistantRole.USER,
            content = trimmed,
            timestamp = System.currentTimeMillis(),
            itemContextIds = itemIds
        )

        _uiState.update { state ->
            state.copy(entries = state.entries + AssistantChatEntry(userMessage), isLoading = true)
        }

        viewModelScope.launch {
            val state = _uiState.value
            val response = assistantRepository.send(
                items = state.snapshots,
                history = state.entries.map { it.message },
                userMessage = trimmed,
                exportProfile = state.profile
            )
            val assistantMessage = AssistantMessage(
                role = AssistantRole.ASSISTANT,
                content = response.content,
                timestamp = System.currentTimeMillis(),
                itemContextIds = itemIds
            )
            _uiState.update { current ->
                current.copy(
                    entries = current.entries + AssistantChatEntry(assistantMessage, response.actions),
                    isLoading = false
                )
            }
        }
    }

    fun applyDraftUpdate(action: AssistantAction) {
        if (action.type != AssistantActionType.APPLY_DRAFT_UPDATE) return
        val payload = action.payload
        val itemId = payload["itemId"] ?: itemIds.firstOrNull() ?: return

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
            _events.emit(AssistantUiEvent.ShowSnackbar("Draft updated"))
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
        fun factory(
            itemIds: List<String>,
            itemsViewModel: ItemsViewModel,
            draftStore: ListingDraftStore,
            exportProfileRepository: com.scanium.app.listing.ExportProfileRepository,
            exportProfilePreferences: ExportProfilePreferences,
            assistantRepository: AssistantRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AssistantViewModel(
                    itemIds = itemIds,
                    itemsViewModel = itemsViewModel,
                    draftStore = draftStore,
                    exportProfileRepository = exportProfileRepository,
                    exportProfilePreferences = exportProfilePreferences,
                    assistantRepository = assistantRepository
                ) as T
            }
        }
    }
}
