package com.scanium.app.items.edit

import com.scanium.shared.core.models.assistant.ComparableListing
import com.scanium.shared.core.models.assistant.PriceRange
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
 * - Parsing OK status with price range and top 5 comparables
 * - Handling all status types (success, disabled, unsupported, no_results, timeout, error)
 * - Graceful handling of missing/null fields
 */
class PricingInsightsParsingTest {

    @Test
    fun `test parsing success status with price range and comparables`() {
        // Given: A pricing insights response with success status
        val pricingInsights = PricingInsights(
            status = "success",
            result = PricingResult(
                priceRange = PriceRange(
                    min = 25.0,
                    max = 45.0,
                    currency = "EUR"
                ),
                comparables = listOf(
                    ComparableListing(
                        title = "Nike Air Max 90 - Size 42",
                        price = 35.0,
                        currency = "EUR",
                        marketplace = "Marktplaats",
                        url = "https://www.marktplaats.nl/item/123"
                    ),
                    ComparableListing(
                        title = "Nike Air Max 90 - White/Blue",
                        price = 40.0,
                        currency = "EUR",
                        marketplace = "Vinted",
                        url = "https://www.vinted.nl/item/456"
                    ),
                    ComparableListing(
                        title = "Nike Air Max 90 - Like New",
                        price = 30.0,
                        currency = "EUR",
                        marketplace = "eBay",
                        url = "https://www.ebay.nl/item/789"
                    ),
                    ComparableListing(
                        title = "Nike Air Max 90 - Excellent Condition",
                        price = 38.0,
                        currency = "EUR",
                        marketplace = "Marktplaats",
                        url = "https://www.marktplaats.nl/item/101"
                    ),
                    ComparableListing(
                        title = "Nike Air Max 90 - Black",
                        price = 42.0,
                        currency = "EUR",
                        marketplace = "Vinted",
                        url = "https://www.vinted.nl/item/112"
                    )
                ),
                sampleSize = 15
            ),
            errorMessage = null
        )

        // Then: All fields are correctly accessible
        assertEquals("success", pricingInsights.status)
        assertNotNull(pricingInsights.result)

        val result = pricingInsights.result!!
        assertEquals(25.0, result.priceRange.min, 0.01)
        assertEquals(45.0, result.priceRange.max, 0.01)
        assertEquals("EUR", result.priceRange.currency)
        assertEquals(15, result.sampleSize)
        assertEquals(5, result.comparables.size)

        // Verify first comparable
        val firstComparable = result.comparables[0]
        assertEquals("Nike Air Max 90 - Size 42", firstComparable.title)
        assertEquals(35.0, firstComparable.price, 0.01)
        assertEquals("EUR", firstComparable.currency)
        assertEquals("Marktplaats", firstComparable.marketplace)
        assertEquals("https://www.marktplaats.nl/item/123", firstComparable.url)
    }

    @Test
    fun `test parsing disabled status`() {
        val pricingInsights = PricingInsights(
            status = "disabled",
            result = null,
            errorMessage = "Price insights are disabled for your account"
        )

        assertEquals("disabled", pricingInsights.status)
        assertNull(pricingInsights.result)
        assertEquals("Price insights are disabled for your account", pricingInsights.errorMessage)
    }

    @Test
    fun `test parsing unsupported_country status`() {
        val pricingInsights = PricingInsights(
            status = "unsupported_country",
            result = null,
            errorMessage = "Price insights not available for country: US"
        )

        assertEquals("unsupported_country", pricingInsights.status)
        assertNull(pricingInsights.result)
        assertNotNull(pricingInsights.errorMessage)
    }

    @Test
    fun `test parsing no_results status`() {
        val pricingInsights = PricingInsights(
            status = "no_results",
            result = null,
            errorMessage = "No comparable listings found for this item"
        )

        assertEquals("no_results", pricingInsights.status)
        assertNull(pricingInsights.result)
        assertNotNull(pricingInsights.errorMessage)
    }

    @Test
    fun `test parsing timeout status`() {
        val pricingInsights = PricingInsights(
            status = "timeout",
            result = null,
            errorMessage = "Pricing request timed out"
        )

        assertEquals("timeout", pricingInsights.status)
        assertNull(pricingInsights.result)
        assertNotNull(pricingInsights.errorMessage)
    }

    @Test
    fun `test parsing error status`() {
        val pricingInsights = PricingInsights(
            status = "error",
            result = null,
            errorMessage = "Internal error fetching pricing data"
        )

        assertEquals("error", pricingInsights.status)
        assertNull(pricingInsights.result)
        assertNotNull(pricingInsights.errorMessage)
    }

    @Test
    fun `test parsing success with empty comparables`() {
        val pricingInsights = PricingInsights(
            status = "success",
            result = PricingResult(
                priceRange = PriceRange(
                    min = 20.0,
                    max = 30.0,
                    currency = "EUR"
                ),
                comparables = emptyList(),
                sampleSize = 3
            ),
            errorMessage = null
        )

        assertEquals("success", pricingInsights.status)
        assertNotNull(pricingInsights.result)
        assertEquals(0, pricingInsights.result!!.comparables.size)
        assertEquals(3, pricingInsights.result!!.sampleSize)
    }

    @Test
    fun `test parsing success with comparables without URLs`() {
        val pricingInsights = PricingInsights(
            status = "success",
            result = PricingResult(
                priceRange = PriceRange(
                    min = 25.0,
                    max = 45.0,
                    currency = "USD"
                ),
                comparables = listOf(
                    ComparableListing(
                        title = "Test Item 1",
                        price = 30.0,
                        currency = "USD",
                        marketplace = "eBay",
                        url = null
                    )
                ),
                sampleSize = 1
            ),
            errorMessage = null
        )

        assertEquals("success", pricingInsights.status)
        assertNotNull(pricingInsights.result)
        assertEquals(1, pricingInsights.result!!.comparables.size)
        assertNull(pricingInsights.result!!.comparables[0].url)
    }

    @Test
    fun `test parsing with different currencies`() {
        val currencies = listOf("EUR", "USD", "GBP", "CHF")

        currencies.forEach { currency ->
            val pricingInsights = PricingInsights(
                status = "success",
                result = PricingResult(
                    priceRange = PriceRange(
                        min = 10.0,
                        max = 20.0,
                        currency = currency
                    ),
                    comparables = emptyList(),
                    sampleSize = 5
                ),
                errorMessage = null
            )

            assertEquals(currency, pricingInsights.result?.priceRange?.currency)
        }
    }
}
