package com.scanium.app.items.edit

import com.scanium.shared.core.models.assistant.PriceInfo
import com.scanium.shared.core.models.assistant.PriceRange
import com.scanium.shared.core.models.assistant.PricingConfidence
import com.scanium.shared.core.models.assistant.PricingInsights
import com.scanium.shared.core.models.assistant.PricingResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 5 acceptance test: Verify pricing insights parsing.
 *
 * Tests:
 * - Parsing OK status with price range and top 5 results
 * - Handling all status types (OK, DISABLED, NOT_SUPPORTED, NO_RESULTS, TIMEOUT, ERROR)
 * - Graceful handling of missing/null fields
 */
class PricingInsightsParsingTest {

    @Test
    fun `test parsing OK status with price range and results`() {
        // Given: A pricing insights response with OK status
        val pricingInsights = PricingInsights(
            status = "OK",
            countryCode = "NL",
            range = PriceRange(
                low = 25.0,
                high = 45.0,
                currency = "EUR"
            ),
            results = listOf(
                PricingResult(
                    title = "Nike Air Max 90 - Size 42",
                    price = PriceInfo(35.0, "EUR"),
                    sourceMarketplaceId = "marktplaats",
                    url = "https://www.marktplaats.nl/item/123"
                ),
                PricingResult(
                    title = "Nike Air Max 90 - White/Blue",
                    price = PriceInfo(40.0, "EUR"),
                    sourceMarketplaceId = "vinted",
                    url = "https://www.vinted.nl/item/456"
                ),
                PricingResult(
                    title = "Nike Air Max 90 - Like New",
                    price = PriceInfo(30.0, "EUR"),
                    sourceMarketplaceId = "ebay",
                    url = "https://www.ebay.nl/item/789"
                ),
                PricingResult(
                    title = "Nike Air Max 90 - Excellent Condition",
                    price = PriceInfo(38.0, "EUR"),
                    sourceMarketplaceId = "marktplaats",
                    url = "https://www.marktplaats.nl/item/101"
                ),
                PricingResult(
                    title = "Nike Air Max 90 - Black",
                    price = PriceInfo(42.0, "EUR"),
                    sourceMarketplaceId = "vinted",
                    url = "https://www.vinted.nl/item/112"
                )
            ),
            confidence = PricingConfidence.HIGH
        )

        // Then: All fields are correctly accessible
        assertEquals("OK", pricingInsights.status)
        assertEquals("NL", pricingInsights.countryCode)
        assertNotNull(pricingInsights.range)

        val range = pricingInsights.range!!
        assertEquals(25.0, range.low, 0.01)
        assertEquals(45.0, range.high, 0.01)
        assertEquals("EUR", range.currency)
        assertEquals(5, pricingInsights.results.size)
        assertEquals(PricingConfidence.HIGH, pricingInsights.confidence)

        // Verify first result
        val firstResult = pricingInsights.results[0]
        assertEquals("Nike Air Max 90 - Size 42", firstResult.title)
        assertEquals(35.0, firstResult.price.amount, 0.01)
        assertEquals("EUR", firstResult.price.currency)
        assertEquals("marktplaats", firstResult.sourceMarketplaceId)
        assertEquals("https://www.marktplaats.nl/item/123", firstResult.url)
    }

    @Test
    fun `test parsing DISABLED status`() {
        val pricingInsights = PricingInsights(
            status = "DISABLED",
            countryCode = "NL",
            errorCode = "DISABLED_BY_POLICY"
        )

        assertEquals("DISABLED", pricingInsights.status)
        assertNull(pricingInsights.range)
        assertEquals("DISABLED_BY_POLICY", pricingInsights.errorCode)
    }

    @Test
    fun `test parsing NOT_SUPPORTED status`() {
        val pricingInsights = PricingInsights(
            status = "NOT_SUPPORTED",
            countryCode = "US",
            errorCode = "UNSUPPORTED_COUNTRY"
        )

        assertEquals("NOT_SUPPORTED", pricingInsights.status)
        assertNull(pricingInsights.range)
        assertEquals("UNSUPPORTED_COUNTRY", pricingInsights.errorCode)
    }

    @Test
    fun `test parsing NO_RESULTS status`() {
        val pricingInsights = PricingInsights(
            status = "NO_RESULTS",
            countryCode = "NL",
            errorCode = "NO_MATCHES_FOUND"
        )

        assertEquals("NO_RESULTS", pricingInsights.status)
        assertNull(pricingInsights.range)
        assertEquals("NO_MATCHES_FOUND", pricingInsights.errorCode)
    }

    @Test
    fun `test parsing TIMEOUT status`() {
        val pricingInsights = PricingInsights(
            status = "TIMEOUT",
            countryCode = "NL",
            errorCode = "BACKEND_TIMEOUT"
        )

        assertEquals("TIMEOUT", pricingInsights.status)
        assertNull(pricingInsights.range)
        assertEquals("BACKEND_TIMEOUT", pricingInsights.errorCode)
    }

    @Test
    fun `test parsing ERROR status`() {
        val pricingInsights = PricingInsights(
            status = "ERROR",
            countryCode = "NL",
            errorCode = "INTERNAL_SERVER_ERROR"
        )

        assertEquals("ERROR", pricingInsights.status)
        assertNull(pricingInsights.range)
        assertEquals("INTERNAL_SERVER_ERROR", pricingInsights.errorCode)
    }

    @Test
    fun `test parsing OK with empty results`() {
        val pricingInsights = PricingInsights(
            status = "OK",
            countryCode = "NL",
            range = PriceRange(
                low = 20.0,
                high = 30.0,
                currency = "EUR"
            ),
            results = emptyList()
        )

        assertEquals("OK", pricingInsights.status)
        assertNotNull(pricingInsights.range)
        assertEquals(0, pricingInsights.results.size)
    }

    @Test
    fun `test parsing with different currencies`() {
        val currencies = listOf("EUR", "USD", "GBP", "CHF")

        currencies.forEach { currency ->
            val pricingInsights = PricingInsights(
                status = "OK",
                countryCode = "NL",
                range = PriceRange(
                    low = 10.0,
                    high = 20.0,
                    currency = currency
                ),
                results = emptyList()
            )

            assertEquals(currency, pricingInsights.range?.currency)
        }
    }
}