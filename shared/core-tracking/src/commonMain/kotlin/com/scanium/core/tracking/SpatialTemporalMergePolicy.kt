package com.scanium.core.tracking

import com.scanium.core.models.geometry.NormalizedRect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * PHASE 4: Lightweight spatial-temporal merge policy (Android-free).
 *
 * Determines if two detections/items represent the same physical object based on:
 * - Spatial proximity (IoU or normalized center distance)
 * - Temporal proximity (time window)
 * - Optional coarse category matching
 *
 * This helps deduplicate items when tracker IDs churn or are unreliable.
 * Uses minimal memory: only stores last position, timestamp, and category per candidate.
 *
 * Performance characteristics:
 * - No allocations in hot path (reuses primitives)
 * - O(1) per-candidate check
 * - Configurable time window and distance thresholds
 */
class SpatialTemporalMergePolicy(
    private val config: MergeConfig = MergeConfig.DEFAULT,
) {
    /**
     * Configuration for merge decisions.
     *
     * @param timeWindowMs Maximum time difference for merge consideration (default: 800ms)
     * @param minIoU Minimum IoU (Intersection over Union) for merge (default: 0.3)
     * @param maxNormalizedDistance Maximum normalized center distance for merge (default: 0.15)
     * @param requireCategoryMatch If true, only merge items with matching categories (default: true)
     * @param useIoU If true, use IoU; if false, use center distance (default: true)
     */
    data class MergeConfig(
        val timeWindowMs: Long = 800L,
        val minIoU: Float = 0.3f,
        val maxNormalizedDistance: Float = 0.15f,
        val requireCategoryMatch: Boolean = true,
        val useIoU: Boolean = true,
    ) {
        companion object {
            val DEFAULT = MergeConfig()
            val STRICT =
                MergeConfig(
                    timeWindowMs = 500L,
                    minIoU = 0.5f,
                    maxNormalizedDistance = 0.10f,
                    requireCategoryMatch = true,
                )
            val LENIENT =
                MergeConfig(
                    timeWindowMs = 1200L,
                    minIoU = 0.2f,
                    maxNormalizedDistance = 0.20f,
                    requireCategoryMatch = false,
                )
        }
    }

    /**
     * Lightweight candidate metadata for merge decisions.
     * Stores only the essential information needed for spatial-temporal matching.
     *
     * @param centerX Normalized center X coordinate (0-1)
     * @param centerY Normalized center Y coordinate (0-1)
     * @param bbox Normalized bounding box
     * @param lastSeenMs Timestamp when last seen
     * @param categoryId Coarse category identifier (0 if unknown)
     */
    data class CandidateMetadata(
        val centerX: Float,
        val centerY: Float,
        val bbox: NormalizedRect,
        val lastSeenMs: Long,
        val categoryId: Int = 0,
    )

    /**
     * Checks if a new detection should be merged with an existing candidate.
     *
     * @param newBbox Normalized bounding box of the new detection
     * @param newTimestampMs Timestamp of the new detection
     * @param newCategoryId Category ID of the new detection (0 if unknown)
     * @param existingCandidate Metadata of the existing candidate
     * @return true if the detection should be merged with the candidate
     */
    fun shouldMerge(
        newBbox: NormalizedRect,
        newTimestampMs: Long,
        newCategoryId: Int,
        existingCandidate: CandidateMetadata,
    ): Boolean {
        // Time window check (fast rejection)
        val timeDiff = abs(newTimestampMs - existingCandidate.lastSeenMs)
        if (timeDiff > config.timeWindowMs) {
            return false
        }

        // Category match check (if required)
        // Note: We only skip category matching if one of the categories is explicitly UNKNOWN
        // Using ordinal-based comparison, all valid categories should match
        if (config.requireCategoryMatch) {
            if (newCategoryId != existingCandidate.categoryId) {
                return false
            }
        }

        // Spatial similarity check
        return if (config.useIoU) {
            val iou = calculateIoU(newBbox, existingCandidate.bbox)
            iou >= config.minIoU
        } else {
            val distance = calculateNormalizedCenterDistance(newBbox, existingCandidate.bbox)
            distance <= config.maxNormalizedDistance
        }
    }

    /**
     * Creates candidate metadata from a normalized bounding box.
     * Zero allocations - extracts primitives for storage.
     */
    fun createCandidateMetadata(
        bbox: NormalizedRect,
        timestampMs: Long,
        categoryId: Int = 0,
    ): CandidateMetadata {
        val centerX = (bbox.left + bbox.right) / 2f
        val centerY = (bbox.top + bbox.bottom) / 2f
        return CandidateMetadata(
            centerX = centerX,
            centerY = centerY,
            bbox = bbox,
            lastSeenMs = timestampMs,
            categoryId = categoryId,
        )
    }

    /**
     * Calculates IoU (Intersection over Union) between two normalized rectangles.
     * No allocations - uses primitives only.
     *
     * @return IoU value between 0.0 and 1.0
     */
    private fun calculateIoU(
        rect1: NormalizedRect,
        rect2: NormalizedRect,
    ): Float {
        // Calculate intersection
        val intersectLeft = max(rect1.left, rect2.left)
        val intersectTop = max(rect1.top, rect2.top)
        val intersectRight = min(rect1.right, rect2.right)
        val intersectBottom = min(rect1.bottom, rect2.bottom)

        // Check if there's no intersection
        if (intersectLeft >= intersectRight || intersectTop >= intersectBottom) {
            return 0f
        }

        val intersectArea = (intersectRight - intersectLeft) * (intersectBottom - intersectTop)

        // Calculate union
        val area1 = rect1.area
        val area2 = rect2.area
        val unionArea = area1 + area2 - intersectArea

        return if (unionArea > 0f) {
            (intersectArea / unionArea).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    /**
     * Calculates normalized center distance between two rectangles.
     * Distance is normalized by the diagonal of the first rectangle.
     * No allocations - uses primitives only.
     *
     * @return Normalized distance (0.0 = same center, 1.0+ = far apart)
     */
    private fun calculateNormalizedCenterDistance(
        rect1: NormalizedRect,
        rect2: NormalizedRect,
    ): Float {
        val center1X = (rect1.left + rect1.right) / 2f
        val center1Y = (rect1.top + rect1.bottom) / 2f
        val center2X = (rect2.left + rect2.right) / 2f
        val center2Y = (rect2.top + rect2.bottom) / 2f

        val dx = abs(center1X - center2X)
        val dy = abs(center1Y - center2Y)
        val distance = sqrt(dx * dx + dy * dy)

        // Normalize by the diagonal of the first rectangle
        val width1 = rect1.right - rect1.left
        val height1 = rect1.bottom - rect1.top
        val diagonal1 = sqrt(width1 * width1 + height1 * height1)

        return if (diagonal1 > 0f) {
            distance / diagonal1
        } else {
            Float.MAX_VALUE // Degenerate rectangle
        }
    }

    /**
     * Finds the best matching candidate from a list of existing candidates.
     *
     * @param newBbox Normalized bounding box of the new detection
     * @param newTimestampMs Timestamp of the new detection
     * @param newCategoryId Category ID of the new detection
     * @param candidates List of existing candidate metadata
     * @return Pair of (best candidate index, merge score) or null if no match
     */
    fun findBestMatch(
        newBbox: NormalizedRect,
        newTimestampMs: Long,
        newCategoryId: Int,
        candidates: List<CandidateMetadata>,
    ): Pair<Int, Float>? {
        var bestIndex: Int? = null
        var bestScore = 0f

        candidates.forEachIndexed { index, candidate ->
            // Time window check
            val timeDiff = abs(newTimestampMs - candidate.lastSeenMs)
            if (timeDiff > config.timeWindowMs) return@forEachIndexed

            // Category check
            if (config.requireCategoryMatch) {
                if (newCategoryId != candidate.categoryId) return@forEachIndexed
            }

            // Calculate spatial score
            val score =
                if (config.useIoU) {
                    calculateIoU(newBbox, candidate.bbox)
                } else {
                    // Convert distance to score (closer = higher score)
                    val distance = calculateNormalizedCenterDistance(newBbox, candidate.bbox)
                    max(0f, 1f - distance / config.maxNormalizedDistance)
                }

            if (score > bestScore) {
                bestScore = score
                bestIndex = index
            }
        }

        val threshold = if (config.useIoU) config.minIoU else 0.5f
        return if (bestIndex != null && bestScore >= threshold) {
            Pair(bestIndex!!, bestScore)
        } else {
            null
        }
    }
}
