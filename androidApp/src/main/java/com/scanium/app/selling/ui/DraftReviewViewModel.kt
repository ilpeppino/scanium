package com.scanium.app.selling.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.listing.DraftProvenance
import com.scanium.app.listing.DraftStatus
import com.scanium.app.listing.ListingDraft
import com.scanium.app.listing.ListingDraftBuilder
import com.scanium.app.selling.persistence.ListingDraftStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DraftReviewUiState(
    val draft: ListingDraft? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

class DraftReviewViewModel(
    private val itemId: String,
    private val itemsViewModel: ItemsViewModel,
    private val draftStore: ListingDraftStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(DraftReviewUiState())
    val uiState: StateFlow<DraftReviewUiState> = _uiState.asStateFlow()

    init {
        loadDraft()
    }

    fun updateTitle(value: String) {
        updateDraft { draft ->
            draft.copy(title = draft.title.copy(value = value, source = DraftProvenance.USER_EDITED))
        }
    }

    fun updateDescription(value: String) {
        updateDraft { draft ->
            draft.copy(description = draft.description.copy(value = value, source = DraftProvenance.USER_EDITED))
        }
    }

    fun updateCondition(value: String) {
        updateDraft { draft ->
            val fields = draft.fields.toMutableMap()
            fields[com.scanium.app.listing.DraftFieldKey.CONDITION] = draft.fields[com.scanium.app.listing.DraftFieldKey.CONDITION]
                ?.copy(value = value, source = DraftProvenance.USER_EDITED)
                ?: com.scanium.app.listing.DraftField(value = value, confidence = 1f, source = DraftProvenance.USER_EDITED)
            draft.copy(fields = fields)
        }
    }

    fun updatePrice(value: String) {
        val parsed = value.toDoubleOrNull() ?: return
        updateDraft { draft ->
            draft.copy(price = draft.price.copy(value = parsed, source = DraftProvenance.USER_EDITED))
        }
    }

    fun saveDraft() {
        val current = _uiState.value.draft ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val updated = current.copy(status = DraftStatus.SAVED, updatedAt = System.currentTimeMillis()).recomputeCompleteness()
            draftStore.upsert(updated)
            _uiState.value = DraftReviewUiState(draft = updated, isSaving = false)
        }
    }

    private fun loadDraft() {
        viewModelScope.launch {
            val savedDraft = draftStore.getByItemId(itemId)
            if (savedDraft != null) {
                _uiState.value = DraftReviewUiState(draft = savedDraft)
                return@launch
            }

            val item = itemsViewModel.items.value.firstOrNull { it.id == itemId }
            val newDraft = item?.let { ListingDraftBuilder.build(it) }
            _uiState.value = DraftReviewUiState(draft = newDraft)
        }
    }

    private fun updateDraft(transformer: (ListingDraft) -> ListingDraft) {
        _uiState.update { state ->
            val draft = state.draft ?: return@update state
            val updated = transformer(draft).copy(updatedAt = System.currentTimeMillis()).recomputeCompleteness()
            DraftReviewUiState(draft = updated, isSaving = state.isSaving, errorMessage = state.errorMessage)
        }
    }

    companion object {
        fun factory(
            itemId: String,
            itemsViewModel: ItemsViewModel,
            draftStore: ListingDraftStore
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return DraftReviewViewModel(
                    itemId = itemId,
                    itemsViewModel = itemsViewModel,
                    draftStore = draftStore
                ) as T
            }
        }
    }
}
