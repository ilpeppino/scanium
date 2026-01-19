package com.scanium.app.model

import kotlinx.serialization.Serializable

/**
 * Country information from marketplaces.json.
 * Represents a country with its code, currency, and available marketplaces.
 */
@Serializable
data class Country(
    /** ISO country code (2-letter, e.g., "NL", "DE", "GB") */
    val code: String,
    /** Default currency code (3-letter, e.g., "EUR", "USD") */
    val defaultCurrency: String,
    /** List of marketplaces available in this country */
    val marketplaces: List<Marketplace> = emptyList(),
) {
    /**
     * Get display name for this country using the current locale.
     * Falls back to the country code if display name cannot be determined.
     */
    fun getDisplayName(languageCode: String = "en"): String =
        try {
            java.util
                .Locale("", code)
                .getDisplayCountry(java.util.Locale(languageCode))
                .takeIf { it.isNotBlank() }
                ?: code
        } catch (e: Exception) {
            code
        }

    /**
     * Get flag emoji for this country code.
     * Converts 2-letter code to regional indicator symbols.
     * Handles special cases: UK -> GB flag, EU -> EU flag.
     */
    fun getFlagEmoji(): String {
        val flagCode =
            when (code.uppercase()) {
                "UK" -> "GB"

                // UK uses GB flag
                else -> code.uppercase()
            }

        return when (flagCode) {
            "EU" -> {
                "\uD83C\uDDEA\uD83C\uDDFA"
            }

            // EU flag
            else -> {
                if (flagCode.length == 2) {
                    // Convert to regional indicator symbols
                    val firstChar = Character.codePointAt(flagCode, 0)
                    val secondChar = Character.codePointAt(flagCode, 1)
                    String(Character.toChars(0x1F1E6 + firstChar - 'A'.code)) +
                        String(Character.toChars(0x1F1E6 + secondChar - 'A'.code))
                } else {
                    "\uD83C\uDFF3\uFE0F" // White flag for unknown
                }
            }
        }
    }
}

/**
 * Marketplace definition from marketplaces.json.
 */
@Serializable
data class Marketplace(
    val id: String,
    val name: String,
    val domains: List<String> = emptyList(),
    val type: String,
)

/**
 * Root structure of marketplaces.json.
 */
@Serializable
data class MarketplacesConfig(
    val version: Int,
    val countries: List<Country>,
)
