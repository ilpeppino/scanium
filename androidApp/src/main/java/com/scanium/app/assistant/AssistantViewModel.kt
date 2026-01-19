package com.scanium.app.assistant

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.items.ScannedItem
import com.scanium.app.selling.persistence.ListingDraftStore
import com.scanium.shared.core.models.assistant.AssistantAction
import com.scanium.shared.core.models.assistant.AssistantActionType
import com.scanium.shared.core.models.assistant.AssistantMessage
import com.scanium.shared.core.models.assistant.AssistantPromptBuilder
import com.scanium.shared.core.models.assistant.AssistantRole
import com.scanium.shared.core.models.assistant.ItemAttributeSnapshot
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
    val pendingActions: List<AssistantAction> = emptyList(),
)

class AssistantViewModel(
    private val assistantRepository: AssistantRepository,
    private val draftStore: ListingDraftStore,
    private val itemsViewModel: ItemsViewModel,
    private val selectedItemIds: List<String> = emptyList(),
    private val activeDraftId: String? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    init {
        loadContext()
    }

    private fun loadContext() {
        viewModelScope.launch {
            val snapshots = refreshContext()
            _uiState.update { it.copy(contextItems = snapshots) }
        }
    }

    /**
     * Refresh and return the latest context items.
     * This ensures we get the most up-to-date vision attributes,
     * which may have been applied after the assistant was opened.
     */
    private suspend fun refreshContext(): List<ItemContextSnapshot> {
        Log.w(TAG, "╔════════════════════════════════════════════════════════════════")
        Log.w(TAG, "║ ASSISTANT: refreshContext() CALLED")
        Log.w(TAG, "║ activeDraftId=$activeDraftId")
        Log.w(TAG, "║ selectedItemIds=$selectedItemIds")
        Log.w(TAG, "╚════════════════════════════════════════════════════════════════")

        val snapshots = mutableListOf<ItemContextSnapshot>()

        if (activeDraftId != null) {
            val draft = draftStore.getByItemId(activeDraftId)
            Log.w(TAG, "DIAG: Draft lookup for activeDraftId=$activeDraftId, found=${draft != null}")
            if (draft != null) {
                snapshots.add(ItemContextSnapshotBuilder.fromDraft(draft))
            }
        } else if (selectedItemIds.isNotEmpty()) {
            val allItems = itemsViewModel.items.value
            Log.w(TAG, "DIAG: Total items in itemsViewModel: ${allItems.size}")
            allItems.forEach { item ->
                Log.w(TAG, "DIAG: Item id=${item.id}, displayLabel=${item.displayLabel}, labelText=${item.labelText}")
                Log.w(TAG, "DIAG:   visionAttributes.isEmpty=${item.visionAttributes.isEmpty}")
                Log.w(TAG, "DIAG:   visionAttributes.ocrText=${item.visionAttributes.ocrText?.take(50)}")
                Log.w(TAG, "DIAG:   visionAttributes.colors=${item.visionAttributes.colors.map { it.name }}")
            }
            val itemsList = allItems.filter { selectedItemIds.contains(it.id) }
            Log.w(TAG, "DIAG: Filtered items matching selectedItemIds: ${itemsList.size}")
            snapshots.addAll(itemsList.map { it.toContextSnapshot() })
        }

        Log.w(TAG, "DIAG: Created ${snapshots.size} context snapshots")
        snapshots.forEach { snapshot ->
            Log.w(TAG, "DIAG: Snapshot title=${snapshot.title}, category=${snapshot.category}")
            Log.w(TAG, "DIAG:   attributes=${snapshot.attributes.map { "${it.key}=${it.value}" }}")
        }

        // Update UI state with fresh context
        _uiState.update { it.copy(contextItems = snapshots) }

        return snapshots
    }

    companion object {
        private const val TAG = "AssistantViewModel"
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMsg =
            AssistantMessage(
                role = AssistantRole.USER,
                content = text,
                timestamp = System.currentTimeMillis(),
            )

        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                isLoading = true,
                error = null,
            )
        }

        viewModelScope.launch {
            // Refresh context to get latest vision attributes before sending
            val freshContextItems = refreshContext()

            // Use generic profile if not specified (TODO: Load real profile)
            val exportProfile =
                ExportProfileDefinition(
                    id = ExportProfileId.GENERIC,
                    displayName = "Standard Listing",
                )

            val currentMessages = _uiState.value.messages
            val request =
                AssistantPromptBuilder.buildRequest(
                    items = freshContextItems,
                    conversation = currentMessages,
                    userMessage = text,
                    exportProfile = exportProfile,
                )

            val result = assistantRepository.sendMessage(request)

            result
                .onSuccess { response ->
                    val assistantMsg =
                        AssistantMessage(
                            role = AssistantRole.ASSISTANT,
                            content = response.text,
                            timestamp = System.currentTimeMillis(),
                        )

                    _uiState.update {
                        it.copy(
                            messages = it.messages + assistantMsg,
                            isLoading = false,
                            pendingActions = response.actions,
                        )
                    }
                }.onFailure { error ->
                    // Use user-friendly message from AssistantException if available
                    val errorMessage =
                        when (error) {
                            is AssistantException -> error.userMessage
                            else -> error.message ?: "Failed to get response"
                        }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = errorMessage,
                        )
                    }
                }
        }
    }

    fun handleAction(action: AssistantAction) {
        when (action.type) {
            AssistantActionType.APPLY_DRAFT_UPDATE -> {
                applyDraftUpdate(action)
            }

            AssistantActionType.COPY_TEXT -> { // Handled by UI clipboard
            }

            AssistantActionType.OPEN_POSTING_ASSIST -> { // Navigation event
            }

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
    // Build attributes list from all available sources
    val extractedAttributes =
        buildList {
            // 1. Add user-edited attributes (highest priority)
            this@toContextSnapshot.attributes.forEach { (key, attr) ->
                add(
                    ItemAttributeSnapshot(
                        key = key,
                        value = attr.value,
                        confidence = attr.confidence,
                    ),
                )
            }

            // 2. Add vision attributes if not already present from user edits
            val existingKeys =
                this@toContextSnapshot
                    .attributes.keys
                    .map { it.lowercase() }
                    .toSet()

            // Brand from vision
            this@toContextSnapshot.visionAttributes.primaryBrand?.let { brand ->
                if ("brand" !in existingKeys) {
                    add(
                        ItemAttributeSnapshot(
                            key = "brand",
                            value = brand,
                            confidence = this@toContextSnapshot.visionAttributes.logos.maxOfOrNull { it.score },
                        ),
                    )
                }
            }

            // Colors from vision (include all unique colors)
            val visionColors = this@toContextSnapshot.visionAttributes.colors
            if (visionColors.isNotEmpty() && "color" !in existingKeys && "colors" !in existingKeys) {
                // Join all color names for the assistant context
                val colorNames = visionColors.map { it.name }.distinct().joinToString(", ")
                val avgConfidence = visionColors.mapNotNull { it.score }.average().toFloat()
                add(
                    ItemAttributeSnapshot(
                        key = "colors",
                        value = colorNames,
                        confidence = if (avgConfidence.isNaN()) null else avgConfidence,
                    ),
                )
            }

            // OCR text (condensed for assistant context)
            this@toContextSnapshot.visionAttributes.ocrText?.takeIf { it.isNotBlank() }?.let { ocr ->
                if ("recognizedText" !in existingKeys && "ocr" !in existingKeys) {
                    // Limit OCR text to first 200 chars for assistant context
                    val condensedOcr = if (ocr.length > 200) ocr.take(200) + "..." else ocr
                    add(
                        ItemAttributeSnapshot(
                            key = "recognizedText",
                            value = condensedOcr,
                            confidence = 0.8f, // OCR is generally reliable
                        ),
                    )
                }
            }

            // Add recognized text from item itself if present
            this@toContextSnapshot.recognizedText?.takeIf { it.isNotBlank() }?.let { text ->
                if ("recognizedText" !in existingKeys && "ocr" !in existingKeys &&
                    this@toContextSnapshot.visionAttributes.ocrText.isNullOrBlank()
                ) {
                    val condensed = if (text.length > 200) text.take(200) + "..." else text
                    add(
                        ItemAttributeSnapshot(
                            key = "recognizedText",
                            value = condensed,
                            confidence = 0.8f,
                        ),
                    )
                }
            }

            // Condition if set
            this@toContextSnapshot.condition?.let { condition ->
                if ("condition" !in existingKeys) {
                    add(
                        ItemAttributeSnapshot(
                            key = "condition",
                            value = condition.displayName,
                            confidence = null,
                        ),
                    )
                }
            }
        }

    return ItemContextSnapshot(
        itemId = this.id,
        title = this.displayLabel,
        description = null, // ScannedItem doesn't have description until drafted
        category = this.category.name,
        confidence = this.confidence,
        attributes = extractedAttributes,
        priceEstimate = this.estimatedPriceRange?.let { (it.low.amount + it.high.amount) / 2.0 },
        photosCount = if (this.fullImageUri != null || this.thumbnail != null) 1 else 0,
    )
}
