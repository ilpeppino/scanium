package com.scanium.app.items.listing

import android.util.Log
import com.scanium.app.items.ItemListingStatus
import com.scanium.app.items.ScannedItem
import com.scanium.app.items.persistence.ScannedItemStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Manages listing status for scanned items.
 *
 * This class is responsible for:
 * - Tracking eBay listing status for items
 * - Persisting listing status updates
 * - Providing listing status queries
 *
 * ***REMOVED******REMOVED*** Thread Safety
 * Uses StateFlow for thread-safe state updates.
 * Persistence operations run on the worker dispatcher.
 *
 * @param scope Coroutine scope for async operations
 * @param itemsStore Persistence layer for items
 * @param workerDispatcher Background dispatcher for persistence
 */
class ListingStatusManager(
    private val scope: CoroutineScope,
    private val itemsStore: ScannedItemStore,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    companion object {
        private const val TAG = "ListingStatusManager"
    }

    // Items state (shared reference to avoid duplication)
    private val _items = MutableStateFlow<List<ScannedItem>>(emptyList())

    /**
     * Set the items reference for status updates.
     * This should be called by ItemsViewModel to sync state.
     */
    fun setItemsReference(itemsFlow: StateFlow<List<ScannedItem>>) {
        scope.launch {
            itemsFlow.collect { items ->
                _items.value = items
            }
        }
    }

    /**
     * Updates the listing status of a scanned item.
     *
     * Called by ListingViewModel after posting to eBay.
     *
     * @param itemId The ID of the scanned item
     * @param status The new listing status
     * @param listingId Optional eBay listing ID
     * @param listingUrl Optional URL to view the listing
     * @return Updated list of items with the status change applied
     */
    fun updateListingStatus(
        itemId: String,
        status: ItemListingStatus,
        listingId: String? = null,
        listingUrl: String? = null,
    ): List<ScannedItem> {
        Log.i(TAG, "Updating listing status for item $itemId: $status")

        val updatedItems =
            _items.value.map { item ->
                if (item.id == itemId) {
                    item.copy(
                        listingStatus = status,
                        listingId = listingId,
                        listingUrl = listingUrl,
                    )
                } else {
                    item
                }
            }

        _items.value = updatedItems
        persistItems(updatedItems)

        return updatedItems
    }

    /**
     * Gets the listing status for a specific item.
     */
    fun getListingStatus(itemId: String): ItemListingStatus? {
        return _items.value.find { it.id == itemId }?.listingStatus
    }

    /**
     * Gets items with a specific listing status.
     */
    fun getItemsByStatus(status: ItemListingStatus): List<ScannedItem> {
        return _items.value.filter { it.listingStatus == status }
    }

    /**
     * Gets all items that have been listed.
     */
    fun getListedItems(): List<ScannedItem> {
        return _items.value.filter { it.listingStatus == ItemListingStatus.LISTED_ACTIVE }
    }

    /**
     * Gets all items that are pending listing.
     */
    fun getPendingItems(): List<ScannedItem> {
        return _items.value.filter { it.listingStatus == ItemListingStatus.LISTING_IN_PROGRESS }
    }

    /**
     * Gets all items that failed to list.
     */
    fun getFailedItems(): List<ScannedItem> {
        return _items.value.filter { it.listingStatus == ItemListingStatus.LISTING_FAILED }
    }

    /**
     * Marks an item as listing in progress.
     */
    fun markListingInProgress(itemId: String): List<ScannedItem> {
        return updateListingStatus(itemId, ItemListingStatus.LISTING_IN_PROGRESS)
    }

    /**
     * Marks an item as successfully listed.
     */
    fun markListingSuccess(
        itemId: String,
        listingId: String,
        listingUrl: String? = null,
    ): List<ScannedItem> {
        return updateListingStatus(
            itemId = itemId,
            status = ItemListingStatus.LISTED_ACTIVE,
            listingId = listingId,
            listingUrl = listingUrl,
        )
    }

    /**
     * Marks an item as failed to list.
     */
    fun markListingFailed(itemId: String): List<ScannedItem> {
        return updateListingStatus(itemId, ItemListingStatus.LISTING_FAILED)
    }

    /**
     * Resets the listing status for an item.
     */
    fun resetListingStatus(itemId: String): List<ScannedItem> {
        return updateListingStatus(itemId, ItemListingStatus.NOT_LISTED)
    }

    // ==================== Internal Methods ====================

    private fun persistItems(items: List<ScannedItem>) {
        scope.launch(workerDispatcher) {
            itemsStore.upsertAll(items)
        }
    }
}
