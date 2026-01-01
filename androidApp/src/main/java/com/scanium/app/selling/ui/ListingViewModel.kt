package com.scanium.app.selling.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scanium.app.items.ItemListingStatus
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.items.ScannedItem
import com.scanium.app.selling.data.EbayMarketplaceService
import com.scanium.app.selling.data.ListingCreationResult
import com.scanium.app.selling.domain.Listing
import com.scanium.app.selling.domain.ListingCondition
import com.scanium.app.selling.domain.ListingDraft
import com.scanium.app.selling.domain.ListingError
import com.scanium.app.selling.util.ListingDraftMapper
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PostingStatus { IDLE, POSTING, SUCCESS, FAILURE }

data class ListingDraftState(
    val draft: ListingDraft,
    val status: PostingStatus = PostingStatus.IDLE,
    val listing: Listing? = null,
    val error: ListingError? = null,
    val errorMessage: String? = null,
)

data class ListingUiState(
    val drafts: List<ListingDraftState> = emptyList(),
    val isPosting: Boolean = false,
)

/**
 * ViewModel for managing listing drafts and posting to eBay.
 *
 * Part of ARCH-001/DX-003: Migrated to Hilt assisted injection to reduce boilerplate.
 * Runtime parameters (selectedItems, itemsViewModel) are passed via @Assisted annotation,
 * while singleton dependencies are injected by Hilt.
 */
class ListingViewModel
    @AssistedInject
    constructor(
        @Assisted private val selectedItems: List<ScannedItem>,
        @Assisted private val itemsViewModel: ItemsViewModel,
        private val marketplaceService: EbayMarketplaceService,
    ) : ViewModel() {
        /**
         * Factory for creating ListingViewModel instances with assisted injection.
         * Part of ARCH-001/DX-003: Replaces verbose manual Factory class.
         */
        @AssistedFactory
        interface Factory {
            fun create(
                selectedItems: List<ScannedItem>,
                itemsViewModel: ItemsViewModel,
            ): ListingViewModel
        }

        companion object {
            private const val TAG = "ListingViewModel"

            /**
             * Creates a ViewModelProvider.Factory for ListingViewModel using Hilt's assisted factory.
             * Part of ARCH-001/DX-003: Simplified factory creation with Hilt.
             *
             * @param assistedFactory The Hilt-generated assisted factory
             * @param selectedItems The list of items to create listings for
             * @param itemsViewModel The shared ItemsViewModel instance
             */
            fun provideFactory(
                assistedFactory: Factory,
                selectedItems: List<ScannedItem>,
                itemsViewModel: ItemsViewModel,
            ): ViewModelProvider.Factory {
                return object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return assistedFactory.create(selectedItems, itemsViewModel) as T
                    }
                }
            }
        }

        private val _uiState =
            MutableStateFlow(
                ListingUiState(
                    drafts =
                        selectedItems.map { item ->
                            ListingDraftState(draft = ListingDraftMapper.fromScannedItem(item))
                        },
                ),
            )
        val uiState: StateFlow<ListingUiState> = _uiState.asStateFlow()

        fun updateDraftTitle(
            itemId: String,
            title: String,
        ) {
            updateDraft(itemId) { it.copy(draft = it.draft.copy(title = title)) }
        }

        fun updateDraftPrice(
            itemId: String,
            priceText: String,
        ) {
            val price = priceText.toDoubleOrNull() ?: return
            updateDraft(itemId) { it.copy(draft = it.draft.copy(price = price)) }
        }

        fun updateDraftCondition(
            itemId: String,
            condition: ListingCondition,
        ) {
            updateDraft(itemId) { it.copy(draft = it.draft.copy(condition = condition)) }
        }

        /**
         * Posts all selected items to eBay.
         *
         * For each item:
         * 1. Updates status to POSTING (in both this VM and ItemsViewModel)
         * 2. Calls marketplace service to create listing
         * 3. Updates status to SUCCESS or FAILURE
         * 4. Updates ItemsViewModel with final status and listing details
         */
        fun postSelectedToEbay() {
            viewModelScope.launch {
                Log.i(TAG, "══════════════════════════════════════════════════════════")
                Log.i(TAG, "Starting batch listing for ${_uiState.value.drafts.size} items")

                _uiState.value = _uiState.value.copy(isPosting = true)
                val updatedDrafts =
                    _uiState.value.drafts.map {
                        it.copy(status = PostingStatus.POSTING, error = null, errorMessage = null)
                    }
                _uiState.value = _uiState.value.copy(drafts = updatedDrafts)

                // Update ItemsViewModel: all items are now in progress
                updatedDrafts.forEach { draftState ->
                    itemsViewModel.updateListingStatus(
                        itemId = draftState.draft.scannedItemId,
                        status = ItemListingStatus.LISTING_IN_PROGRESS,
                    )
                }

                // Process each item sequentially (could be parallelized if needed)
                val results =
                    updatedDrafts.map { draftState ->
                        val draft = draftState.draft
                        val itemId = draft.scannedItemId

                        Log.i(TAG, "Posting item: $itemId")

                        when (val result = marketplaceService.createListingForDraft(draft)) {
                            is ListingCreationResult.Success -> {
                                val listing = result.listing
                                Log.i(TAG, "✓ Success: ${listing.listingId.value}")

                                // Update ItemsViewModel with success status
                                itemsViewModel.updateListingStatus(
                                    itemId = itemId,
                                    status = ItemListingStatus.LISTED_ACTIVE,
                                    listingId = listing.listingId.value,
                                    listingUrl = listing.externalUrl,
                                )

                                draftState.copy(
                                    status = PostingStatus.SUCCESS,
                                    listing = listing,
                                )
                            }
                            is ListingCreationResult.Error -> {
                                Log.e(TAG, "✗ Failed: ${result.error} - ${result.message}")

                                // Update ItemsViewModel with failure status
                                itemsViewModel.updateListingStatus(
                                    itemId = itemId,
                                    status = ItemListingStatus.LISTING_FAILED,
                                )

                                draftState.copy(
                                    status = PostingStatus.FAILURE,
                                    error = result.error,
                                    errorMessage = result.message,
                                )
                            }
                        }
                    }

                _uiState.value =
                    _uiState.value.copy(
                        drafts = results,
                        isPosting = false,
                    )

                val successCount = results.count { it.status == PostingStatus.SUCCESS }
                val failureCount = results.count { it.status == PostingStatus.FAILURE }
                Log.i(TAG, "Batch complete: $successCount success, $failureCount failed")
                Log.i(TAG, "══════════════════════════════════════════════════════════")

                // Cleanup old images after posting
                marketplaceService.cleanupOldImages()
            }
        }

        private fun updateDraft(
            itemId: String,
            transformer: (ListingDraftState) -> ListingDraftState,
        ) {
            _uiState.value =
                _uiState.value.copy(
                    drafts =
                        _uiState.value.drafts.map { state ->
                            if (state.draft.scannedItemId == itemId) transformer(state) else state
                        },
                )
        }
    }
