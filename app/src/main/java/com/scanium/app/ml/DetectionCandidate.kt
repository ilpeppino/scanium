package com.scanium.app.ml

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Represents a detection candidate that is observed across multiple frames.
 * Candidates are promoted to confirmed ScannedItems once they meet minimum thresholds.
 *
 * @param id Unique identifier (tracking ID from ML Kit, or fallback UUID)
 * @param seenCount Number of frames this candidate has been observed in
 * @param maxConfidence Highest label confidence observed across all frames
 * @param category Best category determined from labels
 * @param categoryLabel Text label from ML Kit classification
 * @param lastBoundingBox Most recent bounding box
 * @param thumbnail Most recent thumbnail bitmap
 * @param firstSeenTimestamp When this candidate was first detected
 * @param lastSeenTimestamp When this candidate was last detected
 */
data class DetectionCandidate(
    val id: String,
    val seenCount: Int = 1,
    val maxConfidence: Float = 0.0f,
    val category: ItemCategory = ItemCategory.UNKNOWN,
    val categoryLabel: String = "",
    val lastBoundingBox: Rect? = null,
    val thumbnail: Bitmap? = null,
    val firstSeenTimestamp: Long = System.currentTimeMillis(),
    val lastSeenTimestamp: Long = System.currentTimeMillis()
) {
    /**
     * Returns true if this candidate meets the criteria for promotion to a confirmed item.
     */
    fun isReadyForPromotion(
        minSeenCount: Int,
        minConfidence: Float,
        minBoxArea: Float? = null
    ): Boolean {
        val meetsSeenCount = seenCount >= minSeenCount
        val meetsConfidence = maxConfidence >= minConfidence

        val meetsBoxArea = if (minBoxArea != null && lastBoundingBox != null) {
            val area = lastBoundingBox.width() * lastBoundingBox.height()
            area >= minBoxArea
        } else {
            true // No box area requirement or no box available
        }

        return meetsSeenCount && meetsConfidence && meetsBoxArea
    }

    /**
     * Creates an updated candidate with new observation data.
     */
    fun withNewObservation(
        confidence: Float,
        category: ItemCategory,
        categoryLabel: String,
        boundingBox: Rect?,
        thumbnail: Bitmap?
    ): DetectionCandidate {
        return copy(
            seenCount = seenCount + 1,
            maxConfidence = maxOf(maxConfidence, confidence),
            category = if (confidence > maxConfidence) category else this.category,
            categoryLabel = if (confidence > maxConfidence) categoryLabel else this.categoryLabel,
            lastBoundingBox = boundingBox ?: lastBoundingBox,
            thumbnail = thumbnail ?: this.thumbnail,
            lastSeenTimestamp = System.currentTimeMillis()
        )
    }

    /**
     * Returns the age of this candidate in milliseconds.
     */
    fun ageMs(): Long = System.currentTimeMillis() - firstSeenTimestamp

    /**
     * Returns the time since last seen in milliseconds.
     */
    fun timeSinceLastSeenMs(): Long = System.currentTimeMillis() - lastSeenTimestamp
}
