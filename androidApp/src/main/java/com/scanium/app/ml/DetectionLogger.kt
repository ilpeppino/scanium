package com.scanium.app.ml

import android.util.Log
import com.scanium.app.model.NormalizedRect
import com.scanium.app.platform.toBitmap
import com.scanium.shared.core.models.model.ImageRef

/**
 * Centralized logging utility for object detection debugging and tuning.
 * Logs are only active in debug builds.
 */
object DetectionLogger {
    private const val TAG = "DetectionLogger"

    // Check if Log.d is loggable to determine debug mode
    private val isDebug = Log.isLoggable(TAG, Log.DEBUG)

    /**
     * Logs a raw detection event with all metadata.
     */
    fun logRawDetection(
        frameNumber: Int,
        detection: RawDetection,
        imageWidth: Int,
        imageHeight: Int,
    ) {
        if (!isDebug) return

        val bestLabel = detection.bestLabel
        val normalizedArea = detection.getNormalizedArea(imageWidth, imageHeight)
        val box = detection.bboxNorm
        val thumbnail =
            when (val ref = detection.thumbnailRef) {
                is ImageRef.Bytes -> ref.toBitmap()
                else -> null
            }
        val thumbInfo = thumbnail?.let { "${it.width}x${it.height}" } ?: "none"

        Log.d(
            TAG,
            "Frame ***REMOVED***$frameNumber | Detection: " +
                "id=${detection.trackingId.take(8)}, " +
                "category=${detection.category.displayName}, " +
                "label=${bestLabel?.text ?: "none"}, " +
                "conf=${formatConfidence(bestLabel?.confidence)}, " +
                "box=${formatBox(box)}, " +
                "area=${formatArea(normalizedArea)}, " +
                "thumb=$thumbInfo",
        )

        // Log all labels if there are multiple
        if (detection.labels.size > 1) {
            detection.labels.forEach { label ->
                Log.v(
                    TAG,
                    "  └─ Label: ${label.text} (${formatConfidence(label.confidence)})",
                )
            }
        }
    }

    /**
     * Logs a candidate update event.
     */
    fun logCandidateUpdate(
        trackingId: String,
        seenCount: Int,
        maxConfidence: Float,
        category: ItemCategory,
        isPromoted: Boolean,
    ) {
        if (!isDebug) return

        val status = if (isPromoted) "PROMOTED" else "UPDATED"
        Log.d(
            TAG,
            "$status | Candidate: " +
                "id=${trackingId.take(8)}, " +
                "seen=$seenCount, " +
                "maxConf=${formatConfidence(maxConfidence)}, " +
                "category=${category.displayName}",
        )
    }

    /**
     * Logs candidate expiration.
     */
    fun logCandidateExpiration(
        trackingId: String,
        seenCount: Int,
        maxConfidence: Float,
        ageMs: Long,
    ) {
        if (!isDebug) return

        Log.d(
            TAG,
            "EXPIRED | Candidate: " +
                "id=${trackingId.take(8)}, " +
                "seen=$seenCount, " +
                "maxConf=${formatConfidence(maxConfidence)}, " +
                "age=${ageMs}ms",
        )
    }

    /**
     * Logs frame analysis summary.
     */
    fun logFrameSummary(
        frameNumber: Int,
        rawDetections: Int,
        validDetections: Int,
        promotedItems: Int,
        activeCandidates: Int,
        processingTimeMs: Long,
    ) {
        if (!isDebug) return

        Log.i(
            TAG,
            "Frame ***REMOVED***$frameNumber | Summary: " +
                "raw=$rawDetections, " +
                "valid=$validDetections, " +
                "promoted=$promotedItems, " +
                "active=$activeCandidates, " +
                "time=${processingTimeMs}ms",
        )
    }

    /**
     * Logs tracker statistics.
     */
    fun logTrackerStats(stats: com.scanium.app.tracking.TrackerStats) {
        if (!isDebug) return

        Log.i(
            TAG,
            "Tracker Stats | " +
                "active=${stats.activeCandidates}, " +
                "confirmed=${stats.confirmedCandidates}, " +
                "frame=${stats.currentFrame}",
        )
    }

    /**
     * Logs detection configuration and thresholds.
     */
    fun logConfiguration(
        minSeenCount: Int,
        minConfidence: Float,
        candidateTimeoutMs: Long,
        analysisIntervalMs: Long,
    ) {
        if (!isDebug) return

        Log.i(
            TAG,
            "Configuration | " +
                "minSeen=$minSeenCount, " +
                "minConf=${formatConfidence(minConfidence)}, " +
                "timeout=${candidateTimeoutMs}ms, " +
                "interval=${analysisIntervalMs}ms",
        )
    }

    /**
     * Logs when a detection is rejected and why.
     */
    fun logRejection(
        trackingId: String,
        reason: String,
        seenCount: Int? = null,
        confidence: Float? = null,
    ) {
        if (!isDebug) return

        val details =
            buildString {
                append("id=${trackingId.take(8)}, reason=$reason")
                seenCount?.let { append(", seen=$it") }
                confidence?.let { append(", conf=${formatConfidence(it)}") }
            }

        Log.d(TAG, "REJECTED | $details")
    }

    // Helper formatting functions

    private fun formatConfidence(confidence: Float?): String {
        return confidence?.let { String.format("%.2f", it) } ?: "N/A"
    }

    private fun formatBox(box: NormalizedRect?): String {
        return box?.let {
            "[l=${formatCoord(it.left)}, t=${formatCoord(it.top)}, r=${formatCoord(it.right)}, b=${formatCoord(it.bottom)}]"
        } ?: "[none]"
    }

    private fun formatArea(area: Float): String {
        return String.format("%.3f", area)
    }

    private fun formatCoord(value: Float): String {
        return String.format("%.3f", value)
    }
}
