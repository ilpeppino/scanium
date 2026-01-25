package com.scanium.app.ml.detector

import android.util.Log
import com.google.mlkit.vision.objects.DetectedObject
import com.scanium.app.ItemCategory
import com.scanium.core.models.ml.LabelWithConfidence

/**
 * Resolves item categories using multi-label consensus and enrichment data.
 *
 * This addresses the issue where ML Kit's highest-confidence label may be incorrect
 * (e.g., "T-shirt" for a MacBook). By considering multiple labels and enrichment
 * attributes, we can make more accurate category determinations.
 *
 * See: howto/app/debugging/RCA_MACBOOK_TSHIRT_MISCLASSIFICATION.md
 */
object CategoryResolver {
    private const val TAG = "CategoryResolver"

    /**
     * Brand to category mapping for logo-based overrides.
     * When a brand logo is detected with high confidence, this mapping
     * provides a strong signal for the correct category.
     */
    private val BRAND_CATEGORY_MAP =
        mapOf(
            "apple" to ItemCategory.ELECTRONICS,
            "samsung" to ItemCategory.ELECTRONICS,
            "lg" to ItemCategory.ELECTRONICS,
            "sony" to ItemCategory.ELECTRONICS,
            "dell" to ItemCategory.ELECTRONICS,
            "hp" to ItemCategory.ELECTRONICS,
            "lenovo" to ItemCategory.ELECTRONICS,
            "microsoft" to ItemCategory.ELECTRONICS,
            "google" to ItemCategory.ELECTRONICS,
            "nike" to ItemCategory.FASHION,
            "adidas" to ItemCategory.FASHION,
            "puma" to ItemCategory.FASHION,
            "under armour" to ItemCategory.FASHION,
            "zara" to ItemCategory.FASHION,
            "h&m" to ItemCategory.FASHION,
            "gap" to ItemCategory.FASHION,
            "levi's" to ItemCategory.FASHION,
            "ikea" to ItemCategory.HOME_GOOD,
            "wayfair" to ItemCategory.HOME_GOOD,
        )

    /**
     * Resolves category from ML Kit labels using multi-label consensus.
     * Considers top N labels (up to 3) instead of just the highest confidence.
     *
     * @param detectedObject ML Kit detected object with labels
     * @param confidenceThreshold Minimum confidence to consider a label
     * @return Resolved ItemCategory
     */
    fun resolveCategoryFromLabels(
        detectedObject: DetectedObject,
        confidenceThreshold: Float = 0.2f,
    ): ItemCategory {
        val labels = detectedObject.labels.sortedByDescending { it.confidence }

        if (labels.isEmpty()) {
            Log.d(TAG, "No labels available, returning UNKNOWN")
            return ItemCategory.UNKNOWN
        }

        // Consider top 3 labels for voting
        val topLabels = labels.take(3).filter { it.confidence >= confidenceThreshold }

        if (topLabels.isEmpty()) {
            Log.d(
                TAG,
                "No labels meet confidence threshold $confidenceThreshold " +
                    "(best: ${labels.firstOrNull()?.text}:${labels.firstOrNull()?.confidence})",
            )
            return ItemCategory.UNKNOWN
        }

        // Build weighted votes for each category
        val votes = mutableMapOf<ItemCategory, Float>()
        topLabels.forEach { label ->
            val category = ItemCategory.fromMlKitLabel(label.text)
            val weight = label.confidence
            votes[category] = (votes[category] ?: 0f) + weight

            Log.d(
                TAG,
                "Label vote: \"${label.text}\" (${label.confidence}) → $category (cumulative: ${votes[category]})",
            )
        }

        // Find the category with the highest total confidence
        val winner = votes.maxByOrNull { it.value }
        val winnerCategory = winner?.key ?: ItemCategory.UNKNOWN
        val winnerConfidence = winner?.value ?: 0f

        Log.d(
            TAG,
            "Multi-label consensus: $winnerCategory (confidence: $winnerConfidence) from ${topLabels.size} labels",
        )

        return winnerCategory
    }

    /**
     * Resolves category with enrichment data, allowing post-detection refinement.
     * This is called after cloud enrichment completes to potentially override
     * the initial ML Kit category if high-confidence attributes contradict it.
     *
     * @param initialCategory Category from ML Kit detection
     * @param mlKitLabels All ML Kit labels with confidences
     * @param enrichmentBrand Brand detected from logo/text (e.g., "Apple")
     * @param enrichmentItemType Item type from classifier (e.g., "Laptop")
     * @param brandConfidence Confidence of brand detection (0-1)
     * @param itemTypeConfidence Confidence of item type detection (0-1)
     * @return Refined ItemCategory, or null if no refinement needed
     */
    fun refineCategoryWithEnrichment(
        initialCategory: ItemCategory,
        mlKitLabels: List<LabelWithConfidence>,
        enrichmentBrand: String?,
        enrichmentItemType: String?,
        brandConfidence: Float = 0f,
        itemTypeConfidence: Float = 0f,
    ): ItemCategory? {
        val votes = mutableMapOf<ItemCategory, Float>()

        // Start with ML Kit votes (consider top 3 labels)
        mlKitLabels.take(3).forEach { label ->
            val category = ItemCategory.fromMlKitLabel(label.text)
            votes[category] = (votes[category] ?: 0f) + label.confidence
        }

        Log.d(TAG, "Initial ML Kit votes: $votes")

        // Add brand-based vote (high weight for confident detections)
        if (!enrichmentBrand.isNullOrBlank() && brandConfidence >= 0.5f) {
            val brandCategory = BRAND_CATEGORY_MAP[enrichmentBrand.lowercase()]
            if (brandCategory != null) {
                val brandWeight = 0.9f * brandConfidence
                votes[brandCategory] = (votes[brandCategory] ?: 0f) + brandWeight
                Log.d(
                    TAG,
                    "Brand vote: \"$enrichmentBrand\" ($brandConfidence) → $brandCategory (+$brandWeight)",
                )
            }
        }

        // Add item type vote (high weight for confident detections)
        if (!enrichmentItemType.isNullOrBlank() && itemTypeConfidence >= 0.5f) {
            val itemTypeCategory = ItemCategory.fromClassifierLabel(enrichmentItemType)
            if (itemTypeCategory != ItemCategory.UNKNOWN) {
                val itemTypeWeight = 0.8f * itemTypeConfidence
                votes[itemTypeCategory] = (votes[itemTypeCategory] ?: 0f) + itemTypeWeight
                Log.d(
                    TAG,
                    "ItemType vote: \"$enrichmentItemType\" ($itemTypeConfidence) → $itemTypeCategory (+$itemTypeWeight)",
                )
            }
        }

        // Find winner
        val winner = votes.maxByOrNull { it.value }
        val refinedCategory = winner?.key ?: initialCategory

        // Only return refined category if it's different and has strong support
        return if (refinedCategory != initialCategory && (winner?.value ?: 0f) > 1.0f) {
            Log.i(
                TAG,
                "Category refined: $initialCategory → $refinedCategory " +
                    "(confidence: ${winner?.value}, brand: $enrichmentBrand, itemType: $enrichmentItemType)",
            )
            refinedCategory
        } else {
            Log.d(TAG, "No refinement needed (initial: $initialCategory, votes: $votes)")
            null
        }
    }

    /**
     * Determines if a brand-based category override should be applied.
     * This is used when a high-confidence brand logo is detected.
     *
     * @param brand Brand name (e.g., "Apple", "Nike")
     * @param confidence Brand detection confidence (0-1)
     * @return ItemCategory if override should be applied, null otherwise
     */
    fun getCategoryFromBrand(
        brand: String?,
        confidence: Float,
    ): ItemCategory? {
        if (brand.isNullOrBlank() || confidence < 0.7f) return null

        val category = BRAND_CATEGORY_MAP[brand.lowercase()]
        if (category != null) {
            Log.d(TAG, "Brand-based category: \"$brand\" ($confidence) → $category")
        }
        return category
    }
}
