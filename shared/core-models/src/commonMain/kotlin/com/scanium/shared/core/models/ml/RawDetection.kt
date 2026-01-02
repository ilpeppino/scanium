package com.scanium.shared.core.models.ml

import com.scanium.shared.core.models.model.ImageRef
import com.scanium.shared.core.models.model.NormalizedRect

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
    val thumbnailRef: ImageRef? = null,
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
            if (trackingId.isNotEmpty() && bboxNorm != null) {
                0.6f
            } else {
                0.4f
            }
        }
    }

    /**
     * Returns normalized bounding box area (0.0 to 1.0).
     */
    fun getNormalizedArea(
        imageWidth: Int = 0,
        imageHeight: Int = 0,
    ): Float { // parameters kept for compatibility
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
    val index: Int = 0,
)
