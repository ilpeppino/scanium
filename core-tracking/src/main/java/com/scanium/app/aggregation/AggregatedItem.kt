package com.scanium.app.aggregation

import com.scanium.shared.core.models.items.ScannedItem
import com.scanium.shared.core.models.ml.ItemCategory
import com.scanium.shared.core.models.model.ImageRef
import com.scanium.shared.core.models.model.NormalizedRect

/**
 * Represents a unique physical object aggregated from multiple detections.
 *
 * This is the core model for the real-time item aggregation system. Unlike ScannedItem
 * (which represents a single detection), AggregatedItem represents a persistent physical
 * object that may have been detected multiple times with varying tracking IDs, positions,
 * and confidence levels.
 *
 * Key features:
 * - Maintains stable identity across trackingId changes
 * - Tracks aggregation statistics (merge count, confidence history)
 * - Provides the "best" detection data for UI display
 * - Records timestamps for staleness detection
 *
 * @property aggregatedId Stable unique identifier for this aggregated item
 * @property category Item category (must match for merging)
 * @property labelText Primary label text (from highest confidence detection)
 * @property boundingBox Current bounding box (updated on merge, normalized coordinates)
 * @property thumbnail Best thumbnail image captured
 * @property maxConfidence Highest confidence seen across all detections
 * @property averageConfidence Running average of confidence scores
 * @property priceRange Price range (EUR low to high)
 * @property mergeCount Number of detections merged into this item
 * @property firstSeenTimestamp When first detected
 * @property lastSeenTimestamp When most recently updated
 * @property sourceDetectionIds Set of ScannedItem IDs merged into this aggregate
 * @property dominantColor Optional dominant color for visual similarity (future enhancement)
 */
data class AggregatedItem(
    val aggregatedId: String,
    var category: ItemCategory,
    var labelText: String,
    var boundingBox: NormalizedRect,
    var thumbnail: ImageRef?,
    var maxConfidence: Float,
    var averageConfidence: Float,
    var priceRange: Pair<Double, Double>,
    var mergeCount: Int = 1,
    val firstSeenTimestamp: Long = System.currentTimeMillis(),
    var lastSeenTimestamp: Long = System.currentTimeMillis(),
    val sourceDetectionIds: MutableSet<String> = mutableSetOf(),
    var dominantColor: Int? = null, // For future thumbnail-based similarity
    var enhancedCategory: ItemCategory? = null,
    var enhancedLabelText: String? = null,
    var enhancedPriceRange: Pair<Double, Double>? = null
) {
    /**
     * Convert this aggregated item to a ScannedItem for UI display.
     *
     * This creates a "snapshot" of the aggregated item suitable for the existing UI layer.
     * The ScannedItem retains the stable aggregatedId and uses the best available data.
     */
    fun toScannedItem(): ScannedItem {
        return ScannedItem(
            id = aggregatedId,
            thumbnail = thumbnail,
            category = enhancedCategory ?: category,
            priceRange = enhancedPriceRange ?: priceRange,
            confidence = maxConfidence,
            timestamp = lastSeenTimestamp,
            boundingBox = boundingBox,
            labelText = enhancedLabelText ?: labelText
        )
    }

    /**
     * Merge a new detection into this aggregated item.
     *
     * Updates statistics and keeps the "best" data:
     * - Uses highest confidence detection for label and thumbnail
     * - Updates bounding box to most recent position
     * - Maintains running average of confidence
     * - Tracks all source detection IDs
     *
     * @param detection The new detection to merge
     */
    fun merge(detection: ScannedItem) {
        // Update merge count
        mergeCount++

        // Add source ID
        sourceDetectionIds.add(detection.id)

        // Update confidence statistics
        averageConfidence = ((averageConfidence * (mergeCount - 1)) + detection.confidence) / mergeCount

        // If new detection has higher confidence, update primary data
        if (detection.confidence > maxConfidence) {
            maxConfidence = detection.confidence
            labelText = detection.labelText ?: labelText

            // Update thumbnail if new one is better quality
            detection.thumbnail?.let { thumbnail = it }
        }

        // Always update bounding box to latest position (object may have moved)
        detection.boundingBox?.let { boundingBox = it }

        // Update price range (take the wider range if different)
        val newMin = minOf(priceRange.first, detection.priceRange.first)
        val newMax = maxOf(priceRange.second, detection.priceRange.second)
        priceRange = Pair(newMin, newMax)

        // Update last seen timestamp
        lastSeenTimestamp = System.currentTimeMillis()
    }

    /**
     * Calculate the center point of the bounding box.
     */
    fun getCenterPoint(): Pair<Float, Float> {
        return Pair(
            (boundingBox.left + boundingBox.right) / 2f,
            (boundingBox.top + boundingBox.bottom) / 2f
        )
    }

    /**
     * Get normalized bounding box area.
     */
    fun getBoxArea(): Float {
        return boundingBox.area
    }

    /**
     * Check if this item is stale (hasn't been seen recently).
     *
     * @param maxAgeMs Maximum age in milliseconds
     * @return true if item is older than maxAgeMs
     */
    fun isStale(maxAgeMs: Long): Boolean {
        return (System.currentTimeMillis() - lastSeenTimestamp) > maxAgeMs
    }

    /**
     * Cleanup resources (call before removing from memory).
     * Note: ImageRef is immutable and doesn't require explicit cleanup.
     */
    fun cleanup() {
        thumbnail = null
    }
}
