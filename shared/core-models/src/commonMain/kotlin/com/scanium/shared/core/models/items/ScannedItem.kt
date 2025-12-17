package com.scanium.shared.core.models.items

import com.scanium.shared.core.models.ml.ItemCategory
import com.scanium.shared.core.models.model.ImageRef
import com.scanium.shared.core.models.model.NormalizedRect
import kotlin.math.roundToInt
import kotlin.system.getTimeMillis
import kotlin.random.Random

private const val HEX_DIGITS = "0123456789abcdef"

private fun appendByteHex(builder: StringBuilder, value: Byte) {
    val intVal = value.toInt() and 0xFF
    builder.append(HEX_DIGITS[intVal shr 4])
    builder.append(HEX_DIGITS[intVal and 0x0F])
}

private fun generateRandomId(): String {
    val bytes = Random.nextBytes(16)
    var index = 0
    return buildString(36) {
        repeat(4) { appendByteHex(this, bytes[index++]) }
        append('-')
        repeat(2) { appendByteHex(this, bytes[index++]) }
        append('-')
        repeat(2) { appendByteHex(this, bytes[index++]) }
        append('-')
        repeat(2) { appendByteHex(this, bytes[index++]) }
        append('-')
        repeat(6) { appendByteHex(this, bytes[index++]) }
    }
}

/**
 * Represents a detected object from the camera with pricing information.
 *
 * Uses portable types for cross-platform compatibility:
 * - ImageRef instead of Bitmap
 * - NormalizedRect instead of RectF
 * - FullImageUri generic type to avoid platform coupling
 *
 * @param id Stable identifier (tracking ID or generated value)
 * @param thumbnail Cropped image of the detected object (platform-agnostic)
 * @param category Classified category
 * @param priceRange Price range in EUR (low to high)
 * @param confidence Detection confidence score (0.0 to 1.0)
 * @param timestamp When the item was detected
 * @param recognizedText Text extracted from document (for DOCUMENT items)
 * @param barcodeValue Barcode value (for BARCODE items)
 * @param boundingBox Normalized bounding box position (0-1 coordinates)
 * @param labelText ML Kit classification label (if available)
 * @param fullImageUri Optional URI to a higher quality image (generic type for portability)
 * @param fullImagePath Optional path to higher quality image (legacy storage)
 * @param listingStatus Current eBay listing status
 * @param listingId eBay listing ID (if posted)
 * @param listingUrl External URL to view the listing (if posted)
 */
data class ScannedItem<FullImageUri>(
    val id: String = generateRandomId(),
    val thumbnail: ImageRef? = null,
    val thumbnailRef: ImageRef? = null,
    val category: ItemCategory,
    val priceRange: Pair<Double, Double>,
    val confidence: Float = 0.0f,
    val timestamp: Long = getTimeMillis(),
    val recognizedText: String? = null,
    val barcodeValue: String? = null,
    val boundingBox: NormalizedRect? = null,
    val labelText: String? = null,
    val fullImageUri: FullImageUri? = null,
    val fullImagePath: String? = null,
    val listingStatus: ItemListingStatus = ItemListingStatus.NOT_LISTED,
    val listingId: String? = null,
    val listingUrl: String? = null
) {
    /**
     * Formatted price range string for display.
     * Example: "€20 - €50"
     */
    val formattedPriceRange: String
        get() = "€${priceRange.first.roundToInt()} - €${priceRange.second.roundToInt()}"

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
