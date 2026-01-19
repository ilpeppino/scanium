package com.scanium.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scanium.app.model.FollowOrCustom
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for auto-setting marketplace country when language changes.
 *
 * Verifies that:
 * 1. Setting a supported language auto-sets marketplace country to mapped country
 * 2. Setting an unsupported language auto-sets marketplace country to UK
 * 3. Manual marketplace country selection preserves user choice (sets custom override)
 * 4. Language change always re-applies mapping (clearing manual override)
 */
@RunWith(RobolectricTestRunner::class)
class LanguageMarketplaceAutoSetTest {
    private lateinit var context: Context
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = SettingsRepository(context)
    }

    @Test
    fun `setting Dutch language auto-sets marketplace to Netherlands`() =
        runTest {
            // When setting language to Dutch
            repository.setPrimaryLanguage("nl")

            // Then marketplace country should be Netherlands
            val marketplaceCountry = repository.primaryRegionCountryFlow.first()
            assertThat(marketplaceCountry).isEqualTo("NL")

            // And marketplace override should be cleared (follow)
            val marketplaceOverride = repository.marketplaceCountrySettingFlow.first()
            assertThat(marketplaceOverride is FollowOrCustom.FollowPrimary).isTrue()
        }

    @Test
    fun `setting German language auto-sets marketplace to Germany`() =
        runTest {
            // When setting language to German
            repository.setPrimaryLanguage("de")

            // Then marketplace country should be Germany
            val marketplaceCountry = repository.primaryRegionCountryFlow.first()
            assertThat(marketplaceCountry).isEqualTo("DE")
        }

    @Test
    fun `setting French language auto-sets marketplace to France`() =
        runTest {
            // When setting language to French
            repository.setPrimaryLanguage("fr")

            // Then marketplace country should be France
            val marketplaceCountry = repository.primaryRegionCountryFlow.first()
            assertThat(marketplaceCountry).isEqualTo("FR")
        }

    @Test
    fun `setting English language auto-sets marketplace to UK`() =
        runTest {
            // When setting language to English
            repository.setPrimaryLanguage("en")

            // Then marketplace country should be UK
            val marketplaceCountry = repository.primaryRegionCountryFlow.first()
            assertThat(marketplaceCountry).isEqualTo("GB")
        }

    @Test
    fun `setting Brazilian Portuguese language auto-sets marketplace to UK (unsupported)`() =
        runTest {
            // When setting language to Brazilian Portuguese (not in marketplace list)
            repository.setPrimaryLanguage("pt-BR")

            // Then marketplace country should default to UK
            val marketplaceCountry = repository.primaryRegionCountryFlow.first()
            assertThat(marketplaceCountry).isEqualTo("GB")
        }

    @Test
    fun `manual marketplace country selection sets custom override`() =
        runTest {
            // Given language is set to Dutch (auto-sets NL)
            repository.setPrimaryLanguage("nl")

            // When user manually changes marketplace country to Belgium
            repository.setPrimaryRegionCountry("BE")

            // Then marketplace country should be Belgium
            val marketplaceCountry = repository.primaryRegionCountryFlow.first()
            assertThat(marketplaceCountry).isEqualTo("BE")

            // And marketplace override should be custom
            val marketplaceOverride = repository.marketplaceCountrySettingFlow.first()
            assertThat(marketplaceOverride is FollowOrCustom.Custom).isTrue()
            assertThat((marketplaceOverride as FollowOrCustom.Custom).value).isEqualTo("BE")
        }

    @Test
    fun `language change clears manual marketplace override and re-applies mapping`() =
        runTest {
            // Given language is Dutch and user manually set marketplace to Belgium
            repository.setPrimaryLanguage("nl")
            repository.setPrimaryRegionCountry("BE")

            // Verify override is set
            var marketplaceOverride = repository.marketplaceCountrySettingFlow.first()
            assertThat(marketplaceOverride is FollowOrCustom.Custom).isTrue()

            // When user changes language to German
            repository.setPrimaryLanguage("de")

            // Then marketplace country should be Germany (not Belgium)
            val marketplaceCountry = repository.primaryRegionCountryFlow.first()
            assertThat(marketplaceCountry).isEqualTo("DE")

            // And marketplace override should be cleared
            marketplaceOverride = repository.marketplaceCountrySettingFlow.first()
            assertThat(marketplaceOverride is FollowOrCustom.FollowPrimary).isTrue()
        }

    @Test
    fun `changing from supported to unsupported language sets marketplace to UK`() =
        runTest {
            // Given language is French (supported)
            repository.setPrimaryLanguage("fr")
            assertThat(repository.primaryRegionCountryFlow.first()).isEqualTo("FR")

            // When changing to Brazilian Portuguese (unsupported)
            repository.setPrimaryLanguage("pt-BR")

            // Then marketplace should be UK
            val marketplaceCountry = repository.primaryRegionCountryFlow.first()
            assertThat(marketplaceCountry).isEqualTo("GB")
        }

    @Test
    fun `effective marketplace country returns correct value after language change`() =
        runTest {
            // Given language is set to Dutch
            repository.setPrimaryLanguage("nl")

            // Then effective marketplace country should be Netherlands
            val effectiveCountry = repository.effectiveMarketplaceCountryFlow.first()
            assertThat(effectiveCountry).isEqualTo("NL")
        }

    @Test
    fun `effective marketplace country respects manual override before language change`() =
        runTest {
            // Given language is Dutch (auto-sets NL)
            repository.setPrimaryLanguage("nl")

            // And user manually sets marketplace to Poland
            repository.setPrimaryRegionCountry("PL")

            // Then effective marketplace country should be Poland
            val effectiveCountry = repository.effectiveMarketplaceCountryFlow.first()
            assertThat(effectiveCountry).isEqualTo("PL")
        }

    @Test
    fun `case insensitive language tags work correctly`() =
        runTest {
            // When setting language with different cases
            repository.setPrimaryLanguage("NL")

            // Then marketplace country should still be Netherlands
            val marketplaceCountry = repository.primaryRegionCountryFlow.first()
            assertThat(marketplaceCountry).isEqualTo("NL")
        }
}
