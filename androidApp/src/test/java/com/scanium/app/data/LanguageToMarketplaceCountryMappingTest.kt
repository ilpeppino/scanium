package com.scanium.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for language-to-marketplace-country mapping logic.
 *
 * Verifies that:
 * 1. Languages that map to countries in marketplaces.json use that country
 * 2. Languages that don't map to any marketplace country default to GB (UK)
 */
@RunWith(RobolectricTestRunner::class)
class LanguageToMarketplaceCountryMappingTest {
    private lateinit var context: Context
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = SettingsRepository(context)
    }

    @Test
    fun `dutch language maps to Netherlands`() {
        val result = repository.mapLanguageToMarketplaceCountry("nl")
        assertThat(result).isEqualTo("NL")
    }

    @Test
    fun `german language maps to Germany`() {
        val result = repository.mapLanguageToMarketplaceCountry("de")
        assertThat(result).isEqualTo("DE")
    }

    @Test
    fun `french language maps to France`() {
        val result = repository.mapLanguageToMarketplaceCountry("fr")
        assertThat(result).isEqualTo("FR")
    }

    @Test
    fun `italian language maps to Italy`() {
        val result = repository.mapLanguageToMarketplaceCountry("it")
        assertThat(result).isEqualTo("IT")
    }

    @Test
    fun `spanish language maps to Spain`() {
        val result = repository.mapLanguageToMarketplaceCountry("es")
        assertThat(result).isEqualTo("ES")
    }

    @Test
    fun `portuguese language maps to Portugal`() {
        val result = repository.mapLanguageToMarketplaceCountry("pt")
        assertThat(result).isEqualTo("PT")
    }

    @Test
    fun `english language maps to United Kingdom`() {
        val result = repository.mapLanguageToMarketplaceCountry("en")
        assertThat(result).isEqualTo("GB")
    }

    @Test
    fun `brazilian portuguese defaults to UK (Brazil not in marketplace list)`() {
        val result = repository.mapLanguageToMarketplaceCountry("pt-BR")
        assertThat(result).isEqualTo("GB")
    }

    @Test
    fun `brazilian portuguese lowercase defaults to UK`() {
        val result = repository.mapLanguageToMarketplaceCountry("pt-br")
        assertThat(result).isEqualTo("GB")
    }

    @Test
    fun `unknown language defaults to UK`() {
        val result = repository.mapLanguageToMarketplaceCountry("ja")
        assertThat(result).isEqualTo("GB")
    }

    @Test
    fun `arabic language defaults to UK`() {
        val result = repository.mapLanguageToMarketplaceCountry("ar")
        assertThat(result).isEqualTo("GB")
    }

    @Test
    fun `empty string defaults to UK`() {
        val result = repository.mapLanguageToMarketplaceCountry("")
        assertThat(result).isEqualTo("GB")
    }

    @Test
    fun `language tags are case insensitive`() {
        assertThat(repository.mapLanguageToMarketplaceCountry("NL")).isEqualTo("NL")
        assertThat(repository.mapLanguageToMarketplaceCountry("Nl")).isEqualTo("NL")
        assertThat(repository.mapLanguageToMarketplaceCountry("DE")).isEqualTo("DE")
        assertThat(repository.mapLanguageToMarketplaceCountry("De")).isEqualTo("DE")
    }
}
