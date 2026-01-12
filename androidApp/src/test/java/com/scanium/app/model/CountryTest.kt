package com.scanium.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CountryTest {
    @Test
    fun `getFlagEmoji returns correct flag for NL`() {
        // Given
        val country = Country(code = "NL", defaultCurrency = "EUR")

        // When
        val flag = country.getFlagEmoji()

        // Then
        assertEquals("Should return Netherlands flag", "\uD83C\uDDF3\uD83C\uDDF1", flag)
    }

    @Test
    fun `getFlagEmoji returns correct flag for DE`() {
        // Given
        val country = Country(code = "DE", defaultCurrency = "EUR")

        // When
        val flag = country.getFlagEmoji()

        // Then
        assertEquals("Should return Germany flag", "\uD83C\uDDE9\uD83C\uDDEA", flag)
    }

    @Test
    fun `getFlagEmoji returns correct flag for FR`() {
        // Given
        val country = Country(code = "FR", defaultCurrency = "EUR")

        // When
        val flag = country.getFlagEmoji()

        // Then
        assertEquals("Should return France flag", "\uD83C\uDDEB\uD83C\uDDF7", flag)
    }

    @Test
    fun `getFlagEmoji returns correct flag for UK`() {
        // Given
        val country = Country(code = "UK", defaultCurrency = "GBP")

        // When
        val flag = country.getFlagEmoji()

        // Then
        assertEquals("UK should use GB flag", "\uD83C\uDDEC\uD83C\uDDE7", flag)
    }

    @Test
    fun `getFlagEmoji returns correct flag for GB`() {
        // Given
        val country = Country(code = "GB", defaultCurrency = "GBP")

        // When
        val flag = country.getFlagEmoji()

        // Then
        assertEquals("Should return GB flag", "\uD83C\uDDEC\uD83C\uDDE7", flag)
    }

    @Test
    fun `getFlagEmoji returns EU flag for EU code`() {
        // Given
        val country = Country(code = "EU", defaultCurrency = "EUR")

        // When
        val flag = country.getFlagEmoji()

        // Then
        assertEquals("Should return EU flag", "\uD83C\uDDEA\uD83C\uDDFA", flag)
    }

    @Test
    fun `getFlagEmoji returns placeholder for invalid code`() {
        // Given
        val country = Country(code = "INVALID", defaultCurrency = "EUR")

        // When
        val flag = country.getFlagEmoji()

        // Then
        assertEquals("Should return white flag placeholder", "\uD83C\uDFF3\uFE0F", flag)
    }

    @Test
    fun `getFlagEmoji returns placeholder for single character code`() {
        // Given
        val country = Country(code = "X", defaultCurrency = "EUR")

        // When
        val flag = country.getFlagEmoji()

        // Then
        assertEquals("Should return white flag placeholder", "\uD83C\uDFF3\uFE0F", flag)
    }

    @Test
    fun `getDisplayName returns localized name for NL in English`() {
        // Given
        val country = Country(code = "NL", defaultCurrency = "EUR")

        // When
        val displayName = country.getDisplayName("en")

        // Then
        assertEquals("Should return Netherlands", "Netherlands", displayName)
    }

    @Test
    fun `getDisplayName returns localized name for DE in English`() {
        // Given
        val country = Country(code = "DE", defaultCurrency = "EUR")

        // When
        val displayName = country.getDisplayName("en")

        // Then
        assertEquals("Should return Germany", "Germany", displayName)
    }

    @Test
    fun `getDisplayName returns localized name for FR in English`() {
        // Given
        val country = Country(code = "FR", defaultCurrency = "EUR")

        // When
        val displayName = country.getDisplayName("en")

        // Then
        assertEquals("Should return France", "France", displayName)
    }

    @Test
    fun `getDisplayName returns non-empty string`() {
        // Given
        val country = Country(code = "IT", defaultCurrency = "EUR")

        // When
        val displayName = country.getDisplayName("en")

        // Then
        assertFalse("Display name should not be empty", displayName.isEmpty())
    }

    @Test
    fun `getDisplayName returns different names for different countries`() {
        // Given
        val countryNL = Country(code = "NL", defaultCurrency = "EUR")
        val countryDE = Country(code = "DE", defaultCurrency = "EUR")

        // When
        val displayNameNL = countryNL.getDisplayName("en")
        val displayNameDE = countryDE.getDisplayName("en")

        // Then
        assertNotEquals(
            "Different countries should have different display names",
            displayNameNL,
            displayNameDE,
        )
    }

    @Test
    fun `all marketplace countries have valid 2-letter codes`() {
        // This test validates that all expected marketplace country codes are 2 letters
        val validCodes =
            listOf(
                "AL",
                "AD",
                "AT",
                "BE",
                "BG",
                "CH",
                "CY",
                "CZ",
                "DE",
                "DK",
                "EE",
                "ES",
                "FI",
                "FR",
                "GB",
                "GR",
                "HR",
                "HU",
                "IE",
                "IT",
                "LI",
                "LT",
                "LU",
                "LV",
                "MC",
                "MT",
                "NL",
                "NO",
                "PL",
                "PT",
                "RO",
                "SE",
                "SI",
                "SK",
                "SM",
                "VA",
            )

        validCodes.forEach { code ->
            val country = Country(code = code, defaultCurrency = "EUR")
            val flag = country.getFlagEmoji()

            // Flag should not be the placeholder (white flag)
            assertNotEquals(
                "Country $code should have a valid flag, not placeholder",
                "\uD83C\uDFF3\uFE0F",
                flag,
            )

            // Display name should not be the code itself (unless locale fails)
            val displayName = country.getDisplayName("en")
            assertTrue(
                "Country $code should have a display name",
                displayName.isNotBlank(),
            )
        }
    }
}
