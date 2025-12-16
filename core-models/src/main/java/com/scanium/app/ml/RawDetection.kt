package com.scanium.app.ml

import com.scanium.app.model.ImageRef
import com.scanium.app.model.NormalizedRect

/**
 * Represents raw detection data from ML Kit before conversion to ScannedItem.
 * This allows the multi-frame pipeline to access all detection metadata.
 *
 * @param trackingId Tracking ID from ML Kit, or generated UUID
 * @param bboxNorm Normalized bounding box in image coordinates (0-1)
 * @param labels List of classification labels with confidences
 * @param thumbnailRef Portable thumbnail reference (platform-neutral)
 */
data class RawDetection(
    val trackingId: String,
    val bboxNorm: NormalizedRect? = null,
    val labels: List<LabelWithConfidence>,
    val thumbnailRef: ImageRef? = null
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
            if (trackingId.isNotEmpty() && bboxNorm != null) {
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
        val box = bboxNorm ?: return 0f
        return box.area.coerceIn(0f, 1f)
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
