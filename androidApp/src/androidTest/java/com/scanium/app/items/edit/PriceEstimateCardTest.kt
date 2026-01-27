package com.scanium.app.items.edit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scanium.app.R
import com.scanium.app.pricing.PricingMissingField
import com.scanium.app.pricing.PricingUiState
import com.scanium.shared.core.models.assistant.MarketplaceUsed
import com.scanium.shared.core.models.assistant.PriceInfo
import com.scanium.shared.core.models.assistant.PriceRange
import com.scanium.shared.core.models.assistant.PricingConfidence
import com.scanium.shared.core.models.assistant.PricingInsights
import com.scanium.shared.core.models.assistant.SampleListing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PriceEstimateCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun readyState_enablesGetEstimateButton() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeTestRule.setContent {
            PriceEstimateCard(
                uiState = PricingUiState.Ready,
                missingFields = emptySet(),
                regionLabel = "NL",
                onGetEstimate = {},
                onUsePrice = {},
                onRefresh = {},
                onRetry = {},
            )
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.pricing_button_get_estimate))
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun insufficientData_disablesGetEstimateButton() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeTestRule.setContent {
            PriceEstimateCard(
                uiState = PricingUiState.InsufficientData,
                missingFields = setOf(PricingMissingField.BRAND, PricingMissingField.MODEL),
                regionLabel = "NL",
                onGetEstimate = {},
                onUsePrice = {},
                onRefresh = {},
                onRetry = {},
            )
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.pricing_button_get_estimate))
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun successState_showsRangeAndUseButton() {
        val insights =
            PricingInsights(
                status = "OK",
                countryCode = "NL",
                marketplacesUsed = emptyList(),
                range = PriceRange(low = 75.0, high = 120.0, currency = "EUR"),
                confidence = PricingConfidence.HIGH,
            )

        composeTestRule.setContent {
            PriceEstimateCard(
                uiState = PricingUiState.Success(insights, isStale = false),
                missingFields = emptySet(),
                regionLabel = "NL",
                onGetEstimate = {},
                onUsePrice = {},
                onRefresh = {},
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithText("Estimated Resale Price").assertIsDisplayed()
        composeTestRule.onNodeWithText("Use €98", substring = true).assertExists()
    }

    @Test
    fun successState_showsSourcesAndSampleListings() {
        val insights =
            PricingInsights(
                status = "OK",
                countryCode = "NL",
                marketplacesUsed =
                    listOf(
                        MarketplaceUsed(
                            id = "marktplaats",
                            name = "Marktplaats",
                            baseUrl = "https://www.marktplaats.nl",
                            listingCount = 2,
                            searchUrl = "https://www.marktplaats.nl/q/test/",
                        ),
                    ),
                range = PriceRange(low = 75.0, median = 90.0, high = 120.0, currency = "EUR"),
                confidence = PricingConfidence.MED,
                sampleListings =
                    listOf(
                        SampleListing(
                            title = "Philips 3200 koffiemachine",
                            price = PriceInfo(amount = 199.0, currency = "EUR"),
                            marketplace = "marktplaats",
                        ),
                    ),
                totalListingsAnalyzed = 2,
                timeWindowDays = 30,
            )

        composeTestRule.setContent {
            PriceEstimateCard(
                uiState = PricingUiState.Success(insights, isStale = false),
                missingFields = emptySet(),
                regionLabel = "NL",
                onGetEstimate = {},
                onUsePrice = {},
                onRefresh = {},
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithText("Based on real listings from:").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 listings").assertIsDisplayed()
        composeTestRule.onNodeWithText("View listings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sample listings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Philips 3200 koffiemachine", substring = true).assertExists()
    }
}
