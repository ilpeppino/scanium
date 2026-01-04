package com.scanium.shared.core.models.items

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for VisionAttributes and related models.
 *
 * Tests verify:
 * - Primary value extraction (color, brand, model)
 * - isEmpty behavior
 * - Data class properties
 */
class VisionAttributesTest {

    // ==========================================================================
    // isEmpty Tests
    // ==========================================================================

    @Test
    fun whenAllFieldsEmpty_thenIsEmptyTrue() {
        val attrs = VisionAttributes()

        assertTrue(attrs.isEmpty)
    }

    @Test
    fun whenOnlyOcrTextPresent_thenIsEmptyFalse() {
        val attrs = VisionAttributes(ocrText = "IKEA KALLAX")

        assertFalse(attrs.isEmpty)
    }

    @Test
    fun whenOnlyColorsPresent_thenIsEmptyFalse() {
        val attrs = VisionAttributes(
            colors = listOf(VisionColor(name = "blue", hex = "***REMOVED***0000FF", score = 0.8f))
        )

        assertFalse(attrs.isEmpty)
    }

    @Test
    fun whenOnlyLogosPresent_thenIsEmptyFalse() {
        val attrs = VisionAttributes(
            logos = listOf(VisionLogo(name = "Nike", score = 0.9f))
        )

        assertFalse(attrs.isEmpty)
    }

    @Test
    fun whenOnlyLabelsPresent_thenIsEmptyFalse() {
        val attrs = VisionAttributes(
            labels = listOf(VisionLabel(name = "Furniture", score = 0.85f))
        )

        assertFalse(attrs.isEmpty)
    }

    @Test
    fun whenOnlyBrandCandidatesPresent_thenIsEmptyFalse() {
        val attrs = VisionAttributes(brandCandidates = listOf("IKEA"))

        assertFalse(attrs.isEmpty)
    }

    @Test
    fun whenOnlyModelCandidatesPresent_thenIsEmptyFalse() {
        val attrs = VisionAttributes(modelCandidates = listOf("XK-9000"))

        assertFalse(attrs.isEmpty)
    }

    @Test
    fun whenOcrTextIsBlank_thenIsEmptyTrue() {
        val attrs = VisionAttributes(ocrText = "   ")

        assertTrue(attrs.isEmpty)
    }

    // ==========================================================================
    // primaryColor Tests
    // ==========================================================================

    @Test
    fun whenNoColors_thenPrimaryColorNull() {
        val attrs = VisionAttributes()

        assertNull(attrs.primaryColor)
    }

    @Test
    fun whenSingleColor_thenPrimaryColorIsThatColor() {
        val blue = VisionColor(name = "blue", hex = "***REMOVED***0000FF", score = 0.8f)
        val attrs = VisionAttributes(colors = listOf(blue))

        assertEquals(blue, attrs.primaryColor)
    }

    @Test
    fun whenMultipleColors_thenPrimaryColorIsHighestScore() {
        val blue = VisionColor(name = "blue", hex = "***REMOVED***0000FF", score = 0.6f)
        val white = VisionColor(name = "white", hex = "***REMOVED***FFFFFF", score = 0.9f)
        val gray = VisionColor(name = "gray", hex = "***REMOVED***808080", score = 0.3f)
        val attrs = VisionAttributes(colors = listOf(blue, white, gray))

        assertEquals(white, attrs.primaryColor)
        assertEquals("white", attrs.primaryColor?.name)
    }

    // ==========================================================================
    // primaryBrand Tests
    // ==========================================================================

    @Test
    fun whenNoLogosOrCandidates_thenPrimaryBrandNull() {
        val attrs = VisionAttributes()

        assertNull(attrs.primaryBrand)
    }

    @Test
    fun whenLogoPresent_thenPrimaryBrandFromLogo() {
        val attrs = VisionAttributes(
            logos = listOf(VisionLogo(name = "Nike", score = 0.9f)),
            brandCandidates = listOf("Adidas") // Should be ignored when logo exists
        )

        assertEquals("Nike", attrs.primaryBrand)
    }

    @Test
    fun whenMultipleLogos_thenPrimaryBrandFromHighestScore() {
        val attrs = VisionAttributes(
            logos = listOf(
                VisionLogo(name = "Nike", score = 0.7f),
                VisionLogo(name = "Adidas", score = 0.95f),
                VisionLogo(name = "Puma", score = 0.6f),
            )
        )

        assertEquals("Adidas", attrs.primaryBrand)
    }

    @Test
    fun whenNoLogosButCandidatesPresent_thenPrimaryBrandFromCandidates() {
        val attrs = VisionAttributes(
            brandCandidates = listOf("IKEA", "KALLAX")
        )

        assertEquals("IKEA", attrs.primaryBrand)
    }

    // ==========================================================================
    // primaryModel Tests
    // ==========================================================================

    @Test
    fun whenNoModelCandidates_thenPrimaryModelNull() {
        val attrs = VisionAttributes()

        assertNull(attrs.primaryModel)
    }

    @Test
    fun whenModelCandidatesPresent_thenPrimaryModelIsFirst() {
        val attrs = VisionAttributes(
            modelCandidates = listOf("XK-9000", "802.758.87", "M100")
        )

        assertEquals("XK-9000", attrs.primaryModel)
    }

    // ==========================================================================
    // EMPTY Companion Object Tests
    // ==========================================================================

    @Test
    fun whenUsingEmpty_thenIsEmptyTrue() {
        val attrs = VisionAttributes.EMPTY

        assertTrue(attrs.isEmpty)
    }

    @Test
    fun whenUsingEmpty_thenNoPrimaryValues() {
        val attrs = VisionAttributes.EMPTY

        assertNull(attrs.primaryColor)
        assertNull(attrs.primaryBrand)
        assertNull(attrs.primaryModel)
    }

    // ==========================================================================
    // VisionColor Tests
    // ==========================================================================

    @Test
    fun whenCreatingVisionColor_thenPropertiesAreStored() {
        val color = VisionColor(name = "blue", hex = "***REMOVED***1E40AF", score = 0.75f)

        assertEquals("blue", color.name)
        assertEquals("***REMOVED***1E40AF", color.hex)
        assertEquals(0.75f, color.score)
    }

    // ==========================================================================
    // VisionLogo Tests
    // ==========================================================================

    @Test
    fun whenCreatingVisionLogo_thenPropertiesAreStored() {
        val logo = VisionLogo(name = "Apple", score = 0.92f)

        assertEquals("Apple", logo.name)
        assertEquals(0.92f, logo.score)
    }

    // ==========================================================================
    // VisionLabel Tests
    // ==========================================================================

    @Test
    fun whenCreatingVisionLabel_thenPropertiesAreStored() {
        val label = VisionLabel(name = "Electronics", score = 0.88f)

        assertEquals("Electronics", label.name)
        assertEquals(0.88f, label.score)
    }

    // ==========================================================================
    // Full Scenario Tests
    // ==========================================================================

    @Test
    fun whenFullVisionResponse_thenAllPropertiesAccessible() {
        val attrs = VisionAttributes(
            colors = listOf(
                VisionColor("white", "***REMOVED***FFFFFF", 0.35f),
                VisionColor("brown", "***REMOVED***8B5A2B", 0.28f),
            ),
            ocrText = "IKEA\nKALLAX\n77x77 cm\nMade in Poland\nArt. 802.758.87",
            logos = listOf(VisionLogo("IKEA", 0.91f)),
            labels = listOf(
                VisionLabel("Furniture", 0.95f),
                VisionLabel("Shelf", 0.92f),
            ),
            brandCandidates = listOf("IKEA", "KALLAX"),
            modelCandidates = listOf("802.758.87"),
        )

        assertFalse(attrs.isEmpty)
        assertEquals("white", attrs.primaryColor?.name)
        assertEquals("IKEA", attrs.primaryBrand)
        assertEquals("802.758.87", attrs.primaryModel)
        assertEquals(2, attrs.colors.size)
        assertEquals(1, attrs.logos.size)
        assertEquals(2, attrs.labels.size)
        assertTrue(attrs.ocrText?.contains("KALLAX") == true)
    }

    @Test
    fun whenWeakBrandDetection_thenFallsToCandidates() {
        val attrs = VisionAttributes(
            ocrText = "Generic Product\nModel X100",
            labels = listOf(VisionLabel("Table", 0.85f)),
            colors = listOf(VisionColor("black", "***REMOVED***000000", 0.45f)),
            // No logos, only candidates from OCR
            brandCandidates = listOf("Generic Product"),
            modelCandidates = listOf("X100"),
        )

        assertEquals("Generic Product", attrs.primaryBrand)
        assertEquals("X100", attrs.primaryModel)
    }
}
