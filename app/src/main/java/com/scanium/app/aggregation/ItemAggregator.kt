package com.scanium.app.aggregation

import android.graphics.RectF
import android.util.Log
import com.scanium.app.items.ScannedItem
import com.scanium.app.ml.ItemCategory
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Real-time item aggregation engine for scanning mode.
 *
 * This component solves the duplicate detection problem by aggregating similar detections
 * into persistent AggregatedItems. Unlike the previous strict deduplication approach,
 * this system uses weighted similarity scoring that works even when:
 * - ML Kit trackingIds change frequently
 * - Bounding boxes shift during camera movement
 * - Users pan slowly across objects
 *
 * Core responsibilities:
 * - Maintain collection of AggregatedItems representing unique physical objects
 * - Compare new detections against existing items using multi-factor similarity
 * - Merge similar detections or create new aggregated items
 * - Provide tunable thresholds for different use cases
 * - Comprehensive logging for debugging and tuning
 *
 * @property config Configuration for aggregation behavior
 */
class ItemAggregator(
    private val config: AggregationConfig = AggregationConfig()
) {
    companion object {
        private const val TAG = "ItemAggregator"
    }

    // Active aggregated items, keyed by aggregatedId
    private val aggregatedItems = mutableMapOf<String, AggregatedItem>()

    /**
     * Process a new detection and return the resulting aggregated item.
     *
     * This is the main entry point for the aggregation system. For each detection:
     * 1. Find the most similar existing aggregated item (if any)
     * 2. If similarity >= threshold: merge into existing item
     * 3. If similarity < threshold: create new aggregated item
     *
     * @param detection The new ScannedItem detection to process
     * @return The AggregatedItem (either merged or newly created)
     */
    fun processDetection(detection: ScannedItem): AggregatedItem {
        Log.i(TAG, ">>> processDetection: id=${detection.id}, category=${detection.category}, confidence=${detection.confidence}")

        // Find best matching aggregated item
        val (bestMatch, bestSimilarity) = findBestMatch(detection)

        if (bestMatch != null && bestSimilarity >= config.similarityThreshold) {
            // Merge into existing item
            Log.i(TAG, "    ✓ MERGE: detection ${detection.id} → aggregated ${bestMatch.aggregatedId} (similarity=$bestSimilarity)")
            logSimilarityBreakdown(detection, bestMatch, bestSimilarity)

            bestMatch.merge(detection)
            return bestMatch
        } else {
            // Create new aggregated item
            val newItem = createAggregatedItem(detection)
            aggregatedItems[newItem.aggregatedId] = newItem

            if (bestMatch != null) {
                Log.i(TAG, "    ✗ CREATE NEW: similarity too low ($bestSimilarity < ${config.similarityThreshold})")
                logSimilarityBreakdown(detection, bestMatch, bestSimilarity)
            } else {
                Log.i(TAG, "    ✗ CREATE NEW: no existing items to compare")
            }

            Log.i(TAG, "    Created aggregated item ${newItem.aggregatedId}")
            return newItem
        }
    }

    /**
     * Process multiple detections in batch.
     *
     * @param detections List of new detections
     * @return List of resulting aggregated items (may be newly created or existing)
     */
    fun processDetections(detections: List<ScannedItem>): List<AggregatedItem> {
        Log.i(TAG, ">>> processDetections BATCH: ${detections.size} detections")
        return detections.map { processDetection(it) }
    }

    /**
     * Find the best matching aggregated item for a detection.
     *
     * @return Pair of (best matching item or null, similarity score)
     */
    private fun findBestMatch(detection: ScannedItem): Pair<AggregatedItem?, Float> {
        if (aggregatedItems.isEmpty()) {
            return Pair(null, 0f)
        }

        var bestMatch: AggregatedItem? = null
        var bestSimilarity = 0f

        for (item in aggregatedItems.values) {
            val similarity = calculateSimilarity(detection, item)

            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestMatch = item
            }
        }

        return Pair(bestMatch, bestSimilarity)
    }

    /**
     * Calculate weighted similarity score between a detection and an aggregated item.
     *
     * Uses multiple factors with configurable weights:
     * - Category match (required if config.categoryMatchRequired)
     * - Label text similarity (normalized Levenshtein distance)
     * - Bounding box size ratio
     * - Center distance (normalized by frame diagonal)
     * - Optional: thumbnail-based similarity (future enhancement)
     *
     * @return Similarity score in range [0.0, 1.0]
     */
    private fun calculateSimilarity(detection: ScannedItem, item: AggregatedItem): Float {
        // Hard filter: Category must match if required
        if (config.categoryMatchRequired && detection.category != item.category) {
            return 0f
        }

        // Scores for each factor (0-1 range)
        var categoryScore = if (detection.category == item.category) 1f else 0f
        var labelScore = 0f
        var sizeScore = 0f
        var distanceScore = 0f

        // 1. Label similarity
        val detectionLabel = detection.labelText ?: ""
        val itemLabel = item.labelText

        if (config.labelMatchRequired && (detectionLabel.isEmpty() || itemLabel.isEmpty())) {
            // If labels are required but missing, this is not a match
            return 0f
        }

        if (detectionLabel.isNotEmpty() && itemLabel.isNotEmpty()) {
            labelScore = calculateLabelSimilarity(detectionLabel, itemLabel)
        }

        // 2. Size similarity
        val detectionBox = detection.boundingBox
        val itemBox = item.boundingBox

        if (detectionBox != null) {
            val detectionArea = detectionBox.width() * detectionBox.height()
            val itemArea = itemBox.width() * itemBox.height()

            if (detectionArea > 0.0001f && itemArea > 0.0001f) {
                val sizeRatio = minOf(detectionArea, itemArea) / maxOf(detectionArea, itemArea)

                // Check hard limit
                val sizeDiff = abs(1f - sizeRatio)
                if (sizeDiff > config.maxSizeDifferenceRatio) {
                    Log.d(TAG, "    Size difference too large: $sizeDiff > ${config.maxSizeDifferenceRatio}")
                    return 0f // Size too different - definitely not the same object
                }

                sizeScore = sizeRatio
            }
        }

        // 3. Center distance
        if (detectionBox != null) {
            val detectionCenter = Pair(detectionBox.centerX(), detectionBox.centerY())
            val itemCenter = item.getCenterPoint()

            val dx = detectionCenter.first - itemCenter.first
            val dy = detectionCenter.second - itemCenter.second
            val distance = sqrt(dx * dx + dy * dy)

            // Normalize by frame diagonal (assume normalized coords 0-1)
            val frameDiagonal = sqrt(2f)
            val normalizedDistance = distance / frameDiagonal

            // Check hard limit
            if (normalizedDistance > config.maxCenterDistanceRatio) {
                Log.d(TAG, "    Center distance too large: $normalizedDistance > ${config.maxCenterDistanceRatio}")
                return 0f // Too far apart - not the same object
            }

            // Convert distance to similarity (0 distance = 1.0 similarity)
            distanceScore = 1f - (normalizedDistance / config.maxCenterDistanceRatio).coerceIn(0f, 1f)
        }

        // Weighted combination
        val weights = config.weights
        val totalWeight = weights.categoryWeight + weights.labelWeight +
                         weights.sizeWeight + weights.distanceWeight

        if (totalWeight == 0f) {
            Log.w(TAG, "Total weight is zero - returning 0 similarity")
            return 0f
        }

        val weightedScore = (
            categoryScore * weights.categoryWeight +
            labelScore * weights.labelWeight +
            sizeScore * weights.sizeWeight +
            distanceScore * weights.distanceWeight
        ) / totalWeight

        return weightedScore.coerceIn(0f, 1f)
    }

    /**
     * Calculate label similarity using normalized Levenshtein distance.
     */
    private fun calculateLabelSimilarity(label1: String, label2: String): Float {
        if (label1.isEmpty() || label2.isEmpty()) return 0f
        if (label1 == label2) return 1f

        val s1 = label1.lowercase().trim()
        val s2 = label2.lowercase().trim()
        if (s1 == s2) return 1f

        val distance = levenshteinDistance(s1, s2)
        val maxLen = maxOf(s1.length, s2.length)

        return if (maxLen > 0) {
            1f - (distance.toFloat() / maxLen)
        } else {
            0f
        }
    }

    /**
     * Calculate Levenshtein distance between two strings.
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length

        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        return dp[len1][len2]
    }

    /**
     * Create a new AggregatedItem from a detection.
     */
    private fun createAggregatedItem(detection: ScannedItem): AggregatedItem {
        val aggregatedId = "agg_${UUID.randomUUID()}"

        return AggregatedItem(
            aggregatedId = aggregatedId,
            category = detection.category,
            labelText = detection.labelText ?: "",
            boundingBox = detection.boundingBox ?: RectF(0f, 0f, 0f, 0f),
            thumbnail = detection.thumbnail,
            maxConfidence = detection.confidence,
            averageConfidence = detection.confidence,
            priceRange = detection.priceRange,
            mergeCount = 1,
            firstSeenTimestamp = detection.timestamp,
            lastSeenTimestamp = detection.timestamp,
            sourceDetectionIds = mutableSetOf(detection.id)
        )
    }

    /**
     * Get all current aggregated items.
     */
    fun getAllItems(): List<AggregatedItem> {
        return aggregatedItems.values.toList()
    }

    /**
     * Get aggregated items as ScannedItems for UI compatibility.
     */
    fun getScannedItems(): List<ScannedItem> {
        return aggregatedItems.values.map { it.toScannedItem() }
    }

    /**
     * Remove stale items that haven't been seen recently.
     *
     * @param maxAgeMs Maximum age in milliseconds
     * @return Number of items removed
     */
    fun removeStaleItems(maxAgeMs: Long): Int {
        val toRemove = aggregatedItems.values.filter { it.isStale(maxAgeMs) }

        for (item in toRemove) {
            Log.d(TAG, "Removing stale item ${item.aggregatedId} (age=${System.currentTimeMillis() - item.lastSeenTimestamp}ms)")
            item.cleanup()
            aggregatedItems.remove(item.aggregatedId)
        }

        return toRemove.size
    }

    /**
     * Remove a specific aggregated item.
     */
    fun removeItem(aggregatedId: String) {
        aggregatedItems[aggregatedId]?.cleanup()
        aggregatedItems.remove(aggregatedId)
    }

    /**
     * Clear all aggregated items (call when starting new session).
     */
    fun reset() {
        Log.i(TAG, "Resetting aggregator: clearing ${aggregatedItems.size} items")

        for (item in aggregatedItems.values) {
            item.cleanup()
        }

        aggregatedItems.clear()
    }

    /**
     * Get aggregation statistics for monitoring.
     */
    fun getStats(): AggregationStats {
        val totalMerges = aggregatedItems.values.sumOf { it.mergeCount - 1 }
        val avgMergesPerItem = if (aggregatedItems.isNotEmpty()) {
            totalMerges.toFloat() / aggregatedItems.size
        } else {
            0f
        }

        return AggregationStats(
            totalItems = aggregatedItems.size,
            totalMerges = totalMerges,
            averageMergesPerItem = avgMergesPerItem
        )
    }

    /**
     * Log detailed similarity breakdown for debugging.
     */
    private fun logSimilarityBreakdown(
        detection: ScannedItem,
        item: AggregatedItem,
        finalScore: Float
    ) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return

        val categoryMatch = detection.category == item.category
        val detectionLabel = detection.labelText ?: ""
        val itemLabel = item.labelText
        val labelSim = if (detectionLabel.isNotEmpty() && itemLabel.isNotEmpty()) {
            calculateLabelSimilarity(detectionLabel, itemLabel)
        } else {
            0f
        }

        val detectionBox = detection.boundingBox
        var sizeSim = 0f
        var distanceSim = 0f

        if (detectionBox != null) {
            val detectionArea = detectionBox.width() * detectionBox.height()
            val itemArea = item.getBoxArea()
            if (detectionArea > 0.0001f && itemArea > 0.0001f) {
                sizeSim = minOf(detectionArea, itemArea) / maxOf(detectionArea, itemArea)
            }

            val dx = detectionBox.centerX() - item.boundingBox.centerX()
            val dy = detectionBox.centerY() - item.boundingBox.centerY()
            val distance = sqrt(dx * dx + dy * dy)
            val frameDiagonal = sqrt(2f)
            val normalizedDistance = distance / frameDiagonal
            distanceSim = 1f - (normalizedDistance / config.maxCenterDistanceRatio).coerceIn(0f, 1f)
        }

        Log.d(TAG, "    Similarity breakdown:")
        Log.d(TAG, "      - Category match: $categoryMatch (${detection.category} vs ${item.category})")
        Log.d(TAG, "      - Label similarity: $labelSim ('$detectionLabel' vs '$itemLabel')")
        Log.d(TAG, "      - Size similarity: $sizeSim")
        Log.d(TAG, "      - Distance similarity: $distanceSim")
        Log.d(TAG, "      - Final weighted score: $finalScore")
    }
}

/**
 * Configuration parameters for item aggregation.
 */
data class AggregationConfig(
    /** Minimum similarity score (0-1) for merging detections */
    val similarityThreshold: Float = 0.6f,

    /** Maximum normalized center distance ratio (0-1) for considering items similar */
    val maxCenterDistanceRatio: Float = 0.25f,

    /** Maximum size difference ratio (0-1) for considering items similar */
    val maxSizeDifferenceRatio: Float = 0.5f,

    /** If true, category must match exactly for merging */
    val categoryMatchRequired: Boolean = true,

    /** If true, label text must be present and similar for merging */
    val labelMatchRequired: Boolean = false,

    /** Weights for similarity scoring */
    val weights: SimilarityWeights = SimilarityWeights()
)

/**
 * Weights for combining different similarity factors.
 *
 * These weights determine the relative importance of each factor in the
 * final similarity score. Higher weight = more important.
 */
data class SimilarityWeights(
    val categoryWeight: Float = 0.3f,  // 30% - Category must match
    val labelWeight: Float = 0.25f,    // 25% - Label similarity
    val sizeWeight: Float = 0.20f,     // 20% - Bounding box size
    val distanceWeight: Float = 0.25f  // 25% - Spatial proximity
)

/**
 * Statistics for monitoring aggregation performance.
 */
data class AggregationStats(
    val totalItems: Int,
    val totalMerges: Int,
    val averageMergesPerItem: Float
)
