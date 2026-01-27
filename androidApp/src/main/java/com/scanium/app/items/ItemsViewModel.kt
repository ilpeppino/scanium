package com.scanium.app.items

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanium.app.AggregationStats
import com.scanium.app.camera.CameraXManager
import com.scanium.app.camera.OverlayTrack
import com.scanium.app.camera.detection.DetectionEvent
import com.scanium.app.classification.hypothesis.ClassificationHypothesis
import com.scanium.app.classification.hypothesis.CorrectionDialogData
import com.scanium.app.classification.hypothesis.HypothesisSelectionState
import com.scanium.app.classification.hypothesis.MultiHypothesisResult
import com.scanium.app.domain.DomainPackProvider
import com.scanium.app.domain.category.CategorySelectionInput
import com.scanium.app.items.classification.ItemClassificationCoordinator
import com.scanium.app.items.listing.ListingStatusManager
import com.scanium.app.items.overlay.OverlayTrackManager
import com.scanium.app.items.persistence.ScannedItemStore
import com.scanium.app.items.state.ItemsStateManager
import com.scanium.app.ml.CropBasedEnricher
import com.scanium.app.ml.DetectionResult
import com.scanium.app.ml.VisionInsightsPrefiller
import com.scanium.app.ml.classification.ClassificationMode
import com.scanium.app.ml.classification.ClassificationThumbnailProvider
import com.scanium.app.ml.classification.ItemClassifier
import com.scanium.app.model.toBitmap
import com.scanium.core.export.ExportPayload
import com.scanium.core.models.scanning.ScanRoi
import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.shared.core.models.ml.ItemCategory
import com.scanium.telemetry.facade.Telemetry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named

/**
 * ViewModel for managing detected items across the app.
 *
 * Shared between CameraScreen and ItemsListScreen.
 * Acts as a facade that delegates to specialized managers:
 * - [ItemsStateManager]: StateFlow management, CRUD operations, persistence
 * - [ItemClassificationCoordinator]: Classification orchestration, retry, cloud gating
 * - [OverlayTrackManager]: Camera overlay state
 * - [ListingStatusManager]: eBay posting status
 *
 * This refactoring follows the Single Responsibility Principle by decomposing
 * the original 400+ line ViewModel into focused, testable components.
 *
 * Part of ARCH-001: Migrated to Hilt dependency injection.
 *
 * @see ItemsStateManager
 * @see ItemClassificationCoordinator
 * @see OverlayTrackManager
 * @see ListingStatusManager
 */
@HiltViewModel
class ItemsViewModel
    @Inject
    constructor(
        private val classificationMode: StateFlow<@JvmSuppressWildcards ClassificationMode>,
        @Named("cloudClassificationEnabled") cloudClassificationEnabled: StateFlow<Boolean>,
        @Named("onDevice") onDeviceClassifier: ItemClassifier,
        @Named("cloud") private val cloudClassifier: ItemClassifier,
        private val itemsStore: ScannedItemStore,
        private val stableItemCropper: ClassificationThumbnailProvider,
        private val visionInsightsPrefiller: VisionInsightsPrefiller,
        private val cropBasedEnricher: CropBasedEnricher,
        private val settingsRepository: com.scanium.app.data.SettingsRepository,
        telemetry: Telemetry?,
    ) : ViewModel() {
        // Default dispatchers (can be overridden in tests)
        private var workerDispatcher: CoroutineDispatcher = Dispatchers.Default
        private var mainDispatcher: CoroutineDispatcher = Dispatchers.Main

        // Test aggregation config (null means use production default)
        private var testAggregationConfig: com.scanium.app.AggregationConfig? = null

        // Internal constructor for testing with custom dispatchers and aggregation config
        internal constructor(
            classificationMode: StateFlow<ClassificationMode>,
            cloudClassificationEnabled: StateFlow<Boolean>,
            onDeviceClassifier: ItemClassifier,
            cloudClassifier: ItemClassifier,
            itemsStore: ScannedItemStore,
            stableItemCropper: ClassificationThumbnailProvider,
            visionInsightsPrefiller: VisionInsightsPrefiller,
            cropBasedEnricher: CropBasedEnricher,
            settingsRepository: com.scanium.app.data.SettingsRepository,
            telemetry: Telemetry?,
            workerDispatcher: CoroutineDispatcher,
            mainDispatcher: CoroutineDispatcher,
            aggregationConfig: com.scanium.app.AggregationConfig,
        ) : this(
            classificationMode = classificationMode,
            cloudClassificationEnabled = cloudClassificationEnabled,
            onDeviceClassifier = onDeviceClassifier,
            cloudClassifier = cloudClassifier,
            itemsStore = itemsStore,
            stableItemCropper = stableItemCropper,
            visionInsightsPrefiller = visionInsightsPrefiller,
            cropBasedEnricher = cropBasedEnricher,
            settingsRepository = settingsRepository,
            telemetry = telemetry,
        ) {
            // Set test dispatchers and aggregation config before facade is lazily initialized
            this.workerDispatcher = workerDispatcher
            this.mainDispatcher = mainDispatcher
            this.testAggregationConfig = aggregationConfig
        }

        companion object {
            private const val TAG = "ItemsViewModel"
            private const val QR_URL_TTL_MS = 2000L
        }

        // ==================== Facade ====================

        /**
         * Facade that encapsulates manager coordination.
         * Delegates to ItemsStateManager, OverlayTrackManager, ItemClassificationCoordinator, and ListingStatusManager.
         * Lazily initialized to ensure dispatchers are set first.
         */
        private val facade by lazy {
            ItemsUiFacade(
                scope = viewModelScope,
                classificationMode = classificationMode,
                cloudClassificationEnabled = cloudClassificationEnabled,
                onDeviceClassifier = onDeviceClassifier,
                cloudClassifier = cloudClassifier,
                itemsStore = itemsStore,
                stableItemCropper = stableItemCropper,
                visionInsightsPrefiller = visionInsightsPrefiller,
                cropBasedEnricher = cropBasedEnricher,
                settingsRepository = settingsRepository,
                telemetry = telemetry,
                workerDispatcher = workerDispatcher,
                mainDispatcher = mainDispatcher,
                aggregationConfig = testAggregationConfig ?: com.scanium.app.AggregationPresets.NO_AGGREGATION,
            ).also {
                Log.i(TAG, "ItemsViewModel initialized with facade")
            }
        }

        // ==================== Public State (Delegated to Facade) ====================

        /** Current list of scanned items */
        val items: StateFlow<List<ScannedItem>> get() = facade.items

        /** Current overlay tracks for camera detection visualization */
        val overlayTracks: StateFlow<List<OverlayTrack>> get() = facade.overlayTracks

        /** Events emitted when new items are added (for animations) */
        val itemAddedEvents: SharedFlow<ScannedItem> get() = facade.itemAddedEvents

        /** Alerts for cloud classification errors */
        val cloudClassificationAlerts: SharedFlow<CloudClassificationAlert> get() = facade.cloudClassificationAlerts

        /** Alerts for persistence errors */
        val persistenceAlerts: SharedFlow<PersistenceAlert> get() = facade.persistenceAlerts

        /** Vision API quota exceeded event */
        val quotaExceededEvent: StateFlow<ItemsStateManager.QuotaExceededEvent?> get() = facade.quotaExceededEvent

        /** UI events (e.g., navigation triggers) */
        val uiEvents: SharedFlow<ItemsUiEvent> get() = facade.uiEvents

        /** Current similarity threshold for aggregation */
        val similarityThreshold: StateFlow<Float> get() = facade.similarityThreshold

        /** Latest in-memory export payload for selected items */
        val exportPayload: StateFlow<ExportPayload?> get() = facade.exportPayload

        /** Merge suggestion state for smart duplicate detection */
        val mergeSuggestionState: StateFlow<com.scanium.app.items.merging.MergeSuggestionState> get() = facade.mergeSuggestionState

        /** Latest detected QR URL (if any) - ViewModel-specific with TTL */
        private val _latestQrUrl = MutableStateFlow<String?>(null)
        val latestQrUrl: StateFlow<String?> = _latestQrUrl

        /** Timestamp of the last detected QR URL - ViewModel-specific */
        private val _lastQrSeenTimestampMs = MutableStateFlow(0L)
        val lastQrSeenTimestampMs: StateFlow<Long> = _lastQrSeenTimestampMs

        /** Hypothesis selection state for multi-hypothesis classification */
        private val _hypothesisSelectionState =
            MutableStateFlow<HypothesisSelectionState>(
                HypothesisSelectionState.Hidden,
            )
        val hypothesisSelectionState: StateFlow<HypothesisSelectionState> = _hypothesisSelectionState

        /** Correction dialog state for classification errors */
        private val _showCorrectionDialog = MutableStateFlow(false)
        val showCorrectionDialog: StateFlow<Boolean> = _showCorrectionDialog

        private val _correctionDialogData = MutableStateFlow<CorrectionDialogData?>(null)
        val correctionDialogData: StateFlow<CorrectionDialogData?> = _correctionDialogData

        /** Pending detections awaiting classification/confirmation */
        private val _pendingDetections = MutableStateFlow<List<PendingDetectionState>>(emptyList())

        /** Count of pending detections for UI badge */
        val pendingDetectionCount: StateFlow<Int> =
            _pendingDetections
                .map { list -> list.size }
                .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

        /** Current pending detection being shown (front of queue) */
        val currentPendingDetection: StateFlow<PendingDetectionState> =
            _pendingDetections
                .map { list -> list.firstOrNull() ?: PendingDetectionState.None }
                .stateIn(viewModelScope, SharingStarted.Eagerly, PendingDetectionState.None)

        /** Current ROI filter result for diagnostics */
        val lastRoiFilterResult get() = facade.lastRoiFilterResult

        // ==================== Item CRUD Operations (Delegated to Facade) ====================

        /**
         * Adds a single detected item to the list.
         * If auto-open setting is enabled, emits a navigation event to the items list.
         */
        fun addItem(item: ScannedItem) {
            facade.addItem(item)
        }

        /**
         * Adds multiple detected items at once.
         * If auto-open setting is enabled, emits a navigation event to the items list.
         */
        fun addItems(newItems: List<ScannedItem>) {
            facade.addItems(newItems)
        }

        /**
         * Adds items and immediately triggers vision insights extraction for items with high-res images.
         */
        fun addItemsWithVisionPrefill(
            context: Context,
            newItems: List<ScannedItem>,
        ) {
            facade.addItemsWithVisionPrefill(context, newItems)
        }

        /**
         * Triggers vision insights extraction for a specific item.
         */
        fun extractVisionInsights(
            context: Context,
            itemId: String,
            imageUri: Uri?,
        ) {
            facade.extractVisionInsights(context, itemId, imageUri)
        }

        /**
         * Adds items from a multi-object capture and triggers crop-based enrichment.
         *
         * This is the primary entry point for the multi-object scanning flow.
         */
        fun addItemsFromMultiObjectCapture(
            context: Context,
            captureResult: CameraXManager.MultiObjectCaptureResult,
        ) {
            Log.i(TAG, "╔════════════════════════════════════════════════════════════════")
            Log.i(TAG, "║ MULTI_OBJECT: addItemsFromMultiObjectCapture() CALLED")
            Log.i(TAG, "║ items.size=${captureResult.items.size}")
            Log.i(TAG, "║ sourcePhotoId=${captureResult.sourcePhotoId}")
            Log.i(TAG, "╚════════════════════════════════════════════════════════════════")

            facade.addItemsFromMultiObjectCapture(
                context = context,
                items = captureResult.items,
                fullImageBitmap = captureResult.fullImageBitmap,
            )

            // Note: fullImageBitmap should be recycled by the caller after this returns
            // The enricher makes copies of crops as needed
        }

        /**
         * Removes a specific item by ID.
         */
        fun removeItem(itemId: String) {
            facade.removeItem(itemId)
        }

        /**
         * Restores a previously removed item.
         */
        fun restoreItem(item: ScannedItem) {
            facade.restoreItem(item)
        }

        /**
         * Clears all detected items.
         */
        fun clearAllItems() {
            facade.clearAllItems()
        }

        /**
         * Returns the current count of detected items.
         */
        fun getItemCount(): Int = facade.getItemCount()

        /**
         * Gets a specific item by ID.
         */
        fun getItem(itemId: String): ScannedItem? = facade.getItem(itemId)

        // ==================== Overlay Operations (Delegated to Facade) ====================

        /**
         * Updates overlay with new detections from the camera.
         */
        fun updateOverlayDetections(
            detections: List<DetectionResult>,
            scanRoi: ScanRoi = ScanRoi.DEFAULT,
            lockedTrackingId: String? = null,
            isGoodState: Boolean = false,
        ) {
            facade.updateOverlayDetections(detections, scanRoi, lockedTrackingId, isGoodState)
        }

        /**
         * Check if detections exist but none are inside ROI.
         */
        fun hasDetectionsOutsideRoiOnly(): Boolean = facade.hasDetectionsOutsideRoiOnly()

        /**
         * Update QR URL overlay state based on detection router events.
         * ViewModel handles BarcodeDetected events with TTL logic.
         */
        fun onDetectionEvent(event: DetectionEvent) {
            when (event) {
                is DetectionEvent.BarcodeDetected -> updateQrUrl(event)
                else -> Unit // Other events can be handled here if needed
            }
        }

        // ==================== Export Operations (Delegated to Facade) ====================

        /**
         * Create an in-memory export payload for selected items.
         */
        fun createExportPayload(selectedIds: List<String>): ExportPayload? = facade.createExportPayload(selectedIds)

        // ==================== Classification Operations (Delegated to Facade) ====================

        /**
         * Retry classification for a failed item.
         */
        fun retryClassification(itemId: String) {
            facade.retryClassification(itemId)
        }

        // ==================== Listing Operations (Delegated to Facade) ====================

        /**
         * Updates the listing status of a scanned item.
         */
        fun updateListingStatus(
            itemId: String,
            status: ItemListingStatus,
            listingId: String? = null,
            listingUrl: String? = null,
        ) {
            facade.updateListingStatus(itemId, status, listingId, listingUrl)
        }

        /**
         * Gets the listing status for a specific item.
         */
        fun getListingStatus(itemId: String): ItemListingStatus? = facade.getListingStatus(itemId)

        // ==================== Merge Operations (Delegated to Facade) ====================

        /**
         * Dismisses current merge suggestions.
         */
        fun dismissMergeSuggestions() {
            facade.dismissMergeSuggestions()
        }

        /**
         * Accepts all merge suggestions at once.
         * Merges all similar items into their respective primary items.
         */
        fun acceptAllMerges(groups: List<com.scanium.app.items.merging.MergeGroup>) {
            facade.acceptAllMerges(groups)
        }

        /**
         * Accepts a single merge group.
         * Merges the similar items into the primary item.
         */
        fun acceptMergeGroup(group: com.scanium.app.items.merging.MergeGroup) {
            facade.acceptMergeGroup(group)
        }

        /**
         * Rejects a merge group without merging.
         * Removes the group from suggestions.
         */
        fun rejectMergeGroup(group: com.scanium.app.items.merging.MergeGroup) {
            facade.rejectMergeGroup(group)
        }

        // ==================== Item Edit Operations (Delegated to Facade) ====================

        /**
         * Updates user-editable fields of a scanned item.
         */
        fun updateItemFields(
            itemId: String,
            labelText: String? = null,
            recognizedText: String? = null,
            barcodeValue: String? = null,
        ) {
            facade.updateItemFields(itemId, labelText, recognizedText, barcodeValue)
        }

        /**
         * Updates multiple items at once with their new field values.
         */
        fun updateItemsFields(updates: Map<String, com.scanium.app.items.state.ItemFieldUpdate>) {
            facade.updateItemsFields(updates)
        }

        /**
         * Updates a single attribute for an item.
         */
        fun updateItemAttribute(
            itemId: String,
            attributeKey: String,
            attribute: com.scanium.shared.core.models.items.ItemAttribute,
        ) {
            facade.updateItemAttribute(itemId, attributeKey, attribute)
        }

        /**
         * Updates the summary text for an item.
         */
        fun updateSummaryText(
            itemId: String,
            summaryText: String,
            userEdited: Boolean,
        ) {
            facade.updateSummaryText(itemId, summaryText, userEdited)
        }

        /**
         * Update export assistant fields for an item.
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
            facade.updateExportFields(
                itemId = itemId,
                exportTitle = exportTitle,
                exportDescription = exportDescription,
                exportBullets = exportBullets,
                exportFromCache = exportFromCache,
                exportModel = exportModel,
                exportConfidenceTier = exportConfidenceTier,
            )
        }

        /**
         * Add an additional photo to an existing item and trigger re-enrichment.
         * Used by the item detail screen when user adds more photos to improve detection.
         *
         * @param context Android context for loading images
         * @param itemId The ID of the item to add the photo to
         * @param photoUri URI of the captured photo
         * @see ItemsStateManager.addPhotoToItem
         */
        fun addPhotoToItem(
            context: Context,
            itemId: String,
            photoUri: Uri,
        ) {
            viewModelScope.launch(workerDispatcher) {
                try {
                    // Load the photo
                    val bitmap =
                        android.graphics.BitmapFactory.decodeStream(
                            context.contentResolver.openInputStream(photoUri),
                        ) ?: run {
                            Log.e(TAG, "Failed to load photo from URI for item $itemId")
                            return@launch
                        }

                    // Save to internal storage
                    val photosDir = java.io.File(context.filesDir, "item_photos/$itemId")
                    photosDir.mkdirs()
                    val photoFile = java.io.File(photosDir, "${java.util.UUID.randomUUID()}.jpg")
                    photoFile.outputStream().use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    val savedUri = photoFile.absolutePath

                    // Create ItemPhoto
                    val photo =
                        com.scanium.shared.core.models.items.ItemPhoto(
                            id =
                                java.util.UUID
                                    .randomUUID()
                                    .toString(),
                            uri = savedUri,
                            bytes = null,
                            mimeType = "image/jpeg",
                            width = bitmap.width,
                            height = bitmap.height,
                            capturedAt =
                                kotlinx.datetime.Clock.System
                                    .now()
                                    .toEpochMilliseconds(),
                            photoHash = null,
                            photoType = com.scanium.shared.core.models.items.PhotoType.CLOSEUP,
                        )

                    // Add to item
                    facade.addPhotoToItem(itemId, photo)

                    Log.i(TAG, "Added photo to item $itemId, triggering re-enrichment")

                    // Trigger re-enrichment with the new photo
                    facade.extractVisionInsights(context, itemId, photoUri)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to add photo to item $itemId", e)
                }
            }
        }

        /**
         * Delete multiple photos from an item.
         *
         * This method:
         * - Removes the photos from the item's additionalPhotos list
         * - Deletes the actual files from disk
         * - Updates the item in state
         * - Triggers re-enrichment using remaining photos (including primary thumbnail)
         *
         * @param context Android context for file operations
         * @param itemId The ID of the item
         * @param photoIds Set of photo IDs to delete
         * @param onComplete Callback invoked when deletion completes
         */
        fun deletePhotosFromItem(
            context: Context,
            itemId: String,
            photoIds: Set<String>,
            onComplete: () -> Unit = {},
        ) {
            if (photoIds.isEmpty()) {
                onComplete()
                return
            }

            viewModelScope.launch(workerDispatcher) {
                try {
                    Log.i(TAG, "Deleting ${photoIds.size} photo(s) from item $itemId")

                    // Get the item
                    val item = facade.getItem(itemId)
                    if (item == null) {
                        Log.e(TAG, "Cannot delete photos - item not found: $itemId")
                        withContext(mainDispatcher) { onComplete() }
                        return@launch
                    }

                    // Find photos to delete
                    val photosToDelete = item.additionalPhotos.filter { it.id in photoIds }
                    if (photosToDelete.isEmpty()) {
                        Log.w(TAG, "No photos found to delete for item $itemId")
                        withContext(mainDispatcher) { onComplete() }
                        return@launch
                    }

                    // Delete files from disk
                    var deletedCount = 0
                    photosToDelete.forEach { photo ->
                        photo.uri?.let { uri ->
                            try {
                                val file = java.io.File(uri)
                                if (file.exists() && file.delete()) {
                                    deletedCount++
                                    Log.d(TAG, "Deleted photo file: ${file.name}")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to delete photo file: ${photo.uri}", e)
                            }
                        }
                    }
                    Log.i(TAG, "Deleted $deletedCount file(s) from disk")

                    // Remove photos from item in state
                    facade.deletePhotosFromItem(itemId, photoIds)

                    Log.i(TAG, "Photos removed from item state, triggering re-enrichment")

                    // Trigger re-enrichment with remaining photos
                    // Use the primary thumbnail for re-classification
                    val updatedItem = facade.getItem(itemId)
                    if (updatedItem != null) {
                        val thumbnailRef = updatedItem.thumbnailRef ?: updatedItem.thumbnail
                        if (thumbnailRef != null) {
                            facade.extractVisionInsights(context, itemId, null)
                        }
                    }

                    withContext(mainDispatcher) {
                        onComplete()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete photos from item $itemId", e)
                    withContext(mainDispatcher) {
                        onComplete()
                    }
                }
            }
        }

        // ==================== Threshold Operations (Delegated to Facade) ====================

        /**
         * Update the similarity threshold for real-time tuning.
         */
        fun updateSimilarityThreshold(threshold: Float) {
            facade.updateSimilarityThreshold(threshold)
        }

        /**
         * Get the current effective similarity threshold.
         */
        fun getCurrentSimilarityThreshold(): Float = facade.getCurrentSimilarityThreshold()

        // ==================== Aggregation Statistics (Delegated to Facade) ====================

        /**
         * Get aggregation statistics for monitoring/debugging.
         */
        fun getAggregationStats(): AggregationStats = facade.getAggregationStats()

        /**
         * Remove stale items that haven't been seen recently.
         */
        fun removeStaleItems(maxAgeMs: Long = 30_000L) {
            facade.removeStaleItems(maxAgeMs)
        }

        // ==================== Telemetry (Delegated to Facade) ====================

        /**
         * Enable async telemetry collection.
         */
        fun enableTelemetry() {
            facade.enableTelemetry()
        }

        /**
         * Disable async telemetry collection.
         */
        fun disableTelemetry() {
            facade.disableTelemetry()
        }

        /**
         * Check if async telemetry is currently enabled.
         */
        fun isTelemetryEnabled(): Boolean = facade.isTelemetryEnabled()

        /**
         * Clear the quota exceeded event (called when dialog is dismissed).
         */
        fun clearQuotaExceededEvent() = facade.clearQuotaExceededEvent()

        // ==================== Lifecycle ====================

        override fun onCleared() {
            super.onCleared()
            facade.disableTelemetry()
            Log.i(TAG, "ItemsViewModel cleared")
        }

        private var qrUrlClearJob: Job? = null

        private fun updateQrUrl(event: DetectionEvent.BarcodeDetected) {
            val url =
                event.items
                    .asSequence()
                    .mapNotNull { it.barcodeValue }
                    .mapNotNull(::parseUrl)
                    .lastOrNull()
                    ?: return

            _latestQrUrl.value = url
            _lastQrSeenTimestampMs.value = event.timestampMs

            qrUrlClearJob?.cancel()
            qrUrlClearJob =
                viewModelScope.launch {
                    delay(QR_URL_TTL_MS)
                    val lastSeen = _lastQrSeenTimestampMs.value
                    if (System.currentTimeMillis() - lastSeen >= QR_URL_TTL_MS) {
                        _latestQrUrl.value = null
                    }
                }
        }

        private fun parseUrl(value: String): String? {
            val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase(Locale.US)
            return if (scheme == "http" || scheme == "https") value else null
        }

        // ==================== Multi-Hypothesis Classification ====================

        /**
         * Show hypothesis selection sheet after first classification.
         */
        fun showHypothesisSelection(
            result: MultiHypothesisResult,
            itemId: String,
            thumbnailRef: com.scanium.shared.core.models.model.ImageRef?,
        ) {
            _hypothesisSelectionState.value =
                HypothesisSelectionState.Showing(
                    result = result,
                    itemId = itemId,
                    thumbnailRef = thumbnailRef,
                )
        }

        /**
         * User confirmed a hypothesis - apply it to the item.
         */
        fun confirmHypothesis(
            itemId: String,
            hypothesis: ClassificationHypothesis,
        ) {
            viewModelScope.launch(workerDispatcher) {
                // Update item label with confirmed hypothesis
                facade.updateItemFields(
                    itemId = itemId,
                    labelText = hypothesis.categoryName,
                )

                // TODO: Also update domainCategoryId in the item
                // Currently updateItemFields doesn't support this field
                // May need to add to ItemFieldUpdate or use a different approach

                withContext(mainDispatcher) {
                    _hypothesisSelectionState.value = HypothesisSelectionState.Hidden
                }
            }
        }

        /**
         * Dismiss hypothesis selection sheet.
         */
        fun dismissHypothesisSelection() {
            _hypothesisSelectionState.value = HypothesisSelectionState.Hidden
        }

        /**
         * Show correction dialog when user taps "None of these".
         */
        fun showCorrectionDialog(
            itemId: String,
            imageHash: String,
            predictedCategory: String?,
            predictedConfidence: Float?,
        ) {
            _correctionDialogData.value =
                CorrectionDialogData(
                    itemId = itemId,
                    imageHash = imageHash,
                    predictedCategory = predictedCategory,
                    predictedConfidence = predictedConfidence,
                )
            _showCorrectionDialog.value = true
        }

        /**
         * Submit a classification correction.
         * Stores locally and syncs to backend.
         */
        fun submitCorrection(
            itemId: String,
            imageHash: String,
            predictedCategory: String?,
            predictedConfidence: Float?,
            correctedCategory: String,
            notes: String?,
        ) {
            viewModelScope.launch(workerDispatcher) {
                try {
                    // TODO: Store correction locally in Room database
                    // TODO: Sync to backend via /v1/corrections API

                    Log.i(TAG, "Correction submitted for item $itemId: $correctedCategory")

                    val pending =
                        _pendingDetections.value.find {
                            when (it) {
                                is PendingDetectionState.AwaitingClassification -> it.detectionId == itemId
                                is PendingDetectionState.ShowingHypotheses -> it.detectionId == itemId
                                else -> false
                            }
                        }

                    if (pending != null) {
                        val rawDetection =
                            when (pending) {
                                is PendingDetectionState.AwaitingClassification -> pending.rawDetection
                                is PendingDetectionState.ShowingHypotheses -> pending.rawDetection
                                else -> null
                            }

                        if (rawDetection != null) {
                            val hypothesis =
                                ClassificationHypothesis(
                                    categoryId = "",
                                    categoryName = correctedCategory,
                                    explanation = "",
                                    confidence = predictedConfidence ?: rawDetection.confidence,
                                    confidenceBand = "LOW",
                                    attributes = emptyMap(),
                                )
                            createItemFromDetection(itemId, rawDetection, hypothesis)
                        }
                    } else {
                        // Update the item with the corrected category
                        facade.updateItemFields(
                            itemId = itemId,
                            labelText = correctedCategory,
                        )
                    }

                    withContext(mainDispatcher) {
                        if (pending != null) {
                            _pendingDetections.value =
                                _pendingDetections.value.filterNot {
                                    when (it) {
                                        is PendingDetectionState.AwaitingClassification -> it.detectionId == itemId
                                        is PendingDetectionState.ShowingHypotheses -> it.detectionId == itemId
                                        else -> false
                                    }
                                }
                        }
                        _showCorrectionDialog.value = false
                        _correctionDialogData.value = null
                        _hypothesisSelectionState.value = HypothesisSelectionState.Hidden
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to submit correction", e)
                }
            }
        }

        /**
         * Dismiss correction dialog.
         */
        fun dismissCorrectionDialog() {
            _showCorrectionDialog.value = false
            _correctionDialogData.value = null
        }

        // ==================== Pending Detection Handlers ====================

        /**
         * Handle new detection from camera - add to pending queue and trigger classification.
         *
         * This implements the "no items before confirmation" principle:
         * detections are held in pending state until user confirms a hypothesis.
         *
         * @param rawDetection Raw detection data from ML Kit/ObjectTracker
         */
        fun onDetectionReady(rawDetection: RawDetection) {
            val detectionId = UUID.randomUUID().toString()
            val pendingState =
                PendingDetectionState.AwaitingClassification(
                    detectionId = detectionId,
                    rawDetection = rawDetection,
                    thumbnailRef = rawDetection.thumbnailRef,
                    timestamp = System.currentTimeMillis(),
                )

            viewModelScope.launch(workerDispatcher) {
                val forceHypothesisSheet = settingsRepository.devForceHypothesisSelectionFlow.first()
                if (forceHypothesisSheet) {
                    settingsRepository.setDevForceHypothesisSelection(false)
                }
                // Add to queue (max 5 to prevent memory issues)
                val currentQueue = _pendingDetections.value
                val trimmedQueue =
                    if (currentQueue.size >= 5) {
                        Log.w(TAG, "Pending queue full, dropping oldest detection without auto-confirm")
                        currentQueue.drop(1)
                    } else {
                        currentQueue
                    }

                withContext(mainDispatcher) {
                    _pendingDetections.value = trimmedQueue + pendingState
                }

                // Trigger classification for this detection
                if (forceHypothesisSheet) {
                    Log.d(TAG, "DEV: Forcing hypothesis sheet for detection $detectionId")
                    showFallbackHypothesisSheet(detectionId, rawDetection)
                } else {
                    triggerMultiHypothesisClassification(detectionId, rawDetection)
                }
            }
        }

        /**
         * Trigger multi-hypothesis classification for a pending detection.
         *
         * Calls backend cloud classifier with multi-hypothesis mode.
         * On success, updates pending state to ShowingHypotheses and displays the hypothesis sheet.
         * On failure, falls back to creating item with on-device label.
         *
         * @param detectionId Unique identifier for this pending detection
         * @param rawDetection Raw detection data to classify
         */
        private suspend fun triggerMultiHypothesisClassification(
            detectionId: String,
            rawDetection: RawDetection,
        ) {
            withContext(workerDispatcher) {
                try {
                    // Get WYSIWYG thumbnail for classification (matches what user sees in bounding box)
                    val bitmap = rawDetection.thumbnailRef?.toBitmap()
                    if (bitmap == null) {
                        Log.w(TAG, "No WYSIWYG thumbnail available for classification, using fallback")
                        showFallbackHypothesisSheet(detectionId, rawDetection)
                        return@withContext
                    }

                    // Cast to CloudClassifier to access multi-hypothesis method
                    val cloudClassifierImpl = cloudClassifier as? com.scanium.app.ml.classification.CloudClassifier
                    if (cloudClassifierImpl == null) {
                        Log.w(TAG, "Cloud classifier not available, using fallback")
                        showFallbackHypothesisSheet(detectionId, rawDetection)
                        return@withContext
                    }

                    Log.d(
                        TAG,
                        "Triggering multi-hypothesis classification for detection $detectionId with WYSIWYG thumbnail ${bitmap.width}x${bitmap.height}",
                    )

                    // Call cloud classifier with multi-hypothesis mode
                    val result = cloudClassifierImpl.classifyMultiHypothesis(bitmap)

                    if (result != null && result.hypotheses.isNotEmpty()) {
                        Log.d(TAG, "Received ${result.hypotheses.size} hypotheses for detection $detectionId")
                        showHypothesisSelectionFromResult(detectionId, rawDetection, result)
                    } else {
                        // Fallback: No hypotheses returned, create with on-device label
                        Log.w(TAG, "No hypotheses returned for detection $detectionId, using fallback")
                        showFallbackHypothesisSheet(detectionId, rawDetection)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Classification failed for detection $detectionId", e)
                    // Fallback: Show hypothesis sheet with on-device label
                    showFallbackHypothesisSheet(detectionId, rawDetection)
                }
            }
        }

        /**
         * User confirmed a hypothesis - create the item with confirmed category.
         *
         * @param detectionId Unique identifier for this pending detection
         * @param hypothesis User-selected hypothesis from multi-hypothesis results
         */
        fun confirmPendingDetection(
            detectionId: String,
            hypothesis: ClassificationHypothesis,
        ) {
            viewModelScope.launch(workerDispatcher) {
                val pending =
                    _pendingDetections.value.find {
                        (it as? PendingDetectionState.ShowingHypotheses)?.detectionId == detectionId
                    } as? PendingDetectionState.ShowingHypotheses

                if (pending == null) {
                    Log.e(TAG, "No pending detection found for $detectionId")
                    return@launch
                }

                createItemFromDetection(detectionId, pending.rawDetection, hypothesis)

                withContext(mainDispatcher) {
                    // Remove from queue
                    _pendingDetections.value =
                        _pendingDetections.value.filterNot {
                            (it as? PendingDetectionState.ShowingHypotheses)?.detectionId == detectionId
                        }

                    // Hide hypothesis sheet
                    _hypothesisSelectionState.value = HypothesisSelectionState.Hidden
                }
            }
        }

        /**
         * User dismissed hypothesis sheet - create item with on-device label fallback.
         *
         * @param detectionId Unique identifier for this pending detection
         */
        fun dismissPendingDetection(detectionId: String) {
            viewModelScope.launch(workerDispatcher) {
                removeFromPendingQueue(detectionId)
                withContext(mainDispatcher) {
                    _hypothesisSelectionState.value = HypothesisSelectionState.Hidden
                }
            }
        }

        private suspend fun showFallbackHypothesisSheet(
            detectionId: String,
            rawDetection: RawDetection,
        ) {
            val fallback =
                MultiHypothesisResult(
                    hypotheses =
                        listOf(
                            ClassificationHypothesis(
                                categoryId = "",
                                categoryName = rawDetection.onDeviceLabel,
                                explanation = "",
                                confidence = rawDetection.confidence,
                                confidenceBand = "LOW",
                                attributes = emptyMap(),
                            ),
                        ),
                    globalConfidence = rawDetection.confidence,
                    needsRefinement = true,
                    requestId = "fallback",
                )
            showHypothesisSelectionFromResult(detectionId, rawDetection, fallback)
        }

        private suspend fun showHypothesisSelectionFromResult(
            detectionId: String,
            rawDetection: RawDetection,
            result: MultiHypothesisResult,
        ) {
            withContext(mainDispatcher) {
                val updatedQueue =
                    _pendingDetections.value.map { pending ->
                        if ((pending as? PendingDetectionState.AwaitingClassification)?.detectionId == detectionId) {
                            PendingDetectionState.ShowingHypotheses(
                                detectionId = detectionId,
                                rawDetection = rawDetection,
                                hypothesisResult = result,
                                thumbnailRef = rawDetection.thumbnailRef,
                            )
                        } else {
                            pending
                        }
                    }
                _pendingDetections.value = updatedQueue

                // Show hypothesis sheet if this is the first in queue
                val currentPending = updatedQueue.firstOrNull()
                if (currentPending is PendingDetectionState.ShowingHypotheses &&
                    currentPending.detectionId == detectionId
                ) {
                    showHypothesisSelection(
                        result = result,
                        itemId = detectionId,
                        thumbnailRef = rawDetection.thumbnailRef,
                    )
                }
            }
        }

        /**
         * Create ScannedItem from RawDetection + optional hypothesis.
         *
         * If hypothesis is null, uses on-device label as fallback.
         *
         * @param detectionId Unique identifier for this pending detection
         * @param rawDetection Raw detection data from ML Kit/ObjectTracker
         * @param hypothesis Optional confirmed hypothesis from user selection
         */
        private suspend fun createItemFromDetection(
            detectionId: String,
            rawDetection: RawDetection,
            hypothesis: ClassificationHypothesis?,
        ) {
            Log.i(TAG, "createItemFromDetection: detectionId=$detectionId hypothesis=${hypothesis?.categoryName}")

            try {
                // Resolve category from hypothesis or domain pack fallback
                val resolvedCategory = resolveItemCategory(hypothesis, rawDetection)

                // Resolve label: hypothesis name, domain pack name, or on-device fallback
                val resolvedLabel = resolveItemLabel(hypothesis, rawDetection)

                // Convert hypothesis attributes to ItemAttribute map
                val resolvedAttributes =
                    hypothesis?.attributes
                        ?.filter { it.value.isNotBlank() }
                        ?.mapValues { (_, value) ->
                            ItemAttribute(
                                value = value,
                                confidence = hypothesis.confidence,
                                source = "cloud_hypothesis",
                            )
                        } ?: emptyMap()

                val item =
                    ScannedItem(
                        id = UUID.randomUUID().toString(),
                        labelText = resolvedLabel,
                        category = resolvedCategory,
                        priceRange = 0.0 to 0.0, // Will be estimated by PricingEngine later
                        confidence = hypothesis?.confidence ?: rawDetection.confidence,
                        boundingBox = rawDetection.boundingBox,
                        thumbnail = rawDetection.thumbnailRef,
                        classificationStatus = if (hypothesis != null) "CONFIRMED" else "FALLBACK",
                        timestamp = System.currentTimeMillis(),
                        attributes = resolvedAttributes,
                    )

                withContext(mainDispatcher) {
                    facade.addItemWithThumbnailEnrichment(item, rawDetection.thumbnailRef)
                    Log.i(TAG, "Item created from detection: ${item.id} - ${item.labelText} (enrichment triggered)")
                }
            } finally {
                // Clean up full-frame bitmap after classification and item creation.
                // Note: thumbnailRef (ImageRef) is independent and used by the enrichment pipeline.
                rawDetection.fullFrameBitmap?.recycle()
            }
        }

        /**
         * Resolve the ItemCategory from a hypothesis or domain pack fallback.
         *
         * Priority:
         * 1. Hypothesis categoryId → domain pack lookup → itemCategoryName → ItemCategory
         * 2. Domain pack BasicCategoryEngine using ML Kit label
         * 3. Raw on-device category from ML Kit
         */
        private suspend fun resolveItemCategory(
            hypothesis: ClassificationHypothesis?,
            rawDetection: RawDetection,
        ): ItemCategory {
            // If hypothesis provides a domain category ID, map it through the domain pack
            if (hypothesis != null && hypothesis.categoryId.isNotBlank()) {
                try {
                    if (DomainPackProvider.isInitialized) {
                        val domainPack = DomainPackProvider.repository.getActiveDomainPack()
                        val domainCategory = domainPack.categories.find { it.id == hypothesis.categoryId }
                        if (domainCategory != null) {
                            val mapped = ItemCategory.fromClassifierLabel(domainCategory.itemCategoryName)
                            if (mapped != ItemCategory.UNKNOWN) {
                                Log.d(TAG, "Resolved category from hypothesis: ${hypothesis.categoryId} → $mapped")
                                return mapped
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to resolve category from hypothesis: ${e.message}")
                }
            }

            // Fallback: use domain pack engine with ML Kit label
            if (DomainPackProvider.isInitialized) {
                try {
                    val input =
                        CategorySelectionInput(
                            mlKitLabel = rawDetection.onDeviceLabel,
                            mlKitConfidence = rawDetection.confidence,
                        )
                    val domainCategory = DomainPackProvider.categoryEngine.selectCategory(input)
                    if (domainCategory != null) {
                        val mapped = ItemCategory.fromClassifierLabel(domainCategory.itemCategoryName)
                        if (mapped != ItemCategory.UNKNOWN) {
                            Log.d(TAG, "Resolved category from domain pack: ${domainCategory.id} → $mapped")
                            return mapped
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Domain pack category selection failed: ${e.message}")
                }
            }

            return rawDetection.onDeviceCategory
        }

        /**
         * Resolve the display label for an item.
         *
         * Priority:
         * 1. Hypothesis categoryName (e.g., "Sofa", "Laptop")
         * 2. Domain pack category displayName
         * 3. Raw on-device label from ML Kit
         */
        private suspend fun resolveItemLabel(
            hypothesis: ClassificationHypothesis?,
            rawDetection: RawDetection,
        ): String {
            // Hypothesis provides a fine-grained label
            if (hypothesis != null && hypothesis.categoryName.isNotBlank()) {
                return hypothesis.categoryName
            }

            // Try domain pack for a better label than ML Kit's coarse category
            if (DomainPackProvider.isInitialized) {
                try {
                    val input =
                        CategorySelectionInput(
                            mlKitLabel = rawDetection.onDeviceLabel,
                            mlKitConfidence = rawDetection.confidence,
                        )
                    val domainCategory = DomainPackProvider.categoryEngine.selectCategory(input)
                    if (domainCategory != null) {
                        Log.d(TAG, "Resolved label from domain pack: ${domainCategory.displayName}")
                        return domainCategory.displayName
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Domain pack label resolution failed: ${e.message}")
                }
            }

            return rawDetection.onDeviceLabel
        }

        /**
         * Remove a detection from the pending queue.
         */
        private suspend fun removeFromPendingQueue(detectionId: String) {
            withContext(mainDispatcher) {
                _pendingDetections.value =
                    _pendingDetections.value.filterNot {
                        when (it) {
                            is PendingDetectionState.AwaitingClassification -> it.detectionId == detectionId
                            is PendingDetectionState.ShowingHypotheses -> it.detectionId == detectionId
                            else -> false
                        }
                    }
            }
        }
    }

/**
 * Alert data class for cloud classification errors.
 */
data class CloudClassificationAlert(
    val itemId: String,
    val message: String,
)

/**
 * Alert data class for persistence errors.
 */
data class PersistenceAlert(
    val message: String,
    val operation: String,
    val throwable: Throwable,
)

/**
 * UI events emitted by ItemsViewModel.
 * These are one-shot events that trigger navigation or UI updates in the composable.
 */
sealed class ItemsUiEvent {
    /**
     * Navigate to the items list screen.
     * Emitted after scan completion when the auto-open setting is enabled and items were found.
     */
    object NavigateToItemList : ItemsUiEvent()
}
