package com.scanium.app.items.overlay

import android.util.Log
import com.scanium.app.camera.OverlayTrack
import com.scanium.app.camera.mapOverlayTracks
import com.scanium.app.items.state.ItemsStateManager
import com.scanium.app.logging.ScaniumLog
import com.scanium.app.ml.DetectionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages overlay tracks for camera detection overlays.
 *
 * This class is responsible for:
 * - Maintaining overlay track state
 * - Mapping detections to overlay tracks
 * - Tracking overlay ready states
 * - Caching resolution mappings
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
    private val _overlayTracks = MutableStateFlow<List<OverlayTrack>>(emptyList())
    val overlayTracks: StateFlow<List<OverlayTrack>> = _overlayTracks.asStateFlow()

    // Internal caches
    private val overlayReadyStates = mutableMapOf<String, Boolean>()
    private val overlayResolutionCache = mutableMapOf<String, String?>()
    private var lastOverlayDetections: List<DetectionResult> = emptyList()

    /**
     * Update overlay with new detections from the camera.
     *
     * @param detections List of detection results from the ML pipeline
     */
    fun updateOverlayDetections(detections: List<DetectionResult>) {
        if (DEBUG_LOGGING) {
            ScaniumLog.d(TAG, "[OVERLAY] updateOverlayDetections: ${detections.size} detections")
        }
        lastOverlayDetections = detections
        renderOverlayTracks(detections)
    }

    /**
     * Refresh overlay tracks using the last known detections.
     * Called after classification results are updated.
     */
    fun refreshOverlayTracks() {
        if (lastOverlayDetections.isNotEmpty()) {
            renderOverlayTracks(lastOverlayDetections)
        }
    }

    /**
     * Clear all overlay state.
     */
    fun clear() {
        _overlayTracks.value = emptyList()
        overlayReadyStates.clear()
        overlayResolutionCache.clear()
        lastOverlayDetections = emptyList()
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

    // ==================== Internal Methods ====================

    private fun renderOverlayTracks(detections: List<DetectionResult>) {
        val aggregatedItems = stateManager.getAggregatedItems()
        val mapped = mapOverlayTracks(
            detections = detections,
            aggregatedItems = aggregatedItems,
            readyConfidenceThreshold = readyConfidenceThreshold
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

        _overlayTracks.value = mapped
    }
}
