package com.scanium.app.items

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanium.app.aggregation.AggregationPresets
import com.scanium.app.aggregation.AggregationStats
import com.scanium.app.camera.OverlayTrack
import com.scanium.app.camera.detection.DetectionEvent
import com.scanium.app.items.classification.ItemClassificationCoordinator
import com.scanium.app.items.listing.ListingStatusManager
import com.scanium.app.items.overlay.OverlayTrackManager
import com.scanium.app.items.persistence.ScannedItemStore
import com.scanium.app.items.state.ItemsStateManager
import com.scanium.app.ml.DetectionResult
import com.scanium.app.ml.classification.ClassificationMode
import com.scanium.app.ml.classification.ClassificationThumbnailProvider
import com.scanium.app.ml.classification.ItemClassifier
import com.scanium.core.export.ExportPayload
import com.scanium.app.items.export.toExportPayload
import com.scanium.telemetry.facade.Telemetry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.Locale
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
class ItemsViewModel @Inject constructor(
    classificationMode: StateFlow<ClassificationMode>,
    @Named("cloudClassificationEnabled") cloudClassificationEnabled: StateFlow<Boolean>,
    @Named("onDevice") onDeviceClassifier: ItemClassifier,
    @Named("cloud") cloudClassifier: ItemClassifier,
    private val itemsStore: ScannedItemStore,
    private val stableItemCropper: ClassificationThumbnailProvider,
    telemetry: Telemetry?
) : ViewModel() {

    // Default dispatchers (not injected for simplicity)
    private val workerDispatcher = Dispatchers.Default
    private val mainDispatcher = Dispatchers.Main
    companion object {
        private const val TAG = "ItemsViewModel"
        private const val QR_URL_TTL_MS = 2000L
    }

    // ==================== Managers ====================

    /**
     * Manages item state, aggregation, and persistence.
     */
    private val stateManager = ItemsStateManager(
        scope = viewModelScope,
        itemsStore = itemsStore,
        workerDispatcher = workerDispatcher,
        mainDispatcher = mainDispatcher,
        aggregationConfig = AggregationPresets.REALTIME
    )

    /**
     * Manages overlay track state for camera detection overlays.
     */
    private val overlayManager = OverlayTrackManager(
        stateManager = stateManager
    )

    /**
     * Coordinates classification operations.
     */
    private val classificationCoordinator = ItemClassificationCoordinator(
        scope = viewModelScope,
        stateManager = stateManager,
        classificationMode = classificationMode,
        cloudClassificationEnabled = cloudClassificationEnabled,
        onDeviceClassifier = onDeviceClassifier,
        cloudClassifier = cloudClassifier,
        stableItemCropper = stableItemCropper,
        priceEstimatorProvider = null, // TODO: Add PriceEstimatorProvider to DI when needed
        telemetry = telemetry,
        workerDispatcher = workerDispatcher,
        mainDispatcher = mainDispatcher
    )

    /**
     * Manages listing status for eBay integration.
     */
    private val listingManager = ListingStatusManager(
        scope = viewModelScope,
        itemsStore = itemsStore,
        workerDispatcher = workerDispatcher
    )

    // ==================== Public State (Delegated) ====================

    /** Current list of scanned items */
    val items: StateFlow<List<ScannedItem>> = stateManager.items

    /** Current overlay tracks for camera detection visualization */
    val overlayTracks: StateFlow<List<OverlayTrack>> = overlayManager.overlayTracks

    /** Latest detected QR URL (if any) */
    private val _latestQrUrl = MutableStateFlow<String?>(null)
    val latestQrUrl: StateFlow<String?> = _latestQrUrl

    /** Timestamp of the last detected QR URL */
    private val _lastQrSeenTimestampMs = MutableStateFlow(0L)
    val lastQrSeenTimestampMs: StateFlow<Long> = _lastQrSeenTimestampMs

    /** Events emitted when new items are added (for animations) */
    val itemAddedEvents: SharedFlow<ScannedItem> = stateManager.itemAddedEvents

    /** Alerts for cloud classification errors */
    val cloudClassificationAlerts: SharedFlow<CloudClassificationAlert> = classificationCoordinator.cloudClassificationAlerts

    /** Alerts for persistence errors */
    private val _persistenceAlerts = MutableSharedFlow<PersistenceAlert>(extraBufferCapacity = 1)
    val persistenceAlerts: SharedFlow<PersistenceAlert> = _persistenceAlerts.asSharedFlow()

    /** Current similarity threshold for aggregation */
    val similarityThreshold: StateFlow<Float> = stateManager.similarityThreshold

    /** Latest in-memory export payload for selected items */
    private val _exportPayload = MutableStateFlow<ExportPayload?>(null)
    val exportPayload: StateFlow<ExportPayload?> = _exportPayload

    init {
        classificationCoordinator.startNewSession()

        // Connect managers
        stateManager.setOnStateChangedListener {
            classificationCoordinator.triggerEnhancedClassification()
            classificationCoordinator.syncPriceEstimations(stateManager.getScannedItems())
            overlayManager.refreshOverlayTracks()
        }

        listingManager.setItemsReference(stateManager.items)

        viewModelScope.launch {
            itemsStore.errors.collect { error ->
                _persistenceAlerts.emit(
                    PersistenceAlert(
                        message = "Unable to save scanned items. Please try again.",
                        operation = error.operation,
                        throwable = error.throwable
                    )
                )
            }
        }

        Log.i(TAG, "ItemsViewModel initialized with managers")
    }

    // ==================== Item CRUD Operations (Delegated to StateManager) ====================

    /**
     * Adds a single detected item to the list.
     * @see ItemsStateManager.addItem
     */
    fun addItem(item: ScannedItem) {
        stateManager.addItem(item)
    }

    /**
     * Adds multiple detected items at once.
     * @see ItemsStateManager.addItems
     */
    fun addItems(newItems: List<ScannedItem>) {
        stateManager.addItems(newItems)
    }

    /**
     * Removes a specific item by ID.
     * @see ItemsStateManager.removeItem
     */
    fun removeItem(itemId: String) {
        classificationCoordinator.cancelPriceEstimation(itemId)
        stateManager.removeItem(itemId)
    }

    /**
     * Restores a previously removed item.
     * @see ItemsStateManager.restoreItem
     */
    fun restoreItem(item: ScannedItem) {
        stateManager.restoreItem(item)
    }

    /**
     * Clears all detected items.
     * @see ItemsStateManager.clearAllItems
     */
    fun clearAllItems() {
        classificationCoordinator.cancelAllPriceEstimations()
        classificationCoordinator.reset()
        overlayManager.clear()
        stateManager.clearAllItems()
    }

    /**
     * Returns the current count of detected items.
     * @see ItemsStateManager.getItemCount
     */
    fun getItemCount(): Int = stateManager.getItemCount()

    /**
     * Gets a specific item by ID.
     * @see ItemsStateManager.getItem
     */
    fun getItem(itemId: String): ScannedItem? = stateManager.getItem(itemId)

    // ==================== Overlay Operations (Delegated to OverlayManager) ====================

    /**
     * Updates overlay with new detections from the camera.
     * @see OverlayTrackManager.updateOverlayDetections
     */
    fun updateOverlayDetections(detections: List<DetectionResult>) {
        overlayManager.updateOverlayDetections(detections)
    }

    /**
     * Update QR URL overlay state based on detection router events.
     */
    fun onDetectionEvent(event: DetectionEvent) {
        when (event) {
            is DetectionEvent.BarcodeDetected -> updateQrUrl(event)
            else -> Unit
        }
    }

    // ==================== Export Operations ====================

    /**
     * Create an in-memory export payload for selected items.
     */
    fun createExportPayload(selectedIds: List<String>): ExportPayload? {
        if (selectedIds.isEmpty()) {
            _exportPayload.value = null
            return null
        }

        val selectedSet = selectedIds.toSet()
        val selectedItems = stateManager.getScannedItems()
            .filter { it.id in selectedSet }

        val payload = selectedItems.takeIf { it.isNotEmpty() }?.toExportPayload()
        _exportPayload.value = payload
        return payload
    }

    // ==================== Classification Operations (Delegated to ClassificationCoordinator) ====================

    /**
     * Retry classification for a failed item.
     * @see ItemClassificationCoordinator.retryClassification
     */
    fun retryClassification(itemId: String) {
        classificationCoordinator.retryClassification(itemId)
    }

    // ==================== Listing Operations (Delegated to ListingManager) ====================

    /**
     * Updates the listing status of a scanned item.
     * @see ListingStatusManager.updateListingStatus
     */
    fun updateListingStatus(
        itemId: String,
        status: ItemListingStatus,
        listingId: String? = null,
        listingUrl: String? = null
    ) {
        stateManager.updateListingStatus(itemId, status, listingId, listingUrl)
        listingManager.updateListingStatus(itemId, status, listingId, listingUrl)
    }

    /**
     * Gets the listing status for a specific item.
     * @see ListingStatusManager.getListingStatus
     */
    fun getListingStatus(itemId: String): ItemListingStatus? {
        return listingManager.getListingStatus(itemId)
    }

    // ==================== Threshold Operations (Delegated to StateManager) ====================

    /**
     * Update the similarity threshold for real-time tuning.
     * @see ItemsStateManager.updateSimilarityThreshold
     */
    fun updateSimilarityThreshold(threshold: Float) {
        stateManager.updateSimilarityThreshold(threshold)
    }

    /**
     * Get the current effective similarity threshold.
     * @see ItemsStateManager.getCurrentSimilarityThreshold
     */
    fun getCurrentSimilarityThreshold(): Float {
        return stateManager.getCurrentSimilarityThreshold()
    }

    // ==================== Aggregation Statistics (Delegated to StateManager) ====================

    /**
     * Get aggregation statistics for monitoring/debugging.
     * @see ItemsStateManager.getAggregationStats
     */
    fun getAggregationStats(): AggregationStats = stateManager.getAggregationStats()

    /**
     * Remove stale items that haven't been seen recently.
     * @see ItemsStateManager.removeStaleItems
     */
    fun removeStaleItems(maxAgeMs: Long = 30_000L) {
        stateManager.removeStaleItems(maxAgeMs)
    }

    // ==================== Telemetry (Delegated to StateManager) ====================

    /**
     * Enable async telemetry collection.
     * @see ItemsStateManager.enableTelemetry
     */
    fun enableTelemetry() {
        stateManager.enableTelemetry()
    }

    /**
     * Disable async telemetry collection.
     * @see ItemsStateManager.disableTelemetry
     */
    fun disableTelemetry() {
        stateManager.disableTelemetry()
    }

    /**
     * Check if async telemetry is currently enabled.
     * @see ItemsStateManager.isTelemetryEnabled
     */
    fun isTelemetryEnabled(): Boolean = stateManager.isTelemetryEnabled()

    // ==================== Lifecycle ====================

    override fun onCleared() {
        super.onCleared()
        stateManager.disableTelemetry()
        Log.i(TAG, "ItemsViewModel cleared")
    }

    private var qrUrlClearJob: Job? = null

    private fun updateQrUrl(event: DetectionEvent.BarcodeDetected) {
        val url = event.items.asSequence()
            .mapNotNull { it.barcodeValue }
            .mapNotNull(::parseUrl)
            .lastOrNull()
            ?: return

        _latestQrUrl.value = url
        _lastQrSeenTimestampMs.value = event.timestampMs

        qrUrlClearJob?.cancel()
        qrUrlClearJob = viewModelScope.launch {
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
    val message: String
)

/**
 * Alert data class for persistence errors.
 */
data class PersistenceAlert(
    val message: String,
    val operation: String,
    val throwable: Throwable
)
