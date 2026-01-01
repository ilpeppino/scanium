package com.scanium.app.ml

import kotlin.random.Random

/**
 * Generates mocked resale price ranges in EUR for the EU second-hand market.
 *
 * In a real app, this would call a backend pricing API.
 * For the PoC, we generate randomized price ranges based on category.
 */
object PricingEngine {
    /**
     * Base price ranges for each category (min to max in EUR).
     */
    private val categoryPriceRanges =
        mapOf(
            ItemCategory.FASHION to (8.0 to 40.0),
            ItemCategory.HOME_GOOD to (5.0 to 25.0),
            ItemCategory.FOOD to (2.0 to 10.0),
            ItemCategory.PLACE to (0.0 to 0.0),
// Places don't have prices
            ItemCategory.PLANT to (3.0 to 15.0),
            ItemCategory.ELECTRONICS to (20.0 to 300.0),
            ItemCategory.DOCUMENT to (0.0 to 0.0),
// Documents are informational, not physical goods
            ItemCategory.UNKNOWN to (5.0 to 20.0),
        )

    /**
     * Generates a price range for a detected object.
     *
     * @param category The item category
     * @param boundingBoxArea Optional: normalized area (0.0-1.0) of bounding box.
     *                        Larger objects might fetch higher prices.
     * @return Pair of (lowPrice, highPrice) in EUR
     */
    fun generatePriceRange(
        category: ItemCategory,
        boundingBoxArea: Float? = null,
    ): Pair<Double, Double> {
        val baseRange = categoryPriceRanges[category] ?: (5.0 to 20.0)

        // If the category is PLACE or DOCUMENT, return zero price
        if (category == ItemCategory.PLACE || category == ItemCategory.DOCUMENT) {
            return 0.0 to 0.0
        }

        // Add some randomization to make items unique
        val randomFactor = Random.nextDouble(0.8, 1.2)

        // Optionally adjust based on bounding box size (larger = slightly higher price)
        val sizeFactor =
            boundingBoxArea?.let {
                1.0 + (it * 0.3) // Up to 30% increase for large objects
            } ?: 1.0

        val adjustedMin = baseRange.first * randomFactor * sizeFactor
        val adjustedMax = baseRange.second * randomFactor * sizeFactor

        // Ensure min < max and round to reasonable values
        val finalMin = adjustedMin.coerceAtLeast(0.5)
        val finalMax = (adjustedMax.coerceAtLeast(finalMin + 1.0))

        return finalMin to finalMax
    }

    /**
     * Convenience method that returns a formatted price string.
     */
    fun generateFormattedPrice(
        category: ItemCategory,
        boundingBoxArea: Float? = null,
    ): String {
        val (low, high) = generatePriceRange(category, boundingBoxArea)
        return if (category == ItemCategory.PLACE || category == ItemCategory.DOCUMENT) {
            "N/A"
        } else {
            "€%.0f - €%.0f".format(low, high)
        }
    }
}
