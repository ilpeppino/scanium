package com.scanium.app.pricing

import com.scanium.shared.core.models.assistant.PricingConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PricingV4RepositoryTest {
    @Test
    fun `toPricingInsights maps pricing payload`() {
        val response =
            PricingV4Response(
                success = true,
                pricing =
                    PricingV4PricingDto(
                        status = "OK",
                        countryCode = "NL",
                        sources =
                            listOf(
                                PricingV4SourceDto(
                                    id = "marktplaats",
                                    name = "Marktplaats",
                                    baseUrl = "https://www.marktplaats.nl",
                                    listingCount = 2,
                                    searchUrl = "https://www.marktplaats.nl/q/test/",
                                ),
                            ),
                        totalListingsAnalyzed = 2,
                        timeWindowDays = 30,
                        range = PricingV4RangeDto(low = 75.0, median = 90.0, high = 120.0, currency = "EUR"),
                        confidence = "MED",
                        sampleListings =
                            listOf(
                                PricingV4SampleListingDto(
                                    title = "Philips 3200 koffiemachine",
                                    price = 199.0,
                                    currency = "EUR",
                                    marketplace = "marktplaats",
                                    url = "https://example.com/listing",
                                ),
                            ),
                        fallbackReason = null,
                    ),
            )

        val insights = response.toPricingInsights()

        assertEquals("OK", insights.status)
        assertEquals("NL", insights.countryCode)
        assertEquals(1, insights.marketplacesUsed.size)
        assertEquals("marktplaats", insights.marketplacesUsed[0].id)
        assertEquals(2, insights.marketplacesUsed[0].listingCount)
        assertEquals("https://www.marktplaats.nl", insights.marketplacesUsed[0].baseUrl)
        assertNotNull(insights.range)
        assertEquals(75.0, insights.range!!.low, 0.0)
        assertNotNull(insights.range!!.median)
        assertEquals(90.0, insights.range!!.median!!, 0.0)
        assertEquals(120.0, insights.range!!.high, 0.0)
        assertEquals("EUR", insights.range!!.currency)
        assertEquals(PricingConfidence.MED, insights.confidence)
        assertEquals(2, insights.totalListingsAnalyzed)
        assertEquals(30, insights.timeWindowDays)
        assertEquals(1, insights.sampleListings.size)
        assertEquals("Philips 3200 koffiemachine", insights.sampleListings[0].title)
    }
}
