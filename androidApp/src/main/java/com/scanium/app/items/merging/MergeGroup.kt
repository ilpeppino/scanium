package com.scanium.app.items.merging

import com.scanium.app.ScannedItem

/**
 * Represents a group of similar items that could be merged.
 *
 * @property primaryItem The item to keep after merging (typically highest confidence or first detected)
 * @property similarItems List of items that are similar to the primary item
 * @property averageSimilarity Average similarity score between primary and similar items (0.0-1.0)
 */
data class MergeGroup(
    val primaryItem: ScannedItem,
    val similarItems: List<ScannedItem>,
    val averageSimilarity: Float,
) {
    /**
     * Total number of items in this group (primary + similar items).
     */
    val totalCount: Int get() = 1 + similarItems.size

    /**
     * All item IDs in this group (primary + similar items).
     */
    val allItemIds: List<String> get() = listOf(primaryItem.id) + similarItems.map { it.id }
}

/**
 * Represents the state of merge suggestions in the UI.
 */
sealed class MergeSuggestionState {
    /**
     * No merge suggestions available.
     */
    object None : MergeSuggestionState()

    /**
     * Currently detecting duplicates in the background.
     */
    object Detecting : MergeSuggestionState()

    /**
     * Merge suggestions are available for user review.
     *
     * @property groups List of merge groups found
     * @property timestamp When these suggestions were generated
     */
    data class Available(
        val groups: List<MergeGroup>,
        val timestamp: Long = System.currentTimeMillis(),
    ) : MergeSuggestionState() {
        /**
         * Total number of items that would be merged (excludes primary items).
         */
        val totalSuggestedMerges: Int get() = groups.sumOf { it.similarItems.size }
    }

    /**
     * Suggestions were dismissed by the user.
     *
     * @property dismissedAt When the user dismissed the suggestions
     */
    data class Dismissed(
        val dismissedAt: Long = System.currentTimeMillis(),
    ) : MergeSuggestionState()
}
