package com.scanium.app.tracking

import com.scanium.app.ml.ItemCategory
import com.scanium.app.model.ImageRef
import com.scanium.app.model.NormalizedRect
import kotlin.math.sqrt

/**
 * Represents a candidate object being tracked across multiple frames.
 *
 * This intermediate representation stores tracking metadata to determine
 * when an object should be promoted to a confirmed ScannedItem.
 *
 * @property internalId Stable identifier for this candidate (ML Kit trackingId or generated)
 * @property boundingBox Current bounding box of the object (normalized coordinates)
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
    var boundingBox: NormalizedRect,
    var boundingBoxNorm: NormalizedRect? = null,
    var boundingBoxPx: FloatRect? = null,
    var lastSeenFrame: Long,
    var seenCount: Int = 1,
    var maxConfidence: Float = 0f,
    var category: ItemCategory = ItemCategory.UNKNOWN,
    var labelText: String = "",
    var thumbnail: ImageRef? = null,
    val firstSeenFrame: Long = lastSeenFrame,
    var averageBoxArea: Float = 0f
) {
    /**
     * Update this candidate with new detection information from the current frame.
     */
    fun update(
        newBoundingBox: NormalizedRect,
        frameNumber: Long,
        confidence: Float,
        newCategory: ItemCategory,
        newLabelText: String,
        newThumbnail: ImageRef?,
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
            (boundingBox.left + boundingBox.right) / 2f,
            (boundingBox.top + boundingBox.bottom) / 2f
        )
    }

    /**
     * Calculate Euclidean distance to another bounding box center.
     */
    fun distanceTo(otherBox: NormalizedRect): Float {
        val (cx1, cy1) = getCenterPoint()
        val cx2 = (otherBox.left + otherBox.right) / 2f
        val cy2 = (otherBox.top + otherBox.bottom) / 2f

        val dx = cx1 - cx2
        val dy = cy1 - cy2

        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Calculate Intersection over Union (IoU) with another bounding box.
     */
    fun calculateIoU(otherBox: NormalizedRect): Float {
        // Calculate intersection rectangle
        val intersectLeft = maxOf(boundingBox.left, otherBox.left)
        val intersectTop = maxOf(boundingBox.top, otherBox.top)
        val intersectRight = minOf(boundingBox.right, otherBox.right)
        val intersectBottom = minOf(boundingBox.bottom, otherBox.bottom)

        // Check if there's no intersection
        if (intersectLeft >= intersectRight || intersectTop >= intersectBottom) {
            return 0f
        }

        val intersectionWidth = intersectRight - intersectLeft
        val intersectionHeight = intersectBottom - intersectTop
        val intersectionArea = intersectionWidth * intersectionHeight

        val unionArea = boundingBox.area + otherBox.area - intersectionArea

        return if (unionArea > 0) intersectionArea / unionArea else 0f
    }
}

data class FloatRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)
