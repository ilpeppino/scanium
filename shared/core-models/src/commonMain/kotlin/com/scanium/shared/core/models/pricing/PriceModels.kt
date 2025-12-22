package com.scanium.shared.core.models.pricing

import kotlin.math.roundToInt

data class Money(
    val amount: Double,
    val currencyCode: String = "EUR"
)

data class PriceRange(
    val low: Money,
    val high: Money,
    val confidence: Float = 0.0f,
    val source: String = "",
    val metadata: Map<String, String> = emptyMap()
) {
    val currencyCode: String = high.currencyCode

    fun formatted(): String {
        val symbol = when (currencyCode) {
            "EUR" -> "€"
            "USD" -> "$"
            else -> "$currencyCode "
        }
        return "%s%d–%d".format(
            symbol,
            low.amount.roundToInt(),
            high.amount.roundToInt()
        )
    }

    fun toPair(): Pair<Double, Double> = low.amount to high.amount
}

sealed interface PriceEstimationStatus {
    object Idle : PriceEstimationStatus
    object Estimating : PriceEstimationStatus
    data class Ready(val priceRange: PriceRange) : PriceEstimationStatus
    data class Failed(val reason: String? = null) : PriceEstimationStatus
}

data class PriceEstimationRequest(
    val itemId: String,
    val categoryId: String?,
    val domainCategoryId: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val region: String? = null,
    val currencyCode: String = "EUR"
)

data class PriceEstimatorCapabilities(
    val supportsAttributes: Boolean = true,
    val supportsDomainCategory: Boolean = true
)

interface PriceEstimatorProvider {
    val id: String
    val capabilities: PriceEstimatorCapabilities

    suspend fun estimate(request: PriceEstimationRequest): PriceRange
}
