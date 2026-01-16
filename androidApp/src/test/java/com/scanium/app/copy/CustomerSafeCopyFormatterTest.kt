package com.scanium.app.copy

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.regex.Pattern

/**
 * Comprehensive unit tests for CustomerSafeCopyFormatter.
 *
 * Verifies:
 * - Banned tokens are NEVER present in outputs
 * - No percent signs or confidence numbers in outputs
 * - Titles are non-empty and product-type level (not vague)
 * - Pricing format follows "Typical resale value: €X–€Y" and "Based on …"
 * - Items with weak titles are dropped when dropIfWeak=true
 * - Support for all three display modes (ITEM_LIST, ITEM_CARD, ASSISTANT)
 * - Sanitization of all inputs
 */
@RunWith(RobolectricTestRunner::class)
class CustomerSafeCopyFormatterTest {

    // ====== Banned Tokens Tests ======

    @Test
    fun test_bannedTokens_areNeverPresent_inOutputs() {
        // Test each banned token individually
        val bannedTokens = listOf(
            "unknown", "generic", "unbranded", "confidence", "score",
            "might be", "possibly", "cannot determine",
        )

        bannedTokens.forEach { token ->
            val input = ItemInput(
                id = "test",
                itemType = token,  // Try to inject banned token as itemType
            )
            val output = CustomerSafeCopyFormatter.format(input, dropIfWeak = true)

            // Token should either be sanitized away or item dropped
            if (output != null) {
                assertThat(output.title.lowercase()).doesNotContain(token)
                output.highlights.forEach { highlight ->
                    assertThat(highlight.lowercase()).doesNotContain(token)
                }
                output.tags.forEach { tag ->
                    assertThat(tag.lowercase()).doesNotContain(token)
                }
            }
        }
    }

    @Test
    fun test_bannedTokens_removedFromMixedContent() {
        // Test that banned tokens are removed from mixed content
        val input = ItemInput(
            id = "test",
            itemType = "leather handbag with unknown details",
        )
        val output = CustomerSafeCopyFormatter.format(input)!!

        assertThat(output.title).isEqualTo("leather handbag with details")
        assertThat(output.title).doesNotContain("unknown")
    }

    @Test
    fun test_caseInsensitivityOfBannedTokens() {
        // Test that banned token checks are case-insensitive
        val input = ItemInput(
            id = "test",
            itemType = "UNKNOWN item",
        )
        val output = CustomerSafeCopyFormatter.format(input, dropIfWeak = true)

        // Should be dropped because "unknown" is banned (case-insensitive)
        assertThat(output).isNull()
    }

    // ====== Percent and Confidence Number Tests ======

    @Test
    fun test_noPercentOrConfidenceNumbers() {
        val testCases = listOf(
            "leather bag (58% match)",
            "jacket confidence: 0.92",
            "shoes 95% confidence",
            "handbag 0.85",
        )

        testCases.forEach { text ->
            val input = ItemInput(
                id = "test",
                itemType = text,
            )
            val output = CustomerSafeCopyFormatter.format(input)!!

            assertThat(output.title).doesNotContain("%")
            assertThat(output.title).doesNotContainMatch(Pattern.compile("\\d+\\.\\d+"))
            assertThat(output.title).doesNotContain("confidence")
        }
    }

    @Test
    fun test_percentSignsRemoved() {
        val input = ItemInput(
            id = "test",
            itemType = "backpack 78%",
        )
        val output = CustomerSafeCopyFormatter.format(input)!!

        assertThat(output.title).doesNotContain("%")
        assertThat(output.title).isEqualTo("backpack")
    }

    @Test
    fun test_decimalNumbersRemoved() {
        val input = ItemInput(
            id = "test",
            itemType = "hiking boot 0.92",
        )
        val output = CustomerSafeCopyFormatter.format(input)!!

        assertThat(output.title).doesNotContain("0.92")
    }

    // ====== Title Product-Type Level Tests ======

    @Test
    fun test_title_isNonEmpty_andNotVague() {
        val goodTitles = listOf(
            "leather hiking boots",
            "wool sweater",
            "ceramic vase",
            "stainless steel watch",
        )

        goodTitles.forEach { title ->
            val input = ItemInput(
                id = "test",
                itemType = title,
            )
            val output = CustomerSafeCopyFormatter.format(input)!!

            assertThat(output.title).isNotEmpty()
            assertThat(output.title).isEqualTo(title)
        }
    }

    @Test
    fun test_vagueTitle_dropsItemWhenDropIfWeakTrue() {
        val vagueInputs = listOf(
            ItemInput(id = "test1", itemType = "Item"),
            ItemInput(id = "test2", itemType = "Object"),
            ItemInput(id = "test3", itemType = "Thing"),
            ItemInput(id = "test4", itemType = "Unknown"),
            ItemInput(id = "test5"),  // No type, no attributes
        )

        vagueInputs.forEach { input ->
            val output = CustomerSafeCopyFormatter.format(input, dropIfWeak = true)
            assertThat(output).isNull()
        }
    }

    @Test
    fun test_vagueTitle_fallbackWhenDropIfWeakFalse() {
        val input = ItemInput(id = "test")  // No identifying attributes
        val output = CustomerSafeCopyFormatter.format(input, dropIfWeak = false)!!

        assertThat(output.title).isNotEmpty()
        // Fallback title should still be safe (but may be "Item")
    }

    @Test
    fun test_title_constructedFromMaterial_andColor() {
        val input = ItemInput(
            id = "test",
            material = "leather",
            color = "brown",
        )
        val output = CustomerSafeCopyFormatter.format(input)!!

        assertThat(output.title).contains("leather")
        assertThat(output.title).contains("brown")
    }

    @Test
    fun test_title_preferItemTypeOverOtherHints() {
        val input = ItemInput(
            id = "test",
            itemType = "hiking boots",
            material = "rubber",
            color = "red",
        )
        val output = CustomerSafeCopyFormatter.format(input)!!

        assertThat(output.title).isEqualTo("hiking boots")
    }

    @Test
    fun test_title_fallsBackToMaterialAndColor() {
        val input = ItemInput(
            id = "test",
            material = "ceramic",
            color = "blue",
        )
        val output = CustomerSafeCopyFormatter.format(input)!!

        assertThat(output.title).contains("ceramic")
        assertThat(output.title).contains("blue")
    }

    // ====== Pricing Format Tests ======

    @Test
    fun test_pricing_format_includesTypicalResaleValue() {
        val input = ItemInput(
            id = "test",
            itemType = "leather jacket",
            pricingRange = PricingRange(min = 50, max = 150),
        )
        val output = CustomerSafeCopyFormatter.format(input)!!

        assertThat(output.priceLine).isEqualTo("Typical resale value: €50–€150")
    }

    @Test
    fun test_pricing_format_includesBasedOnLine() {
        val input = ItemInput(
            id = "test",
            itemType = "leather jacket",
            pricingRange = PricingRange(min = 50, max = 150),
            pricingContextHint = "excellent condition",
        )
        val output = CustomerSafeCopyFormatter.format(input)!!

        assertThat(output.priceContext).startsWith("Based on")
        assertThat(output.priceContext).contains("excellent condition")
    }

    @Test
    fun test_pricing_defaultContext_whenNoHint() {
        val input = ItemInput(
            id = "test",
            itemType = "backpack",
            pricingRange = PricingRange(min = 20, max = 60),
        )
        val output = CustomerSafeCopyFormatter.format(input)!!

        assertThat(output.priceContext).isEqualTo("Based on current market conditions")
    }

    @Test
    fun test_pricing_nullWhenNoRange() {
        val input = ItemInput(
            id = "test",
            itemType = "hiking boots",
        )
        val output = CustomerSafeCopyFormatter.format(input)!!

        assertThat(output.priceLine).isNull()
        assertThat(output.priceContext).isNull()
    }

    @Test
    fun test_pricing_nullWhenInvalidRange() {
        val input = ItemInput(
            id = "test",
            itemType = "jacket",
            pricingRange = PricingRange(min = 100, max = 50),  // Invalid: min > max
        )
        val output = CustomerSafeCopyFormatter.format(input)!!

        assertThat(output.priceLine).isNull()
        assertThat(output.priceContext).isNull()
    }

    @Test
    fun test_pricing_usesEuroSymbolAndEnDash() {
        val input = ItemInput(
            id = "test",
            itemType = "shoes",
            pricingRange = PricingRange(min = 30, max = 80),
        )
        val output = CustomerSafeCopyFormatter.format(input)!!

        assertThat(output.priceLine).contains("€")
        assertThat(output.priceLine).contains("–")
    }

    // ====== Drop If Weak Tests ======

    @Test
    fun test_dropIfWeak_dropsItemsWhenTitleCannotBeMadeProductType() {
        val weakInputs = listOf(
            ItemInput(id = "1"),  // No attributes
            ItemInput(id = "2", itemType = ""),  // Empty itemType
            ItemInput(id = "3", itemType = "Unknown"),  // Vague
            ItemInput(id = "4", itemType = "Item"),  // Vague
        )

        weakInputs.forEach { input ->
            val output = CustomerSafeCopyFormatter.format(input, dropIfWeak = true)
            assertThat(output).isNull()
        }
    }

    @Test
    fun test_keepIfWeak_false_returnsFallback() {
        val input = ItemInput(id = "test")
        val output = CustomerSafeCopyFormatter.format(input, dropIfWeak = false)!!

        assertThat(output).isNotNull()
        assertThat(output.title).isNotEmpty()
    }

    @Test
    fun test_dropIfWeak_keepsStrongItems() {
        val input = ItemInput(
            id = "test",
            itemType = "leather handbag",
        )
        val output = CustomerSafeCopyFormatter.format(input, dropIfWeak = true)

        assertThat(output).isNotNull()
        assertThat(output!!.title).isEqualTo("leather handbag")
    }

    // ====== Display Mode Tests ======

    @Test
    fun test_itemListMode_noHighlightsOrTags() {
        val input = ItemInput(
            id = "test",
            itemType = "leather boots",
            color = "brown",
            material = "leather",
            inferredBrand = "Nike",
        )
        val output = CustomerSafeCopyFormatter.format(
            input,
            mode = CopyDisplayMode.ITEM_LIST,
        )!!

        assertThat(output.highlights).isEmpty()
        assertThat(output.tags).isEmpty()
    }

    @Test
    fun test_itemCardMode_noHighlightsOrTags() {
        val input = ItemInput(
            id = "test",
            itemType = "leather boots",
            color = "brown",
            material = "leather",
            inferredBrand = "Nike",
        )
        val output = CustomerSafeCopyFormatter.format(
            input,
            mode = CopyDisplayMode.ITEM_CARD,
        )!!

        assertThat(output.highlights).isEmpty()
        assertThat(output.tags).isEmpty()
    }

    @Test
    fun test_assistantMode_includesHighlightsAndTags() {
        val input = ItemInput(
            id = "test",
            itemType = "leather boots",
            color = "brown",
            material = "leather",
            inferredBrand = "Nike",
        )
        val output = CustomerSafeCopyFormatter.format(
            input,
            mode = CopyDisplayMode.ASSISTANT,
        )!!

        assertThat(output.highlights).isNotEmpty()
        assertThat(output.tags).isNotEmpty()
    }

    @Test
    fun test_assistantMode_highlightsContainAttributes() {
        val input = ItemInput(
            id = "test",
            itemType = "hiking boot",
            color = "brown",
            material = "rubber",
            imageHint = "laced design",
        )
        val output = CustomerSafeCopyFormatter.format(
            input,
            mode = CopyDisplayMode.ASSISTANT,
        )!!

        assertThat(output.highlights).containsAtLeast("Color: brown", "Material: rubber", "Details: laced design")
    }

    @Test
    fun test_assistantMode_tagsContainBrandAndCategory() {
        val input = ItemInput(
            id = "test",
            itemType = "hiking boots",
            inferredBrand = "Salomon",
        )
        val output = CustomerSafeCopyFormatter.format(
            input,
            mode = CopyDisplayMode.ASSISTANT,
        )!!

        assertThat(output.tags).contains("Salomon")
        assertThat(output.tags).contains("Footwear")  // Inferred from "boots"
    }

    @Test
    fun test_assistantMode_sanitizesHighlightsAndTags() {
        val input = ItemInput(
            id = "test",
            itemType = "jacket",
            color = "red 95%",
            material = "leather unknown",
        )
        val output = CustomerSafeCopyFormatter.format(
            input,
            mode = CopyDisplayMode.ASSISTANT,
        )!!

        output.highlights.forEach { highlight ->
            assertThat(highlight).doesNotContain("%")
            assertThat(highlight).doesNotContain("unknown")
        }
    }

    // ====== Batch Processing Tests ======

    @Test
    fun test_formatBatch_formatsMultipleItems() {
        val inputs = listOf(
            ItemInput(id = "1", itemType = "hiking boots"),
            ItemInput(id = "2", itemType = "leather jacket"),
            ItemInput(id = "3", itemType = "wool sweater"),
        )
        val outputs = CustomerSafeCopyFormatter.formatBatch(inputs)

        assertThat(outputs).hasSize(3)
        assertThat(outputs[0].title).isEqualTo("hiking boots")
        assertThat(outputs[1].title).isEqualTo("leather jacket")
        assertThat(outputs[2].title).isEqualTo("wool sweater")
    }

    @Test
    fun test_formatBatch_dropsWeakItemsWhenRequested() {
        val inputs = listOf(
            ItemInput(id = "1", itemType = "hiking boots"),  // Strong
            ItemInput(id = "2"),  // Weak
            ItemInput(id = "3", itemType = "leather jacket"),  // Strong
        )
        val outputs = CustomerSafeCopyFormatter.formatBatch(inputs, dropIfWeak = true)

        assertThat(outputs).hasSize(2)
        assertThat(outputs[0].title).isEqualTo("hiking boots")
        assertThat(outputs[1].title).isEqualTo("leather jacket")
    }

    // ====== Edge Cases ======

    @Test
    fun test_emptyStringsAreIgnored() {
        val input = ItemInput(
            id = "test",
            itemType = "leather jacket",
            color = "",
            material = "",
        )
        val output = CustomerSafeCopyFormatter.format(input)!!

        assertThat(output.title).isEqualTo("leather jacket")
    }

    @Test
    fun test_whitespaceOnlyStringsAreTreatedAsEmpty() {
        val input = ItemInput(
            id = "test",
            itemType = "leather jacket",
            color = "   ",
        )
        val output = CustomerSafeCopyFormatter.format(input)!!

        assertThat(output.title).isEqualTo("leather jacket")
    }

    @Test
    fun test_multipleSpacesAreNormalized() {
        val input = ItemInput(
            id = "test",
            itemType = "leather   jacket",
        )
        val output = CustomerSafeCopyFormatter.format(input)!!

        assertThat(output.title).isEqualTo("leather jacket")
    }

    @Test
    fun test_noBannedTokensInPricingContext() {
        val input = ItemInput(
            id = "test",
            itemType = "jacket",
            pricingRange = PricingRange(min = 50, max = 100),
            pricingContextHint = "excellent condition cannot determine exact age",
        )
        val output = CustomerSafeCopyFormatter.format(input)!!

        assertThat(output.priceContext).doesNotContain("cannot determine")
    }

    @Test
    fun test_completeFlowWithAllAttributes() {
        val input = ItemInput(
            id = "item-001",
            itemType = "leather hiking boots",
            material = "leather",
            color = "brown",
            inferredBrand = "Salomon",
            imageHint = "lace-up design",
            pricingRange = PricingRange(min = 60, max = 150),
            pricingContextHint = "excellent condition",
        )
        val output = CustomerSafeCopyFormatter.format(
            input,
            mode = CopyDisplayMode.ASSISTANT,
        )!!

        // Verify all fields
        assertThat(output.title).isEqualTo("leather hiking boots")
        assertThat(output.priceLine).isEqualTo("Typical resale value: €60–€150")
        assertThat(output.priceContext).contains("excellent condition")
        assertThat(output.highlights).isNotEmpty()
        assertThat(output.tags).contains("Salomon")
        assertThat(output.tags).contains("Footwear")

        // Verify no banned tokens or confidence indicators
        assertThat(output.title).doesNotContain("unknown")
        assertThat(output.title).doesNotContain("%")
        assertThat(output.priceLine).doesNotContain("confidence")
    }
}
