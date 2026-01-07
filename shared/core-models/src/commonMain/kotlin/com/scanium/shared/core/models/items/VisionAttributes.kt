package com.scanium.shared.core.models.items

/**
 * Vision attributes extracted from Google Vision API.
 *
 * Contains raw extracted data from the Vision API:
 * - Detected colors with scores
 * - OCR text from the image
 * - Detected logos (brand indicators)
 * - Detected labels (category/material hints)
 * - Brand and model candidates for user selection
 *
 * This is a KMP-compatible data class for cross-platform use.
 */
data class VisionAttributes(
    val colors: List<VisionColor> = emptyList(),
    val ocrText: String? = null,
    val logos: List<VisionLogo> = emptyList(),
    val labels: List<VisionLabel> = emptyList(),
    val brandCandidates: List<String> = emptyList(),
    val modelCandidates: List<String> = emptyList(),
    /** Sellable item type noun (e.g., "Lip Balm", "T-Shirt") */
    val itemType: String? = null,
) {
    /**
     * Primary color (highest score) if available.
     */
    val primaryColor: VisionColor?
        get() = colors.maxByOrNull { it.score }

    /**
     * Primary brand from logos (highest score) if available.
     */
    val primaryBrand: String?
        get() = logos.maxByOrNull { it.score }?.name
            ?: brandCandidates.firstOrNull()

    /**
     * Primary model from candidates if available.
     */
    val primaryModel: String?
        get() = modelCandidates.firstOrNull()

    /**
     * Whether any meaningful vision data was extracted.
     */
    val isEmpty: Boolean
        get() = colors.isEmpty() &&
            ocrText.isNullOrBlank() &&
            logos.isEmpty() &&
            labels.isEmpty() &&
            brandCandidates.isEmpty() &&
            modelCandidates.isEmpty() &&
            itemType.isNullOrBlank()

    companion object {
        val EMPTY = VisionAttributes()
    }
}

/**
 * Color detected by Vision API.
 *
 * @property name Human-readable color name (e.g., "blue", "red")
 * @property hex RGB hex code (e.g., "#1E40AF")
 * @property score Confidence/prominence score (0.0 to 1.0)
 */
data class VisionColor(
    val name: String,
    val hex: String,
    val score: Float,
)

/**
 * Logo detected by Vision API.
 *
 * @property name Brand/company name (e.g., "IKEA", "Apple")
 * @property score Detection confidence (0.0 to 1.0)
 */
data class VisionLogo(
    val name: String,
    val score: Float,
)

/**
 * Label detected by Vision API.
 *
 * @property name Semantic label (e.g., "Furniture", "Electronics", "Wood")
 * @property score Detection confidence (0.0 to 1.0)
 */
data class VisionLabel(
    val name: String,
    val score: Float,
)
