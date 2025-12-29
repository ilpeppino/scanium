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
 * - Selecting which detection is inside ROI (user intent)
 * - Mapping detections to overlay tracks with appropriate visual states
 * - Tracking overlay ready states
 * - Caching resolution mappings
 *
 * ***REMOVED******REMOVED*** Eye Mode vs Focus Mode
 * ALL detections are rendered (Eye mode = global vision):
 * - Detections outside ROI: EYE style (subtle, global awareness)
 * - Detection inside ROI: SELECTED/READY/LOCKED style (user intent)
 *
 * ROI is a SELECTION TOOL, not a detection filter:
 * - User centers object in ROI to select it
 * - Only selected object can be scanned/captured
 *
 * ***REMOVED******REMOVED*** Thread Safety
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

    // Selected detection - the ONE detection inside ROI (Focus mode target)
    private val _selectedTrackingId = MutableStateFlow<String?>(null)
    val selectedTrackingId: StateFlow<String?> = _selectedTrackingId.asStateFlow()

    // Internal caches
    private val overlayReadyStates = mutableMapOf<String, Boolean>()
    private val overlayResolutionCache = mutableMapOf<String, String?>()
    private var lastOverlayDetections: List<DetectionResult> = emptyList()
    private var lastScanRoi: ScanRoi = ScanRoi.DEFAULT
    private var lastLockedTrackingId: String? = null
    private var lastIsGoodState: Boolean = false

    /**
     * Update overlay with new detections from the camera.
     *
     * Eye Mode vs Focus Mode:
     * - ALL detections are rendered (Eye mode = global vision)
     * - Only the selected detection (center inside ROI) is highlighted (Focus mode)
     *
     * @param detections List of detection results from the ML pipeline (ALL detections)
     * @param scanRoi Current scan ROI (used for selection, not filtering)
     * @param lockedTrackingId Tracking ID of locked candidate (if any) for visual distinction
     * @param isGoodState True if guidance state is GOOD (conditions met, waiting for lock)
     */
    fun updateOverlayDetections(
        detections: List<DetectionResult>,
        scanRoi: ScanRoi = ScanRoi.DEFAULT,
        lockedTrackingId: String? = null,
        isGoodState: Boolean = false
    ) {
        if (DEBUG_LOGGING) {
            ScaniumLog.d(TAG, "[OVERLAY] updateOverlayDetections: ${detections.size} detections, roi=${scanRoi.widthNorm}x${scanRoi.heightNorm}, isGoodState=$isGoodState")
        }
        lastOverlayDetections = detections
        lastScanRoi = scanRoi
        lastLockedTrackingId = lockedTrackingId
        lastIsGoodState = isGoodState
        renderOverlayTracks(detections, scanRoi, lockedTrackingId, isGoodState)
    }

    /**
     * Refresh overlay tracks using the last known detections and ROI.
     * Called after classification results are updated.
     */
    fun refreshOverlayTracks() {
        if (lastOverlayDetections.isNotEmpty()) {
            renderOverlayTracks(lastOverlayDetections, lastScanRoi, lastLockedTrackingId, lastIsGoodState)
        }
    }

    /**
     * Clear all overlay state.
     */
    fun clear() {
        _overlayTracks.value = CopyOnWriteArrayList()
        _lastRoiFilterResult.value = null
        _selectedTrackingId.value = null
        overlayReadyStates.clear()
        overlayResolutionCache.clear()
        lastOverlayDetections = emptyList()
        lastScanRoi = ScanRoi.DEFAULT
        lastLockedTrackingId = null
        lastIsGoodState = false
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
        lockedTrackingId: String?,
        isGoodState: Boolean
    ) {
        // Eye Mode vs Focus Mode:
        // - Use ROI for SELECTION, not filtering
        // - All detections are rendered (Eye mode = global vision)
        // - Selected detection (center inside ROI) gets highlighted (Focus mode)
        val filterResult = RoiDetectionFilter.filterByRoi(detections, scanRoi)
        _lastRoiFilterResult.value = filterResult

        if (DEBUG_LOGGING) {
            ScaniumLog.d(TAG, "[OVERLAY] ROI selection: ${filterResult.eligibleCount} inside ROI, ${filterResult.outsideCount} outside, total=${detections.size}")
        }

        // Determine selected detection: closest to ROI center among those inside ROI
        val selectedTrackingId = selectBestCandidate(filterResult.roiEligible, scanRoi)

        // Render ALL detections (Eye mode) with selection highlighting (Focus mode)
        val aggregatedItems = stateManager.getAggregatedItems()
        val mapped = mapOverlayTracks(
            detections = detections,  // ALL detections, not filtered
            aggregatedItems = aggregatedItems,
            readyConfidenceThreshold = readyConfidenceThreshold,
            selectedTrackingId = selectedTrackingId,  // Which one is selected
            lockedTrackingId = lockedTrackingId,
            isGoodState = isGoodState
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

        // Update selected tracking ID state
        _selectedTrackingId.value = selectedTrackingId
    }

    /**
     * Select the best candidate from detections inside ROI.
     *
     * Selection criteria (in order of priority):
     * 1. Center of detection must be inside ROI
     * 2. Closest to ROI center (user intent = centering)
     * 3. Higher confidence as tiebreaker
     *
     * @param roiEligibleDetections Detections with center inside ROI
     * @param scanRoi Current scan ROI
     * @return Tracking ID of the selected detection, or null if none eligible
     */
    private fun selectBestCandidate(
        roiEligibleDetections: List<DetectionResult>,
        scanRoi: ScanRoi
    ): String? {
        if (roiEligibleDetections.isEmpty()) return null

        // Score each detection by distance to ROI center (closer = better)
        val scored = roiEligibleDetections.mapNotNull { detection ->
            val trackingId = detection.trackingId?.toString() ?: return@mapNotNull null
            val bbox = detection.bboxNorm
            val centerX = (bbox.left + bbox.right) / 2f
            val centerY = (bbox.top + bbox.bottom) / 2f
            val centerScore = scanRoi.centerScore(centerX, centerY)
            // Combined score: 70% center proximity, 30% confidence
            val score = centerScore * 0.7f + detection.confidence * 0.3f
            Triple(trackingId, score, detection)
        }

        // Return the detection with highest score (closest to center)
        return scored.maxByOrNull { it.second }?.first
    }
}
