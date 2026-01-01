package com.scanium.shared.core.models.pricing.providers

import com.scanium.shared.core.models.pricing.Money
import com.scanium.shared.core.models.pricing.PriceEstimationRequest
import com.scanium.shared.core.models.pricing.PriceEstimatorCapabilities
import com.scanium.shared.core.models.pricing.PriceEstimatorProvider
import com.scanium.shared.core.models.pricing.PriceRange
import kotlinx.coroutines.delay

class MockPriceEstimatorProvider(
    private val delayMs: Long = 250L,
    private val currencyCode: String = "EUR",
) : PriceEstimatorProvider {
    override val id: String = "mock"
    override val capabilities: PriceEstimatorCapabilities = PriceEstimatorCapabilities()

    private val categoryBaseRanges =
        mapOf(
            "FASHION" to (8.0 to 28.0),
            "HOME_GOOD" to (4.0 to 20.0),
            "FOOD" to (1.0 to 8.0),
            "PLANT" to (3.0 to 14.0),
            "ELECTRONICS" to (25.0 to 180.0),
            "DOCUMENT" to (0.0 to 0.0),
            "PLACE" to (0.0 to 0.0),
        )

    override suspend fun estimate(request: PriceEstimationRequest): PriceRange {
        if (delayMs > 0) {
            delay(delayMs)
        }

        val baseRange = categoryBaseRanges[request.categoryId] ?: (10.0 to 45.0)
        val low = baseRange.first
        val high = baseRange.second

        return PriceRange(
            low = Money(low, request.currencyCode.ifEmpty { currencyCode }),
            high = Money(high, request.currencyCode.ifEmpty { currencyCode }),
            confidence = 0.72f,
            source = id,
        )
    }
}

class EbayCompsProvider : PriceEstimatorProvider {
    override val id: String = "ebay-comps"
    override val capabilities: PriceEstimatorCapabilities = PriceEstimatorCapabilities()

    override suspend fun estimate(request: PriceEstimationRequest): PriceRange {
        throw NotImplementedError("EbayCompsProvider is a placeholder for future integration")
    }
}

class PlaceholderProvider : PriceEstimatorProvider {
    override val id: String = "placeholder"
    override val capabilities: PriceEstimatorCapabilities = PriceEstimatorCapabilities()

    override suspend fun estimate(request: PriceEstimationRequest): PriceRange {
        throw NotImplementedError("PlaceholderProvider is not implemented yet")
    }
}
