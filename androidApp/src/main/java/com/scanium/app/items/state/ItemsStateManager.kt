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
import com.scanium.shared.core.models.items.VisionAttributes
import com.scanium.shared.core.models.ml.ItemCategory
import com.scanium.shared.core.models.model.ImageRef
import com.scanium.shared.core.models.pricing.PriceEstimationStatus
import com.scanium.shared.core.models.pricing.PriceRange
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
 * ***REMOVED******REMOVED*** Thread Safety
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
    initialWorkerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    initialMainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    aggregationConfig: AggregationConfig = AggregationPresets.REALTIME,
) {
    companion object {
        private const val TAG = "ItemsStateManager"
        private const val DEBUG_LOGGING = false
        private const val TELEMETRY_INTERVAL_MS = 5000L
    }

    private var workerDispatcher: CoroutineDispatcher = initialWorkerDispatcher
    private var mainDispatcher: CoroutineDispatcher = initialMainDispatcher

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
     * Override coroutine dispatchers after construction.
     *
     * This is primarily used in tests where the ViewModel injects a TestDispatcher after
     * the primary constructor (and init block) have run.
     */
    internal fun overrideDispatchers(
        workerDispatcher: CoroutineDispatcher,
        mainDispatcher: CoroutineDispatcher,
    ) {
        this.workerDispatcher = workerDispatcher
        this.mainDispatcher = mainDispatcher
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
     * Updates the listing status of a scanned item.
     * This updates the item directly in the items state and persists the change.
     *
     * @param itemId The ID of the item to update
     * @param status The new listing status
     * @param listingId Optional eBay listing ID
     * @param listingUrl Optional URL to view the listing
     */
    fun updateListingStatus(
        itemId: String,
        status: com.scanium.shared.core.models.items.ItemListingStatus,
        listingId: String? = null,
        listingUrl: String? = null,
    ) {
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
    }

    /**
     * Updates user-editable fields of a scanned item.
     * This updates the item directly in the items state and persists the change.
     *
     * @param itemId The ID of the item to update
     * @param labelText New display label (null to keep existing)
     * @param recognizedText New recognized text (null to keep existing)
     * @param barcodeValue New barcode value (null to keep existing)
     */
    fun updateItemFields(
        itemId: String,
        labelText: String? = null,
        recognizedText: String? = null,
        barcodeValue: String? = null,
    ) {
        val updatedItems =
            _items.value.map { item ->
                if (item.id == itemId) {
                    item.copy(
                        labelText = labelText ?: item.labelText,
                        recognizedText = recognizedText ?: item.recognizedText,
                        barcodeValue = barcodeValue ?: item.barcodeValue,
                    )
                } else {
                    item
                }
            }
        _items.value = updatedItems
        persistItems(updatedItems)
        onStateChanged?.invoke()
    }

    /**
     * Updates multiple items at once with their new field values.
     * More efficient than calling updateItemFields() multiple times.
     *
     * @param updates Map of item ID to updated field values
     */
    fun updateItemsFields(updates: Map<String, ItemFieldUpdate>) {
        if (updates.isEmpty()) return

        val updatedItems =
            _items.value.map { item ->
                val update = updates[item.id]
                if (update != null) {
                    val newUserPriceCents = when {
                        update.clearUserPriceCents -> null
                        update.userPriceCents != null -> update.userPriceCents
                        else -> item.userPriceCents
                    }
                    val newCondition = when {
                        update.clearCondition -> null
                        update.condition != null -> update.condition
                        else -> item.condition
                    }
                    item.copy(
                        labelText = update.labelText ?: item.labelText,
                        recognizedText = update.recognizedText ?: item.recognizedText,
                        barcodeValue = update.barcodeValue ?: item.barcodeValue,
                        userPriceCents = newUserPriceCents,
                        condition = newCondition,
                        category = update.category ?: item.category,
                    )
                } else {
                    item
                }
            }
        _items.value = updatedItems
        persistItems(updatedItems)
        onStateChanged?.invoke()
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
     *
     * @param aggregatedId The ID of the aggregated item
     * @param category Optional category from classification
     * @param label Optional label from classification
     * @param priceRange Optional price range from classification
     * @param classificationConfidence Optional confidence score
     * @param attributes Optional enriched attributes (brand, model, color, etc.)
     * @param visionAttributes Optional raw vision data (OCR, colors, logos)
     * @param isFromBackend If true (default), this is a backend classification result and
     *                      attributes should be stored in detectedAttributes as well.
     *                      If false, this is a user edit and only enrichedAttributes is updated.
     */
    fun applyEnhancedClassification(
        aggregatedId: String,
        category: ItemCategory?,
        label: String?,
        priceRange: Pair<Double, Double>?,
        classificationConfidence: Float? = null,
        attributes: Map<String, com.scanium.shared.core.models.items.ItemAttribute>? = null,
        visionAttributes: VisionAttributes? = null,
        isFromBackend: Boolean = true,
    ) {
        itemAggregator.applyEnhancedClassification(
            aggregatedId = aggregatedId,
            category = category,
            label = label,
            priceRange = priceRange,
            classificationConfidence = classificationConfidence,
            attributes = attributes,
            visionAttributes = visionAttributes,
            isFromBackend = isFromBackend,
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
        requestId: String? = null,
    ) {
        itemAggregator.updateClassificationStatus(
            aggregatedId = aggregatedId,
            status = status,
            domainCategoryId = domainCategoryId,
            errorMessage = errorMessage,
            requestId = requestId,
        )
    }

    /**
     * Apply vision insights to an item for immediate prefill.
     *
     * This is called immediately after a photo is captured to populate
     * OCR text, brand/logo, and color information without waiting for
     * full classification.
     *
     * @param aggregatedId The ID of the aggregated item
     * @param visionAttributes Vision data (OCR, colors, logos, labels)
     * @param suggestedLabel Optional suggested label derived from vision data
     * @param categoryHint Optional category hint from vision labels
     */
    fun applyVisionInsights(
        aggregatedId: String,
        visionAttributes: VisionAttributes,
        suggestedLabel: String? = null,
        categoryHint: String? = null,
    ) {
        // Apply vision attributes
        itemAggregator.applyEnhancedClassification(
            aggregatedId = aggregatedId,
            category = categoryHint?.let { hint ->
                // Try to map category hint to ItemCategory
                try {
                    ItemCategory.entries.find { it.name.equals(hint, ignoreCase = true) }
                } catch (e: Exception) {
                    null
                }
            },
            label = suggestedLabel,
            priceRange = null,
            classificationConfidence = null,
            attributes = null,
            visionAttributes = visionAttributes,
            isFromBackend = true,
        )

        // Update the items state to reflect the changes
        scope.launch(workerDispatcher) {
            withContext(mainDispatcher) {
                updateItemsState(notifyNewItems = false)
            }
        }

        Log.i(TAG, "Applied vision insights to item $aggregatedId: label=$suggestedLabel category=$categoryHint")
    }

    /**
     * Update price estimation for an aggregated item.
     */
    fun updatePriceEstimation(
        aggregatedId: String,
        status: PriceEstimationStatus,
        priceRange: PriceRange? = null,
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
    fun updateThumbnail(
        aggregatedId: String,
        thumbnail: ImageRef?,
    ) {
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
        telemetryJob =
            scope.launch(workerDispatcher) {
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
        animationEnabled: Boolean = true,
    ) {
        val oldItems = _items.value
        val scannedItems = itemAggregator.getScannedItems()
        val cachedItems = cacheThumbnails(scannedItems)
        _items.value = cachedItems
        Log.d(TAG, "Updated UI state: ${cachedItems.size} items")

        if (notifyNewItems && animationEnabled) {
            val newItems =
                cachedItems.filter { newItem ->
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
     * Applies thumbnail caching for UI display but does NOT persist
     * (aggregator state already has bytes, persistence handled elsewhere).
     */
    internal fun refreshItemsFromAggregator() {
        val scannedItems = itemAggregator.getScannedItems()
        _items.value = cacheThumbnails(scannedItems)
    }

    private fun loadPersistedItems() {
        scope.launch(workerDispatcher) {
            val persistedItems = itemsStore.loadAll()
            if (persistedItems.isEmpty()) return@launch

            // Dev logging: persistence diagnostics
            if (DEBUG_LOGGING) {
                logPersistenceStats("LOAD", persistedItems)
            }

            itemAggregator.seedFromScannedItems(persistedItems)
            withContext(mainDispatcher) {
                updateItemsState(notifyNewItems = false, triggerCallback = false)
            }
        }
    }

    /**
     * Log persistence statistics for debugging thumbnail rehydration.
     */
    private fun logPersistenceStats(operation: String, items: List<ScannedItem>) {
        val totalItems = items.size
        val withThumbnailBytes = items.count { it.thumbnail is ImageRef.Bytes }
        val withThumbnailRefBytes = items.count { it.thumbnailRef is ImageRef.Bytes }
        val withCacheKey = items.count {
            it.thumbnail is ImageRef.CacheKey || it.thumbnailRef is ImageRef.CacheKey
        }
        val withNoThumbnail = items.count {
            it.thumbnail == null && it.thumbnailRef == null
        }
        val missingThumbnailWithUri = items.count {
            (it.thumbnail == null && it.thumbnailRef == null) && it.fullImageUri != null
        }

        Log.i(TAG, "┌─────────────────────────────────────────────────────────────")
        Log.i(TAG, "│ PERSISTENCE STATS ($operation)")
        Log.i(TAG, "├─────────────────────────────────────────────────────────────")
        Log.i(TAG, "│ Total items: $totalItems")
        Log.i(TAG, "│ With thumbnail Bytes: $withThumbnailBytes")
        Log.i(TAG, "│ With thumbnailRef Bytes: $withThumbnailRefBytes")
        Log.i(TAG, "│ With CacheKey (unexpected after load): $withCacheKey")
        Log.i(TAG, "│ With no thumbnail: $withNoThumbnail")
        Log.i(TAG, "│   - Recoverable (has fullImageUri): $missingThumbnailWithUri")
        Log.i(TAG, "└─────────────────────────────────────────────────────────────")

        // Warn if any items have CacheKey after load - this shouldn't happen
        if (withCacheKey > 0) {
            Log.w(TAG, "WARNING: $withCacheKey items have CacheKey thumbnails after DB load!")
        }

        // Log items that may have been affected by the previous bug
        if (withNoThumbnail > 0) {
            val affectedIds = items
                .filter { it.thumbnail == null && it.thumbnailRef == null }
                .map { it.id }
            Log.w(TAG, "Items with missing thumbnails (legacy bug): $affectedIds")
        }
    }

    private fun persistItems(items: List<ScannedItem>) {
        scope.launch(workerDispatcher) {
            itemsStore.upsertAll(items)
        }
    }

    /**
     * Cache thumbnails for UI display performance.
     *
     * IMPORTANT: This function does NOT mutate the aggregator's thumbnail state.
     * The aggregator retains ImageRef.Bytes so that persistence always has
     * access to the actual image data. CacheKey is only used for UI display.
     *
     * After process death, ThumbnailCache is empty but the aggregator (seeded from DB)
     * will have ImageRef.Bytes, which will be re-cached here on next state update.
     */
    private fun cacheThumbnails(items: List<ScannedItem>): List<ScannedItem> {
        return items.map { item ->
            val thumbnail = item.thumbnailRef ?: item.thumbnail
            val bytesRef = thumbnail as? ImageRef.Bytes ?: return@map item
            ThumbnailCache.put(item.id, bytesRef)
            val cachedRef =
                ImageRef.CacheKey(
                    key = item.id,
                    mimeType = bytesRef.mimeType,
                    width = bytesRef.width,
                    height = bytesRef.height,
                )
            // NOTE: Do NOT call itemAggregator.updateThumbnail() here.
            // The aggregator must retain ImageRef.Bytes for persistence.
            // CacheKey is only for UI display in the returned items.
            item.copy(thumbnail = cachedRef, thumbnailRef = cachedRef)
        }
    }

    /**
     * Update a single attribute for an item.
     * Updates the item's attributes map with the new value and persists.
     *
     * This preserves the original detected attributes for reference - the UI can
     * show "Detected: X" alongside the user's overridden value.
     *
     * @param itemId The ID of the item to update
     * @param attributeKey The key of the attribute to update (e.g., "brand", "color")
     * @param attribute The new attribute value
     */
    fun updateItemAttribute(
        itemId: String,
        attributeKey: String,
        attribute: com.scanium.shared.core.models.items.ItemAttribute,
    ) {
        val updatedItems =
            _items.value.map { item ->
                if (item.id == itemId) {
                    val updatedAttributes = item.attributes.toMutableMap()
                    updatedAttributes[attributeKey] = attribute
                    // detectedAttributes remain unchanged - they're preserved for UI reference
                    item.copy(attributes = updatedAttributes)
                } else {
                    item
                }
            }
        _items.value = updatedItems

        // Also update the aggregator with isFromBackend=false to preserve detectedAttributes
        itemAggregator.applyEnhancedClassification(
            aggregatedId = itemId,
            category = null,
            label = null,
            priceRange = null,
            classificationConfidence = null,
            attributes = updatedItems.find { it.id == itemId }?.attributes,
            isFromBackend = false, // User edit - preserve detectedAttributes
        )

        persistItems(updatedItems)
        onStateChanged?.invoke()
    }
}

/**
 * Represents field updates for a scanned item.
 * Null values mean "keep existing value".
 * Use [clearUserPriceCents] = true to explicitly clear the user price.
 * Use [clearCondition] = true to explicitly clear the condition.
 */
data class ItemFieldUpdate(
    val labelText: String? = null,
    val recognizedText: String? = null,
    val barcodeValue: String? = null,
    val userPriceCents: Long? = null,
    val clearUserPriceCents: Boolean = false,
    val condition: com.scanium.app.items.ItemCondition? = null,
    val clearCondition: Boolean = false,
    val category: com.scanium.app.ml.ItemCategory? = null,
)
