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
                val draft = draftStore.getByItemId(activeDraftId)
                if (draft != null) {
                    snapshots.add(ItemContextSnapshotBuilder.fromDraft(draft))
                }
            } else if (selectedItemIds.isNotEmpty()) {
                val itemsList = itemsViewModel.items.value.filter { selectedItemIds.contains(it.id) }
                snapshots.addAll(itemsList.map { it.toContextSnapshot() })
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

            val currentMessages = _uiState.value.messages
            val request = AssistantPromptBuilder.buildRequest(
                items = _uiState.value.contextItems,
                conversation = currentMessages,
                userMessage = text,
                exportProfile = exportProfile
            )

            val result = assistantRepository.sendMessage(request)

            result.onSuccess { response ->
                val assistantMsg = AssistantMessage(
                    role = AssistantRole.ASSISTANT,
                    content = response.text,
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
                // Use user-friendly message from AssistantException if available
                val errorMessage = when (error) {
                    is AssistantException -> error.userMessage
                    else -> error.message ?: "Failed to get response"
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = errorMessage
                    )
                }
            }
        }
    }

    fun handleAction(action: AssistantAction) {
        when (action.type) {
            AssistantActionType.APPLY_DRAFT_UPDATE -> applyDraftUpdate(action)
            AssistantActionType.COPY_TEXT -> { /* Handled by UI clipboard */ }
            AssistantActionType.OPEN_POSTING_ASSIST -> { /* Navigation event */ }
            else -> {}
        }
    }

    private fun applyDraftUpdate(action: AssistantAction) {
        val payload = action.payload
        val itemId = payload["itemId"] ?: return
        val title = payload["title"]
        val description = payload["description"]

        viewModelScope.launch {
            // If we have an active draft, update it
            if (activeDraftId != null && activeDraftId == itemId) {
                 val draft = draftStore.getByItemId(activeDraftId)
                 if (draft != null) {
                     var newDraft = draft
                     if (title != null) newDraft = newDraft.copy(title = newDraft.title.copy(value = title))
                     if (description != null) newDraft = newDraft.copy(description = newDraft.description.copy(value = description))
                     draftStore.upsert(newDraft)
                 }
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
        title = this.displayLabel,
        description = null, // ScannedItem doesn't have description until drafted
        category = this.category.name,
        confidence = this.confidence,
        attributes = emptyList(), // TODO: extract attributes if available
        priceEstimate = this.estimatedPriceRange?.let { (it.low.amount + it.high.amount) / 2.0 },
        photosCount = if (this.fullImageUri != null || this.thumbnail != null) 1 else 0
    )
}
