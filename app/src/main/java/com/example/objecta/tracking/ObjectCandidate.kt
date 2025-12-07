package com.example.objecta.tracking

import android.graphics.Bitmap
import android.graphics.RectF
import com.example.objecta.ml.ItemCategory

/**
 * Represents a candidate object being tracked across multiple frames.
 *
 * This intermediate representation stores tracking metadata to determine
 * when an object should be promoted to a confirmed ScannedItem.
 *
 * @property internalId Stable identifier for this candidate (ML Kit trackingId or generated)
 * @property boundingBox Current bounding box of the object
 * @property lastSeenFrame Frame number when this candidate was last observed
 * @property seenCount Number of frames this candidate has been detected in
 * @property maxConfidence Maximum label confidence observed across all frames
 * @property category The object category (based on ML Kit labels)
 * @property labelText The most confident label text
 * @property thumbnail Best quality thumbnail captured so far
 * @property firstSeenFrame Frame number when first detected
 * @property averageBoxArea Running average of normalized bounding box area
 */
data class ObjectCandidate(
    val internalId: String,
    var boundingBox: RectF,
    var lastSeenFrame: Long,
    var seenCount: Int = 1,
    var maxConfidence: Float = 0f,
    var category: ItemCategory = ItemCategory.UNKNOWN,
    var labelText: String = "",
    var thumbnail: Bitmap? = null,
    val firstSeenFrame: Long = lastSeenFrame,
    var averageBoxArea: Float = 0f
) {
    /**
     * Update this candidate with new detection information from the current frame.
     */
    fun update(
        newBoundingBox: RectF,
        frameNumber: Long,
        confidence: Float,
        newCategory: ItemCategory,
        newLabelText: String,
        newThumbnail: Bitmap?,
        boxArea: Float
    ) {
        boundingBox = newBoundingBox
        lastSeenFrame = frameNumber
        seenCount++

        // Track maximum confidence
        if (confidence > maxConfidence) {
            maxConfidence = confidence
            category = newCategory
            labelText = newLabelText

            // Update thumbnail when we get better confidence
            if (newThumbnail != null) {
                thumbnail?.recycle()
                thumbnail = newThumbnail
            }
        }

        // Update running average of box area
        averageBoxArea = (averageBoxArea * (seenCount - 1) + boxArea) / seenCount
    }

    /**
     * Calculate the center point of the bounding box.
     */
    fun getCenterPoint(): Pair<Float, Float> {
        return Pair(
            boundingBox.centerX(),
            boundingBox.centerY()
        )
    }

    /**
     * Calculate Euclidean distance to another bounding box center.
     */
    fun distanceTo(otherBox: RectF): Float {
        val (cx1, cy1) = getCenterPoint()
        val cx2 = otherBox.centerX()
        val cy2 = otherBox.centerY()

        val dx = cx1 - cx2
        val dy = cy1 - cy2

        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /**
     * Calculate Intersection over Union (IoU) with another bounding box.
     */
    fun calculateIoU(otherBox: RectF): Float {
        val intersection = RectF()
        if (!intersection.setIntersect(boundingBox, otherBox)) {
            return 0f
        }

        val intersectionArea = intersection.width() * intersection.height()
        val unionArea = boundingBox.width() * boundingBox.height() +
                otherBox.width() * otherBox.height() - intersectionArea

        return if (unionArea > 0) intersectionArea / unionArea else 0f
    }
}
