package com.example.objecta.items

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
 */
class ItemsViewModel : ViewModel() {

    // Private mutable state
    private val _items = MutableStateFlow<List<ScannedItem>>(emptyList())

    // Public immutable state
    val items: StateFlow<List<ScannedItem>> = _items.asStateFlow()

    // Track IDs we've already seen to avoid duplicates during scanning
    private val seenIds = mutableSetOf<String>()

    /**
     * Adds a single detected item to the list.
     * Deduplicates based on item ID (tracking ID from ML Kit).
     */
    fun addItem(item: ScannedItem) {
        if (!seenIds.contains(item.id)) {
            _items.update { currentItems ->
                currentItems + item
            }
            seenIds.add(item.id)
        }
    }

    /**
     * Adds multiple detected items at once.
     * Used when processing a frame that detects multiple objects.
     * Deduplicates both against existing items and within the new batch.
     */
    fun addItems(newItems: List<ScannedItem>) {
        // Deduplicate within the new batch by taking the first occurrence of each ID
        val deduplicatedNewItems = newItems.distinctBy { it.id }

        // Filter out items already seen
        val uniqueItems = deduplicatedNewItems.filter { !seenIds.contains(it.id) }

        if (uniqueItems.isNotEmpty()) {
            _items.update { currentItems ->
                currentItems + uniqueItems
            }
            seenIds.addAll(uniqueItems.map { it.id })
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
    }

    /**
     * Clears all detected items.
     */
    fun clearAllItems() {
        _items.value = emptyList()
        seenIds.clear()
    }

    /**
     * Returns the current count of detected items.
     */
    fun getItemCount(): Int = _items.value.size
}
