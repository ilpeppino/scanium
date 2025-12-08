package com.example.objecta.tracking

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.example.objecta.ml.ItemCategory
import java.util.UUID

/**
 * Tracks detected objects across multiple frames to reduce duplicates and ensure stable tracking.
 *
 * This tracker maintains an in-memory collection of object candidates and applies
 * confirmation logic before promoting them to final ScannedItems.
 *
 * Key features:
 * - Uses ML Kit trackingId when available
 * - Fallback spatial matching using IoU and center distance
 * - Candidate confirmation based on frame count and confidence thresholds
 * - Automatic expiry of stale candidates
 *
 * @property config Configuration parameters for tracking behavior
 */
class ObjectTracker(
    private val config: TrackerConfig = TrackerConfig()
) {
    companion object {
        private const val TAG = "ObjectTracker"
    }

    // Active candidates being tracked, keyed by internalId
    private val candidates = mutableMapOf<String, ObjectCandidate>()

    // Frame counter for tracking temporal information
    private var currentFrame: Long = 0

    // IDs of candidates that have been confirmed and promoted
    private val confirmedIds = mutableSetOf<String>()

    /**
     * Process detections from the current frame and return newly confirmed candidates.
     *
     * @param detections List of raw detections from ML Kit
     * @return List of candidates that just reached confirmation threshold this frame
     */
    fun processFrame(detections: List<DetectionInfo>): List<ObjectCandidate> {
        currentFrame++
        Log.i(TAG, ">>> processFrame START: frame=$currentFrame, detections=${detections.size}, existingCandidates=${candidates.size}")

        val newlyConfirmed = mutableListOf<ObjectCandidate>()
        val matchedCandidates = mutableSetOf<String>()

        // Process each detection
        for ((index, detection) in detections.withIndex()) {
            Log.i(TAG, "    Processing detection $index: trackingId=${detection.trackingId}, category=${detection.category}, confidence=${detection.confidence}, area=${detection.normalizedBoxArea}")

            // Skip if bounding box is too small (hard filter)
            if (detection.normalizedBoxArea < config.minBoxArea) {
                Log.i(TAG, "    SKIPPED: box too small (${detection.normalizedBoxArea} < ${config.minBoxArea})")
                continue
            }

            // Note: We don't filter by confidence here - candidates can be created with low confidence
            // The confirmation logic will check minConfidence threshold later

            // Try to match with existing candidate
            val matchedCandidate = findMatchingCandidate(detection)

            if (matchedCandidate != null) {
                // Update existing candidate
                val wasConfirmed = isConfirmed(matchedCandidate)

                matchedCandidate.update(
                    newBoundingBox = detection.boundingBox,
                    frameNumber = currentFrame,
                    confidence = detection.confidence,
                    newCategory = detection.category,
                    newLabelText = detection.labelText,
                    newThumbnail = detection.thumbnail,
                    boxArea = detection.normalizedBoxArea
                )

                matchedCandidates.add(matchedCandidate.internalId)

                Log.i(TAG, "    MATCHED existing candidate ${matchedCandidate.internalId}: seenCount=${matchedCandidate.seenCount}, maxConfidence=${matchedCandidate.maxConfidence}")

                // Check if it just became confirmed
                if (!wasConfirmed && isConfirmed(matchedCandidate)) {
                    if (!confirmedIds.contains(matchedCandidate.internalId)) {
                        confirmedIds.add(matchedCandidate.internalId)
                        newlyConfirmed.add(matchedCandidate)
                        Log.i(TAG, "    ✓✓✓ CONFIRMED candidate ${matchedCandidate.internalId}: ${matchedCandidate.category} (${matchedCandidate.labelText}) after ${matchedCandidate.seenCount} frames")
                    }
                }
            } else {
                // Create new candidate
                val newCandidate = createCandidate(detection)
                candidates[newCandidate.internalId] = newCandidate
                matchedCandidates.add(newCandidate.internalId)

                Log.i(TAG, "    CREATED new candidate ${newCandidate.internalId}: ${newCandidate.category} (${newCandidate.labelText})")

                // Check if it's immediately confirmed (rare, but possible with minFramesToConfirm=1)
                if (isConfirmed(newCandidate)) {
                    if (!confirmedIds.contains(newCandidate.internalId)) {
                        confirmedIds.add(newCandidate.internalId)
                        newlyConfirmed.add(newCandidate)
                        Log.i(TAG, "    ✓✓✓ IMMEDIATELY CONFIRMED candidate ${newCandidate.internalId}")
                    }
                }
            }
        }

        // Remove stale candidates
        removeExpiredCandidates(matchedCandidates)

        Log.i(TAG, ">>> processFrame END: returning ${newlyConfirmed.size} newly confirmed candidates")
        return newlyConfirmed
    }

    /**
     * Find an existing candidate that matches the given detection.
     *
     * Matching strategy:
     * 1. If detection has a trackingId, try direct ID match first
     * 2. Otherwise, use spatial heuristics (IoU + center distance)
     */
    private fun findMatchingCandidate(detection: DetectionInfo): ObjectCandidate? {
        // Strategy 1: Direct tracking ID match (preferred)
        if (detection.trackingId != null) {
            val candidate = candidates[detection.trackingId]
            if (candidate != null) {
                return candidate
            }
        }

        // Strategy 2: Spatial matching for detections without tracking ID
        var bestMatch: ObjectCandidate? = null
        var bestScore = 0f

        for (candidate in candidates.values) {
            // Skip if last seen too long ago
            if (currentFrame - candidate.lastSeenFrame > config.maxFrameGap) {
                continue
            }

            // Calculate IoU (Intersection over Union)
            val iou = candidate.calculateIoU(detection.boundingBox)

            // Calculate normalized center distance
            // Use diagonal of detection box as normalization factor (more robust than fixed 1000px)
            val distance = candidate.distanceTo(detection.boundingBox)
            val detectionBoxDiagonal = kotlin.math.sqrt(
                (detection.boundingBox.width() * detection.boundingBox.width() +
                detection.boundingBox.height() * detection.boundingBox.height()).toDouble()
            ).toFloat()
            val normalizedDistance = if (detectionBoxDiagonal > 0) {
                (distance / detectionBoxDiagonal).coerceIn(0f, 2f) / 2f // Normalize to 0-1 range
            } else {
                1f // Maximum distance if box has no size
            }

            // Combined score: prioritize IoU, but also consider distance
            // IoU is weighted 60%, distance is weighted 40% (more balanced for movement)
            val score = iou * 0.6f + (1f - normalizedDistance) * 0.4f

            if (score > bestScore && score > config.minMatchScore) {
                bestScore = score
                bestMatch = candidate
            }
        }

        if (bestMatch != null) {
            Log.d(TAG, "Spatial match found: ${bestMatch.internalId} (score=$bestScore)")
        }

        return bestMatch
    }

    /**
     * Create a new candidate from a detection.
     */
    private fun createCandidate(detection: DetectionInfo): ObjectCandidate {
        // Use ML Kit trackingId if available, otherwise generate stable ID
        val id = detection.trackingId ?: generateStableId(detection)

        return ObjectCandidate(
            internalId = id,
            boundingBox = detection.boundingBox,
            lastSeenFrame = currentFrame,
            seenCount = 1,
            maxConfidence = detection.confidence,
            category = detection.category,
            labelText = detection.labelText,
            thumbnail = detection.thumbnail,
            firstSeenFrame = currentFrame,
            averageBoxArea = detection.normalizedBoxArea
        )
    }

    /**
     * Generate a stable ID for a detection without a trackingId.
     *
     * Uses spatial and category information to create a deterministic ID.
     */
    private fun generateStableId(detection: DetectionInfo): String {
        // Create a hash based on initial position and category
        val box = detection.boundingBox
        val positionHash = "${box.centerX().toInt()}_${box.centerY().toInt()}_${detection.category}"

        // Use UUID for now, but include position hash for debugging
        return "gen_${UUID.randomUUID()}_$positionHash"
    }

    /**
     * Check if a candidate meets the confirmation criteria.
     */
    private fun isConfirmed(candidate: ObjectCandidate): Boolean {
        return candidate.seenCount >= config.minFramesToConfirm &&
                candidate.maxConfidence >= config.minConfidence &&
                candidate.averageBoxArea >= config.minBoxArea
    }

    /**
     * Remove candidates that haven't been seen recently.
     */
    private fun removeExpiredCandidates(matchedIds: Set<String>) {
        val toRemove = mutableListOf<String>()

        for ((id, candidate) in candidates) {
            val framesSinceLastSeen = currentFrame - candidate.lastSeenFrame

            if (framesSinceLastSeen > config.expiryFrames) {
                toRemove.add(id)
                Log.d(TAG, "Expiring candidate $id: not seen for $framesSinceLastSeen frames")
            }
        }

        for (id in toRemove) {
            candidates[id]?.thumbnail?.recycle()
            candidates.remove(id)
            confirmedIds.remove(id)
        }
    }

    /**
     * Reset the tracker, clearing all candidates and state.
     * Call this when starting a new scan session or switching modes.
     */
    fun reset() {
        Log.i(TAG, "Resetting tracker: clearing ${candidates.size} candidates")

        // Clean up bitmaps
        for (candidate in candidates.values) {
            candidate.thumbnail?.recycle()
        }

        candidates.clear()
        confirmedIds.clear()
        currentFrame = 0
    }

    /**
     * Get current tracking statistics for debugging.
     */
    fun getStats(): TrackerStats {
        return TrackerStats(
            activeCandidates = candidates.size,
            confirmedCandidates = confirmedIds.size,
            currentFrame = currentFrame
        )
    }
}

/**
 * Raw detection information extracted from ML Kit.
 */
data class DetectionInfo(
    val trackingId: String?,
    val boundingBox: RectF,
    val confidence: Float,
    val category: ItemCategory,
    val labelText: String,
    val thumbnail: Bitmap?,
    val normalizedBoxArea: Float
)

/**
 * Configuration parameters for ObjectTracker behavior.
 */
data class TrackerConfig(
    /** Minimum frames a candidate must be seen before confirmation */
    val minFramesToConfirm: Int = 3,

    /** Minimum confidence threshold (0.0 - 1.0) */
    val minConfidence: Float = 0.4f,

    /** Minimum normalized bounding box area (0.0 - 1.0) */
    val minBoxArea: Float = 0.001f,

    /** Maximum frame gap for spatial matching */
    val maxFrameGap: Int = 5,

    /** Minimum matching score for spatial association (0.0 - 1.0) */
    val minMatchScore: Float = 0.3f,

    /** Frames without detection before candidate expires */
    val expiryFrames: Int = 10
)

/**
 * Tracking statistics for monitoring and debugging.
 */
data class TrackerStats(
    val activeCandidates: Int,
    val confirmedCandidates: Int,
    val currentFrame: Long
)
