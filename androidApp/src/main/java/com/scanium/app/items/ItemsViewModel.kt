package com.scanium.app.items

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanium.app.aggregation.AggregationPresets
import com.scanium.app.aggregation.AggregationStats
import com.scanium.app.camera.OverlayTrack
import com.scanium.app.camera.detection.DetectionEvent
import com.scanium.app.items.classification.ItemClassificationCoordinator
import com.scanium.app.items.export.toExportPayload
import com.scanium.app.items.listing.ListingStatusManager
import com.scanium.app.items.overlay.OverlayTrackManager
import com.scanium.app.items.persistence.ScannedItemStore
import com.scanium.app.items.state.ItemsStateManager
import com.scanium.app.camera.CameraXManager
import com.scanium.app.ml.CropBasedEnricher
import com.scanium.app.ml.DetectionResult
import com.scanium.app.ml.VisionInsightsPrefiller
import com.scanium.app.ml.classification.ClassificationMode
import com.scanium.app.ml.classification.ClassificationThumbnailProvider
import com.scanium.app.ml.classification.ItemClassifier
import com.scanium.core.export.ExportPayload
import com.scanium.core.models.scanning.ScanRoi
import com.scanium.telemetry.facade.Telemetry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named
import kotlin.jvm.JvmSuppressWildcards

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
        @Named("cloud") cloudClassifier: ItemClassifier,
        private val itemsStore: ScannedItemStore,
        private val stableItemCropper: ClassificationThumbnailProvider,
        private val visionInsightsPrefiller: VisionInsightsPrefiller,
        private val cropBasedEnricher: CropBasedEnricher,
        private val settingsRepository: com.scanium.app.data.SettingsRepository,
        telemetry: Telemetry?,
    ) : ViewModel() {
        // Default dispatchers (override in tests if needed)
        private var workerDispatcher: CoroutineDispatcher = Dispatchers.Default
        private var mainDispatcher: CoroutineDispatcher = Dispatchers.Main

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
            this.workerDispatcher = workerDispatcher
            this.mainDispatcher = mainDispatcher
            // Dispatchers are now passed to facade constructor above
        }

        companion object {
            private const val TAG = "ItemsViewModel"
            private const val QR_URL_TTL_MS = 2000L
        }

        // ==================== Facade ====================

        /**
         * Facade that encapsulates manager coordination.
         * Delegates to ItemsStateManager, OverlayTrackManager, ItemClassificationCoordinator, and ListingStatusManager.
         */
        private val facade =
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
            )

        // ==================== Public State (Delegated to Facade) ====================

        /** Current list of scanned items */
        val items: StateFlow<List<ScannedItem>> = facade.items

        /** Current overlay tracks for camera detection visualization */
        val overlayTracks: StateFlow<List<OverlayTrack>> = facade.overlayTracks

        /** Events emitted when new items are added (for animations) */
        val itemAddedEvents: SharedFlow<ScannedItem> = facade.itemAddedEvents

        /** Alerts for cloud classification errors */
        val cloudClassificationAlerts: SharedFlow<CloudClassificationAlert> = facade.cloudClassificationAlerts

        /** Alerts for persistence errors */
        val persistenceAlerts: SharedFlow<PersistenceAlert> = facade.persistenceAlerts

        /** UI events (e.g., navigation triggers) */
        val uiEvents: SharedFlow<ItemsUiEvent> = facade.uiEvents

        /** Current similarity threshold for aggregation */
        val similarityThreshold: StateFlow<Float> = facade.similarityThreshold

        /** Latest in-memory export payload for selected items */
        val exportPayload: StateFlow<ExportPayload?> = facade.exportPayload

        /** Latest detected QR URL (if any) - ViewModel-specific with TTL */
        private val _latestQrUrl = MutableStateFlow<String?>(null)
        val latestQrUrl: StateFlow<String?> = _latestQrUrl

        /** Timestamp of the last detected QR URL - ViewModel-specific */
        private val _lastQrSeenTimestampMs = MutableStateFlow(0L)
        val lastQrSeenTimestampMs: StateFlow<Long> = _lastQrSeenTimestampMs

        /** Current ROI filter result for diagnostics */
        val lastRoiFilterResult = facade.lastRoiFilterResult

        init {
            Log.i(TAG, "ItemsViewModel initialized with facade")
        }

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
        fun addItemsWithVisionPrefill(context: Context, newItems: List<ScannedItem>) {
            facade.addItemsWithVisionPrefill(context, newItems)
        }

        /**
         * Triggers vision insights extraction for a specific item.
         */
        fun extractVisionInsights(context: Context, itemId: String, imageUri: Uri?) {
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
        fun createExportPayload(selectedIds: List<String>): ExportPayload? {
            return facade.createExportPayload(selectedIds)
        }

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
        fun getListingStatus(itemId: String): ItemListingStatus? {
            return facade.getListingStatus(itemId)
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
                    val bitmap = android.graphics.BitmapFactory.decodeStream(
                        context.contentResolver.openInputStream(photoUri)
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
                    val photo = com.scanium.shared.core.models.items.ItemPhoto(
                        id = java.util.UUID.randomUUID().toString(),
                        uri = savedUri,
                        bytes = null,
                        mimeType = "image/jpeg",
                        width = bitmap.width,
                        height = bitmap.height,
                        capturedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
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
            onComplete: () -> Unit = {}
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
        fun getCurrentSimilarityThreshold(): Float {
            return facade.getCurrentSimilarityThreshold()
        }

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

        // ==================== Lifecycle ====================

        override fun onCleared() {
            super.onCleared()
            facade.disableTelemetry()
            Log.i(TAG, "ItemsViewModel cleared")
        }

        private var qrUrlClearJob: Job? = null

        private fun updateQrUrl(event: DetectionEvent.BarcodeDetected) {
            val url =
                event.items.asSequence()
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
