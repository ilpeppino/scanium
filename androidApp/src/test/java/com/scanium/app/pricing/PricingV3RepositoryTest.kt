package com.scanium.app.pricing

import com.scanium.shared.core.models.assistant.PricingConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PricingV3RepositoryTest {
    @Test
    fun `toPricingInsights maps pricing payload`() {
        val response =
            PricingV3Response(
                success = true,
                pricing =
                    PricingV3PricingDto(
                        status = "OK",
                        countryCode = "NL",
                        marketplacesUsed =
                            listOf(
                                PricingV3MarketplaceDto(id = "marktplaats", name = "Marktplaats"),
                                PricingV3MarketplaceDto(id = "bol", name = "Bol.com", baseUrl = "https://bol.com"),
                            ),
                        range = PricingV3RangeDto(low = 75.0, high = 120.0, currency = "EUR"),
                        confidence = "HIGH",
                        reason = "Based on 4 similar listings",
                        resultCount = 4,
                    ),
            )

        val insights = response.toPricingInsights()

        assertEquals("OK", insights.status)
        assertEquals("NL", insights.countryCode)
        assertEquals(2, insights.marketplacesUsed.size)
        assertEquals("marktplaats", insights.marketplacesUsed[0].id)
        assertEquals("", insights.marketplacesUsed[0].baseUrl)
        assertEquals("bol", insights.marketplacesUsed[1].id)
        assertEquals("https://bol.com", insights.marketplacesUsed[1].baseUrl)
        assertNotNull(insights.range)
        assertEquals(75.0, insights.range!!.low, 0.0)
        assertEquals(120.0, insights.range!!.high, 0.0)
        assertEquals("EUR", insights.range!!.currency)
        assertEquals(PricingConfidence.HIGH, insights.confidence)
        assertEquals("Based on 4 similar listings", insights.querySummary)
    }
}
