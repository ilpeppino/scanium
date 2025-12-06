package com.example.objecta.items

import android.graphics.Bitmap
import com.example.objecta.ml.ItemCategory
import java.util.UUID

/**
 * Represents a detected object from the camera with pricing information.
 *
 * @param id Stable identifier (ML Kit tracking ID or generated UUID)
 * @param thumbnail Cropped image of the detected object
 * @param category Classified category
 * @param priceRange Price range in EUR (low to high)
 * @param timestamp When the item was detected
 * @param recognizedText Text extracted from document (for DOCUMENT items)
 * @param barcodeValue Barcode value (for BARCODE items)
 */
data class ScannedItem(
    val id: String = UUID.randomUUID().toString(),
    val thumbnail: Bitmap? = null,
    val category: ItemCategory,
    val priceRange: Pair<Double, Double>,
    val timestamp: Long = System.currentTimeMillis(),
    val recognizedText: String? = null,
    val barcodeValue: String? = null
) {
    /**
     * Formatted price range string for display.
     * Example: "€20 - €50"
     */
    val formattedPriceRange: String
        get() = "€%.0f - €%.0f".format(priceRange.first, priceRange.second)
}
