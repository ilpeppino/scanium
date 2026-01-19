package com.scanium.app.quality

import com.scanium.app.ItemCategory
import com.scanium.shared.core.models.items.ItemAttribute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for AttributeCompletenessEvaluator.
 *
 * Tests:
 * - Score calculation based on filled attributes
 * - Missing attributes detection and ordering
 * - Ready threshold evaluation
 * - Category-specific requirements
 */
class AttributeCompletenessEvaluatorTest {
    @Test
    fun `empty attributes returns zero score`() {
        val result =
            AttributeCompletenessEvaluator.evaluate(
                category = ItemCategory.FASHION,
                attributes = emptyMap(),
            )

        assertEquals(0, result.score)
        assertFalse(result.isReadyForListing)
        assertTrue(result.missingAttributes.isNotEmpty())
    }

    @Test
    fun `fully filled attributes returns high score`() {
        val attributes =
            mapOf(
                "brand" to ItemAttribute(value = "Nike", confidence = 0.9f, source = "vision"),
                "itemType" to ItemAttribute(value = "T-Shirt", confidence = 0.9f, source = "vision"),
                "color" to ItemAttribute(value = "Blue", confidence = 0.8f, source = "vision"),
                "size" to ItemAttribute(value = "M", confidence = 0.7f, source = "user"),
                "condition" to ItemAttribute(value = "New", confidence = 0.9f, source = "user"),
                "material" to ItemAttribute(value = "Cotton", confidence = 0.6f, source = "vision"),
                "pattern" to ItemAttribute(value = "Solid", confidence = 0.5f, source = "vision"),
                "style" to ItemAttribute(value = "Casual", confidence = 0.5f, source = "vision"),
            )

        val result =
            AttributeCompletenessEvaluator.evaluate(
                category = ItemCategory.FASHION,
                attributes = attributes,
            )

        assertEquals(100, result.score)
        assertTrue(result.isReadyForListing)
        assertTrue(result.missingAttributes.isEmpty())
    }

    @Test
    fun `missing high importance attributes lowers score significantly`() {
        // Only low-importance attributes filled
        val attributes =
            mapOf(
                "pattern" to ItemAttribute(value = "Solid", confidence = 0.5f),
                "style" to ItemAttribute(value = "Casual", confidence = 0.5f),
            )

        val result =
            AttributeCompletenessEvaluator.evaluate(
                category = ItemCategory.FASHION,
                attributes = attributes,
            )

        // Score should be low since high-importance attrs are missing
        assertTrue(result.score < 20)
        assertFalse(result.isReadyForListing)

        // Brand (weight 10) should be first in missing list
        assertEquals("brand", result.missingAttributes.first().key)
    }

    @Test
    fun `missing attributes ordered by importance`() {
        val attributes =
            mapOf(
                "color" to ItemAttribute(value = "Blue", confidence = 0.8f),
            )

        val result =
            AttributeCompletenessEvaluator.evaluate(
                category = ItemCategory.FASHION,
                attributes = attributes,
            )

        val missingKeys = result.missingAttributes.map { it.key }

        // Higher importance should come first
        val brandIndex = missingKeys.indexOf("brand")
        val patternIndex = missingKeys.indexOf("pattern")

        assertTrue("brand should come before pattern", brandIndex < patternIndex)
    }

    @Test
    fun `ready threshold at 70 percent`() {
        // Fill enough to get ~70%
        val attributes =
            mapOf(
                // weight 10
                "brand" to ItemAttribute(value = "Nike", confidence = 0.9f),
                // weight 9
                "itemType" to ItemAttribute(value = "Shirt", confidence = 0.9f),
                // weight 8
                "color" to ItemAttribute(value = "Blue", confidence = 0.8f),
                // weight 8
                "size" to ItemAttribute(value = "M", confidence = 0.7f),
                // weight 7
                "condition" to ItemAttribute(value = "Good", confidence = 0.7f),
            )
        // Total filled weight: 10+9+8+8+7 = 42
        // Total weight: 10+9+8+8+7+5+3+3 = 53
        // Score = 42/53 * 100 = ~79%

        val result =
            AttributeCompletenessEvaluator.evaluate(
                category = ItemCategory.FASHION,
                attributes = attributes,
            )

        assertTrue(result.score >= AttributeCompletenessEvaluator.READY_THRESHOLD)
        assertTrue(result.isReadyForListing)
    }

    @Test
    fun `low confidence attributes do not count as filled`() {
        val attributes =
            mapOf(
                // below threshold
                "brand" to ItemAttribute(value = "Nike", confidence = 0.2f),
                // above threshold
                "color" to ItemAttribute(value = "Blue", confidence = 0.8f),
            )

        val result =
            AttributeCompletenessEvaluator.evaluate(
                category = ItemCategory.FASHION,
                attributes = attributes,
            )

        // Brand should still be in missing list due to low confidence
        assertTrue(result.missingAttributes.any { it.key == "brand" })
        assertTrue(result.filledAttributes.contains("color"))
        assertFalse(result.filledAttributes.contains("brand"))
    }

    @Test
    fun `user source always counts as filled`() {
        val attributes =
            mapOf(
                "brand" to ItemAttribute(value = "Nike", confidence = 0.1f, source = "user"),
            )

        val result =
            AttributeCompletenessEvaluator.evaluate(
                category = ItemCategory.FASHION,
                attributes = attributes,
            )

        // User-entered attributes always count regardless of confidence
        assertTrue(result.filledAttributes.contains("brand"))
    }

    @Test
    fun `electronics category has different requirements`() {
        val fashionResult =
            AttributeCompletenessEvaluator.evaluate(
                category = ItemCategory.FASHION,
                attributes = emptyMap(),
            )

        val electronicsResult =
            AttributeCompletenessEvaluator.evaluate(
                category = ItemCategory.ELECTRONICS,
                attributes = emptyMap(),
            )

        // Both should have missing attributes but with different keys
        val fashionKeys = fashionResult.missingAttributes.map { it.key }.toSet()
        val electronicsKeys = electronicsResult.missingAttributes.map { it.key }.toSet()

        // Electronics should have "model" requirement that fashion doesn't
        assertTrue(electronicsKeys.contains("model"))
        assertFalse(fashionKeys.contains("model"))
    }

    @Test
    fun `photo hints provided for missing attributes`() {
        val result =
            AttributeCompletenessEvaluator.evaluate(
                category = ItemCategory.FASHION,
                attributes = emptyMap(),
            )

        // Brand should have a photo hint
        val brandMissing = result.missingAttributes.find { it.key == "brand" }
        assertNotNull(brandMissing?.photoHint)
        assertTrue(brandMissing?.photoHint?.contains("label") == true || brandMissing?.photoHint?.contains("logo") == true)
    }

    @Test
    fun `getTopMissingAttributes limits results`() {
        val result =
            AttributeCompletenessEvaluator.evaluate(
                category = ItemCategory.FASHION,
                attributes = emptyMap(),
            )

        val topMissing = AttributeCompletenessEvaluator.getTopMissingAttributes(result, limit = 3)

        assertEquals(3, topMissing.size)
    }

    @Test
    fun `getNextPhotoGuidance returns hint for most important missing`() {
        val result =
            AttributeCompletenessEvaluator.evaluate(
                category = ItemCategory.FASHION,
                attributes = emptyMap(),
            )

        val guidance = AttributeCompletenessEvaluator.getNextPhotoGuidance(result)

        assertNotNull(guidance)
        // Should be photo hint for brand (highest importance)
        assertTrue(guidance?.isNotBlank() == true)
    }

    @Test
    fun `unknown category uses default requirements`() {
        val result =
            AttributeCompletenessEvaluator.evaluate(
                category = ItemCategory.UNKNOWN,
                attributes = emptyMap(),
            )

        // Should still have some requirements
        assertTrue(result.missingAttributes.isNotEmpty())
        assertTrue(result.missingAttributes.any { it.key == "itemType" })
    }

    @Test
    fun `blank attribute values do not count as filled`() {
        val attributes =
            mapOf(
                "brand" to ItemAttribute(value = "", confidence = 0.9f),
                "color" to ItemAttribute(value = "  ", confidence = 0.9f),
            )

        val result =
            AttributeCompletenessEvaluator.evaluate(
                category = ItemCategory.FASHION,
                attributes = attributes,
            )

        assertTrue(result.missingAttributes.any { it.key == "brand" })
        assertTrue(result.missingAttributes.any { it.key == "color" })
    }
}
