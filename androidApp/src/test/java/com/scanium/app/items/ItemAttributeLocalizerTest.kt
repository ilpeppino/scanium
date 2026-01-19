package com.scanium.app.items

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for ItemAttributeLocalizer.
 *
 * Verifies:
 * - Known vocabulary detection for colors/materials
 * - Language violation detection
 * - English vocabulary detection
 * - Localization fallback for unknown values
 *
 * Note: Context-based localization tests (localizeColor with Italian context, etc.)
 * are better tested via instrumented tests or integration tests, as Robolectric
 * doesn't fully support locale-specific resource loading with createConfigurationContext.
 *
 * These tests ensure the NON-NEGOTIABLE LANGUAGE CONTRACT is enforced:
 * - Any user-visible attribute value MUST be localized when languageTag != "en"
 * - If languageTag != "en", English words are NOT allowed in attribute display values
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ItemAttributeLocalizerTest {
    private lateinit var applicationContext: Context

    @Before
    fun setUp() {
        applicationContext = ApplicationProvider.getApplicationContext()
    }

    // ==================== KNOWN COLOR VOCABULARY TESTS ====================

    @Test
    fun whenColorIsKnown_thenIsKnownColorReturnsTrue() {
        assertThat(ItemAttributeLocalizer.isKnownColor("orange")).isTrue()
        assertThat(ItemAttributeLocalizer.isKnownColor("ORANGE")).isTrue()
        assertThat(ItemAttributeLocalizer.isKnownColor("blue")).isTrue()
        assertThat(ItemAttributeLocalizer.isKnownColor("red")).isTrue()
        assertThat(ItemAttributeLocalizer.isKnownColor("green")).isTrue()
        assertThat(ItemAttributeLocalizer.isKnownColor("black")).isTrue()
        assertThat(ItemAttributeLocalizer.isKnownColor("white")).isTrue()
    }

    @Test
    fun whenColorIsUnknown_thenIsKnownColorReturnsFalse() {
        assertThat(ItemAttributeLocalizer.isKnownColor("chartreuse")).isFalse()
        assertThat(ItemAttributeLocalizer.isKnownColor("periwinkle")).isFalse()
        assertThat(ItemAttributeLocalizer.isKnownColor("vermilion")).isFalse()
    }

    @Test
    fun whenColorHasWhitespace_thenTrimsAndChecks() {
        assertThat(ItemAttributeLocalizer.isKnownColor("  orange  ")).isTrue()
        assertThat(ItemAttributeLocalizer.isKnownColor(" blue\n")).isTrue()
    }

    // ==================== KNOWN MATERIAL VOCABULARY TESTS ====================

    @Test
    fun whenMaterialIsKnown_thenIsKnownMaterialReturnsTrue() {
        assertThat(ItemAttributeLocalizer.isKnownMaterial("plastic")).isTrue()
        assertThat(ItemAttributeLocalizer.isKnownMaterial("cotton")).isTrue()
        assertThat(ItemAttributeLocalizer.isKnownMaterial("leather")).isTrue()
        assertThat(ItemAttributeLocalizer.isKnownMaterial("wool")).isTrue()
        assertThat(ItemAttributeLocalizer.isKnownMaterial("silk")).isTrue()
        assertThat(ItemAttributeLocalizer.isKnownMaterial("metal")).isTrue()
    }

    @Test
    fun whenMaterialIsUnknown_thenIsKnownMaterialReturnsFalse() {
        assertThat(ItemAttributeLocalizer.isKnownMaterial("unobtanium")).isFalse()
        assertThat(ItemAttributeLocalizer.isKnownMaterial("vibranium")).isFalse()
        assertThat(ItemAttributeLocalizer.isKnownMaterial("mithril")).isFalse()
    }

    // ==================== KNOWN CONDITION VOCABULARY TESTS ====================

    @Test
    fun whenConditionIsKnown_thenIsKnownConditionReturnsTrue() {
        assertThat(ItemAttributeLocalizer.isKnownCondition("new")).isTrue()
        assertThat(ItemAttributeLocalizer.isKnownCondition("used")).isTrue()
        assertThat(ItemAttributeLocalizer.isKnownCondition("refurbished")).isTrue()
        assertThat(ItemAttributeLocalizer.isKnownCondition("as_good_as_new")).isTrue()
    }

    @Test
    fun whenConditionIsUnknown_thenIsKnownConditionReturnsFalse() {
        assertThat(ItemAttributeLocalizer.isKnownCondition("excellent")).isFalse()
        assertThat(ItemAttributeLocalizer.isKnownCondition("mint")).isFalse()
    }

    // ==================== ENGLISH VOCABULARY DETECTION TESTS ====================

    @Test
    fun whenValueContainsEnglishColorWords_thenContainsEnglishVocabularyReturnsTrue() {
        assertThat(ItemAttributeLocalizer.containsEnglishVocabulary("orange")).isTrue()
        assertThat(ItemAttributeLocalizer.containsEnglishVocabulary("blue")).isTrue()
        assertThat(ItemAttributeLocalizer.containsEnglishVocabulary("red")).isTrue()
    }

    @Test
    fun whenValueContainsEnglishMaterialWords_thenContainsEnglishVocabularyReturnsTrue() {
        assertThat(ItemAttributeLocalizer.containsEnglishVocabulary("plastic")).isTrue()
        assertThat(ItemAttributeLocalizer.containsEnglishVocabulary("cotton")).isTrue()
        assertThat(ItemAttributeLocalizer.containsEnglishVocabulary("leather")).isTrue()
    }

    @Test
    fun whenValueContainsEnglishConditionWords_thenContainsEnglishVocabularyReturnsTrue() {
        assertThat(ItemAttributeLocalizer.containsEnglishVocabulary("new")).isTrue()
        assertThat(ItemAttributeLocalizer.containsEnglishVocabulary("used")).isTrue()
    }

    @Test
    fun whenValueDoesNotContainEnglishWords_thenContainsEnglishVocabularyReturnsFalse() {
        assertThat(ItemAttributeLocalizer.containsEnglishVocabulary("arancione")).isFalse()
        assertThat(ItemAttributeLocalizer.containsEnglishVocabulary("plastica")).isFalse()
        assertThat(ItemAttributeLocalizer.containsEnglishVocabulary("Nike")).isFalse()
    }

    // ==================== LANGUAGE VIOLATION DETECTION TESTS ====================

    @Test
    fun whenAttributeHasEnglishColor_andLanguageIsItalian_thenDetectsViolation() {
        // Arrange
        val attributes =
            mapOf(
                "color" to "orange",
                "brand" to "Nike",
            )

        // Act
        val violations = ItemAttributeLocalizer.detectEnglishViolations(attributes, "it")

        // Assert
        assertThat(violations).contains("color")
        assertThat(violations).hasSize(1) // Brand is not a constrained vocabulary, not checked
    }

    @Test
    fun whenAttributeHasEnglishMaterial_andLanguageIsGerman_thenDetectsViolation() {
        // Arrange
        val attributes =
            mapOf(
                "material" to "plastic",
                "size" to "Large",
            )

        // Act
        val violations = ItemAttributeLocalizer.detectEnglishViolations(attributes, "de")

        // Assert
        assertThat(violations).contains("material")
        assertThat(violations).hasSize(1)
    }

    @Test
    fun whenAttributeHasMultipleViolations_thenDetectsAll() {
        // Arrange
        val attributes =
            mapOf(
                "color" to "orange",
                "material" to "plastic",
                "condition" to "used",
            )

        // Act
        val violations = ItemAttributeLocalizer.detectEnglishViolations(attributes, "fr")

        // Assert
        assertThat(violations).containsExactly("color", "material", "condition")
    }

    @Test
    fun whenAttributeHasLocalizedValues_andLanguageIsItalian_thenNoViolation() {
        // Arrange - These are non-English words
        val attributes =
            mapOf(
                "color" to "arancione",
                "material" to "plastica",
            )

        // Act
        val violations = ItemAttributeLocalizer.detectEnglishViolations(attributes, "it")

        // Assert
        assertThat(violations).isEmpty()
    }

    @Test
    fun whenLanguageIsEnglish_thenNoViolationCheck() {
        // Arrange - English values are OK when language is English
        val attributes =
            mapOf(
                "color" to "orange",
                "material" to "plastic",
                "condition" to "used",
            )

        // Act
        val violations = ItemAttributeLocalizer.detectEnglishViolations(attributes, "en")

        // Assert
        assertThat(violations).isEmpty()
    }

    @Test
    fun whenLanguageIsEnUppercase_thenNoViolationCheck() {
        // Arrange - Case insensitive check for language
        val attributes =
            mapOf(
                "color" to "orange",
            )

        // Act
        val violations = ItemAttributeLocalizer.detectEnglishViolations(attributes, "EN")

        // Assert
        assertThat(violations).isEmpty()
    }

    @Test
    fun whenNonConstrainedVocabularyHasEnglishWords_thenNoViolation() {
        // Arrange - Brand and size are not constrained vocabularies
        val attributes =
            mapOf(
                "brand" to "Apple",
                "size" to "Medium",
                "model" to "iPhone 15 Pro",
            )

        // Act
        val violations = ItemAttributeLocalizer.detectEnglishViolations(attributes, "it")

        // Assert
        assertThat(violations).isEmpty()
    }

    // ==================== LOCALIZATION FALLBACK TESTS ====================

    @Test
    fun whenColorIsUnknown_thenLocalizeColorReturnsAsIs() {
        // Arrange - Unknown color should be returned as-is
        val result = ItemAttributeLocalizer.localizeColor(applicationContext, "chartreuse")

        // Assert
        assertThat(result).isEqualTo("chartreuse")
    }

    @Test
    fun whenMaterialIsUnknown_thenLocalizeMaterialReturnsAsIs() {
        // Arrange - Unknown material should be returned as-is
        val result = ItemAttributeLocalizer.localizeMaterial(applicationContext, "unobtanium")

        // Assert
        assertThat(result).isEqualTo("unobtanium")
    }

    @Test
    fun whenAttributeKeyIsNotConstrained_thenLocalizeAttributeValueReturnsAsIs() {
        // Arrange - Brand is not a constrained vocabulary
        val result = ItemAttributeLocalizer.localizeAttributeValue(applicationContext, "brand", "Nike")

        // Assert
        assertThat(result).isEqualTo("Nike")
    }
}
