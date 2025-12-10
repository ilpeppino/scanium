package com.scanium.app.items

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanium.app.data.ItemsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for managing detected items across the app.
 *
 * Shared between CameraScreen and ItemsListScreen.
 * Uses ItemsRepository for persistence and provides in-memory de-duplication
 * to avoid adding duplicates during scanning sessions.
 *
 * Architecture:
 * - Repository handles persistence (Room database)
 * - ViewModel handles in-memory de-duplication (seenIds, SessionDeduplicator)
 * - UI observes the repository's Flow for reactive updates
 *
 * @param repository Repository for persistent item storage
 */
class ItemsViewModel(
    private val repository: ItemsRepository
) : ViewModel() {
    companion object {
        private const val TAG = "ItemsViewModel"
    }

    // Observe items from repository and convert to StateFlow
    // SharingStarted.WhileSubscribed keeps the flow active while there are subscribers
    val items: StateFlow<List<ScannedItem>> = repository.observeItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Track IDs we've already seen to avoid duplicates during scanning session
    private val seenIds = mutableSetOf<String>()

    // Session-level de-duplicator for similarity-based matching
    private val sessionDeduplicator = SessionDeduplicator()

    // Error state (optional, for future error handling in UI)
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Adds a single detected item to the repository.
     * Performs both ID-based and similarity-based de-duplication.
     */
    fun addItem(item: ScannedItem) {
        Log.i(TAG, ">>> addItem: id=${item.id}, category=${item.category}, confidence=${item.confidence}")

        // Second check: similarity-based de-duplication
        val currentItems = items.value
        val similarItemId = sessionDeduplicator.findSimilarItem(item, currentItems)

        // Update UI state with all aggregated items
        updateItemsState()

        // Item is unique - add it to repository
        Log.i(TAG, "Adding new item: ${item.id} (${item.category})")
        viewModelScope.launch {
            val result = repository.addItem(item)
            if (result.isSuccess()) {
                seenIds.add(item.id)
            } else {
                val error = (result as com.scanium.app.data.Result.Failure).error
                Log.e(TAG, "Failed to add item: ${error.message}", error.cause)
                _error.value = error.getUserMessage()
            }
        }
    }

    /**
     * Adds multiple detected items at once.
     *
     * Processes each item through the aggregator in batch, which automatically
     * handles merging and deduplication. This is more efficient than calling
     * addItem() multiple times.
     */
    fun addItems(newItems: List<ScannedItem>) {
        Log.i(TAG, ">>> addItems BATCH: received ${newItems.size} items")
        if (newItems.isEmpty()) {
            Log.i(TAG, ">>> addItems: empty list, returning")
            return
        }

        newItems.forEachIndexed { index, item ->
            Log.i(TAG, "    Input item $index: id=${item.id}, category=${item.category}, confidence=${item.confidence}")
        }

        // Deduplicate within the new batch by taking the first occurrence of each ID
        val deduplicatedNewItems = newItems.distinctBy { it.id }
        Log.i(TAG, ">>> After deduplication within batch: ${deduplicatedNewItems.size} items")

        // Filter using both ID-based and similarity-based de-duplication
        val uniqueItems = mutableListOf<ScannedItem>()
        val currentItems = items.value
        Log.i(TAG, ">>> Current items in ViewModel: ${currentItems.size}")

        for ((index, item) in deduplicatedNewItems.withIndex()) {
            Log.i(TAG, "    Evaluating item $index: ${item.id}")

            // Check 1: ID already seen?
            if (seenIds.contains(item.id)) {
                Log.i(TAG, "    REJECTED: duplicate ID ${item.id}")
                continue
            }

            // Check 2: Similar to existing item?
            val similarItemId = sessionDeduplicator.findSimilarItem(item, currentItems + uniqueItems)
            if (similarItemId != null) {
                Log.i(TAG, "    REJECTED: similar to existing item $similarItemId")
                seenIds.add(item.id) // Mark as seen to avoid re-evaluation
                continue
            }

            // Item is unique
            Log.i(TAG, "    ACCEPTED: unique item ${item.id}")
            uniqueItems.add(item)
        }

        // Add all unique items to repository at once
        if (uniqueItems.isNotEmpty()) {
            Log.i(TAG, ">>> ADDING ${uniqueItems.size} unique items from batch of ${newItems.size}")

            // Log memory before adding items
            val runtime = Runtime.getRuntime()
            val usedMemoryBefore = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
            Log.w(TAG, ">>> MEMORY BEFORE ADD: ${usedMemoryBefore}MB used, ${runtime.maxMemory() / 1024 / 1024}MB max")

            viewModelScope.launch {
                val result = repository.addItems(uniqueItems)
                if (result.isSuccess()) {
                    seenIds.addAll(uniqueItems.map { it.id })

                    // Log memory after adding items
                    val usedMemoryAfter = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                    Log.w(TAG, ">>> MEMORY AFTER ADD: ${usedMemoryAfter}MB used, added ${usedMemoryAfter - usedMemoryBefore}MB")
                    Log.i(TAG, ">>> Total items now: ${items.value.size}")
                } else {
                    val error = (result as com.scanium.app.data.Result.Failure).error
                    Log.e(TAG, "Failed to add items: ${error.message}", error.cause)
                    _error.value = error.getUserMessage()
                }
            }
        } else {
            Log.i(TAG, ">>> NO unique items in batch of ${newItems.size}")
        }
    }

    /**
     * Removes a specific item by ID from the repository.
     */
    fun removeItem(itemId: String) {
        viewModelScope.launch {
            val result = repository.removeItem(itemId)
            if (result.isSuccess()) {
                seenIds.remove(itemId)
                sessionDeduplicator.removeItem(itemId)
            } else {
                val error = (result as com.scanium.app.data.Result.Failure).error
                Log.e(TAG, "Failed to remove item: ${error.message}", error.cause)
                _error.value = error.getUserMessage()
            }
        }
    }

    /**
     * Clears all detected items from the repository.
     */
    fun clearAllItems() {
        Log.i(TAG, "Clearing all items")
        viewModelScope.launch {
            val result = repository.clearAll()
            if (result.isSuccess()) {
                seenIds.clear()
                sessionDeduplicator.reset()
            } else {
                val error = (result as com.scanium.app.data.Result.Failure).error
                Log.e(TAG, "Failed to clear items: ${error.message}", error.cause)
                _error.value = error.getUserMessage()
            }
        }
    }

    /**
     * Returns the current count of detected items.
     */
    fun getItemCount(): Int = items.value.size

    /**
     * Clears any error message.
     */
    fun clearError() {
        _error.value = null
    }
}
