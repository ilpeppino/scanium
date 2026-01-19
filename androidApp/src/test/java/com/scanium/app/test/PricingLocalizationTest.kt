package com.scanium.app.test

import com.scanium.app.copy.CopyDisplayMode
import com.scanium.app.copy.CustomerSafeCopyFormatter
import com.scanium.app.copy.ItemInput
import com.scanium.app.copy.PricingRange
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Regression test to ensure pricing is structured (not English sentences) for localization.
 *
 * Validates that CustomerSafeCopyFormatter outputs language-agnostic PricingDisplay
 * instead of hardcoded English strings like "Typical resale value: €X–€Y".
 * UI layer is responsible for localizing via stringResource().
 */
class PricingLocalizationTest {
    @Test
    fun `formatter outputs structured pricing display not English sentences`() {
        val itemInput =
            ItemInput(
                id = "test-item",
                itemType = "Leather Bag",
                pricingRange =
                    PricingRange(
                        min = 20,
                        max = 40,
                        currency = "EUR",
                    ),
                pricingContextHint = "condition: excellent",
            )

        val result =
            CustomerSafeCopyFormatter.format(
                itemInput,
                mode = CopyDisplayMode.ITEM_LIST,
            )

        assertTrue("Formatter should return non-null result", result != null)
        result!!

        // CRITICAL: Pricing must be structured, not English strings
        assertTrue("Pricing should be structured PricingDisplay", result.pricing != null)

        val pricing = result.pricing!!
        assertTrue("Min price should be 20", pricing.min == 20)
        assertTrue("Max price should be 40", pricing.max == 40)
        assertTrue("Currency should be EUR", pricing.currency == "EUR")
        assertTrue("Context key should be set", pricing.contextKey != null)

        // VERIFY: No English hardcoded strings in pricing output
        val contextKey = pricing.contextKey ?: ""
        if (contextKey.contains("Based on", ignoreCase = true) ||
            contextKey.contains("current market", ignoreCase = true)
        ) {
            fail(
                "Pricing context key should NOT contain English text. " +
                    "UI layer (via stringResource) handles localization. Got: $contextKey",
            )
        }
    }

    @Test
    fun `formatter returns null pricing for invalid price range`() {
        val itemInput =
            ItemInput(
                id = "test-item",
                itemType = "Item",
                pricingRange =
                    PricingRange(
                        min = 100,
                        // Invalid: max < min
                        max = 50,
                        currency = "EUR",
                    ),
            )

        val result =
            CustomerSafeCopyFormatter.format(
                itemInput,
                mode = CopyDisplayMode.ITEM_LIST,
            )

        assertTrue("Should return result", result != null)
        result!!
        assertTrue("Invalid price range should result in null pricing", result.pricing == null)
    }

    @Test
    fun `formatter returns null pricing when no price range provided`() {
        val itemInput =
            ItemInput(
                id = "test-item",
                itemType = "Item",
                // No pricing data
                pricingRange = null,
            )

        val result =
            CustomerSafeCopyFormatter.format(
                itemInput,
                mode = CopyDisplayMode.ITEM_LIST,
            )

        assertTrue("Should return result", result != null)
        result!!
        assertTrue("Missing price range should result in null pricing", result.pricing == null)
    }

    @Test
    fun `formatter handles multiple items with structured pricing`() {
        val items =
            listOf(
                ItemInput(
                    id = "item1",
                    itemType = "Leather Jacket",
                    pricingRange = PricingRange(30, 60),
                ),
                ItemInput(
                    id = "item2",
                    itemType = "Wooden Chair",
                    pricingRange = PricingRange(15, 35),
                ),
                ItemInput(
                    id = "item3",
                    itemType = "Vintage Watch",
                    pricingRange = PricingRange(50, 150),
                ),
            )

        items.forEach { item ->
            val result = CustomerSafeCopyFormatter.format(item, CopyDisplayMode.ITEM_LIST)
            assertTrue("Should format item", result != null)
            result!!

            if (result.pricing != null) {
                val pricing = result.pricing!!
                assertTrue("Min should be positive", pricing.min > 0)
                assertTrue("Max should be >= min", pricing.max >= pricing.min)
                assertTrue("Currency should be EUR", pricing.currency == "EUR")
                assertTrue("Context key should be set", pricing.contextKey != null)
            }
        }
    }

    @Test
    fun `no English pricing text in formatter output`() {
        val bannedPhrases =
            listOf(
                "Typical resale value",
                "Based on",
                "current market",
                "market conditions",
            )

        val itemInput =
            ItemInput(
                id = "test",
                itemType = "Test Item",
                pricingRange = PricingRange(10, 20),
                pricingContextHint = "test hint",
            )

        val result = CustomerSafeCopyFormatter.format(itemInput, CopyDisplayMode.ITEM_LIST)
        assertTrue("Result should not be null", result != null)
        result!!

        val pricing = result.pricing
        if (pricing != null) {
            val contextKey = pricing.contextKey ?: ""
            bannedPhrases.forEach { phrase ->
                if (contextKey.contains(phrase, ignoreCase = true)) {
                    fail(
                        "Pricing structure contains English text '$phrase'. " +
                            "Should use resource key for localization.",
                    )
                }
            }
        }
    }
}
