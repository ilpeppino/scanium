package com.scanium.app.items

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import com.scanium.app.ml.ItemCategory
import java.util.UUID

/**
 * Represents a detected object from the camera with pricing information.
 *
 * @param id Stable identifier (ML Kit tracking ID or generated UUID)
 * @param thumbnail Cropped image of the detected object
 * @param category Classified category
 * @param priceRange Price range in EUR (low to high)
 * @param confidence Detection confidence score (0.0 to 1.0)
 * @param timestamp When the item was detected
 * @param recognizedText Text extracted from document (for DOCUMENT items)
 * @param barcodeValue Barcode value (for BARCODE items)
 * @param boundingBox Normalized bounding box position (0-1 coordinates)
 * @param labelText ML Kit classification label (if available)
 * @param fullImageUri Optional URI to higher quality image (for listing purposes)
 * @param listingStatus Current eBay listing status
 * @param listingId eBay listing ID (if posted)
 * @param listingUrl External URL to view the listing (if posted)
 */
data class ScannedItem(
    val id: String = UUID.randomUUID().toString(),
    val thumbnail: Bitmap? = null,
    val category: ItemCategory,
    val priceRange: Pair<Double, Double>,
    val confidence: Float = 0.0f,
    val timestamp: Long = System.currentTimeMillis(),
    val recognizedText: String? = null,
    val barcodeValue: String? = null,
    val boundingBox: RectF? = null,
    val labelText: String? = null,
    val fullImageUri: Uri? = null,
    val listingStatus: ItemListingStatus = ItemListingStatus.NOT_LISTED,
    val listingId: String? = null,
    val listingUrl: String? = null
) {
    /**
     * Formatted price range string for display.
     * Example: "€20 - €50"
     */
    val formattedPriceRange: String
        get() = "€%.0f - €%.0f".format(priceRange.first, priceRange.second)

    /**
     * Formatted confidence percentage for display.
     * Example: "85%"
     */
    val formattedConfidence: String
        get() = "${(confidence * 100).toInt()}%"

    /**
     * Confidence level classification based on thresholds.
     */
    val confidenceLevel: ConfidenceLevel
        get() = when {
            confidence >= ConfidenceLevel.HIGH.threshold -> ConfidenceLevel.HIGH
            confidence >= ConfidenceLevel.MEDIUM.threshold -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
}

/**
 * Represents confidence level classifications for detected items.
 */
enum class ConfidenceLevel(
    val threshold: Float,
    val displayName: String,
    val description: String
) {
    LOW(
        threshold = 0.0f,
        displayName = "Low",
        description = "Detection confidence is low"
    ),
    MEDIUM(
        threshold = 0.5f,
        displayName = "Medium",
        description = "Detection confidence is moderate"
    ),
    HIGH(
        threshold = 0.75f,
        displayName = "High",
        description = "Detection confidence is high"
    )
}

/**
 * Represents the eBay listing status of a scanned item.
 */
enum class ItemListingStatus(
    val displayName: String,
    val description: String
) {
    NOT_LISTED(
        displayName = "Not Listed",
        description = "Item has not been posted to eBay"
    ),
    LISTING_IN_PROGRESS(
        displayName = "Posting...",
        description = "Item is currently being posted to eBay"
    ),
    LISTED_ACTIVE(
        displayName = "Listed",
        description = "Item is actively listed on eBay"
    ),
    LISTING_FAILED(
        displayName = "Failed",
        description = "Failed to post item to eBay"
    )
}
