package com.scanium.app.data

import android.content.Context
import android.util.Log
import com.scanium.app.model.Country
import com.scanium.app.model.MarketplacesConfig
import kotlinx.serialization.json.Json
import java.io.IOException

private const val TAG = "MarketplaceRepository"

/**
 * Repository for loading marketplace and country data.
 * Loads countries from the bundled marketplaces.json file.
 */
class MarketplaceRepository(
    private val context: Context,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private var cachedCountries: List<Country>? = null

    /**
     * Load all countries from the marketplaces.json file.
     * Results are cached after first load.
     *
     * @return List of countries sorted alphabetically by display name (in English)
     */
    fun loadCountries(): List<Country> {
        cachedCountries?.let { return it }

        return try {
            val jsonString =
                context.assets
                    .open("config/marketplaces.json")
                    .bufferedReader()
                    .use { it.readText() }

            val config = json.decodeFromString<MarketplacesConfig>(jsonString)
            val countries =
                config.countries
                    .sortedBy { it.getDisplayName("en") }

            cachedCountries = countries
            Log.d(TAG, "Loaded ${countries.size} countries from marketplaces.json")
            countries
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load marketplaces.json", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse marketplaces.json", e)
            emptyList()
        }
    }

    /**
     * Get a country by its code.
     *
     * @param code ISO country code (e.g., "NL", "DE", "GB")
     * @return Country if found, null otherwise
     */
    fun getCountryByCode(code: String): Country? {
        return loadCountries().find { it.code.equals(code, ignoreCase = true) }
    }

    /**
     * Clear cached countries (useful for testing).
     */
    fun clearCache() {
        cachedCountries = null
    }
}
