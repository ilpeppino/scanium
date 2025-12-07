package com.example.objecta.ml

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Represents raw detection data from ML Kit before conversion to ScannedItem.
 * This allows the multi-frame pipeline to access all detection metadata.
 *
 * @param trackingId Tracking ID from ML Kit, or generated UUID
 * @param boundingBox Object bounding box in image coordinates
 * @param labels List of classification labels with confidences
 * @param thumbnail Cropped thumbnail of the detected object
 */
data class RawDetection(
    val trackingId: String,
    val boundingBox: Rect?,
    val labels: List<LabelWithConfidence>,
    val thumbnail: Bitmap?
) {
    /**
     * Returns the best (highest confidence) label.
     */
    val bestLabel: LabelWithConfidence?
        get() = labels.maxByOrNull { it.confidence }

    /**
     * Returns the category determined from the best label.
     */
    val category: ItemCategory
        get() = bestLabel?.let { ItemCategory.fromMlKitLabel(it.text) } ?: ItemCategory.UNKNOWN

    /**
     * Returns effective confidence for this detection.
     * For objects with classification labels, uses the label confidence.
     * For objects without labels, uses a fallback confidence based on detection quality.
     */
    fun getEffectiveConfidence(): Float {
        return bestLabel?.confidence ?: run {
            // Objects detected without classification get a higher confidence
            // ML Kit's object detection is reliable even without classification
            if (trackingId.isNotEmpty() && boundingBox != null) {
                0.6f // Good confidence for tracked but unlabeled objects
            } else {
                0.4f // Moderate confidence for objects without tracking
            }
        }
    }

    /**
     * Returns normalized bounding box area (0.0 to 1.0).
     */
    fun getNormalizedArea(imageWidth: Int, imageHeight: Int): Float {
        val box = boundingBox ?: return 0f
        val boxArea = box.width() * box.height()
        val totalArea = imageWidth * imageHeight
        return if (totalArea > 0) {
            (boxArea.toFloat() / totalArea).coerceIn(0f, 1f)
        } else {
            0f
        }
    }
}

/**
 * Represents a classification label with confidence score.
 */
data class LabelWithConfidence(
    val text: String,
    val confidence: Float,
    val index: Int = 0
)
