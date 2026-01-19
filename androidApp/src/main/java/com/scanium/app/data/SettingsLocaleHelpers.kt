package com.scanium.app.data

import com.scanium.app.model.AssistantRegion
import java.util.Locale

internal object SettingsLocaleHelpers {
    fun detectCountryCodeFromLocale(): String {
        val countryCode = Locale.getDefault().country.uppercase()
        return if (countryCode.length == 2) {
            normalizeCountryCode(countryCode)
        } else {
            "GB"
        }
    }

    fun normalizeCountryCode(countryCode: String): String =
        when (countryCode.uppercase()) {
            "UK" -> "GB"
            else -> countryCode.uppercase()
        }

    fun mapLanguageToMarketplaceCountry(languageTag: String): String =
        when (languageTag.lowercase()) {
            "nl" -> {
                "NL"
            }

            "de" -> {
                "DE"
            }

            "fr" -> {
                "FR"
            }

            "it" -> {
                "IT"
            }

            "es" -> {
                "ES"
            }

            "pt" -> {
                "PT"
            }

            "en" -> {
                "GB"
            }

            "pt-br" -> {
                "GB"
            }

            "system" -> {
                val deviceCountry = detectCountryCodeFromLocale()
                if (isValidMarketplaceCountry(deviceCountry)) deviceCountry else "GB"
            }

            else -> {
                "GB"
            }
        }

    fun mapCountryCodeToRegion(countryCode: String): AssistantRegion =
        when (countryCode.uppercase()) {
            "NL" -> AssistantRegion.NL
            "DE", "AT", "CH", "LI" -> AssistantRegion.DE
            "BE" -> AssistantRegion.BE
            "FR", "MC", "LU" -> AssistantRegion.FR
            "GB", "UK", "IE" -> AssistantRegion.UK
            "US" -> AssistantRegion.US
            else -> AssistantRegion.EU
        }

    private fun isValidMarketplaceCountry(countryCode: String): Boolean {
        val validCountries =
            setOf(
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
        return countryCode.uppercase() in validCountries
    }
}
