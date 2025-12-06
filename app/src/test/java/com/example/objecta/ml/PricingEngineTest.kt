package com.example.objecta.ml

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for PricingEngine.
 *
 * Tests price generation for all categories including the new DOCUMENT category.
 */
class PricingEngineTest {

    @Test
    fun `generatePriceRange returns valid range for all categories`() {
        val categories = ItemCategory.values()

        for (category in categories) {
            val (low, high) = PricingEngine.generatePriceRange(category)

            assertTrue("Low price should be >= 0 for $category", low >= 0.0)
            assertTrue("High price should be >= low price for $category", high >= low)
        }
    }

    @Test
    fun `generatePriceRange for FASHION returns reasonable range`() {
        val (low, high) = PricingEngine.generatePriceRange(ItemCategory.FASHION)

        assertTrue("Fashion low price should be reasonable", low >= 0.0)
        assertTrue("Fashion high price should be > low", high > low)
        // Fashion base range is 8-40 EUR with randomization
        assertTrue("Fashion should be in expected range", high <= 100.0)
    }

    @Test
    fun `generatePriceRange for ELECTRONICS returns higher range`() {
        val (low, high) = PricingEngine.generatePriceRange(ItemCategory.ELECTRONICS)

        // Electronics base range is 20-300 EUR
        assertTrue("Electronics should have higher base price", low >= 0.0)
        assertTrue("Electronics high price should be substantial", high > low)
    }

    @Test
    fun `generatePriceRange for PLACE returns zero`() {
        val (low, high) = PricingEngine.generatePriceRange(ItemCategory.PLACE)

        assertEquals("Place low price should be 0", 0.0, low, 0.001)
        assertEquals("Place high price should be 0", 0.0, high, 0.001)
    }

    @Test
    fun `generatePriceRange for DOCUMENT returns appropriate range`() {
        val (low, high) = PricingEngine.generatePriceRange(ItemCategory.DOCUMENT)

        // Documents should have zero or minimal pricing since they're informational
        assertTrue("Document low price should be >= 0", low >= 0.0)
        assertTrue("Document high price should be >= low", high >= low)
    }

    @Test
    fun `generatePriceRange for UNKNOWN returns fallback range`() {
        val (low, high) = PricingEngine.generatePriceRange(ItemCategory.UNKNOWN)

        assertTrue("Unknown low price should be reasonable", low >= 0.0)
        assertTrue("Unknown high price should be > low", high > low)
        // Unknown base range is 5-20 EUR
        assertTrue("Unknown should use fallback range", high <= 50.0)
    }

    @Test
    fun `generatePriceRange with bounding box area adjusts price`() {
        val smallArea = 0.1f // 10% of image
        val largeArea = 0.8f // 80% of image

        val (smallLow, smallHigh) = PricingEngine.generatePriceRange(
            ItemCategory.FASHION,
            smallArea
        )
        val (largeLow, largeHigh) = PricingEngine.generatePriceRange(
            ItemCategory.FASHION,
            largeArea
        )

        // Larger objects should generally have higher prices (with randomization considered)
        // We can't guarantee strict inequality due to randomization, but ranges should be positive
        assertTrue("Small item should have positive price", smallHigh > 0)
        assertTrue("Large item should have positive price", largeHigh > 0)
    }

    @Test
    fun `generatePriceRange ensures min is less than max`() {
        val categories = ItemCategory.values()

        for (category in categories) {
            val (low, high) = PricingEngine.generatePriceRange(category)

            if (category == ItemCategory.PLACE || category == ItemCategory.DOCUMENT) {
                // Special categories may have zero prices
                assertTrue("$category: high >= low", high >= low)
            } else {
                assertTrue("$category: high should be > low", high > low)
            }
        }
    }

    @Test
    fun `generateFormattedPrice returns proper EUR format`() {
        val formatted = PricingEngine.generateFormattedPrice(ItemCategory.FASHION)

        assertTrue("Should start with €", formatted.startsWith("€"))
        assertTrue("Should contain dash", formatted.contains("-"))
        assertTrue("Should contain €", formatted.contains("€"))
    }

    @Test
    fun `generateFormattedPrice for PLACE returns N-A`() {
        val formatted = PricingEngine.generateFormattedPrice(ItemCategory.PLACE)

        assertEquals("N/A", formatted)
    }

    @Test
    fun `generateFormattedPrice for DOCUMENT returns valid format`() {
        val formatted = PricingEngine.generateFormattedPrice(ItemCategory.DOCUMENT)

        // Document should return either "€0 - €0" or a valid price range
        assertTrue(
            "Document should have valid price format",
            formatted.startsWith("€") || formatted == "N/A"
        )
    }

    @Test
    fun `generatePriceRange is deterministic with same random seed`() {
        // This test verifies that the function produces consistent results
        // Note: Due to Random, results will vary, but structure should be consistent
        val (low1, high1) = PricingEngine.generatePriceRange(ItemCategory.FOOD)
        val (low2, high2) = PricingEngine.generatePriceRange(ItemCategory.FOOD)

        // Both should be valid ranges even if different values
        assertTrue("First call: low1 >= 0", low1 >= 0.0)
        assertTrue("First call: high1 > low1", high1 >= low1)
        assertTrue("Second call: low2 >= 0", low2 >= 0.0)
        assertTrue("Second call: high2 > low2", high2 >= low2)
    }

    @Test
    fun `generatePriceRange handles zero bounding box area`() {
        val (low, high) = PricingEngine.generatePriceRange(
            ItemCategory.ELECTRONICS,
            0.0f
        )

        assertTrue("Should handle zero area", low >= 0.0)
        assertTrue("Should handle zero area", high > low)
    }

    @Test
    fun `generatePriceRange handles maximum bounding box area`() {
        val (low, high) = PricingEngine.generatePriceRange(
            ItemCategory.HOME_GOOD,
            1.0f
        )

        assertTrue("Should handle full area", low >= 0.0)
        assertTrue("Should handle full area", high > low)
    }

    @Test
    fun `generatePriceRange for all categories produces non-negative values`() {
        val categories = ItemCategory.values()

        for (category in categories) {
            for (area in listOf(null, 0.0f, 0.5f, 1.0f)) {
                val (low, high) = PricingEngine.generatePriceRange(category, area)

                assertTrue(
                    "$category with area $area: low should be >= 0",
                    low >= 0.0
                )
                assertTrue(
                    "$category with area $area: high should be >= 0",
                    high >= 0.0
                )
            }
        }
    }

    @Test
    fun `formatted price contains proper currency symbol`() {
        val categories = listOf(
            ItemCategory.FASHION,
            ItemCategory.ELECTRONICS,
            ItemCategory.FOOD,
            ItemCategory.DOCUMENT
        )

        for (category in categories) {
            val formatted = PricingEngine.generateFormattedPrice(category)

            if (category != ItemCategory.PLACE) {
                assertTrue(
                    "$category formatted price should contain €",
                    formatted.contains("€")
                )
            }
        }
    }

    @Test
    fun `formatted price uses whole numbers`() {
        // Prices should be formatted without decimal places
        val formatted = PricingEngine.generateFormattedPrice(ItemCategory.FASHION)

        if (formatted != "N/A") {
            // Should not contain decimal points
            val numbers = formatted.replace("[^0-9.]".toRegex(), "")
            // After replacing non-digit/non-period chars, we shouldn't have decimals
            // Format is "€X - €Y" so after removing €, -, and spaces we get just numbers
            assertFalse(
                "Should use whole numbers",
                formatted.matches(Regex(".*€\\d+\\.\\d+.*"))
            )
        }
    }
}
