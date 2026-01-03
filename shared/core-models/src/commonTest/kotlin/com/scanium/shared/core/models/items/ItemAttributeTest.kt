package com.scanium.shared.core.models.items

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for ItemAttribute and AttributeConfidenceTier.
 *
 * Tests verify:
 * - Confidence tier classification (LOW/MEDIUM/HIGH)
 * - Verified status based on confidence threshold
 * - Factory methods
 * - Boundary conditions
 */
class ItemAttributeTest {

    // ==========================================================================
    // Confidence Tier Tests
    // ==========================================================================

    @Test
    fun whenConfidenceBelow50Percent_thenLowTier() {
        val attr = ItemAttribute(value = "test", confidence = 0.3f)

        assertEquals(AttributeConfidenceTier.LOW, attr.confidenceTier)
    }

    @Test
    fun whenConfidenceBetween50And80Percent_thenMediumTier() {
        val attr = ItemAttribute(value = "test", confidence = 0.6f)

        assertEquals(AttributeConfidenceTier.MEDIUM, attr.confidenceTier)
    }

    @Test
    fun whenConfidence80PercentOrAbove_thenHighTier() {
        val attr = ItemAttribute(value = "test", confidence = 0.9f)

        assertEquals(AttributeConfidenceTier.HIGH, attr.confidenceTier)
    }

    @Test
    fun whenConfidenceExactly50Percent_thenMediumTier() {
        val attr = ItemAttribute(value = "test", confidence = 0.5f)

        assertEquals(AttributeConfidenceTier.MEDIUM, attr.confidenceTier)
    }

    @Test
    fun whenConfidenceExactly80Percent_thenHighTier() {
        val attr = ItemAttribute(value = "test", confidence = 0.8f)

        assertEquals(AttributeConfidenceTier.HIGH, attr.confidenceTier)
    }

    @Test
    fun whenConfidenceZero_thenLowTier() {
        val attr = ItemAttribute(value = "test", confidence = 0.0f)

        assertEquals(AttributeConfidenceTier.LOW, attr.confidenceTier)
    }

    @Test
    fun whenConfidence100Percent_thenHighTier() {
        val attr = ItemAttribute(value = "test", confidence = 1.0f)

        assertEquals(AttributeConfidenceTier.HIGH, attr.confidenceTier)
    }

    @Test
    fun whenConfidenceJustBelow50Percent_thenLowTier() {
        val attr = ItemAttribute(value = "test", confidence = 0.49f)

        assertEquals(AttributeConfidenceTier.LOW, attr.confidenceTier)
    }

    @Test
    fun whenConfidenceJustBelow80Percent_thenMediumTier() {
        val attr = ItemAttribute(value = "test", confidence = 0.79f)

        assertEquals(AttributeConfidenceTier.MEDIUM, attr.confidenceTier)
    }

    // ==========================================================================
    // isVerified Tests
    // ==========================================================================

    @Test
    fun whenConfidenceBelow50Percent_thenNotVerified() {
        val attr = ItemAttribute(value = "test", confidence = 0.3f)

        assertFalse(attr.isVerified)
    }

    @Test
    fun whenConfidenceAtLeast50Percent_thenVerified() {
        val attr = ItemAttribute(value = "test", confidence = 0.5f)

        assertTrue(attr.isVerified)
    }

    @Test
    fun whenConfidenceHigh_thenVerified() {
        val attr = ItemAttribute(value = "test", confidence = 0.9f)

        assertTrue(attr.isVerified)
    }

    @Test
    fun whenConfidenceJustBelow50_thenNotVerified() {
        val attr = ItemAttribute(value = "test", confidence = 0.49f)

        assertFalse(attr.isVerified)
    }

    // ==========================================================================
    // Data Class Properties Tests
    // ==========================================================================

    @Test
    fun whenCreatingAttribute_thenValueIsStored() {
        val attr = ItemAttribute(value = "Dell")

        assertEquals("Dell", attr.value)
    }

    @Test
    fun whenCreatingAttributeWithSource_thenSourceIsStored() {
        val attr = ItemAttribute(value = "Dell", confidence = 0.9f, source = "logo")

        assertEquals("logo", attr.source)
    }

    @Test
    fun whenCreatingAttributeWithoutSource_thenSourceIsNull() {
        val attr = ItemAttribute(value = "Dell", confidence = 0.9f)

        assertNull(attr.source)
    }

    @Test
    fun whenDefaultConfidence_thenIsZero() {
        val attr = ItemAttribute(value = "test")

        assertEquals(0.0f, attr.confidence)
    }

    // ==========================================================================
    // Factory Method Tests
    // ==========================================================================

    @Test
    fun whenUsingFromValue_thenCreatesAttributeWithDefaultConfidence() {
        val attr = ItemAttribute.fromValue("TestBrand")

        assertEquals("TestBrand", attr.value)
        assertEquals(0.0f, attr.confidence)
        assertNull(attr.source)
    }

    @Test
    fun whenUsingFromMapEntry_thenCreatesAttribute() {
        val attr = ItemAttribute.fromMapEntry("brand", "Dell")

        assertEquals("Dell", attr.value)
    }

    // ==========================================================================
    // AttributeConfidenceTier Tests
    // ==========================================================================

    @Test
    fun whenLowTier_thenHasCorrectDisplayName() {
        assertEquals("Low", AttributeConfidenceTier.LOW.displayName)
    }

    @Test
    fun whenMediumTier_thenHasCorrectDisplayName() {
        assertEquals("Medium", AttributeConfidenceTier.MEDIUM.displayName)
    }

    @Test
    fun whenHighTier_thenHasCorrectDisplayName() {
        assertEquals("High", AttributeConfidenceTier.HIGH.displayName)
    }

    @Test
    fun whenLowTier_thenHasAppropriateDescription() {
        assertTrue(AttributeConfidenceTier.LOW.description.isNotEmpty())
        assertTrue(AttributeConfidenceTier.LOW.description.contains("verification", ignoreCase = true))
    }

    @Test
    fun whenMediumTier_thenHasAppropriateDescription() {
        assertTrue(AttributeConfidenceTier.MEDIUM.description.isNotEmpty())
    }

    @Test
    fun whenHighTier_thenHasAppropriateDescription() {
        assertTrue(AttributeConfidenceTier.HIGH.description.isNotEmpty())
        assertTrue(AttributeConfidenceTier.HIGH.description.contains("verified", ignoreCase = true))
    }

    @Test
    fun whenAllTiers_thenHaveUniqueDisplayNames() {
        val displayNames = AttributeConfidenceTier.values().map { it.displayName }

        assertEquals(displayNames.toSet().size, displayNames.size, "Display names should be unique")
    }

    // ==========================================================================
    // Boundary Testing
    // ==========================================================================

    @Test
    fun whenBoundaryTestingAllThresholds_thenCorrectClassification() {
        val testCases = mapOf(
            0.0f to AttributeConfidenceTier.LOW,
            0.49f to AttributeConfidenceTier.LOW,
            0.5f to AttributeConfidenceTier.MEDIUM,
            0.51f to AttributeConfidenceTier.MEDIUM,
            0.79f to AttributeConfidenceTier.MEDIUM,
            0.8f to AttributeConfidenceTier.HIGH,
            0.81f to AttributeConfidenceTier.HIGH,
            1.0f to AttributeConfidenceTier.HIGH,
        )

        testCases.forEach { (confidence, expectedTier) ->
            val attr = ItemAttribute(value = "test", confidence = confidence)
            assertEquals(
                expectedTier,
                attr.confidenceTier,
                "Confidence $confidence should map to $expectedTier",
            )
        }
    }

    @Test
    fun whenBoundaryTestingVerified_thenCorrectStatus() {
        val testCases = mapOf(
            0.0f to false,
            0.49f to false,
            0.5f to true,
            0.51f to true,
            0.99f to true,
            1.0f to true,
        )

        testCases.forEach { (confidence, expectedVerified) ->
            val attr = ItemAttribute(value = "test", confidence = confidence)
            assertEquals(
                expectedVerified,
                attr.isVerified,
                "Confidence $confidence should be verified=$expectedVerified",
            )
        }
    }

    // ==========================================================================
    // Common Attribute Scenarios
    // ==========================================================================

    @Test
    fun whenBrandAttributeFromLogo_thenHighConfidenceAndVerified() {
        val brand = ItemAttribute(
            value = "Nike",
            confidence = 0.92f,
            source = "logo",
        )

        assertEquals(AttributeConfidenceTier.HIGH, brand.confidenceTier)
        assertTrue(brand.isVerified)
        assertEquals("logo", brand.source)
    }

    @Test
    fun whenColorAttributeFromVision_thenMediumConfidence() {
        val color = ItemAttribute(
            value = "blue",
            confidence = 0.65f,
            source = "color",
        )

        assertEquals(AttributeConfidenceTier.MEDIUM, color.confidenceTier)
        assertTrue(color.isVerified)
    }

    @Test
    fun whenModelAttributeFromOcr_thenLowConfidence() {
        val model = ItemAttribute(
            value = "XK-9000",
            confidence = 0.35f,
            source = "ocr",
        )

        assertEquals(AttributeConfidenceTier.LOW, model.confidenceTier)
        assertFalse(model.isVerified)
    }

    @Test
    fun whenMaterialAttributeFromLabel_thenMediumConfidence() {
        val material = ItemAttribute(
            value = "wood",
            confidence = 0.7f,
            source = "label",
        )

        assertEquals(AttributeConfidenceTier.MEDIUM, material.confidenceTier)
        assertTrue(material.isVerified)
    }
}
