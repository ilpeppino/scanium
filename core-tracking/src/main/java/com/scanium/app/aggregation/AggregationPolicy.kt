package com.scanium.app.aggregation

import com.scanium.app.items.ScannedItem

/**
 * Pure Kotlin class responsible for aggregation policy decisions.
 *
 * This class encapsulates the logic for:
 * - Finding the best matching aggregated item for a new detection
 * - Deciding whether to merge a detection into an existing item or create a new one
 * - Managing the similarity threshold for merge decisions
 *
 * Separating policy from scoring allows:
 * - Independent testing of decision logic
 * - Easy experimentation with different aggregation strategies
 * - Clear separation of concerns (scoring vs. decision making)
 *
 * @param config Configuration parameters for aggregation behavior
 * @param similarityScorer The scorer to use for calculating similarity
 */
class AggregationPolicy(
    private val config: AggregationConfig,
    private val similarityScorer: SimilarityScorer,
) {
    // Dynamic threshold override (if set, this overrides config.similarityThreshold)
    private var dynamicThreshold: Float? = null

    /**
     * Update the similarity threshold dynamically.
     *
     * This allows real-time adjustment of the threshold without recreating
     * the policy or aggregator. Useful for debugging and live tuning.
     *
     * @param threshold New threshold value (0-1), or null to use config default
     */
    fun updateSimilarityThreshold(threshold: Float?) {
        dynamicThreshold = threshold?.coerceIn(0f, 1f)
    }

    /**
     * Get the current effective similarity threshold.
     *
     * @return The active threshold (dynamic override if set, otherwise config default)
     */
    fun getCurrentSimilarityThreshold(): Float {
        return dynamicThreshold ?: config.similarityThreshold
    }

    /**
     * Find the best matching aggregated item for a new detection.
     *
     * Iterates through all existing items and finds the one with the highest
     * similarity score to the new detection.
     *
     * @param detection The new detection to match
     * @param existingItems Collection of existing aggregated items
     * @return MatchResult containing the best match (if any) and its similarity score
     */
    fun findBestMatch(
        detection: ScannedItem,
        existingItems: Collection<AggregatedItem>,
    ): MatchResult {
        if (existingItems.isEmpty()) {
            return MatchResult(null, 0f)
        }

        var bestMatch: AggregatedItem? = null
        var bestSimilarity = 0f

        for (item in existingItems) {
            val similarity = similarityScorer.calculateSimilarity(detection, item)

            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestMatch = item
            }
        }

        return MatchResult(bestMatch, bestSimilarity)
    }

    /**
     * Decide whether a detection should be merged with an existing item or create a new one.
     *
     * The decision is based on comparing the similarity score against the current threshold:
     * - If similarity >= threshold: MERGE
     * - If similarity < threshold: CREATE_NEW
     *
     * @param similarity The similarity score between detection and existing item
     * @return MergeDecision indicating whether to merge or create new
     */
    fun decideMerge(similarity: Float): MergeDecision {
        val threshold = getCurrentSimilarityThreshold()

        return if (similarity >= threshold) {
            MergeDecision.MERGE
        } else {
            MergeDecision.CREATE_NEW
        }
    }

    /**
     * Convenience method to find best match and decide on merge in one call.
     *
     * @param detection The new detection to process
     * @param existingItems Collection of existing aggregated items
     * @return AggregationDecision with match result and merge decision
     */
    fun decideAggregation(
        detection: ScannedItem,
        existingItems: Collection<AggregatedItem>,
    ): AggregationDecision {
        val matchResult = findBestMatch(detection, existingItems)
        val decision =
            if (matchResult.bestMatch != null) {
                decideMerge(matchResult.similarity)
            } else {
                MergeDecision.CREATE_NEW
            }

        return AggregationDecision(
            matchResult = matchResult,
            decision = decision,
            threshold = getCurrentSimilarityThreshold(),
        )
    }
}

/**
 * Result of finding the best matching item for a detection.
 *
 * @property bestMatch The best matching aggregated item (null if no items exist)
 * @property similarity The similarity score with the best match
 */
data class MatchResult(
    val bestMatch: AggregatedItem?,
    val similarity: Float,
)

/**
 * Decision on whether to merge or create new.
 */
enum class MergeDecision {
    /** Merge the detection into the existing item */
    MERGE,

    /** Create a new aggregated item for the detection */
    CREATE_NEW,
}

/**
 * Complete aggregation decision with context.
 *
 * @property matchResult The result of finding the best match
 * @property decision Whether to merge or create new
 * @property threshold The threshold used for the decision
 */
data class AggregationDecision(
    val matchResult: MatchResult,
    val decision: MergeDecision,
    val threshold: Float,
)
