package com.scanium.app.data

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
class MarketplaceRepositoryTest {
    private lateinit var context: Context
    private lateinit var assetManager: AssetManager
    private lateinit var repository: MarketplaceRepository

    private val sampleJson =
        """
        {
          "version": 1,
          "countries": [
            {
              "code": "NL",
              "defaultCurrency": "EUR",
              "marketplaces": [
                { "id": "bol", "name": "Bol.com", "domains": ["bol.com"], "type": "marketplace" }
              ]
            },
            {
              "code": "DE",
              "defaultCurrency": "EUR",
              "marketplaces": [
                { "id": "amazon", "name": "Amazon", "domains": ["amazon.de"], "type": "global" }
              ]
            },
            {
              "code": "FR",
              "defaultCurrency": "EUR",
              "marketplaces": []
            }
          ]
        }
        """.trimIndent()

    @Before
    fun setup() {
        assetManager = mockk()
        context =
            mockk {
                every { assets } returns assetManager
            }
        repository = MarketplaceRepository(context)
    }

    @Test
    fun `loadCountries returns non-empty list`() {
        // Given
        val inputStream = ByteArrayInputStream(sampleJson.toByteArray())
        every { assetManager.open("config/marketplaces.json") } returns inputStream

        // When
        val countries = repository.loadCountries()

        // Then
        assertFalse("Countries list should not be empty", countries.isEmpty())
    }

    @Test
    fun `loadCountries parses all countries correctly`() {
        // Given
        val inputStream = ByteArrayInputStream(sampleJson.toByteArray())
        every { assetManager.open("config/marketplaces.json") } returns inputStream

        // When
        val countries = repository.loadCountries()

        // Then
        assertEquals("Should have 3 countries", 3, countries.size)
    }

    @Test
    fun `loadCountries returns countries with unique codes`() {
        // Given
        val inputStream = ByteArrayInputStream(sampleJson.toByteArray())
        every { assetManager.open("config/marketplaces.json") } returns inputStream

        // When
        val countries = repository.loadCountries()
        val codes = countries.map { it.code }

        // Then
        assertEquals("All country codes should be unique", codes.size, codes.distinct().size)
    }

    @Test
    fun `loadCountries returns sorted countries`() {
        // Given
        val inputStream = ByteArrayInputStream(sampleJson.toByteArray())
        every { assetManager.open("config/marketplaces.json") } returns inputStream

        // When
        val countries = repository.loadCountries()
        val displayNames = countries.map { it.getDisplayName("en") }

        // Then
        assertEquals(
            "Countries should be sorted alphabetically by display name",
            displayNames.sorted(),
            displayNames,
        )
    }

    @Test
    fun `getCountryByCode returns correct country`() {
        // Given
        val inputStream = ByteArrayInputStream(sampleJson.toByteArray())
        every { assetManager.open("config/marketplaces.json") } returns inputStream

        // When
        val country = repository.getCountryByCode("NL")

        // Then
        assertNotNull("Country should be found", country)
        assertEquals("Country code should match", "NL", country?.code)
        assertEquals("Currency should match", "EUR", country?.defaultCurrency)
    }

    @Test
    fun `getCountryByCode is case insensitive`() {
        // Given
        val inputStream = ByteArrayInputStream(sampleJson.toByteArray())
        every { assetManager.open("config/marketplaces.json") } returns inputStream

        // When
        val countryLower = repository.getCountryByCode("nl")
        val countryUpper = repository.getCountryByCode("NL")

        // Then
        assertNotNull("Country should be found with lowercase", countryLower)
        assertNotNull("Country should be found with uppercase", countryUpper)
        assertEquals("Both should return the same country", countryLower?.code, countryUpper?.code)
    }

    @Test
    fun `loadCountries caches results`() {
        // Given
        val inputStream1 = ByteArrayInputStream(sampleJson.toByteArray())
        val inputStream2 = ByteArrayInputStream(sampleJson.toByteArray())
        every { assetManager.open("config/marketplaces.json") } returnsMany listOf(inputStream1, inputStream2)

        // When
        val countries1 = repository.loadCountries()
        val countries2 = repository.loadCountries()

        // Then
        assertTrue("Should return cached result", countries1 === countries2)
    }

    @Test
    fun `clearCache allows reloading`() {
        // Given
        val inputStream1 = ByteArrayInputStream(sampleJson.toByteArray())
        val inputStream2 = ByteArrayInputStream(sampleJson.toByteArray())
        every { assetManager.open("config/marketplaces.json") } returnsMany listOf(inputStream1, inputStream2)

        // When
        val countries1 = repository.loadCountries()
        repository.clearCache()
        val countries2 = repository.loadCountries()

        // Then
        assertFalse("Should not return same instance after cache clear", countries1 === countries2)
    }
}
