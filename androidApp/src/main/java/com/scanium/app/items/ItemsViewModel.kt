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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        // Gate heavy logging to prevent UI jank during burst detections
        private const val DEBUG_LOGGING = false
        // Async telemetry collection interval (ms) - only runs when enabled
        private const val TELEMETRY_INTERVAL_MS = 5000L
    }

    // Async telemetry collection job (off the hot path)
    private var telemetryJob: Job? = null
    private val _telemetryEnabled = MutableStateFlow(false)

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
     * Offloads aggregation to background thread to prevent UI jank.
     */
    fun addItem(item: ScannedItem) {
        // Offload all processing to background thread
        viewModelScope.launch(Dispatchers.Default) {
            if (DEBUG_LOGGING) {
                Log.i(TAG, ">>> addItem: id=${item.id}, category=${item.category}, confidence=${item.confidence}")
            }

            // Process through aggregator on background thread
            val aggregatedItem = itemAggregator.processDetection(item)

            if (DEBUG_LOGGING) {
                Log.i(TAG, "    Processed item ${item.id} → aggregated ${aggregatedItem.aggregatedId} (mergeCount=${aggregatedItem.mergeCount})")
            }

            // Update UI state on main thread
            withContext(Dispatchers.Main) {
                updateItemsState()
            }
        }
    }

    /**
     * Adds multiple detected items at once.
     *
     * Processes each item through the aggregator in batch, which automatically
     * handles merging and deduplication. This is more efficient than calling
     * addItem() multiple times.
     *
     * Offloads aggregation to background thread to prevent UI jank during
     * burst detections from camera. All stats/telemetry collection removed
     * from hot path - use enableTelemetry() for async monitoring instead.
     */
    fun addItems(newItems: List<ScannedItem>) {
        if (newItems.isEmpty()) return

        // Launch directly on background thread to avoid any main-thread overhead
        viewModelScope.launch(Dispatchers.Default) {
            // Minimal logging on hot path - only batch size when debugging
            if (DEBUG_LOGGING) {
                Log.i(TAG, ">>> Processing batch: ${newItems.size} items")
            }

            // Process aggregation on background thread (already on Dispatchers.Default)
            // NO stats collection, NO memory profiling - keep the hot path fast
            itemAggregator.processDetections(newItems)

            // Update UI state on main thread
            withContext(Dispatchers.Main) {
                updateItemsState()
            }
        }
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
     * Restores a previously removed item (used for undo flows).
     */
    fun restoreItem(item: ScannedItem) {
        Log.i(TAG, "Restoring item: ${item.id}")
        itemAggregator.processDetection(item)
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
     * Note: This is synchronous - for async telemetry, use enableTelemetry().
     */
    fun getAggregationStats() = itemAggregator.getStats()

    /**
     * Enable async telemetry collection for monitoring stats and memory usage.
     * Runs independently on background thread, completely off the hot path.
     *
     * Stats are logged periodically (every 5s by default) without blocking
     * the main processing flow. Useful for debugging and performance monitoring.
     */
    fun enableTelemetry() {
        if (_telemetryEnabled.value) {
            Log.w(TAG, "Telemetry already enabled")
            return
        }

        _telemetryEnabled.value = true
        telemetryJob = viewModelScope.launch(Dispatchers.Default) {
            Log.i(TAG, "╔═══════════════════════════════════════════════════════════════")
            Log.i(TAG, "║ ASYNC TELEMETRY ENABLED")
            Log.i(TAG, "║ Collection interval: ${TELEMETRY_INTERVAL_MS}ms")
            Log.i(TAG, "╚═══════════════════════════════════════════════════════════════")

            while (isActive && _telemetryEnabled.value) {
                delay(TELEMETRY_INTERVAL_MS)

                // Collect stats asynchronously
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

        if (DEBUG_LOGGING) {
            Log.w(TAG, "╔═══════════════════════════════════════════════════════════════")
            Log.w(TAG, "║ THRESHOLD UPDATED: $previousThreshold → $clampedThreshold")
            Log.w(TAG, "║ Aggregator confirms: ${itemAggregator.getCurrentSimilarityThreshold()}")
            Log.w(TAG, "╚═══════════════════════════════════════════════════════════════")
        }
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
        // Offload filtering to background to avoid blocking UI during burst detections
        viewModelScope.launch {
            val pendingItems = withContext(Dispatchers.Default) {
                itemAggregator.getAggregatedItems()
                    .filter { it.thumbnail != null && classificationOrchestrator.shouldClassify(it.aggregatedId) }
            }

            if (pendingItems.isEmpty()) return@launch

            // Mark items as PENDING using thread-safe method
            val pendingIds = pendingItems.map { it.aggregatedId }
            itemAggregator.markClassificationPending(pendingIds)

            // Update UI on main dispatcher
            withContext(Dispatchers.Main) {
                _items.value = itemAggregator.getScannedItems()
            }

            classificationOrchestrator.classify(pendingItems) { aggregatedItem, result ->
                val boxArea = aggregatedItem.boundingBox.area
                val priceRange = PricingEngine.generatePriceRange(result.category, boxArea)
                val categoryOverride = if (result.confidence >= aggregatedItem.maxConfidence || aggregatedItem.category == ItemCategory.UNKNOWN) {
                    result.category
                } else {
                    aggregatedItem.enhancedCategory ?: aggregatedItem.category
                }

                // Apply classification using thread-safe methods
                itemAggregator.applyEnhancedClassification(
                    aggregatedId = aggregatedItem.aggregatedId,
                    category = categoryOverride,
                    label = result.label ?: aggregatedItem.labelText,
                    priceRange = priceRange
                )

                itemAggregator.updateClassificationStatus(
                    aggregatedId = aggregatedItem.aggregatedId,
                    status = result.status.name,
                    domainCategoryId = result.domainCategoryId,
                    errorMessage = result.errorMessage,
                    requestId = result.requestId
                )

                // Propagate updates to UI on main dispatcher
                viewModelScope.launch(Dispatchers.Main) {
                    _items.value = itemAggregator.getScannedItems()

                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Enhanced classification applied to ${aggregatedItem.aggregatedId} using ${result.mode}, status=${result.status}")
                    }
                }
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

    /**
     * Retry classification for a failed item.
     *
     * @param itemId The aggregated item ID to retry
     */
    fun retryClassification(itemId: String) {
        val aggregatedItems = itemAggregator.getAggregatedItems()
        val item = aggregatedItems.find { it.aggregatedId == itemId }

        if (item == null) {
            Log.w(TAG, "Cannot retry classification: item $itemId not found")
            return
        }

        Log.i(TAG, "Retrying classification for item $itemId")

        // Mark as pending using thread-safe method
        itemAggregator.markClassificationPending(listOf(itemId))

        // Update UI on main dispatcher
        viewModelScope.launch(Dispatchers.Main) {
            _items.value = itemAggregator.getScannedItems()
        }

        // Trigger retry via orchestrator
        classificationOrchestrator.retry(itemId, item) { aggregatedItem, result ->
            val boxArea = aggregatedItem.boundingBox.area
            val priceRange = PricingEngine.generatePriceRange(result.category, boxArea)
            val categoryOverride = if (result.confidence >= aggregatedItem.maxConfidence || aggregatedItem.category == ItemCategory.UNKNOWN) {
                result.category
            } else {
                aggregatedItem.enhancedCategory ?: aggregatedItem.category
            }

            // Apply classification using thread-safe methods
            itemAggregator.applyEnhancedClassification(
                aggregatedId = aggregatedItem.aggregatedId,
                category = categoryOverride,
                label = result.label ?: aggregatedItem.labelText,
                priceRange = priceRange
            )

            itemAggregator.updateClassificationStatus(
                aggregatedId = aggregatedItem.aggregatedId,
                status = result.status.name,
                domainCategoryId = result.domainCategoryId,
                errorMessage = result.errorMessage,
                requestId = result.requestId
            )

            // Propagate updates to UI on main dispatcher
            viewModelScope.launch(Dispatchers.Main) {
                _items.value = itemAggregator.getScannedItems()

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Retry classification result for ${aggregatedItem.aggregatedId}: status=${result.status}")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up async telemetry job
        disableTelemetry()
    }
}
