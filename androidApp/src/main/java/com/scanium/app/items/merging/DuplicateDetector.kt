package com.scanium.app.items.merging

import com.scanium.app.AggregationConfig
import com.scanium.app.AggregationPresets
import com.scanium.app.ScannedItem
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Detects duplicate items WITHOUT mutating any state.
 *
 * Uses the same similarity calculation logic as ItemAggregator but operates
 * in a read-only mode for analysis purposes. Groups similar items and returns
 * MergeGroup suggestions for user review.
 *
 * @property config Aggregation configuration (default: BALANCED for conservative suggestions)
 */
class DuplicateDetector(
    private val config: AggregationConfig = AggregationPresets.BALANCED,
) {
    /**
     * Finds groups of duplicate items.
     *
     * Algorithm:
     * 1. Calculate pairwise similarity for all items
     * 2. Group items that exceed similarity threshold
     * 3. For each group, select highest confidence item as primary
     * 4. Return groups sorted by average similarity (highest first)
     *
     * @param allItems All items to analyze
     * @return List of merge groups, sorted by similarity descending
     */
    fun findDuplicateGroups(allItems: List<ScannedItem>): List<MergeGroup> {
        if (allItems.size < 2) return emptyList()

        // Track which items have been assigned to groups
        val assignedItems = mutableSetOf<String>()
        val groups = mutableListOf<MergeGroup>()

        // Sort by confidence (highest first) to prioritize best detections as primaries
        val sortedItems = allItems.sortedByDescending { it.confidence }

        for (primaryCandidate in sortedItems) {
            if (primaryCandidate.id in assignedItems) continue

            // Find all similar items (not yet assigned)
            val similarItems = mutableListOf<Pair<ScannedItem, Float>>() // (item, similarity)

            for (candidate in sortedItems) {
                if (candidate.id == primaryCandidate.id) continue
                if (candidate.id in assignedItems) continue

                val similarity = calculateSimilarity(primaryCandidate, candidate)
                if (similarity >= config.similarityThreshold) {
                    similarItems.add(candidate to similarity)
                }
            }

            // Only create a group if we found at least 1 similar item
            if (similarItems.isNotEmpty()) {
                val avgSimilarity = similarItems.map { it.second }.average().toFloat()
                val group = MergeGroup(
                    primaryItem = primaryCandidate,
                    similarItems = similarItems.map { it.first },
                    averageSimilarity = avgSimilarity,
                )
                groups.add(group)

                // Mark all items in this group as assigned
                assignedItems.add(primaryCandidate.id)
                similarItems.forEach { assignedItems.add(it.first.id) }
            }
        }

        // Return groups sorted by average similarity (highest first)
        return groups.sortedByDescending { it.averageSimilarity }
    }

    /**
     * Calculates similarity between two items using ItemAggregator logic.
     *
     * Similarity factors:
     * - Category match (required)
     * - Label text similarity (Levenshtein distance)
     * - Bounding box size similarity
     * - Spatial distance between centers
     *
     * Hard filters (return 0f if failed):
     * - Timestamp difference > 2s (prevents cross-photo merging)
     * - Category mismatch (if required)
     * - Label mismatch (if required)
     * - Size difference > maxSizeDifferenceRatio
     * - Distance > maxCenterDistanceRatio
     *
     * @return Similarity score 0.0-1.0, or 0.0 if hard filters failed
     */
    private fun calculateSimilarity(
        item1: ScannedItem,
        item2: ScannedItem,
    ): Float {
        // Hard filter: don't merge items from different captures (>2 seconds apart)
        val timestampDiffMs = abs(item1.timestamp - item2.timestamp)
        if (timestampDiffMs > 2000) {
            return 0f
        }

        // Hard filter: category must match if required
        if (config.categoryMatchRequired && item1.category != item2.category) {
            return 0f
        }

        val box1 = item1.boundingBox
        val box2 = item2.boundingBox

        var labelScore = 0f
        var sizeScore = 0f
        var distanceScore = 0f

        val label1 = item1.labelText
        val label2 = item2.labelText

        // Hard filter: label must match if required
        if (config.labelMatchRequired && (label1?.isEmpty() != false || label2?.isEmpty() != false)) {
            return 0f
        }

        // Calculate label similarity
        if (label1 != null && label1.isNotEmpty() && label2 != null && label2.isNotEmpty()) {
            labelScore = calculateLabelSimilarity(label1, label2)
        }

        // Calculate size similarity
        val area1 = box1?.area ?: 0f
        val area2 = box2?.area ?: 0f

        if (area1 > 0.0001f && area2 > 0.0001f) {
            val sizeRatio = minOf(area1, area2) / maxOf(area1, area2)
            val sizeDiff = abs(1f - sizeRatio)
            if (sizeDiff > config.maxSizeDifferenceRatio) {
                return 0f
            }
            sizeScore = sizeRatio
        }

        // Calculate spatial distance
        if (box1 != null && box2 != null) {
            val center1 = box1.center()
            val center2 = box2.center()
            val dx = center1.first - center2.first
            val dy = center1.second - center2.second
            val distance = sqrt(dx * dx + dy * dy)
            val normalizedDistance = distance / sqrt(2f) // Normalize to 0-1 range

            if (normalizedDistance > config.maxCenterDistanceRatio) {
                return 0f
            }
            distanceScore = 1f - (normalizedDistance / config.maxCenterDistanceRatio).coerceIn(0f, 1f)
        }

        // Calculate weighted score
        val weights = config.weights
        val totalWeight = weights.categoryWeight + weights.labelWeight + weights.sizeWeight + weights.distanceWeight
        if (totalWeight == 0f) return 0f

        val categoryScore = if (item1.category == item2.category) 1f else 0f
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
     * Calculates label similarity using Levenshtein distance.
     * Returns 1.0 for identical labels, 0.0 for completely different.
     */
    private fun calculateLabelSimilarity(
        label1: String,
        label2: String,
    ): Float {
        if (label1.isEmpty() || label2.isEmpty()) return 0f
        if (label1.equals(label2, ignoreCase = true)) return 1f

        val s1 = label1.lowercase().trim()
        val s2 = label2.lowercase().trim()
        val distance = levenshteinDistance(s1, s2)
        val maxLen = maxOf(s1.length, s2.length)

        return if (maxLen > 0) 1f - (distance.toFloat() / maxLen) else 0f
    }

    /**
     * Computes Levenshtein distance (edit distance) between two strings.
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
                        dp[i - 1][j] + 1,     // deletion
                        dp[i][j - 1] + 1,     // insertion
                        dp[i - 1][j - 1] + cost, // substitution
                    )
            }
        }

        return dp[len1][len2]
    }

    /**
     * Extension function to calculate center point of bounding box.
     * Returns (x, y) as normalized coordinates in 0-1 range.
     */
    private fun com.scanium.app.NormalizedRect.center(): Pair<Float, Float> {
        return Pair(
            (left + right) / 2f,
            (top + bottom) / 2f,
        )
    }
}
