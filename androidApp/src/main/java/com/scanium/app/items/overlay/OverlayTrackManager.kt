package com.scanium.app.items.overlay

import android.util.Log
import com.scanium.app.camera.OverlayTrack
import com.scanium.app.camera.detection.RoiDetectionFilter
import com.scanium.app.camera.detection.RoiFilterResult
import com.scanium.app.camera.mapOverlayTracks
import com.scanium.app.items.state.ItemsStateManager
import com.scanium.app.logging.ScaniumLog
import com.scanium.app.ml.DetectionResult
import com.scanium.core.models.scanning.ScanRoi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Manages overlay tracks for camera detection overlays.
 *
 * This class is responsible for:
 * - Maintaining overlay track state
 * - ROI-filtering detections before visualization
 * - Mapping detections to overlay tracks
 * - Tracking overlay ready states
 * - Caching resolution mappings
 *
 * ## ROI Enforcement (Phase 2)
 * Detections are filtered by ROI BEFORE being rendered:
 * - Only detections with center inside ROI are shown
 * - This teaches users to center objects in the scan zone
 * - Visualization matches what the scan pipeline considers eligible
 *
 * ## Thread Safety
 * This class is designed to be called from the main thread.
 * Internal state uses mutable maps which should be accessed from the same thread.
 *
 * @param stateManager Reference to the items state manager
 * @param readyConfidenceThreshold Confidence threshold for marking items as "ready"
 */
class OverlayTrackManager(
    private val stateManager: ItemsStateManager,
    private val readyConfidenceThreshold: Float = DEFAULT_READY_THRESHOLD
) {
    companion object {
        private const val TAG = "OverlayTrackManager"
        private const val DEFAULT_READY_THRESHOLD = 0.55f
        private const val DEBUG_LOGGING = false
    }

    // Overlay state
    private val _overlayTracks = MutableStateFlow<List<OverlayTrack>>(CopyOnWriteArrayList())
    val overlayTracks: StateFlow<List<OverlayTrack>> = _overlayTracks.asStateFlow()

    // ROI filter result - tracks what was filtered for diagnostics and hints
    private val _lastRoiFilterResult = MutableStateFlow<RoiFilterResult?>(null)
    val lastRoiFilterResult: StateFlow<RoiFilterResult?> = _lastRoiFilterResult.asStateFlow()

    // Internal caches
    private val overlayReadyStates = mutableMapOf<String, Boolean>()
    private val overlayResolutionCache = mutableMapOf<String, String?>()
    private var lastOverlayDetections: List<DetectionResult> = emptyList()
    private var lastScanRoi: ScanRoi = ScanRoi.DEFAULT
    private var lastLockedTrackingId: String? = null

    /**
     * Update overlay with new detections from the camera.
     *
     * PHASE 2: ROI enforcement - detections are filtered by ROI BEFORE rendering.
     * Only detections with center inside ROI are shown as bounding boxes.
     *
     * @param detections List of detection results from the ML pipeline
     * @param scanRoi Current scan ROI (detections outside are filtered out)
     * @param lockedTrackingId Tracking ID of locked candidate (if any) for visual distinction
     */
    fun updateOverlayDetections(
        detections: List<DetectionResult>,
        scanRoi: ScanRoi = ScanRoi.DEFAULT,
        lockedTrackingId: String? = null
    ) {
        if (DEBUG_LOGGING) {
            ScaniumLog.d(TAG, "[OVERLAY] updateOverlayDetections: ${detections.size} detections, roi=${scanRoi.widthNorm}x${scanRoi.heightNorm}")
        }
        lastOverlayDetections = detections
        lastScanRoi = scanRoi
        lastLockedTrackingId = lockedTrackingId
        renderOverlayTracks(detections, scanRoi, lockedTrackingId)
    }

    /**
     * Refresh overlay tracks using the last known detections and ROI.
     * Called after classification results are updated.
     */
    fun refreshOverlayTracks() {
        if (lastOverlayDetections.isNotEmpty()) {
            renderOverlayTracks(lastOverlayDetections, lastScanRoi, lastLockedTrackingId)
        }
    }

    /**
     * Clear all overlay state.
     */
    fun clear() {
        _overlayTracks.value = CopyOnWriteArrayList()
        _lastRoiFilterResult.value = null
        overlayReadyStates.clear()
        overlayResolutionCache.clear()
        lastOverlayDetections = emptyList()
        lastScanRoi = ScanRoi.DEFAULT
        lastLockedTrackingId = null
    }

    /**
     * Get the last overlay detections (for re-rendering after state changes).
     */
    fun getLastDetections(): List<DetectionResult> = lastOverlayDetections

    /**
     * Check if an aggregated item is marked as ready.
     */
    fun isItemReady(aggregatedId: String): Boolean {
        return overlayReadyStates[aggregatedId] ?: false
    }

    /**
     * Check if detections exist but none are inside ROI.
     * Used for showing "Center the object" hint.
     */
    fun hasDetectionsOutsideRoiOnly(): Boolean {
        return _lastRoiFilterResult.value?.hasDetectionsOutsideRoiOnly ?: false
    }

    // ==================== Internal Methods ====================

    private fun renderOverlayTracks(
        detections: List<DetectionResult>,
        scanRoi: ScanRoi,
        lockedTrackingId: String?
    ) {
        // PHASE 2: ROI filtering - only show detections inside ROI
        val filterResult = RoiDetectionFilter.filterByRoi(detections, scanRoi)
        _lastRoiFilterResult.value = filterResult

        if (DEBUG_LOGGING) {
            ScaniumLog.d(TAG, "[OVERLAY] ROI filter: ${filterResult.eligibleCount} eligible, ${filterResult.outsideCount} outside")
        }

        // Only render ROI-eligible detections
        val roiEligibleDetections = filterResult.roiEligible

        val aggregatedItems = stateManager.getAggregatedItems()
        val mapped = mapOverlayTracks(
            detections = roiEligibleDetections,
            aggregatedItems = aggregatedItems,
            readyConfidenceThreshold = readyConfidenceThreshold,
            lockedTrackingId = lockedTrackingId
        )

        // Log when overlay tracks change
        val prevCount = _overlayTracks.value.size
        if (mapped.size != prevCount) {
            Log.d(TAG, "[OVERLAY] Track count changed: $prevCount -> ${mapped.size} (detections=${detections.size})")
        }
        if (mapped.isEmpty() && detections.isNotEmpty()) {
            Log.w(TAG, "[OVERLAY] WARNING: ${detections.size} detections but 0 tracks after mapping!")
        }

        mapped.forEach { track ->
            val trackingId = track.trackingId
            val aggregatedId = track.aggregatedId
            if (trackingId != null) {
                val previous = overlayResolutionCache[trackingId]
                if (previous != aggregatedId) {
                    ScaniumLog.d(
                        TAG,
                        "[OVERLAY] track=$trackingId -> aggregated=${aggregatedId ?: "none"} label=${track.label}"
                    )
                    overlayResolutionCache[trackingId] = aggregatedId
                } else if (!overlayResolutionCache.containsKey(trackingId) && aggregatedId == null) {
                    ScaniumLog.d(
                        TAG,
                        "[OVERLAY] track=$trackingId -> aggregated=none label=${track.label}"
                    )
                    overlayResolutionCache[trackingId] = aggregatedId
                }
            }

            if (aggregatedId != null) {
                val wasReady = overlayReadyStates[aggregatedId] ?: false
                if (!wasReady && track.isReady) {
                    ScaniumLog.d(
                        TAG,
                        "[OVERLAY] READY aggregated=$aggregatedId label=${track.label} conf=${track.confidence}"
                    )
                }
                overlayReadyStates[aggregatedId] = track.isReady
            }
        }

        _overlayTracks.value = CopyOnWriteArrayList(mapped)
    }
}
