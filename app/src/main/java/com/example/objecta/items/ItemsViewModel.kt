package com.example.objecta.items

import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel for managing detected items across the app.
 *
 * Shared between CameraScreen and ItemsListScreen.
 * Maintains a list of detected items and provides operations to add/remove them.
 *
 * Now includes session-level de-duplication to handle cases where the same
 * physical object is detected with different tracking IDs.
 */
class ItemsViewModel : ViewModel() {
    companion object {
        private const val TAG = "ItemsViewModel"
    }

    // Private mutable state
    private val _items = MutableStateFlow<List<ScannedItem>>(emptyList())

    // Public immutable state
    val items: StateFlow<List<ScannedItem>> = _items.asStateFlow()

    // Track IDs we've already seen to avoid duplicates during scanning
    private val seenIds = mutableSetOf<String>()

    // Session-level de-duplicator for similarity-based matching
    private val sessionDeduplicator = SessionDeduplicator()

    /**
     * Adds a single detected item to the list.
     * Performs both ID-based and similarity-based de-duplication.
     */
    fun addItem(item: ScannedItem) {
        // First check: exact ID match
        if (seenIds.contains(item.id)) {
            Log.d(TAG, "Skipping duplicate ID: ${item.id}")
            return
        }

        // Second check: similarity-based de-duplication
        val currentItems = _items.value
        val similarItemId = sessionDeduplicator.findSimilarItem(item, currentItems)

        if (similarItemId != null) {
            Log.i(TAG, "Skipping similar item: new ${item.id} is similar to existing $similarItemId")
            // Mark this ID as seen even though we didn't add it
            // This prevents the same detection from being re-evaluated multiple times
            seenIds.add(item.id)
            return
        }

        // Item is unique - add it
        Log.i(TAG, "Adding new item: ${item.id} (${item.category})")
        _items.update { currentItems ->
            currentItems + item
        }
        seenIds.add(item.id)
    }

    /**
     * Adds multiple detected items at once.
     * Used when processing a frame that detects multiple objects.
     * Performs de-duplication both against existing items and within the new batch.
     */
    fun addItems(newItems: List<ScannedItem>) {
        Log.i(TAG, ">>> addItems CALLED: received ${newItems.size} items")
        if (newItems.isEmpty()) {
            Log.i(TAG, ">>> addItems: empty list, returning")
            return
        }

        newItems.forEachIndexed { index, item ->
            Log.i(TAG, "    Input item $index: id=${item.id}, category=${item.category}, priceRange=${item.priceRange}")
        }

        // Deduplicate within the new batch by taking the first occurrence of each ID
        val deduplicatedNewItems = newItems.distinctBy { it.id }
        Log.i(TAG, ">>> After deduplication within batch: ${deduplicatedNewItems.size} items")

        // Filter using both ID-based and similarity-based de-duplication
        val uniqueItems = mutableListOf<ScannedItem>()
        val currentItems = _items.value
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

        // Add all unique items at once
        if (uniqueItems.isNotEmpty()) {
            Log.i(TAG, ">>> ADDING ${uniqueItems.size} unique items from batch of ${newItems.size}")

            // Log memory before adding items
            val runtime = Runtime.getRuntime()
            val usedMemoryBefore = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
            Log.w(TAG, ">>> MEMORY BEFORE ADD: ${usedMemoryBefore}MB used, ${runtime.maxMemory() / 1024 / 1024}MB max")

            _items.update { currentItems ->
                currentItems + uniqueItems
            }
            seenIds.addAll(uniqueItems.map { it.id })

            // Log memory after adding items
            val usedMemoryAfter = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
            Log.w(TAG, ">>> MEMORY AFTER ADD: ${usedMemoryAfter}MB used, added ${usedMemoryAfter - usedMemoryBefore}MB")
            Log.i(TAG, ">>> Total items now: ${_items.value.size}")
        } else {
            Log.i(TAG, ">>> NO unique items in batch of ${newItems.size}")
        }
    }

    /**
     * Removes a specific item by ID.
     */
    fun removeItem(itemId: String) {
        _items.update { currentItems ->
            currentItems.filterNot { it.id == itemId }
        }
        seenIds.remove(itemId)
        sessionDeduplicator.removeItem(itemId)
    }

    /**
     * Clears all detected items.
     */
    fun clearAllItems() {
        Log.i(TAG, "Clearing all items")
        _items.value = emptyList()
        seenIds.clear()
        sessionDeduplicator.reset()
    }

    /**
     * Returns the current count of detected items.
     */
    fun getItemCount(): Int = _items.value.size
}
