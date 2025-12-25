package com.scanium.app.items.state

import android.util.Log
import com.scanium.app.aggregation.AggregatedItem
import com.scanium.app.aggregation.AggregationConfig
import com.scanium.app.aggregation.AggregationPresets
import com.scanium.app.aggregation.AggregationStats
import com.scanium.app.aggregation.ItemAggregator
import com.scanium.app.items.ScannedItem
import com.scanium.app.items.ThumbnailCache
import com.scanium.app.items.persistence.NoopScannedItemStore
import com.scanium.app.items.persistence.ScannedItemStore
import com.scanium.shared.core.models.model.ImageRef
import com.scanium.shared.core.models.pricing.PriceEstimationStatus
import com.scanium.shared.core.models.pricing.PriceRange
import com.scanium.shared.core.models.ml.ItemCategory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages the state of scanned items including aggregation, persistence, and telemetry.
 *
 * This class is responsible for:
 * - StateFlow management for items
 * - CRUD operations (add, remove, clear)
 * - Persistence coordination
 * - Telemetry collection
 * - Similarity threshold management
 *
 * ## Thread Safety
 * All state modifications are performed on the worker dispatcher to prevent UI jank.
 * The ItemAggregator uses synchronized methods for thread-safe access.
 *
 * @param scope Coroutine scope for async operations
 * @param itemsStore Persistence layer for items
 * @param workerDispatcher Background dispatcher for heavy operations
 * @param mainDispatcher Main dispatcher for UI updates
 * @param aggregationConfig Configuration for the item aggregator
 */
class ItemsStateManager(
    private val scope: CoroutineScope,
    private val itemsStore: ScannedItemStore = NoopScannedItemStore,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    aggregationConfig: AggregationConfig = AggregationPresets.REALTIME
) {
    companion object {
        private const val TAG = "ItemsStateManager"
        private const val DEBUG_LOGGING = false
        private const val TELEMETRY_INTERVAL_MS = 5000L
    }

    // Real-time item aggregator for similarity-based deduplication
    private val itemAggregator = ItemAggregator(config = aggregationConfig)

    // Private mutable state
    private val _items = MutableStateFlow<List<ScannedItem>>(emptyList())
    private val _itemAddedEvents = MutableSharedFlow<ScannedItem>(extraBufferCapacity = 10)

    // Public immutable state
    val items: StateFlow<List<ScannedItem>> = _items.asStateFlow()
    val itemAddedEvents = _itemAddedEvents.asSharedFlow()

    // Dynamic similarity threshold control
    private val _similarityThreshold = MutableStateFlow(aggregationConfig.similarityThreshold)
    val similarityThreshold: StateFlow<Float> = _similarityThreshold.asStateFlow()

    // Telemetry
    private var telemetryJob: Job? = null
    private val _telemetryEnabled = MutableStateFlow(false)

    // Callback for state changes (used to notify observers)
    private var onStateChanged: (() -> Unit)? = null

    init {
        // Explicitly initialize the aggregator's dynamic threshold
        itemAggregator.updateSimilarityThreshold(aggregationConfig.similarityThreshold)
        Log.i(TAG, "ItemsStateManager initialized with threshold: ${aggregationConfig.similarityThreshold}")
        loadPersistedItems()
    }

    /**
     * Set a callback to be invoked when items state changes.
     * Used by ItemsViewModel to trigger dependent operations like classification.
     */
    fun setOnStateChangedListener(listener: (() -> Unit)?) {
        onStateChanged = listener
    }

    /**
     * Adds a single detected item.
     * Processes the item through the aggregator which handles merging.
     */
    fun addItem(item: ScannedItem) {
        scope.launch(workerDispatcher) {
            if (DEBUG_LOGGING) {
                Log.i(TAG, ">>> addItem: id=${item.id}, category=${item.category}, confidence=${item.confidence}")
            }

            val aggregatedItem = itemAggregator.processDetection(item)

            if (DEBUG_LOGGING) {
                Log.i(TAG, "    Processed item ${item.id} → aggregated ${aggregatedItem.aggregatedId}")
            }

            withContext(mainDispatcher) {
                updateItemsState()
            }
        }
    }

    /**
     * Adds multiple detected items at once.
     * More efficient than calling addItem() multiple times.
     */
    fun addItems(newItems: List<ScannedItem>) {
        if (newItems.isEmpty()) return

        scope.launch(workerDispatcher) {
            if (DEBUG_LOGGING) {
                Log.i(TAG, ">>> Processing batch: ${newItems.size} items")
            }

            itemAggregator.processDetections(newItems)

            withContext(mainDispatcher) {
                updateItemsState()
            }
        }
    }

    /**
     * Removes a specific item by ID.
     */
    fun removeItem(itemId: String) {
        Log.i(TAG, "Removing item: $itemId")
        ThumbnailCache.remove(itemId)
        itemAggregator.removeItem(itemId)
        updateItemsState(notifyNewItems = false)
    }

    /**
     * Restores a previously removed item.
     */
    fun restoreItem(item: ScannedItem) {
        Log.i(TAG, "Restoring item: ${item.id}")
        itemAggregator.processDetection(item)
        updateItemsState(notifyNewItems = false)
    }

    /**
     * Clears all detected items.
     */
    fun clearAllItems() {
        Log.i(TAG, "Clearing all items")
        ThumbnailCache.clear()
        itemAggregator.reset()
        _items.value = emptyList()

        scope.launch(workerDispatcher) {
            itemsStore.deleteAll()
        }
    }

    /**
     * Returns the current count of detected items.
     */
    fun getItemCount(): Int = _items.value.size

    /**
     * Get aggregation statistics for monitoring/debugging.
     */
    fun getAggregationStats(): AggregationStats = itemAggregator.getStats()

    /**
     * Get all aggregated items (for classification and overlay rendering).
     */
    fun getAggregatedItems(): List<AggregatedItem> = itemAggregator.getAggregatedItems()

    /**
     * Get scanned items (converted from aggregated items).
     */
    fun getScannedItems(): List<ScannedItem> = itemAggregator.getScannedItems()

    /**
     * Gets a specific item by ID.
     */
    fun getItem(itemId: String): ScannedItem? {
        return _items.value.find { it.id == itemId }
    }

    /**
     * Seed the aggregator from persisted items (used on startup).
     */
    fun seedFromScannedItems(items: List<ScannedItem>) {
        itemAggregator.seedFromScannedItems(items)
    }

    /**
     * Remove stale items that haven't been seen recently.
     *
     * @param maxAgeMs Maximum age in milliseconds (default: 30 seconds)
     */
    fun removeStaleItems(maxAgeMs: Long = 30_000L) {
        val removed = itemAggregator.removeStaleItems(maxAgeMs)
        if (removed > 0) {
            Log.i(TAG, "Removed $removed stale items")
            updateItemsState(notifyNewItems = false)
        }
    }

    /**
     * Update the similarity threshold for real-time tuning.
     */
    fun updateSimilarityThreshold(threshold: Float) {
        val clampedThreshold = threshold.coerceIn(0f, 1f)
        val previousThreshold = _similarityThreshold.value

        _similarityThreshold.value = clampedThreshold
        itemAggregator.updateSimilarityThreshold(clampedThreshold)

        if (DEBUG_LOGGING) {
            Log.w(TAG, "╔═══════════════════════════════════════════════════════════════")
            Log.w(TAG, "║ THRESHOLD UPDATED: $previousThreshold → $clampedThreshold")
            Log.w(TAG, "╚═══════════════════════════════════════════════════════════════")
        }
    }

    /**
     * Get the current effective similarity threshold.
     */
    fun getCurrentSimilarityThreshold(): Float {
        return itemAggregator.getCurrentSimilarityThreshold()
    }

    // ==================== Aggregator Delegation Methods ====================

    /**
     * Apply enhanced classification results to an aggregated item.
     */
    fun applyEnhancedClassification(
        aggregatedId: String,
        category: ItemCategory?,
        label: String?,
        priceRange: Pair<Double, Double>?,
        classificationConfidence: Float? = null
    ) {
        itemAggregator.applyEnhancedClassification(
            aggregatedId = aggregatedId,
            category = category,
            label = label,
            priceRange = priceRange,
            classificationConfidence = classificationConfidence
        )
    }

    /**
     * Update classification status for an aggregated item.
     */
    fun updateClassificationStatus(
        aggregatedId: String,
        status: String,
        domainCategoryId: String? = null,
        errorMessage: String? = null,
        requestId: String? = null
    ) {
        itemAggregator.updateClassificationStatus(
            aggregatedId = aggregatedId,
            status = status,
            domainCategoryId = domainCategoryId,
            errorMessage = errorMessage,
            requestId = requestId
        )
    }

    /**
     * Update price estimation for an aggregated item.
     */
    fun updatePriceEstimation(
        aggregatedId: String,
        status: PriceEstimationStatus,
        priceRange: PriceRange? = null
    ) {
        itemAggregator.updatePriceEstimation(aggregatedId, status, priceRange)
    }

    /**
     * Mark items as pending classification.
     */
    fun markClassificationPending(aggregatedIds: List<String>) {
        itemAggregator.markClassificationPending(aggregatedIds)
    }

    /**
     * Update thumbnail for an aggregated item.
     */
    fun updateThumbnail(aggregatedId: String, thumbnail: ImageRef?) {
        itemAggregator.updateThumbnail(aggregatedId, thumbnail)
    }

    // ==================== Telemetry ====================

    /**
     * Enable async telemetry collection.
     */
    fun enableTelemetry() {
        if (_telemetryEnabled.value) {
            Log.w(TAG, "Telemetry already enabled")
            return
        }

        _telemetryEnabled.value = true
        telemetryJob = scope.launch(workerDispatcher) {
            Log.i(TAG, "╔═══════════════════════════════════════════════════════════════")
            Log.i(TAG, "║ ASYNC TELEMETRY ENABLED")
            Log.i(TAG, "║ Collection interval: ${TELEMETRY_INTERVAL_MS}ms")
            Log.i(TAG, "╚═══════════════════════════════════════════════════════════════")

            while (isActive && _telemetryEnabled.value) {
                delay(TELEMETRY_INTERVAL_MS)

                val stats = itemAggregator.getStats()
                val runtime = Runtime.getRuntime()
                val usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                val maxMemoryMB = runtime.maxMemory() / 1024 / 1024

                Log.i(TAG, "┌─────────────────────────────────────────────────────────────")
                Log.i(TAG, "│ TELEMETRY SNAPSHOT")
                Log.i(TAG, "├─────────────────────────────────────────────────────────────")
                Log.i(TAG, "│ Aggregated items: ${stats.totalItems}")
                Log.i(TAG, "│ Total merges: ${stats.totalMerges}")
                Log.i(TAG, "│ Avg merges/item: ${"%.2f".format(stats.averageMergesPerItem)}")
                Log.i(TAG, "│ Memory: ${usedMemoryMB}MB / ${maxMemoryMB}MB")
                Log.i(TAG, "└─────────────────────────────────────────────────────────────")
            }

            Log.i(TAG, "Async telemetry stopped")
        }
    }

    /**
     * Disable async telemetry collection.
     */
    fun disableTelemetry() {
        _telemetryEnabled.value = false
        telemetryJob?.cancel()
        telemetryJob = null
        Log.i(TAG, "Async telemetry disabled")
    }

    /**
     * Check if async telemetry is currently enabled.
     */
    fun isTelemetryEnabled(): Boolean = _telemetryEnabled.value

    // ==================== Internal Methods ====================

    /**
     * Update the UI state with current aggregated items.
     */
    internal fun updateItemsState(
        notifyNewItems: Boolean = true,
        triggerCallback: Boolean = true,
        animationEnabled: Boolean = true
    ) {
        val oldItems = _items.value
        val scannedItems = itemAggregator.getScannedItems()
        val cachedItems = cacheThumbnails(scannedItems)
        _items.value = cachedItems
        Log.d(TAG, "Updated UI state: ${cachedItems.size} items")

        if (notifyNewItems && animationEnabled) {
            val newItems = cachedItems.filter { newItem ->
                oldItems.none { oldItem -> oldItem.id == newItem.id }
            }
            newItems.forEach {
                if (DEBUG_LOGGING) {
                    Log.d(TAG, "Emitting new item event: ${it.id}")
                }
                _itemAddedEvents.tryEmit(it)
            }
        }

        persistItems(scannedItems)

        if (triggerCallback) {
            onStateChanged?.invoke()
        }
    }

    /**
     * Refresh items state from aggregator without triggering callback.
     */
    internal fun refreshItemsFromAggregator() {
        _items.value = itemAggregator.getScannedItems()
    }

    private fun loadPersistedItems() {
        scope.launch(workerDispatcher) {
            val persistedItems = itemsStore.loadAll()
            if (persistedItems.isEmpty()) return@launch

            itemAggregator.seedFromScannedItems(persistedItems)
            withContext(mainDispatcher) {
                updateItemsState(notifyNewItems = false, triggerCallback = false)
            }
        }
    }

    private fun persistItems(items: List<ScannedItem>) {
        scope.launch(workerDispatcher) {
            itemsStore.upsertAll(items)
        }
    }

    private fun cacheThumbnails(items: List<ScannedItem>): List<ScannedItem> {
        return items.map { item ->
            val thumbnail = item.thumbnailRef ?: item.thumbnail
            val bytesRef = thumbnail as? ImageRef.Bytes ?: return@map item
            ThumbnailCache.put(item.id, bytesRef)
            val cachedRef = ImageRef.CacheKey(
                key = item.id,
                mimeType = bytesRef.mimeType,
                width = bytesRef.width,
                height = bytesRef.height
            )
            itemAggregator.updateThumbnail(item.id, cachedRef)
            item.copy(thumbnail = cachedRef, thumbnailRef = cachedRef)
        }
    }
}
