package com.scanium.app.items

import android.util.Log
import androidx.lifecycle.ViewModel
import com.scanium.app.aggregation.AggregationConfig
import com.scanium.app.aggregation.AggregationPresets
import com.scanium.app.aggregation.ItemAggregator
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
 * Now uses real-time item aggregation to merge similar detections into persistent
 * AggregatedItems. This replaces the previous strict deduplication logic that
 * failed when ML Kit tracking IDs changed frequently.
 *
 * Key improvements:
 * - Resilient to trackingId changes
 * - Weighted similarity scoring
 * - Configurable thresholds
 * - Always produces items (no "no items" failure mode)
 */
class ItemsViewModel : ViewModel() {
    companion object {
        private const val TAG = "ItemsViewModel"
    }

    // Private mutable state
    private val _items = MutableStateFlow<List<ScannedItem>>(emptyList())

    // Public immutable state
    val items: StateFlow<List<ScannedItem>> = _items.asStateFlow()

    // Real-time item aggregator (replaces SessionDeduplicator)
    // Using REALTIME preset optimized for continuous scanning with camera movement
    private val itemAggregator = ItemAggregator(
        config = AggregationPresets.REALTIME
    )

    /**
     * Adds a single detected item to the list.
     *
     * Processes the item through the aggregator, which either:
     * - Merges it into an existing aggregated item
     * - Creates a new aggregated item
     *
     * Then updates the UI state with all current aggregated items.
     */
    fun addItem(item: ScannedItem) {
        Log.i(TAG, ">>> addItem: id=${item.id}, category=${item.category}, confidence=${item.confidence}")

        // Process through aggregator
        val aggregatedItem = itemAggregator.processDetection(item)

        // Update UI state with all aggregated items
        updateItemsState()

        Log.i(TAG, "    Processed item ${item.id} → aggregated ${aggregatedItem.aggregatedId} (mergeCount=${aggregatedItem.mergeCount})")
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

        // Log memory before processing
        val runtime = Runtime.getRuntime()
        val usedMemoryBefore = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        Log.w(TAG, ">>> MEMORY BEFORE AGGREGATION: ${usedMemoryBefore}MB used, ${runtime.maxMemory() / 1024 / 1024}MB max")

        // Process all items through aggregator
        val aggregatedItems = itemAggregator.processDetections(newItems)

        // Update UI state with all aggregated items
        updateItemsState()

        // Log statistics
        val stats = itemAggregator.getStats()
        Log.i(TAG, ">>> AGGREGATION COMPLETE:")
        Log.i(TAG, "    - Input detections: ${newItems.size}")
        Log.i(TAG, "    - Aggregated items: ${stats.totalItems}")
        Log.i(TAG, "    - Total merges: ${stats.totalMerges}")
        Log.i(TAG, "    - Avg merges/item: ${"%.2f".format(stats.averageMergesPerItem)}")

        // Log memory after processing
        val usedMemoryAfter = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        Log.w(TAG, ">>> MEMORY AFTER AGGREGATION: ${usedMemoryAfter}MB used, delta=${usedMemoryAfter - usedMemoryBefore}MB")
    }

    /**
     * Removes a specific item by ID.
     *
     * Note: itemId is now an aggregatedId from the AggregatedItem.
     */
    fun removeItem(itemId: String) {
        Log.i(TAG, "Removing item: $itemId")

        // Remove from aggregator
        itemAggregator.removeItem(itemId)

        // Update UI state
        updateItemsState()
    }

    /**
     * Clears all detected items.
     */
    fun clearAllItems() {
        Log.i(TAG, "Clearing all items")

        // Reset aggregator
        itemAggregator.reset()

        // Update UI state
        _items.value = emptyList()
    }

    /**
     * Returns the current count of detected items.
     */
    fun getItemCount(): Int = _items.value.size

    /**
     * Get aggregation statistics for monitoring/debugging.
     */
    fun getAggregationStats() = itemAggregator.getStats()

    /**
     * Remove stale items that haven't been seen recently.
     *
     * This can be called periodically to clean up old items from a long scanning session.
     *
     * @param maxAgeMs Maximum age in milliseconds (default: 30 seconds)
     */
    fun removeStaleItems(maxAgeMs: Long = 30_000L) {
        val removed = itemAggregator.removeStaleItems(maxAgeMs)
        if (removed > 0) {
            Log.i(TAG, "Removed $removed stale items")
            updateItemsState()
        }
    }

    /**
     * Update the UI state with current aggregated items.
     *
     * Converts AggregatedItems to ScannedItems for UI compatibility.
     */
    private fun updateItemsState() {
        val scannedItems = itemAggregator.getScannedItems()
        _items.value = scannedItems
        Log.d(TAG, "Updated UI state: ${scannedItems.size} items")
    }
}
