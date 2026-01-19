package com.scanium.shared.core.models.items

/**
 * Represents an extracted attribute for a scanned item.
 *
 * Attributes are extracted from images via Vision API (OCR, logo detection, color analysis)
 * and stored with confidence levels to indicate reliability.
 *
 * @property value The extracted attribute value (e.g., "Dell", "blue", "XPS 15")
 * @property confidence Confidence score (0.0 to 1.0) indicating reliability
 * @property source Origin of the extraction (e.g., "logo", "ocr", "color", "label")
 */
data class ItemAttribute(
    val value: String,
    val confidence: Float = 0.0f,
    val source: String? = null,
) {
    /**
     * Confidence tier based on score thresholds.
     */
    val confidenceTier: AttributeConfidenceTier
        get() =
            when {
                confidence >= 0.8f -> AttributeConfidenceTier.HIGH
                confidence >= 0.5f -> AttributeConfidenceTier.MEDIUM
                else -> AttributeConfidenceTier.LOW
            }

    /**
     * Whether this attribute has high enough confidence to be trusted.
     */
    val isVerified: Boolean
        get() = confidence >= 0.5f

    companion object {
        /**
         * Create an attribute from a simple string value with default confidence.
         */
        fun fromValue(value: String): ItemAttribute = ItemAttribute(value = value)

        /**
         * Create an attribute from backend response map entry.
         */
        fun fromMapEntry(
            key: String,
            value: String,
        ): ItemAttribute = ItemAttribute(value = value)
    }
}

/**
 * Confidence tier classifications for extracted attributes.
 */
enum class AttributeConfidenceTier(
    val displayName: String,
    val description: String,
) {
    LOW(
        displayName = "Low",
        description = "Needs verification",
    ),
    MEDIUM(
        displayName = "Medium",
        description = "Likely correct",
    ),
    HIGH(
        displayName = "High",
        description = "Verified",
    ),
}
