package com.scanium.domain.repository

/**
 * Interface for estimating item prices based on category and attributes.
 *
 * Implementations:
 * - RuleBasedPriceEstimator: Uses simple rules (current implementation in PricingEngine)
 * - MachineLearningPriceEstimator: ML model-based pricing (future)
 * - MarketplacePriceEstimator: Queries real marketplace data (future)
 * - CachedPriceEstimator: Wraps another estimator with caching
 *
 * Design:
 * - Takes category + attributes, returns price range
 * - Returns confidence score (how reliable is this estimate)
 * - Currency-aware (EUR, USD, etc.)
 *
 * Usage:
 * ```
 * val estimator: PriceEstimator = RuleBasedPriceEstimator()
 * val estimate = estimator.estimatePrice(
 *     domainCategoryId = "furniture_sofa",
 *     attributes = mapOf("condition" to "good", "material" to "leather")
 * )
 * println("${estimate.minPrice}-${estimate.maxPrice} ${estimate.currency}")
 * // Output: "50.0-500.0 EUR"
 * ```
 */
interface PriceEstimator {
    /**
     * Estimate price for an item.
     *
     * @param domainCategoryId Category of item (e.g., "furniture_sofa")
     * @param attributes Item attributes (brand, color, material, condition, size, etc.)
     * @param currency Desired currency for estimate (default: EUR)
     * @return Price estimate with range and confidence
     */
    suspend fun estimatePrice(
        domainCategoryId: String,
        attributes: Map<String, String> = emptyMap(),
        currency: String = "EUR",
    ): PriceEstimate

    /**
     * Get price factors explanation.
     * Helps users understand why an item is priced a certain way.
     *
     * @param domainCategoryId Category of item
     * @param attributes Item attributes
     * @return List of factors affecting price (e.g., "Brand: +20%", "Condition: Good")
     */
    suspend fun getPriceFactors(
        domainCategoryId: String,
        attributes: Map<String, String>,
    ): List<PriceFactor> = emptyList()
}

/**
 * Price estimate for an item.
 *
 * @property minPrice Minimum estimated price
 * @property maxPrice Maximum estimated price
 * @property currency Currency code (ISO 4217: EUR, USD, GBP, etc.)
 * @property confidence Confidence in estimate (0.0 to 1.0)
 * @property source Where estimate came from (RULE_BASED, ML_MODEL, MARKETPLACE)
 * @property explanation Optional human-readable explanation
 */
data class PriceEstimate(
    val minPrice: Double,
    val maxPrice: Double,
    val currency: String,
    val confidence: Float,
    val source: PriceEstimateSource = PriceEstimateSource.RULE_BASED,
    val explanation: String? = null,
) {
    init {
        require(minPrice >= 0) { "Min price cannot be negative" }
        require(maxPrice >= minPrice) { "Max price must be >= min price" }
        require(confidence in 0f..1f) { "Confidence must be between 0 and 1" }
    }

    /**
     * Average price (midpoint of range).
     */
    val averagePrice: Double get() = (minPrice + maxPrice) / 2.0

    /**
     * Price range width.
     */
    val rangeWidth: Double get() = maxPrice - minPrice

    /**
     * Is this a narrow estimate (small range)?
     */
    val isNarrowEstimate: Boolean get() = rangeWidth / averagePrice < 0.3 // Range < 30% of average

    /**
     * Format price for display.
     * Example: "€50 - €500"
     */
    fun formatForDisplay(): String {
        val symbol =
            when (currency) {
                "EUR" -> "€"
                "USD" -> "$"
                "GBP" -> "£"
                else -> currency
            }
        return "$symbol${minPrice.toInt()} - $symbol${maxPrice.toInt()}"
    }
}

/**
 * Source of price estimate.
 */
enum class PriceEstimateSource {
    /**
     * Rule-based estimation (simple heuristics).
     * Fast, but less accurate.
     */
    RULE_BASED,

    /**
     * Machine learning model-based estimation.
     * More accurate, requires training data.
     */
    ML_MODEL,

    /**
     * Real marketplace data (eBay, Vinted, etc.).
     * Most accurate, but requires API calls.
     */
    MARKETPLACE,

    /**
     * User-provided price (manual override).
     */
    USER_OVERRIDE,
}

/**
 * Factor affecting price estimate.
 *
 * @property name Factor name (e.g., "Brand", "Condition", "Material")
 * @property impact Impact on price (-1.0 to +1.0, where 0 = neutral)
 * @property description Human-readable description
 */
data class PriceFactor(
    val name: String,
    val impact: Float,
    val description: String,
) {
    /**
     * Format impact as percentage.
     * Example: "+20%" or "-10%"
     */
    fun formatImpact(): String {
        val percentage = (impact * 100).toInt()
        return if (percentage >= 0) "+$percentage%" else "$percentage%"
    }
}

/**
 * Exception thrown when price estimation fails.
 */
class PriceEstimationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
