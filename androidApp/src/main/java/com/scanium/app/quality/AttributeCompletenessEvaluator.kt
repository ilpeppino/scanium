package com.scanium.app.quality

import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.shared.core.models.ml.ItemCategory

/**
 * Evaluates attribute completeness for scanned items based on category-specific requirements.
 *
 * Each category has a "required-for-selling" checklist with importance weights.
 * The evaluator computes:
 * - Completeness score (0-100)
 * - List of missing attributes ordered by importance
 * - Readiness status for marketplace listing
 */
object AttributeCompletenessEvaluator {
    /**
     * Minimum completeness score to be considered "ready for listing".
     */
    const val READY_THRESHOLD = 70

    /**
     * Minimum confidence for an attribute to count as "filled".
     */
    private const val MIN_CONFIDENCE_THRESHOLD = 0.3f

    /**
     * Attribute requirement with importance weight.
     * Higher weight = more important for selling.
     */
    data class AttributeRequirement(
        val key: String,
        val displayName: String,
        val weight: Int,
        val photoHint: String? = null,
    )

    /**
     * Completeness evaluation result.
     */
    data class CompletenessResult(
        val score: Int,
        val missingAttributes: List<MissingAttribute>,
        val filledAttributes: List<String>,
        val isReadyForListing: Boolean,
        val category: ItemCategory,
    ) {
        val totalRequired: Int
            get() = missingAttributes.size + filledAttributes.size

        val filledCount: Int
            get() = filledAttributes.size
    }

    /**
     * Missing attribute with guidance for how to fill it.
     */
    data class MissingAttribute(
        val key: String,
        val displayName: String,
        val importance: Int,
        val photoHint: String?,
    )

    /**
     * Category-specific attribute requirements.
     *
     * Weights (1-10):
     * - 10: Critical for identification/pricing
     * - 7-9: Important for buyer decision
     * - 4-6: Nice to have
     * - 1-3: Optional enhancement
     */
    private val categoryRequirements: Map<ItemCategory, List<AttributeRequirement>> =
        mapOf(
            ItemCategory.FASHION to
                listOf(
                    AttributeRequirement("brand", "Brand", 10, "Take a close-up of the label or logo"),
                    AttributeRequirement("itemType", "Item Type", 9, "Capture the full item clearly"),
                    AttributeRequirement("color", "Color", 8, "Ensure good lighting for accurate color"),
                    AttributeRequirement("size", "Size", 8, "Photo the size tag or label"),
                    AttributeRequirement("condition", "Condition", 7, "Show any wear, stains, or damage"),
                    AttributeRequirement("material", "Material", 5, "Close-up of fabric/material tag"),
                    AttributeRequirement("pattern", "Pattern", 3, null),
                    AttributeRequirement("style", "Style", 3, null),
                ),
            ItemCategory.ELECTRONICS to
                listOf(
                    AttributeRequirement("brand", "Brand", 10, "Photo the brand logo or label"),
                    AttributeRequirement("model", "Model", 10, "Capture model number (often on back/bottom)"),
                    AttributeRequirement("itemType", "Item Type", 9, "Show the full device"),
                    AttributeRequirement("condition", "Condition", 8, "Show screen condition and any damage"),
                    AttributeRequirement("color", "Color", 5, "Capture in good lighting"),
                    AttributeRequirement("storage", "Storage Capacity", 6, "Check settings or label for specs"),
                    AttributeRequirement("connectivity", "Connectivity", 4, null),
                ),
            ItemCategory.HOME_GOOD to
                listOf(
                    AttributeRequirement("itemType", "Item Type", 10, "Capture the full item"),
                    AttributeRequirement("brand", "Brand", 7, "Photo any brand markings"),
                    AttributeRequirement("color", "Color", 7, "Ensure accurate color representation"),
                    AttributeRequirement("condition", "Condition", 7, "Show any wear or damage"),
                    AttributeRequirement("material", "Material", 6, "Close-up of material/construction"),
                    AttributeRequirement("dimensions", "Dimensions", 5, "Include a reference object for scale"),
                    AttributeRequirement("style", "Style", 4, null),
                ),
            ItemCategory.FOOD to
                listOf(
                    AttributeRequirement("brand", "Brand", 9, "Photo the brand label"),
                    AttributeRequirement("itemType", "Product Type", 9, "Show the full product"),
                    AttributeRequirement("flavor", "Flavor/Variety", 7, "Capture flavor label"),
                    AttributeRequirement("size", "Size/Weight", 6, "Photo the size information"),
                    AttributeRequirement("expiration", "Expiration Date", 8, "Photo the expiration date"),
                ),
            ItemCategory.PLANT to
                listOf(
                    AttributeRequirement("itemType", "Plant Type", 10, "Capture the full plant"),
                    AttributeRequirement("color", "Flower Color", 6, "Photo blooms if present"),
                    AttributeRequirement("size", "Size", 5, "Include reference for scale"),
                    AttributeRequirement("potIncluded", "Pot Included", 4, null),
                ),
        )

    /**
     * Default requirements for categories without specific definitions.
     */
    private val defaultRequirements =
        listOf(
            AttributeRequirement("itemType", "Item Type", 10, "Capture the full item clearly"),
            AttributeRequirement("brand", "Brand", 7, "Photo any brand markings"),
            AttributeRequirement("color", "Color", 6, "Ensure good lighting"),
            AttributeRequirement("condition", "Condition", 6, "Show item condition"),
        )

    /**
     * Evaluate completeness for an item.
     *
     * @param category The item's category
     * @param attributes Current attributes map
     * @return Completeness evaluation result
     */
    fun evaluate(
        category: ItemCategory,
        attributes: Map<String, ItemAttribute>,
    ): CompletenessResult {
        val requirements = categoryRequirements[category] ?: defaultRequirements
        val totalWeight = requirements.sumOf { it.weight }

        val filledAttributes = mutableListOf<String>()
        val missingAttributes = mutableListOf<MissingAttribute>()
        var filledWeight = 0

        for (req in requirements) {
            val attr = attributes[req.key]
            val isFilled =
                attr != null &&
                    attr.value.isNotBlank() &&
                    (attr.confidence >= MIN_CONFIDENCE_THRESHOLD || attr.source == "user")

            if (isFilled) {
                filledAttributes.add(req.key)
                filledWeight += req.weight
            } else {
                missingAttributes.add(
                    MissingAttribute(
                        key = req.key,
                        displayName = req.displayName,
                        importance = req.weight,
                        photoHint = req.photoHint,
                    ),
                )
            }
        }

        // Sort missing attributes by importance (highest first)
        missingAttributes.sortByDescending { it.importance }

        // Calculate score as percentage of filled weight
        val score =
            if (totalWeight > 0) {
                ((filledWeight.toFloat() / totalWeight) * 100).toInt().coerceIn(0, 100)
            } else {
                100
            }

        return CompletenessResult(
            score = score,
            missingAttributes = missingAttributes,
            filledAttributes = filledAttributes,
            isReadyForListing = score >= READY_THRESHOLD,
            category = category,
        )
    }

    /**
     * Get the top N missing attributes for UI display.
     */
    fun getTopMissingAttributes(
        result: CompletenessResult,
        limit: Int = 3,
    ): List<MissingAttribute> = result.missingAttributes.take(limit)

    /**
     * Get photo guidance for the most important missing attribute.
     */
    fun getNextPhotoGuidance(result: CompletenessResult): String? =
        result.missingAttributes
            .firstOrNull { it.photoHint != null }
            ?.photoHint

    /**
     * Get requirements for a specific category.
     */
    fun getRequirementsForCategory(category: ItemCategory): List<AttributeRequirement> =
        categoryRequirements[category] ?: defaultRequirements

    /**
     * Check if a specific attribute is required for a category.
     */
    fun isAttributeRequired(
        category: ItemCategory,
        attributeKey: String,
    ): Boolean {
        val requirements = categoryRequirements[category] ?: defaultRequirements
        return requirements.any { it.key == attributeKey }
    }

    /**
     * Get the importance weight for an attribute in a category.
     */
    fun getAttributeImportance(
        category: ItemCategory,
        attributeKey: String,
    ): Int {
        val requirements = categoryRequirements[category] ?: defaultRequirements
        return requirements.find { it.key == attributeKey }?.weight ?: 0
    }
}
