package com.scanium.app.ml

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.scanium.app.items.ScannedItem

/**
 * Tracks detection candidates across multiple frames and promotes them to confirmed items
 * once they meet configured thresholds.
 *
 * This prevents noisy single-frame detections from appearing in the item list and ensures
 * only stable, high-confidence detections are shown to the user.
 */
class CandidateTracker(
    private val minSeenCount: Int = 2,
    private val minConfidence: Float = 0.4f,
    private val candidateTimeoutMs: Long = 3000L,
    private val enableDebugLogging: Boolean = true // Will be controlled by DetectionLogger
) {
    companion object {
        private const val TAG = "CandidateTracker"
    }

    // Map of tracking ID -> candidate
    private val candidates = mutableMapOf<String, DetectionCandidate>()

    // Statistics for debugging
    private var totalDetections = 0
    private var totalPromotions = 0
    private var totalTimeouts = 0

    /**
     * Processes a new detection observation and updates or creates a candidate.
     *
     * @return Promoted ScannedItem if this observation caused a candidate to be promoted, null otherwise
     */
    fun processDetection(
        trackingId: String,
        confidence: Float,
        category: ItemCategory,
        categoryLabel: String,
        boundingBox: Rect?,
        thumbnail: Bitmap?,
        boundingBoxArea: Float = 0.0f
    ): ScannedItem? {
        totalDetections++

        // Get or create candidate
        val existingCandidate = candidates[trackingId]
        val updatedCandidate = if (existingCandidate != null) {
            existingCandidate.withNewObservation(
                confidence = confidence,
                category = category,
                categoryLabel = categoryLabel,
                boundingBox = boundingBox,
                thumbnail = thumbnail
            )
        } else {
            DetectionCandidate(
                id = trackingId,
                seenCount = 1,
                maxConfidence = confidence,
                category = category,
                categoryLabel = categoryLabel,
                lastBoundingBox = boundingBox,
                thumbnail = thumbnail
            )
        }

        candidates[trackingId] = updatedCandidate

        if (enableDebugLogging) {
            Log.d(
                TAG,
                "Detection: id=$trackingId, label=$categoryLabel, conf=${String.format("%.2f", confidence)}, " +
                        "seenCount=${updatedCandidate.seenCount}, maxConf=${String.format("%.2f", updatedCandidate.maxConfidence)}"
            )
        }

        // Check if ready for promotion
        if (updatedCandidate.isReadyForPromotion(minSeenCount, minConfidence)) {
            totalPromotions++

            if (enableDebugLogging) {
                Log.i(
                    TAG,
                    "PROMOTED: id=$trackingId, category=${updatedCandidate.category}, " +
                            "seenCount=${updatedCandidate.seenCount}, confidence=${String.format("%.2f", updatedCandidate.maxConfidence)}"
                )
            }

            // Remove from candidates map after promotion to avoid re-promoting
            candidates.remove(trackingId)

            // Convert to ScannedItem with pricing
            val priceRange = PricingEngine.generatePriceRange(
                category = updatedCandidate.category,
                boundingBoxArea = boundingBoxArea
            )

            return ScannedItem(
                id = updatedCandidate.id,
                thumbnail = updatedCandidate.thumbnail,
                category = updatedCandidate.category,
                priceRange = priceRange,
                confidence = updatedCandidate.maxConfidence,
                timestamp = updatedCandidate.firstSeenTimestamp
            )
        }

        return null
    }

    /**
     * Removes expired candidates that haven't been seen recently.
     * Call this periodically (e.g., after each frame analysis) to prevent memory leaks.
     *
     * @return Number of candidates removed
     */
    fun cleanupExpiredCandidates(): Int {
        val before = candidates.size
        val iterator = candidates.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val candidate = entry.value

            if (candidate.timeSinceLastSeenMs() > candidateTimeoutMs) {
                if (enableDebugLogging) {
                    Log.d(
                        TAG,
                        "Expired candidate: id=${entry.key}, seenCount=${candidate.seenCount}, " +
                                "maxConf=${String.format("%.2f", candidate.maxConfidence)}, " +
                                "age=${candidate.ageMs()}ms"
                    )
                }
                iterator.remove()
                totalTimeouts++
            }
        }

        val removed = before - candidates.size
        if (removed > 0 && enableDebugLogging) {
            Log.d(TAG, "Cleaned up $removed expired candidates")
        }

        return removed
    }

    /**
     * Clears all candidates. Useful when stopping scanning or resetting state.
     */
    fun clear() {
        if (enableDebugLogging && candidates.isNotEmpty()) {
            Log.d(TAG, "Clearing ${candidates.size} candidates")
        }
        candidates.clear()
    }

    /**
     * Returns current statistics for debugging and tuning.
     */
    fun getStats(): TrackerStats {
        return TrackerStats(
            activeCandidates = candidates.size,
            totalDetections = totalDetections,
            totalPromotions = totalPromotions,
            totalTimeouts = totalTimeouts,
            promotionRate = if (totalDetections > 0) totalPromotions.toFloat() / totalDetections else 0f
        )
    }

    /**
     * Logs current statistics (debug builds only).
     */
    fun logStats() {
        if (enableDebugLogging) {
            val stats = getStats()
            Log.i(
                TAG,
                "Stats: active=${stats.activeCandidates}, detections=${stats.totalDetections}, " +
                        "promotions=${stats.totalPromotions}, timeouts=${stats.totalTimeouts}, " +
                        "promoteRate=${String.format("%.1f%%", stats.promotionRate * 100)}"
            )
        }
    }

    /**
     * Returns a copy of current candidates for inspection (debugging).
     */
    fun getCurrentCandidates(): Map<String, DetectionCandidate> {
        return candidates.toMap()
    }
}

/**
 * Statistics about candidate tracking performance.
 */
data class TrackerStats(
    val activeCandidates: Int,
    val totalDetections: Int,
    val totalPromotions: Int,
    val totalTimeouts: Int,
    val promotionRate: Float
)
