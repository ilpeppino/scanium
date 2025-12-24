package com.scanium.app.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.items.ScannedItem
import com.scanium.app.listing.ListingDraft
import com.scanium.app.selling.persistence.ListingDraftStore
import com.scanium.shared.core.models.assistant.AssistantAction
import com.scanium.shared.core.models.assistant.AssistantActionType
import com.scanium.shared.core.models.assistant.AssistantMessage
import com.scanium.shared.core.models.assistant.AssistantPromptBuilder
import com.scanium.shared.core.models.assistant.AssistantRole
import com.scanium.shared.core.models.assistant.ItemContextSnapshot
import com.scanium.shared.core.models.assistant.ItemContextSnapshotBuilder
import com.scanium.shared.core.models.listing.ExportProfileDefinition
import com.scanium.shared.core.models.listing.ExportProfileId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssistantUiState(
    val messages: List<AssistantMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val contextItems: List<ItemContextSnapshot> = emptyList(),
    val pendingActions: List<AssistantAction> = emptyList()
)

class AssistantViewModel(
    private val assistantRepository: AssistantRepository,
    private val draftStore: ListingDraftStore,
    private val itemsViewModel: ItemsViewModel,
    private val selectedItemIds: List<String> = emptyList(),
    private val activeDraftId: String? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    init {
        loadContext()
    }

    private fun loadContext() {
        viewModelScope.launch {
            val snapshots = mutableListOf<ItemContextSnapshot>()

            if (activeDraftId != null) {
                val draft = draftStore.getDraft(activeDraftId)
                if (draft != null) {
                    snapshots.add(ItemContextSnapshotBuilder.fromDraft(draft))
                }
            } else if (selectedItemIds.isNotEmpty()) {
                val items = itemsViewModel.items.value.filter { selectedItemIds.contains(it.id) }
                snapshots.addAll(items.map { it.toContextSnapshot() })
            }

            _uiState.update { it.copy(contextItems = snapshots) }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMsg = AssistantMessage(
            role = AssistantRole.USER,
            content = text,
            timestamp = System.currentTimeMillis()
        )

        _uiState.update { 
            it.copy(
                messages = it.messages + userMsg,
                isLoading = true,
                error = null
            )
        }

        viewModelScope.launch {
            // Use generic profile if not specified (TODO: Load real profile)
            val exportProfile = ExportProfileDefinition(
                id = ExportProfileId.GENERIC,
                displayName = "Standard Listing"
            )

            val request = AssistantPromptBuilder.buildRequest(
                items = _uiState.value.contextItems,
                conversation = _uiState.value.messages,
                userMessage = text,
                exportProfile = exportProfile
            )

            val result = assistantRepository.sendMessage(request)

            result.onSuccess { response ->
                val assistantMsg = AssistantMessage(
                    role = AssistantRole.ASSISTANT,
                    content = response.content,
                    timestamp = System.currentTimeMillis()
                )
                
                _uiState.update {
                    it.copy(
                        messages = it.messages + assistantMsg,
                        isLoading = false,
                        pendingActions = response.actions
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to get response"
                    )
                }
            }
        }
    }

    fun handleAction(action: AssistantAction) {
        when (action.type) {
            AssistantActionType.APPLY_DRAFT_UPDATE -> applyDraftUpdate(action.payload)
            AssistantActionType.COPY_TEXT -> { /* Handled by UI clipboard */ }
            AssistantActionType.OPEN_POSTING_ASSIST -> { /* Navigation event */ }
            else -> {}
        }
    }

    private fun applyDraftUpdate(payload: Map<String, String>) {
        val itemId = payload["itemId"] ?: return
        val title = payload["title"]
        val description = payload["description"]

        viewModelScope.launch {
            // If we have an active draft, update it
            if (activeDraftId != null && activeDraftId == itemId) { // itemId in payload is usually draftId/itemId
                 draftStore.updateDraft(activeDraftId) { draft ->
                     var newDraft = draft
                     if (title != null) newDraft = newDraft.copy(title = newDraft.title.copy(value = title))
                     if (description != null) newDraft = newDraft.copy(description = newDraft.description.copy(value = description))
                     newDraft
                 }
            } else {
                // If scanned items, we might need to update item state or create draft
                // For now, let's assume we update the ScannedItem via ItemsViewModel if possible, 
                // or just ignore if strictly draft-only.
                // But Assistant usually returns draft updates.
                // If we are in "Items List", we don't have a draft.
                // We could create a draft?
                // For now, let's just log or toast "Draft not created yet" if no draft.
            }
            
            // Clear the action from pending
            _uiState.update { state ->
                state.copy(pendingActions = state.pendingActions.filterNot { it == action })
            }
        }
    }
}

private fun ScannedItem.toContextSnapshot(): ItemContextSnapshot {
    return ItemContextSnapshot(
        itemId = this.id,
        title = this.enhancedLabelText ?: this.labelText,
        description = null, // ScannedItem doesn't have description until drafted
        category = this.enhancedCategory?.name ?: this.category.name,
        confidence = this.classificationConfidence ?: this.confidence,
        attributes = emptyList(), // TODO: extract attributes if available
        priceEstimate = this.enhancedPriceRange?.let { (it.first + it.second) / 2 } 
            ?: this.estimatedPriceRange?.let { (it.min + it.max) / 2 },
        photosCount = if (this.fullImageUri != null || this.thumbnail != null) 1 else 0
    )
}
