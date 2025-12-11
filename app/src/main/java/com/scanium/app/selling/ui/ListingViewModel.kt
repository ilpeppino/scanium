package com.scanium.app.selling.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanium.app.items.ScannedItem
import com.scanium.app.selling.data.EbayMarketplaceService
import com.scanium.app.selling.data.ListingCreationResult
import com.scanium.app.selling.domain.Listing
import com.scanium.app.selling.domain.ListingCondition
import com.scanium.app.selling.domain.ListingDraft
import com.scanium.app.selling.domain.ListingError
import com.scanium.app.selling.util.ListingDraftMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PostingStatus { IDLE, POSTING, SUCCESS, FAILURE }

data class ListingDraftState(
    val draft: ListingDraft,
    val status: PostingStatus = PostingStatus.IDLE,
    val listing: Listing? = null,
    val error: ListingError? = null
)

data class ListingUiState(
    val drafts: List<ListingDraftState> = emptyList(),
    val isPosting: Boolean = false
)

class ListingViewModel(
    private val selectedItems: List<ScannedItem>,
    private val marketplaceService: EbayMarketplaceService
) : ViewModel() {
    private val draftsById = selectedItems.associateBy { it.id }
    private val _uiState = MutableStateFlow(
        ListingUiState(
            drafts = selectedItems.map { item ->
                ListingDraftState(draft = ListingDraftMapper.fromScannedItem(item))
            }
        )
    )
    val uiState: StateFlow<ListingUiState> = _uiState.asStateFlow()

    fun updateDraftTitle(itemId: String, title: String) {
        updateDraft(itemId) { it.copy(draft = it.draft.copy(title = title)) }
    }

    fun updateDraftPrice(itemId: String, priceText: String) {
        val price = priceText.toDoubleOrNull() ?: return
        updateDraft(itemId) { it.copy(draft = it.draft.copy(price = price)) }
    }

    fun updateDraftCondition(itemId: String, condition: ListingCondition) {
        updateDraft(itemId) { it.copy(draft = it.draft.copy(condition = condition)) }
    }

    fun postSelectedToEbay() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPosting = true)
            val updatedDrafts = _uiState.value.drafts.map { it.copy(status = PostingStatus.POSTING, error = null) }
            _uiState.value = _uiState.value.copy(drafts = updatedDrafts)

            val results = updatedDrafts.map { draftState ->
                val item = draftsById[draftState.draft.scannedItemId] ?: return@map draftState.copy(
                    status = PostingStatus.FAILURE,
                    error = ListingError.VALIDATION_ERROR
                )
                when (val result = marketplaceService.createListingForItem(item)) {
                    is ListingCreationResult.Success -> {
                        draftState.copy(
                            status = PostingStatus.SUCCESS,
                            listing = result.listing
                        )
                    }
                    is ListingCreationResult.Error -> {
                        draftState.copy(
                            status = PostingStatus.FAILURE,
                            error = result.error
                        )
                    }
                }
            }
            _uiState.value = _uiState.value.copy(
                drafts = results,
                isPosting = false
            )
        }
    }

    private fun updateDraft(itemId: String, transformer: (ListingDraftState) -> ListingDraftState) {
        _uiState.value = _uiState.value.copy(
            drafts = _uiState.value.drafts.map { state ->
                if (state.draft.scannedItemId == itemId) transformer(state) else state
            }
        )
    }
}
