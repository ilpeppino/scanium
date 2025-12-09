package com.scanium.app.ml

import android.graphics.Rect

/**
 * Represents a real-time object detection result with bounding box and metadata.
 * Used for rendering overlay graphics on the camera preview.
 *
 * @param boundingBox Bounding box in image coordinates
 * @param category Classified category of the detected object
 * @param priceRange Estimated price range in EUR (low to high)
 * @param confidence Detection confidence score (0.0 to 1.0)
 * @param trackingId Optional tracking ID for object persistence across frames
 */
data class DetectionResult(
    val boundingBox: Rect,
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
        get() = "€%.0f - €%.0f".format(priceRange.first, priceRange.second)
}
