package com.scanium.app.aggregation

import com.scanium.app.items.ScannedItem
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pure Kotlin class responsible for calculating similarity scores between detections
 * and aggregated items.
 *
 * This class encapsulates all similarity scoring logic, making it:
 * - Easy to test in isolation
 * - Easy to modify without affecting aggregation behavior
 * - Reusable across different aggregation strategies
 *
 * The scorer uses multiple factors with configurable weights:
 * - Category match
 * - Label text similarity (normalized Levenshtein distance)
 * - Bounding box size ratio
 * - Center distance (normalized by frame diagonal)
 *
 * @param config Configuration parameters including weights and thresholds
 */
class SimilarityScorer(
    private val config: AggregationConfig,
) {
    /**
     * Calculate weighted similarity score between a detection and an aggregated item.
     *
     * Returns a score in range [0.0, 1.0] where:
     * - 1.0 = perfect match
     * - 0.0 = no similarity or hard constraint violated
     *
     * Hard constraints (return 0 immediately if violated):
     * - Category mismatch (if categoryMatchRequired)
     * - Label mismatch (if labelMatchRequired)
     * - Size difference too large (> maxSizeDifferenceRatio)
     * - Center distance too large (> maxCenterDistanceRatio)
     *
     * @param detection The new detection to compare
     * @param aggregatedItem The existing aggregated item
     * @return Similarity score in range [0.0, 1.0]
     */
    fun calculateSimilarity(
        detection: ScannedItem,
        aggregatedItem: AggregatedItem,
    ): Float {
        // Hard filter: Category must match if required
        if (config.categoryMatchRequired && detection.category != aggregatedItem.category) {
            return 0f
        }

        // Individual factor scores (0-1 range)
        val categoryScore = if (detection.category == aggregatedItem.category) 1f else 0f
        val labelScore = calculateLabelScore(detection, aggregatedItem)
        val sizeScore = calculateSizeScore(detection, aggregatedItem)
        val distanceScore = calculateDistanceScore(detection, aggregatedItem)

        // Check hard constraints on size and distance
        if (sizeScore == -1f || distanceScore == -1f) {
            return 0f // Hard constraint violated
        }

        // Weighted combination
        val weights = config.weights
        val totalWeight =
            weights.categoryWeight + weights.labelWeight +
                weights.sizeWeight + weights.distanceWeight

        if (totalWeight == 0f) {
            return 0f
        }

        val weightedScore =
            (
                categoryScore * weights.categoryWeight +
                    labelScore * weights.labelWeight +
                    sizeScore * weights.sizeWeight +
                    distanceScore * weights.distanceWeight
            ) / totalWeight

        return weightedScore.coerceIn(0f, 1f)
    }

    /**
     * Calculate label similarity score.
     *
     * @return Score in range [0.0, 1.0], or 0f if label requirement violated
     */
    private fun calculateLabelScore(
        detection: ScannedItem,
        aggregatedItem: AggregatedItem,
    ): Float {
        val detectionLabel = detection.labelText ?: ""
        val itemLabel = aggregatedItem.labelText

        // Hard filter: If labels are required but missing, no match
        if (config.labelMatchRequired && (detectionLabel.isEmpty() || itemLabel.isEmpty())) {
            return 0f
        }

        return if (detectionLabel.isNotEmpty() && itemLabel.isNotEmpty()) {
            calculateLabelSimilarity(detectionLabel, itemLabel)
        } else {
            0f
        }
    }

    /**
     * Calculate size similarity score.
     *
     * @return Score in range [0.0, 1.0], or -1f if size constraint violated
     */
    private fun calculateSizeScore(
        detection: ScannedItem,
        aggregatedItem: AggregatedItem,
    ): Float {
        val detectionBox = detection.boundingBox ?: return 0f
        val itemBox = aggregatedItem.boundingBox

        val detectionArea = detectionBox.area
        val itemArea = itemBox.area

        if (detectionArea < 0.0001f || itemArea < 0.0001f) {
            return 0f
        }

        val sizeRatio = minOf(detectionArea, itemArea) / maxOf(detectionArea, itemArea)
        val sizeDiff = abs(1f - sizeRatio)

        // Hard constraint: Size difference too large
        if (sizeDiff > config.maxSizeDifferenceRatio) {
            return -1f // Signal constraint violation
        }

        return sizeRatio
    }

    /**
     * Calculate distance similarity score based on center point proximity.
     *
     * @return Score in range [0.0, 1.0], or -1f if distance constraint violated
     */
    private fun calculateDistanceScore(
        detection: ScannedItem,
        aggregatedItem: AggregatedItem,
    ): Float {
        val detectionBox = detection.boundingBox ?: return 0f

        val detectionCenterX = (detectionBox.left + detectionBox.right) / 2f
        val detectionCenterY = (detectionBox.top + detectionBox.bottom) / 2f
        val itemCenter = aggregatedItem.getCenterPoint()

        val dx = detectionCenterX - itemCenter.first
        val dy = detectionCenterY - itemCenter.second
        val distance = sqrt(dx * dx + dy * dy)

        // Normalize by frame diagonal (assume normalized coords 0-1)
        val frameDiagonal = sqrt(2f)
        val normalizedDistance = distance / frameDiagonal

        // Hard constraint: Too far apart
        if (normalizedDistance > config.maxCenterDistanceRatio) {
            return -1f // Signal constraint violation
        }

        // Convert distance to similarity (0 distance = 1.0 similarity)
        return 1f - (normalizedDistance / config.maxCenterDistanceRatio).coerceIn(0f, 1f)
    }

    /**
     * Calculate label similarity using normalized Levenshtein distance.
     *
     * @param label1 First label to compare
     * @param label2 Second label to compare
     * @return Similarity score in range [0.0, 1.0]
     */
    fun calculateLabelSimilarity(
        label1: String,
        label2: String,
    ): Float {
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
     *
     * This is the minimum number of single-character edits (insertions, deletions,
     * or substitutions) required to change one string into the other.
     *
     * @param s1 First string
     * @param s2 Second string
     * @return Edit distance
     */
    private fun levenshteinDistance(
        s1: String,
        s2: String,
    ): Int {
        val len1 = s1.length
        val len2 = s2.length

        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] =
                    minOf(
                        dp[i - 1][j] + 1,
                        dp[i][j - 1] + 1,
                        dp[i - 1][j - 1] + cost,
                    )
            }
        }

        return dp[len1][len2]
    }

    /**
     * Get a detailed breakdown of similarity components for debugging.
     *
     * @param detection The new detection
     * @param aggregatedItem The existing aggregated item
     * @return SimilarityBreakdown with individual component scores
     */
    fun getSimilarityBreakdown(
        detection: ScannedItem,
        aggregatedItem: AggregatedItem,
    ): SimilarityBreakdown {
        val categoryMatch = detection.category == aggregatedItem.category
        val detectionLabel = detection.labelText ?: ""
        val itemLabel = aggregatedItem.labelText

        val labelSimilarity =
            if (detectionLabel.isNotEmpty() && itemLabel.isNotEmpty()) {
                calculateLabelSimilarity(detectionLabel, itemLabel)
            } else {
                0f
            }

        val detectionBox = detection.boundingBox
        var sizeSimilarity = 0f
        var distanceSimilarity = 0f

        if (detectionBox != null) {
            val detectionArea = detectionBox.area
            val itemArea = aggregatedItem.getBoxArea()
            if (detectionArea > 0.0001f && itemArea > 0.0001f) {
                sizeSimilarity = minOf(detectionArea, itemArea) / maxOf(detectionArea, itemArea)
            }

            val detectionCenterX = (detectionBox.left + detectionBox.right) / 2f
            val detectionCenterY = (detectionBox.top + detectionBox.bottom) / 2f
            val itemCenter = aggregatedItem.getCenterPoint()

            val dx = detectionCenterX - itemCenter.first
            val dy = detectionCenterY - itemCenter.second
            val distance = sqrt(dx * dx + dy * dy)
            val frameDiagonal = sqrt(2f)
            val normalizedDistance = distance / frameDiagonal
            distanceSimilarity = 1f - (normalizedDistance / config.maxCenterDistanceRatio).coerceIn(0f, 1f)
        }

        val finalScore = calculateSimilarity(detection, aggregatedItem)

        return SimilarityBreakdown(
            categoryMatch = categoryMatch,
            categoryScore = if (categoryMatch) 1f else 0f,
            labelSimilarity = labelSimilarity,
            sizeSimilarity = sizeSimilarity,
            distanceSimilarity = distanceSimilarity,
            finalWeightedScore = finalScore,
        )
    }
}

/**
 * Detailed breakdown of similarity components for debugging and analysis.
 */
data class SimilarityBreakdown(
    val categoryMatch: Boolean,
    val categoryScore: Float,
    val labelSimilarity: Float,
    val sizeSimilarity: Float,
    val distanceSimilarity: Float,
    val finalWeightedScore: Float,
)
