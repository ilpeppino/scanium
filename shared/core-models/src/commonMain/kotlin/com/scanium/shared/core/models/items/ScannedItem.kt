package com.scanium.shared.core.models.items

import com.scanium.shared.core.models.ml.ItemCategory
import com.scanium.shared.core.models.model.ImageRef
import com.scanium.shared.core.models.model.NormalizedRect
import com.scanium.shared.core.models.pricing.PriceEstimationStatus
import com.scanium.shared.core.models.pricing.PriceRange
import kotlinx.datetime.Clock
import kotlin.random.Random

private const val HEX_DIGITS = "0123456789abcdef"

private fun appendByteHex(
    builder: StringBuilder,
    value: Byte,
) {
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
 * @param classificationStatus Cloud classification status (NOT_STARTED, PENDING, SUCCESS, FAILED)
 * @param domainCategoryId Fine-grained domain category ID from cloud classifier
 * @param classificationErrorMessage Error message if classification failed
 * @param classificationRequestId Backend request ID for debugging
 * @param attributes Extracted attributes (brand, color, model, etc.) with confidence scores
 */
data class ScannedItem<FullImageUri>(
    val id: String = generateRandomId(),
    val thumbnail: ImageRef? = null,
    val thumbnailRef: ImageRef? = null,
    val category: ItemCategory,
    val priceRange: Pair<Double, Double>,
    val estimatedPriceRange: PriceRange? = null,
    val priceEstimationStatus: PriceEstimationStatus = PriceEstimationStatus.Idle,
    val confidence: Float = 0.0f,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val recognizedText: String? = null,
    val barcodeValue: String? = null,
    val boundingBox: NormalizedRect? = null,
    val labelText: String? = null,
    val fullImageUri: FullImageUri? = null,
    val fullImagePath: String? = null,
    val listingStatus: ItemListingStatus = ItemListingStatus.NOT_LISTED,
    val listingId: String? = null,
    val listingUrl: String? = null,
    val classificationStatus: String = "NOT_STARTED",
    val domainCategoryId: String? = null,
    val classificationErrorMessage: String? = null,
    val classificationRequestId: String? = null,
    val qualityScore: Float = 0.0f,
    val userPriceCents: Long? = null,
    val condition: ItemCondition? = null,
    val attributes: Map<String, ItemAttribute> = emptyMap(),
    val visionAttributes: VisionAttributes = VisionAttributes.EMPTY,
    /**
     * Original detected attributes from backend classification, preserved for reference.
     * When user edits an attribute, this retains the original detected value so the UI can
     * show "Detected: X" alongside the user's override.
     */
    val detectedAttributes: Map<String, ItemAttribute> = emptyMap(),
    /**
     * User-editable attribute summary text.
     * Generated from structured attributes in a readable format:
     * Category: Fashion > Tops > T-Shirt
     * Brand: Nike
     * Color: Gray
     * Size: (missing)
     */
    val attributesSummaryText: String = "",
    /**
     * Flag indicating whether the user has manually edited the summary text.
     * When true, new attributes from enrichment will NOT auto-merge into the text.
     * Instead, they'll be shown as "Suggested Additions" for the user to accept.
     */
    val summaryTextUserEdited: Boolean = false,
    /**
     * Additional photos attached to this item (close-ups, alternate angles).
     * The primary photo is stored in thumbnail/fullImageUri.
     * These additional photos are used for:
     * - Re-enrichment to fill missing attributes
     * - Better AI generation with more visual context
     */
    val additionalPhotos: List<ItemPhoto> = emptyList(),
    /**
     * ID linking items captured from the same multi-object photo.
     * When a single capture detects multiple objects, all resulting items
     * share the same sourcePhotoId, allowing:
     * - Reference back to the full scene image
     * - Grouping related items in the UI
     */
    val sourcePhotoId: String? = null,
    /**
     * Status of each enrichment layer (A/B/C).
     * Used to show "Enriching..." UI and track progress.
     */
    val enrichmentStatus: EnrichmentLayerStatus = EnrichmentLayerStatus(),
    /**
     * AI-generated marketplace-ready title.
     * Set when user applies export assistant output.
     */
    val exportTitle: String? = null,
    /**
     * AI-generated marketplace-ready description.
     * Set when user applies export assistant output.
     */
    val exportDescription: String? = null,
    /**
     * AI-generated bullet highlights for the listing.
     */
    val exportBullets: List<String> = emptyList(),
    /**
     * Timestamp when export fields were generated.
     */
    val exportGeneratedAt: Long? = null,
    /**
     * Whether the export was served from cache.
     */
    val exportFromCache: Boolean = false,
    /**
     * LLM model used to generate the export.
     */
    val exportModel: String? = null,
    /**
     * Confidence tier of the AI-generated export (HIGH/MED/LOW).
     */
    val exportConfidenceTier: String? = null,
    /**
     * Completeness score (0-100) based on category-specific required attributes.
     * Updated after each enrichment pass or manual edit.
     */
    val completenessScore: Int = 0,
    /**
     * List of missing attribute keys ordered by importance.
     * Used to guide user on what photos to add.
     */
    val missingAttributes: List<String> = emptyList(),
    /**
     * Timestamp of the last enrichment operation.
     * Used by EnrichmentPolicy to avoid redundant API calls.
     */
    val lastEnrichedAt: Long? = null,
    /**
     * Photo shot types that have been captured for this item.
     * Used by PhotoPlaybook to avoid recommending duplicate shots.
     */
    val capturedShotTypes: List<String> = emptyList(),
    /**
     * Whether the item is considered ready for marketplace listing.
     * True when completenessScore >= threshold (default 70%).
     */
    val isReadyForListing: Boolean = false,
) {
    /**
     * Formatted price range string for display.
     * Example: "€20 - €50"
     */
    val formattedPriceRange: String
        get() =
            when (val status = priceEstimationStatus) {
                is PriceEstimationStatus.Ready -> status.priceRange.formatted()
                else -> ""
            }

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
        get() =
            when {
                confidence >= ConfidenceLevel.HIGH.threshold -> ConfidenceLevel.HIGH
                confidence >= ConfidenceLevel.MEDIUM.threshold -> ConfidenceLevel.MEDIUM
                else -> ConfidenceLevel.LOW
            }

    /**
     * Preferred human-readable label prioritizing vision attributes.
     *
     * Priority order:
     * 1. brand + itemType + color → "Brand ItemType · Color"
     * 2. itemType + color → "ItemType · Color"
     * 3. brand + itemType → "Brand ItemType"
     * 4. itemType only → "ItemType"
     * 5. labelText (suggestedLabel from backend)
     * 6. category.displayName (fallback)
     */
    val displayLabel: String
        get() {
            val brand =
                attributes["brand"]?.value?.trim()?.takeIf { it.isNotEmpty() }
                    ?: visionAttributes.primaryBrand?.trim()?.takeIf { it.isNotEmpty() }
            val itemType =
                attributes["itemType"]?.value?.trim()?.takeIf { it.isNotEmpty() }
                    ?: visionAttributes.itemType?.trim()?.takeIf { it.isNotEmpty() }
            val color =
                attributes["color"]?.value?.trim()?.takeIf { it.isNotEmpty() }
                    ?: visionAttributes.primaryColor?.name?.trim()?.takeIf { it.isNotEmpty() }

            // Build label using vision attributes with priority
            val label =
                when {
                    // Brand + ItemType + Color: "Labello Lip Balm · Blue"
                    brand != null && itemType != null && color != null ->
                        "$brand $itemType · $color"

                    // ItemType + Color: "Lip Balm · Blue"
                    itemType != null && color != null ->
                        "$itemType · $color"

                    // Brand + ItemType: "Labello Lip Balm"
                    brand != null && itemType != null ->
                        "$brand $itemType"

                    // Brand + Color (no itemType): "Labello · Blue"
                    brand != null && color != null ->
                        "$brand · $color"

                    // ItemType only: "Lip Balm"
                    itemType != null ->
                        itemType

                    // Brand only: "Labello"
                    brand != null ->
                        brand

                    // Fallback to labelText (suggestedLabel from backend)
                    else ->
                        labelText?.trim()?.takeIf { it.isNotEmpty() }
                }

            // Final fallback to category
            val result = label ?: category.displayName
            return capitalizeDisplayLabel(result)
        }

    /**
     * Formatted user price string for display.
     * Returns null if no user price is set.
     * Example: "€12.50"
     */
    val formattedUserPrice: String?
        get() {
            val cents = userPriceCents ?: return null
            val euros = cents / 100
            val centsPart = cents % 100
            return "€$euros.${centsPart.toString().padStart(2, '0')}"
        }
}

private fun capitalizeDisplayLabel(value: String): String {
    if (value.isEmpty()) return value
    val first = value[0].uppercaseChar()
    return if (value.length == 1) {
        first.toString()
    } else {
        first.toString() + value.substring(1)
    }
}

/**
 * Represents confidence level classifications for detected items.
 */
enum class ConfidenceLevel(
    val threshold: Float,
    val displayName: String,
    val description: String,
) {
    LOW(
        threshold = 0.0f,
        displayName = "Low",
        description = "Detection confidence is low",
    ),
    MEDIUM(
        threshold = 0.5f,
        displayName = "Medium",
        description = "Detection confidence is moderate",
    ),
    HIGH(
        threshold = 0.75f,
        displayName = "High",
        description = "Detection confidence is high",
    ),
}

/**
 * Represents the eBay listing status of a scanned item.
 */
enum class ItemListingStatus(
    val displayName: String,
    val description: String,
) {
    NOT_LISTED(
        displayName = "Not Listed",
        description = "Item has not been posted to eBay",
    ),
    LISTING_IN_PROGRESS(
        displayName = "Posting...",
        description = "Item is currently being posted to eBay",
    ),
    LISTED_ACTIVE(
        displayName = "Listed",
        description = "Item is actively listed on eBay",
    ),
    LISTING_FAILED(
        displayName = "Failed",
        description = "Failed to post item to eBay",
    ),
}
