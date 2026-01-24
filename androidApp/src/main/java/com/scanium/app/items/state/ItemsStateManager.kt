package com.scanium.app.items.state

import android.util.Log
import com.scanium.app.AggregatedItem
import com.scanium.app.AggregationConfig
import com.scanium.app.AggregationPresets
import com.scanium.app.AggregationStats
import com.scanium.app.ItemAggregator
import com.scanium.app.ItemCategory
import com.scanium.app.ScannedItem
import com.scanium.app.items.ThumbnailCache
import com.scanium.app.items.persistence.NoopScannedItemStore
import com.scanium.app.items.persistence.ScannedItemStore
import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.shared.core.models.items.VisionAttributes
import com.scanium.shared.core.models.model.ImageRef
import com.scanium.shared.core.models.pricing.PriceEstimationStatus
import com.scanium.shared.core.models.pricing.PriceRange
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    initialWorkerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    initialMainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    aggregationConfig: AggregationConfig = AggregationPresets.REALTIME,
) {
    companion object {
        private const val TAG = "ItemsStateManager"
        private const val DEBUG_LOGGING = false
    }

    private var workerDispatcher: CoroutineDispatcher = initialWorkerDispatcher
    private var mainDispatcher: CoroutineDispatcher = initialMainDispatcher

    private val itemAggregator = ItemAggregator(config = aggregationConfig)
    private val stateStore = ItemsStateStore()
    private val persistence = ItemsPersistence(itemsStore)
    private val telemetry =
        ItemsTelemetry(
            scope = scope,
            workerDispatcher = initialWorkerDispatcher,
            statsProvider = { itemAggregator.getStats() },
        )

    val items: StateFlow<List<ScannedItem>> = stateStore.items
    val itemAddedEvents = stateStore.itemAddedEvents

    fun getItems(): List<ScannedItem> = stateStore.getItems()

    private val _similarityThreshold = MutableStateFlow(aggregationConfig.similarityThreshold)
    val similarityThreshold: StateFlow<Float> = _similarityThreshold.asStateFlow()

    // Vision API quota exceeded event
    data class QuotaExceededEvent(val quotaLimit: Int?, val resetTime: String?)

    private val _quotaExceededEvent = MutableStateFlow<QuotaExceededEvent?>(null)
    val quotaExceededEvent: StateFlow<QuotaExceededEvent?> = _quotaExceededEvent.asStateFlow()

    private var onStateChanged: (() -> Unit)? = null

    init {
        itemAggregator.updateSimilarityThreshold(aggregationConfig.similarityThreshold)
        Log.i(TAG, "ItemsStateManager initialized with threshold: ${aggregationConfig.similarityThreshold}")
        loadPersistedItems()
    }

    internal fun overrideDispatchers(
        workerDispatcher: CoroutineDispatcher,
        mainDispatcher: CoroutineDispatcher,
    ) {
        this.workerDispatcher = workerDispatcher
        this.mainDispatcher = mainDispatcher
        telemetry.setDispatcher(workerDispatcher)
    }

    fun setOnStateChangedListener(listener: (() -> Unit)?) {
        onStateChanged = listener
    }

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

    fun addItemsSync(newItems: List<ScannedItem>): List<AggregatedItem> {
        if (newItems.isEmpty()) return emptyList()

        Log.i(TAG, ">>> addItemsSync: Processing ${newItems.size} items synchronously")

        val result = itemAggregator.processDetections(newItems)

        scope.launch(mainDispatcher) {
            updateItemsState()
        }

        return result
    }

    fun removeItem(itemId: String) {
        Log.i(TAG, "Removing item: $itemId")
        ThumbnailCache.remove(itemId)
        itemAggregator.removeItem(itemId)
        updateItemsState(notifyNewItems = false)
    }

    fun restoreItem(item: ScannedItem) {
        Log.i(TAG, "Restoring item: ${item.id}")
        itemAggregator.processDetection(item)
        updateItemsState(notifyNewItems = false)
    }

    fun clearAllItems() {
        Log.i(TAG, "Clearing all items")
        ThumbnailCache.clear()
        itemAggregator.reset()
        stateStore.clearItems()

        scope.launch(workerDispatcher) {
            persistence.deleteAll()
        }
    }

    fun getItemCount(): Int = stateStore.getItemCount()

    fun getAggregationStats(): AggregationStats = itemAggregator.getStats()

    fun getAggregatedItems(): List<AggregatedItem> = itemAggregator.getAggregatedItems()

    fun getScannedItems(): List<ScannedItem> = itemAggregator.getScannedItems()

    fun getItem(itemId: String): ScannedItem? = stateStore.getItem(itemId)

    fun updateListingStatus(
        itemId: String,
        status: com.scanium.shared.core.models.items.ItemListingStatus,
        listingId: String? = null,
        listingUrl: String? = null,
    ) {
        val updatedItems =
            stateStore.getItems().map { item ->
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
        stateStore.setItems(updatedItems)
        persistItems(updatedItems)
    }

    fun updateItemFields(
        itemId: String,
        labelText: String? = null,
        recognizedText: String? = null,
        barcodeValue: String? = null,
    ) {
        val updatedItems =
            stateStore.getItems().map { item ->
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
        stateStore.setItems(updatedItems)
        persistItems(updatedItems)
        onStateChanged?.invoke()
    }

    fun updateItemsFields(updates: Map<String, ItemFieldUpdate>) {
        if (updates.isEmpty()) return

        val updatedItems =
            stateStore.getItems().map { item ->
                val update = updates[item.id]
                if (update != null) {
                    val newUserPriceCents =
                        when {
                            update.clearUserPriceCents -> null
                            update.userPriceCents != null -> update.userPriceCents
                            else -> item.userPriceCents
                        }
                    val newCondition =
                        when {
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
        stateStore.setItems(updatedItems)
        persistItems(updatedItems)
        onStateChanged?.invoke()
    }

    fun seedFromScannedItems(items: List<ScannedItem>) {
        itemAggregator.seedFromScannedItems(items)
    }

    fun removeStaleItems(maxAgeMs: Long = 30_000L) {
        val removed = itemAggregator.removeStaleItems(maxAgeMs)
        if (removed > 0) {
            Log.i(TAG, "Removed $removed stale items")
            updateItemsState(notifyNewItems = false)
        }
    }

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

    fun getCurrentSimilarityThreshold(): Float = itemAggregator.getCurrentSimilarityThreshold()

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

    fun applyVisionInsights(
        aggregatedId: String,
        visionAttributes: VisionAttributes,
        suggestedLabel: String? = null,
        categoryHint: String? = null,
    ) {
        val allItems = itemAggregator.getAggregatedItems()
        val existingItem = allItems.find { it.aggregatedId == aggregatedId }

        if (existingItem == null) {
            Log.e(TAG, "SCAN_ENRICH: ❌ Cannot apply vision insights - item NOT FOUND: $aggregatedId")
            Log.e(TAG, "SCAN_ENRICH: Available items: ${allItems.map { it.aggregatedId.take(8) }}")
            return
        }

        Log.i(TAG, "SCAN_ENRICH: Applying vision insights to item $aggregatedId")
        Log.i(TAG, "SCAN_ENRICH:   suggestedLabel=$suggestedLabel")
        Log.i(TAG, "SCAN_ENRICH:   brand=${visionAttributes.primaryBrand}")
        Log.i(TAG, "SCAN_ENRICH:   colors=${visionAttributes.colors.map { it.name }}")
        Log.i(TAG, "SCAN_ENRICH:   hasOCR=${!visionAttributes.ocrText.isNullOrBlank()}")

        val visionAttributeMap = buildVisionAttributeMap(visionAttributes).takeIf { it.isNotEmpty() }

        itemAggregator.applyEnhancedClassification(
            aggregatedId = aggregatedId,
            category =
                categoryHint?.let { hint ->
                    try {
                        ItemCategory.entries.find { it.name.equals(hint, ignoreCase = true) }
                    } catch (e: Exception) {
                        null
                    }
                },
            label = suggestedLabel,
            priceRange = null,
            classificationConfidence = null,
            attributes = visionAttributeMap,
            visionAttributes = visionAttributes,
            isFromBackend = true,
        )

        val updatedItem = itemAggregator.getAggregatedItems().find { it.aggregatedId == aggregatedId }
        if (updatedItem != null) {
            Log.i(TAG, "SCAN_ENRICH: ✓ Applied - label now: ${updatedItem.toScannedItem().displayLabel}")
        }

        scope.launch(workerDispatcher) {
            withContext(mainDispatcher) {
                updateItemsState(notifyNewItems = false)
            }
        }
    }

    fun updatePriceEstimation(
        aggregatedId: String,
        status: PriceEstimationStatus,
        priceRange: PriceRange? = null,
    ) {
        itemAggregator.updatePriceEstimation(aggregatedId, status, priceRange)
    }

    private fun buildVisionAttributeMap(visionAttributes: VisionAttributes): Map<String, ItemAttribute> {
        val attributes = mutableMapOf<String, ItemAttribute>()

        val brand = visionAttributes.primaryBrand?.trim()?.takeIf { it.isNotEmpty() }
        if (brand != null) {
            val confidence = visionAttributes.logos.maxOfOrNull { it.score } ?: 0.6f
            attributes["brand"] = ItemAttribute(value = brand, confidence = confidence, source = "vision")
        }

        val colors = visionAttributes.colors.sortedByDescending { it.score }
        colors.getOrNull(0)?.let { color ->
            attributes["color"] =
                ItemAttribute(
                    value = color.name,
                    confidence = color.score,
                    source = "vision-color",
                )
        }
        colors.getOrNull(1)?.let { color ->
            attributes["secondaryColor"] =
                ItemAttribute(
                    value = color.name,
                    confidence = color.score,
                    source = "vision-color",
                )
        }

        val itemType =
            visionAttributes.itemType?.trim()?.takeIf { it.isNotEmpty() }
                ?: visionAttributes.labels
                    .firstOrNull()
                    ?.name
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
        if (itemType != null) {
            val confidence = visionAttributes.labels.maxOfOrNull { it.score } ?: 0.7f
            attributes["itemType"] =
                ItemAttribute(
                    value = itemType,
                    confidence = confidence,
                    source = "vision-label",
                )
        }

        val labelNames =
            visionAttributes.labels
                .map { it.name }
                .distinct()
                .take(3)
        if (labelNames.isNotEmpty()) {
            val confidence = visionAttributes.labels.maxOfOrNull { it.score } ?: 0.5f
            attributes["labelHints"] =
                ItemAttribute(
                    value = labelNames.joinToString(", "),
                    confidence = confidence,
                    source = "vision-label",
                )
        }

        val ocrSnippet =
            visionAttributes.ocrText
                ?.lineSequence()
                ?.map { it.trim() }
                ?.firstOrNull { it.isNotEmpty() }
                ?.take(80)
        if (!ocrSnippet.isNullOrBlank()) {
            attributes["ocrText"] =
                ItemAttribute(
                    value = ocrSnippet,
                    confidence = 0.8f,
                    source = "vision-ocr",
                )
        }

        return attributes
    }

    fun markClassificationPending(aggregatedIds: List<String>) {
        itemAggregator.markClassificationPending(aggregatedIds)
    }

    fun updateThumbnail(
        aggregatedId: String,
        thumbnail: ImageRef?,
    ) {
        itemAggregator.updateThumbnail(aggregatedId, thumbnail)
    }

    fun enableTelemetry() {
        telemetry.enable()
    }

    fun disableTelemetry() {
        telemetry.disable()
    }

    fun isTelemetryEnabled(): Boolean = telemetry.isEnabled()

    internal fun updateItemsState(
        notifyNewItems: Boolean = true,
        triggerCallback: Boolean = true,
        animationEnabled: Boolean = true,
    ) {
        val scannedItems =
            stateStore.updateFromAggregator(
                itemAggregator = itemAggregator,
                notifyNewItems = notifyNewItems,
                triggerCallback = triggerCallback,
                animationEnabled = animationEnabled,
                onStateChanged = onStateChanged,
            )

        persistItems(scannedItems)
    }

    internal fun refreshItemsFromAggregator() {
        stateStore.refreshFromAggregator(itemAggregator)
    }

    private fun loadPersistedItems() {
        scope.launch(workerDispatcher) {
            val persistedItems = persistence.loadAll()
            if (persistedItems.isEmpty()) return@launch

            if (DEBUG_LOGGING) {
                persistence.logPersistenceStats("LOAD", persistedItems)
            }

            itemAggregator.seedFromScannedItems(persistedItems)
            withContext(mainDispatcher) {
                updateItemsState(notifyNewItems = false, triggerCallback = false)
            }
        }
    }

    private fun persistItems(items: List<ScannedItem>) {
        scope.launch(workerDispatcher) {
            persistence.upsertAll(items)
        }
    }

    fun updateItemAttribute(
        itemId: String,
        attributeKey: String,
        attribute: com.scanium.shared.core.models.items.ItemAttribute,
    ) {
        val updatedItems =
            stateStore.getItems().map { item ->
                if (item.id == itemId) {
                    val updatedAttributes = item.attributes.toMutableMap()
                    updatedAttributes[attributeKey] = attribute
                    item.copy(attributes = updatedAttributes)
                } else {
                    item
                }
            }
        stateStore.setItems(updatedItems)

        itemAggregator.applyEnhancedClassification(
            aggregatedId = itemId,
            category = null,
            label = null,
            priceRange = null,
            classificationConfidence = null,
            attributes = updatedItems.find { it.id == itemId }?.attributes,
            isFromBackend = false,
        )

        persistItems(updatedItems)
        onStateChanged?.invoke()
    }

    fun updateEnrichmentStatus(
        itemId: String,
        transform: (
            com.scanium.shared.core.models.items.EnrichmentLayerStatus,
        ) -> com.scanium.shared.core.models.items.EnrichmentLayerStatus,
    ) {
        val updatedItems =
            stateStore.getItems().map { item ->
                if (item.id == itemId) {
                    val newStatus = transform(item.enrichmentStatus)
                    item.copy(
                        enrichmentStatus =
                            newStatus.copy(
                                lastUpdated =
                                    kotlinx.datetime.Clock.System
                                        .now()
                                        .toEpochMilliseconds(),
                            ),
                    )
                } else {
                    item
                }
            }
        stateStore.setItems(updatedItems)

        val updatedItem = updatedItems.find { it.id == itemId }
        if (updatedItem != null) {
            itemAggregator.updateEnrichmentStatus(itemId, updatedItem.enrichmentStatus)
        }

        persistItems(updatedItems)
        onStateChanged?.invoke()
    }

    fun updateSummaryText(
        itemId: String,
        summaryText: String,
        userEdited: Boolean,
    ) {
        val updatedItems =
            stateStore.getItems().map { item ->
                if (item.id == itemId) {
                    item.copy(
                        attributesSummaryText = summaryText,
                        summaryTextUserEdited = userEdited,
                    )
                } else {
                    item
                }
            }
        stateStore.setItems(updatedItems)

        itemAggregator.updateSummaryText(itemId, summaryText, userEdited)

        persistItems(updatedItems)
        onStateChanged?.invoke()
    }

    fun addPhotoToItem(
        itemId: String,
        photo: com.scanium.shared.core.models.items.ItemPhoto,
    ) {
        val updatedItems =
            stateStore.getItems().map { item ->
                if (item.id == itemId) {
                    item.copy(
                        additionalPhotos = item.additionalPhotos + photo,
                    )
                } else {
                    item
                }
            }
        stateStore.setItems(updatedItems)

        itemAggregator.addPhotoToItem(itemId, photo)

        persistItems(updatedItems)
        onStateChanged?.invoke()
    }

    fun removePhotosFromItem(
        itemId: String,
        photoIds: Set<String>,
    ) {
        if (photoIds.isEmpty()) return

        val updatedItems =
            stateStore.getItems().map { item ->
                if (item.id == itemId) {
                    val remainingPhotos = item.additionalPhotos.filter { it.id !in photoIds }
                    Log.i(TAG, "Removing ${photoIds.size} photo(s) from item $itemId, ${remainingPhotos.size} remaining")
                    item.copy(additionalPhotos = remainingPhotos)
                } else {
                    item
                }
            }
        stateStore.setItems(updatedItems)

        itemAggregator.removePhotosFromItem(itemId, photoIds)

        persistItems(updatedItems)
        onStateChanged?.invoke()
    }

    fun updateExportFields(
        itemId: String,
        exportTitle: String?,
        exportDescription: String?,
        exportBullets: List<String>,
        exportFromCache: Boolean,
        exportModel: String?,
        exportConfidenceTier: String?,
    ) {
        val now =
            kotlinx.datetime.Clock.System
                .now()
                .toEpochMilliseconds()
        val updatedItems =
            stateStore.getItems().map { item ->
                if (item.id == itemId) {
                    item.copy(
                        exportTitle = exportTitle,
                        exportDescription = exportDescription,
                        exportBullets = exportBullets,
                        exportGeneratedAt = now,
                        exportFromCache = exportFromCache,
                        exportModel = exportModel,
                        exportConfidenceTier = exportConfidenceTier,
                    )
                } else {
                    item
                }
            }
        stateStore.setItems(updatedItems)

        itemAggregator.updateExportFields(
            itemId,
            exportTitle,
            exportDescription,
            exportBullets,
            now,
            exportFromCache,
            exportModel,
            exportConfidenceTier,
        )

        persistItems(updatedItems)
        onStateChanged?.invoke()
    }

    fun updateCompleteness(
        itemId: String,
        completenessScore: Int,
        missingAttributes: List<String>,
        isReadyForListing: Boolean,
        lastEnrichedAt: Long? = null,
    ) {
        val updatedItems =
            stateStore.getItems().map { item ->
                if (item.id == itemId) {
                    item.copy(
                        completenessScore = completenessScore,
                        missingAttributes = missingAttributes,
                        isReadyForListing = isReadyForListing,
                        lastEnrichedAt = lastEnrichedAt ?: item.lastEnrichedAt,
                    )
                } else {
                    item
                }
            }
        stateStore.setItems(updatedItems)

        itemAggregator.updateCompleteness(
            itemId,
            completenessScore,
            missingAttributes,
            isReadyForListing,
            lastEnrichedAt,
        )

        persistItems(updatedItems)
        onStateChanged?.invoke()
    }

    fun addCapturedShotType(
        itemId: String,
        shotType: String,
    ) {
        val updatedItems =
            stateStore.getItems().map { item ->
                if (item.id == itemId && shotType !in item.capturedShotTypes) {
                    item.copy(
                        capturedShotTypes = item.capturedShotTypes + shotType,
                    )
                } else {
                    item
                }
            }
        stateStore.setItems(updatedItems)

        itemAggregator.addCapturedShotType(itemId, shotType)

        persistItems(updatedItems)
        onStateChanged?.invoke()
    }

    /**
     * Notify that the Vision API quota has been exceeded.
     * This will trigger the UI to show a quota exceeded dialog.
     *
     * @param quotaLimit The daily quota limit (e.g., 50)
     * @param resetTime The time when quota resets (e.g., "23:45")
     */
    fun notifyQuotaExceeded(
        quotaLimit: Int?,
        resetTime: String?,
    ) {
        _quotaExceededEvent.value = QuotaExceededEvent(quotaLimit, resetTime)
    }

    /**
     * Clear the quota exceeded event after the dialog is dismissed.
     */
    fun clearQuotaExceededEvent() {
        _quotaExceededEvent.value = null
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
