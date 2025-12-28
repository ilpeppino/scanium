package com.scanium.app.selling.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scanium.app.data.ExportProfilePreferences
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.listing.DraftProvenance
import com.scanium.app.listing.DraftStatus
import com.scanium.app.listing.ListingDraft
import com.scanium.app.listing.ListingDraftBuilder
import com.scanium.app.listing.ExportProfileDefinition
import com.scanium.app.listing.ExportProfileId
import com.scanium.app.listing.ExportProfileRepository
import com.scanium.app.listing.ExportProfiles
import com.scanium.app.selling.persistence.ListingDraftStore
import com.scanium.app.logging.CorrelationIds
import com.scanium.app.logging.ScaniumLog
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DraftReviewUiState(
    val itemIds: List<String> = emptyList(),
    val currentIndex: Int = 0,
    val draft: ListingDraft? = null,
    val profiles: List<ExportProfileDefinition> = emptyList(),
    val selectedProfileId: ExportProfileId = ExportProfileId.GENERIC,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val errorMessage: String? = null
) {
    val totalCount: Int
        get() = itemIds.size

    val currentItemId: String?
        get() = itemIds.getOrNull(currentIndex)
}

data class DraftReviewItemState(
    val draft: ListingDraft,
    val isDirty: Boolean
)

/**
 * ViewModel for the Draft Review screen that manages listing draft editing.
 *
 * Part of ARCH-001/DX-003: Migrated to Hilt assisted injection to reduce boilerplate.
 * Runtime parameters (itemIds, itemsViewModel) are passed via @Assisted annotation,
 * while singleton dependencies are injected by Hilt.
 */
class DraftReviewViewModel @AssistedInject constructor(
    @Assisted private val itemIds: List<String>,
    @Assisted private val itemsViewModel: ItemsViewModel,
    private val draftStore: ListingDraftStore,
    private val exportProfileRepository: ExportProfileRepository,
    private val exportProfilePreferences: ExportProfilePreferences
) : ViewModel() {

    /**
     * Factory for creating DraftReviewViewModel instances with assisted injection.
     * Part of ARCH-001/DX-003: Replaces verbose manual Factory class.
     */
    @AssistedFactory
    interface Factory {
        fun create(
            itemIds: List<String>,
            itemsViewModel: ItemsViewModel
        ): DraftReviewViewModel
    }

    private val draftCache = mutableMapOf<String, DraftReviewItemState>()
    private val _uiState = MutableStateFlow(
        DraftReviewUiState(
            itemIds = itemIds,
            currentIndex = 0
        )
    )
    val uiState: StateFlow<DraftReviewUiState> = _uiState.asStateFlow()

    init {
        loadDraftForCurrent()
        loadProfiles()
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
        viewModelScope.launch {
            saveCurrentDraft()
        }
    }

    fun selectProfile(profileId: ExportProfileId) {
        _uiState.update { it.copy(selectedProfileId = profileId) }
        updateDraft { draft -> draft.copy(profile = profileId) }
        viewModelScope.launch {
            exportProfilePreferences.setLastProfileId(profileId)
        }
    }

    fun goToPrevious() {
        val state = _uiState.value
        if (state.currentIndex <= 0) return
        navigateToIndex(state.currentIndex - 1)
    }

    fun goToNext() {
        val state = _uiState.value
        if (state.currentIndex >= state.totalCount - 1) return
        navigateToIndex(state.currentIndex + 1)
    }

    private fun navigateToIndex(targetIndex: Int) {
        viewModelScope.launch {
            saveCurrentDraft()
            _uiState.update { it.copy(currentIndex = targetIndex, errorMessage = null) }
            loadDraftForCurrent()
        }
    }

    private suspend fun saveCurrentDraft() {
        val state = _uiState.value
        val currentId = state.currentItemId ?: return
        val currentDraft = state.draft ?: return

        if (!state.isDirty) return

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        val updated = currentDraft
            .copy(status = DraftStatus.SAVED, updatedAt = System.currentTimeMillis())
            .recomputeCompleteness()
        draftStore.upsert(updated)
        val correlationId = CorrelationIds.newDraftRequestId()
        ScaniumLog.i(
            "DraftReview",
            "Draft saved correlationId=$correlationId itemId=$currentId fields=${updated.fields.size}"
        )
        draftCache[currentId] = DraftReviewItemState(updated, isDirty = false)
        _uiState.update {
            it.copy(draft = updated, isSaving = false, isDirty = false)
        }
    }

    private fun loadDraftForCurrent() {
        val state = _uiState.value
        val currentId = state.currentItemId ?: return
        draftCache[currentId]?.let { cached ->
            _uiState.update {
                it.copy(
                    draft = cached.draft,
                    isDirty = cached.isDirty,
                    isSaving = false,
                    errorMessage = null
                )
            }
            return
        }

        viewModelScope.launch {
            val savedDraft = draftStore.getByItemId(currentId)
            if (savedDraft != null) {
                draftCache[currentId] = DraftReviewItemState(savedDraft, isDirty = false)
                _uiState.update {
                    it.copy(draft = savedDraft, isDirty = false, isSaving = false, errorMessage = null)
                }
                return@launch
            }

            val item = itemsViewModel.items.value.firstOrNull { it.id == currentId }
            val newDraft = item?.let { ListingDraftBuilder.build(it) }
            if (newDraft != null) {
                draftCache[currentId] = DraftReviewItemState(newDraft, isDirty = false)
            }
            _uiState.update {
                it.copy(draft = newDraft, isDirty = false, isSaving = false, errorMessage = null)
            }
        }
    }

    private fun loadProfiles() {
        viewModelScope.launch {
            val profiles = exportProfileRepository.getProfiles().ifEmpty {
                listOf(ExportProfiles.generic())
            }
            val defaultId = exportProfileRepository.getDefaultProfileId()
            val persistedId = exportProfilePreferences.getLastProfileId(defaultId)
            val selectedId = profiles.firstOrNull { it.id == persistedId }?.id
                ?: profiles.firstOrNull { it.id == defaultId }?.id
                ?: ExportProfiles.generic().id
            _uiState.update {
                it.copy(profiles = profiles, selectedProfileId = selectedId)
            }
        }
    }

    private fun updateDraft(transformer: (ListingDraft) -> ListingDraft) {
        _uiState.update { state ->
            val draft = state.draft ?: return@update state
            val updated = transformer(draft).copy(updatedAt = System.currentTimeMillis()).recomputeCompleteness()
            val currentId = state.currentItemId
            if (currentId != null) {
                draftCache[currentId] = DraftReviewItemState(updated, isDirty = true)
            }
            state.copy(draft = updated, isDirty = true)
        }
    }

    companion object {
        /**
         * Creates a ViewModelProvider.Factory for DraftReviewViewModel using Hilt's assisted factory.
         * Part of ARCH-001/DX-003: Simplified factory creation with Hilt.
         *
         * @param assistedFactory The Hilt-generated assisted factory
         * @param itemIds The list of item IDs to review
         * @param itemsViewModel The shared ItemsViewModel instance
         */
        fun provideFactory(
            assistedFactory: Factory,
            itemIds: List<String>,
            itemsViewModel: ItemsViewModel
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
