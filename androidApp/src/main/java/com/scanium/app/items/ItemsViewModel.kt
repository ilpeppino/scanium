package com.scanium.app.items

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanium.app.BuildConfig
import com.scanium.app.aggregation.AggregatedItem
import com.scanium.app.aggregation.AggregationPresets
import com.scanium.app.aggregation.ItemAggregator
import com.scanium.app.ml.ItemCategory
import com.scanium.app.ml.classification.ClassificationMode
import com.scanium.app.ml.classification.ClassificationOrchestrator
import com.scanium.app.ml.classification.ClassificationResult
import com.scanium.app.ml.classification.ClassificationThumbnailProvider
import com.scanium.app.ml.classification.ItemClassifier
import com.scanium.app.ml.classification.NoopClassifier
import com.scanium.app.ml.classification.NoopClassificationThumbnailProvider
import com.scanium.app.items.persistence.NoopScannedItemStore
import com.scanium.app.items.persistence.ScannedItemStore
import com.scanium.app.pricing.PriceEstimationRepository
import com.scanium.shared.core.models.pricing.PriceEstimationStatus
import com.scanium.shared.core.models.pricing.PriceEstimatorProvider
import kotlinx.coroutines.CoroutineDispatcher
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
    cloudClassifier: ItemClassifier = NoopClassifier,
    private val itemsStore: ScannedItemStore = NoopScannedItemStore,
    private val stableItemCropper: ClassificationThumbnailProvider = NoopClassificationThumbnailProvider,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    priceEstimatorProvider: PriceEstimatorProvider? = null
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

    private val priceEstimationRepository = PriceEstimationRepository(
        provider = priceEstimatorProvider ?: com.scanium.shared.core.models.pricing.providers.MockPriceEstimatorProvider(),
        scope = viewModelScope
    )

    private val priceStatusJobs = mutableMapOf<String, Job>()

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

        loadPersistedItems()
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
        viewModelScope.launch(workerDispatcher) {
            if (DEBUG_LOGGING) {
                Log.i(TAG, ">>> addItem: id=${item.id}, category=${item.category}, confidence=${item.confidence}")
            }

            // Process through aggregator on background thread
            val aggregatedItem = itemAggregator.processDetection(item)

            if (DEBUG_LOGGING) {
                Log.i(TAG, "    Processed item ${item.id} → aggregated ${aggregatedItem.aggregatedId} (mergeCount=${aggregatedItem.mergeCount})")
            }

            // Update UI state on main thread
            withContext(mainDispatcher) {
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
        viewModelScope.launch(workerDispatcher) {
            // Minimal logging on hot path - only batch size when debugging
            if (DEBUG_LOGGING) {
                Log.i(TAG, ">>> Processing batch: ${newItems.size} items")
            }

            // Process aggregation on background thread (already on Dispatchers.Default)
            // NO stats collection, NO memory profiling - keep the hot path fast
            itemAggregator.processDetections(newItems)

            // Update UI state on main thread
            withContext(mainDispatcher) {
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

        priceStatusJobs.remove(itemId)?.cancel()
        priceEstimationRepository.cancel(itemId)

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

        priceStatusJobs.values.forEach { it.cancel() }
        priceStatusJobs.clear()

        // Reset aggregator
        itemAggregator.reset()

        // Reset classifier cache to avoid stale matches
        classificationOrchestrator.reset()

        // Update UI state
        _items.value = emptyList()
        viewModelScope.launch(workerDispatcher) {
            itemsStore.deleteAll()
        }
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
        telemetryJob = viewModelScope.launch(workerDispatcher) {
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
    private fun updateItemsState(triggerClassification: Boolean = true) {
        val scannedItems = itemAggregator.getScannedItems()
        _items.value = scannedItems
        Log.d(TAG, "Updated UI state: ${scannedItems.size} items")

        syncPriceEstimations(scannedItems)

        persistItems(scannedItems)

        if (triggerClassification) {
            triggerEnhancedClassification()
        }
    }

    private fun syncPriceEstimations(scannedItems: List<ScannedItem>) {
        val activeIds = scannedItems.map { it.id }.toSet()
        priceStatusJobs.keys.filterNot { activeIds.contains(it) }.forEach { id ->
            priceStatusJobs.remove(id)?.cancel()
            priceEstimationRepository.cancel(id)
        }

        scannedItems.forEach { item ->
            if (item.category != ItemCategory.UNKNOWN) {
                observePriceStatus(item.id)
                priceEstimationRepository.ensureEstimation(item)
            }
        }
    }

    private fun observePriceStatus(itemId: String) {
        if (priceStatusJobs.containsKey(itemId)) return

        val statusFlow = priceEstimationRepository.observeStatus(itemId)
        priceStatusJobs[itemId] = viewModelScope.launch {
            statusFlow.collect { status ->
                itemAggregator.updatePriceEstimation(
                    aggregatedId = itemId,
                    status = status,
                    priceRange = (status as? PriceEstimationStatus.Ready)?.priceRange
                )
                _items.value = itemAggregator.getScannedItems()
            }
        }
    }

    private fun triggerEnhancedClassification() {
        // Offload filtering to background to avoid blocking UI during burst detections
        viewModelScope.launch(mainDispatcher) {
            val pendingItems = withContext(workerDispatcher) {
                itemAggregator.getAggregatedItems()
                    .filter {
                        (it.thumbnail != null || it.fullImageUri != null) &&
                            classificationOrchestrator.shouldClassify(it.aggregatedId)
                    }
            }

            if (pendingItems.isEmpty()) return@launch

            val preparedItems = prepareThumbnailsForClassification(pendingItems)
            if (preparedItems.isEmpty()) return@launch

            // Mark items as PENDING using thread-safe method
            val pendingIds = preparedItems.map { it.aggregatedId }
            itemAggregator.markClassificationPending(pendingIds)

            // Update UI on main dispatcher
            withContext(mainDispatcher) {
                _items.value = itemAggregator.getScannedItems()
            }

            classificationOrchestrator.classify(preparedItems) { aggregatedItem, result ->
                handleClassificationResult(aggregatedItem, result)
            }
        }
    }

    private suspend fun prepareThumbnailsForClassification(
        items: List<AggregatedItem>
    ): List<AggregatedItem> {
        if (items.isEmpty()) return emptyList()

        return withContext(workerDispatcher) {
            items.mapNotNull { aggregatedItem ->
                val preparedThumbnail = runCatching {
                    stableItemCropper.prepare(aggregatedItem)
                }.onFailure { error ->
                    Log.w(TAG, "Failed to prepare stable thumbnail for ${aggregatedItem.aggregatedId}", error)
                }.getOrNull()

                val thumbnailToUse = preparedThumbnail ?: aggregatedItem.thumbnail
                if (thumbnailToUse == null) {
                    null
                } else {
                    itemAggregator.updateThumbnail(aggregatedItem.aggregatedId, thumbnailToUse)
                    aggregatedItem
                }
            }
        }
    }

    private fun handleClassificationResult(
        aggregatedItem: AggregatedItem,
        result: ClassificationResult
    ) {
        val shouldOverrideCategory = result.category != ItemCategory.UNKNOWN &&
            (result.confidence >= aggregatedItem.maxConfidence || aggregatedItem.category == ItemCategory.UNKNOWN)

        val categoryOverride = if (shouldOverrideCategory) {
            result.category
        } else {
            aggregatedItem.enhancedCategory ?: aggregatedItem.category
        }

        val labelOverride = result.label?.takeUnless { it.isBlank() } ?: aggregatedItem.labelText
        val priceCategory = if (result.category != ItemCategory.UNKNOWN) result.category else aggregatedItem.category
        val boxArea = aggregatedItem.boundingBox.area
        val priceRange = PricingEngine.generatePriceRange(priceCategory, boxArea)

        itemAggregator.applyEnhancedClassification(
            aggregatedId = aggregatedItem.aggregatedId,
            category = categoryOverride,
            label = labelOverride,
            priceRange = priceRange
        )

        itemAggregator.updateClassificationStatus(
            aggregatedId = aggregatedItem.aggregatedId,
            status = result.status.name,
            domainCategoryId = result.domainCategoryId,
            errorMessage = result.errorMessage,
            requestId = result.requestId
        )

        viewModelScope.launch(mainDispatcher) {
            _items.value = itemAggregator.getScannedItems()

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Enhanced classification applied to ${aggregatedItem.aggregatedId} using ${result.mode}, status=${result.status}")
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

        persistItems(_items.value)
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
        viewModelScope.launch(mainDispatcher) {
            _items.value = itemAggregator.getScannedItems()
        }

        // Trigger retry via orchestrator
        viewModelScope.launch(workerDispatcher) {
            val preparedItems = prepareThumbnailsForClassification(listOf(item))
            if (preparedItems.isEmpty()) {
                Log.w(TAG, "Retry classification aborted: no thumbnail available")
                return@launch
            }

            val preparedItem = preparedItems.first()
            classificationOrchestrator.retry(itemId, preparedItem) { aggregatedItem, result ->
                handleClassificationResult(aggregatedItem, result)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up async telemetry job
        disableTelemetry()
    }

    private fun loadPersistedItems() {
        viewModelScope.launch(workerDispatcher) {
            val persistedItems = itemsStore.loadAll()
            if (persistedItems.isEmpty()) return@launch

            itemAggregator.seedFromScannedItems(persistedItems)
            withContext(mainDispatcher) {
                updateItemsState(triggerClassification = false)
            }
        }
    }

    private fun persistItems(items: List<ScannedItem>) {
        viewModelScope.launch(workerDispatcher) {
            itemsStore.upsertAll(items)
        }
    }
}
