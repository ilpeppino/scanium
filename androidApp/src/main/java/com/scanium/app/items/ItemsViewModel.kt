package com.scanium.app.items

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanium.app.BuildConfig
import com.scanium.app.aggregation.AggregationPresets
import com.scanium.app.aggregation.ItemAggregator
import com.scanium.app.ml.ItemCategory
import com.scanium.app.ml.PricingEngine
import com.scanium.app.ml.classification.ClassificationMode
import com.scanium.app.ml.classification.ClassificationOrchestrator
import com.scanium.app.ml.classification.ItemClassifier
import com.scanium.app.ml.classification.NoopClassifier
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
class ItemsViewModel(
    classificationMode: StateFlow<ClassificationMode> = MutableStateFlow(ClassificationMode.ON_DEVICE),
    onDeviceClassifier: ItemClassifier = NoopClassifier,
    cloudClassifier: ItemClassifier = NoopClassifier
) : ViewModel() {
    companion object {
        private const val TAG = "ItemsViewModel"
    }

    // Private mutable state
    private val _items = MutableStateFlow<List<ScannedItem>>(emptyList())

    // Public immutable state
    val items: StateFlow<List<ScannedItem>> = _items.asStateFlow()

    // Real-time item aggregator for similarity-based deduplication
    // Using REALTIME preset optimized for continuous scanning with camera movement
    private val itemAggregator = ItemAggregator(
        config = AggregationPresets.REALTIME
    )

    private val classificationOrchestrator = ClassificationOrchestrator(
        modeFlow = classificationMode,
        onDeviceClassifier = onDeviceClassifier,
        cloudClassifier = cloudClassifier,
        scope = viewModelScope
    )

    // Dynamic similarity threshold control (0.0 - 1.0)
    // Default is REALTIME preset value (0.55)
    // Higher threshold = fewer, more confident items
    // Lower threshold = more items, but possibly noisier
    //
    // Note: To add persistence, convert to AndroidViewModel and use ThresholdPreferences:
    //   private val thresholdPreferences = ThresholdPreferences(application)
    //   Then load/save threshold in init{} and updateSimilarityThreshold()
    private val _similarityThreshold = MutableStateFlow(AggregationPresets.REALTIME.similarityThreshold)
    val similarityThreshold: StateFlow<Float> = _similarityThreshold.asStateFlow()

    init {
        // Explicitly initialize the aggregator's dynamic threshold to ensure
        // it's synchronized with the ViewModel's state from the start
        val initialThreshold = AggregationPresets.REALTIME.similarityThreshold
        itemAggregator.updateSimilarityThreshold(initialThreshold)
        Log.i(TAG, "ItemsViewModel initialized with threshold: $initialThreshold")
    }

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

        // Reset classifier cache to avoid stale matches
        classificationOrchestrator.reset()

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
     * Update the similarity threshold for real-time tuning.
     *
     * This immediately affects how detections are aggregated:
     * - Higher threshold = fewer, more confident items (stricter matching)
     * - Lower threshold = more items (looser matching)
     *
     * @param threshold New threshold value (0.0 - 1.0)
     */
    fun updateSimilarityThreshold(threshold: Float) {
        val clampedThreshold = threshold.coerceIn(0f, 1f)
        val previousThreshold = _similarityThreshold.value

        _similarityThreshold.value = clampedThreshold
        itemAggregator.updateSimilarityThreshold(clampedThreshold)

        Log.w(TAG, "╔═══════════════════════════════════════════════════════════════")
        Log.w(TAG, "║ THRESHOLD UPDATED: $previousThreshold → $clampedThreshold")
        Log.w(TAG, "║ Aggregator confirms: ${itemAggregator.getCurrentSimilarityThreshold()}")
        Log.w(TAG, "╚═══════════════════════════════════════════════════════════════")
    }

    /**
     * Get the current effective similarity threshold.
     */
    fun getCurrentSimilarityThreshold(): Float {
        return itemAggregator.getCurrentSimilarityThreshold()
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

        triggerEnhancedClassification()
    }

    private fun triggerEnhancedClassification() {
        val pendingItems = itemAggregator.getAggregatedItems()
            .filter { it.thumbnail != null && classificationOrchestrator.shouldClassify(it.aggregatedId) }

        if (pendingItems.isEmpty()) return

        classificationOrchestrator.classify(pendingItems) { aggregatedItem, result ->
            val boxArea = aggregatedItem.boundingBox.width * aggregatedItem.boundingBox.height
            val priceRange = PricingEngine.generatePriceRange(result.category, boxArea)
            val categoryOverride = if (result.confidence >= aggregatedItem.maxConfidence || aggregatedItem.category == ItemCategory.UNKNOWN) {
                result.category
            } else {
                aggregatedItem.enhancedCategory ?: aggregatedItem.category
            }

            itemAggregator.applyEnhancedClassification(
                aggregatedId = aggregatedItem.aggregatedId,
                category = categoryOverride,
                label = result.label ?: aggregatedItem.labelText,
                priceRange = priceRange
            )

            // Propagate updates to the UI layer
            val updatedItems = itemAggregator.getScannedItems()
            _items.value = updatedItems

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Enhanced classification applied to ${aggregatedItem.aggregatedId} using ${result.mode}")
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
     */
    fun updateListingStatus(
        itemId: String,
        status: ItemListingStatus,
        listingId: String? = null,
        listingUrl: String? = null
    ) {
        Log.i(TAG, "Updating listing status for item $itemId: $status")

        _items.update { currentItems ->
            currentItems.map { item ->
                if (item.id == itemId) {
                    item.copy(
                        listingStatus = status,
                        listingId = listingId,
                        listingUrl = listingUrl
                    )
                } else {
                    item
                }
            }
        }
    }

    /**
     * Gets the listing status for a specific item.
     */
    fun getListingStatus(itemId: String): ItemListingStatus? {
        return _items.value.find { it.id == itemId }?.listingStatus
    }

    /**
     * Gets a specific item by ID.
     */
    fun getItem(itemId: String): ScannedItem? {
        return _items.value.find { it.id == itemId }
    }
}
