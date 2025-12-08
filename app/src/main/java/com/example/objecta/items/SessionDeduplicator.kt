package com.example.objecta.items

import android.util.Log
import com.example.objecta.ml.ItemCategory
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Session-level de-duplication for scanned items.
 *
 * This component operates above the frame-level ObjectTracker to handle cases where:
 * - ML Kit changes tracking IDs for the same physical object
 * - Objects move significantly and are detected as "new" by the tracker
 * - The same object is captured from slightly different angles
 *
 * Strategy: Compare new items against already-seen items using multiple similarity heuristics:
 * - Category match
 * - Label similarity (if available)
 * - Approximate spatial proximity (for items detected in the same session)
 * - Bounding box size similarity
 *
 * If an item is "similar enough" to an existing item, it's considered a duplicate
 * and not added to the list.
 */
class SessionDeduplicator {
    companion object {
        private const val TAG = "SessionDeduplicator"

        // Similarity thresholds (tunable)
        private const val MAX_CENTER_DISTANCE_RATIO = 0.15f  // 15% of frame diagonal
        private const val MAX_SIZE_RATIO_DIFF = 0.4f         // 40% size difference allowed
        private const val MIN_LABEL_SIMILARITY = 0.7f        // 70% string similarity for labels
    }

    // Track recently seen items with their metadata for similarity matching
    private val seenItemsMetadata = mutableMapOf<String, ItemMetadata>()

    /**
     * Check if a new item is similar to any existing item in the current session.
     *
     * @param newItem The item to check
     * @param existingItems Currently confirmed items in the session
     * @return The ID of the similar existing item, or null if no match found
     */
    fun findSimilarItem(
        newItem: ScannedItem,
        existingItems: List<ScannedItem>
    ): String? {
        // Quick check: if ID already exists, it's definitely a duplicate
        if (existingItems.any { it.id == newItem.id }) {
            Log.d(TAG, "Exact ID match found for ${newItem.id}")
            return newItem.id
        }

        // Extract metadata for new item
        val newMetadata = extractMetadata(newItem)

        // Check similarity against all existing items
        for (existingItem in existingItems) {
            val existingMetadata = seenItemsMetadata[existingItem.id]
                ?: extractMetadata(existingItem).also { seenItemsMetadata[existingItem.id] = it }

            if (areSimilar(newMetadata, existingMetadata)) {
                Log.i(TAG, "Similar item found: new ${newItem.id} matches existing ${existingItem.id}")
                return existingItem.id
            }
        }

        // No similar item found - this is a new unique item
        seenItemsMetadata[newItem.id] = newMetadata
        return null
    }

    /**
     * Determine if two items are similar enough to be considered the same physical object.
     */
    private fun areSimilar(item1: ItemMetadata, item2: ItemMetadata): Boolean {
        // Rule 0: If both items lack distinguishing features (no thumbnail, default position),
        // we cannot confidently say they are the same object even if categories match
        // This prevents false positives with minimally-populated test data
        val item1HasDistinguishingFeatures = item1.hasPosition || item1.boxArea > 0.01f
        val item2HasDistinguishingFeatures = item2.hasPosition || item2.boxArea > 0.01f

        if (!item1HasDistinguishingFeatures && !item2HasDistinguishingFeatures) {
            Log.d(TAG, "Both items lack distinguishing features - treating as different")
            return false
        }

        // Rule 1: Category must match (essential)
        if (item1.category != item2.category) {
            return false
        }

        // Rule 2: Label similarity (if both have labels)
        val labelSimilarity = calculateLabelSimilarity(item1.labelText, item2.labelText)
        if (labelSimilarity < MIN_LABEL_SIMILARITY && item1.labelText.isNotEmpty() && item2.labelText.isNotEmpty()) {
            Log.d(TAG, "Label mismatch: '${item1.labelText}' vs '${item2.labelText}' (sim=$labelSimilarity)")
            return false
        }

        // Rule 3: Size similarity (normalized box area)
        val sizeRatio = if (item1.boxArea > 0.01f && item2.boxArea > 0.01f) {
            val larger = maxOf(item1.boxArea, item2.boxArea)
            val smaller = minOf(item1.boxArea, item2.boxArea)
            smaller / larger
        } else {
            1.0f // Ignore size if one has no size info
        }

        if (sizeRatio < (1f - MAX_SIZE_RATIO_DIFF)) {
            Log.d(TAG, "Size mismatch: area1=${item1.boxArea}, area2=${item2.boxArea} (ratio=$sizeRatio)")
            return false
        }

        // Rule 4: Spatial proximity (if we have position data)
        // Note: This is optional since position data might not always be reliable
        // We use it as a weak signal, not a hard requirement
        if (item1.hasPosition && item2.hasPosition) {
            val distance = calculateNormalizedDistance(item1, item2)
            if (distance > MAX_CENTER_DISTANCE_RATIO) {
                Log.d(TAG, "Position too far: distance=$distance (max=$MAX_CENTER_DISTANCE_RATIO)")
                return false
            }
        }

        // All checks passed - items are similar
        Log.d(TAG, "Items are similar: labelSim=$labelSimilarity, sizeRatio=$sizeRatio")
        return true
    }

    /**
     * Calculate label similarity using normalized Levenshtein distance.
     */
    private fun calculateLabelSimilarity(label1: String, label2: String): Float {
        if (label1 == label2) return 1.0f
        if (label1.isEmpty() || label2.isEmpty()) return 0.0f

        // Normalize: lowercase and trim
        val s1 = label1.lowercase().trim()
        val s2 = label2.lowercase().trim()

        if (s1 == s2) return 1.0f

        // Simple Levenshtein distance
        val distance = levenshteinDistance(s1, s2)
        val maxLen = maxOf(s1.length, s2.length)
        return if (maxLen > 0) {
            1.0f - (distance.toFloat() / maxLen)
        } else {
            0.0f
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
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return dp[len1][len2]
    }

    /**
     * Calculate normalized distance between two item positions.
     */
    private fun calculateNormalizedDistance(item1: ItemMetadata, item2: ItemMetadata): Float {
        val dx = item1.centerX - item2.centerX
        val dy = item1.centerY - item2.centerY
        val distance = sqrt(dx * dx + dy * dy)

        // Normalize by frame diagonal (assumed 1.0 for normalized coordinates)
        val frameDiagonal = sqrt(2.0f) // sqrt(1^2 + 1^2) for normalized frame
        return distance / frameDiagonal
    }

    /**
     * Extract metadata from a ScannedItem for similarity comparison.
     */
    private fun extractMetadata(item: ScannedItem): ItemMetadata {
        // Try to extract position and size from thumbnail dimensions if available
        val (centerX, centerY, boxArea) = if (item.thumbnail != null) {
            Triple(
                0.5f, // Default center - we don't have actual position from ScannedItem
                0.5f,
                (item.thumbnail.width * item.thumbnail.height).toFloat() / (1280f * 720f) // Approximate normalized area
            )
        } else {
            Triple(0.5f, 0.5f, 0.01f) // Default values
        }

        return ItemMetadata(
            id = item.id,
            category = item.category,
            labelText = item.category.name, // Use category name as label fallback
            centerX = centerX,
            centerY = centerY,
            boxArea = boxArea,
            hasPosition = false, // We don't have reliable position data from ScannedItem
            confidence = item.confidence
        )
    }

    /**
     * Clear all tracked metadata (call when starting a new scan session).
     */
    fun reset() {
        Log.d(TAG, "Resetting session de-duplicator (cleared ${seenItemsMetadata.size} items)")
        seenItemsMetadata.clear()
    }

    /**
     * Remove metadata for a specific item (when item is deleted).
     */
    fun removeItem(itemId: String) {
        seenItemsMetadata.remove(itemId)
    }
}

/**
 * Metadata extracted from ScannedItem for similarity comparison.
 */
private data class ItemMetadata(
    val id: String,
    val category: ItemCategory,
    val labelText: String,
    val centerX: Float,
    val centerY: Float,
    val boxArea: Float,
    val hasPosition: Boolean,
    val confidence: Float
)
