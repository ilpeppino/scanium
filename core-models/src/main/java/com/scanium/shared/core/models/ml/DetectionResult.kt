package com.scanium.shared.core.models.ml

import com.scanium.shared.core.models.model.NormalizedRect
import kotlin.math.roundToInt

/**
 * Represents a real-time object detection result with bounding box and metadata.
 * Used for rendering overlay graphics on the camera preview.
 *
 * @param bboxNorm Normalized bounding box (0-1 coordinates)
 * @param category Classified category of the detected object
 * @param priceRange Estimated price range in EUR (low to high)
 * @param confidence Detection confidence score (0.0 to 1.0)
 * @param trackingId Optional tracking ID for object persistence across frames
 */
data class DetectionResult(
    val bboxNorm: NormalizedRect,
    val category: ItemCategory,
    val priceRange: Pair<Double, Double>,
    val confidence: Float,
    val trackingId: Int? = null
) {
    /**
     * Formatted price range string for display on overlay.
     * Example: "€20 - €50"
     */
    val formattedPriceRange: String
        get() = "€${priceRange.first.roundToInt()} - €${priceRange.second.roundToInt()}"
}
