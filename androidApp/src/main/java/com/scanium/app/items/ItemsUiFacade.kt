package com.scanium.app.items

import android.content.Context
import android.net.Uri
import android.util.Log
import com.scanium.app.AggregationPresets
import com.scanium.app.AggregationStats
import com.scanium.app.camera.OverlayTrack
import com.scanium.app.data.SettingsRepository
import com.scanium.app.items.classification.ItemClassificationCoordinator
import com.scanium.app.items.export.toExportPayload
import com.scanium.app.items.listing.ListingStatusManager
import com.scanium.app.items.overlay.OverlayTrackManager
import com.scanium.app.items.persistence.ScannedItemStore
import com.scanium.app.items.state.ItemFieldUpdate
import com.scanium.app.items.state.ItemsStateManager
import com.scanium.app.ml.CropBasedEnricher
import com.scanium.app.ml.DetectionResult
import com.scanium.app.ml.VisionInsightsPrefiller
import com.scanium.app.ml.classification.ClassificationMode
import com.scanium.app.ml.classification.ClassificationThumbnailProvider
import com.scanium.app.ml.classification.ItemClassifier
import com.scanium.core.export.ExportPayload
import com.scanium.core.models.scanning.ScanRoi
import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.telemetry.facade.Telemetry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * UI facade that encapsulates manager coordination for items operations.
 *
 * This facade provides a focused API for UI operations while delegating to specialized managers:
 * - [ItemsStateManager]: State management, aggregation, persistence
 * - [ItemClassificationCoordinator]: Classification orchestration
 * - [OverlayTrackManager]: Camera overlay state
 * - [ListingStatusManager]: eBay listing status
 *
 * Benefits of the facade pattern:
 * - Narrows the public API surface exposed to ViewModels/UI
 * - Encapsulates manager creation and lifecycle
 * - Provides a single point of coordination
 * - Easier to test and reason about
 * - Reduces coupling between UI and internal managers
 *
 * @param scope Coroutine scope for async operations
 * @param classificationMode Current classification mode (on-device vs cloud)
 * @param cloudClassificationEnabled Whether cloud classification is enabled
 * @param onDeviceClassifier On-device classifier implementation
 * @param cloudClassifier Cloud-based classifier implementation
 * @param itemsStore Persistence layer for scanned items
 * @param stableItemCropper Provider for stable classification thumbnails
 * @param visionInsightsPrefiller Vision insights extraction service
 * @param cropBasedEnricher Crop-based enrichment service
 * @param settingsRepository Settings repository for user preferences
 * @param telemetry Optional telemetry facade
 * @param workerDispatcher Dispatcher for background work
 * @param mainDispatcher Dispatcher for main thread work
 */
class ItemsUiFacade(
    private val scope: CoroutineScope,
    private val classificationMode: StateFlow<ClassificationMode>,
    cloudClassificationEnabled: StateFlow<Boolean>,
    onDeviceClassifier: ItemClassifier,
    cloudClassifier: ItemClassifier,
    private val itemsStore: ScannedItemStore,
    stableItemCropper: ClassificationThumbnailProvider,
    private val visionInsightsPrefiller: VisionInsightsPrefiller,
    private val cropBasedEnricher: CropBasedEnricher,
    private val settingsRepository: SettingsRepository,
    telemetry: Telemetry?,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    aggregationConfig: com.scanium.app.AggregationConfig = AggregationPresets.NO_AGGREGATION,
) {
    companion object {
        private const val TAG = "ItemsUiFacade"
    }

    // ==================== Managers ====================

    /**
     * Manages item state, aggregation, and persistence.
     */
    private val stateManager =
        ItemsStateManager(
            scope = scope,
            itemsStore = itemsStore,
            initialWorkerDispatcher = workerDispatcher,
            initialMainDispatcher = mainDispatcher,
            aggregationConfig = aggregationConfig,
        )

    /**
     * Manages overlay track state for camera detection overlays.
     */
    private val overlayManager =
        OverlayTrackManager(
            stateManager = stateManager,
        )

    /**
     * Coordinates classification operations.
     */
    private val classificationCoordinator =
        ItemClassificationCoordinator(
            scope = scope,
            stateManager = stateManager,
            classificationMode = classificationMode,
            cloudClassificationEnabled = cloudClassificationEnabled,
            onDeviceClassifier = onDeviceClassifier,
            cloudClassifier = cloudClassifier,
            stableItemCropper = stableItemCropper,
            priceEstimatorProvider = null,
            telemetry = telemetry,
            workerDispatcher = workerDispatcher,
            mainDispatcher = mainDispatcher,
        )

    /**
     * Manages listing status for eBay integration.
     */
    private val listingManager =
        ListingStatusManager(
            scope = scope,
            itemsStore = itemsStore,
            workerDispatcher = workerDispatcher,
        )

    /**
     * Coordinates automatic duplicate detection and merge suggestions.
     */
    private val duplicateDetectionCoordinator =
        com.scanium.app.items.merging.DuplicateDetectionCoordinator(
            scope = scope,
            stateManager = stateManager,
            settingsRepository = settingsRepository,
            workerDispatcher = workerDispatcher,
        )

    // ==================== Public State ====================

    /** Current list of scanned items */
    val items: StateFlow<List<ScannedItem>> = stateManager.items

    /** Current overlay tracks for camera detection visualization */
    val overlayTracks: StateFlow<List<OverlayTrack>> = overlayManager.overlayTracks

    /** Events emitted when new items are added */
    val itemAddedEvents: SharedFlow<ScannedItem> = stateManager.itemAddedEvents

    /** Alerts for cloud classification errors */
    val cloudClassificationAlerts: SharedFlow<CloudClassificationAlert> = classificationCoordinator.cloudClassificationAlerts

    /** Alerts for persistence errors */
    private val _persistenceAlerts = MutableSharedFlow<PersistenceAlert>(extraBufferCapacity = 1)
    val persistenceAlerts: SharedFlow<PersistenceAlert> = _persistenceAlerts.asSharedFlow()

    /** UI events (e.g., navigation triggers) */
    private val _uiEvents = MutableSharedFlow<ItemsUiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<ItemsUiEvent> = _uiEvents.asSharedFlow()

    /** Current similarity threshold for aggregation */
    val similarityThreshold: StateFlow<Float> = stateManager.similarityThreshold

    /** Vision API quota exceeded event */
    val quotaExceededEvent: StateFlow<ItemsStateManager.QuotaExceededEvent?> = stateManager.quotaExceededEvent

    /** Latest in-memory export payload */
    private val _exportPayload = MutableStateFlow<ExportPayload?>(null)
    val exportPayload: StateFlow<ExportPayload?> = _exportPayload

    /** Merge suggestion state for smart duplicate detection */
    val mergeSuggestionState: StateFlow<com.scanium.app.items.merging.MergeSuggestionState> =
        duplicateDetectionCoordinator.mergeSuggestionState

    init {
        // Start classification session
        classificationCoordinator.startNewSession()

        // Connect managers
        stateManager.setOnStateChangedListener {
            classificationCoordinator.triggerEnhancedClassification()
            classificationCoordinator.syncPriceEstimations(stateManager.getScannedItems())
            overlayManager.refreshOverlayTracks()
            duplicateDetectionCoordinator.triggerDetection()
        }

        listingManager.setItemsReference(stateManager.items)

        // Collect persistence errors
        scope.launch {
            itemsStore.errors.collect { error ->
                _persistenceAlerts.emit(
                    PersistenceAlert(
                        message = "Unable to save scanned items. Please try again.",
                        operation = error.operation,
                        throwable = error.throwable,
                    ),
                )
            }
        }

        Log.i(TAG, "ItemsUiFacade initialized with managers")
    }

    // ==================== Item Operations ====================

    /**
     * Add a single item to the list.
     */
    fun addItem(item: ScannedItem) {
        stateManager.addItem(item)
        emitNavigateToItemListIfEnabled(1)
    }

    /**
     * Add a single item and trigger vision enrichment using its thumbnail.
     *
     * This is the primary path for detection-flow items where a thumbnail
     * is available but no Activity context is accessible. The enrichment
     * runs asynchronously and updates the item's visionAttributes when complete.
     */
    fun addItemWithThumbnailEnrichment(
        item: ScannedItem,
        thumbnail: com.scanium.shared.core.models.model.ImageRef?,
    ) {
        stateManager.addItem(item)
        if (thumbnail != null) {
            visionInsightsPrefiller.extractAndApplyFromThumbnail(
                scope = scope,
                stateManager = stateManager,
                itemId = item.id,
                thumbnail = thumbnail,
            )
        }
        emitNavigateToItemListIfEnabled(1)
    }

    /**
     * Add multiple items to the list.
     */
    fun addItems(newItems: List<ScannedItem>) {
        stateManager.addItems(newItems)
        emitNavigateToItemListIfEnabled(newItems.size)
    }

    /**
     * Add items with vision insights extraction.
     */
    fun addItemsWithVisionPrefill(
        context: Context,
        newItems: List<ScannedItem>,
    ) {
        Log.i(TAG, "addItemsWithVisionPrefill: ${newItems.size} items")

        val imageUriByOriginalId =
            newItems
                .filter { it.fullImageUri != null }
                .associate { it.id to it.fullImageUri!! }

        val aggregatedItems = stateManager.addItemsSync(newItems)

        for (aggregated in aggregatedItems) {
            val thumbnail = aggregated.thumbnail
            val uri =
                aggregated.fullImageUri
                    ?: aggregated.sourceDetectionIds.firstNotNullOfOrNull { imageUriByOriginalId[it] }

            if (thumbnail != null || uri != null) {
                visionInsightsPrefiller.extractAndApply(
                    context = context,
                    scope = scope,
                    stateManager = stateManager,
                    itemId = aggregated.aggregatedId,
                    imageUri = uri,
                    thumbnail = thumbnail,
                )
            }
        }

        emitNavigateToItemListIfEnabled(aggregatedItems.size)
    }

    /**
     * Extract vision insights for a specific item.
     */
    fun extractVisionInsights(
        context: Context,
        itemId: String,
        imageUri: Uri?,
    ) {
        if (imageUri == null) {
            Log.w(TAG, "Cannot extract vision insights - no image URI for item $itemId")
            return
        }
        visionInsightsPrefiller.extractAndApply(
            context = context,
            scope = scope,
            stateManager = stateManager,
            itemId = itemId,
            imageUri = imageUri,
            thumbnail = null,
        )
    }

    /**
     * Add items from multi-object capture and trigger crop-based enrichment.
     */
    fun addItemsFromMultiObjectCapture(
        context: Context,
        items: List<ScannedItem>,
        fullImageBitmap: android.graphics.Bitmap,
    ) {
        Log.i(TAG, "Processing ${items.size} items from multi-object capture")

        // Add items synchronously to get aggregated IDs
        val aggregatedItems = stateManager.addItemsSync(items)

        Log.i(TAG, "Aggregated ${aggregatedItems.size} items")

        // Trigger crop-based enrichment for each item
        for (aggregated in aggregatedItems) {
            val boundingBox = aggregated.boundingBox
            cropBasedEnricher.enrichFromCrop(
                context = context,
                scope = scope,
                fullImage = fullImageBitmap,
                boundingBox = boundingBox,
                itemId = aggregated.aggregatedId,
                stateManager = stateManager,
            )
        }

        // Emit navigation event if enabled
        emitNavigateToItemListIfEnabled(aggregatedItems.size)
    }

    /**
     * Remove an item from the list.
     */
    fun removeItem(itemId: String) {
        classificationCoordinator.cancelPriceEstimation(itemId)
        stateManager.removeItem(itemId)
    }

    /**
     * Restore a previously removed item.
     */
    fun restoreItem(item: ScannedItem) {
        stateManager.restoreItem(item)
    }

    /**
     * Clear all items from the list.
     */
    fun clearAllItems() {
        classificationCoordinator.cancelAllPriceEstimations()
        classificationCoordinator.reset()
        overlayManager.clear()
        stateManager.clearAllItems()
    }

    /**
     * Clear the quota exceeded event (called when dialog is dismissed).
     */
    fun clearQuotaExceededEvent() {
        stateManager.clearQuotaExceededEvent()
    }

    /**
     * Get the number of items in the list.
     */
    fun getItemCount(): Int = stateManager.getItemCount()

    /**
     * Get a specific item by ID.
     */
    fun getItem(itemId: String): ScannedItem? = stateManager.getItem(itemId)

    // ==================== Overlay Operations ====================

    /**
     * Update overlay detections from camera.
     */
    fun updateOverlayDetections(
        detections: List<DetectionResult>,
        scanRoi: ScanRoi = ScanRoi.DEFAULT,
        lockedTrackingId: String? = null,
        isGoodState: Boolean = false,
    ) {
        overlayManager.updateOverlayDetections(detections, scanRoi, lockedTrackingId, isGoodState)
    }

    /**
     * Check if there are detections outside the ROI.
     */
    fun hasDetectionsOutsideRoiOnly(): Boolean = overlayManager.hasDetectionsOutsideRoiOnly()

    /**
     * Last ROI filter result for diagnostics.
     */
    val lastRoiFilterResult = overlayManager.lastRoiFilterResult

    // ==================== Export Operations ====================

    /**
     * Create export payload for selected items.
     */
    fun createExportPayload(selectedIds: List<String>): ExportPayload? {
        val selectedItems =
            stateManager
                .getScannedItems()
                .filter { it.id in selectedIds }

        if (selectedItems.isEmpty()) {
            Log.w(TAG, "No items selected for export")
            return null
        }

        val payload = selectedItems.toExportPayload()
        _exportPayload.value = payload
        return payload
    }

    // ==================== Classification Operations ====================

    /**
     * Retry classification for a specific item.
     */
    fun retryClassification(itemId: String) {
        classificationCoordinator.retryClassification(itemId)
    }

    // ==================== Listing Operations ====================

    /**
     * Update listing status for an item.
     */
    fun updateListingStatus(
        itemId: String,
        status: ItemListingStatus,
        listingId: String? = null,
        listingUrl: String? = null,
    ) {
        stateManager.updateListingStatus(itemId, status, listingId, listingUrl)
        listingManager.updateListingStatus(itemId, status, listingId, listingUrl)
    }

    /**
     * Get listing status for an item.
     */
    fun getListingStatus(itemId: String): ItemListingStatus? = listingManager.getListingStatus(itemId)

    // ==================== Item Field Updates ====================

    /**
     * Update fields for a single item.
     */
    fun updateItemFields(
        itemId: String,
        labelText: String? = null,
        recognizedText: String? = null,
        barcodeValue: String? = null,
    ) {
        stateManager.updateItemFields(itemId, labelText, recognizedText, barcodeValue)
    }

    /**
     * Update fields for multiple items.
     */
    fun updateItemsFields(updates: Map<String, ItemFieldUpdate>) {
        stateManager.updateItemsFields(updates)
    }

    /**
     * Update a specific attribute for an item.
     */
    fun updateItemAttribute(
        itemId: String,
        attributeKey: String,
        value: ItemAttribute,
    ) {
        stateManager.updateItemAttribute(itemId, attributeKey, value)
    }

    /**
     * Update summary text for an item.
     */
    fun updateSummaryText(
        itemId: String,
        summaryText: String,
        userEdited: Boolean,
    ) {
        stateManager.updateSummaryText(itemId, summaryText, userEdited)
    }

    /**
     * Update export fields for an item.
     */
    fun updateExportFields(
        itemId: String,
        exportTitle: String?,
        exportDescription: String?,
        exportBullets: List<String>,
        exportFromCache: Boolean,
        exportModel: String?,
        exportConfidenceTier: String?,
    ) {
        stateManager.updateExportFields(
            itemId = itemId,
            exportTitle = exportTitle,
            exportDescription = exportDescription,
            exportBullets = exportBullets,
            exportFromCache = exportFromCache,
            exportModel = exportModel,
            exportConfidenceTier = exportConfidenceTier,
        )
    }

    // ==================== Photo Operations ====================

    /**
     * Add a photo to an item.
     */
    fun addPhotoToItem(
        itemId: String,
        photo: com.scanium.shared.core.models.items.ItemPhoto,
    ) {
        stateManager.addPhotoToItem(itemId, photo)
    }

    /**
     * Delete photos from an item.
     */
    fun deletePhotosFromItem(
        itemId: String,
        photoIds: Set<String>,
    ) {
        stateManager.removePhotosFromItem(itemId, photoIds)
    }

    // ==================== Aggregation Operations ====================

    /**
     * Update similarity threshold for aggregation.
     */
    fun updateSimilarityThreshold(threshold: Float) {
        stateManager.updateSimilarityThreshold(threshold)
    }

    /**
     * Get current similarity threshold.
     */
    fun getCurrentSimilarityThreshold(): Float = stateManager.getCurrentSimilarityThreshold()

    /**
     * Get aggregation statistics.
     */
    fun getAggregationStats(): AggregationStats = stateManager.getAggregationStats()

    /**
     * Remove stale items from the list.
     */
    fun removeStaleItems(maxAgeMs: Long = 30_000L) {
        stateManager.removeStaleItems(maxAgeMs)
    }

    // ==================== Merge Operations ====================

    /**
     * Dismiss current merge suggestions.
     */
    fun dismissMergeSuggestions() {
        duplicateDetectionCoordinator.dismissSuggestions()
    }

    /**
     * Accept all merge groups at once.
     * Merges all suggested similar items and clears suggestions.
     *
     * @param groups List of merge groups to accept
     */
    fun acceptAllMerges(groups: List<com.scanium.app.items.merging.MergeGroup>) {
        groups.forEach { group ->
            mergeItems(group.allItemIds, group.primaryItem.id)
        }
        duplicateDetectionCoordinator.acceptAllGroups()
    }

    /**
     * Accept a single merge group.
     * Merges the similar items into the primary item.
     *
     * @param group The merge group to accept
     */
    fun acceptMergeGroup(group: com.scanium.app.items.merging.MergeGroup) {
        mergeItems(group.allItemIds, group.primaryItem.id)
        duplicateDetectionCoordinator.acceptGroup(group)
    }

    /**
     * Reject a merge group (user chose not to merge).
     * Removes the group from suggestions without merging.
     *
     * @param group The merge group to reject
     */
    fun rejectMergeGroup(group: com.scanium.app.items.merging.MergeGroup) {
        duplicateDetectionCoordinator.rejectGroup(group)
    }

    /**
     * Merges multiple items into a single primary item.
     *
     * Strategy:
     * 1. Keep primary item unchanged
     * 2. For secondary items:
     *    - Consolidate attributes (merge unique values)
     *    - Copy photos to primary item
     *    - Increment merge count
     *    - Delete secondary items from state
     *
     * @param itemIds All item IDs in the group (primary + similar)
     * @param keepPrimaryId ID of the item to keep as primary
     */
    private fun mergeItems(
        itemIds: List<String>,
        keepPrimaryId: String,
    ) {
        scope.launch(workerDispatcher) {
            Log.i(TAG, "Merging ${itemIds.size} items, keeping primary: $keepPrimaryId")

            val allItems = stateManager.getScannedItems()
            val itemsToMerge = allItems.filter { it.id in itemIds }

            if (itemsToMerge.isEmpty()) {
                Log.w(TAG, "No items found to merge")
                return@launch
            }

            val primaryItem = itemsToMerge.find { it.id == keepPrimaryId }
            if (primaryItem == null) {
                Log.w(TAG, "Primary item not found: $keepPrimaryId")
                return@launch
            }

            val secondaryItems = itemsToMerge.filter { it.id != keepPrimaryId }

            // TODO(Phase 5): Implement actual merge logic
            // - Consolidate attributes from secondary items
            // - Copy photos from secondary items to primary
            // - Increment merge count on primary
            // - Delete secondary items from state manager

            // For now, just delete the secondary items (simple merge)
            secondaryItems.forEach { secondaryItem ->
                stateManager.removeItem(secondaryItem.id)
                Log.i(TAG, "Removed secondary item: ${secondaryItem.id}")
            }

            Log.i(TAG, "Merge complete. Kept primary: $keepPrimaryId, removed ${secondaryItems.size} items")
        }
    }

    // ==================== Telemetry Operations ====================

    /**
     * Enable telemetry.
     */
    fun enableTelemetry() {
        stateManager.enableTelemetry()
    }

    /**
     * Disable telemetry.
     */
    fun disableTelemetry() {
        stateManager.disableTelemetry()
    }

    /**
     * Check if telemetry is enabled.
     */
    fun isTelemetryEnabled(): Boolean = stateManager.isTelemetryEnabled()

    // ==================== Private Helpers ====================

    /**
     * Emit navigation event to item list if setting is enabled.
     */
    private fun emitNavigateToItemListIfEnabled(itemCount: Int) {
        if (itemCount > 0) {
            scope.launch {
                val shouldAutoOpen = settingsRepository.openItemListAfterScanFlow.first()
                if (shouldAutoOpen) {
                    Log.i(TAG, "Auto-opening item list after scan (items added: $itemCount)")
                    _uiEvents.emit(ItemsUiEvent.NavigateToItemList)
                }
            }
        }
    }
}
